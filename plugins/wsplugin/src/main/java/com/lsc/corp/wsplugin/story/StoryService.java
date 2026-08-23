package com.lsc.corp.wsplugin.story;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class StoryService implements Listener {
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final Map<String, String> BOSS_STORY_IDS = Map.of(
            "BOSS-D10", "BOSS-001", "BOSS-D20", "BOSS-002", "BOSS-D30", "BOSS-003", "BOSS-D40", "BOSS-004");
    private static final Map<String, String> BOSS_PARTS = Map.of(
            "BOSS-D10", "A", "BOSS-D20", "B", "BOSS-D30", "C", "BOSS-D40", "D");

    private final RunService runs;
    private final ProductionContentCatalog production;
    private final List<ProductionContentCatalog.StorySceneEntry> ordered;

    public StoryService(RunService runs, ProductionContentCatalog production) {
        this.runs = runs;
        this.production = production;
        this.ordered = production.storyScenesById().values().stream()
                .sorted(Comparator.comparingInt((ProductionContentCatalog.StorySceneEntry value) ->
                                StoryTriggerPolicy.priority(value.priority()))
                        .thenComparing(ProductionContentCatalog.StorySceneEntry::id)).toList();
    }

    public void tick() {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null || !"RUNNING".equals(run.state)) return;
        reconcileFacts(run);
        presentNext();
    }

    public void trigger(String event, String reference) {
        String normalizedReference = reference == null ? "" : reference;
        for (ProductionContentCatalog.StorySceneEntry scene : ordered) {
            if (!StoryTriggerPolicy.matches(scene, event, normalizedReference)) continue;
            runs.commitOnce("story-queue:" + scene.id(), "STORY_SCENE_QUEUED",
                    "{\"sceneId\":\"" + scene.id() + "\",\"trigger\":\"" + scene.triggerKey() + "\"}",
                    run -> run.story.queuedSceneIds.add(scene.id()));
        }
    }

    public void unlockLog(String logId) {
        if (!production.storyLogsById().containsKey(logId)) throw new IllegalArgumentException("Unknown Story log " + logId);
        runs.commitOnce("story-log:" + logId, "STORY_LOG_UNLOCKED", "{\"logId\":\"" + logId + "\"}",
                run -> run.story.unlockedLogIds.add(logId));
    }

    public void open(Player player) {
        if (!runs.isMember(player)) {
            player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다.");
            return;
        }
        open(player, 0);
    }

    private void open(Player player, int requestedPage) {
        RunSnapshot run = runs.current().orElseThrow();
        List<ProductionContentCatalog.StorySceneEntry> visible = ordered.stream()
                .filter(scene -> run.story.playedSceneIds.contains(scene.id())
                        || run.story.queuedSceneIds.contains(scene.id())).toList();
        int pageCount = Math.max(1, (visible.size() + ENTRY_SLOTS.length - 1) / ENTRY_SLOTS.length);
        int page = Math.max(0, Math.min(pageCount - 1, requestedPage));
        Inventory inventory = Bukkit.createInventory(new StoryHolder(player.getUniqueId(), page), 54,
                ChatColor.DARK_BLUE + "Story 기록");
        int start = page * ENTRY_SLOTS.length;
        for (int local = 0; local < ENTRY_SLOTS.length && start + local < visible.size(); local++) {
            ProductionContentCatalog.StorySceneEntry scene = visible.get(start + local);
            boolean played = run.story.playedSceneIds.contains(scene.id());
            inventory.setItem(ENTRY_SLOTS[local], render(scene, played));
        }
        inventory.setItem(4, named(Material.WRITTEN_BOOK, ChatColor.GOLD + "Season 1 기록 "
                        + run.story.playedSceneIds.size() + "/" + ordered.size(), List.of(
                ChatColor.GRAY + "Story 표현 실패는 전투·연구·Final 원장을 되돌리지 않습니다.",
                ChatColor.WHITE + "페이지 " + (page + 1) + "/" + pageCount)));
        inventory.setItem(46, named(Material.MAP, ChatColor.AQUA + "선택 기록 " + run.story.unlockedLogIds.size()
                + "/" + production.storyLogsById().size(), new ArrayList<>(run.story.unlockedLogIds)));
        if (page > 0) inventory.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "이전 페이지", List.of()));
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        if (page + 1 < pageCount) inventory.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "다음 페이지", List.of()));
        player.openInventory(inventory);
    }

    private void reconcileFacts(RunSnapshot run) {
        trigger("RUN_STATE_CHANGED", "ACTIVE");
        trigger("REGISTERED_MEMBERS_SPAWNED", "");
        for (RunSnapshot.DiscoveryNodeState state : run.discoveryNodes.values()) {
            if ("CLUE".equals(state.state)) trigger("DISCOVERY", state.discoveryId + ":CLUE_FOUND");
            if ("DISCOVERED".equals(state.state) || "MASTERED".equals(state.state)) {
                trigger("DISCOVERY", state.discoveryId + ":DISCOVERED");
                trigger("DISCOVERY", state.discoveryId + ":COMPLETE");
            }
        }
        if (run.boss != null && "ACTIVE".equals(run.boss.state)) {
            String storyId = BOSS_STORY_IDS.get(run.boss.bossId);
            if (storyId != null) trigger("BOSS_STATE", storyId + ":ACTIVE");
        }
        for (String bossId : run.defeatedBossIds) {
            String part = BOSS_PARTS.get(bossId);
            if (part != null) {
                trigger("BOSS_REWARD", "PART_" + part + ":COMMITTED");
                trigger("BOSS_REWARD_STEP", "B" + bossId.substring(bossId.length() - 2) + "-RW-PART-" + part);
            }
        }
        for (RunSnapshot.PlayerState player : run.players.values()) {
            if (player.level >= 35) trigger("LEVEL_REACHED", "35");
            if (player.level >= 45) trigger("LEVEL_REACHED", "45");
            if (player.resolvedPersonalMilestones.contains(3)) trigger("PERSONAL_AUGMENT_SELECTED", "LEVEL_3");
            if (player.resolvedPersonalMilestones.contains(15)) trigger("AUGMENT_TIER_LOCKED", "LEVEL_15");
        }
        if (run.seasonDay != null && run.seasonDay.activeEventId != null) {
            trigger("EVENT", run.seasonDay.activeEventId + ":ACTIVE");
            trigger("EVENT", run.seasonDay.activeEventId + ":OBJECTIVE");
        }
        if (run.finalObjective != null) {
            trigger("FINAL_STATE", run.finalObjective.state);
            if (run.finalObjective.stage == 1) trigger("FINAL_STATE", "ACTIVE_STAGE_1");
            if (run.finalObjective.completionCommitted) {
                trigger("FINAL_COMPLETION_STEP", "F50-TX-05");
                trigger("RUN_STATE", "COMPLETED");
            }
        }
    }

    private void presentNext() {
        RunSnapshot run = runs.current().orElseThrow();
        ProductionContentCatalog.StorySceneEntry scene = ordered.stream()
                .filter(value -> run.story.queuedSceneIds.contains(value.id()))
                .filter(value -> !run.story.playedSceneIds.contains(value.id())).findFirst().orElse(null);
        if (scene == null) return;
        runs.commitOnce("story-present:" + scene.id(), "STORY_SCENE_PRESENTED",
                "{\"sceneId\":\"" + scene.id() + "\"}", snapshot -> {
                    snapshot.story.activeSceneId = scene.id();
                    snapshot.story.queuedSceneIds.remove(scene.id());
                    snapshot.story.playedSceneIds.add(scene.id());
                    snapshot.story.sequence++;
                });
        String body = scene.payloadText().isBlank() ? "기록 신호가 수신되었습니다." : scene.payloadText();
        for (Player player : runs.onlineMembers()) {
            player.sendTitle(ChatColor.GOLD + "WildSurvival", ChatColor.WHITE + body, 5, 50, 10);
            player.sendMessage(ChatColor.DARK_AQUA + "[Story] " + ChatColor.WHITE + body
                    + (scene.payloadText().isBlank() ? ChatColor.DARK_GRAY + " (" + scene.payloadKey() + ")" : ""));
        }
        runs.mutate(snapshot -> snapshot.story.activeSceneId = null);
    }

    private ItemStack render(ProductionContentCatalog.StorySceneEntry scene, boolean played) {
        String body = scene.payloadText().isBlank() ? "본문 현지화 대기 · " + scene.payloadKey() : scene.payloadText();
        return named(played ? Material.WRITTEN_BOOK : Material.BOOK,
                (played ? ChatColor.GOLD : ChatColor.YELLOW) + scene.id(), List.of(
                        ChatColor.DARK_GRAY + scene.sceneGroup() + " · " + scene.priority(),
                        ChatColor.GRAY + body,
                        ChatColor.DARK_GRAY + "Trigger: " + scene.triggerKey()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof StoryHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (event.getRawSlot() == 45) open(player, holder.page - 1);
        else if (event.getRawSlot() == 53) open(player, holder.page + 1);
        else if (event.getRawSlot() == 49) player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof StoryHolder) event.setCancelled(true);
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta);
        return item;
    }

    private static final class StoryHolder implements InventoryHolder {
        private final UUID owner;
        private final int page;
        private StoryHolder(UUID owner, int page) { this.owner = owner; this.page = page; }
        @Override public Inventory getInventory() { return null; }
    }
}
