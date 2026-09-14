package com.lsc.corp.wsplugin.run;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.economy.ResourceLedger;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.world.PrototypeLoopService;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffectType;

public final class RunService {
    private final JavaPlugin plugin;
    private final RunRepository seasonRepository;
    private final RunRepository legacyPrototypeRepository;
    private final RunRepository testRepository;
    private final PrototypeContent content;
    private final String runtimeRevision;
    private final TelemetryService telemetry;
    private final Object serialQueue = new Object();
    private final AtomicBoolean acceptingCommands = new AtomicBoolean(true);
    private final TestTransactionPauseGate testTransactionPause = new TestTransactionPauseGate();
    private RunSnapshot current;
    private BukkitTask heartbeat;
    private PrototypeLoopService loop;
    private EquipmentService equipment;
    private GrowthService growth;
    private BiConsumer<String, Map<String, Integer>> personalResourceReconciler = (owner, balance) -> { };
    private int ticksUntilSave;

    public RunService(JavaPlugin plugin, RunRepository seasonRepository, RunRepository legacyPrototypeRepository,
                      RunRepository testRepository, PrototypeContent content, String runtimeRevision,
                      TelemetryService telemetry) {
        this.plugin = plugin;
        this.seasonRepository = seasonRepository;
        this.legacyPrototypeRepository = legacyPrototypeRepository;
        this.testRepository = testRepository;
        this.content = content;
        this.runtimeRevision = runtimeRevision;
        this.telemetry = telemetry;
        this.ticksUntilSave = autosaveTicks();
        plugin.saveDefaultConfig();
    }

    public void attach(PrototypeLoopService loop, EquipmentService equipment, GrowthService growth) {
        this.loop = loop;
        this.equipment = equipment;
        this.growth = growth;
    }

    public void setPersonalResourceReconciler(
            BiConsumer<String, Map<String, Integer>> personalResourceReconciler) {
        this.personalResourceReconciler = java.util.Objects.requireNonNull(personalResourceReconciler);
    }

