package com.lsc.corp.wsplugin.world;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

public final class DiscoveryService implements Listener {
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final Map<String, String> BOSS_DISCOVERIES = Map.of(
            "BOSS-D10", "C07", "BOSS-D20", "C13", "BOSS-D30", "C20", "BOSS-D40", "C26");
    private static final Map<String, String> RECONSTRUCTION_DISCOVERIES = Map.of(
            "FAC-R01", "C28-A", "FAC-R02", "C28-B", "FAC-R03", "C28-C", "FAC-R04", "C28-D");

    private final RunService runs;
    private final ProductionContentCatalog production;
    private final List<ProductionContentCatalog.DiscoveryEntry> ordered;

    public DiscoveryService(RunService runs, ProductionContentCatalog production) {
        this.runs = runs;
        this.production = production;
        this.ordered = production.discoveriesById().values().stream()
                .sorted(Comparator.comparing(ProductionContentCatalog.DiscoveryEntry::id)).toList();
    }

    public void tick() {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null || !"RUNNING".equals(run.state)) return;
        reconcileObjectiveFacts(run);
        refreshStates();
    }

    public boolean recordEvidence(String discoveryId, String evidenceId, Player actor, boolean conclusive) {
        ProductionContentCatalog.DiscoveryEntry definition = production.discoveriesById().get(discoveryId);
        if (definition == null) throw new IllegalArgumentException("Unknown discovery " + discoveryId);
        String normalized = (conclusive ? "COMPLETE:" : "EVIDENCE:") + evidenceId;
        String actorId = actor == null ? "SYSTEM" : actor.getUniqueId().toString();
        boolean committed = runs.commitOnce("discovery-evidence:" + discoveryId + ":" + normalized,
                "DISCOVERY_EVIDENCE_RECORDED", "{\"discoveryId\":\"" + discoveryId
                        + "\",\"evidence\":\"" + normalized + "\",\"actor\":\"" + actorId + "\"}", run -> {
                    RunSnapshot.DiscoveryNodeState state = node(run, discoveryId);
                    state.evidence.add(normalized);
                    if (state.firstClueAtEpochMs == 0L) {
                        state.firstClueAtEpochMs = runs.clockNowMillis();
                        state.firstCluePlayerUuid = actor == null ? null : actor.getUniqueId().toString();
                    }
                });
        if (committed) refreshStates();
        return committed;
    }

    public void recordFinalCompletion() {
        recordEvidence("C30", "FINAL_COMPLETION_TRANSACTION", null, true);
    }

    public void open(Player player) {
        if (!runs.isMember(player)) {
            player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다.");
            return;
        }
        tick();
        open(player, 0);
    }

    private void open(Player player, int requestedPage) {
        RunSnapshot run = runs.current().orElseThrow();
        List<ProductionContentCatalog.DiscoveryEntry> visible = ordered.stream()
                .filter(entry -> !"HIDDEN".equals(state(run, entry.id()).state)).toList();
        int pageCount = Math.max(1, (visible.size() + ENTRY_SLOTS.length - 1) / ENTRY_SLOTS.length);
        int page = Math.max(0, Math.min(pageCount - 1, requestedPage));
        DiscoveryHolder holder = new DiscoveryHolder(player.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_PURPLE + "발견 기록");
        int start = page * ENTRY_SLOTS.length;
        for (int local = 0; local < ENTRY_SLOTS.length && start + local < visible.size(); local++) {
            ProductionContentCatalog.DiscoveryEntry definition = visible.get(start + local);
            inventory.setItem(ENTRY_SLOTS[local], render(definition, state(run, definition.id())));
        }
        inventory.setItem(4, named(Material.SPYGLASS, ChatColor.LIGHT_PURPLE + "발견 "
                        + run.discoveryIds.size() + "/" + production.discoveriesById().size(), List.of(
                ChatColor.GRAY + "관찰·실험 증거는 숨김 상태에서도 소급 저장됩니다.",
                ChatColor.WHITE + "페이지 " + (page + 1) + "/" + pageCount)));
        if (page > 0) inventory.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "이전 페이지", List.of()));
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        if (page + 1 < pageCount) inventory.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "다음 페이지", List.of()));
        player.openInventory(inventory);
    }

    private void reconcileObjectiveFacts(RunSnapshot run) {
        for (Map.Entry<String, String> entry : BOSS_DISCOVERIES.entrySet()) {
            if (run.defeatedBossIds.contains(entry.getKey())) {
                recordEvidence(entry.getValue(), entry.getKey() + "_DEFEATED", null, true);
            }
        }
        if (run.reconstructionPartIds.containsAll(Set.of("A", "B", "C", "D"))) {
            recordEvidence("C27", "RECONSTRUCTION_PARTS_A_TO_D_REGISTERED", null, true);
        }
        for (Map.Entry<String, String> entry : RECONSTRUCTION_DISCOVERIES.entrySet()) {
            boolean ready = run.facilities.values().stream().anyMatch(instance -> entry.getKey().equals(instance.facilityType)
                    && "READY".equals(instance.state));
            if (ready) recordEvidence(entry.getValue(), entry.getKey() + "_READY", null, true);
        }
        long calibrated = run.facilities.values().stream().filter(instance -> "FAC-R05".equals(instance.facilityType)
                && "CALIBRATED".equals(instance.state)).count();
        if (calibrated >= 3) recordEvidence("C29", "THREE_SIGNAL_STAKES_CALIBRATED", null, true);
        if (run.finalObjective != null && run.finalObjective.completionCommitted) recordFinalCompletion();
    }

    private void refreshStates() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null) return;
        boolean needsRefresh = false;
        for (ProductionContentCatalog.DiscoveryEntry definition : ordered) {
            RunSnapshot.DiscoveryNodeState current = state(snapshot, definition.id());
            String desired = DiscoveryPolicy.desiredState(definition, snapshot, current);
            if (!desired.equals(current.state)) { needsRefresh = true; break; }
        }
        if (!needsRefresh) return;
        Set<String> newlyDiscovered = new LinkedHashSet<>();
        runs.mutate(run -> {
            boolean changed;
            do {
                changed = false;
                for (ProductionContentCatalog.DiscoveryEntry definition : ordered) {
                    RunSnapshot.DiscoveryNodeState current = node(run, definition.id());
                    String desired = DiscoveryPolicy.desiredState(definition, run, current);
                    if (desired.equals(current.state)) continue;
                    current.state = desired;
                    if ("DISCOVERED".equals(desired)) {
                        current.discoveredAtEpochMs = runs.clockNowMillis();
                        current.unlockCommitted = true;
                        if (run.discoveryIds.add(definition.id())) newlyDiscovered.add(definition.id());
                    }
                    changed = true;
                }
                if (run.discoveryIds.containsAll(Set.of("C28-A", "C28-B", "C28-C", "C28-D"))
                        && run.day >= 42 && run.discoveryIds.add("C28")) {
                    RunSnapshot.DiscoveryNodeState aggregate = node(run, "C28");
                    aggregate.state = "DISCOVERED";
                    aggregate.discoveredAtEpochMs = runs.clockNowMillis();
                    aggregate.unlockCommitted = true;
                    newlyDiscovered.add("C28");
                    changed = true;
                }
            } while (changed);
        });
        for (String id : newlyDiscovered) {
            ProductionContentCatalog.DiscoveryEntry definition = production.discoveriesById().get(id);
            runs.broadcast(ChatColor.LIGHT_PURPLE + "[발견] " + definition.name() + " — " + definition.unlockText());
        }
    }

    private ItemStack render(ProductionContentCatalog.DiscoveryEntry definition, RunSnapshot.DiscoveryNodeState state) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + definition.id() + " · " + definition.kind());
        lore.add(ChatColor.WHITE + "상태: " + state.state);
        lore.add(ChatColor.GRAY + "권장 Day " + definition.recommendedDayMin() + "~" + definition.recommendedDayMax());
        Material material;
        String name;
        switch (state.state) {
            case "DISCOVERED", "MASTERED" -> {
                material = state.state.equals("MASTERED") ? Material.NETHER_STAR : Material.ENCHANTED_BOOK;
                name = ChatColor.GOLD + definition.name();
                lore.add(ChatColor.GREEN + "해금: " + definition.unlockText());
                lore.add(ChatColor.GRAY + "주 경로: " + definition.primaryPath());
                if (!definition.alternativePath().isBlank()) lore.add(ChatColor.GRAY + "대체: " + definition.alternativePath());
            }
            case "EXPERIMENT" -> {
                material = Material.AMETHYST_SHARD;
                name = ChatColor.LIGHT_PURPLE + "실험 중인 현상";
                lore.add(ChatColor.GRAY + definition.primaryPath());
                lore.add(ChatColor.YELLOW + "누적 증거 " + state.evidence.size() + "개");
            }
            case "HYPOTHESIS" -> {
                material = Material.WRITABLE_BOOK;
                name = ChatColor.AQUA + "가설이 생긴 현상";
                lore.add(ChatColor.GRAY + definition.primaryPath());
                if (!definition.alternativePath().isBlank()) lore.add(ChatColor.GRAY + "대체 경로가 존재합니다.");
            }
            default -> {
                material = Material.GRAY_DYE;
                name = ChatColor.GRAY + "관측된 미지 현상";
                lore.add(ChatColor.GRAY + (definition.clueText().isBlank() ? "추가 관찰이 필요합니다." : definition.clueText()));
            }
        }
        return named(material, name, lore);
    }

    private static RunSnapshot.DiscoveryNodeState state(RunSnapshot run, String id) {
        RunSnapshot.DiscoveryNodeState state = run.discoveryNodes.get(id);
        if (state != null) return state;
        RunSnapshot.DiscoveryNodeState hidden = new RunSnapshot.DiscoveryNodeState();
        hidden.discoveryId = id;
        return hidden;
    }

    private static RunSnapshot.DiscoveryNodeState node(RunSnapshot run, String id) {
        return run.discoveryNodes.computeIfAbsent(id, ignored -> {
            RunSnapshot.DiscoveryNodeState created = new RunSnapshot.DiscoveryNodeState();
            created.discoveryId = id;
            return created;
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DiscoveryHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (event.getRawSlot() == 45) open(player, holder.page - 1);
        else if (event.getRawSlot() == 53) open(player, holder.page + 1);
        else if (event.getRawSlot() == 49) player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DiscoveryHolder) event.setCancelled(true);
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta);
        return item;
    }

    private static final class DiscoveryHolder implements InventoryHolder {
        private final UUID owner;
        private final int page;
        private DiscoveryHolder(UUID owner, int page) { this.owner = owner; this.page = page; }
        @Override public Inventory getInventory() { return null; }
    }
}
