package com.lsc.corp.wsplugin.research;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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

/** Party research journal. Starting work remains hard-blocked until the two data contracts are supplied. */
public final class ResearchService implements Listener {
    private final RunService runs;
    private final List<ProductionContentCatalog.ResearchEntry> ordered;

    public ResearchService(RunService runs, ProductionContentCatalog production) {
        this.runs = runs;
        this.ordered = production.researchById().values().stream()
                .sorted(Comparator.comparingInt(ProductionContentCatalog.ResearchEntry::minimumDay)
                        .thenComparing(ProductionContentCatalog.ResearchEntry::id))
                .toList();
    }

    public void restore() {
        if (runs.current().isEmpty()) return;
        runs.mutate(run -> {
            for (ProductionContentCatalog.ResearchEntry entry : ordered) {
                run.researchNodes.computeIfAbsent(entry.id(), id -> {
                    RunSnapshot.ResearchNodeState state = new RunSnapshot.ResearchNodeState();
                    state.researchId = id;
                    return state;
                });
            }
            refreshAvailability(run);
        });
    }

    public void tick() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state)) return;
        if (needsRefresh(snapshot)) runs.mutate(this::refreshAvailability);
    }

    public void open(Player player) {
        if (!runs.isMember(player)) return;
        restore();
        RunSnapshot snapshot = runs.current().orElseThrow();
        Inventory inventory = Bukkit.createInventory(new ResearchHolder(player.getUniqueId()), 54,
                ChatColor.DARK_GREEN + "파티 연구 원장");
        ItemStack border = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, border);
        inventory.setItem(4, named(Material.LECTERN, ChatColor.GREEN + "Season 1 연구",
                List.of(ChatColor.WHITE + "공용 연구 " + ordered.size() + "개",
                        ChatColor.RED + "입력 계약 미확정: 연구 시작 차단",
                        ChatColor.GRAY + "조회는 가능하며 비용은 소비되지 않습니다.")));
        int slot = 9;
        for (ProductionContentCatalog.ResearchEntry entry : ordered) {
            RunSnapshot.ResearchNodeState state = snapshot.researchNodes.get(entry.id());
            inventory.setItem(slot++, icon(entry, state == null ? "HIDDEN" : state.state));
        }
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ResearchHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        int index = event.getRawSlot() - 9;
        if (index < 0 || index >= ordered.size()) return;
        ProductionContentCatalog.ResearchEntry entry = ordered.get(index);
        RunSnapshot.ResearchNodeState state = runs.current().orElseThrow().researchNodes.get(entry.id());
        player.sendMessage(ChatColor.RED + "[DATA BLOCKER] " + entry.id() + " 연구는 아직 시작할 수 없습니다.");
        player.sendMessage(ChatColor.GRAY + "필요 1: general/metal/signal/specialist → 실제 자원 ID·동가치 표");
        player.sendMessage(ChatColor.GRAY + "필요 2: '" + entry.comparisonInput() + "'의 기계 판독 가능한 증거 ID·수량");
        if (state != null && "HIDDEN".equals(state.state)) {
            player.sendMessage(ChatColor.YELLOW + "최소 Day " + entry.minimumDay() + " · " + entry.prerequisiteText());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ResearchHolder) event.setCancelled(true);
    }

    private void refreshAvailability(RunSnapshot run) {
        Set<String> completed = new LinkedHashSet<>();
        run.researchNodes.values().stream()
                .filter(value -> "UNLOCKED".equals(value.state) || "MASTERED".equals(value.state))
                .forEach(value -> completed.add(value.researchId));
        Set<String> discoveries = new LinkedHashSet<>(run.discoveryIds);
        run.discoveryNodes.values().stream()
                .filter(value -> "DISCOVERED".equals(value.state) || "MASTERED".equals(value.state))
                .forEach(value -> discoveries.add(value.discoveryId));
        for (ProductionContentCatalog.ResearchEntry entry : ordered) {
            RunSnapshot.ResearchNodeState state = run.researchNodes.get(entry.id());
            if (state == null || Set.of("QUEUED", "PROCESSING", "PAUSED", "ANALYZED", "UNLOCKED", "MASTERED")
                    .contains(state.state)) continue;
            state.state = ResearchPolicy.availability(run.day, entry.minimumDay(), entry.prerequisiteText(),
                    discoveries, completed, false);
        }
    }

    private boolean needsRefresh(RunSnapshot run) {
        Set<String> completed = new LinkedHashSet<>();
        run.researchNodes.values().stream()
                .filter(value -> "UNLOCKED".equals(value.state) || "MASTERED".equals(value.state))
                .forEach(value -> completed.add(value.researchId));
        Set<String> discoveries = new LinkedHashSet<>(run.discoveryIds);
        run.discoveryNodes.values().stream()
                .filter(value -> "DISCOVERED".equals(value.state) || "MASTERED".equals(value.state))
                .forEach(value -> discoveries.add(value.discoveryId));
        for (ProductionContentCatalog.ResearchEntry entry : ordered) {
            RunSnapshot.ResearchNodeState state = run.researchNodes.get(entry.id());
            if (state == null) return true;
            if (Set.of("QUEUED", "PROCESSING", "PAUSED", "ANALYZED", "UNLOCKED", "MASTERED")
                    .contains(state.state)) continue;
            String expected = ResearchPolicy.availability(run.day, entry.minimumDay(), entry.prerequisiteText(),
                    discoveries, completed, false);
            if (!expected.equals(state.state)) return true;
        }
        return false;
    }

    private static ItemStack icon(ProductionContentCatalog.ResearchEntry entry, String state) {
        Material material = switch (state) {
            case "UNLOCKED", "MASTERED" -> Material.LIME_DYE;
            case "QUEUED", "PROCESSING", "PAUSED", "ANALYZED" -> Material.CLOCK;
            case "READY" -> Material.REDSTONE_TORCH;
            case "HYPOTHESIZED" -> Material.WRITABLE_BOOK;
            case "OBSERVABLE" -> Material.PAPER;
            default -> Material.BLACK_DYE;
        };
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + "상태: " + state + " · 최소 Day " + entry.minimumDay());
        lore.add(ChatColor.GRAY + "선행: " + entry.prerequisiteText());
        lore.add(ChatColor.GRAY + "비교: " + entry.comparisonInput());
        lore.add(ChatColor.GRAY + "비용 G/M/S/X: " + entry.cost().get("general") + "/"
                + entry.cost().get("metal") + "/" + entry.cost().get("signal") + "/"
                + entry.cost().get("specialist"));
        lore.add(ChatColor.GRAY + "시간 " + entry.durationSeconds() + "초");
        lore.add(ChatColor.AQUA + "해금: " + entry.unlockText());
        if ("HYPOTHESIZED".equals(state)) lore.add(ChatColor.RED + "클릭: 미확정 데이터 계약 확인");
        return named(material, color(state) + entry.id(), lore);
    }

    private static ChatColor color(String state) {
        return switch (state) {
            case "UNLOCKED", "MASTERED" -> ChatColor.GREEN;
            case "QUEUED", "PROCESSING", "PAUSED", "ANALYZED" -> ChatColor.GOLD;
            case "READY", "HYPOTHESIZED" -> ChatColor.YELLOW;
            case "OBSERVABLE" -> ChatColor.WHITE;
            default -> ChatColor.DARK_GRAY;
        };
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static final class ResearchHolder implements InventoryHolder {
        private final UUID owner;
        private ResearchHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
}
