package com.lsc.corp.wsplugin.boss;

import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.economy.LootService;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.ui.ActionBarService;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrototypeBossService implements Listener, CombatService.BossDamageHandler {
    private final JavaPlugin plugin;
    private final RunService runs;
    private final ProductionContentCatalog production;
    private final CombatService combat;
    private final GrowthService growth;
    private final LootService loot;
    private final TelemetryService telemetry;
    private BossBar healthBar;
    private long nextPatternAtTick;
    private long channelEndsAtTick;
    private boolean cooperationChannelActive;

    public PrototypeBossService(JavaPlugin plugin, RunService runs, ProductionContentCatalog production,
                                CombatService combat, GrowthService growth, LootService loot,
                                TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.production = production;
        this.combat = combat;
        this.growth = growth;
        this.loot = loot;
        this.telemetry = telemetry;
    }

    public LivingEntity spawn() {
        RunSnapshot snapshot = runs.current().orElseThrow();
        ProductionContentCatalog.DayEntry day = production.daysByNumber().get(snapshot.day);
        if (!"RUNNING".equals(snapshot.state) || day == null || !day.bossDay()) {
            throw new IllegalStateException("An active Season 1 boss Day is required");
        }
        if (snapshot.boss != null && ("ACTIVE".equals(snapshot.boss.state) || snapshot.boss.rewardCommitted)) {
            throw new IllegalStateException("The Day boss is already active or completed");
        }
        ProductionContentCatalog.BossEntry definition = definition(day.bossId());
        Player anchor = runs.onlineMembers().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No online member"));
        Location location = safeSpawn(anchor.getLocation().clone()
                .add(anchor.getLocation().getDirection().setY(0).normalize().multiply(10)));
        int players = Math.max(1, runs.effectivePartySize());
        double hpMultiplier = switch (players) { case 1 -> 0.72; case 3 -> 1.32; case 4 -> 1.60; default -> 1.0; };
        double breakMultiplier = switch (players) { case 1 -> 0.75; case 3 -> 1.25; case 4 -> 1.50; default -> 1.0; };
        double maxHp = definition.baseHp() * hpMultiplier;
        double maxBreak = definition.breakMax() * breakMultiplier;
        LivingEntity entity = spawnEntity(definition, location);
        combat.tagCombatEntity(entity, definition.id(), maxHp, definition.defence(), maxBreak);
        initializeVanillaHealth(entity);
        runs.commitOnce("boss-spawn:day" + snapshot.day, "BOSS_ACTIVATED",
                "{\"bossId\":\"" + definition.id() + "\",\"players\":" + players + "}", run -> {
                    RunSnapshot.BossState state = new RunSnapshot.BossState();
                    state.bossId = definition.id();
                    state.entityUuid = entity.getUniqueId().toString();
                    state.world = location.getWorld().getName();
                    state.x = location.getX();
                    state.y = location.getY();
                    state.z = location.getZ();
                    state.hp = maxHp;
                    state.maxHp = maxHp;
                    state.breakMax = maxBreak;
                    run.boss = state;
                    run.seasonDay.state = "PRESSURE";
                    run.seasonDay.pressureStartedAtEpochMs = runs.clockNowMillis();
                });
        createHealthBar(entity, definition);
        nextPatternAtTick = runs.clockTick() + Math.max(40L, definition.telegraphTicks() + 20L);
        runs.broadcast(ChatColor.DARK_RED + "[Day " + snapshot.day + "] " + definition.name()
                + " 출현 — 전조, 브레이크, 협동 중단을 준비하세요.");
        return entity;
    }

    public void restore() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.boss == null || !"ACTIVE".equals(snapshot.boss.state)) return;
        ProductionContentCatalog.BossEntry definition = definition(snapshot.boss.bossId);
        Entity existing = findEntity(snapshot.boss.entityUuid);
        if (existing instanceof LivingEntity living) {
            createHealthBar(living, definition);
            restoreCooperationChannel(living, snapshot.boss);
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(snapshot.boss.world);
        if (world == null) throw new IllegalStateException("Cannot restore boss world " + snapshot.boss.world);
        LivingEntity entity = spawnEntity(definition,
                new Location(world, snapshot.boss.x, snapshot.boss.y, snapshot.boss.z));
        combat.tagCombatEntity(entity, definition.id(), snapshot.boss.hp, definition.defence(), snapshot.boss.breakMax);
        initializeVanillaHealth(entity);
        runs.mutate(run -> run.boss.entityUuid = entity.getUniqueId().toString());
        createHealthBar(entity, definition);
        restoreCooperationChannel(entity, runs.current().orElseThrow().boss);
        nextPatternAtTick = runs.clockTick() + Math.max(40L, definition.telegraphTicks() + 20L);
    }

    public void tick() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.boss == null || !"ACTIVE".equals(snapshot.boss.state)) return;
        Entity found = findEntity(snapshot.boss.entityUuid);
        if (!(found instanceof LivingEntity entity) || !entity.isValid()) {
            restore();
            return;
        }
        ProductionContentCatalog.BossEntry definition = definition(snapshot.boss.bossId);
        updateHealthBar(snapshot.boss, definition);
        if (cooperationChannelActive) {
            resolveCooperationChannel(entity, definition);
            return;
        }
        if (runs.clockTick() >= nextPatternAtTick) executeNextPattern(entity, definition, snapshot.boss);
    }

    @Override
    public void damage(Player attacker, LivingEntity entity, double damage, double breakDamage, String executionId) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (snapshot.boss == null || !entity.getUniqueId().toString().equals(snapshot.boss.entityUuid)) return;
        boolean committed = runs.commitOnce("boss-hit:" + executionId, "BOSS_DAMAGE_COMMITTED",
                "{\"damage\":" + round(damage) + ",\"break\":" + round(breakDamage) + "}", run -> {
                    run.boss.hp = Math.max(0.0, run.boss.hp - damage);
                    run.boss.x = entity.getLocation().getX();
                    run.boss.y = entity.getLocation().getY();
                    run.boss.z = entity.getLocation().getZ();
                });
        if (!committed) return;
        combat.applyBreak(entity, breakDamage);
        runs.mutate(run -> run.boss.breakCurrent = combat.currentBreak(entity));
        RunSnapshot.BossState state = runs.current().orElseThrow().boss;
        if (state.hp <= 0.0) {
            defeat(entity);
        } else if (state.phase == 1 && state.hp / state.maxHp <= 0.70) {
            enterPhaseTwo(entity, definition(state.bossId));
        } else if (state.phase == 2 && state.hp / state.maxHp <= 0.35) {
            enterPhaseThree(entity, definition(state.bossId));
        }
    }

    @EventHandler
    public void onBossInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !cooperationChannelActive
                || !runs.isRunningMember(event.getPlayer())) return;
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.boss == null
                || !event.getRightClicked().getUniqueId().toString().equals(snapshot.boss.entityUuid)) return;
        event.setCancelled(true);
        runs.mutate(run -> run.boss.channelParticipants.add(event.getPlayer().getUniqueId().toString()));
        ActionBarService.notice(event.getPlayer(), Component.text("공명 고정 참여 완료", NamedTextColor.GREEN), 40);
    }

    public void forcePhaseTwoForTest() {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (!runs.isTestRun() || snapshot.boss == null || !"ACTIVE".equals(snapshot.boss.state)) {
            throw new IllegalStateException("An active Test Lab boss is required");
        }
        Entity found = findEntity(snapshot.boss.entityUuid);
        if (!(found instanceof LivingEntity entity)) throw new IllegalStateException("Test boss entity is not loaded");
        if (snapshot.boss.phase < 2) enterPhaseTwo(entity, definition(snapshot.boss.bossId));
    }

    public void simulateCooperationForTest(int contributors) {
        if (!runs.isTestRun() || contributors < 0 || contributors > 4) {
            throw new IllegalArgumentException("Test contributors must be 0 to 4");
        }
        runs.mutate(run -> {
            if (run.boss == null || !"ACTIVE".equals(run.boss.state)) {
                throw new IllegalStateException("An active Test Lab boss is required");
            }
            run.boss.channelParticipants.removeIf(value -> value.startsWith("virtual-test-"));
            for (int index = 1; index <= contributors; index++) {
                run.boss.channelParticipants.add("virtual-test-" + index);
            }
        });
    }

    public void forcePatternForTest() {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (!runs.isTestRun() || snapshot.boss == null || !"ACTIVE".equals(snapshot.boss.state)) {
            throw new IllegalStateException("An active Test Lab boss is required");
        }
        Entity found = findEntity(snapshot.boss.entityUuid);
        if (!(found instanceof LivingEntity entity)) throw new IllegalStateException("Test boss entity is not loaded");
        executeNextPattern(entity, definition(snapshot.boss.bossId), snapshot.boss);
    }

    public void cleanup() {
        cooperationChannelActive = false;
        if (healthBar != null) {
            healthBar.removeAll();
            healthBar = null;
        }
    }

    private void enterPhaseTwo(LivingEntity entity, ProductionContentCatalog.BossEntry definition) {
        runs.mutate(run -> {
            run.boss.phase = 2;
            run.boss.channelParticipants.clear();
        });
        entity.setCustomName(ChatColor.DARK_PURPLE + definition.name() + ChatColor.GRAY + " [P2 적응]");
        entity.setAI(false);
        cooperationChannelActive = true;
        channelEndsAtTick = runs.clockTick() + 120L;
        int required = Math.min(2, Math.max(1, runs.effectivePartySize()));
        runs.broadcast(ChatColor.LIGHT_PURPLE + "[협동 중단] " + required
                + "명이 보스를 우클릭해 공명을 고정하세요. 6초");
        for (Player player : runs.onlineMembers()) {
            player.sendTitle(ChatColor.LIGHT_PURPLE + "공명 고정",
                    ChatColor.WHITE + "보스 우클릭 — " + required + "명 필요", 5, 80, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.65f);
        }
    }

    private void resolveCooperationChannel(LivingEntity entity, ProductionContentCatalog.BossEntry definition) {
        RunSnapshot.BossState state = runs.current().orElseThrow().boss;
        int required = Math.min(2, Math.max(1, runs.effectivePartySize()));
        double cooperationBreak = Math.max(300.0, definition.breakMax() * 0.03);
        if (state.channelParticipants.size() >= required) {
            cooperationChannelActive = false;
            entity.setAI(true);
            combat.applyBreak(entity, cooperationBreak);
            runs.mutate(run -> {
                run.boss.phaseTwoChannelResolved = true;
                run.boss.breakCurrent = combat.currentBreak(entity);
            });
            runs.broadcast(ChatColor.GREEN + "협동 중단 성공 — 고정 브레이크 +" + (int) cooperationBreak);
            nextPatternAtTick = runs.clockTick() + 80L;
            return;
        }
        if (runs.clockTick() < channelEndsAtTick) return;
        cooperationChannelActive = false;
        entity.setAI(true);
        runs.mutate(run -> run.boss.phaseTwoChannelResolved = true);
        runs.broadcast(ChatColor.RED + "협동 중단 실패 — 공명 파동");
        for (Player player : activePlayers()) combat.damagePlayerFromPattern(entity, player, definition.attackDamage());
        nextPatternAtTick = runs.clockTick() + 80L;
    }

    private void enterPhaseThree(LivingEntity entity, ProductionContentCatalog.BossEntry definition) {
        cooperationChannelActive = false;
        entity.setAI(false);
        combat.setCombatEntityNumber(entity, "break", 0.0);
        runs.mutate(run -> {
            run.boss.phase = 3;
            run.boss.breakCurrent = 0.0;
        });
        entity.setCustomName(ChatColor.DARK_RED + definition.name() + ChatColor.GRAY + " [P3 과부하]");
        runs.broadcast(ChatColor.DARK_RED + "[보스 P3] 최종 패턴 풀 진입 — 위치 교대와 브레이크 집중");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) entity.setAI(true);
        }, 50L);
        nextPatternAtTick = runs.clockTick() + 50L;
    }

    private void restoreCooperationChannel(LivingEntity entity, RunSnapshot.BossState state) {
        if (state.phase != 2 || state.phaseTwoChannelResolved) return;
        entity.setAI(false);
        cooperationChannelActive = true;
        channelEndsAtTick = runs.clockTick() + 120L;
        runs.broadcast(ChatColor.LIGHT_PURPLE + "[복구] 중단된 협동 공명 고정을 6초 상태로 재개합니다.");
    }

    private void executeNextPattern(LivingEntity entity, ProductionContentCatalog.BossEntry definition,
                                    RunSnapshot.BossState state) {
        ProductionContentCatalog.ActionBundleEntry bundle = production.actionBundlesById().get(definition.actionBundleId());
        if (bundle == null || bundle.actions().isEmpty()) throw new IllegalStateException("Missing boss actions " + definition.id());
        int phaseOffset = Math.max(0, Math.min(2, state.phase - 1)) * 4;
        int phaseSize = Math.min(4, bundle.actions().size() - phaseOffset);
        ProductionContentCatalog.ActionEntry action = bundle.actions().get(phaseOffset
                + (int) Math.floorMod(state.patternSequence, phaseSize));
        runs.mutate(run -> run.boss.patternSequence++);
        runs.broadcast(ChatColor.RED + "⚠ " + action.name() + " — 전조 후 범위 이탈");
        entity.getWorld().spawnParticle(Particle.DUST_PLUME, entity.getLocation(), 18,
                action.range(), 0.15, action.range(), 0.0);
        for (Player player : activePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f,
                    state.phase == 1 ? 0.8f : state.phase == 2 ? 0.65f : 0.5f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!entity.isValid()) return;
            for (Player player : activePlayers()) {
                if (player.getWorld().equals(entity.getWorld())
                        && player.getLocation().distanceSquared(entity.getLocation()) <= action.range() * action.range()) {
                    combat.damagePlayerFromPattern(entity, player, action.damage());
                }
            }
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
        }, Math.max(1L, action.telegraphTicks()));
        nextPatternAtTick = runs.clockTick() + Math.max(40L, action.cooldownTicks());
    }

    private void defeat(LivingEntity entity) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        int dayNumber = snapshot.day;
        ProductionContentCatalog.BossEntry definition = definition(snapshot.boss.bossId);
        entity.remove();
        cleanup();
        boolean committed = runs.commitOnce("boss-reward:day" + dayNumber, "BOSS_DEFEATED",
                "{\"bossId\":\"" + definition.id() + "\"}", run -> {
                    run.boss.state = "DEFEATED";
                    run.boss.rewardCommitted = true;
                    run.defeatedBossIds.add(definition.id());
                    run.seasonDay.activityExpCommitted = true;
                });
        if (!committed) return;
        ProductionContentCatalog.DayEntry day = production.daysByNumber().get(dayNumber);
        growth.awardSeasonExp(day.activityExp(), "day-" + dayNumber + "-activity", true);
        loot.rewardBoss("boss-day-" + dayNumber, definition, runs.onlineMembers());
        runs.broadcast(ChatColor.GOLD + definition.name() + " 격파. 재건 증명과 부품이 정산되었습니다.");
        telemetry.event(snapshot.runId, "BOSS_REWARD_COMMITTED", "{\"day\":" + dayNumber + "}");
    }

    private void createHealthBar(LivingEntity entity, ProductionContentCatalog.BossEntry definition) {
        cleanup();
        healthBar = Bukkit.createBossBar("Day " + definition.firstDay() + " — " + definition.name(),
                BarColor.RED, BarStyle.SEGMENTED_10);
        runs.onlineMembers().forEach(healthBar::addPlayer);
        healthBar.setVisible(true);
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot != null && snapshot.boss != null) updateHealthBar(snapshot.boss, definition);
    }

    private void updateHealthBar(RunSnapshot.BossState state, ProductionContentCatalog.BossEntry definition) {
        if (healthBar == null) return;
        healthBar.setProgress(Math.max(0.0, Math.min(1.0, state.hp / state.maxHp)));
        healthBar.setTitle("Day " + definition.firstDay() + " — " + definition.name() + " P" + state.phase
                + "  " + Math.round(state.hp) + "/" + Math.round(state.maxHp));
    }

    private LivingEntity spawnEntity(ProductionContentCatalog.BossEntry definition, Location location) {
        EntityType type = EntityType.valueOf(definition.bukkitType());
        if (!type.isAlive()) throw new IllegalArgumentException("Boss entity type is not living " + type);
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, type);
        entity.setCustomName(ChatColor.DARK_RED + definition.name() + ChatColor.GRAY + " [P1]");
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        return entity;
    }

    private void initializeVanillaHealth(LivingEntity entity) {
        if (entity.getAttribute(Attribute.MAX_HEALTH) == null) return;
        entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
        entity.setHealth(40.0);
    }

    private ProductionContentCatalog.BossEntry definition(String id) {
        ProductionContentCatalog.BossEntry result = production.bossesById().get(id);
        if (result == null) throw new IllegalArgumentException("Unknown Season 1 boss " + id);
        return result;
    }

    private List<Player> activePlayers() {
        return runs.onlineMembers().stream().filter(player -> runs.playerState(player.getUniqueId())
                .map(state -> "ACTIVE".equals(state.lifeState)).orElse(false)).toList();
    }

    private Location safeSpawn(Location requested) {
        Location result = requested.clone();
        result.setY(requested.getWorld().getHighestBlockYAt(requested) + 1.0);
        return result;
    }

    private Entity findEntity(String uuid) {
        if (uuid == null) return null;
        UUID id = UUID.fromString(uuid);
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
