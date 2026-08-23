package com.lsc.corp.wsplugin.boss;

import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.ui.ActionBarService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
    private final PrototypeContent content;
    private final CombatService combat;
    private final GrowthService growth;
    private final TelemetryService telemetry;
    private BossBar healthBar;
    private long nextPatternAtTick;
    private long channelEndsAtTick;
    private boolean cooperationChannelActive;

    public PrototypeBossService(JavaPlugin plugin, RunService runs, PrototypeContent content, CombatService combat,
                                GrowthService growth, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.combat = combat;
        this.growth = growth;
        this.telemetry = telemetry;
    }

    public LivingEntity spawn() {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (!"RUNNING".equals(snapshot.state) || snapshot.day != 10) {
            throw new IllegalStateException("Day 10 running state is required");
        }
        if (snapshot.boss != null && ("ACTIVE".equals(snapshot.boss.state) || snapshot.boss.rewardCommitted)) {
            throw new IllegalStateException("Day 10 boss is already active or completed");
        }
        Player anchor = runs.onlineMembers().stream().findFirst().orElseThrow(() -> new IllegalStateException("No online member"));
        Location location = safeSpawn(anchor.getLocation().add(anchor.getLocation().getDirection().setY(0).normalize().multiply(10)));
        PrototypeContent.BossDefinition definition = content.boss();
        int players = Math.max(1, runs.effectivePartySize());
        double hpMultiplier = switch (players) { case 1 -> 0.72; case 3 -> 1.32; case 4 -> 1.60; default -> 1.0; };
        double breakMultiplier = switch (players) { case 1 -> 0.75; case 3 -> 1.25; case 4 -> 1.50; default -> 1.0; };
        double maxHp = definition.hp() * hpMultiplier;
        double maxBreak = definition.breakMax() * breakMultiplier;
        LivingEntity boss = (LivingEntity) location.getWorld().spawnEntity(location, EntityType.RAVAGER);
        boss.setCustomName(ChatColor.DARK_RED + "공명 추적체" + ChatColor.GRAY + " [P1 추적]");
        boss.setCustomNameVisible(true);
        combat.tagCombatEntity(boss, definition.id(), maxHp, 70.0, maxBreak);
        if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
            boss.setHealth(40.0);
        }
        runs.commitOnce("boss-spawn:day10", "BOSS_ACTIVATED", "{\"bossId\":\"" + definition.id() + "\",\"players\":" + players + "}", run -> {
            RunSnapshot.BossState state = new RunSnapshot.BossState();
            state.bossId = definition.id();
            state.entityUuid = boss.getUniqueId().toString();
            state.world = location.getWorld().getName();
            state.x = location.getX();
            state.y = location.getY();
            state.z = location.getZ();
            state.hp = maxHp;
            state.maxHp = maxHp;
            state.breakMax = maxBreak;
            run.boss = state;
        });
        createHealthBar(boss);
        nextPatternAtTick = runs.clockTick() + 80L;
        runs.broadcast(ChatColor.DARK_RED + "[Day 10] 공명 추적체가 출현했습니다. 2페이즈 협동 중단에 대비하세요.");
        return boss;
    }

    public void restore() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.boss == null || !"ACTIVE".equals(snapshot.boss.state)) {
            return;
        }
        Entity existing = findEntity(snapshot.boss.entityUuid);
        if (existing instanceof LivingEntity living) {
            createHealthBar(living);
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(snapshot.boss.world);
        if (world == null) {
            throw new IllegalStateException("Cannot restore boss world " + snapshot.boss.world);
        }
        LivingEntity boss = (LivingEntity) world.spawnEntity(new Location(world, snapshot.boss.x, snapshot.boss.y, snapshot.boss.z), EntityType.RAVAGER);
        boss.customName(Component.text(ChatColor.DARK_RED + "공명 추적체" + ChatColor.GRAY + " [복구]"));
        boss.setCustomNameVisible(true);
        combat.tagCombatEntity(boss, snapshot.boss.bossId, snapshot.boss.hp, 70.0, snapshot.boss.breakMax);
        runs.mutate(run -> run.boss.entityUuid = boss.getUniqueId().toString());
        createHealthBar(boss);
    }

    public void tick() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.boss == null || !"ACTIVE".equals(snapshot.boss.state)) {
            return;
        }
        Entity found = findEntity(snapshot.boss.entityUuid);
        if (!(found instanceof LivingEntity boss) || !boss.isValid()) {
            restore();
            return;
        }
        updateHealthBar(snapshot.boss);
        if (cooperationChannelActive) {
            resolveCooperationChannel(boss);
            return;
        }
        long tick = runs.clockTick();
        if (tick >= nextPatternAtTick) {
            telegraphPulse(boss, snapshot.boss.phase);
            nextPatternAtTick = tick + (snapshot.boss.phase == 1 ? 180L : 140L);
        }
    }

    @Override
    public void damage(Player attacker, LivingEntity boss, double damage, double breakDamage, String executionId) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (snapshot.boss == null || !boss.getUniqueId().toString().equals(snapshot.boss.entityUuid)) {
            return;
        }
        boolean committed = runs.commitOnce("boss-hit:" + executionId, "BOSS_DAMAGE_COMMITTED",
                "{\"damage\":" + round(damage) + ",\"break\":" + round(breakDamage) + "}", run -> {
                    run.boss.hp = Math.max(0.0, run.boss.hp - damage);
                    run.boss.x = boss.getLocation().getX();
                    run.boss.y = boss.getLocation().getY();
                    run.boss.z = boss.getLocation().getZ();
                });
        if (!committed) {
            return;
        }
        combat.applyBreak(boss, breakDamage);
        runs.mutate(run -> run.boss.breakCurrent = combat.currentBreak(boss));
        RunSnapshot.BossState state = runs.current().orElseThrow().boss;
        if (state.phase == 1 && state.hp / state.maxHp <= content.boss().phaseTwoHpPercent() / 100.0) {
            enterPhaseTwo(boss);
        }
        if (state.hp <= 0.0) {
            defeat(boss);
        }
    }

    @EventHandler
    public void onBossInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !cooperationChannelActive || !runs.isRunningMember(event.getPlayer())) {
            return;
        }
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.boss == null || !event.getRightClicked().getUniqueId().toString().equals(snapshot.boss.entityUuid)) {
            return;
        }
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
        if (!(found instanceof LivingEntity boss)) {
            throw new IllegalStateException("Test boss entity is not loaded");
        }
        if (snapshot.boss.phase < 2) {
            enterPhaseTwo(boss);
        }
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
        if (!(found instanceof LivingEntity boss)) {
            throw new IllegalStateException("Test boss entity is not loaded");
        }
        telegraphPulse(boss, snapshot.boss.phase);
    }

    private void enterPhaseTwo(LivingEntity boss) {
        runs.mutate(run -> {
            run.boss.phase = 2;
            run.boss.channelParticipants.clear();
        });
        boss.setCustomName(ChatColor.DARK_PURPLE + "공명 추적체" + ChatColor.GRAY + " [P2 적응]");
        boss.setAI(false);
        cooperationChannelActive = true;
        channelEndsAtTick = runs.clockTick() + content.boss().cooperationChannelTicks();
        int required = Math.min(2, Math.max(1, runs.effectivePartySize()));
        runs.broadcast(ChatColor.LIGHT_PURPLE + "[협동 중단] " + required + "명이 보스를 우클릭해 공명을 고정하세요. 6초");
        for (Player player : runs.onlineMembers()) {
            player.sendTitle(ChatColor.LIGHT_PURPLE + "공명 고정", ChatColor.WHITE + "보스 우클릭 — " + required + "명 필요", 5, 80, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.65f);
        }
    }

    private void resolveCooperationChannel(LivingEntity boss) {
        RunSnapshot.BossState state = runs.current().orElseThrow().boss;
        int required = Math.min(2, Math.max(1, runs.effectivePartySize()));
        if (state.channelParticipants.size() >= required) {
            cooperationChannelActive = false;
            boss.setAI(true);
            combat.applyBreak(boss, content.boss().cooperationBreak());
            runs.mutate(run -> {
                run.boss.phaseTwoChannelResolved = true;
                run.boss.breakCurrent = combat.currentBreak(boss);
            });
            runs.broadcast(ChatColor.GREEN + "협동 중단 성공 — 고정 브레이크 +" + (int) content.boss().cooperationBreak());
            nextPatternAtTick = runs.clockTick() + 80L;
            return;
        }
        if (runs.clockTick() < channelEndsAtTick) {
            return;
        }
        cooperationChannelActive = false;
        boss.setAI(true);
        runs.mutate(run -> run.boss.phaseTwoChannelResolved = true);
        runs.broadcast(ChatColor.RED + "협동 중단 실패 — 공명 파동");
        for (Player player : runs.onlineMembers()) {
            if (runs.playerState(player.getUniqueId()).map(value -> "ACTIVE".equals(value.lifeState)).orElse(false)) {
                player.damage(8.0);
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.8f);
            }
        }
        nextPatternAtTick = runs.clockTick() + 80L;
    }

    private void telegraphPulse(LivingEntity boss, int phase) {
        String label = phase == 1 ? "추적 돌진" : "붕괴 고리";
        runs.broadcast(ChatColor.RED + "⚠ " + label + " — 흰 입자와 경고음 뒤 회피");
        for (Player player : runs.onlineMembers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, phase == 1 ? 0.8f : 0.55f);
            player.sendTitle(ChatColor.RED + "⚠ " + label, ChatColor.WHITE + "회피 준비", 0, 25, 5);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!boss.isValid()) {
                return;
            }
            double radius = phase == 1 ? 4.5 : 7.0;
            for (Player player : runs.onlineMembers()) {
                if (player.getLocation().getWorld().equals(boss.getWorld())
                        && player.getLocation().distanceSquared(boss.getLocation()) <= radius * radius) {
                    player.damage(phase == 1 ? 6.0 : 8.0);
                }
            }
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
        }, 24L);
    }

    private void defeat(LivingEntity boss) {
        boss.remove();
        if (healthBar != null) {
            healthBar.removeAll();
            healthBar = null;
        }
        boolean committed = runs.commitOnce("boss-reward:day10", "BOSS_DEFEATED", "{\"bossId\":\"" + content.boss().id() + "\"}", run -> {
            run.boss.state = "DEFEATED";
            run.boss.rewardCommitted = true;
        });
        if (!committed) {
            return;
        }
        for (Player player : runs.onlineMembers()) {
            growth.awardExp(player, content.boss().rewardExp(), "boss-exp:day10:" + player.getUniqueId());
        }
        runs.broadcast(ChatColor.GOLD + "공명 추적체 격파. 개인 프리즘 선택 후 파티 증강 투표가 열립니다.");
        telemetry.event(runs.current().orElseThrow().runId, "BOSS_REWARD_COMMITTED", "{\"step\":1}");
    }

    private void createHealthBar(LivingEntity boss) {
        if (healthBar != null) {
            healthBar.removeAll();
        }
        healthBar = Bukkit.createBossBar("Day 10 — 공명 추적체", BarColor.RED, BarStyle.SEGMENTED_10);
        runs.onlineMembers().forEach(healthBar::addPlayer);
        healthBar.setVisible(true);
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot != null && snapshot.boss != null) {
            updateHealthBar(snapshot.boss);
        }
    }

    private void updateHealthBar(RunSnapshot.BossState state) {
        if (healthBar == null) {
            return;
        }
        healthBar.setProgress(Math.max(0.0, Math.min(1.0, state.hp / state.maxHp)));
        healthBar.setTitle("Day 10 — 공명 추적체 P" + state.phase + "  " + Math.round(state.hp) + "/" + Math.round(state.maxHp));
    }

    private Location safeSpawn(Location requested) {
        Location result = requested.clone();
        result.setY(requested.getWorld().getHighestBlockYAt(requested) + 1.0);
        return result;
    }

    private Entity findEntity(String uuid) {
        if (uuid == null) {
            return null;
        }
        UUID id = UUID.fromString(uuid);
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
