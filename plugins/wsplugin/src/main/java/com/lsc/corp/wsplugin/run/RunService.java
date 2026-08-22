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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class RunService {
    private final JavaPlugin plugin;
    private final RunRepository repository;
    private final PrototypeContent content;
    private final TelemetryService telemetry;
    private final Object serialQueue = new Object();
    private final AtomicBoolean acceptingCommands = new AtomicBoolean(true);
    private RunSnapshot current;
    private BukkitTask heartbeat;
    private PrototypeLoopService loop;
    private EquipmentService equipment;
    private GrowthService growth;
    private int ticksUntilSave;

    public RunService(JavaPlugin plugin, RunRepository repository, PrototypeContent content, TelemetryService telemetry) {
        this.plugin = plugin;
        this.repository = repository;
        this.content = content;
        this.telemetry = telemetry;
        this.ticksUntilSave = plugin.getConfig().getInt("prototype.autosave-ticks", 100);
        plugin.saveDefaultConfig();
    }

    public void attach(PrototypeLoopService loop, EquipmentService equipment, GrowthService growth) {
        this.loop = loop;
        this.equipment = equipment;
        this.growth = growth;
    }

    public void restore() throws IOException {
        synchronized (serialQueue) {
            current = repository.load().orElse(null);
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
            throw new IllegalArgumentException("Prototype run requires 2 to 4 players");
        }
        synchronized (serialQueue) {
            if (current != null && !List.of("ENDED", "ABORTED").contains(current.state)) {
                throw new IllegalStateException("A run already exists: " + current.runId);
            }
            RunSnapshot snapshot = new RunSnapshot();
            snapshot.runId = "proto-" + UUID.randomUUID();
            snapshot.contentRevision = content.contentRevision();
            snapshot.createdAtEpochMs = Instant.now().toEpochMilli();
            snapshot.checkpointStartedAtEpochMs = snapshot.createdAtEpochMs;
            snapshot.seed = UUID.fromString(snapshot.runId.substring("proto-".length())).getMostSignificantBits();
            for (PrototypeContent.ResourceDefinition definition : content.resources()) {
                snapshot.resources.put(definition.id(), 0);
            }
            for (Player player : players) {
                String uuid = player.getUniqueId().toString();
                snapshot.registeredPlayers.add(uuid);
                RunSnapshot.PlayerState state = new RunSnapshot.PlayerState();
                state.uuid = uuid;
                state.lastKnownName = player.getName();
                snapshot.players.put(uuid, state);
                captureLocation(state, player.getLocation());
            }
            current = snapshot;
            commitEventLocked("create:" + snapshot.runId, "RUN_CREATED", "{\"members\":" + players.size() + "}");
            saveLocked();
            return snapshot;
        }
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
            current.startedAtEpochMs = Instant.now().toEpochMilli();
            current.checkpointStartedAtEpochMs = current.startedAtEpochMs;
            commitEventLocked("start:" + current.runId, "RUN_STARTED", "{\"day\":1}");
            saveLocked();
            for (Player player : online) {
                player.setLevel(1);
                player.setExp(0);
                equipment.syncAuthoritativeEquipment(player);
            }
            loop.startRunWorld();
            broadcast(ChatColor.GOLD + "[WildSurvival] 프로토타입 회차가 시작되었습니다. Day 1");
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
            telemetry.audit(current.runId, actor, "prototype.stop", reason);
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
            current.state = "ENDED";
            current.endReason = reason;
            if (loop != null) {
                loop.cleanupWorldObjects();
            }
            commitEventLocked("complete:" + current.runId, "RUN_ENDED", "{\"result\":\"PROTOTYPE_COMPLETE\"}");
            saveLocked();
            telemetry.sessionReport(current);
            broadcast(ChatColor.GREEN + "[WildSurvival] 프로토타입 완주: " + reason);
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

    public void mutate(Consumer<RunSnapshot> mutation) {
        synchronized (serialQueue) {
            requireCurrent();
            mutation.accept(current);
            saveUnchecked();
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
            if (!ResourceLedger.reserveAndMutate(current, key, costs, mutation)) {
                return false;
            }
            commitEventLocked(key, eventType, payload);
            saveUnchecked();
            return true;
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

    public boolean consumeAp(Player player, double amount) {
        synchronized (serialQueue) {
            RunSnapshot.PlayerState state = playerState(player.getUniqueId()).orElse(null);
            if (state == null || state.ap + 1.0e-6 < amount || !"ACTIVE".equals(state.lifeState)) {
                return false;
            }
            state.ap = Math.max(0.0, state.ap - amount);
            return true;
        }
    }

    public Optional<RunSnapshot> current() {
        synchronized (serialQueue) {
            return Optional.ofNullable(current);
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
            return (int) current.players.values().stream().filter(state -> !"DEAD".equals(state.lifeState)).count();
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
                if ("DEAD".equals(state.lifeState)) {
                    player.setGameMode(GameMode.SPECTATOR);
                } else if ("DOWNED".equals(state.lifeState)) {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setHealth(Math.max(1.0, Math.min(player.getHealth(), 1.0)));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 9, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
                }
                equipment.syncAuthoritativeEquipment(player);
            });
        }
    }

    public String inspect() {
        synchronized (serialQueue) {
            if (current == null) {
                return "No prototype run";
            }
            long undelivered = current.outbox.stream().filter(event -> !event.delivered).count();
            return "run=" + current.runId + " state=" + current.state + " day=" + current.day
                    + " members=" + current.registeredPlayers.size() + " survivors=" + activeSurvivorCount()
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
                    tickAp();
                    loop.tick();
                    equipment.tick();
                    growth.tick();
                    if (--ticksUntilSave <= 0) {
                        saveUnchecked();
                        ticksUntilSave = plugin.getConfig().getInt("prototype.autosave-ticks", 100);
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
        current.committedKeys.add(idempotencyKey);
        RunSnapshot.OutboxEvent event = new RunSnapshot.OutboxEvent();
        event.eventId = UUID.nameUUIDFromBytes((current.runId + ":" + idempotencyKey).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        event.type = type;
        event.payload = payload;
        event.createdAtEpochMs = Instant.now().toEpochMilli();
        current.outbox.add(event);
        telemetry.event(current.runId, type, payload);
        event.delivered = true;
    }

    private void requireCurrent() {
        if (current == null) {
            throw new IllegalStateException("No prototype run exists");
        }
    }

    private void requireRunning() {
        requireCurrent();
        if (!"RUNNING".equals(current.state)) {
            throw new IllegalStateException("Prototype run is not running");
        }
    }

    private void saveLocked() throws IOException {
        repository.save(current);
    }

    private void saveUnchecked() {
        try {
            repository.save(current);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist prototype run", exception);
        }
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
