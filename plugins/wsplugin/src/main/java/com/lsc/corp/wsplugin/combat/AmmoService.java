package com.lsc.corp.wsplugin.combat;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.economy.ItemCodexService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AmmoService implements Listener {
    private static final String REGISTERED_ITEM_PREFIX = "ITEM:";
    private final JavaPlugin plugin;
    private final RunService runs;
    private final ProductionContentCatalog production;
    private final ItemCodexService codex;
    private final TelemetryService telemetry;

    public AmmoService(JavaPlugin plugin, RunService runs, ProductionContentCatalog production,
                       ItemCodexService codex, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.production = production;
        this.codex = codex;
        this.telemetry = telemetry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUseAmmo(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !runs.isRunningMember(event.getPlayer())) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (player.getInventory().getHeldItemSlot() == 0) return;
        ItemStack held = event.getItem();
        String itemId = codex.itemId(held);
        ProductionContentCatalog.ItemEntry definition = itemId == null
                ? null : production.nonEquipmentItemsById().get(itemId);
        if (definition == null || !"AMMO".equals(definition.category())) return;
        event.setCancelled(true);
        if (!AmmoLedgerPolicy.executable(itemId)) {
            player.sendMessage(ChatColor.RED + definition.name()
                    + "은 특수 탄약 수치·발사 계약이 확정되기 전까지 입금·소비하지 않습니다.");
            return;
        }
        if (!reconcilePendingInventory(player)) {
            player.sendMessage(ChatColor.RED + "이전 탄약 거래를 복구하는 중입니다. 인벤토리 공간을 확인하세요.");
            return;
        }
        int amount = held == null ? 0 : held.getAmount();
        int inventoryBefore = codex.countItem(player, itemId);
        if (amount < 1 || inventoryBefore < amount) {
            player.sendMessage(ChatColor.RED + "탄약 아이템 예약에 실패했습니다. 수량을 다시 확인하세요.");
            return;
        }
        String checkpointKey = registeredItemCheckpoint(itemId);
        runs.mutateAtomically(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.pendingPhysicalItemCounts.put(checkpointKey, inventoryBefore - amount);
            AmmoLedgerPolicy.deposit(state.ammoLedger, itemId, amount);
        });
        if (!reconcilePendingInventory(player)) {
            player.sendMessage(ChatColor.YELLOW + "탄약 입금은 저장됐고 물리 아이템 정리를 재시도합니다.");
        }
        int balance = balance(player, itemId);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.5f, 1.5f);
        player.sendMessage(ChatColor.AQUA + definition.name() + " " + amount
                + "발 입금 · 원장 잔량 " + balance);
        telemetry.event(runs.current().orElseThrow().runId, "AMMO_LEDGER_DEPOSITED",
                "{\"player\":\"" + player.getUniqueId() + "\",\"itemId\":\""
                        + itemId + "\",\"amount\":" + amount + ",\"balance\":" + balance + "}");
    }

    public int generalArrowBalance(Player player) {
        return balance(player, AmmoLedgerPolicy.GENERAL_ARROW);
    }

    public boolean consumeGeneralArrows(Player player, int amount) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || AmmoLedgerPolicy.balance(state.ammoLedger, AmmoLedgerPolicy.GENERAL_ARROW) < amount) {
            return false;
        }
        final boolean[] consumed = {false};
        runs.mutateAtomically(run -> consumed[0] = AmmoLedgerPolicy.consume(
                run.players.get(player.getUniqueId().toString()).ammoLedger,
                AmmoLedgerPolicy.GENERAL_ARROW, amount));
        return consumed[0];
    }

    public int vanillaArrowCount(Player player) {
        return countMaterial(player, Material.ARROW);
    }

    /**
     * Repairs durable-first physical item checkpoints to their exact saved totals. A successful
     * repair is saved to Bukkit playerdata before the checkpoint is cleared from the run.
     */
    public boolean reconcilePendingInventory(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.pendingPhysicalItemCounts == null
                || state.pendingPhysicalItemCounts.isEmpty()) return true;
        Map<String, Integer> pending = new LinkedHashMap<>(state.pendingPhysicalItemCounts);
        Set<String> completed = new LinkedHashSet<>();
        int corrected = 0;
        for (Map.Entry<String, Integer> entry : pending.entrySet()) {
            int expected = Math.max(0, entry.getValue());
            if (CombatCostMutation.VANILLA_ARROW_CHECKPOINT.equals(entry.getKey())) {
                int before = countMaterial(player, Material.ARROW);
                setMaterialCount(player, Material.ARROW, expected);
                int after = countMaterial(player, Material.ARROW);
                corrected += Math.abs(after - before);
                if (after == expected) completed.add(entry.getKey());
                continue;
            }
            if (entry.getKey().startsWith(REGISTERED_ITEM_PREFIX)) {
                String itemId = entry.getKey().substring(REGISTERED_ITEM_PREFIX.length());
                int before = codex.countItem(player, itemId);
                if (before > expected) codex.takeItem(player, itemId, before - expected);
                else if (before < expected) codex.grantItem(player, itemId, expected - before);
                int after = codex.countItem(player, itemId);
                corrected += Math.abs(after - before);
                if (after == expected) completed.add(entry.getKey());
            }
        }
        player.saveData();
        if (!completed.isEmpty()) {
            runs.mutateAtomically(run -> {
                RunSnapshot.PlayerState mutable = run.players.get(player.getUniqueId().toString());
                for (String key : completed) {
                    if (pending.get(key).equals(mutable.pendingPhysicalItemCounts.get(key))) {
                        mutable.pendingPhysicalItemCounts.remove(key);
                    }
                }
            });
        }
        if (corrected > 0) {
            telemetry.event(runs.current().orElseThrow().runId, "AMMO_PHYSICAL_ITEM_RECOVERED",
                    "{\"player\":\"" + player.getUniqueId() + "\",\"corrected\":" + corrected + "}");
        }
        RunSnapshot.PlayerState after = runs.playerState(player.getUniqueId()).orElse(null);
        return after != null && (after.pendingPhysicalItemCounts == null
                || after.pendingPhysicalItemCounts.isEmpty());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (runs.isMember(event.getPlayer())) reconcilePendingInventory(event.getPlayer());
        });
    }

    private static String registeredItemCheckpoint(String itemId) {
        return REGISTERED_ITEM_PREFIX + itemId;
    }

    private int countMaterial(Player player, Material material) {
        int count = 0;
        for (int slot = 1; slot <= 35; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && item.getType() == material && codex.itemId(item) == null) count += item.getAmount();
        }
        return count;
    }

    private void setMaterialCount(Player player, Material material, int expected) {
        int current = countMaterial(player, material);
        int remove = Math.max(0, current - expected);
        for (int slot = 1; slot <= 35 && remove > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() != material || codex.itemId(item) != null) continue;
            int amount = Math.min(remove, item.getAmount());
            item.setAmount(item.getAmount() - amount);
            if (item.getAmount() <= 0) player.getInventory().setItem(slot, null);
            remove -= amount;
        }
        int add = Math.max(0, expected - countMaterial(player, material));
        for (int slot = 1; slot <= 35 && add > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir() || item.getType() != material
                    || codex.itemId(item) != null || item.getAmount() >= item.getMaxStackSize()) continue;
            int amount = Math.min(add, item.getMaxStackSize() - item.getAmount());
            item.setAmount(item.getAmount() + amount);
            add -= amount;
        }
        for (int slot = 1; slot <= 35 && add > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir()) continue;
            int amount = Math.min(add, material.getMaxStackSize());
            player.getInventory().setItem(slot, new ItemStack(material, amount));
            add -= amount;
        }
    }

    private int balance(Player player, String itemId) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        return state == null ? 0 : AmmoLedgerPolicy.balance(state.ammoLedger, itemId);
    }
}
