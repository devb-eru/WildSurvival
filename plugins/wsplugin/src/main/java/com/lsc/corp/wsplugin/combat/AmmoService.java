package com.lsc.corp.wsplugin.combat;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.economy.ItemCodexService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class AmmoService implements Listener {
    private final RunService runs;
    private final ProductionContentCatalog production;
    private final ItemCodexService codex;
    private final TelemetryService telemetry;

    public AmmoService(RunService runs, ProductionContentCatalog production,
                       ItemCodexService codex, TelemetryService telemetry) {
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
        int amount = held == null ? 0 : held.getAmount();
        if (amount < 1 || !codex.takeItem(player, itemId, amount)) {
            player.sendMessage(ChatColor.RED + "탄약 아이템 예약에 실패했습니다. 수량을 다시 확인하세요.");
            return;
        }
        try {
            runs.mutate(run -> AmmoLedgerPolicy.deposit(
                    run.players.get(player.getUniqueId().toString()).ammoLedger, itemId, amount));
        } catch (RuntimeException exception) {
            codex.grantItem(player, itemId, amount);
            throw exception;
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
        runs.mutate(run -> consumed[0] = AmmoLedgerPolicy.consume(
                run.players.get(player.getUniqueId().toString()).ammoLedger,
                AmmoLedgerPolicy.GENERAL_ARROW, amount));
        return consumed[0];
    }

    private int balance(Player player, String itemId) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        return state == null ? 0 : AmmoLedgerPolicy.balance(state.ammoLedger, itemId);
    }
}
