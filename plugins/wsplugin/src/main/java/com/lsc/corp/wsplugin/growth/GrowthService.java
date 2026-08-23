package com.lsc.corp.wsplugin.growth;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
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
    private static final List<Integer> PERSONAL_MILESTONES = AugmentMilestonePolicy.personalMilestones();
    private static final List<Integer> PARTY_MILESTONES = AugmentMilestonePolicy.partyMilestones();
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent content;
    private final ProductionContentCatalog production;
    private final Map<String, PrototypeContent.AugmentDefinition> runtimeAugments;
    private final TelemetryService telemetry;
    private final Map<UUID, Integer> openMilestones = new HashMap<>();
    private boolean partyVoteOpened;
    private int tickCounter;

    public GrowthService(JavaPlugin plugin, RunService runs, PrototypeContent content,
                         ProductionContentCatalog production, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.production = production;
        this.runtimeAugments = production.augmentsById().values().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(ProductionContentCatalog.AugmentEntry::id,
                        GrowthService::runtimeAugment));
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
            for (int milestone : PERSONAL_MILESTONES) {
                if (beforeLevel < milestone && state.level >= milestone) {
                    lockAndOpenPersonalDraw(player, milestone);
                    break;
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
        return runs.current().map(run -> {
            Integer milestone = latestPartyMilestone(run.day);
            java.util.Set<Integer> resolved = run.resolvedPartyAugmentMilestones == null ? java.util.Set.of()
                    : run.resolvedPartyAugmentMilestones;
            return milestone != null && (resolved.contains(milestone)
                    || milestone == 10 && run.partyAugmentId != null && !run.partyAugmentId.isBlank());
        }).orElse(false);
    }

    public void startPartyVoteWhenReady() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null) {
            return;
        }
        Integer milestone = nextPartyMilestone(snapshot);
        if (milestone == null || snapshot.activePartyAugmentMilestone != null || partyVoteOpened) return;
        boolean pending = snapshot.players.values().stream()
                .filter(state -> !"DEAD".equals(state.lifeState))
                .anyMatch(this::hasPendingPersonalDraw);
        if (!pending) {
            startPartyVote(milestone);
        }
    }

    public void openPendingPersonalDraw(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) {
            return;
        }
        for (int milestone : PERSONAL_MILESTONES) {
            if (state.level >= milestone && !state.resolvedPersonalMilestones.contains(milestone)) {
                lockAndOpenPersonalDraw(player, milestone);
                return;
            }
        }
    }

    public void openAugments(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return;
        for (int milestone : PERSONAL_MILESTONES) {
            if (state.level >= milestone && !state.resolvedPersonalMilestones.contains(milestone)) {
                openMilestones.remove(player.getUniqueId());
                lockAndOpenPersonalDraw(player, milestone);
                return;
            }
        }
        RunSnapshot run = runs.current().orElseThrow();
        if ((partyVoteOpened || run.activePartyAugmentMilestone != null)
                && !run.partyAugmentVotes.containsKey(player.getUniqueId().toString())) {
            int milestone = run.activePartyAugmentMilestone == null ? 10 : run.activePartyAugmentMilestone;
            List<PrototypeContent.AugmentDefinition> choices = partyChoices(run.seed, milestone, partyAugmentIds(run));
            player.openInventory(createAugmentInventory(
                    new AugmentHolder(player.getUniqueId(), milestone, true, choices), "Day " + milestone + " 파티 증강 투표"));
            return;
        }
        openOwnedAugments(player);
    }

    public void startPartyVote() {
        RunSnapshot snapshot = runs.current().orElseThrow();
        Integer milestone = nextPartyMilestone(snapshot);
        if (milestone == null) return;
        startPartyVote(milestone);
    }

    private void startPartyVote(int milestone) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        partyVoteOpened = true;
        runs.commitOnce("party-draw:day" + milestone, "PARTY_AUGMENT_DRAWN", "{\"day\":" + milestone + "}", run -> {
            run.partyAugmentVotes.clear();
            run.activePartyAugmentMilestone = milestone;
        });
        List<PrototypeContent.AugmentDefinition> choices = partyChoices(snapshot.seed, milestone, partyAugmentIds(snapshot));
        for (Player player : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
            if (!"DEAD".equals(state.lifeState)) {
                player.openInventory(createAugmentInventory(new AugmentHolder(player.getUniqueId(), milestone, true, choices),
                        "Day " + milestone + " 파티 증강 투표"));
            }
        }
    }

    public double attackMultiplier(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        double result = aggregate(player, PrototypeContent.AugmentDefinition::attackMultiplier)
                * (state == null ? 1.0 : PlayerStatPolicy.attackMultiplier(state.investedStats));
        if (hasAugment(player, "AUG-P-001") && player.getHealth() / Math.max(1.0, player.getMaxHealth()) <= 0.40) {
            result *= 1.22;
        }
        return result;
    }

    public double breakMultiplier(Player player) {
        double result = aggregate(player, PrototypeContent.AugmentDefinition::breakMultiplier);
        if (hasAugment(player, "AUG-P-001") && player.getHealth() / Math.max(1.0, player.getMaxHealth()) <= 0.40) {
            result *= 1.15;
        }
        return result;
    }

    public double resourceMultiplier(Player player) {
        return aggregate(player, PrototypeContent.AugmentDefinition::resourceMultiplier);
    }

    public double reviveSpeedMultiplier(Player player) {
        return aggregate(player, PrototypeContent.AugmentDefinition::reviveSpeedMultiplier)
                * (hasAugment(player, "AUG-S-014") ? 1.0 / 0.90 : 1.0);
    }

    public void tick() {
        tickCounter++;
        if (tickCounter % 20 == 0) {
            RunSnapshot snapshot = runs.current().orElse(null);
            if (snapshot != null && snapshot.activePartyAugmentMilestone != null) partyVoteOpened = true;
        }
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
        PrototypeContent.AugmentDefinition augment = findAugment(augmentId);
        if (!production.augmentsById().get(augment.id()).personal()) {
            throw new IllegalArgumentException("Not a personal augment " + augmentId);
        }
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
        PrototypeContent.AugmentDefinition augment = findAugment(augmentId);
        if (production.augmentsById().get(augment.id()).personal()) {
            throw new IllegalArgumentException("Not a party augment " + augmentId);
        }
        runs.mutate(run -> {
            run.partyAugmentId = augment.id();
            if (run.partyAugmentIds == null) run.partyAugmentIds = new ArrayList<>();
            run.partyAugmentIds.clear();
            run.partyAugmentIds.add(augment.id());
            if (run.resolvedPartyAugmentMilestones == null) run.resolvedPartyAugmentMilestones = new java.util.LinkedHashSet<>();
            run.resolvedPartyAugmentMilestones.clear();
            run.resolvedPartyAugmentMilestones.add(10);
            run.activePartyAugmentMilestone = null;
            run.partyAugmentVotes.clear();
            run.players.values().forEach(state -> recalculateMaxAp(run, state));
        });
    }

    public void clearPartyAugmentForTest() {
        runs.mutate(run -> {
            run.partyAugmentId = null;
            if (run.partyAugmentIds == null) run.partyAugmentIds = new ArrayList<>();
            run.partyAugmentIds.clear();
            if (run.resolvedPartyAugmentMilestones == null) run.resolvedPartyAugmentMilestones = new java.util.LinkedHashSet<>();
            run.resolvedPartyAugmentMilestones.clear();
            run.activePartyAugmentMilestone = null;
            run.partyAugmentVotes.clear();
            run.players.values().forEach(state -> recalculateMaxAp(run, state));
        });
        partyVoteOpened = false;
    }

    public void resetPersonalDrawForTest(Player player, int milestone) {
        if (!PERSONAL_MILESTONES.contains(milestone)) {
            throw new IllegalArgumentException("Personal milestones are " + PERSONAL_MILESTONES);
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
        RunSnapshot snapshot = runs.current().orElseThrow();
        RunSnapshot.MilestoneLock existing = snapshot.milestoneLocks.get("LEVEL_" + milestone);
        String tier = existing == null ? tierForMilestone(snapshot.seed, milestone) : existing.tier;
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
            Bukkit.getScheduler().runTask(plugin, () -> openPendingPersonalDraw(player));
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
        int milestone = holderMilestone(snapshot);
        runs.commitOnce("party-augment:day" + milestone, "PARTY_AUGMENT_SELECTED",
                "{\"augmentId\":\"" + selected.id() + "\"}", run -> {
                    run.partyAugmentId = selected.id();
                    if (run.partyAugmentIds == null) run.partyAugmentIds = new ArrayList<>();
                    if (!run.partyAugmentIds.contains(selected.id())) run.partyAugmentIds.add(selected.id());
                    if (run.resolvedPartyAugmentMilestones == null) run.resolvedPartyAugmentMilestones = new java.util.LinkedHashSet<>();
                    run.resolvedPartyAugmentMilestones.add(milestone);
                    run.activePartyAugmentMilestone = null;
                    run.partyAugmentVotes.clear();
                    for (RunSnapshot.PlayerState state : run.players.values()) {
                        recalculateMaxAp(run, state);
                    }
                });
        partyVoteOpened = false;
        runs.broadcast(ChatColor.GOLD + "파티 증강 확정: " + selected.name());
    }

    private List<PrototypeContent.AugmentDefinition> personalChoices(Player player, int milestone, String tier) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        java.util.Set<String> owned = new java.util.HashSet<>(state.personalAugments);
        List<ProductionContentCatalog.AugmentEntry> pool = new ArrayList<>(production.personalAugments().stream()
                .filter(augment -> tier.equals(augment.tier()) && !owned.contains(augment.id()))
                .filter(augment -> augment.exclusiveWith().stream().noneMatch(owned::contains)).toList());
        long seed = runs.current().orElseThrow().seed ^ player.getUniqueId().getMostSignificantBits() ^ milestone;
        java.util.Set<String> buildTags = buildTags(state);
        List<PrototypeContent.AugmentDefinition> choices = new ArrayList<>();
        Random random = new Random(seed);
        while (!pool.isEmpty() && choices.size() < 3) {
            double total = pool.stream().mapToDouble(augment -> augmentWeight(augment, buildTags)).sum();
            double roll = random.nextDouble() * total;
            int selectedIndex = 0;
            for (int i = 0; i < pool.size(); i++) {
                roll -= augmentWeight(pool.get(i), buildTags);
                if (roll <= 0.0) { selectedIndex = i; break; }
            }
            choices.add(runtimeAugments.get(pool.remove(selectedIndex).id()));
        }
        return List.copyOf(choices);
    }

    private List<PrototypeContent.AugmentDefinition> partyChoices(long seed, int milestone, List<String> owned) {
        List<PrototypeContent.AugmentDefinition> choices = new ArrayList<>(production.partyAugments().stream()
                .filter(augment -> !owned.contains(augment.id())).map(augment -> runtimeAugments.get(augment.id())).toList());
        java.util.Collections.shuffle(choices, new Random(seed ^ milestone));
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
        List<String> partyIds = partyAugmentIds(run);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        Inventory inventory = Bukkit.createInventory(new AugmentOverviewHolder(player.getUniqueId()), 54,
                ChatColor.DARK_PURPLE + "보유 증강");
        ItemStack border = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
        inventory.setItem(4, named(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "증강 현황",
                List.of(ChatColor.WHITE + "개인 " + state.personalAugments.size() + "개",
                        ChatColor.WHITE + "파티 " + partyIds.size() + "/4개",
                        ChatColor.GRAY + (state.detailedTooltips ? "상세 설명 모드" : "간단 설명 모드"))));
        int[] personalSlots = {10, 12, 14, 16, 28, 30, 32, 34, 36, 38};
        for (int i = 0; i < state.personalAugments.size() && i < personalSlots.length; i++) {
            PrototypeContent.AugmentDefinition augment = findAugment(state.personalAugments.get(i));
            inventory.setItem(personalSlots[i], augmentIcon(augment, state.detailedTooltips, ChatColor.AQUA + "개인 증강"));
        }
        int[] partySlots = {20, 22, 24, 26};
        for (int i = 0; i < partySlots.length; i++) {
            inventory.setItem(partySlots[i], i < partyIds.size()
                    ? augmentIcon(findAugment(partyIds.get(i)), state.detailedTooltips,
                    ChatColor.GOLD + "파티 증강 " + (i + 1))
                    : named(Material.BARRIER, ChatColor.GRAY + "파티 증강 " + (i + 1) + " 미보유",
                    List.of(partyVoteOpened ? ChatColor.YELLOW + "파티 투표 진행 중"
                            : ChatColor.DARK_GRAY + "Day " + PARTY_MILESTONES.get(i) + " 보스 이후 결정")));
        }
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
        ProductionContentCatalog.AugmentEntry productionAugment = production.augmentsById().get(augment.id());
        if (productionAugment != null) {
            lore.add(ChatColor.GRAY + productionAugment.effectText());
            if (detailed && !productionAugment.constraintText().isBlank()) {
                lore.add(ChatColor.DARK_GRAY + "조건: " + productionAugment.constraintText());
            }
        }
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
        for (String id : partyAugmentIds(snapshot)) result *= getter.applyAsDouble(findAugment(id));
        return result;
    }

    private void recalculateMaxAp(RunSnapshot run, RunSnapshot.PlayerState state) {
        int bonus = state.personalAugments.stream().map(this::findAugment).mapToInt(PrototypeContent.AugmentDefinition::maxApBonus).sum();
        for (String id : partyAugmentIds(run)) bonus += findAugment(id).maxApBonus();
        int base = "TEST".equals(run.runType)
                ? state.testBaseMaxAp + Math.min(25, PlayerStatPolicy.points(state.investedStats, "AP"))
                : PlayerStatPolicy.baseMaxAp(state.investedStats);
        state.maxAp = Math.min("TEST".equals(run.runType) ? 10_000 : 200,
                Math.max(1, base + bonus + Math.max(0, state.equipmentMaxApBonus)));
        state.ap = Math.min(state.maxAp, state.ap);
    }

    public void recalculateMaxAp(Player player) {
        runs.mutate(run -> recalculateMaxAp(run, run.players.get(player.getUniqueId().toString())));
    }

    public boolean hasAugment(Player player, String id) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        return state != null && state.personalAugments.contains(id);
    }

    public boolean partyHasAugment(String id) {
        return runs.current().map(run -> partyAugmentIds(run).contains(id)).orElse(false);
    }

    public double dodgeCost(Player player, double baseCost) {
        double result = baseCost;
        if (hasAugment(player, "AUG-S-001")) result -= 4.0;
        if (partyHasAugment("PAUG-001") && nearbyLivingMembers(player, 12.0) >= 2) result -= 3.0;
        return Math.max(4.0, result);
    }

    public double basicAttackApCost(Player player, String weaponClass, double baseCost) {
        return hasAugment(player, "AUG-P-008") && ("BOW".equals(weaponClass) || "CROSSBOW".equals(weaponClass))
                ? baseCost * 1.20 : baseCost;
    }

    public double skillApCost(Player player, String skillId, double baseCost) {
        double result = baseCost;
        if (hasAugment(player, "AUG-P-013") && baseCost >= 50.0) result += 15.0;
        if (hasAugment(player, "AUG-P-009") && skillId.contains("trident.cast_recall")) result += 8.0;
        return result;
    }

    public double ammoConserveChance(Player player) {
        double chance = 0.0;
        if (hasAugment(player, "AUG-S-008")) chance += 0.15;
        if (hasAugment(player, "AUG-P-008")) chance += 0.45;
        return Math.min(0.75, chance);
    }

    public double apRegenBonusPerSecond(RunSnapshot run, RunSnapshot.PlayerState state, long nowEpochMs) {
        double bonus = state.personalAugments.contains("AUG-S-002")
                && nowEpochMs - state.lastDamageAtEpochMs >= 4000L ? 3.0 : 0.0;
        if (partyAugmentIds(run).contains("PAUG-010")) {
            boolean lowAlly = run.players.values().stream().filter(other -> other != state && !"DEAD".equals(other.lifeState))
                    .anyMatch(other -> other.ap <= other.maxAp * 0.20);
            if (lowAlly) bonus += 2.0;
        }
        return bonus;
    }

    private int nearbyLivingMembers(Player player, double range) {
        return (int) runs.onlineMembers().stream().filter(member -> sameWorldWithin(player, member, range))
                .filter(member -> runs.playerState(member.getUniqueId()).map(state -> !"DEAD".equals(state.lifeState)).orElse(false))
                .count();
    }

    private static boolean sameWorldWithin(Player source, Player target, double range) {
        return source.getWorld().equals(target.getWorld())
                && source.getLocation().distanceSquared(target.getLocation()) <= range * range;
    }

    private boolean hasPendingPersonalDraw(RunSnapshot.PlayerState state) {
        return PERSONAL_MILESTONES.stream().anyMatch(milestone -> state.level >= milestone
                && !state.resolvedPersonalMilestones.contains(milestone));
    }

    private static Integer latestPartyMilestone(int day) {
        Integer result = null;
        for (int milestone : PARTY_MILESTONES) if (day >= milestone) result = milestone;
        return result;
    }

    private Integer nextPartyMilestone(RunSnapshot run) {
        java.util.Set<Integer> resolved = run.resolvedPartyAugmentMilestones == null
                ? new java.util.HashSet<>() : new java.util.HashSet<>(run.resolvedPartyAugmentMilestones);
        if (run.partyAugmentId != null && !run.partyAugmentId.isBlank()) resolved.add(10);
        return PARTY_MILESTONES.stream().filter(milestone -> run.day >= milestone && !resolved.contains(milestone))
                .findFirst().orElse(null);
    }

    private static int holderMilestone(RunSnapshot run) {
        if (run.activePartyAugmentMilestone != null) return run.activePartyAugmentMilestone;
        Integer latest = latestPartyMilestone(run.day);
        return latest == null ? 10 : latest;
    }

    private static List<String> partyAugmentIds(RunSnapshot run) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        if (run.partyAugmentIds != null) result.addAll(run.partyAugmentIds);
        if (run.partyAugmentId != null && !run.partyAugmentId.isBlank()) result.add(run.partyAugmentId);
        return List.copyOf(result);
    }

    private static String tierForMilestone(long seed, int milestone) {
        return AugmentMilestonePolicy.tier(seed, milestone);
    }

    private java.util.Set<String> buildTags(RunSnapshot.PlayerState state) {
        java.util.Set<String> tags = new java.util.HashSet<>();
        if (state.mainWeaponId != null) {
            ProductionContentCatalog.CatalogEntry weapon = production.itemsById().get(state.mainWeaponId);
            if (weapon != null) tags.add(switch (weapon.equipmentType()) {
                case "SW" -> "SWORD"; case "AX" -> "AXE"; case "BO" -> "BOW";
                case "CB" -> "CROSSBOW"; case "DG" -> "DAGGER"; case "BL" -> "BLUNT";
                case "ST" -> "MAGIC"; case "PK" -> "PICKAXE"; case "TR" -> "TRIDENT";
                default -> "UNARMED";
            });
        } else tags.add("UNARMED");
        for (String ownedId : state.personalAugments) {
            ProductionContentCatalog.AugmentEntry owned = production.augmentsById().get(ownedId);
            if (owned != null) tags.addAll(owned.tags());
        }
        return tags;
    }

    private static double augmentWeight(ProductionContentCatalog.AugmentEntry augment, java.util.Set<String> buildTags) {
        long matches = augment.tags().stream().filter(buildTags::contains).count();
        double weight = 1.0 + Math.min(2.0, matches * 0.75);
        if (augment.tags().contains("BRIDGE")) weight += 0.35;
        if (augment.evolution() && matches > 0) weight += 0.5;
        return weight;
    }

    private static PrototypeContent.AugmentDefinition runtimeAugment(ProductionContentCatalog.AugmentEntry augment) {
        return new PrototypeContent.AugmentDefinition(augment.id(), augment.name(), augment.tier(), augment.scope(),
                1.0, 1.0, 1.0, 0, 1.0);
    }

    private PrototypeContent.AugmentDefinition findAugment(String id) {
        PrototypeContent.AugmentDefinition augment = runtimeAugments.get(id);
        if (augment == null) throw new IllegalStateException("Unknown augment " + id);
        return augment;
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
