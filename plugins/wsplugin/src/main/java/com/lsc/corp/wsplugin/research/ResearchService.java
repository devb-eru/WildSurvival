package com.lsc.corp.wsplugin.research;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.economy.CostValuePolicy;
import com.lsc.corp.wsplugin.economy.ResourceLedger;
import com.lsc.corp.wsplugin.facility.FacilityStateAccess;
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

/** Party research journal with persisted RCOST reservation and active-time processing. */
public final class ResearchService implements Listener {
    private final RunService runs;
    private final List<ProductionContentCatalog.ResearchEntry> ordered;
    private long lastWorkTick;

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
            reconcileTransactions(run);
            refreshAvailability(run);
        });
        if (!"RUNNING".equals(runs.current().orElseThrow().state)) return;
        List<String> reserved = runs.current().orElseThrow().resourceTransactions.values().stream()
                .filter(transaction -> transaction.costId != null && transaction.costId.startsWith("RCOST-")
                        && "RESERVED".equals(transaction.state))
                .map(transaction -> transaction.transactionId).toList();
        for (String transactionId : reserved) {
            runs.beginResourceTransaction(transactionId, run -> {
                RunSnapshot.ResourceTransactionState transaction = run.resourceTransactions.get(transactionId);
                RunSnapshot.ResearchNodeState state = run.researchNodes.get(transaction.targetId);
                if (state != null) state.state = "PROCESSING";
            });
        }
    }

    public void tick() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state)) return;
        long now = runs.clockNowMillis();
        long delta = lastWorkTick == 0L ? 0L : Math.max(0L, Math.min(2_000L, now - lastWorkTick));
        lastWorkTick = now;
        if (delta > 0L) processResearch(delta, now);
        snapshot = runs.current().orElse(null);
        if (snapshot == null) return;
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
                        ChatColor.GRAY + "좌클릭: 개인 원장 · 우클릭: 공용 원장",
                        ChatColor.GRAY + "공용 원장은 활성 FAC-S16이 필요합니다.")));
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
        if (state == null) return;
        if ("HIDDEN".equals(state.state)) {
            player.sendMessage(ChatColor.YELLOW + "최소 Day " + entry.minimumDay() + " · " + entry.prerequisiteText());
            return;
        }
        if (!"READY".equals(state.state)) {
            if (Set.of("QUEUED", "PROCESSING", "PAUSED", "ANALYZED").contains(state.state)) {
                player.sendMessage(ChatColor.YELLOW + entry.id() + " 진행 " + progress(state) + "% · " + state.state);
            } else if (Set.of("UNLOCKED", "MASTERED").contains(state.state)) {
                player.sendMessage(ChatColor.GREEN + entry.id() + " 연구 완료");
            } else {
                player.sendMessage(ChatColor.RED + "연구 증거가 부족합니다: " + entry.comparisonInput());
            }
            return;
        }
        startResearch(player, entry, event.isRightClick() ? ResourceLedger.Scope.SHARED : ResourceLedger.Scope.PERSONAL);
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

    private void startResearch(Player player, ProductionContentCatalog.ResearchEntry entry, ResourceLedger.Scope scope) {
        RunSnapshot run = runs.current().orElseThrow();
        String owner = player.getUniqueId().toString();
        if (scope == ResourceLedger.Scope.SHARED && (!run.sharedLedgerUnlocked
                || !FacilityStateAccess.active(run, "FAC-S16"))) {
            player.sendMessage(ChatColor.RED + "공용 결제에는 활성 FAC-S16이 필요합니다.");
            return;
        }
        Map<String, Integer> balance = scope == ResourceLedger.Scope.SHARED
                ? run.resources : run.players.get(owner).personalResources;
        Map<String, Integer> planned = CostValuePolicy.plan(entry.cost(), runs.effectivePartySize(), balance);
        if (planned == null) {
            player.sendMessage(ChatColor.RED + "정확한 RCOST 동가치 조합이 부족합니다: " + entry.costId());
            return;
        }
        String transactionId = "cost:research:" + entry.costId();
        ResourceLedger.ReserveResult reserved = runs.reserveResourceTransaction(transactionId, entry.costId(),
                entry.id(), scope, scope == ResourceLedger.Scope.PERSONAL ? owner : null, planned, snapshot -> {
                    RunSnapshot.ResearchNodeState state = snapshot.researchNodes.get(entry.id());
                    state.state = "QUEUED";
                    state.startedByUuid = owner;
                    state.startedAtEpochMs = runs.clockNowMillis();
                    state.durationMillis = entry.durationSeconds() * 1_000L;
                    state.processedMillis = 0L;
                    state.reservedCost.clear();
                    state.reservedCost.putAll(planned);
                });
        if (reserved == ResourceLedger.ReserveResult.ALREADY_COMMITTED) {
            player.sendMessage(ChatColor.GREEN + "이미 완료된 연구입니다.");
            return;
        }
        if (reserved != ResourceLedger.ReserveResult.RESERVED) {
            player.sendMessage(ChatColor.RED + "연구 비용 예약 실패: " + reserved);
            return;
        }
        boolean processingStarted = runs.beginResourceTransaction(transactionId, snapshot -> {
            RunSnapshot.ResearchNodeState state = snapshot.researchNodes.get(entry.id());
            state.state = "PROCESSING";
            state.completesAtEpochMs = 0L;
        });
        if (processingStarted) {
            player.sendMessage(ChatColor.GREEN + entry.id() + " 연구 시작 · " + entry.durationSeconds() + "초");
        } else {
            player.sendMessage(ChatColor.YELLOW + entry.id()
                    + " 연구 비용이 예약되었습니다. 처리 재개를 기다리는 중입니다.");
        }
    }

    private void processResearch(long deltaMillis, long now) {
        List<String> completed = new ArrayList<>();
        runs.mutateTransient(run -> {
            for (ProductionContentCatalog.ResearchEntry entry : ordered) {
                RunSnapshot.ResearchNodeState state = run.researchNodes.get(entry.id());
                if (state == null || !"PROCESSING".equals(state.state) || state.durationMillis <= 0L) continue;
                RunSnapshot.ResourceTransactionState transaction = run.resourceTransactions.values().stream()
                        .filter(value -> entry.id().equals(value.targetId) && "PROCESSING".equals(value.state))
                        .findFirst().orElse(null);
                if (transaction == null) continue;
                state.processedMillis = Math.min(state.durationMillis, state.processedMillis + deltaMillis);
                if (state.processedMillis >= state.durationMillis) completed.add(entry.id());
            }
        });
        for (String researchId : completed) {
            ProductionContentCatalog.ResearchEntry entry = ordered.stream()
                    .filter(value -> value.id().equals(researchId)).findFirst().orElseThrow();
            String transactionId = "cost:research:" + entry.costId();
            boolean committed = runs.commitResourceTransaction(transactionId, "RESEARCH_UNLOCKED",
                    "{\"researchId\":\"" + researchId + "\",\"costId\":\"" + entry.costId() + "\"}", run -> {
                        RunSnapshot.ResearchNodeState state = run.researchNodes.get(researchId);
                        state.state = "UNLOCKED";
                        state.completedAtEpochMs = now;
                        state.unlockCommitted = true;
                    });
            if (committed) runs.broadcast(ChatColor.GREEN + "연구 완료: " + researchId);
        }
    }

    private void reconcileTransactions(RunSnapshot run) {
        for (RunSnapshot.ResourceTransactionState transaction : run.resourceTransactions.values()) {
            if (transaction.costId == null || !transaction.costId.startsWith("RCOST-")) continue;
            RunSnapshot.ResearchNodeState state = run.researchNodes.get(transaction.targetId);
            if (state == null) continue;
            if ("COMMITTED".equals(transaction.state)) {
                state.state = "UNLOCKED";
                state.unlockCommitted = true;
            } else if ("PROCESSING".equals(transaction.state)) {
                state.state = "PROCESSING";
            } else if ("RESERVED".equals(transaction.state)) {
                state.state = "QUEUED";
            }
        }
    }

    private static int progress(RunSnapshot.ResearchNodeState state) {
        return state.durationMillis <= 0L ? 0
                : (int) Math.min(100L, Math.round(state.processedMillis * 100.0 / state.durationMillis));
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
        if ("READY".equals(state)) lore.add(ChatColor.YELLOW + "좌클릭 개인 / 우클릭 공용 결제");
        if ("HYPOTHESIZED".equals(state)) lore.add(ChatColor.RED + "정확한 비교 증거가 더 필요합니다.");
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
