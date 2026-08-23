package com.lsc.corp.wsplugin.player;

import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerStatService implements Listener {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 22};
    private final JavaPlugin plugin;
    private final RunService runs;
    private final GrowthService growth;

    public PlayerStatService(JavaPlugin plugin, RunService runs, GrowthService growth) {
        this.plugin = plugin;
        this.runs = runs;
        this.growth = growth;
    }

    public void open(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) { player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다."); return; }
        Map<String, Integer> baseline = PlayerStatPolicy.validate(state.level, state.investedStats);
        StatHolder holder = new StatHolder(player.getUniqueId(), baseline, new LinkedHashMap<>(baseline));
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_PURPLE + "스탯 배분");
        render(inventory, holder, state.level);
        player.openInventory(inventory);
    }

    public void apply(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return;
        var hp = player.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            double ratio = player.getHealth() / Math.max(1.0, hp.getValue());
            hp.setBaseValue(PlayerStatPolicy.maxHealth(state.investedStats));
            player.setHealth(Math.max(1.0, Math.min(hp.getValue(), hp.getValue() * ratio)));
        }
        var speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(PlayerStatPolicy.movementSpeed(state.investedStats));
        var blockRange = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (blockRange != null) blockRange.setBaseValue(PlayerStatPolicy.blockInteractionRange());
        var entityRange = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (entityRange != null) entityRange.setBaseValue(PlayerStatPolicy.entityInteractionRange());
        growth.recalculateMaxAp(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof StatHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        if (event.getRawSlot() == 49) {
            Map<String, Integer> valid = PlayerStatPolicy.validate(state.level, holder.draft);
            runs.mutate(run -> run.players.get(player.getUniqueId().toString()).investedStats = new LinkedHashMap<>(valid));
            apply(player);
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "스탯 배분이 확정되었습니다.");
            return;
        }
        for (int i = 0; i < SLOTS.length; i++) if (event.getRawSlot() == SLOTS[i]) {
            String id = PlayerStatPolicy.IDS.get(i);
            int value = holder.draft.getOrDefault(id, 0);
            if (event.isRightClick()) holder.draft.put(id, Math.max(holder.baseline.getOrDefault(id, 0), value - 1));
            else if (PlayerStatPolicy.availablePoints(state.level, holder.draft) > 0) holder.draft.put(id, value + 1);
            try { PlayerStatPolicy.validate(state.level, holder.draft); }
            catch (IllegalArgumentException exception) { holder.draft.put(id, value); player.sendMessage(ChatColor.RED + exception.getMessage()); }
            render(event.getInventory(), holder, state.level);
            return;
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof StatHolder) event.setCancelled(true);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (runs.isMember(event.getPlayer())) Bukkit.getScheduler().runTask(plugin, () -> apply(event.getPlayer()));
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        if (runs.isMember(event.getPlayer())) Bukkit.getScheduler().runTask(plugin, () -> apply(event.getPlayer()));
    }

    private void render(Inventory inventory, StatHolder holder, int level) {
        int available = PlayerStatPolicy.availablePoints(level, holder.draft);
        inventory.setItem(4, named(Material.NETHER_STAR, ChatColor.GOLD + "사용 가능 포인트: " + available,
                List.of(ChatColor.GRAY + "좌클릭 +1 / 우클릭은 이번 창에서만 -1",
                        ChatColor.WHITE + "기본 보정: 이동속도 +10% · 블록/개체 상호작용 거리 +30%")));
        Material[] materials = {Material.RED_DYE, Material.LIGHT_BLUE_DYE, Material.IRON_SWORD, Material.SHIELD,
                Material.SPECTRAL_ARROW, Material.FEATHER, Material.SUGAR, Material.EXPERIENCE_BOTTLE};
        for (int i = 0; i < SLOTS.length; i++) {
            String id = PlayerStatPolicy.IDS.get(i);
            inventory.setItem(SLOTS[i], named(materials[i], ChatColor.AQUA + id + " " + holder.draft.getOrDefault(id, 0),
                    List.of(description(id))));
        }
        inventory.setItem(49, named(Material.LIME_DYE, ChatColor.GREEN + "배분 확정", List.of(ChatColor.RED + "확정 후 무료 초기화 불가")));
    }

    private String description(String id) {
        return switch (id) {
            case "HP" -> "최대 체력 +0.2/포인트"; case "AP" -> "최대 AP +1 (25 제한)";
            case "ATK" -> "공격 피해 +2%"; case "DEF" -> "받는 피해 감소";
            case "HIT" -> "상태이상 적중 +0.5%"; case "EVA" -> "회피 AP·거리 개선";
            case "SPD" -> "이동 속도 +0.5%"; case "EXP" -> "활동 경험치 +0.6%"; default -> "";
        };
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item;
    }
    private static final class StatHolder implements InventoryHolder {
        private final UUID owner; private final Map<String, Integer> baseline; private final Map<String, Integer> draft;
        private StatHolder(UUID owner, Map<String, Integer> baseline, Map<String, Integer> draft) {
            this.owner = owner; this.baseline = baseline; this.draft = draft;
        }
        @Override public Inventory getInventory() { return null; }
    }
}
