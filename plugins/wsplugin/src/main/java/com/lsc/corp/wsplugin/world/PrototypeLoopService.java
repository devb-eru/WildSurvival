package com.lsc.corp.wsplugin.world;

import com.lsc.corp.wsplugin.boss.PrototypeBossService;
import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.economy.EconomyService;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.PlayerStatService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.ui.ActionBarService;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class PrototypeLoopService implements Listener {
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent content;
    private final EconomyService economy;
    private final CombatService combat;
    private final PrototypeBossService boss;
    private final GrowthService growth;
    private final PlayerStatService stats;
    private final TelemetryService telemetry;
    private long nextCheckpointAtEpochMs;
    private long dayTenBossAtEpochMs;
    private Location corruptionCenter;
    private int tickCounter;
    private boolean completionScheduled;
    private BukkitTask dayOneEncounterTask;

    public PrototypeLoopService(JavaPlugin plugin, RunService runs, PrototypeContent content, EconomyService economy,
                                CombatService combat, PrototypeBossService boss, GrowthService growth,
                                PlayerStatService stats, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.economy = economy;
        this.combat = combat;
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
        growth.awardCheckpointTarget(1);
        startDayEvent(1);
        scheduleNextCheckpoint(3);
    }

    public void restoreWorldObjects() {
        economy.restoreFacility();
        boss.restore();
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state)) {
            return;
        }
        rebuildCheckpointTimer(snapshot.day);
        if (snapshot.day >= 6) {
            corruptionCenter = facilityOrAnchor();
        }
        if (snapshot.day == 1 && !snapshot.committedKeys.contains("day-1-encounter-spawned")) {
            scheduleDayOneEncounter();
        }
    }

    public void tick() {
        combat.tick();
        boss.tick();
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state)) {
            return;
        }
        long now = runs.clockNowMillis();
        if (snapshot.day < 10 && nextCheckpointAtEpochMs > 0 && now >= nextCheckpointAtEpochMs) {
            advanceCheckpoint();
            snapshot = runs.current().orElseThrow();
        }
        if (snapshot.day == 10 && (snapshot.boss == null || (!snapshot.boss.rewardCommitted && !"ACTIVE".equals(snapshot.boss.state)))
                && dayTenBossAtEpochMs > 0 && now >= dayTenBossAtEpochMs) {
            boss.spawn();
            dayTenBossAtEpochMs = 0L;
        }
        if (snapshot.boss != null && snapshot.boss.rewardCommitted && growth.partyAugmentSelected() && !completionScheduled) {
            completionScheduled = true;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    runs.complete("DAY10_BOSS_AND_PARTY_AUGMENT");
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }, 80L);
        }
        if (snapshot.boss != null && snapshot.boss.rewardCommitted && !growth.partyAugmentSelected()) {
            growth.startPartyVoteWhenReady();
        }
        if (++tickCounter % 10 == 0) {
            tickCorruption();
        }
    }

    public void forceAdvance() {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (!"RUNNING".equals(snapshot.state)) {
            throw new IllegalStateException("Run is not running");
        }
        if (snapshot.day < 10) {
            advanceCheckpoint();
        } else if (snapshot.boss == null) {
            boss.spawn();
        }
    }

    public boolean isCombatActive() {
        RunSnapshot snapshot = runs.current().orElse(null);
        return snapshot != null && "RUNNING".equals(snapshot.state)
                && (combat.hasActiveEnemies() || snapshot.boss != null && "ACTIVE".equals(snapshot.boss.state));
    }

    public void cleanupWorldObjects() {
        if (dayOneEncounterTask != null) {
            dayOneEncounterTask.cancel();
            dayOneEncounterTask = null;
        }
        economy.removeFacility();
        combat.cleanupCombatEntities();
        corruptionCenter = null;
    }

    private void advanceCheckpoint() {
        RunSnapshot snapshot = runs.current().orElseThrow();
        int nextDay = switch (snapshot.day) {
            case 1 -> 3;
            case 3 -> 6;
            case 6 -> 10;
            default -> throw new IllegalStateException("No checkpoint after Day " + snapshot.day);
        };
        runs.commitOnce("day-start:" + nextDay, "DAY_STARTED", "{\"day\":" + nextDay + "}", run -> {
            run.day = nextDay;
            run.checkpointStartedAtEpochMs = runs.clockNowMillis();
        });
        if (nextDay == 10) {
            int preBossTarget = content.progressExpByDay().get(10) - content.boss().rewardExp();
            growth.awardTargetExp(preBossTarget, "day10-preboss");
        } else {
            growth.awardCheckpointTarget(nextDay);
        }
        startDayEvent(nextDay);
        if (nextDay < 10) {
            scheduleNextCheckpoint(nextDay == 3 ? 6 : 10);
        } else {
            nextCheckpointAtEpochMs = 0L;
            dayTenBossAtEpochMs = runs.clockNowMillis() + 60_000L;
            runs.broadcast(ChatColor.GOLD + "Day 10 보스 게이트 개방. 60초 뒤 자동 호출됩니다. /ws craft로 최종 준비하세요.");
        }
    }

    private void startDayEvent(int day) {
        switch (day) {
            case 1 -> {
                runs.broadcast(ChatColor.YELLOW + "[사건: 잔해 수색] 자연 자원을 채집하고 첫 장비를 제작하세요.");
                runs.broadcast(ChatColor.AQUA + "[길잡이] L키에서 채집→Craft 해금→도구→무기 순서를 확인하세요.");
                scheduleDayOneEncounter();
            }
            case 3 -> {
                runs.broadcast(ChatColor.YELLOW + "[사건: 원거리 압박] 엄폐와 회피로 뼈 사수를 제거하세요.");
                spawnGroup("EN-D2-01", Math.max(2, runs.effectivePartySize()));
                spawnGroup("EN-D1-01", 1);
            }
            case 6 -> {
                runs.broadcast(ChatColor.LIGHT_PURPLE + "[사건: 오염 파열] 장갑 적 브레이크와 오염 지대를 함께 관리하세요.");
                spawnGroup("EN-D4-01", Math.max(1, runs.effectivePartySize() - 1));
                spawnGroup("EN-D8-E01", 1);
                corruptionCenter = facilityOrAnchor();
            }
            case 10 -> runs.broadcast(ChatColor.DARK_RED + "[사건: 공명 추적] 보스 호출 신호가 수렴합니다.");
            default -> throw new IllegalArgumentException("Unsupported prototype day " + day);
        }
        if (day != 1) {
            telemetry.event(runs.current().orElseThrow().runId, "ENCOUNTER_COMMITTED", "{\"day\":" + day + "}");
        }
    }

    private void spawnGroup(String enemyId, int count) {
        PrototypeContent.EnemyDefinition definition = content.enemy(enemyId);
        List<Player> members = runs.onlineMembers();
        if (members.isEmpty()) {
            return;
        }
        for (int i = 0; i < count; i++) {
            Player anchor = members.get(i % members.size());
            double angle = Math.PI * 2.0 * i / Math.max(1, count);
            int radius = plugin.getConfig().getInt("prototype.spawn-radius", 10);
            Location requested = anchor.getLocation().clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            requested.setY(requested.getWorld().getHighestBlockYAt(requested) + 1.0);
            LivingEntity entity = combat.spawnEnemy(definition, requested);
            entity.setRemoveWhenFarAway(false);
        }
    }

    private void scheduleDayOneEncounter() {
        if (dayOneEncounterTask != null) dayOneEncounterTask.cancel();
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state) || snapshot.day != 1
                || snapshot.committedKeys.contains("day-1-encounter-spawned")) return;
        int graceSeconds = Math.max(0, plugin.getConfig().getInt("prototype.day-1-grace-seconds", 90));
        long targetEpochMs = snapshot.startedAtEpochMs + graceSeconds * 1000L;
        long remainingMs = Math.max(0L, targetEpochMs - runs.clockNowMillis());
        long delayTicks = Math.max(1L, (remainingMs + 49L) / 50L);
        runs.broadcast(ChatColor.GREEN + "첫 습격까지 " + Math.max(1L, (remainingMs + 999L) / 1000L)
                + "초: 급조 곡괭이와 첫 무기를 준비하세요.");
        dayOneEncounterTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            dayOneEncounterTask = null;
            RunSnapshot current = runs.current().orElse(null);
            if (current == null || !"RUNNING".equals(current.state) || current.day != 1) return;
            if (!runs.commitOnce("day-1-encounter-spawned", "ENCOUNTER_COMMITTED", "{\"day\":1}", run -> { })) return;
            spawnGroup("EN-D1-01", Math.max(2, runs.effectivePartySize()));
            runs.broadcast(ChatColor.RED + "[습격] 부패한 배회자가 접근합니다.");
        }, delayTicks);
    }

    private void scheduleNextCheckpoint(int targetDay) {
        int seconds = switch (targetDay) {
            case 3 -> plugin.getConfig().getInt("prototype.checkpoint-seconds.day-3", 600);
            case 6 -> plugin.getConfig().getInt("prototype.checkpoint-seconds.day-6", 1500);
            case 10 -> plugin.getConfig().getInt("prototype.checkpoint-seconds.day-10", 2700);
            default -> throw new IllegalArgumentException("Unknown target checkpoint " + targetDay);
        };
        RunSnapshot snapshot = runs.current().orElseThrow();
        nextCheckpointAtEpochMs = snapshot.startedAtEpochMs + seconds * 1000L;
    }

    private void rebuildCheckpointTimer(int currentDay) {
        if (currentDay == 1) {
            scheduleNextCheckpoint(3);
        } else if (currentDay == 3) {
            scheduleNextCheckpoint(6);
        } else if (currentDay == 6) {
            scheduleNextCheckpoint(10);
        } else if (currentDay == 10) {
            RunSnapshot snapshot = runs.current().orElseThrow();
            if (snapshot.boss == null) {
                dayTenBossAtEpochMs = runs.clockNowMillis() + 15_000L;
            }
        }
    }

    private void tickCorruption() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.day < 6 || corruptionCenter == null || corruptionCenter.getWorld() == null) {
            return;
        }
        corruptionCenter.getWorld().spawnParticle(Particle.WITCH, corruptionCenter.clone().add(0, 1, 0), 14, 3.0, 0.7, 3.0, 0.02);
        for (Player player : runs.onlineMembers()) {
            if (player.getWorld().equals(corruptionCenter.getWorld()) && player.getLocation().distanceSquared(corruptionCenter) <= 16.0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, true, true));
                if (tickCounter % 40 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.25f, 1.5f);
                    ActionBarService.notice(player, net.kyori.adventure.text.Component.text("☣ 오염 지대 — 이동 둔화",
                            net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE), 30);
                }
            }
        }
    }

    private Location facilityOrAnchor() {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (snapshot.facility != null && snapshot.facility.active) {
            org.bukkit.World world = Bukkit.getWorld(snapshot.facility.world);
            if (world != null) {
                return new Location(world, snapshot.facility.x + 0.5, snapshot.facility.y, snapshot.facility.z + 0.5);
            }
        }
        return runs.onlineMembers().stream().findFirst().map(Player::getLocation)
                .orElseThrow(() -> new IllegalStateException("No anchor for corruption zone"));
    }
}
