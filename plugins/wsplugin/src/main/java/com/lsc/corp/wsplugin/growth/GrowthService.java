package com.lsc.corp.wsplugin.growth;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.PlayerStatPolicy;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class GrowthService implements Listener {
    private static final Map<Integer, String> FIXED_TIERS = Map.of(3, "SILVER", 6, "GOLD", 10, "PRISM");
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent content;
    private final TelemetryService telemetry;
    private final Map<UUID, Integer> openMilestones = new HashMap<>();
    private boolean partyVoteOpened;
    private int tickCounter;

    public GrowthService(JavaPlugin plugin, RunService runs, PrototypeContent content, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.telemetry = telemetry;
    }

    public void awardExp(Player player, int amount, String idempotencyKey) {
        if (amount <= 0 || !runs.isRunningMember(player)) {
            return;
        }
        RunSnapshot.PlayerState before = runs.playerState(player.getUniqueId()).orElse(null);
        if (before == null) return;
        if (!idempotencyKey.startsWith("checkpoint-exp:") && !idempotencyKey.startsWith("target-exp:")) {
            amount = Math.max(1, (int) Math.floor(amount * PlayerStatPolicy.activityExpMultiplier(before.investedStats)));
        }
        int committedAmount = amount;
        int beforeLevel = before.level;
        boolean committed = runs.commitOnce(idempotencyKey, "EXP_COMMITTED",
                "{\"amount\":" + committedAmount + "}", snapshot -> {
                    RunSnapshot.PlayerState state = snapshot.players.get(player.getUniqueId().toString());
                    state.exp = Math.min(cumulativeExpForLevel(50), state.exp + committedAmount);
                    state.level = levelForExp(state.exp);
                });
        if (!committed) {
            return;
        }
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        player.setLevel(state.level);
        player.setExp(levelProgress(state));
        if (state.level > beforeLevel) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
            player.sendMessage(ChatColor.GREEN + "WildSurvival Lv." + state.level + " 달성");
            for (int milestone : List.of(3, 6, 10)) {
                if (beforeLevel < milestone && state.level >= milestone) {
                    lockAndOpenPersonalDraw(player, milestone);
                }
            }
        }
    }

    public void awardCheckpointTarget(int day) {
        Integer target = content.progressExpByDay().get(day);
        if (target == null) {
            return;
        }
        for (Player player : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
            if (state.exp < target) {
                awardExp(player, target - state.exp, "checkpoint-exp:" + day + ":" + player.getUniqueId());
            }
        }
    }

    public void awardTargetExp(int target, String checkpointId) {
        for (Player player : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
            if (state.exp < target) {
                awardExp(player, target - state.exp, "target-exp:" + checkpointId + ":" + player.getUniqueId());
            }
        }
    }

    public boolean partyAugmentSelected() {
        return runs.current().map(run -> run.partyAugmentId != null).orElse(false);
    }

    public void startPartyVoteWhenReady() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.partyAugmentId != null || partyVoteOpened) {
            return;
        }
        boolean pending = snapshot.players.values().stream()
                .filter(state -> !"DEAD".equals(state.lifeState))
                .anyMatch(state -> state.level < 10 || !state.resolvedPersonalMilestones.contains(10));
        if (!pending) {
            startPartyVote();
        }
    }

    public void openPendingPersonalDraw(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) {
            return;
        }
        for (int milestone : List.of(3, 6, 10)) {
            if (state.level >= milestone && !state.resolvedPersonalMilestones.contains(milestone)) {
                lockAndOpenPersonalDraw(player, milestone);
                return;
            }
        }
    }

    public void startPartyVote() {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (snapshot.partyAugmentId != null) {
            return;
        }
        partyVoteOpened = true;
        runs.commitOnce("party-draw:day10", "PARTY_AUGMENT_DRAWN", "{\"day\":10}", run -> run.partyAugmentVotes.clear());
        List<PrototypeContent.AugmentDefinition> choices = partyChoices(snapshot.seed);
        for (Player player : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
            if (!"DEAD".equals(state.lifeState)) {
                player.openInventory(createAugmentInventory(new AugmentHolder(player.getUniqueId(), 10, true, choices), "파티 증강 투표"));
            }
        }
    }

    public double attackMultiplier(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        return aggregate(player, PrototypeContent.AugmentDefinition::attackMultiplier)
                * (state == null ? 1.0 : PlayerStatPolicy.attackMultiplier(state.investedStats));
    }

    public double breakMultiplier(Player player) {
        return aggregate(player, PrototypeContent.AugmentDefinition::breakMultiplier);
    }

    public double resourceMultiplier(Player player) {
        return aggregate(player, PrototypeContent.AugmentDefinition::resourceMultiplier);
    }

    public double reviveSpeedMultiplier(Player player) {
        return aggregate(player, PrototypeContent.AugmentDefinition::reviveSpeedMultiplier);
    }

    public void tick() {
        if (++tickCounter % 100 != 0) {
            return;
        }
        for (Player player : runs.onlineMembers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AugmentHolder) {
                continue;
            }
            openPendingPersonalDraw(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AugmentHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.playerId)) {
            return;
        }
        int index = switch (event.getRawSlot()) {
            case 11 -> 0;
            case 13 -> 1;
            case 15 -> 2;
            default -> -1;
        };
        if (index < 0 || index >= holder.choices.size()) {
            return;
        }
        if (holder.party) {
            registerPartyVote(player, index, holder.choices);
        } else {
            choosePersonal(player, holder.milestone, holder.choices.get(index));
        }
        player.closeInventory();
    }

    public void setPersonalAugmentForTest(Player player, String augmentId, boolean present) {
        PrototypeContent.AugmentDefinition augment = content.personalAugments().stream()
                .filter(value -> value.id().equalsIgnoreCase(augmentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown personal augment " + augmentId));
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            if (present && !state.personalAugments.contains(augment.id())) {
                state.personalAugments.add(augment.id());
            } else if (!present) {
                state.personalAugments.remove(augment.id());
            }
            recalculateMaxAp(run, state);
        });
    }

    public void clearPersonalAugmentsForTest(Player player) {
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.personalAugments.clear();
            state.resolvedPersonalMilestones.clear();
            recalculateMaxAp(run, state);
        });
        openMilestones.remove(player.getUniqueId());
    }

    public void setPartyAugmentForTest(String augmentId) {
        PrototypeContent.AugmentDefinition augment = content.partyAugments().stream()
                .filter(value -> value.id().equalsIgnoreCase(augmentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown party augment " + augmentId));
        runs.mutate(run -> {
            run.partyAugmentId = augment.id();
            run.partyAugmentVotes.clear();
            run.players.values().forEach(state -> recalculateMaxAp(run, state));
        });
    }

    public void clearPartyAugmentForTest() {
        runs.mutate(run -> {
            run.partyAugmentId = null;
            run.partyAugmentVotes.clear();
            run.players.values().forEach(state -> recalculateMaxAp(run, state));
        });
        partyVoteOpened = false;
    }

    public void resetPersonalDrawForTest(Player player, int milestone) {
        if (!FIXED_TIERS.containsKey(milestone)) {
            throw new IllegalArgumentException("Prototype personal milestones are 3, 6, and 10");
        }
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.resolvedPersonalMilestones.remove(milestone);
            run.milestoneLocks.remove("LEVEL_" + milestone);
            run.committedKeys.remove("milestone-lock:" + milestone);
            run.committedKeys.remove("personal-augment:" + milestone + ":" + player.getUniqueId());
        });
        openMilestones.remove(player.getUniqueId());
        lockAndOpenPersonalDraw(player, milestone);
    }

    private void lockAndOpenPersonalDraw(Player player, int milestone) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        if (state.resolvedPersonalMilestones.contains(milestone) || openMilestones.getOrDefault(player.getUniqueId(), -1) == milestone) {
            return;
        }
        String tier = FIXED_TIERS.get(milestone);
        RunSnapshot snapshot = runs.current().orElseThrow();
        runs.commitOnce("milestone-lock:" + milestone, "MILESTONE_LOCKED",
                "{\"milestone\":" + milestone + ",\"tier\":\"" + tier + "\"}", run -> {
                    RunSnapshot.MilestoneLock lock = new RunSnapshot.MilestoneLock();
                    lock.milestone = "LEVEL_" + milestone;
                    lock.tier = tier;
                    lock.firstPlayer = player.getUniqueId().toString();
                    lock.drawSeed = snapshot.seed ^ milestone;
                    run.milestoneLocks.put("LEVEL_" + milestone, lock);
                });
        List<PrototypeContent.AugmentDefinition> choices = personalChoices(player, milestone, tier);
        openMilestones.put(player.getUniqueId(), milestone);
        player.openInventory(createAugmentInventory(new AugmentHolder(player.getUniqueId(), milestone, false, choices), tier + " 개인 증강"));
    }

    private void choosePersonal(Player player, int milestone, PrototypeContent.AugmentDefinition augment) {
        boolean committed = runs.commitOnce("personal-augment:" + milestone + ":" + player.getUniqueId(), "PERSONAL_AUGMENT_SELECTED",
                "{\"milestone\":" + milestone + ",\"augmentId\":\"" + augment.id() + "\"}", run -> {
                    RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                    state.personalAugments.add(augment.id());
                    state.resolvedPersonalMilestones.add(milestone);
                    recalculateMaxAp(run, state);
                });
        openMilestones.remove(player.getUniqueId());
        if (committed) {
            player.sendMessage(ChatColor.AQUA + "증강 선택: " + augment.name());
            telemetry.event(runs.current().orElseThrow().runId, "AUGMENT_UI_CONFIRMED", "{\"scope\":\"PERSONAL\"}");
        }
    }

    private void registerPartyVote(Player player, int index, List<PrototypeContent.AugmentDefinition> choices) {
        runs.mutate(run -> run.partyAugmentVotes.put(player.getUniqueId().toString(), index));
        player.sendMessage(ChatColor.AQUA + "파티 증강 투표: " + choices.get(index).name());
        long eligible = runs.onlineMembers().stream()
                .filter(member -> runs.playerState(member.getUniqueId()).map(state -> !"DEAD".equals(state.lifeState)).orElse(false))
                .count();
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (snapshot.partyAugmentVotes.size() < eligible) {
            return;
        }
        Map<Integer, Long> counts = new HashMap<>();
        snapshot.partyAugmentVotes.values().forEach(vote -> counts.merge(vote, 1L, Long::sum));
        int winner = counts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry::getKey))
                .findFirst().orElse(Map.entry(0, 0L)).getKey();
        PrototypeContent.AugmentDefinition selected = choices.get(winner);
        runs.commitOnce("party-augment:day10", "PARTY_AUGMENT_SELECTED",
                "{\"augmentId\":\"" + selected.id() + "\"}", run -> {
                    run.partyAugmentId = selected.id();
                    for (RunSnapshot.PlayerState state : run.players.values()) {
                        recalculateMaxAp(run, state);
                    }
                });
        runs.broadcast(ChatColor.GOLD + "파티 증강 확정: " + selected.name());
    }

    private List<PrototypeContent.AugmentDefinition> personalChoices(Player player, int milestone, String tier) {
        List<PrototypeContent.AugmentDefinition> choices = new ArrayList<>(content.personalAugments().stream()
                .filter(augment -> tier.equals(augment.tier())).toList());
        long seed = runs.current().orElseThrow().seed ^ player.getUniqueId().getMostSignificantBits() ^ milestone;
        java.util.Collections.shuffle(choices, new Random(seed));
        return List.copyOf(choices.subList(0, Math.min(3, choices.size())));
    }

    private List<PrototypeContent.AugmentDefinition> partyChoices(long seed) {
        List<PrototypeContent.AugmentDefinition> choices = new ArrayList<>(content.partyAugments());
        java.util.Collections.shuffle(choices, new Random(seed ^ 10L));
        return List.copyOf(choices.subList(0, Math.min(3, choices.size())));
    }

    private Inventory createAugmentInventory(AugmentHolder holder, String title) {
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.DARK_PURPLE + title);
        int[] slots = {11, 13, 15};
        Material[] materials = {Material.IRON_NUGGET, Material.GOLD_INGOT, Material.AMETHYST_SHARD};
        for (int i = 0; i < holder.choices.size(); i++) {
            PrototypeContent.AugmentDefinition augment = holder.choices.get(i);
            ItemStack item = new ItemStack(materials[Math.min(i, materials.length - 1)]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + augment.name());
            meta.setLore(List.of(
                    ChatColor.GRAY + "ID: " + augment.id(),
                    ChatColor.WHITE + "등급: " + augment.tier(),
                    ChatColor.YELLOW + "클릭하여 " + (holder.party ? "투표" : "선택")
            ));
            item.setItemMeta(meta);
            inventory.setItem(slots[i], item);
        }
        return inventory;
    }

    private double aggregate(Player player, java.util.function.ToDoubleFunction<PrototypeContent.AugmentDefinition> getter) {
        RunSnapshot snapshot = runs.current().orElse(null);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (snapshot == null || state == null) {
            return 1.0;
        }
        double result = 1.0;
        for (String id : state.personalAugments) {
            PrototypeContent.AugmentDefinition augment = findAugment(id);
            result *= getter.applyAsDouble(augment);
        }
        if (snapshot.partyAugmentId != null) {
            result *= getter.applyAsDouble(findAugment(snapshot.partyAugmentId));
        }
        return result;
    }

    private void recalculateMaxAp(RunSnapshot run, RunSnapshot.PlayerState state) {
        int bonus = state.personalAugments.stream().map(this::findAugment).mapToInt(PrototypeContent.AugmentDefinition::maxApBonus).sum();
        if (run.partyAugmentId != null) {
            bonus += findAugment(run.partyAugmentId).maxApBonus();
        }
        int base = "TEST".equals(run.runType)
                ? state.testBaseMaxAp + Math.min(25, PlayerStatPolicy.points(state.investedStats, "AP"))
                : PlayerStatPolicy.baseMaxAp(state.investedStats);
        state.maxAp = Math.min("TEST".equals(run.runType) ? 10_000 : 200, Math.max(1, base + bonus));
        state.ap = Math.min(state.maxAp, state.ap);
    }

    public void recalculateMaxAp(Player player) {
        runs.mutate(run -> recalculateMaxAp(run, run.players.get(player.getUniqueId().toString())));
    }

    private PrototypeContent.AugmentDefinition findAugment(String id) {
        return java.util.stream.Stream.concat(content.personalAugments().stream(), content.partyAugments().stream())
                .filter(augment -> augment.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown augment " + id));
    }

    public static int nextLevelExp(int currentLevel) {
        return LevelCurve.nextLevelExp(currentLevel);
    }

    public static int cumulativeExpForLevel(int level) {
        return LevelCurve.cumulativeExpForLevel(level);
    }

    public static int levelForExp(int exp) {
        return LevelCurve.levelForExp(exp);
    }

    private static float levelProgress(RunSnapshot.PlayerState state) {
        if (state.level >= 50) {
            return 1.0f;
        }
        int reached = cumulativeExpForLevel(state.level);
        return Math.max(0.0f, Math.min(1.0f, (state.exp - reached) / (float) nextLevelExp(state.level)));
    }

    private static final class AugmentHolder implements InventoryHolder {
        private final UUID playerId;
        private final int milestone;
        private final boolean party;
        private final List<PrototypeContent.AugmentDefinition> choices;

        private AugmentHolder(UUID playerId, int milestone, boolean party, List<PrototypeContent.AugmentDefinition> choices) {
            this.playerId = playerId;
            this.milestone = milestone;
            this.party = party;
            this.choices = choices;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
