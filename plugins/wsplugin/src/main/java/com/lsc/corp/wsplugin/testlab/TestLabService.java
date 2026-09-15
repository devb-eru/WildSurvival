package com.lsc.corp.wsplugin.testlab;

import com.lsc.corp.wsplugin.combat.DeathRuntimePolicy;
import com.lsc.corp.wsplugin.boss.PrototypeBossService;
import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.economy.ResourceLedger;
import com.lsc.corp.wsplugin.facility.FacilityService;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.player.PlayerStatPolicy;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.world.PrototypeLoopService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class TestLabService implements Listener {
    private static final Set<String> BUILT_IN_PRESETS = Set.of(
            "DEFAULT", "GLASS-CANNON", "TANK", "NO-COOLDOWN", "SURVIVABILITY", "PARTY-4");
    private final JavaPlugin plugin;
    private final RunService runs;
    private final TestLabRepository repository;
    private final PrototypeContent content;
    private final ProductionContentCatalog production;
    private final FacilityService facility;
    private final EquipmentService equipment;
    private final GrowthService growth;
    private final CombatService combat;
    private final PrototypeBossService boss;
    private final PrototypeLoopService loop;
    private final TelemetryService telemetry;
    private final NamespacedKey testItemKey;
    private final NamespacedKey testSessionKey;

    public TestLabService(JavaPlugin plugin, RunService runs, TestLabRepository repository,
                          PrototypeContent content, ProductionContentCatalog production,
                          FacilityService facility, EquipmentService equipment, GrowthService growth,
                          CombatService combat, PrototypeBossService boss, PrototypeLoopService loop,
                          TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.repository = repository;
        this.content = content;
        this.production = production;
        this.facility = facility;
        this.equipment = equipment;
        this.growth = growth;
        this.combat = combat;
        this.boss = boss;
        this.loop = loop;
        this.telemetry = telemetry;
        this.testItemKey = new NamespacedKey(plugin, "test_item");
        this.testSessionKey = new NamespacedKey(plugin, "test_session");
    }

    public RunSnapshot enter(Player player, long seed, int virtualPartySize) throws IOException {
        requireEnabled();
        if (runs.current().map(run -> !List.of("ENDED", "ABORTED").contains(run.state)).orElse(false)) {
            throw new IllegalStateException("Stop the active run before entering Test Lab");
        }
        repository.saveBackup(TestPlayerBackup.capture(player));
        RunSnapshot snapshot = runs.createTest(player, seed, virtualPartySize);
        try {
            runs.start();
            loop.cleanupWorldObjects();
            resetState(player, false);
            applyPreset(player, builtInPreset("DEFAULT"), false);
            checkpoint(player, "SESSION_ENTER");
            audit(player, "session.enter", "none", summary());
            player.sendMessage(ChatColor.GREEN + "[Test Lab] 격리 테스트 회차에 입장했습니다. /ws test gui");
            return runs.current().orElseThrow();
        } catch (Exception exception) {
            restoreBackupAfterFailure(player);
            throw exception;
        }
    }

    public void exit(Player player, String reason) throws IOException {
        requireSessionOwner(player);
        RunSnapshot run = runs.current().orElseThrow();
        String runId = run.runId;
        String before = summary();
        TestPlayerBackup backup = repository.loadBackup(player.getUniqueId().toString())
                .orElseThrow(() -> new IOException("Test Lab player backup is missing"));
        if (!List.of("ENDED", "ABORTED").contains(run.state)) {
            runs.stop(reason, player.getName());
        }
        loop.cleanupWorldObjects();
        backup.restore(player);
        repository.audit(runId, player.getUniqueId().toString(), "session.exit", before, reason);
        runs.clearCurrentTest();
        cleanupExitedSessionArtifacts(runId, player.getUniqueId().toString());
        player.sendMessage(ChatColor.GREEN + "[Test Lab] 테스트 상태를 폐기하고 입장 전 상태를 복원했습니다.");
    }

    public TestLabSnapshot checkpoint(Player actor, String reason) throws IOException {
        requireOwner(actor);
        RunSnapshot run = runs.current().orElseThrow();
        TestLabSnapshot saved = repository.snapshot(run, actor.getUniqueId().toString(), reason,
                plugin.getConfig().getInt("test-lab.max-snapshots", 20), TestPlayerBackup.capture(actor));
        runs.mutate(ignored -> { });
        repository.audit(run.runId, actor.getUniqueId().toString(), "snapshot.create", reason, saved.snapshotId);
        return saved;
    }

    public TestLabSnapshot undo(Player actor) throws IOException {
        requireSessionOwner(actor);
        RunSnapshot current = runs.current().orElseThrow();
        TestLabSnapshot snapshot = repository.latestSnapshot(current.runId)
                .orElseThrow(() -> new IllegalStateException("No Test Lab snapshot is available"));
        String before = summary();
        loop.cleanupWorldObjects();
        runs.replaceCurrentTest(repository.deepCopy(snapshot.run));
        if (snapshot.player != null) {
            snapshot.player.restore(actor);
        }
        applyRuntimePlayerState(actor);
        repository.audit(current.runId, actor.getUniqueId().toString(), "snapshot.undo", before, snapshot.snapshotId);
        repository.deleteSnapshot(current.runId, snapshot.snapshotId);
        return snapshot;
    }

    public void reset(Player actor) throws IOException {
        requireOwner(actor);
        checkpoint(actor, "BEFORE_RESET");
        String before = summary();
        loop.cleanupWorldObjects();
        resetState(actor, true);
        repository.audit(runs.current().orElseThrow().runId, actor.getUniqueId().toString(), "session.reset", before, summary());
    }

    public void setLevel(Player actor, int level) throws IOException {
        TestValuePolicy.integerInRange("level", level, 1, 50);
        beforeMutation(actor, "player.level");
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = state(run, actor);
            state.level = level;
            state.exp = GrowthService.cumulativeExpForLevel(level);
        });
        runs.restorePlayer(actor);
        afterMutation(actor, "player.level", "level=" + level);
    }

    public void setExperience(Player actor, int experience) throws IOException {
        TestValuePolicy.integerInRange("experience", experience, 0, 2_000_000_000);
        beforeMutation(actor, "player.exp");
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = state(run, actor);
            state.exp = experience;
            state.level = GrowthService.levelForExp(experience);
        });
        runs.restorePlayer(actor);
        afterMutation(actor, "player.exp", "exp=" + experience);
    }

    public void setHealth(Player actor, double health) throws IOException {
        requireOwner(actor);
        double maximum = actor.getAttribute(Attribute.MAX_HEALTH).getValue();
        TestValuePolicy.finiteInRange("health", health, 0.1, maximum);
        beforeMutation(actor, "player.health");
        actor.setHealth(health);
        afterMutation(actor, "player.health", "health=" + health);
    }

    public void heal(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "player.heal");
        actor.setHealth(actor.getAttribute(Attribute.MAX_HEALTH).getValue());
        actor.setFoodLevel(20);
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = state(run, actor);
            state.lifeState = "ACTIVE";
            state.ap = state.maxAp;
        });
        actor.setGameMode(GameMode.SURVIVAL);
        clearPlayerStatuses(actor, false);
        afterMutation(actor, "player.heal", "full");
    }

    public void setAp(Player actor, double ap) throws IOException {
        requireOwner(actor);
        RunSnapshot.PlayerState state = playerState(actor);
        TestValuePolicy.finiteInRange("ap", ap, 0.0, state.maxAp);
        beforeMutation(actor, "player.ap");
        runs.mutate(run -> state(run, actor).ap = ap);
        afterMutation(actor, "player.ap", "ap=" + ap);
    }

    public void setLifeState(Player actor, String requested) throws IOException {
        requireOwner(actor);
        String life = requested.toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "DOWNED", "DEAD").contains(life)) {
            throw new IllegalArgumentException("Life state must be ACTIVE, DOWNED, or DEAD");
        }
        beforeMutation(actor, "player.life");
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = state(run, actor);
            state.lifeState = life;
            state.downedAtEpochMs = "DOWNED".equals(life) ? runs.clockNowMillis() : 0L;
            if ("DOWNED".equals(life)) {
                state.injuryStacks = Math.max(1, Math.min(DeathRuntimePolicy.MAX_INJURY_STACKS, state.injuryStacks));
                double maximum = actor.getAttribute(Attribute.MAX_HEALTH).getValue();
                state.downedMaxHp = maximum * DeathRuntimePolicy.downedHealthFraction(state.injuryStacks);
                state.downedHp = state.downedMaxHp;
                state.ap = 0.0;
            } else {
                state.downedGraceUntilEpochMs = 0L;
                state.downedHp = 0.0;
                state.downedMaxHp = 0.0;
            }
        });
        if ("DEAD".equals(life)) {
            actor.setGameMode(GameMode.SPECTATOR);
            actor.removePotionEffect(PotionEffectType.SLOWNESS);
            actor.removePotionEffect(PotionEffectType.GLOWING);
        } else {
            actor.setGameMode(GameMode.SURVIVAL);
            actor.setHealth("DOWNED".equals(life) ? 1.0 : Math.max(1.0,
                    actor.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.25));
            if ("DOWNED".equals(life)) {
                actor.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 4, false, false));
                actor.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            } else {
                actor.removePotionEffect(PotionEffectType.SLOWNESS);
                actor.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
        afterMutation(actor, "player.life", life);
    }

    public void setPlayerStat(Player actor, String rawStat, double rawValue) throws IOException {
        requireOwner(actor);
        String stat = TestValuePolicy.statId(rawStat);
        double value = TestValuePolicy.statValue(stat, rawValue);
        beforeMutation(actor, "player.stat." + stat);
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = state(run, actor);
            switch (stat) {
                case "damage-dealt" -> state.testDamageDealtMultiplier = value;
                case "break" -> state.testBreakMultiplier = value;
                case "damage-taken" -> state.testDamageTakenMultiplier = value;
                case "damage-reduction" -> state.testDamageReductionRate = value;
                case "cooldown" -> state.testCooldownMultiplier = value;
                case "ap-cost" -> state.testApCostMultiplier = value;
                case "ap-regen" -> state.testApRegenMultiplier = value;
                case "move-speed" -> state.testMoveSpeedMultiplier = value;
                case "max-health" -> state.testMaxHealth = value;
                case "max-ap" -> {
                    state.testBaseMaxAp = (int) Math.round(value);
                    state.maxAp = state.testBaseMaxAp;
                    state.ap = Math.min(state.ap, state.maxAp);
                }
                default -> throw new IllegalArgumentException("Unsupported stat " + stat);
            }
        });
        applyRuntimePlayerState(actor);
        afterMutation(actor, "player.stat." + stat, Double.toString(value));
    }

    public void setInvulnerable(Player actor, boolean value) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "player.invulnerable");
        runs.mutate(run -> state(run, actor).testInvulnerable = value);
        afterMutation(actor, "player.invulnerable", Boolean.toString(value));
    }

    public void addPlayerStatus(Player actor, String rawType, int durationTicks, int amplifier) throws IOException {
        requireOwner(actor);
        TestValuePolicy.integerInRange("durationTicks", durationTicks, 1, 1_728_000);
        TestValuePolicy.integerInRange("amplifier", amplifier, 0, 255);
        PotionEffectType type = potionType(rawType);
        beforeMutation(actor, "player.status.add");
        actor.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, true, true));
        afterMutation(actor, "player.status.add", type.getKey() + ":" + durationTicks + ":" + amplifier);
    }

    public void clearPlayerStatuses(Player actor, boolean snapshot) throws IOException {
        requireOwner(actor);
        if (snapshot) {
            beforeMutation(actor, "player.status.clear");
        }
        new ArrayList<>(actor.getActivePotionEffects()).forEach(effect -> actor.removePotionEffect(effect.getType()));
        if (snapshot) {
            afterMutation(actor, "player.status.clear", "all");
        }
    }

    public int setResource(Player actor, String rawId, int amount) throws IOException {
        requireOwner(actor);
        String id = rawId.toUpperCase(Locale.ROOT);
        content.resources().stream().filter(resource -> resource.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown resource " + rawId));
        TestValuePolicy.integerInRange("resource amount", amount, 0, 1_000_000_000);
        beforeMutation(actor, "resource.set");
        runs.mutate(run -> run.resources.put(id, amount));
        afterMutation(actor, "resource.set", id + "=" + amount);
        return amount;
    }

    public void fillResources(Player actor, int amount) throws IOException {
        requireOwner(actor);
        TestValuePolicy.integerInRange("resource amount", amount, 0, 1_000_000_000);
        beforeMutation(actor, "resource.fill");
        runs.mutate(run -> content.resources().forEach(resource -> run.resources.put(resource.id(), amount)));
        afterMutation(actor, "resource.fill", "all=" + amount);
    }

    public int setLedgerResource(Player actor, String rawScope, String rawId, int amount) throws IOException {
        requireOwner(actor);
        ResourceLedger.Scope scope = ledgerScope(rawScope);
        String id = rawId.toUpperCase(Locale.ROOT);
        if (!production.materialsById().containsKey(id)) {
            throw new IllegalArgumentException("Unknown production material " + rawId);
        }
        TestValuePolicy.integerInRange("ledger resource amount", amount, 0, 1_000_000_000);
        beforeMutation(actor, "ledger.resource.set");
        runs.mutate(run -> ledger(run, actor, scope).put(id, amount));
        afterMutation(actor, "ledger.resource.set", scope + ":" + id + "=" + amount);
        return amount;
    }

    public void fillLedgerResources(Player actor, String rawScope, int amount) throws IOException {
        requireOwner(actor);
        ResourceLedger.Scope scope = ledgerScope(rawScope);
        TestValuePolicy.integerInRange("ledger resource amount", amount, 0, 1_000_000_000);
        beforeMutation(actor, "ledger.resource.fill");
        runs.mutate(run -> {
            Map<String, Integer> balance = ledger(run, actor, scope);
            production.materialsById().keySet().forEach(id -> balance.put(id, amount));
        });
        afterMutation(actor, "ledger.resource.fill", scope + ":all=" + amount);
    }

    public Map<String, Integer> ledgerView(Player actor, String rawScope) {
        requireOwner(actor);
        ResourceLedger.Scope scope = ledgerScope(rawScope);
        return Map.copyOf(ledger(runs.current().orElseThrow(), actor, scope));
    }

    public void setResearchState(Player actor, String rawId, String rawState) throws IOException {
        requireOwner(actor);
        String id = rawId.toUpperCase(Locale.ROOT);
        if (!production.researchById().containsKey(id)) {
            throw new IllegalArgumentException("Unknown research " + rawId);
        }
        String state = rawState.toUpperCase(Locale.ROOT);
        if (!Set.of("HIDDEN", "OBSERVABLE", "HYPOTHESIZED", "READY", "QUEUED", "PROCESSING",
                "PAUSED", "ANALYZED", "UNLOCKED", "MASTERED").contains(state)) {
            throw new IllegalArgumentException("Unknown research state " + rawState);
        }
        beforeMutation(actor, "research.state");
        runs.mutate(run -> {
            RunSnapshot.ResearchNodeState value = run.researchNodes.computeIfAbsent(id, ignored -> {
                RunSnapshot.ResearchNodeState created = new RunSnapshot.ResearchNodeState();
                created.researchId = id;
                return created;
            });
            value.state = state;
        });
        afterMutation(actor, "research.state", id + "=" + state);
    }

    public String placeFacility(Player actor, String facilityId, int level) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "facility.place");
        String instanceId = facility.placeForTest(actor, facilityId, level);
        afterMutation(actor, "facility.place", instanceId);
        return instanceId;
    }

    public void armTransactionPause(Player actor, String phase) throws IOException {
        requireOwner(actor);
        runs.armTestTransactionPause(phase);
        audit(actor, "fault.transaction.arm", "none", phase.toUpperCase(Locale.ROOT));
    }

    public void clearTransactionPause(Player actor) throws IOException {
        requireOwner(actor);
        String before = String.valueOf(runs.testTransactionPauseStatus());
        runs.clearTestTransactionPause();
        audit(actor, "fault.transaction.clear", before, "clear");
    }

    public Object transactionPauseStatus(Player actor) {
        requireOwner(actor);
        return runs.testTransactionPauseStatus();
    }

    public Map<String, Object> latestCraftTransaction(Player actor) {
        requireOwner(actor);
        RunSnapshot.ResourceTransactionState transaction = runs.current().orElseThrow()
                .resourceTransactions.values().stream()
                .filter(candidate -> "CRAFT".equals(candidate.transactionKind))
                .filter(candidate -> actor.getUniqueId().toString().equals(candidate.ownerUuid))
                .max(java.util.Comparator.comparingLong(candidate -> candidate.validatedAtEpochMs))
                .orElse(null);
        Map<String, Object> view = new LinkedHashMap<>();
        if (transaction == null) {
            view.put("craftTransaction", "NONE");
            return view;
        }
        view.put("transactionId", transaction.transactionId);
        view.put("state", transaction.state);
        view.put("recipeId", transaction.costId);
        view.put("reservedResources", transaction.reservedResources);
        view.put("inputSignature", transaction.inputSignature);
        view.put("outputSignature", transaction.outputSignature);
        view.put("output", transaction.outputType + ":" + transaction.outputId
                + " x" + transaction.outputAmount);
        view.put("outputInstanceId", transaction.outputInstanceId == null
                ? "-" : transaction.outputInstanceId);
        return view;
    }

    private static ResourceLedger.Scope ledgerScope(String rawScope) {
        try {
            return ResourceLedger.Scope.valueOf(rawScope.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("Ledger scope must be PERSONAL or SHARED");
        }
    }

    private static Map<String, Integer> ledger(RunSnapshot run, Player actor, ResourceLedger.Scope scope) {
        return scope == ResourceLedger.Scope.SHARED
                ? run.resources : run.players.get(actor.getUniqueId().toString()).personalResources;
    }

    public void setEquipment(Player actor, String rawWeapon, boolean equipNow) throws IOException {
        requireOwner(actor);
        String id = rawWeapon.toUpperCase(Locale.ROOT);
        if (!"UNARMED".equals(id)
                && content.weapons().stream().noneMatch(weapon -> weapon.id().equals(id))
                && !production.equipmentById().containsKey(id)) {
            throw new IllegalArgumentException("Unknown equipment " + rawWeapon);
        }
        beforeMutation(actor, "equipment.set");
        if (equipNow) {
            equipment.equipForTest(actor, id);
        } else {
            equipment.setEquipmentOwned(actor, id, true);
        }
        afterMutation(actor, "equipment.set", id + ":equip=" + equipNow);
    }

    public void removeEquipment(Player actor, String rawWeapon) throws IOException {
        requireOwner(actor);
        String id = rawWeapon.toUpperCase(Locale.ROOT);
        beforeMutation(actor, "equipment.remove");
        equipment.setEquipmentOwned(actor, id, false);
        afterMutation(actor, "equipment.remove", id);
    }

    public void clearLoadout(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "equipment.clear");
        equipment.clearTestLoadout(actor);
        afterMutation(actor, "equipment.clear", "all");
    }

    public void setQuickItem(Player actor, String rawId, int amount) throws IOException {
        requireOwner(actor);
        String id = rawId.toUpperCase(Locale.ROOT);
        TestValuePolicy.identifier(id);
        beforeMutation(actor, "item.quick");
        equipment.setQuickItem(actor, id, amount);
        afterMutation(actor, "item.quick", id + "=" + amount);
    }

    public void giveVanillaItem(Player actor, String rawMaterial, int amount) throws IOException {
        requireOwner(actor);
        TestValuePolicy.integerInRange("item amount", amount, 1, 64 * 36);
        Material material = Material.matchMaterial(rawMaterial);
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException("Unknown material " + rawMaterial);
        }
        beforeMutation(actor, "item.vanilla.give");
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(material.getMaxStackSize(), remaining);
            ItemStack item = new ItemStack(material, stackAmount);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(testItemKey, PersistentDataType.BYTE, (byte) 1);
                meta.getPersistentDataContainer().set(testSessionKey, PersistentDataType.STRING,
                        runs.current().orElseThrow().runId);
                item.setItemMeta(meta);
            }
            actor.getInventory().addItem(item).values().forEach(leftover ->
                    actor.getWorld().dropItemNaturally(actor.getLocation(), leftover));
            remaining -= stackAmount;
        }
        afterMutation(actor, "item.vanilla.give", material + "x" + amount);
    }

    public int clearTestItems(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "item.test.clear");
        int removed = 0;
        for (int slot = 0; slot < actor.getInventory().getSize(); slot++) {
            ItemStack item = actor.getInventory().getItem(slot);
            if (isTestItem(item)) {
                removed += item.getAmount();
                actor.getInventory().setItem(slot, null);
            }
        }
        afterMutation(actor, "item.test.clear", "removed=" + removed);
        return removed;
    }

    public void setPersonalAugment(Player actor, String augmentId, boolean present) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "augment.personal");
        growth.setPersonalAugmentForTest(actor, augmentId.toUpperCase(Locale.ROOT), present);
        afterMutation(actor, "augment.personal", augmentId + "=" + present);
    }

    public void clearPersonalAugments(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "augment.personal.clear");
        growth.clearPersonalAugmentsForTest(actor);
        afterMutation(actor, "augment.personal.clear", "all");
    }

    public void setPartyAugment(Player actor, String augmentId) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "augment.party");
        growth.setPartyAugmentForTest(augmentId.toUpperCase(Locale.ROOT));
        afterMutation(actor, "augment.party", augmentId);
    }

    public void clearPartyAugment(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "augment.party.clear");
        growth.clearPartyAugmentForTest();
        afterMutation(actor, "augment.party.clear", "none");
    }

    public void redrawPersonalAugment(Player actor, int milestone) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "augment.redraw");
        growth.resetPersonalDrawForTest(actor, milestone);
        afterMutation(actor, "augment.redraw", Integer.toString(milestone));
    }

    public LivingEntity spawnEnemy(Player actor, String enemyId, int count) throws IOException {
        requireOwner(actor);
        TestValuePolicy.integerInRange("enemy count", count, 1, 100);
        String normalizedId = enemyId.toUpperCase(Locale.ROOT);
        ProductionContentCatalog.EnemyEntry productionEnemy = production.enemiesById().get(normalizedId);
        PrototypeContent.EnemyDefinition definition = productionEnemy == null ? content.enemy(normalizedId) : null;
        beforeMutation(actor, "mob.spawn");
        LivingEntity first = null;
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0 * index / Math.max(1, count);
            Location location = actor.getLocation().clone().add(
                    actor.getLocation().getDirection().setY(0).normalize().multiply(6.0))
                    .add(Math.cos(angle) * 2.0, 0.0, Math.sin(angle) * 2.0);
            location.setY(location.getWorld().getHighestBlockYAt(location) + 1.0);
            LivingEntity entity = productionEnemy == null
                    ? combat.spawnEnemy(definition, location)
                    : combat.spawnProductionEnemy(productionEnemy.id(), location, 1.0);
            entity.setRemoveWhenFarAway(false);
            if (first == null) {
                first = entity;
            }
        }
        afterMutation(actor, "mob.spawn", normalizedId + "x" + count);
        return first;
    }

    public LivingEntity spawnBoss(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "boss.spawn");
        loop.cleanupWorldObjects();
        runs.mutate(run -> {
            run.day = 10;
            run.boss = null;
            run.committedKeys.removeIf(key -> key.startsWith("boss-"));
        });
        LivingEntity spawned = boss.spawn();
        afterMutation(actor, "boss.spawn", content.boss().id());
        return spawned;
    }

    public LivingEntity target(Player actor) {
        requireOwner(actor);
        return combat.targetedCombatEntity(actor, 64.0)
                .orElseThrow(() -> new IllegalStateException("Look at a WildSurvival combat entity within 64 blocks"));
    }

    public CombatService.CombatEntityView inspectTarget(Player actor) {
        return combat.inspectCombatEntity(target(actor));
    }

    public CombatService.TestProductionActionView armTargetAttack(Player actor, String mode) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "mob.attack." + mode.toLowerCase(Locale.ROOT));
        CombatService.TestProductionActionView view = combat.armTestProductionEnemyAction(target(actor), actor, mode);
        afterMutation(actor, "mob.attack." + mode.toLowerCase(Locale.ROOT),
                view.enemyId() + ":" + view.actionId());
        return view;
    }

    public void setTargetNumber(Player actor, String stat, double value) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "mob.stat." + stat);
        LivingEntity entity = target(actor);
        combat.setCombatEntityNumber(entity, stat, value);
        afterMutation(actor, "mob.stat." + stat, entity.getUniqueId() + "=" + value);
    }

    public void setTargetFlag(Player actor, String flag, boolean value) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "mob.flag." + flag);
        LivingEntity entity = target(actor);
        combat.setCombatEntityFlag(entity, flag, value);
        afterMutation(actor, "mob.flag." + flag, entity.getUniqueId() + "=" + value);
    }

    public void addTargetStatus(Player actor, String status, int durationTicks, int amplifier) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "mob.status.add");
        LivingEntity entity = target(actor);
        combat.applyTestStatus(entity, status, durationTicks, amplifier);
        afterMutation(actor, "mob.status.add", status + ":" + durationTicks + ":" + amplifier);
    }

    public void clearTargetStatuses(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "mob.status.clear");
        LivingEntity entity = target(actor);
        combat.clearTestStatuses(entity);
        afterMutation(actor, "mob.status.clear", entity.getUniqueId().toString());
    }

    public void removeTarget(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "mob.remove");
        LivingEntity entity = target(actor);
        String id = entity.getUniqueId().toString();
        entity.remove();
        runs.mutate(run -> {
            if (run.boss != null && id.equals(run.boss.entityUuid)) {
                run.boss = null;
            }
        });
        afterMutation(actor, "mob.remove", id);
    }

    public int clearMobs(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "mob.clear");
        int count = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (combat.isManagedCombatEntity(entity)) {
                    entity.remove();
                    count++;
                }
            }
        }
        combat.cleanupCombatEntities();
        runs.mutate(run -> run.boss = null);
        afterMutation(actor, "mob.clear", "removed=" + count);
        return count;
    }

    public void setDay(Player actor, int day) throws IOException {
        requireOwner(actor);
        TestValuePolicy.integerInRange("Season 1 day", day, 1, 50);
        ProductionContentCatalog.DayEntry definition = production.daysByNumber().get(day);
        if (definition == null) throw new IllegalArgumentException("Missing production day " + day);
        beforeMutation(actor, "world.day");
        loop.cleanupWorldObjects();
        long now = runs.clockNowMillis();
        runs.mutate(run -> {
            run.day = day;
            run.checkpointStartedAtEpochMs = now;
            run.boss = null;
            RunSnapshot.DayState previous = run.seasonDay;
            run.seasonDay = TestDayResetPolicy.prepare(definition, production.eventsById(),
                    run.test.virtualPartySize, now, previous == null ? 1L : previous.sequence + 1L);
        });
        loop.restoreWorldObjects();
        afterMutation(actor, "world.day", Integer.toString(day));
    }

    public void advanceDay(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "world.day.advance");
        runs.forceAdvance();
        afterMutation(actor, "world.day.advance", Integer.toString(runs.current().orElseThrow().day));
    }

    public void setWeather(Player actor, String rawWeather) throws IOException {
        requireOwner(actor);
        String weather = rawWeather.toUpperCase(Locale.ROOT);
        if (!Set.of("CLEAR", "RAIN", "THUNDER").contains(weather)) {
            throw new IllegalArgumentException("Weather must be CLEAR, RAIN, or THUNDER");
        }
        beforeMutation(actor, "world.weather");
        actor.getWorld().setStorm(!"CLEAR".equals(weather));
        actor.getWorld().setThundering("THUNDER".equals(weather));
        afterMutation(actor, "world.weather", weather);
    }

    public void setTimeFrozen(Player actor, boolean frozen) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "world.time.freeze");
        runs.mutate(run -> run.test.timeFrozen = frozen);
        afterMutation(actor, "world.time.freeze", Boolean.toString(frozen));
    }

    public void setTimeScale(Player actor, double scale) throws IOException {
        requireOwner(actor);
        TestValuePolicy.finiteInRange("time scale", scale, 0.05, 100.0);
        beforeMutation(actor, "world.time.scale");
        runs.mutate(run -> run.test.timeScale = scale);
        afterMutation(actor, "world.time.scale", Double.toString(scale));
    }

    public void stepTime(Player actor, long ticks) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "world.time.step");
        runs.stepTestClock(ticks);
        loop.tick();
        afterMutation(actor, "world.time.step", Long.toString(ticks));
    }

    public void setVirtualPartySize(Player actor, int size) throws IOException {
        requireOwner(actor);
        TestValuePolicy.integerInRange("virtual party size", size, 1, content.maximumPlayers());
        beforeMutation(actor, "party.size");
        runs.mutate(run -> run.test.virtualPartySize = size);
        afterMutation(actor, "party.size", Integer.toString(size));
    }

    public void setVirtualContribution(Player actor, String category, int amount) throws IOException {
        requireOwner(actor);
        String id = TestValuePolicy.identifier(category).toUpperCase(Locale.ROOT);
        TestValuePolicy.integerInRange("virtual contribution", amount, 0, 1_000_000);
        beforeMutation(actor, "party.contribution");
        runs.mutate(run -> run.test.virtualContributions.put(id, amount));
        afterMutation(actor, "party.contribution", id + "=" + amount);
    }

    public void simulateBossContributors(Player actor, int contributors) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "party.boss-channel");
        boss.simulateCooperationForTest(contributors);
        afterMutation(actor, "party.boss-channel", Integer.toString(contributors));
    }

    public void forceBossPhaseTwo(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "boss.phase-two");
        boss.forcePhaseTwoForTest();
        afterMutation(actor, "boss.phase-two", "2");
    }

    public void forceBossPattern(Player actor) throws IOException {
        requireOwner(actor);
        beforeMutation(actor, "boss.pattern");
        boss.forcePatternForTest();
        afterMutation(actor, "boss.pattern", "forced");
    }

    public TestPreset capturePreset(Player actor, String id) throws IOException {
        requireOwner(actor);
        TestPreset preset = fromCurrent(id, actor);
        repository.savePreset(preset);
        audit(actor, "preset.save", "none", preset.id);
        return preset;
    }

    public TestPreset loadPreset(Player actor, String id) throws IOException {
        requireOwner(actor);
        TestPreset preset = BUILT_IN_PRESETS.contains(id.toUpperCase(Locale.ROOT))
                ? builtInPreset(id) : repository.loadPreset(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown preset " + id));
        applyPreset(actor, preset, true);
        return preset;
    }

    public List<String> listPresets() throws IOException {
        Set<String> result = new LinkedHashSet<>(BUILT_IN_PRESETS);
        repository.listPresets().forEach(value -> result.add(value.toUpperCase(Locale.ROOT)));
        return result.stream().sorted().toList();
    }

    public void deletePreset(Player actor, String id) throws IOException {
        requireOwner(actor);
        if (BUILT_IN_PRESETS.contains(id.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Built-in presets cannot be deleted");
        }
        repository.deletePreset(id);
        audit(actor, "preset.delete", id, "deleted");
    }

    public Path export(Player actor) throws IOException {
        requireOwner(actor);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportedAtEpochMs", Instant.now().toEpochMilli());
        payload.put("run", repository.deepCopy(runs.current().orElseThrow()));
        payload.put("player", playerView(actor));
        combat.targetedCombatEntity(actor, 64.0).ifPresent(entity ->
                payload.put("target", combat.inspectCombatEntity(entity)));
        payload.put("pluginTickP95Ms", telemetry.p95PluginTickMs());
        Path path = repository.export("test-lab", payload);
        audit(actor, "export", "none", path.getFileName().toString());
        return path;
    }

    public Map<String, Object> playerView(Player actor) {
        requireOwner(actor);
        RunSnapshot.PlayerState state = playerState(actor);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", state.level);
        result.put("exp", state.exp);
        result.put("lifeState", state.lifeState);
        result.put("health", actor.getHealth());
        result.put("maxHealth", actor.getAttribute(Attribute.MAX_HEALTH).getValue());
        result.put("ap", state.ap);
        result.put("maxAp", state.maxAp);
        result.put("weapon", state.mainWeaponId == null ? "UNARMED" : state.mainWeaponId);
        result.put("quickItems", Map.copyOf(state.quickItems));
        result.put("personalAugments", List.copyOf(state.personalAugments));
        result.put("partyAugment", runs.current().orElseThrow().partyAugmentId);
        result.put("partyAugments", runs.current().orElseThrow().partyAugmentIds == null
                ? List.of() : List.copyOf(runs.current().orElseThrow().partyAugmentIds));
        result.put("damageDealtMultiplier", state.testDamageDealtMultiplier);
        result.put("breakMultiplier", state.testBreakMultiplier);
        result.put("damageTakenMultiplier", state.testDamageTakenMultiplier);
        result.put("damageReductionRate", state.testDamageReductionRate);
        result.put("cooldownMultiplier", state.testCooldownMultiplier);
        result.put("apCostMultiplier", state.testApCostMultiplier);
        result.put("apRegenMultiplier", state.testApRegenMultiplier);
        result.put("moveSpeedMultiplier", state.testMoveSpeedMultiplier);
        result.put("invulnerable", state.testInvulnerable);
        result.put("potionEffects", actor.getActivePotionEffects().stream()
                .map(effect -> effect.getType().getKey() + ":" + effect.getDuration() + ":" + effect.getAmplifier()).toList());
        return result;
    }

    public String summary() {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null || !"TEST".equals(run.runType) || run.test == null) {
            return "Test Lab inactive";
        }
        return "run=" + run.runId + " state=" + run.state + " day=" + run.day
                + " virtualParty=" + run.test.virtualPartySize + " seed=" + run.test.deterministicSeed
                + " frozen=" + run.test.timeFrozen + " timeScale=" + run.test.timeScale
                + " preset=" + run.test.activePreset + " scenario=" + run.test.activeScenario;
    }

    public Collection<String> weaponIds() {
        return java.util.stream.Stream.concat(
                        content.weapons().stream().map(PrototypeContent.WeaponDefinition::id),
                        production.equipmentById().keySet().stream())
                .distinct().sorted().toList();
    }

    public Collection<String> enemyIds() {
        return production.enemiesById().keySet().stream().sorted().toList();
    }

    public Collection<String> resourceIds() {
        return content.resources().stream().map(PrototypeContent.ResourceDefinition::id).toList();
    }

    public Collection<String> materialResourceIds() {
        return production.materialsById().keySet().stream().sorted().toList();
    }

    public Collection<String> researchIds() {
        return production.researchById().keySet().stream().sorted().toList();
    }

    public Collection<String> facilityIds() {
        return production.facilitiesById().values().stream()
                .filter(entry -> !entry.portableDevice() && !entry.reconstruction())
                .map(ProductionContentCatalog.FacilityEntry::id).sorted().toList();
    }

    public Collection<String> personalAugmentIds() {
        return production.personalAugments().stream().map(ProductionContentCatalog.AugmentEntry::id).toList();
    }

    public Collection<String> partyAugmentIds() {
        return production.partyAugments().stream().map(ProductionContentCatalog.AugmentEntry::id).toList();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("test-lab.enabled", false);
    }

    public boolean active() {
        return runs.isTestRun() && runs.current().map(run -> "RUNNING".equals(run.state)).orElse(false);
    }

    public boolean owner(Player player) {
        return runs.isTestRun() && runs.current().map(run -> run.test != null
                && TestLabSessionPolicy.activeOwner(run.state, run.test.ownerUuid,
                player.getUniqueId().toString())).orElse(false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (owner(event.getPlayer())) {
            Bukkit.getScheduler().runTask(plugin, () -> applyRuntimePlayerState(event.getPlayer()));
        } else if (sessionOwner(event.getPlayer())) {
            event.getPlayer().sendMessage(ChatColor.RED
                    + "[Test Lab] 중단된 세션 복구가 대기 중입니다. 최신 스냅샷은 /ws test undo, "
                    + "입장 전 상태 복원·폐기는 /ws test exit RECOVERED --confirm");
        }
    }

    public void recoverActiveSession() {
        if (!runs.isTestRun()) {
            return;
        }
        RunSnapshot run = runs.current().orElse(null);
        if (run == null || run.test == null) {
            return;
        }
        if ("RUNNING".equals(run.state)) {
            Player owner = Bukkit.getPlayer(UUID.fromString(run.test.ownerUuid));
            if (owner != null) {
                applyRuntimePlayerState(owner);
            }
            plugin.getLogger().warning("Restored active Test Lab run " + run.runId
                    + "; exit it with /ws test exit <reason> --confirm to restore the player backup.");
        } else if (run.test.restorePending) {
            plugin.getLogger().warning("Restored Test Lab session awaiting recovery " + run.runId
                    + "; the owner may run /ws test undo for the latest snapshot or "
                    + "/ws test exit RECOVERED --confirm to restore the entry backup.");
        }
    }

    private void resetState(Player actor, boolean preserveClock) {
        RunSnapshot previous = runs.current().orElseThrow();
        long now = runs.clockNowMillis();
        RunSnapshot replacement = new RunSnapshot();
        replacement.runId = previous.runId;
        replacement.runType = "TEST";
        replacement.contentRevision = previous.contentRevision;
        replacement.state = "RUNNING";
        replacement.createdAtEpochMs = previous.createdAtEpochMs;
        replacement.startedAtEpochMs = preserveClock ? previous.startedAtEpochMs : previous.createdAtEpochMs;
        replacement.checkpointStartedAtEpochMs = now;
        replacement.seed = previous.seed;
        replacement.test = repository.deepCopy(previous).test;
        replacement.test.activePreset = "DEFAULT";
        replacement.test.activeScenario = "SANDBOX";
        replacement.day = 1;
        replacement.seasonDay = TestDayResetPolicy.prepare(production.daysByNumber().get(1),
                production.eventsById(), replacement.test.virtualPartySize, now,
                previous.seasonDay == null ? 1L : previous.seasonDay.sequence + 1L);
        replacement.registeredPlayers.add(actor.getUniqueId().toString());
        RunSnapshot.PlayerState state = new RunSnapshot.PlayerState();
        state.uuid = actor.getUniqueId().toString();
        state.lastKnownName = actor.getName();
        Location location = actor.getLocation();
        state.world = location.getWorld().getName();
        state.x = location.getX();
        state.y = location.getY();
        state.z = location.getZ();
        state.yaw = location.getYaw();
        state.pitch = location.getPitch();
        replacement.players.put(state.uuid, state);
        content.resources().forEach(resource -> replacement.resources.put(resource.id(), 0));
        runs.replaceCurrentTest(replacement);
        actor.getInventory().clear();
        actor.getActivePotionEffects().forEach(effect -> actor.removePotionEffect(effect.getType()));
        actor.setGameMode(GameMode.SURVIVAL);
        applyRuntimePlayerState(actor);
        equipment.syncAuthoritativeEquipment(actor);
    }

    private void applyPreset(Player actor, TestPreset preset, boolean createSnapshot) throws IOException {
        if (createSnapshot) {
            beforeMutation(actor, "preset.apply");
        }
        validatePreset(preset);
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = state(run, actor);
            run.test.virtualPartySize = preset.virtualPartySize;
            run.test.deterministicSeed = preset.deterministicSeed == 0L ? run.seed : preset.deterministicSeed;
            run.test.timeFrozen = preset.timeFrozen;
            run.test.timeScale = preset.timeScale;
            run.test.activePreset = preset.id.toUpperCase(Locale.ROOT);
            state.level = preset.level;
            state.exp = preset.exp;
            state.testMaxHealth = preset.maxHealth;
            state.testBaseMaxAp = preset.maxAp;
            state.maxAp = preset.maxAp;
            state.ap = preset.maxAp;
            state.testDamageDealtMultiplier = preset.damageDealtMultiplier;
            state.testBreakMultiplier = preset.breakMultiplier;
            state.testDamageTakenMultiplier = preset.damageTakenMultiplier;
            state.testDamageReductionRate = preset.damageReductionRate;
            state.testCooldownMultiplier = preset.cooldownMultiplier;
            state.testApCostMultiplier = preset.apCostMultiplier;
            state.testApRegenMultiplier = preset.apRegenMultiplier;
            state.testMoveSpeedMultiplier = preset.moveSpeedMultiplier;
            state.testInvulnerable = preset.invulnerable;
        });
        applyRuntimePlayerState(actor);
        if (createSnapshot) {
            afterMutation(actor, "preset.apply", preset.id);
        }
    }

    private TestPreset fromCurrent(String id, Player actor) {
        RunSnapshot run = runs.current().orElseThrow();
        RunSnapshot.PlayerState state = playerState(actor);
        TestPreset preset = new TestPreset();
        preset.id = TestValuePolicy.fileId(id);
        preset.virtualPartySize = run.test.virtualPartySize;
        preset.deterministicSeed = run.test.deterministicSeed;
        preset.timeFrozen = run.test.timeFrozen;
        preset.timeScale = run.test.timeScale;
        preset.level = state.level;
        preset.exp = state.exp;
        preset.maxHealth = state.testMaxHealth;
        preset.maxAp = state.testBaseMaxAp;
        preset.damageDealtMultiplier = state.testDamageDealtMultiplier;
        preset.breakMultiplier = state.testBreakMultiplier;
        preset.damageTakenMultiplier = state.testDamageTakenMultiplier;
        preset.damageReductionRate = state.testDamageReductionRate;
        preset.cooldownMultiplier = state.testCooldownMultiplier;
        preset.apCostMultiplier = state.testApCostMultiplier;
        preset.apRegenMultiplier = state.testApRegenMultiplier;
        preset.moveSpeedMultiplier = state.testMoveSpeedMultiplier;
        preset.invulnerable = state.testInvulnerable;
        return preset;
    }

    private TestPreset builtInPreset(String rawId) {
        String id = rawId.toUpperCase(Locale.ROOT);
        TestPreset preset = new TestPreset();
        preset.id = id;
        switch (id) {
            case "DEFAULT" -> { }
            case "GLASS-CANNON" -> {
                preset.damageDealtMultiplier = 4.0;
                preset.damageTakenMultiplier = 3.0;
                preset.maxHealth = 10.0;
            }
            case "TANK" -> {
                preset.maxHealth = 100.0;
                preset.damageReductionRate = 0.75;
                preset.damageDealtMultiplier = 0.75;
            }
            case "NO-COOLDOWN" -> {
                preset.cooldownMultiplier = 0.05;
                preset.apCostMultiplier = 0.0;
                preset.apRegenMultiplier = 20.0;
                preset.maxAp = 1000;
            }
            case "SURVIVABILITY" -> {
                preset.maxHealth = 40.0;
                preset.damageReductionRate = 0.50;
                preset.apRegenMultiplier = 3.0;
                preset.invulnerable = false;
            }
            case "PARTY-4" -> {
                preset.virtualPartySize = 4;
                preset.timeFrozen = true;
            }
            default -> throw new IllegalArgumentException("Unknown built-in preset " + rawId);
        }
        return preset;
    }

    private void validatePreset(TestPreset preset) {
        TestValuePolicy.fileId(preset.id);
        TestValuePolicy.integerInRange("virtual party size", preset.virtualPartySize, 1, content.maximumPlayers());
        TestValuePolicy.finiteInRange("time scale", preset.timeScale, 0.05, 100.0);
        TestValuePolicy.integerInRange("level", preset.level, 1, 50);
        TestValuePolicy.integerInRange("experience", preset.exp, 0, 2_000_000_000);
        TestValuePolicy.statValue("max-health", preset.maxHealth);
        TestValuePolicy.statValue("max-ap", preset.maxAp);
        TestValuePolicy.statValue("damage-dealt", preset.damageDealtMultiplier);
        TestValuePolicy.statValue("break", preset.breakMultiplier);
        TestValuePolicy.statValue("damage-taken", preset.damageTakenMultiplier);
        TestValuePolicy.statValue("damage-reduction", preset.damageReductionRate);
        TestValuePolicy.statValue("cooldown", preset.cooldownMultiplier);
        TestValuePolicy.statValue("ap-cost", preset.apCostMultiplier);
        TestValuePolicy.statValue("ap-regen", preset.apRegenMultiplier);
        TestValuePolicy.statValue("move-speed", preset.moveSpeedMultiplier);
    }

    private void applyRuntimePlayerState(Player player) {
        if (!owner(player)) {
            return;
        }
        RunSnapshot.PlayerState state = playerState(player);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(
                TestValuePolicy.statValue("max-health", state.testMaxHealth));
        double defaultMovement = Math.max(PlayerStatPolicy.movementSpeed(Map.of()),
                plugin.getConfig().getDouble("test-lab.default-movement-speed", 0.11));
        player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(
                Math.max(0.01, Math.min(1.0, defaultMovement * state.testMoveSpeedMultiplier)));
        var blockRange = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (blockRange != null) blockRange.setBaseValue(PlayerStatPolicy.blockInteractionRange());
        var entityRange = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (entityRange != null) entityRange.setBaseValue(PlayerStatPolicy.entityInteractionRange());
        player.setHealth(Math.min(player.getHealth(), player.getAttribute(Attribute.MAX_HEALTH).getValue()));
        runs.restorePlayer(player);
        equipment.syncAuthoritativeEquipment(player);
    }

    private PotionEffectType potionType(String raw) {
        String key = raw.toLowerCase(Locale.ROOT).replace('_', '-');
        PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(key));
        if (type == null) {
            throw new IllegalArgumentException("Unknown potion effect " + raw);
        }
        return type;
    }

    private boolean isTestItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().getOrDefault(testItemKey,
                PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private RunSnapshot.PlayerState playerState(Player player) {
        return runs.playerState(player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("Player is not part of the Test Lab run"));
    }

    private static RunSnapshot.PlayerState state(RunSnapshot run, Player player) {
        RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
        if (state == null) {
            throw new IllegalStateException("Player is not part of the Test Lab run");
        }
        return state;
    }

    private void beforeMutation(Player actor, String action) throws IOException {
        requireOwner(actor);
        checkpoint(actor, "BEFORE_" + action.toUpperCase(Locale.ROOT).replace('.', '_'));
    }

    private void afterMutation(Player actor, String action, String after) throws IOException {
        audit(actor, action, "snapshot", after);
    }

    private void audit(Player actor, String action, String before, String after) throws IOException {
        RunSnapshot run = runs.current().orElseThrow();
        repository.audit(run.runId, actor.getUniqueId().toString(), action, before, after);
        telemetry.audit(run.runId, actor.getUniqueId().toString(), "test." + action, before + " -> " + after);
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new IllegalStateException("Test Lab is disabled. Set test-lab.enabled=true in config.yml and restart.");
        }
    }

    private void requireOwner(Player player) {
        requireEnabled();
        if (!owner(player)) {
            throw new IllegalStateException("Only the active Test Lab owner can use this operation");
        }
    }

    private boolean sessionOwner(Player player) {
        return runs.isTestRun() && runs.current().map(run -> run.test != null
                && TestLabSessionPolicy.recoverableOwner(run.state, run.test.restorePending,
                run.test.ownerUuid, player.getUniqueId().toString())).orElse(false);
    }

    private void requireSessionOwner(Player player) {
        if (!sessionOwner(player)) {
            throw new IllegalStateException("Only the Test Lab session owner can restore this session");
        }
    }

    private void restoreBackupAfterFailure(Player player) {
        try {
            TestPlayerBackup backup = repository.loadBackup(player.getUniqueId().toString()).orElse(null);
            if (runs.isTestRun()) {
                RunSnapshot run = runs.current().orElse(null);
                if (run != null && !List.of("ENDED", "ABORTED").contains(run.state)) {
                    runs.stop("TEST_ENTRY_FAILED", "system");
                }
                loop.cleanupWorldObjects();
                if (backup != null) {
                    backup.restore(player);
                }
                if (runs.current().isPresent() && backup != null) {
                    runs.clearCurrentTest();
                }
            } else if (backup != null) {
                backup.restore(player);
            }
            if (backup != null && !runs.isTestRun()) {
                repository.deleteBackup(player.getUniqueId().toString());
            }
        } catch (Exception restoreFailure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot restore failed Test Lab entry", restoreFailure);
        }
    }

    private void cleanupExitedSessionArtifacts(String runId, String playerUuid) {
        IOException failure = null;
        try {
            repository.clearSnapshots(runId);
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            repository.deleteBackup(playerUuid);
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Test Lab exit completed but stale recovery artifacts remain for " + runId, failure);
        }
    }
}
