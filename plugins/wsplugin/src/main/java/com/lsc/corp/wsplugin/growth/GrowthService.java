package com.lsc.corp.wsplugin.growth;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.PlayerStatPolicy;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.ui.ActionBarService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
            ActionBarService.important(player,
                    Component.text("레벨 상승! Lv." + state.level + " · EXP +" + committedAmount, NamedTextColor.GREEN), 80);
            for (int milestone : List.of(3, 6, 10)) {
                if (beforeLevel < milestone && state.level >= milestone) {
                    lockAndOpenPersonalDraw(player, milestone);
                }
            }
        } else {
            ActionBarService.notice(player, Component.text("EXP +" + committedAmount, NamedTextColor.GREEN), 35);
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

    public void openAugments(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return;
        for (int milestone : List.of(3, 6, 10)) {
            if (state.level >= milestone && !state.resolvedPersonalMilestones.contains(milestone)) {
                openMilestones.remove(player.getUniqueId());
                lockAndOpenPersonalDraw(player, milestone);
                return;
            }
        }
        RunSnapshot run = runs.current().orElseThrow();
        if (partyVoteOpened && run.partyAugmentId == null
                && !run.partyAugmentVotes.containsKey(player.getUniqueId().toString())) {
            List<PrototypeContent.AugmentDefinition> choices = partyChoices(run.seed);
            player.openInventory(createAugmentInventory(
                    new AugmentHolder(player.getUniqueId(), 10, true, choices), "파티 증강 투표"));
            return;
        }
        openOwnedAugments(player);
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
        tickCounter++;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof AugmentOverviewHolder holder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && player.getUniqueId().equals(holder.playerId)
                    && event.getRawSlot() == 49) player.closeInventory();
            return;
        }
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AugmentHolder holder) || holder.party) return;
        RunSnapshot.PlayerState state = runs.playerState(holder.playerId).orElse(null);
        if (state != null && !state.resolvedPersonalMilestones.contains(holder.milestone)) {
            openMilestones.remove(holder.playerId);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AugmentHolder
                || event.getInventory().getHolder() instanceof AugmentOverviewHolder) event.setCancelled(true);
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
        for (int i = 0; i < holder.choices.size(); i++) {
            PrototypeContent.AugmentDefinition augment = holder.choices.get(i);
            ItemStack item = new ItemStack(augmentMaterial(augment));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + augment.name());
            boolean detailed = runs.playerState(holder.playerId).map(state -> state.detailedTooltips).orElse(false);
            meta.setLore(augmentLore(augment, detailed, ChatColor.YELLOW + "클릭하여 " + (holder.party ? "투표" : "선택")));
            item.setItemMeta(meta);
            inventory.setItem(slots[i], item);
        }
        return inventory;
    }

    private void openOwnedAugments(Player player) {
        RunSnapshot run = runs.current().orElseThrow();
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        Inventory inventory = Bukkit.createInventory(new AugmentOverviewHolder(player.getUniqueId()), 54,
                ChatColor.DARK_PURPLE + "보유 증강");
        ItemStack border = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
        inventory.setItem(4, named(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "증강 현황",
                List.of(ChatColor.WHITE + "개인 " + state.personalAugments.size() + "개",
                        ChatColor.WHITE + "파티 " + (run.partyAugmentId == null ? "미보유" : "1개"),
                        ChatColor.GRAY + (state.detailedTooltips ? "상세 설명 모드" : "간단 설명 모드"))));
        int[] personalSlots = {10, 12, 14, 16, 28, 30, 32, 34, 36, 38};
        for (int i = 0; i < state.personalAugments.size() && i < personalSlots.length; i++) {
            PrototypeContent.AugmentDefinition augment = findAugment(state.personalAugments.get(i));
            inventory.setItem(personalSlots[i], augmentIcon(augment, state.detailedTooltips, ChatColor.AQUA + "개인 증강"));
        }
        inventory.setItem(22, run.partyAugmentId == null
                ? named(Material.BARRIER, ChatColor.GRAY + "파티 증강 미보유",
                List.of(partyVoteOpened ? ChatColor.YELLOW + "파티 투표 진행 중" : ChatColor.DARK_GRAY + "Day 10 보스 이후 결정"))
                : augmentIcon(findAugment(run.partyAugmentId), state.detailedTooltips, ChatColor.GOLD + "파티 증강"));
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        player.openInventory(inventory);
    }

    private ItemStack augmentIcon(PrototypeContent.AugmentDefinition augment, boolean detailed, String scopeLine) {
        return named(augmentMaterial(augment), ChatColor.LIGHT_PURPLE + augment.name(), augmentLore(augment, detailed, scopeLine));
    }

    private static Material augmentMaterial(PrototypeContent.AugmentDefinition augment) {
        return switch (augment.tier()) {
            case "SILVER" -> Material.IRON_NUGGET;
            case "GOLD" -> Material.GOLD_INGOT;
            default -> Material.AMETHYST_SHARD;
        };
    }

    private List<String> augmentLore(PrototypeContent.AugmentDefinition augment, boolean detailed, String tail) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "등급 " + augment.tier() + " · " + ("PARTY".equals(augment.scope()) ? "파티" : "개인"));
        if (detailed) {
            lore.add(ChatColor.GRAY + "공격 배율 x" + augment.attackMultiplier());
            lore.add(ChatColor.GRAY + "브레이크 배율 x" + augment.breakMultiplier());
            lore.add(ChatColor.GRAY + "자원 배율 x" + augment.resourceMultiplier());
            lore.add(ChatColor.GRAY + "최대 AP +" + augment.maxApBonus());
            lore.add(ChatColor.GRAY + "구조 속도 x" + augment.reviveSpeedMultiplier());
            lore.add(ChatColor.DARK_GRAY + "ID: " + augment.id());
        } else {
            if (augment.attackMultiplier() != 1.0) lore.add(ChatColor.GRAY + "공격 능력을 강화합니다.");
            if (augment.breakMultiplier() != 1.0) lore.add(ChatColor.GRAY + "브레이크 능력을 강화합니다.");
            if (augment.resourceMultiplier() != 1.0) lore.add(ChatColor.GRAY + "자원 획득량을 늘립니다.");
            if (augment.maxApBonus() != 0) lore.add(ChatColor.GRAY + "최대 AP를 늘립니다.");
            if (augment.reviveSpeedMultiplier() != 1.0) lore.add(ChatColor.GRAY + "아군 구조 속도를 높입니다.");
        }
        lore.add(tail);
        return lore;
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
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

    private static final class AugmentOverviewHolder implements InventoryHolder {
        private final UUID playerId;
        private AugmentOverviewHolder(UUID playerId) { this.playerId = playerId; }
        @Override public Inventory getInventory() { return null; }
    }
}
