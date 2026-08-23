package com.lsc.corp.wsplugin.finale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.economy.ItemCodexService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.story.StoryService;
import com.lsc.corp.wsplugin.world.DiscoveryService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class FinalService implements Listener, CombatService.BossDamageHandler {
    private static final String OBJECTIVE_ID = "FINAL-D50-FIRST-RECONSTRUCTION-SIGNAL";
    private static final String FINAL_BOSS_COMBAT_ID = "BOSS-FINAL-WORLD-COLLAPSE-CORE";
    private static final List<String> PHASE_ONE = List.of("F50-COLLAPSE_CUT", "F50-STATUS_QUADRANT",
            "F50-TRACK_LINE", "F50-ECHO_SUMMON");
    private static final List<String> PHASE_TWO = List.of("F50-MUTATION_ROTATE", "F50-PURIFY_BACKFLOW",
            "F50-FACILITY_JAM", "F50-SYNAPSE_CORE");
    private static final List<String> PHASE_THREE = List.of("F50-FRACTURE_CHANNEL", "F50-LOCKED_RING",
            "F50-THREE_CORES", "F50-FINAL_COLLAPSE");

    private final JavaPlugin plugin;
    private final RunService runs;
    private final ProductionContentCatalog production;
    private final ItemCodexService codex;
    private final CombatService combat;
    private final DiscoveryService discoveries;
    private final StoryService story;
    private final Set<String> activeChannels = new HashSet<>();
    private BossBar bossBar;

    public FinalService(JavaPlugin plugin, RunService runs, ProductionContentCatalog production,
                        ItemCodexService codex, CombatService combat, DiscoveryService discoveries,
                        StoryService story) {
        this.plugin = plugin;
        this.runs = runs;
        this.production = production;
        this.codex = codex;
        this.combat = combat;
        this.discoveries = discoveries;
        this.story = story;
    }

    public boolean handles(LivingEntity entity) {
        RunSnapshot run = runs.current().orElse(null);
        return run != null && run.finalObjective != null && run.finalObjective.finalBossEntityUuid != null
                && run.finalObjective.finalBossEntityUuid.equals(entity.getUniqueId().toString());
    }

    public void restore() {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null || run.finalObjective == null) return;
        if ("ACTIVE_STAGE_2".equals(run.finalObjective.state) && !run.finalObjective.coreSubdued) {
            LivingEntity existing = finalBossEntity(run.finalObjective);
            if (existing == null) spawnFinalBoss(true);
            else createBossBar(existing);
        }
    }

    public void tick() {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null || !"RUNNING".equals(run.state) || run.finalObjective == null) return;
        switch (run.finalObjective.state) {
            case "LOCKED", "AVAILABLE" -> refreshAvailability(run);
            case "ACTIVATING" -> startStageOne();
            case "ACTIVE_STAGE_1" -> tickStageOne(run);
            case "ACTIVE_STAGE_2" -> tickStageTwo(run);
            case "ACTIVE_STAGE_3" -> tickStageThree(run);
            case "RESOLVING" -> resolveCompletion();
            default -> { }
        }
    }

    public void open(Player player) {
        if (!runs.isMember(player)) {
            player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다.");
            return;
        }
        RunSnapshot run = runs.current().orElseThrow();
        refreshAvailability(run);
        run = runs.current().orElseThrow();
        List<String> missing = missingRequirements(run);
        Inventory inventory = Bukkit.createInventory(new FinalHolder(player.getUniqueId()), 54,
                ChatColor.DARK_PURPLE + "첫 재건 신호");
        inventory.setItem(4, named(Material.BEACON, ChatColor.GOLD + "Final · " + run.finalObjective.state, List.of(
                ChatColor.WHITE + "Day " + run.day + " · Stage " + run.finalObjective.stage,
                ChatColor.GRAY + "Day 50 전에는 어떤 진입 경로로도 활성화할 수 없습니다.")));
        inventory.setItem(20, named(Material.LODESTONE, ChatColor.AQUA + "활성 조건",
                missing.isEmpty() ? List.of(ChatColor.GREEN + "모든 시작 조건 충족")
                        : missing.stream().map(value -> ChatColor.RED + "- " + value).toList()));
        inventory.setItem(22, named(Material.RESPAWN_ANCHOR, ChatColor.LIGHT_PURPLE + "진행 상태",
                progressLore(run.finalObjective)));
        int requiredVotes = FinalPolicy.majority(runs.survivableCount());
        inventory.setItem(31, named(missing.isEmpty() ? Material.LIME_DYE : Material.GRAY_DYE,
                missing.isEmpty() ? ChatColor.GREEN + "활성 준비 투표" : ChatColor.GRAY + "활성 불가", List.of(
                        ChatColor.WHITE + "준비 " + run.finalObjective.activationVotes.size() + "/" + requiredVotes,
                        ChatColor.GRAY + "과반 준비 후 전장 Manifest를 만들고 키를 소비합니다.")));
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        player.openInventory(inventory);
    }

    public List<String> missingRequirements(RunSnapshot run) {
        List<String> missing = new ArrayList<>();
        if (run.day < 50) missing.add("FINAL_DAY_LOCK · Day 50 필요");
        requireAll(missing, "보스", run.defeatedBossIds, Set.of("BOSS-D10", "BOSS-D20", "BOSS-D30", "BOSS-D40"));
        requireAll(missing, "재건 부품", run.reconstructionPartIds, Set.of("A", "B", "C", "D"));
        requireAll(missing, "발견", run.discoveryIds, Set.of("C27", "C28-A", "C28-B", "C28-C", "C28-D", "C29"));
        for (String type : List.of("FAC-R01", "FAC-R02", "FAC-R03", "FAC-R04", "FAC-R06")) {
            if (run.facilities.values().stream().noneMatch(value -> type.equals(value.facilityType)
                    && "READY".equals(value.state))) missing.add(type + " READY");
        }
        long stakes = run.facilities.values().stream().filter(value -> "FAC-R05".equals(value.facilityType)
                && "CALIBRATED".equals(value.state)).count();
        if (stakes < 3) missing.add("FAC-R05 CALIBRATED 3기 (현재 " + stakes + ")");
        if (runs.onlineMembers().stream().noneMatch(player -> codex.countItem(player, "WSR-FINAL_SIGNAL_KEY") > 0)) {
            missing.add("WSR-FINAL_SIGNAL_KEY 소지자 온라인");
        }
        if (run.boss != null && "ACTIVE".equals(run.boss.state)) missing.add("활성 보스 종료");
        if (run.encounters.values().stream().anyMatch(value -> Set.of("SPAWNING", "ACTIVE").contains(value.state))) {
            missing.add("활성 공세 종료");
        }
        return List.copyOf(missing);
    }

    public void vote(Player player) {
        RunSnapshot run = runs.current().orElseThrow();
        if (!"AVAILABLE".equals(run.finalObjective.state) || !missingRequirements(run).isEmpty()) {
            player.sendMessage(ChatColor.RED + "Final 활성 조건이 아직 충족되지 않았습니다.");
            return;
        }
        runs.mutate(snapshot -> {
            RunSnapshot.FinalState state = snapshot.finalObjective;
            if (state.activationVoteStartedAtEpochMs == 0L) state.activationVoteStartedAtEpochMs = runs.clockNowMillis();
            if (!state.activationVotes.add(player.getUniqueId().toString())) state.activationVotes.remove(player.getUniqueId().toString());
        });
        run = runs.current().orElseThrow();
        int required = FinalPolicy.majority(runs.survivableCount());
        runs.broadcast(ChatColor.LIGHT_PURPLE + "[Final 준비] " + run.finalObjective.activationVotes.size() + "/" + required);
        if (run.finalObjective.activationVotes.size() < required) return;
        Player keyOwner = runs.onlineMembers().stream().filter(value -> codex.countItem(value, "WSR-FINAL_SIGNAL_KEY") > 0)
                .findFirst().orElse(null);
        if (keyOwner == null) return;
        RunSnapshot.FacilityInstanceState r06 = facility(run, "FAC-R06", "READY").stream().findFirst().orElse(null);
        if (r06 == null || Bukkit.getWorld(r06.world) == null) return;
        runs.commitOnce("final-activate:" + run.runId, "FINAL_ACTIVATION_MANIFEST_COMMITTED",
                "{\"objectiveId\":\"" + OBJECTIVE_ID + "\",\"partySize\":" + runs.effectivePartySize() + "}", snapshot -> {
                    RunSnapshot.FinalState state = snapshot.finalObjective;
                    state.state = "ACTIVATING";
                    state.lockedPartySize = runs.effectivePartySize();
                    state.uniqueInputOwnerUuid = keyOwner.getUniqueId().toString();
                    state.arenaManifestId = "final-arena:" + snapshot.runId;
                    state.arenaWorld = r06.world;
                    state.arenaX = r06.x + 0.5;
                    state.arenaY = r06.y + 1.0;
                    state.arenaZ = r06.z + 0.5;
                    state.activatedAtEpochMs = runs.clockNowMillis();
                });
        story.trigger("FINAL_STATE", "ACTIVATING");
    }

    @Override
    public void damage(Player attacker, LivingEntity boss, double damage, double breakDamage, String executionId) {
        if (!handles(boss)) return;
        boolean committed = runs.commitOnce("final-boss-hit:" + executionId, "FINAL_BOSS_DAMAGE_COMMITTED",
                "{\"damage\":" + damage + ",\"break\":" + breakDamage + "}", run -> {
                    RunSnapshot.FinalState state = run.finalObjective;
                    state.finalBossHp = Math.max(0.0, state.finalBossHp - damage);
                });
        if (!committed) return;
        combat.applyBreak(boss, breakDamage);
        RunSnapshot.FinalState state = runs.current().orElseThrow().finalObjective;
        if (state.finalBossHp <= 0.0) {
            subdueCore(boss);
            return;
        }
        double ratio = state.finalBossHp / state.finalBossMaxHp;
        int phase = ratio <= 0.38 ? 3 : ratio <= 0.72 ? 2 : 1;
        if (phase != state.finalBossPhase) {
            runs.mutate(run -> {
                run.finalObjective.finalBossPhase = phase;
                run.finalObjective.nextPatternAtTick = runs.clockTick() + 80L;
            });
            story.trigger("FINAL_PHASE", "F50-P" + phase);
            boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.6f);
        }
    }

    private void refreshAvailability(RunSnapshot run) {
        boolean available = missingRequirements(run).isEmpty();
        String desired = available ? "AVAILABLE" : "LOCKED";
        if (desired.equals(run.finalObjective.state)) return;
        runs.mutate(snapshot -> {
            snapshot.finalObjective.state = desired;
            if (!available) snapshot.finalObjective.activationVotes.clear();
        });
        if (available) {
            runs.broadcast(ChatColor.GOLD + "[Final] 첫 재건 신호를 활성화할 수 있습니다.");
            story.trigger("FINAL_STATE", "AVAILABLE");
        }
    }

    private void startStageOne() {
        RunSnapshot run = runs.current().orElseThrow();
        RunSnapshot.FinalState state = run.finalObjective;
        Player owner = state.uniqueInputOwnerUuid == null ? null : Bukkit.getPlayer(UUID.fromString(state.uniqueInputOwnerUuid));
        if (owner == null || codex.countItem(owner, "WSR-FINAL_SIGNAL_KEY") < 1) {
            rollbackActivation("FINAL_SIGNAL_KEY_MISSING");
            return;
        }
        List<RunSnapshot.FacilityInstanceState> stakes = facility(run, "FAC-R05", "CALIBRATED");
        if (stakes.size() < 3 || facility(run, "FAC-R06", "READY").isEmpty()) {
            rollbackActivation("FINAL_OBJECT_MANIFEST_INVALID");
            return;
        }
        List<Integer> budgets = stageOneBudgets(state.lockedPartySize);
        if (!codex.takeItem(owner, "WSR-FINAL_SIGNAL_KEY", 1)) {
            rollbackActivation("FINAL_SIGNAL_KEY_COMMIT_FAILED");
            return;
        }
        runs.commitOnce("final-stage1:" + run.runId, "FINAL_STAGE_STARTED", "{\"stage\":1}", snapshot -> {
            RunSnapshot.FinalState current = snapshot.finalObjective;
            current.uniqueInputReserved = true;
            current.state = "ACTIVE_STAGE_1";
            current.stage = 1;
            current.stage1WaveBudgets.clear();
            current.stage1WaveBudgets.addAll(budgets);
            current.stage1WaveIndex = 0;
            current.componentProgress.clear();
            stakes.stream().limit(3).forEach(value -> current.componentProgress.put("F50-STAKE:" + value.instanceId, 0));
            current.componentProgress.put("FAC-R06-OUTPUT", 0);
            current.activeEntityUuids.clear();
        });
        story.trigger("FINAL_STATE", "ACTIVE_STAGE_1");
        spawnNextStageOneWave();
    }

    private void tickStageOne(RunSnapshot run) {
        pruneFinalEntities(run.finalObjective);
        run = runs.current().orElseThrow();
        RunSnapshot.FinalState state = run.finalObjective;
        if (state.activeEntityUuids.isEmpty() && state.stage1WaveIndex < state.stage1WaveBudgets.size()) {
            spawnNextStageOneWave();
            return;
        }
        boolean objectives = state.componentProgress.entrySet().stream()
                .filter(value -> value.getKey().startsWith("F50-STAKE:")).count() == 3
                && state.componentProgress.entrySet().stream().filter(value -> value.getKey().startsWith("F50-STAKE:"))
                .allMatch(value -> value.getValue() >= 100)
                && state.componentProgress.getOrDefault("FAC-R06-OUTPUT", 0) >= 100;
        if (objectives && state.stage1WaveIndex >= state.stage1WaveBudgets.size() && state.activeEntityUuids.isEmpty()) {
            story.trigger("FINAL_STAKES_ALL_LOCKED", "");
            spawnFinalBoss(false);
        }
    }

    private void spawnNextStageOneWave() {
        RunSnapshot run = runs.current().orElseThrow();
        RunSnapshot.FinalState state = run.finalObjective;
        if (state.stage1WaveIndex >= state.stage1WaveBudgets.size()) return;
        int wave = state.stage1WaveIndex;
        int budget = state.stage1WaveBudgets.get(wave);
        int cap = state.lockedPartySize >= 3 ? 12 : state.lockedPartySize == 2 ? 10 : 8;
        int count = Math.max(1, Math.min(cap, (int) Math.ceil(budget / 10.0)));
        String enemyId = switch (wave) { case 0 -> "EN-F50-A03"; case 1 -> "EN-F50-A01"; case 2 -> "EN-F50-A02"; default -> "EN-F50-A04"; };
        Location center = arena(state);
        List<String> spawned = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0 * index / count;
            Location location = safe(center.clone().add(Math.cos(angle) * 10.0, 0.0, Math.sin(angle) * 10.0));
            LivingEntity enemy = combat.spawnProductionEnemy(enemyId, location, 1.0);
            spawned.add(enemy.getUniqueId().toString());
        }
        runs.mutate(snapshot -> {
            snapshot.finalObjective.stage1WaveIndex++;
            snapshot.finalObjective.activeEntityUuids.addAll(spawned);
        });
        runs.broadcast(ChatColor.RED + "[Final Stage 1] Wave " + (wave + 1) + "/" + state.stage1WaveBudgets.size());
    }

    private void spawnFinalBoss(boolean restoring) {
        RunSnapshot run = runs.current().orElseThrow();
        RunSnapshot.FinalState state = run.finalObjective;
        JsonObject bossData = finalRecord("FINAL-BOSS-WORLD-COLLAPSE-CORE");
        JsonObject profile = partyProfile(bossData.getAsJsonArray("partyProfiles"), state.lockedPartySize);
        double hp = restoring && state.finalBossHp > 0 ? state.finalBossHp : profile.get("hp").getAsDouble();
        double maxHp = restoring && state.finalBossMaxHp > 0 ? state.finalBossMaxHp : hp;
        double breakMax = restoring && state.finalBossBreakMax > 0 ? state.finalBossBreakMax : profile.get("breakMax").getAsDouble();
        Location location = arena(state).clone().add(0.0, 1.0, 0.0);
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, EntityType.RAVAGER);
        entity.setAI(false);
        entity.setRemoveWhenFarAway(false);
        entity.setCustomName(ChatColor.DARK_PURPLE + "세계 붕괴 핵");
        entity.setCustomNameVisible(true);
        Attribute maxHealthAttribute = Attribute.MAX_HEALTH;
        if (entity.getAttribute(maxHealthAttribute) != null) {
            entity.getAttribute(maxHealthAttribute).setBaseValue(40.0);
            entity.setHealth(40.0);
        }
        combat.tagCombatEntity(entity, FINAL_BOSS_COMBAT_ID, hp, bossData.get("defence").getAsDouble(), breakMax);
        runs.mutate(snapshot -> {
            RunSnapshot.FinalState current = snapshot.finalObjective;
            current.state = "ACTIVE_STAGE_2";
            current.stage = 2;
            current.finalBossEntityUuid = entity.getUniqueId().toString();
            current.finalBossHp = hp;
            current.finalBossMaxHp = maxHp;
            current.finalBossBreakMax = breakMax;
            if (current.finalBossPhase == 0) current.finalBossPhase = 1;
            current.nextPatternAtTick = runs.clockTick() + 80L;
            current.activeEntityUuids.clear();
            current.activeEntityUuids.add(entity.getUniqueId().toString());
        });
        createBossBar(entity);
        if (!restoring) {
            runs.broadcast(ChatColor.DARK_RED + "[Final Stage 2] 세계 붕괴 핵 출현");
            story.trigger("FINAL_PHASE", "F50-P1");
        }
    }

    private void tickStageTwo(RunSnapshot run) {
        RunSnapshot.FinalState state = run.finalObjective;
        LivingEntity boss = finalBossEntity(state);
        if (boss == null) { spawnFinalBoss(true); return; }
        updateBossBar(state);
        if (runs.clockTick() >= state.nextPatternAtTick) executeFinalPattern(boss, state);
    }

    private void executeFinalPattern(LivingEntity boss, RunSnapshot.FinalState state) {
        List<String> pool = state.finalBossPhase == 1 ? PHASE_ONE : state.finalBossPhase == 2 ? PHASE_TWO : PHASE_THREE;
        String id = pool.get((int) Math.floorMod(state.finalBossPatternSequence, pool.size()));
        JsonObject data = finalRecord(id);
        JsonObject parameters = data.getAsJsonObject("parameters");
        int telegraph = number(parameters, "telegraphTicks", 20);
        int cooldown = Math.max(80, number(parameters, "cooldownTicks", 200));
        double damage = number(parameters, "baseDamage", number(parameters, "failureDamage", 0));
        runs.mutate(run -> {
            run.finalObjective.finalBossPatternSequence++;
            run.finalObjective.nextPatternAtTick = runs.clockTick() + cooldown;
        });
        runs.broadcast(ChatColor.RED + "⚠ " + id + " — " + data.get("responseText").getAsString());
        boss.getWorld().spawnParticle(Particle.DUST_PLUME, boss.getLocation(), 30, 8.0, 0.4, 8.0, 0.0);
        for (Player player : activePlayers()) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.55f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> resolveFinalPattern(boss, id, parameters, damage), telegraph);
    }

    private void resolveFinalPattern(LivingEntity boss, String id, JsonObject parameters, double damage) {
        if (!boss.isValid() || !"ACTIVE_STAGE_2".equals(runs.current().orElseThrow().finalObjective.state)) return;
        if ("F50-ECHO_SUMMON".equals(id) || "F50-THREE_CORES".equals(id) || "F50-SYNAPSE_CORE".equals(id)) {
            int count = "F50-THREE_CORES".equals(id) ? 3 : Math.min(runs.effectivePartySize() + 1, 4);
            spawnFinalAdds("EN-F50-A03", count);
        }
        if ("F50-PURIFY_BACKFLOW".equals(id) || "F50-FACILITY_JAM".equals(id)
                || "F50-FRACTURE_CHANNEL".equals(id)) {
            int loss = number(parameters, "outputLoss", 10);
            runs.mutate(run -> run.finalObjective.componentProgress.compute("FAC-R06-OUTPUT",
                    (ignored, value) -> Math.max(0, (value == null ? 0 : value) - loss)));
        }
        if ("F50-MUTATION_ROTATE".equals(id)) {
            for (Player player : activePlayers()) player.addPotionEffect(
                    new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 280, 0));
        }
        if (damage > 0.0) {
            double range = "F50-LOCKED_RING".equals(id) ? 20.0 : 14.0;
            for (Player player : activePlayers()) {
                if (player.getWorld().equals(boss.getWorld())
                        && player.getLocation().distanceSquared(boss.getLocation()) <= range * range) {
                    combat.damagePlayerFromPattern(boss, player, damage);
                }
            }
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.65f);
    }

    private void subdueCore(LivingEntity boss) {
        RunSnapshot before = runs.current().orElseThrow();
        for (String id : new ArrayList<>(before.finalObjective.activeEntityUuids)) {
            Entity active = Bukkit.getEntity(UUID.fromString(id));
            if (active != null) active.remove();
        }
        boss.remove();
        cleanupBossBar();
        runs.commitOnce("final-core-subdued:" + runs.current().orElseThrow().runId, "FINAL_CORE_SUBDUED", "{}", run -> {
            RunSnapshot.FinalState state = run.finalObjective;
            state.coreSubdued = true;
            state.state = "ACTIVE_STAGE_3";
            state.stage = 3;
            state.activeEntityUuids.clear();
            state.purificationTicks = 0.0;
            state.purificationCheckpointSeconds = 0;
        });
        story.trigger("FINAL_BOSS", "CORE_SUBDUED");
        story.trigger("PURIFICATION_CHECKPOINT", "0");
        spawnStageThreeWave(0);
    }

    private void tickStageThree(RunSnapshot run) {
        pruneFinalEntities(run.finalObjective);
        run = runs.current().orElseThrow();
        RunSnapshot.FinalState state = run.finalObjective;
        int stakes = (int) state.componentProgress.entrySet().stream()
                .filter(value -> value.getKey().startsWith("F50-STAKE:") && value.getValue() >= 100).count();
        int jammers = countLiveFinalAdds(state, Set.of(EntityType.WITCH, EntityType.MAGMA_CUBE));
        int output = 40 + stakes * 20 - jammers * 20;
        double next = FinalPolicy.applyPurificationOutput(state.purificationTicks, output);
        int checkpoint = FinalPolicy.checkpointSeconds(next);
        if (next != state.purificationTicks || checkpoint != state.purificationCheckpointSeconds) {
            int previousCheckpoint = state.purificationCheckpointSeconds;
            java.util.function.Consumer<RunSnapshot> progressMutation = snapshot -> {
                snapshot.finalObjective.purificationTicks = next;
                snapshot.finalObjective.purificationCheckpointSeconds = checkpoint;
            };
            if (checkpoint != previousCheckpoint) runs.mutate(progressMutation);
            else runs.mutateTransient(progressMutation);
            if (checkpoint > previousCheckpoint) {
                story.trigger("PURIFICATION_CHECKPOINT", Integer.toString(checkpoint));
                if (checkpoint == 60) spawnStageThreeWave(1);
                else if (checkpoint == 120) spawnStageThreeWave(2);
            }
        }
        if (next < 180.0 * 20.0) return;
        if (state.confirmationOpenedAtEpochMs == 0L) {
            runs.mutate(snapshot -> snapshot.finalObjective.confirmationOpenedAtEpochMs = runs.clockNowMillis());
            story.trigger("FINAL_CONFIRMATION_OPENED", "");
            runs.broadcast(ChatColor.GOLD + "[Final 확인] 생존자는 FAC-R06을 2초간 확인하세요.");
        }
        Set<String> required = activePlayers().stream().map(value -> value.getUniqueId().toString())
                .collect(java.util.stream.Collectors.toSet());
        if (!required.isEmpty() && state.confirmationUuids.containsAll(required)) {
            runs.mutate(snapshot -> snapshot.finalObjective.state = "RESOLVING");
        }
    }

    private void resolveCompletion() {
        RunSnapshot run = runs.current().orElseThrow();
        String completionId = "final-completion:" + run.runId;
        commitCompletionStep(completionId, "F50-TX-01", snapshot -> {
            if (!snapshot.finalObjective.coreSubdued || snapshot.finalObjective.purificationTicks < 3600.0
                    || snapshot.finalObjective.confirmationUuids.isEmpty()) {
                throw new IllegalStateException("Final completion preconditions changed");
            }
        });
        commitCompletionStep(completionId, "F50-TX-02", snapshot -> {
            snapshot.discoveryIds.add("C30");
            RunSnapshot.DiscoveryNodeState node = snapshot.discoveryNodes.computeIfAbsent("C30", ignored -> new RunSnapshot.DiscoveryNodeState());
            node.discoveryId = "C30"; node.state = "DISCOVERED"; node.unlockCommitted = true;
            node.discoveredAtEpochMs = runs.clockNowMillis();
        });
        commitCompletionStep(completionId, "F50-TX-03", snapshot -> snapshot.finalObjective.state = "RESOLVING");
        commitCompletionStep(completionId, "F50-TX-04", snapshot -> snapshot.committedKeys.add("world:FIRST_SIGNAL_SENT"));
        commitCompletionStep(completionId, "F50-TX-05", snapshot -> {
            snapshot.finalObjective.completionCommitted = true;
            snapshot.finalObjective.resolvedAtEpochMs = runs.clockNowMillis();
        });
        commitCompletionStep(completionId, "F50-TX-06", snapshot -> snapshot.finalObjective.state = "COMPLETED");
        discoveries.recordFinalCompletion();
        story.trigger("FINAL_COMPLETION_STEP", "F50-TX-05");
        story.trigger("RUN_STATE", "COMPLETED");
        cleanup();
        try {
            runs.complete("인류 재건을 위한 첫 신호 송신");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot commit Season 1 completion", exception);
        }
    }

    private void commitCompletionStep(String completionId, String step, java.util.function.Consumer<RunSnapshot> mutation) {
        runs.commitOnce(completionId + ":" + step, "FINAL_COMPLETION_STEP_COMMITTED", "{\"step\":\"" + step + "\"}", run -> {
            mutation.accept(run);
            run.finalObjective.completedTransactionSteps.add(step);
        });
    }

    @EventHandler
    public void onFacilityInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !event.getAction().isRightClick() || !runs.isRunningMember(event.getPlayer())) return;
        RunSnapshot run = runs.current().orElse(null);
        if (run == null || run.finalObjective == null) return;
        RunSnapshot.FacilityInstanceState instance = run.facilities.values().stream().filter(value ->
                event.getClickedBlock().getWorld().getName().equals(value.world)
                        && event.getClickedBlock().getX() == value.x && event.getClickedBlock().getY() == value.y
                        && event.getClickedBlock().getZ() == value.z).findFirst().orElse(null);
        if (instance == null) return;
        if ("ACTIVE_STAGE_1".equals(run.finalObjective.state) && "FAC-R05".equals(instance.facilityType)) {
            startChannel(event.getPlayer(), "F50-STAKE:" + instance.instanceId, 60L, 25);
        } else if ("ACTIVE_STAGE_1".equals(run.finalObjective.state) && "FAC-R06".equals(instance.facilityType)) {
            startChannel(event.getPlayer(), "FAC-R06-OUTPUT", 100L, 10);
        } else if ("ACTIVE_STAGE_3".equals(run.finalObjective.state) && "FAC-R06".equals(instance.facilityType)
                && run.finalObjective.purificationTicks >= 3600.0) {
            startConfirmation(event.getPlayer(), 40L);
        }
    }

    private void startChannel(Player player, String component, long ticks, int gain) {
        RunSnapshot run = runs.current().orElseThrow();
        if (!run.finalObjective.componentProgress.containsKey(component)) return;
        String key = player.getUniqueId() + ":" + component;
        if (!activeChannels.add(key)) return;
        long damageAt = runs.playerState(player.getUniqueId()).orElseThrow().lastDamageAtEpochMs;
        player.sendMessage(ChatColor.AQUA + "채널 시작 · 움직이거나 피격되면 취소됩니다.");
        Location origin = player.getLocation().clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeChannels.remove(key);
            RunSnapshot.PlayerState playerState = runs.playerState(player.getUniqueId()).orElse(null);
            RunSnapshot current = runs.current().orElse(null);
            if (playerState == null || current == null || !"ACTIVE_STAGE_1".equals(current.finalObjective.state)
                    || !player.isOnline() || playerState.lastDamageAtEpochMs != damageAt
                    || !player.getWorld().equals(origin.getWorld()) || player.getLocation().distanceSquared(origin) > 2.25) {
                player.sendMessage(ChatColor.RED + "Final 채널이 취소되었습니다.");
                return;
            }
            runs.mutate(snapshot -> snapshot.finalObjective.componentProgress.compute(component,
                    (ignored, value) -> Math.min(100, (value == null ? 0 : value) + gain)));
            player.sendMessage(ChatColor.GREEN + "Final 진행 " + component + " +" + gain);
        }, ticks);
    }

    private void startConfirmation(Player player, long ticks) {
        String key = player.getUniqueId() + ":FINAL_CONFIRM";
        if (!activeChannels.add(key)) return;
        long damageAt = runs.playerState(player.getUniqueId()).orElseThrow().lastDamageAtEpochMs;
        Location origin = player.getLocation().clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeChannels.remove(key);
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
            if (state == null || !"ACTIVE".equals(state.lifeState) || state.lastDamageAtEpochMs != damageAt
                    || !player.isOnline() || player.getLocation().distanceSquared(origin) > 2.25) return;
            runs.mutate(run -> run.finalObjective.confirmationUuids.add(player.getUniqueId().toString()));
            player.sendMessage(ChatColor.GREEN + "최종 생존 확인 완료");
        }, ticks);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof FinalHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (event.getRawSlot() == 31) { vote(player); open(player); }
        else if (event.getRawSlot() == 49) player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FinalHolder) event.setCancelled(true);
    }

    public void cleanup() {
        activeChannels.clear();
        cleanupBossBar();
    }

    private void rollbackActivation(String reason) {
        runs.mutate(run -> {
            run.finalObjective.state = "AVAILABLE";
            run.finalObjective.failureReason = reason;
            run.finalObjective.activationVotes.clear();
            run.finalObjective.uniqueInputOwnerUuid = null;
            run.finalObjective.arenaManifestId = null;
        });
        runs.broadcast(ChatColor.RED + "Final 활성화 취소: " + reason + " · 키는 소비되지 않았습니다.");
    }

    private void spawnFinalAdds(String enemyId, int count) {
        RunSnapshot.FinalState state = runs.current().orElseThrow().finalObjective;
        pruneFinalEntities(state);
        state = runs.current().orElseThrow().finalObjective;
        count = Math.min(count, Math.max(0, 10 - state.activeEntityUuids.size()));
        if (count <= 0) return;
        Location center = arena(state);
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0 * index / count;
            LivingEntity enemy = combat.spawnProductionEnemy(enemyId,
                    safe(center.clone().add(Math.cos(angle) * 8.0, 0.0, Math.sin(angle) * 8.0)), 1.0);
            ids.add(enemy.getUniqueId().toString());
        }
        runs.mutate(run -> run.finalObjective.activeEntityUuids.addAll(ids));
    }

    private void spawnStageThreeWave(int index) {
        String id = switch (index) { case 0 -> "EN-F50-A01"; case 1 -> "EN-F50-A02"; default -> "EN-F50-A04"; };
        int party = runs.current().orElseThrow().finalObjective.lockedPartySize;
        int budget = switch (index) {
            case 0 -> party <= 2 ? 30 : party == 3 ? 39 : 48;
            case 1 -> party <= 2 ? 36 : party == 3 ? 47 : 58;
            default -> party <= 2 ? 42 : party == 3 ? 55 : 67;
        };
        spawnFinalAdds(id, Math.max(1, Math.min(10, (int) Math.ceil(budget / 10.0))));
    }

    private int countLiveFinalAdds(RunSnapshot.FinalState state, Set<EntityType> types) {
        int count = 0;
        for (String id : state.activeEntityUuids) {
            Entity entity = Bukkit.getEntity(UUID.fromString(id));
            if (entity != null && entity.isValid() && types.contains(entity.getType())) count++;
        }
        return count;
    }

    private void pruneFinalEntities(RunSnapshot.FinalState state) {
        Set<String> live = state.activeEntityUuids.stream().filter(id -> {
            Entity entity = Bukkit.getEntity(UUID.fromString(id));
            return entity != null && entity.isValid() && !entity.isDead();
        }).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!live.equals(state.activeEntityUuids)) runs.mutate(run -> {
            run.finalObjective.activeEntityUuids.clear();
            run.finalObjective.activeEntityUuids.addAll(live);
        });
    }

    private List<Integer> stageOneBudgets(int partySize) {
        JsonArray profiles = finalRecord(OBJECTIVE_ID).getAsJsonArray("stage1BudgetByParty");
        for (JsonElement value : profiles) {
            JsonObject profile = value.getAsJsonObject();
            if (profile.get("partySize").getAsInt() != partySize) continue;
            List<Integer> result = new ArrayList<>();
            profile.getAsJsonArray("waveBudgets").forEach(element -> result.add(element.getAsInt()));
            return List.copyOf(result);
        }
        throw new IllegalStateException("Missing Final party profile " + partySize);
    }

    private JsonObject partyProfile(JsonArray profiles, int partySize) {
        for (JsonElement value : profiles) if (value.getAsJsonObject().get("partySize").getAsInt() == partySize) {
            return value.getAsJsonObject();
        }
        throw new IllegalStateException("Missing Final boss profile " + partySize);
    }

    private JsonObject finalRecord(String id) {
        ProductionContentCatalog.FinalRecordEntry record = production.finalRecordsById().get(id);
        if (record == null) throw new IllegalStateException("Missing Final record " + id);
        return record.payload();
    }

    private List<RunSnapshot.FacilityInstanceState> facility(RunSnapshot run, String type, String state) {
        return run.facilities.values().stream().filter(value -> type.equals(value.facilityType) && state.equals(value.state))
                .sorted(Comparator.comparing(value -> value.instanceId)).toList();
    }

    private LivingEntity finalBossEntity(RunSnapshot.FinalState state) {
        if (state.finalBossEntityUuid == null) return null;
        Entity entity = Bukkit.getEntity(UUID.fromString(state.finalBossEntityUuid));
        return entity instanceof LivingEntity living && living.isValid() ? living : null;
    }

    private Location arena(RunSnapshot.FinalState state) {
        World world = Bukkit.getWorld(state.arenaWorld);
        if (world == null) throw new IllegalStateException("Final arena world is unavailable");
        return new Location(world, state.arenaX, state.arenaY, state.arenaZ);
    }

    private Location safe(Location candidate) {
        int y = candidate.getWorld().getHighestBlockYAt(candidate.getBlockX(), candidate.getBlockZ()) + 1;
        candidate.setY(Math.max(candidate.getY(), y));
        return candidate;
    }

    private List<Player> activePlayers() {
        return runs.onlineMembers().stream().filter(player -> runs.playerState(player.getUniqueId())
                .map(state -> "ACTIVE".equals(state.lifeState)).orElse(false)).toList();
    }

    private void createBossBar(LivingEntity boss) {
        cleanupBossBar();
        bossBar = Bukkit.createBossBar("세계 붕괴 핵", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        runs.onlineMembers().forEach(bossBar::addPlayer);
        updateBossBar(runs.current().orElseThrow().finalObjective);
    }

    private void updateBossBar(RunSnapshot.FinalState state) {
        if (bossBar == null) return;
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, state.finalBossHp / Math.max(1.0, state.finalBossMaxHp))));
        bossBar.setTitle("세계 붕괴 핵 · P" + state.finalBossPhase + " · " + Math.round(state.finalBossHp));
    }

    private void cleanupBossBar() {
        if (bossBar != null) { bossBar.removeAll(); bossBar = null; }
    }

    private static void requireAll(List<String> missing, String label, Set<String> actual, Set<String> expected) {
        Set<String> absent = new LinkedHashSet<>(expected); absent.removeAll(actual);
        if (!absent.isEmpty()) missing.add(label + " " + absent);
    }

    private static int number(JsonObject object, String key, int fallback) {
        return object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static List<String> progressLore(RunSnapshot.FinalState state) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "Stage 1 Wave " + state.stage1WaveIndex + "/" + state.stage1WaveBudgets.size());
        state.componentProgress.forEach((key, value) -> lore.add(ChatColor.GRAY + key + " " + value + "/100"));
        lore.add(ChatColor.WHITE + "Core " + Math.round(state.finalBossHp) + "/" + Math.round(state.finalBossMaxHp));
        lore.add(ChatColor.WHITE + "정화 " + Math.round(state.purificationTicks / 20.0) + "/180초");
        lore.add(ChatColor.WHITE + "최종 확인 " + state.confirmationUuids.size());
        return lore;
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta);
        return item;
    }

    private static final class FinalHolder implements InventoryHolder {
        private final UUID owner;
        private FinalHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
}
