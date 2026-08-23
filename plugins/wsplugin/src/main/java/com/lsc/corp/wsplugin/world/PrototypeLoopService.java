package com.lsc.corp.wsplugin.world;

import com.lsc.corp.wsplugin.boss.PrototypeBossService;
import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.combat.DeathRuntimePolicy;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.death.GraveService;
import com.lsc.corp.wsplugin.economy.EconomyService;
import com.lsc.corp.wsplugin.facility.FacilityStateAccess;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.PlayerStatService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.ui.ActionBarService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class PrototypeLoopService implements Listener {
    private final JavaPlugin plugin;
    private final RunService runs;
    private final ProductionContentCatalog production;
    private final EconomyService economy;
    private final CombatService combat;
    private final GraveService graves;
    private final PrototypeBossService boss;
    private final GrowthService growth;
    private final PlayerStatService stats;
    private final TelemetryService telemetry;
    private Location corruptionCenter;
    private int tickCounter;

    public PrototypeLoopService(JavaPlugin plugin, RunService runs,
                                ProductionContentCatalog production, EconomyService economy,
                                CombatService combat, GraveService graves, PrototypeBossService boss, GrowthService growth,
                                PlayerStatService stats, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.production = production;
        this.economy = economy;
        this.combat = combat;
        this.graves = graves;
        this.boss = boss;
        this.growth = growth;
        this.stats = stats;
        this.telemetry = telemetry;
    }

    public void startRunWorld() {
        combat.cleanupForeignCombatEntities();
        for (Player player : runs.onlineMembers()) {
            player.setGameMode(GameMode.SURVIVAL);
            stats.apply(player);
            player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            economy.grantPersonalItem(player, "SURVIVAL-CLOCK", 1);
        }
        beginDay(1);
    }

    public void restoreWorldObjects() {
        economy.restoreFacility();
        boss.restore();
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state)) return;
        if (snapshot.day >= 6) corruptionCenter = facilityOrAnchor();
        if (snapshot.seasonDay == null || snapshot.seasonDay.day != snapshot.day) {
            beginDay(snapshot.day);
        }
    }

    public void tick() {
        combat.tick();
        boss.tick();
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state)) return;
        ProductionContentCatalog.DayEntry day = requireDay(snapshot.day);
        long now = runs.clockNowMillis();

        if ("PREPARING".equals(snapshot.seasonDay.state)
                && now >= snapshot.seasonDay.pressureStartedAtEpochMs) {
            startPressure(day);
            snapshot = runs.current().orElseThrow();
        }
        if (day.bossDay()) {
            tickBossDay(snapshot, day);
        } else {
            tickEncounter(snapshot, day);
        }
        snapshot = runs.current().orElseThrow();
        if ("COMPLETED".equals(snapshot.seasonDay.state) && snapshot.day < 50
                && now >= snapshot.seasonDay.startedAtEpochMs + dayDurationMillis()) {
            beginDay(snapshot.day + 1);
        }
        if (++tickCounter % 10 == 0) tickCorruption();
    }

    public void forceAdvance() {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (!"RUNNING".equals(snapshot.state)) throw new IllegalStateException("Run is not running");
        ProductionContentCatalog.DayEntry day = requireDay(snapshot.day);
        if ("PREPARING".equals(snapshot.seasonDay.state)) {
            startPressure(day);
            return;
        }
        if ("COMPLETED".equals(snapshot.seasonDay.state)) {
            if (snapshot.day >= 50) throw new IllegalStateException("Day 50 requires the Final objective");
            beginDay(snapshot.day + 1);
            return;
        }
        if (day.bossDay() && snapshot.boss == null) {
            boss.spawn();
            return;
        }
        if (isCombatActive()) throw new IllegalStateException("Active combat must be resolved before advancing");
        if (!day.bossDay()) tickEncounter(snapshot, day);
    }

    public boolean isCombatActive() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state)) return false;
        if (snapshot.boss != null && "ACTIVE".equals(snapshot.boss.state)) return true;
        return snapshot.encounters.values().stream().anyMatch(encounter -> encounter.day == snapshot.day
                && Set.of("ACTIVE", "SPAWNING").contains(encounter.state)
                && !encounter.spawnedEntityUuids.isEmpty()) || combat.hasActiveEnemies();
    }

    public void cleanupWorldObjects() {
        boss.cleanup();
        economy.removeFacility();
        combat.cleanupCombatEntities();
        graves.cleanup();
        corruptionCenter = null;
    }

    private void beginDay(int dayNumber) {
        ProductionContentCatalog.DayEntry day = requireDay(dayNumber);
        RunSnapshot before = runs.current().orElseThrow();
        int threat = SeasonDayPolicy.threatForParty(day, production.eventsById(), runs.effectivePartySize());
        long now = runs.clockNowMillis();
        long pressureAt = now + preparationMillis(dayNumber);
        boolean committed = runs.commitOnce("season-day-start:" + dayNumber, "DAY_STARTED",
                "{\"day\":" + dayNumber + ",\"threat\":" + threat + "}", run -> {
                    run.day = dayNumber;
                    run.boss = null;
                    RunSnapshot.DayState state = new RunSnapshot.DayState();
                    state.day = dayNumber;
                    state.dayId = day.id();
                    state.state = "PREPARING";
                    state.lockedBudgetProfileId = "STD-BALANCED";
                    state.lockedThreatBudget3 = threat;
                    state.lockedResourceBudgets.addAll(day.resourceBudgetTotals());
                    state.eventQueue.addAll(day.eventIds());
                    state.activeEventId = day.eventIds().isEmpty() ? null : day.eventIds().getFirst();
                    state.startedAtEpochMs = now;
                    state.pressureStartedAtEpochMs = pressureAt;
                    state.sequence = before.seasonDay == null ? 1L : before.seasonDay.sequence + 1L;
                    run.seasonDay = state;
                });
        if (!committed) return;
        growth.awardSeasonExp(day.progressExp(), "day-" + dayNumber + "-progress", false);
        runs.mutate(run -> run.seasonDay.progressionExpCommitted = true);
        announceDay(day);
        if (dayNumber >= 6) corruptionCenter = facilityOrAnchor();
    }

    private void announceDay(ProductionContentCatalog.DayEntry day) {
        runs.broadcast(ChatColor.GOLD + "[WildSurvival] Day " + day.day() + " — " + day.milestoneText());
        for (String eventId : day.eventIds()) {
            ProductionContentCatalog.EventEntry event = production.eventsById().get(eventId);
            if (event == null) continue;
            String message = !event.telegraphText().isBlank() ? event.telegraphText() : event.objectiveText();
            if (!message.isBlank()) runs.broadcast(ChatColor.YELLOW + "[사건] " + message);
        }
        if (day.bossDay()) {
            ProductionContentCatalog.BossEntry definition = production.bossesById().get(day.bossId());
            runs.broadcast(ChatColor.DARK_RED + "보스 신호 감지: " + definition.name());
        }
    }

    private void startPressure(ProductionContentCatalog.DayEntry day) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (!"PREPARING".equals(snapshot.seasonDay.state)) return;
        if (day.bossDay()) {
            boss.spawn();
            return;
        }
        String encounterId = "DAY-" + day.day() + "-ENCOUNTER";
        List<String> plan = SeasonDayPolicy.enemyPlan(production.enemiesById(), day.day(),
                snapshot.seasonDay.lockedThreatBudget3, snapshot.seed);
        int cap = SeasonDayPolicy.activeCap(day.day(), runs.effectivePartySize());
        runs.commitOnce("encounter-plan:day" + day.day(), "ENCOUNTER_PLANNED",
                "{\"day\":" + day.day() + ",\"count\":" + plan.size() + "}", run -> {
                    RunSnapshot.EncounterState encounter = new RunSnapshot.EncounterState();
                    encounter.encounterId = encounterId;
                    encounter.eventId = run.seasonDay.activeEventId;
                    encounter.day = day.day();
                    encounter.executionOpcode = "RUN_PRESSURE_WAVES";
                    encounter.state = "SPAWNING";
                    encounter.budgetProfileId = run.seasonDay.lockedBudgetProfileId;
                    encounter.threatBudget = run.seasonDay.lockedThreatBudget3;
                    encounter.plannedEnemyIds.addAll(plan);
                    encounter.waveCount = (plan.size() + cap - 1) / cap;
                    encounter.startedAtEpochMs = runs.clockNowMillis();
                    run.encounters.put(encounterId, encounter);
                    run.seasonDay.state = "PRESSURE";
                });
        spawnNextWave(encounterId, cap);
    }

    private void tickEncounter(RunSnapshot snapshot, ProductionContentCatalog.DayEntry day) {
        if ("PREPARING".equals(snapshot.seasonDay.state) || "COMPLETED".equals(snapshot.seasonDay.state)) return;
        String encounterId = "DAY-" + day.day() + "-ENCOUNTER";
        RunSnapshot.EncounterState encounter = snapshot.encounters.get(encounterId);
        if (encounter == null) return;
        Set<String> live = encounter.spawnedEntityUuids.stream().filter(this::isLiveEntity)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!live.equals(encounter.spawnedEntityUuids)) {
            runs.mutate(run -> {
                RunSnapshot.EncounterState stored = run.encounters.get(encounterId);
                stored.spawnedEntityUuids.clear();
                stored.spawnedEntityUuids.addAll(live);
            });
            encounter = runs.current().orElseThrow().encounters.get(encounterId);
        }
        if (!encounter.spawnedEntityUuids.isEmpty()) return;
        int cap = SeasonDayPolicy.activeCap(day.day(), runs.effectivePartySize());
        if (encounter.nextEnemyIndex < encounter.plannedEnemyIds.size()) {
            spawnNextWave(encounterId, cap);
            return;
        }
        if (combat.hasActiveEnemies()) return;
        resolveEncounter(encounterId, day);
    }

    private void spawnNextWave(String encounterId, int cap) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        RunSnapshot.EncounterState encounter = snapshot.encounters.get(encounterId);
        if (encounter == null || !encounter.spawnedEntityUuids.isEmpty()) return;
        int from = encounter.nextEnemyIndex;
        int to = Math.min(encounter.plannedEnemyIds.size(), from + cap);
        if (from >= to) return;
        List<String> spawned = new ArrayList<>();
        List<Player> members = runs.onlineMembers();
        if (members.isEmpty()) return;
        for (int index = from; index < to; index++) {
            String enemyId = encounter.plannedEnemyIds.get(index);
            Player anchor = members.get((index - from) % members.size());
            Location location = encounterLocation(anchor, index - from, to - from);
            LivingEntity entity = combat.spawnProductionEnemy(enemyId, location, 1.0);
            spawned.add(entity.getUniqueId().toString());
        }
        runs.mutate(run -> {
            RunSnapshot.EncounterState stored = run.encounters.get(encounterId);
            stored.spawnedEntityUuids.addAll(spawned);
            stored.nextEnemyIndex = to;
            stored.waveIndex++;
            stored.state = "ACTIVE";
        });
        runs.broadcast(ChatColor.RED + "[공세 " + (encounter.waveIndex + 1) + "/" + encounter.waveCount
                + "] 적 " + spawned.size() + "체 접근");
    }

    private void resolveEncounter(String encounterId, ProductionContentCatalog.DayEntry day) {
        boolean committed = runs.commitOnce("encounter-reward:day" + day.day(), "ENCOUNTER_RESOLVED",
                "{\"day\":" + day.day() + "}", run -> {
                    RunSnapshot.EncounterState encounter = run.encounters.get(encounterId);
                    encounter.state = "RESOLVED";
                    encounter.resolvedAtEpochMs = runs.clockNowMillis();
                    encounter.rewardCommitted = true;
                    run.seasonDay.state = "COMPLETED";
                    run.seasonDay.resolvedAtEpochMs = runs.clockNowMillis();
                    run.seasonDay.activityExpCommitted = true;
                    run.players.values().stream()
                            .filter(player -> Set.of("ACTIVE", "DOWNED_GRACE", "DOWNED", "BEING_REVIVED")
                                    .contains(player.lifeState))
                            .forEach(player -> player.injuryStacks = DeathRuntimePolicy.injuryAfterDayEnd(
                                    player.injuryStacks, false, false));
                    if (day.day() == 50) run.finalObjective.state = "AVAILABLE";
                });
        if (!committed) return;
        growth.awardSeasonExp(day.activityExp(), "day-" + day.day() + "-activity", true);
        runs.broadcast(day.day() == 50 ? ChatColor.LIGHT_PURPLE
                + "Day 50 일일 압박 해소 — 인류 재건을 위한 첫 신호를 활성화할 수 있습니다."
                : ChatColor.GREEN + "Day " + day.day() + " 사건 해결");
        telemetry.event(runs.current().orElseThrow().runId, "DAY_RESOLVED", "{\"day\":" + day.day() + "}");
    }

    private void tickBossDay(RunSnapshot snapshot, ProductionContentCatalog.DayEntry day) {
        if (snapshot.boss == null || !snapshot.boss.rewardCommitted) return;
        if (!growth.partyAugmentSelected()) {
            growth.startPartyVoteWhenReady();
            return;
        }
        runs.commitOnce("boss-day-resolved:" + day.day(), "DAY_RESOLVED",
                "{\"day\":" + day.day() + "}", run -> {
                    run.seasonDay.state = "COMPLETED";
                    run.seasonDay.resolvedAtEpochMs = runs.clockNowMillis();
                });
    }

    private void tickCorruption() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.day < 6 || corruptionCenter == null || corruptionCenter.getWorld() == null) return;
        double radius = Math.min(8.0, 3.0 + snapshot.day / 12.0);
        corruptionCenter.getWorld().spawnParticle(Particle.WITCH, corruptionCenter.clone().add(0, 1, 0),
                14, radius, 0.7, radius, 0.02);
        for (Player player : runs.onlineMembers()) {
            if (player.getWorld().equals(corruptionCenter.getWorld())
                    && player.getLocation().distanceSquared(corruptionCenter) <= radius * radius) {
                if (FacilityStateAccess.corruptionProtected(snapshot, player.getWorld().getName(),
                        player.getX(), player.getY(), player.getZ())) {
                    player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0),
                            3, 0.4, 0.7, 0.4, 0.01);
                    continue;
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, true, true));
                if (tickCounter % 40 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.25f, 1.5f);
                    ActionBarService.notice(player, Component.text("☣ 오염 지대 — 이동 둔화",
                            NamedTextColor.LIGHT_PURPLE), 30);
                }
            }
        }
    }

    private long preparationMillis(int day) {
        int seconds = day == 1
                ? plugin.getConfig().getInt("season.day-1-grace-seconds",
                        plugin.getConfig().getInt("prototype.day-1-grace-seconds", 90))
                : plugin.getConfig().getInt("season.preparation-seconds", 30);
        return Math.max(0L, seconds) * 1000L;
    }

    private long dayDurationMillis() {
        return Math.max(60L, plugin.getConfig().getLong("season.day-duration-seconds", 1200L)) * 1000L;
    }

    private Location encounterLocation(Player anchor, int index, int count) {
        double angle = Math.PI * 2.0 * index / Math.max(1, count);
        int radius = plugin.getConfig().getInt("season.spawn-radius",
                plugin.getConfig().getInt("prototype.spawn-radius", 10));
        Location result = anchor.getLocation().clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        result.setY(result.getWorld().getHighestBlockYAt(result) + 1.0);
        return result;
    }

    private boolean isLiveEntity(String uuidText) {
        try {
            UUID uuid = UUID.fromString(uuidText);
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) return entity.isValid() && !entity.isDead();
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return false;
    }

    private Location facilityOrAnchor() {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (snapshot.facility != null && snapshot.facility.active) {
            org.bukkit.World world = Bukkit.getWorld(snapshot.facility.world);
            if (world != null) return new Location(world, snapshot.facility.x + 0.5,
                    snapshot.facility.y, snapshot.facility.z + 0.5);
        }
        return runs.onlineMembers().stream().findFirst().map(Player::getLocation).orElse(null);
    }

    private ProductionContentCatalog.DayEntry requireDay(int day) {
        ProductionContentCatalog.DayEntry result = production.daysByNumber().get(day);
        if (result == null) throw new IllegalArgumentException("Unknown Season 1 Day " + day);
        return result;
    }
}