    public void restore() throws IOException {
        synchronized (serialQueue) {
            RunSnapshot season = seasonRepository.load().orElse(null);
            RunSnapshot prototype = legacyPrototypeRepository.load().orElse(null);
            RunSnapshot test = testRepository.load().orElse(null);
            try {
                current = RunRecoveryPolicy.select(season, prototype, test);
            } catch (IllegalStateException exception) {
                throw new IOException(exception.getMessage(), exception);
            }
            if (current != null && "RUNNING".equals(current.state)) {
                telemetry.event(current.runId, "RUN_RESTORED", "{\"version\":" + current.version + "}");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player player : onlineMembers()) {
                        restorePlayer(player);
                    }
                    if (loop != null) {
                        loop.restoreWorldObjects();
                    }
                });
            }
        }
    }

    public RunSnapshot create(Collection<Player> players) throws IOException {
        if (!acceptingCommands.get()) {
            throw new IllegalStateException("Server is shutting down");
        }
        if (players.size() < content.minimumPlayers() || players.size() > content.maximumPlayers()) {
            throw new IllegalArgumentException("Season 1 run requires 2 to 4 players");
        }
        synchronized (serialQueue) {
            return createLocked(players, "SEASON_1", runtimeRevision, "s1-", 0L, players.size());
        }
    }

    public RunSnapshot createTest(Player owner, long deterministicSeed, int virtualPartySize) throws IOException {
        if (!acceptingCommands.get()) {
            throw new IllegalStateException("Server is shutting down");
        }
        if (virtualPartySize < 1 || virtualPartySize > content.maximumPlayers()) {
            throw new IllegalArgumentException("Virtual party size must be 1 to " + content.maximumPlayers());
        }
        synchronized (serialQueue) {
            RunSnapshot snapshot = createLocked(List.of(owner), "TEST", runtimeRevision,
                    "test-", deterministicSeed, virtualPartySize);
            snapshot.test = new RunSnapshot.TestState();
            snapshot.test.ownerUuid = owner.getUniqueId().toString();
            snapshot.test.virtualPartySize = virtualPartySize;
            snapshot.test.deterministicSeed = deterministicSeed == 0L ? snapshot.seed : deterministicSeed;
            snapshot.test.logicalNowEpochMs = snapshot.createdAtEpochMs;
            snapshot.test.restorePending = true;
            saveLocked();
            return snapshot;
        }
    }

    private RunSnapshot createLocked(Collection<Player> players, String runType, String contentRevision, String idPrefix,
                                     long requestedSeed, int effectivePartySize) throws IOException {
        if (current != null && !List.of("ENDED", "ABORTED").contains(current.state)) {
            throw new IllegalStateException("A run already exists: " + current.runId);
        }
        RunSnapshot snapshot = new RunSnapshot();
        UUID id = UUID.randomUUID();
        snapshot.runId = idPrefix + id;
        snapshot.runType = runType;
        snapshot.contentRevision = contentRevision;
        snapshot.createdAtEpochMs = Instant.now().toEpochMilli();
        snapshot.checkpointStartedAtEpochMs = snapshot.createdAtEpochMs;
        snapshot.seed = requestedSeed == 0L ? id.getMostSignificantBits() : requestedSeed;
        for (PrototypeContent.ResourceDefinition definition : content.resources()) {
            snapshot.resources.put(definition.id(), 0);
        }
        for (Player player : players) {
            String uuid = player.getUniqueId().toString();
            snapshot.registeredPlayers.add(uuid);
            RunSnapshot.PlayerState state = new RunSnapshot.PlayerState();
            state.uuid = uuid;
            state.lastKnownName = player.getName();
            state.personalResourcesInitialized = true;
            snapshot.players.put(uuid, state);
            captureLocation(state, player.getLocation());
        }
        current = snapshot;
        commitEventLocked("create:" + snapshot.runId, "RUN_CREATED", "{\"members\":" + players.size()
                + ",\"effectivePartySize\":" + effectivePartySize + ",\"runType\":\"" + runType + "\"}");
        saveLocked();
        return snapshot;
    }

    public void start() throws IOException {
        synchronized (serialQueue) {
            requireCurrent();
            if (!"LOBBY".equals(current.state)) {
                throw new IllegalStateException("Run is not in LOBBY");
            }
            List<Player> online = onlineMembers();
            if (online.size() != current.registeredPlayers.size()) {
                throw new IllegalStateException("All registered players must be online to start");
            }
            current.state = "RUNNING";
            current.startedAtEpochMs = clockNowMillisLocked();
            current.checkpointStartedAtEpochMs = current.startedAtEpochMs;
            commitEventLocked("start:" + current.runId, "RUN_STARTED", "{\"day\":1}");
            saveLocked();
            for (Player player : online) {
                player.setLevel(1);
                player.setExp(0);
                equipment.syncAuthoritativeEquipment(player);
            }
            loop.startRunWorld();
            broadcast(ChatColor.GOLD + "[WildSurvival] Season 1 회차가 시작되었습니다. Day 1");
        }
    }

    public void stop(String reason, String actor) throws IOException {
        synchronized (serialQueue) {
            requireCurrent();
            if (List.of("ENDED", "ABORTED").contains(current.state)) {
                return;
            }
            current.state = "ABORTED";
            current.endReason = reason;
            if (loop != null) {
                loop.cleanupWorldObjects();
            }
            commitEventLocked("stop:" + current.runId, "RUN_ENDED", "{\"reason\":\"" + escape(reason) + "\"}");
            saveLocked();
            telemetry.audit(current.runId, actor, "season.stop", reason);
            telemetry.sessionReport(current);
            broadcast(ChatColor.RED + "[WildSurvival] 회차가 종료되었습니다: " + reason);
        }
    }

    public void complete(String reason) throws IOException {
        synchronized (serialQueue) {
            requireCurrent();
            if (!"RUNNING".equals(current.state)) {
                return;
            }
            if (current.day < 50 || current.finalObjective == null
                    || !current.finalObjective.completionCommitted) {
                throw new IllegalStateException("Season 1 cannot complete before the Day 50 Final transaction");
            }
            current.state = "ENDED";
            current.endReason = reason;
            if (loop != null) {
                loop.cleanupWorldObjects();
            }
            commitEventLocked("complete:" + current.runId, "RUN_ENDED", "{\"result\":\"SEASON_1_COMPLETE\"}");
            saveLocked();
            telemetry.sessionReport(current);
            broadcast(ChatColor.GREEN + "[WildSurvival] Season 1 완주: " + reason);
        }
    }

    public boolean commitOnce(String idempotencyKey, String eventType, String payload, Consumer<RunSnapshot> mutation) {
        synchronized (serialQueue) {
            requireCurrent();
            if (current.committedKeys.contains(idempotencyKey)) {
                return false;
            }
            mutation.accept(current);
            commitEventLocked(idempotencyKey, eventType, payload);
            saveUnchecked();
            return true;
        }
    }

    /** Commits an idempotent event on a detached candidate and adopts it only after the save wins. */
    public boolean commitOnceAtomically(String idempotencyKey, String eventType, String payload,
                                        Consumer<RunSnapshot> mutation) {
        synchronized (serialQueue) {
            requireCurrent();
            DurableRunMutation.Outcome<Boolean> outcome = persistCandidateLocked(candidate -> {
                if (candidate.committedKeys.contains(idempotencyKey)) return false;
                mutation.accept(candidate);
                appendEvent(candidate, idempotencyKey, eventType, payload);
                return true;
            }, Boolean.TRUE::equals);
            if (outcome.persisted()) {
                current = outcome.snapshot();
                telemetry.event(current.runId, eventType, payload);
            }
            return outcome.result();
        }
    }

    public void mutate(Consumer<RunSnapshot> mutation) {
        synchronized (serialQueue) {
            requireCurrent();
            mutation.accept(current);
            saveUnchecked();
        }
    }

    /** Applies a persisted mutation on a detached snapshot and adopts it only after a successful save. */
    public void mutateAtomically(Consumer<RunSnapshot> mutation) {
        synchronized (serialQueue) {
            requireCurrent();
            DurableRunMutation.Outcome<Boolean> outcome = persistCandidateLocked(candidate -> {
                mutation.accept(candidate);
                return true;
            }, Boolean.TRUE::equals);
            current = outcome.snapshot();
        }
    }

    public boolean spendResources(String key, java.util.Map<String, Integer> costs) {
        synchronized (serialQueue) {
            requireRunning();
            if (current.committedKeys.contains(key)) {
                return true;
            }
            for (var entry : costs.entrySet()) {
                if (current.resources.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                    return false;
                }
            }
            for (var entry : costs.entrySet()) {
                current.resources.compute(entry.getKey(), (ignored, value) -> Math.max(0, value - entry.getValue()));
            }
            commitEventLocked(key, "LEDGER_COMMITTED", "{\"domain\":\"CRAFT_COST\"}");
            saveUnchecked();
            return true;
        }
    }

    public boolean transactResources(String key, java.util.Map<String, Integer> costs, String eventType,
                                     String payload, Consumer<RunSnapshot> mutation) {
        synchronized (serialQueue) {
            requireRunning();
            DurableRunMutation.Outcome<Boolean> outcome = persistCandidateLocked(candidate -> {
                if (!ResourceLedger.reserveAndMutate(candidate, key, costs, mutation)) {
                    return false;
                }
                commitEvent(candidate, key, eventType, payload);
                return true;
            }, Boolean.TRUE::equals);
            if (outcome.persisted()) {
                current = outcome.snapshot();
            }
            return outcome.result();
        }
    }

    public ResourceLedger.ReserveResult reserveResourceTransaction(String transactionId, String costId,
            String targetId, ResourceLedger.Scope scope, String ownerUuid, java.util.Map<String, Integer> costs) {
        return reserveResourceTransaction(transactionId, costId, targetId, scope, ownerUuid, costs, run -> { });
    }

    public ResourceLedger.ReserveResult reserveResourceTransaction(String transactionId, String costId,
            String targetId, ResourceLedger.Scope scope, String ownerUuid, java.util.Map<String, Integer> costs,
            Consumer<RunSnapshot> reservationMutation) {
        synchronized (serialQueue) {
            requireRunning();
            long now = clockNowMillisLocked();
            DurableRunMutation.Outcome<ResourceLedger.ReserveResult> outcome = persistCandidateLocked(candidate -> {
                ResourceLedger.ReserveResult result = ResourceLedger.reserve(candidate, transactionId, costId,
                        targetId, scope, ownerUuid, costs, now);
                if (result == ResourceLedger.ReserveResult.RESERVED) {
                    reservationMutation.accept(candidate);
                }
                return result;
            }, result -> result == ResourceLedger.ReserveResult.RESERVED);
            if (outcome.persisted()) {
                current = outcome.snapshot();
                testTransactionPause.checkpoint(transactionId, TestTransactionPauseGate.Phase.RESERVED);
                RunSnapshot.ResourceTransactionState transaction = current.resourceTransactions.get(transactionId);
                reconcilePersonalResources(transaction);
            }
            return outcome.result();
        }
    }

    public boolean beginResourceTransaction(String transactionId) {
        return beginResourceTransaction(transactionId, run -> { });
    }

    public boolean beginResourceTransaction(String transactionId, Consumer<RunSnapshot> processingMutation) {
        synchronized (serialQueue) {
            requireRunning();
            if (testTransactionPause.blocks(transactionId)) return false;
            long now = clockNowMillisLocked();
            DurableRunMutation.Outcome<Boolean> outcome = persistCandidateLocked(candidate -> {
                boolean changed = ResourceLedger.beginProcessing(candidate, transactionId, now);
                if (changed) {
                    processingMutation.accept(candidate);
                }
                return changed;
            }, Boolean.TRUE::equals);
            if (outcome.persisted()) {
                current = outcome.snapshot();
                testTransactionPause.checkpoint(transactionId, TestTransactionPauseGate.Phase.PROCESSING);
            }
            return outcome.result();
        }
    }

    public boolean commitResourceTransaction(String transactionId, String eventType, String payload,
                                             Consumer<RunSnapshot> mutation) {
        synchronized (serialQueue) {
            requireRunning();
            if (testTransactionPause.blocks(transactionId)) return false;
            long now = clockNowMillisLocked();
            DurableRunMutation.Outcome<Boolean> outcome = persistCandidateLocked(candidate -> {
                boolean committed = ResourceLedger.commit(candidate, transactionId, now, mutation);
                if (committed) {
                    commitEvent(candidate, transactionId, eventType, payload);
                }
                return committed;
            }, Boolean.TRUE::equals);
            if (outcome.persisted()) {
                current = outcome.snapshot();
                RunSnapshot.ResourceTransactionState transaction = current.resourceTransactions.get(transactionId);
                reconcilePersonalResources(transaction);
            }
            return outcome.result();
        }
    }

    private void reconcilePersonalResources(RunSnapshot.ResourceTransactionState transaction) {
        if (transaction == null || !ResourceLedger.Scope.PERSONAL.name().equals(transaction.ledgerScope)
                || transaction.ownerUuid == null) return;
        RunSnapshot.PlayerState owner = current.players.get(transaction.ownerUuid);
        if (owner != null) {
            personalResourceReconciler.accept(transaction.ownerUuid,
                    java.util.Map.copyOf(owner.personalResources));
        }
    }

    public boolean cancelResourceReservation(String transactionId, String reason) {
        return cancelResourceReservation(transactionId, reason, run -> { });
    }

    public boolean cancelResourceReservation(String transactionId, String reason,
                                             Consumer<RunSnapshot> cancellationMutation) {
        synchronized (serialQueue) {
            requireRunning();
            DurableRunMutation.Outcome<Boolean> outcome = persistCandidateLocked(candidate -> {
                boolean cancelled = ResourceLedger.cancelReservation(candidate, transactionId, reason);
                if (cancelled) {
                    cancellationMutation.accept(candidate);
                }
                return cancelled;
            }, Boolean.TRUE::equals);
            if (outcome.persisted()) {
                current = outcome.snapshot();
                RunSnapshot.ResourceTransactionState transaction = current.resourceTransactions.get(transactionId);
                reconcilePersonalResources(transaction);
            }
            return outcome.result();
        }
    }

    public void armTestTransactionPause(String phase) {
        synchronized (serialQueue) {
            requireTestRun();
            testTransactionPause.arm(phase);
        }
    }

    public void clearTestTransactionPause() {
        synchronized (serialQueue) {
            requireTestRun();
            testTransactionPause.clear();
        }
    }

    public TestTransactionPauseGate.Status testTransactionPauseStatus() {
        synchronized (serialQueue) {
            requireTestRun();
            return testTransactionPause.status();
        }
    }

    public int addResource(String key, String resourceId, int amount) {
        synchronized (serialQueue) {
            requireRunning();
            if (current.committedKeys.contains(key)) {
                return current.resources.getOrDefault(resourceId, 0);
            }
            int next = Math.max(0, current.resources.getOrDefault(resourceId, 0) + amount);
            current.resources.put(resourceId, next);
            commitEventLocked(key, "LEDGER_COMMITTED", "{\"resource\":\"" + resourceId + "\",\"delta\":" + amount + ",\"balance\":" + next + "}");
            saveUnchecked();
            return next;
        }
    }

    public boolean withdrawResource(String key, String resourceId, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        synchronized (serialQueue) {
            requireRunning();
            if (!current.sharedLedgerUnlocked || current.facility == null || !current.facility.active) {
                return false;
            }
            if (current.committedKeys.contains(key)) {
                return true;
            }
            int balance = current.resources.getOrDefault(resourceId, 0);
            if (balance < amount) {
                return false;
            }
            current.resources.put(resourceId, balance - amount);
            commitEventLocked(key, "LEDGER_COMMITTED", "{\"resource\":\"" + resourceId
                    + "\",\"delta\":" + (-amount) + ",\"balance\":" + (balance - amount) + "}");
            saveUnchecked();
            return true;
        }
    }

    public boolean consumeAp(Player player, double amount) {
        synchronized (serialQueue) {
            RunSnapshot.PlayerState state = playerState(player.getUniqueId()).orElse(null);
            double adjusted = effectiveApCostLocked(state, amount);
            if (state == null || state.ap + 1.0e-6 < adjusted || !"ACTIVE".equals(state.lifeState)) {
                return false;
            }
            state.ap = Math.max(0.0, state.ap - adjusted);
            return true;
        }
    }

    public double effectiveApCost(Player player, double amount) {
        synchronized (serialQueue) {
            return effectiveApCostLocked(playerState(player.getUniqueId()).orElse(null), amount);
        }
    }

    public boolean canConsumeAp(Player player, double amount) {
        synchronized (serialQueue) {
            RunSnapshot.PlayerState state = playerState(player.getUniqueId()).orElse(null);
            double adjusted = effectiveApCostLocked(state, amount);
            return state != null && "ACTIVE".equals(state.lifeState) && state.ap + 1.0e-6 >= adjusted;
        }
    }

    public Optional<RunSnapshot> current() {
        synchronized (serialQueue) {
            return Optional.ofNullable(current);
        }
    }

    public boolean isTestRun() {
        synchronized (serialQueue) {
            return current != null && "TEST".equals(current.runType);
        }
    }

    public int effectivePartySize() {
        synchronized (serialQueue) {
            if (current == null) {
                return 0;
            }
            if ("TEST".equals(current.runType) && current.test != null) {
                return Math.max(1, Math.min(content.maximumPlayers(), current.test.virtualPartySize));
            }
            return Math.max(1, activeSurvivorCount());
        }
    }

    public long clockNowMillis() {
        synchronized (serialQueue) {
            return clockNowMillisLocked();
        }
    }

    public long clockTick() {
        synchronized (serialQueue) {
            if (current != null && "TEST".equals(current.runType) && current.test != null) {
                return current.test.logicalTick;
            }
            return Bukkit.getCurrentTick();
        }
    }

    public void stepTestClock(long ticks) {
        if (ticks < 1 || ticks > 72_000L) {
            throw new IllegalArgumentException("Step ticks must be 1 to 72000");
        }
        synchronized (serialQueue) {
            requireTestRun();
            current.test.logicalTick += ticks;
            current.test.logicalNowEpochMs += ticks * 50L;
            saveUnchecked();
        }
    }

    public void replaceCurrentTest(RunSnapshot replacement) {
        synchronized (serialQueue) {
            requireTestRun();
            if (replacement == null || !"TEST".equals(replacement.runType)
                    || !current.runId.equals(replacement.runId)
                    || !current.contentRevision.equals(replacement.contentRevision)) {
                throw new IllegalArgumentException("Snapshot does not belong to the active Test Lab run");
            }
            current = replacement;
            saveUnchecked();
            for (Player player : onlineMembers()) {
                restorePlayer(player);
            }
            if (loop != null) {
                loop.restoreWorldObjects();
            }
        }
    }

    public void clearCurrentTest() throws IOException {
        synchronized (serialQueue) {
            requireTestRun();
            if (!List.of("ENDED", "ABORTED").contains(current.state)) {
                throw new IllegalStateException("Test run must be stopped before it is cleared");
            }
            boolean restorePending = current.test.restorePending;
            current.test.restorePending = false;
            try {
                testRepository.archiveAndClear(current);
                current = null;
            } catch (IOException | RuntimeException exception) {
                // The on-disk current snapshot was never rewritten with restorePending=false.
                // Roll memory back as well so the owner can retry recovery in this process.
                current.test.restorePending = restorePending;
                throw exception;
            }
        }
    }

    public Optional<RunSnapshot.PlayerState> playerState(UUID uuid) {
        synchronized (serialQueue) {
            if (current == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(current.players.get(uuid.toString()));
        }
    }

    public boolean isMember(Player player) {
        synchronized (serialQueue) {
            return current != null && current.registeredPlayers.contains(player.getUniqueId().toString());
        }
    }

    public boolean isRunningMember(Player player) {
        synchronized (serialQueue) {
            return current != null && "RUNNING".equals(current.state) && isMember(player);
        }
    }

    public List<Player> onlineMembers() {
        synchronized (serialQueue) {
            if (current == null) {
                return List.of();
            }
            List<Player> result = new ArrayList<>();
            for (String uuid : current.registeredPlayers) {
                Player player = Bukkit.getPlayer(UUID.fromString(uuid));
                if (player != null && player.isOnline()) {
                    result.add(player);
                }
            }
            return result;
        }
    }

    public int activeSurvivorCount() {
        synchronized (serialQueue) {
            if (current == null) {
                return 0;
            }
            return (int) current.players.values().stream().filter(state -> "ACTIVE".equals(state.lifeState)).count();
        }
    }

    public int survivableCount() {
        synchronized (serialQueue) {
            if (current == null) {
                return 0;
            }
            return (int) current.players.values().stream().filter(state -> Set.of(
                    "ACTIVE", "DOWNED_GRACE", "DOWNED", "BEING_REVIVED").contains(state.lifeState)).count();
        }
    }

    public void mutateTransient(Consumer<RunSnapshot> mutation) {
        synchronized (serialQueue) {
            requireCurrent();
            mutation.accept(current);
        }
    }

    public void savePlayer(Player player) {
        synchronized (serialQueue) {
            playerState(player.getUniqueId()).ifPresent(state -> {
                state.lastKnownName = player.getName();
                captureLocation(state, player.getLocation());
                saveUnchecked();
            });
        }
    }

    public void restorePlayer(Player player) {
        synchronized (serialQueue) {
            playerState(player.getUniqueId()).ifPresent(state -> {
                state.lastKnownName = player.getName();
                player.setLevel(state.level);
                player.setExp(levelProgress(state));
                if ("DEAD".equals(state.lifeState) || "DEAD_PENDING".equals(state.lifeState)) {
                    player.setGameMode(GameMode.SPECTATOR);
                } else if ("DOWNED".equals(state.lifeState) || "DOWNED_GRACE".equals(state.lifeState)
                        || "BEING_REVIVED".equals(state.lifeState)) {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setHealth(Math.max(1.0, Math.min(player.getHealth(), 1.0)));
                    if (state.injuryStacks <= 0) state.injuryStacks = 1;
                    if (state.downedMaxHp <= 0.0) {
                        double maximum = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20.0
                                : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                        state.downedMaxHp = maximum * com.lsc.corp.wsplugin.combat.DeathRuntimePolicy
                                .downedHealthFraction(Math.min(3, state.injuryStacks));
                        state.downedHp = state.downedMaxHp;
                    }
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
                }
                equipment.syncAuthoritativeEquipment(player);
            });
        }
    }

    public String inspect() {
        synchronized (serialQueue) {
            if (current == null) {
                return "No Season 1 run";
            }
            long undelivered = current.outbox.stream().filter(event -> !event.delivered).count();
            return "run=" + current.runId + " type=" + current.runType + " state=" + current.state + " day=" + current.day
                    + " members=" + current.registeredPlayers.size() + " effectiveParty=" + effectivePartySize()
                    + " survivors=" + activeSurvivorCount()
                    + " revision=" + current.contentRevision + " version=" + current.version
                    + " outboxPending=" + undelivered + " resources=" + current.resources;
        }
    }

    public void forceAdvance() {
        if (loop == null) {
            throw new IllegalStateException("Prototype loop is not attached");
        }
        loop.forceAdvance();
    }

    public void startHeartbeat() {
        if (heartbeat != null) {
            return;
        }
        heartbeat = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long started = System.nanoTime();
            synchronized (serialQueue) {
                if (current != null && "RUNNING".equals(current.state)) {
                    advanceTestClockLocked();
                    tickAp();
                    loop.tick();
                    equipment.tick();
                    growth.tick();
                    if (--ticksUntilSave <= 0) {
                        autosaveUnchecked();
                        ticksUntilSave = autosaveTicks();
                    }
                }
            }
            telemetry.tick(System.nanoTime() - started);
        }, 1L, 1L);
    }

    public void shutdown() {
        acceptingCommands.set(false);
        synchronized (serialQueue) {
            if (heartbeat != null) {
                heartbeat.cancel();
                heartbeat = null;
            }
            if (current != null) {
                for (Player player : onlineMembers()) {
                    savePlayer(player);
                }
                saveUnchecked();
            }
        }
    }

    public void broadcast(String message) {
        for (Player player : onlineMembers()) {
            player.sendMessage(message);
        }
    }

    private void tickAp() {
        long now = Instant.now().toEpochMilli();
        for (RunSnapshot.PlayerState state : current.players.values()) {
            if (!"ACTIVE".equals(state.lifeState) || now < state.apRegenBlockedUntilEpochMs) {
                continue;
            }
            double perTick = loop != null && loop.isCombatActive() ? 5.0 / 20.0 : 12.0 / 20.0;
            if ("TEST".equals(current.runType)) {
                perTick *= clamp(state.testApRegenMultiplier, 0.0, 20.0);
            }
            if (growth != null) perTick += growth.apRegenBonusPerSecond(current, state, now) / 20.0;
            state.ap = Math.min(state.maxAp, state.ap + perTick);
        }
    }

    private float levelProgress(RunSnapshot.PlayerState state) {
        if (state.level >= 50) {
            return 1.0f;
        }
        int reached = GrowthService.cumulativeExpForLevel(state.level);
        int needed = GrowthService.nextLevelExp(state.level);
        return Math.max(0.0f, Math.min(1.0f, (state.exp - reached) / (float) needed));
    }

    private void commitEventLocked(String idempotencyKey, String type, String payload) {
        commitEvent(current, idempotencyKey, type, payload);
    }

    private void commitEvent(RunSnapshot snapshot, String idempotencyKey, String type, String payload) {
        appendEvent(snapshot, idempotencyKey, type, payload);
        telemetry.event(snapshot.runId, type, payload);
    }

    private static void appendEvent(RunSnapshot snapshot, String idempotencyKey, String type, String payload) {
        snapshot.committedKeys.add(idempotencyKey);
        RunSnapshot.OutboxEvent event = new RunSnapshot.OutboxEvent();
        event.eventId = UUID.nameUUIDFromBytes((snapshot.runId + ":" + idempotencyKey)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        event.type = type;
        event.payload = payload;
        event.createdAtEpochMs = Instant.now().toEpochMilli();
        snapshot.outbox.add(event);
        event.delivered = true;
    }

    private <T> DurableRunMutation.Outcome<T> persistCandidateLocked(Function<RunSnapshot, T> mutation,
                                                                      Predicate<T> shouldPersist) {
        try {
            return DurableRunMutation.execute(current, mutation, shouldPersist, activeRepositoryLocked()::save);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist run", exception);
        }
    }

    private void requireCurrent() {
        if (current == null) {
            throw new IllegalStateException("No run exists");
        }
    }

    private void requireRunning() {
        requireCurrent();
        if (!"RUNNING".equals(current.state)) {
            throw new IllegalStateException("Run is not running");
        }
    }

    private void requireTestRun() {
        requireCurrent();
        if (!"TEST".equals(current.runType) || current.test == null) {
            throw new IllegalStateException("An active Test Lab run is required");
        }
    }

    private void saveLocked() throws IOException {
        activeRepositoryLocked().save(current);
    }

    private void saveUnchecked() {
        try {
            activeRepositoryLocked().save(current);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist run", exception);
        }
    }

    private void autosaveUnchecked() {
        try {
            activeRepositoryLocked().saveIfChanged(current);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist run autosave", exception);
        }
    }

    private RunRepository activeRepositoryLocked() {
        if (current == null) throw new IllegalStateException("No run exists");
        return switch (current.runType) {
            case "SEASON_1" -> seasonRepository;
            case "PROTOTYPE" -> legacyPrototypeRepository;
            case "TEST" -> testRepository;
            default -> throw new IllegalStateException("Unsupported run type " + current.runType);
        };
    }

    private long clockNowMillisLocked() {
        if (current != null && "TEST".equals(current.runType) && current.test != null) {
            return current.test.logicalNowEpochMs;
        }
        return Instant.now().toEpochMilli();
    }

    private double effectiveApCostLocked(RunSnapshot.PlayerState state, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("AP cost must be finite and non-negative");
        }
        return state != null && current != null && "TEST".equals(current.runType)
                ? amount * clamp(state.testApCostMultiplier, 0.0, 10.0) : amount;
    }

    private void advanceTestClockLocked() {
        if (current == null || !"TEST".equals(current.runType) || current.test == null || current.test.timeFrozen) {
            return;
        }
        double scale = clamp(current.test.timeScale, 0.05, 100.0);
        current.test.logicalNowEpochMs += Math.max(1L, Math.round(50.0 * scale));
        current.test.logicalTick += Math.max(1L, Math.round(scale));
    }

    private int autosaveTicks() {
        return plugin.getConfig().getInt("season.autosave-ticks",
                plugin.getConfig().getInt("prototype.autosave-ticks", 100));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void captureLocation(RunSnapshot.PlayerState state, Location location) {
        state.world = location.getWorld() == null ? null : location.getWorld().getName();
        state.x = location.getX();
        state.y = location.getY();
        state.z = location.getZ();
        state.yaw = location.getYaw();
        state.pitch = location.getPitch();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
