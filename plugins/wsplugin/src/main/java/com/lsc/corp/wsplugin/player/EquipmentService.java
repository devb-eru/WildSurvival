package com.lsc.corp.wsplugin.player;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class EquipmentService implements Listener {
    private static final int GUI_MAIN_WEAPON = 20;
    private static final int GUI_OFFHAND = 24;
    private static final int[] GUI_CATALOGUE = {29, 31, 33, 35};
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent content;
    private final TelemetryService telemetry;
    private final NamespacedKey weaponIdKey;
    private final NamespacedKey proxyKey;
    private int tickCounter;

    public EquipmentService(JavaPlugin plugin, RunService runs, PrototypeContent content, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.telemetry = telemetry;
        this.weaponIdKey = new NamespacedKey(plugin, "weapon_id");
        this.proxyKey = new NamespacedKey(plugin, "input_proxy");
    }

    public void open(Player player) {
        if (!runs.isMember(player)) {
            player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다.");
            return;
        }
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        EquipmentHolder holder = new EquipmentHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 45, ChatColor.DARK_GREEN + "WildSurvival 장비");
        inventory.setItem(GUI_MAIN_WEAPON, state.mainWeaponId == null
                ? named(Material.PLAYER_HEAD, ChatColor.YELLOW + "주무기: 권투(빈 슬롯)", List.of("실제 슬롯 0"))
                : weaponItem(state.mainWeaponId));
        inventory.setItem(GUI_OFFHAND, named(Material.SHIELD, ChatColor.GRAY + "보조무기: "
                + (state.offhandId == null ? "비어 있음" : state.offhandId), List.of("실제 슬롯 -106")));
        List<String> owned = new ArrayList<>(state.ownedEquipment);
        owned.remove("UNARMED");
        for (int i = 0; i < Math.min(owned.size(), GUI_CATALOGUE.length); i++) {
            ItemStack item = weaponItem(owned.get(i));
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();
            lore.add(ChatColor.YELLOW + "클릭하여 주무기 장착");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(GUI_CATALOGUE[i], item);
        }
        inventory.setItem(40, named(Material.BARRIER, ChatColor.RED + "권투로 전환", List.of("주무기와 보조무기를 비웁니다.")));
        player.openInventory(inventory);
    }

    public void grantEquipment(Player player, String weaponId) {
        content.weapon(weaponId);
        runs.mutate(run -> run.players.get(player.getUniqueId().toString()).ownedEquipment.add(weaponId));
        player.sendMessage(ChatColor.GREEN + content.weapon(weaponId).name() + " 제작 완료. /ws equipment에서 장착하세요.");
    }

    public void grantQuickItem(Player player, String id, int amount) {
        runs.mutate(run -> run.players.get(player.getUniqueId().toString()).quickItems.merge(id, amount, Integer::sum));
        syncAuthoritativeEquipment(player);
    }

    public boolean consumeQuickItem(Player player, String id) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.quickItems.getOrDefault(id, 0) <= 0) {
            return false;
        }
        runs.mutate(run -> run.players.get(player.getUniqueId().toString()).quickItems.compute(id,
                (ignored, count) -> count == null || count <= 1 ? 0 : count - 1));
        syncAuthoritativeEquipment(player);
        return true;
    }

    public String resolveWeaponId(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.mainWeaponId == null || state.mainWeaponId.isBlank()) {
            return "UNARMED";
        }
        return state.mainWeaponId;
    }

    public void syncAuthoritativeEquipment(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack expectedMain = state.mainWeaponId == null ? null : weaponItem(state.mainWeaponId);
        if (!sameAuthoritativeItem(inventory.getItem(0), expectedMain)) {
            ItemStack displaced = inventory.getItem(0);
            inventory.setItem(0, null);
            preserveUnexpected(player, displaced);
            inventory.setItem(0, expectedMain);
            auditSlotRepair(player, "0");
        }
        ItemStack expectedOff = state.offhandId == null ? null : named(Material.SHIELD, ChatColor.GRAY + state.offhandId, List.of("보조무기"));
        if (!sameAuthoritativeItem(inventory.getItemInOffHand(), expectedOff)) {
            ItemStack displaced = inventory.getItemInOffHand();
            inventory.setItemInOffHand(null);
            preserveUnexpected(player, displaced);
            inventory.setItemInOffHand(expectedOff);
            auditSlotRepair(player, "-106");
        }
        for (int slot = 1; slot <= 4; slot++) {
            ItemStack displaced = inventory.getItem(slot);
            if (!isWildSurvivalItem(displaced)) {
                inventory.setItem(slot, null);
                preserveUnexpected(player, displaced);
            }
            inventory.setItem(slot, proxy(Material.ECHO_SHARD, "C" + slot + " 공용 액티브", "Shift+숫자 " + (slot + 1)));
        }
        int rationCount = state.quickItems.getOrDefault("RATION", 0);
        ItemStack displacedQ1 = inventory.getItem(5);
        if (!isWildSurvivalItem(displacedQ1)) {
            inventory.setItem(5, null);
            preserveUnexpected(player, displacedQ1);
        }
        inventory.setItem(5, quickItem("Q1 응급 배급", rationCount));
        for (int slot = 6; slot <= 8; slot++) {
            ItemStack displaced = inventory.getItem(slot);
            if (!isWildSurvivalItem(displaced)) {
                inventory.setItem(slot, null);
                preserveUnexpected(player, displaced);
            }
            inventory.setItem(slot, proxy(Material.GRAY_DYE, "Q" + (slot - 4) + " 비어 있음", "숫자 " + (slot + 1)));
        }
    }

    public void tick() {
        if (++tickCounter % 5 != 0) {
            return;
        }
        for (Player player : runs.onlineMembers()) {
            syncAuthoritativeEquipment(player);
            if (player.getInventory().getHeldItemSlot() != 0) {
                player.getInventory().setHeldItemSlot(0);
            }
        }
    }

    public ItemStack weaponItem(String weaponId) {
        PrototypeContent.WeaponDefinition weapon = content.weapon(weaponId);
        Material material = Material.matchMaterial(weapon.material());
        if (material == null || material.isAir()) {
            return null;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "[WS] " + weapon.name());
        meta.setLore(List.of(
                ChatColor.GRAY + "서버 권위 BASIC_ATTACK",
                ChatColor.WHITE + "간격 " + weapon.intervalTicks() + "틱 / 사거리 " + weapon.range(),
                ChatColor.DARK_GRAY + "ID: " + weapon.id()
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(weaponIdKey, PersistentDataType.STRING, weaponId);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof EquipmentHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner)) {
                return;
            }
            if (event.getRawSlot() == 40) {
                equip(player, null, null);
                player.closeInventory();
                return;
            }
            if (java.util.Arrays.stream(GUI_CATALOGUE).anyMatch(slot -> slot == event.getRawSlot())) {
                ItemStack clicked = event.getCurrentItem();
                String weaponId = weaponId(clicked);
                if (weaponId != null) {
                    equip(player, weaponId, null);
                    player.closeInventory();
                }
            }
            return;
        }
        if (event.getWhoClicked() instanceof Player player && runs.isMember(player) && event.getClickedInventory() instanceof PlayerInventory
                && (event.getSlot() == 0 || event.getSlot() == 40 || (event.getSlot() >= 1 && event.getSlot() <= 8))) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> syncAuthoritativeEquipment(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && runs.isMember(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> syncAuthoritativeEquipment(player));
        }
    }

    @EventHandler
    public void onEquipmentClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof EquipmentHolder && event.getPlayer() instanceof Player player) {
            syncAuthoritativeEquipment(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (runs.isMember(event.getPlayer()) && isWildSurvivalItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> syncAuthoritativeEquipment(event.getPlayer()));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (runs.isMember(event.getPlayer())) {
            runs.restorePlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (runs.isMember(event.getPlayer())) {
            runs.savePlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (runs.isMember(event.getPlayer())) {
            Bukkit.getScheduler().runTask(plugin, () -> syncAuthoritativeEquipment(event.getPlayer()));
        }
    }

    private void equip(Player player, String mainWeaponId, String offhandId) {
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            if (mainWeaponId != null && !state.ownedEquipment.contains(mainWeaponId)) {
                throw new IllegalStateException("Equipment is not owned");
            }
            state.mainWeaponId = mainWeaponId;
            state.offhandId = offhandId;
        });
        syncAuthoritativeEquipment(player);
        player.sendMessage(ChatColor.GREEN + "장착 완료: " + (mainWeaponId == null ? "권투" : content.weapon(mainWeaponId).name()));
        telemetry.event(runs.current().orElseThrow().runId, "EQUIPMENT_COMMITTED",
                "{\"slot\":\"0\",\"weaponId\":\"" + (mainWeaponId == null ? "UNARMED" : mainWeaponId) + "\"}");
    }

    private String weaponId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(weaponIdKey, PersistentDataType.STRING);
    }

    private boolean isWildSurvivalItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(weaponIdKey, PersistentDataType.STRING)
                || item.getItemMeta().getPersistentDataContainer().has(proxyKey, PersistentDataType.STRING);
    }

    private boolean sameAuthoritativeItem(ItemStack actual, ItemStack expected) {
        if (actual == null || actual.getType().isAir()) {
            return expected == null || expected.getType().isAir();
        }
        if (expected == null || expected.getType().isAir()) {
            return false;
        }
        String actualWeapon = weaponId(actual);
        String expectedWeapon = weaponId(expected);
        if (actualWeapon != null || expectedWeapon != null) {
            return Objects.equals(actualWeapon, expectedWeapon);
        }
        return actual.isSimilar(expected);
    }

    private void preserveUnexpected(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || isWildSurvivalItem(item)) {
            return;
        }
        ItemStack remaining = item.clone();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 9; slot <= 35 && remaining.getAmount() > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(remaining)) {
                continue;
            }
            int capacity = existing.getMaxStackSize() - existing.getAmount();
            if (capacity <= 0) {
                continue;
            }
            int moved = Math.min(capacity, remaining.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
        }
        for (int slot = 9; slot <= 35 && remaining.getAmount() > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            int moved = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            inventory.setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - moved);
        }
        if (remaining.getAmount() > 0) {
            player.getWorld().dropItemNaturally(player.getLocation(), remaining);
        }
    }

    private void auditSlotRepair(Player player, String slot) {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot != null) {
            telemetry.audit(snapshot.runId, player.getUniqueId().toString(), "equipment.slot_repair", "slot=" + slot);
        }
    }

    private ItemStack proxy(Material material, String name, String hint) {
        ItemStack item = named(material, ChatColor.AQUA + name, List.of(ChatColor.GRAY + hint, ChatColor.DARK_GRAY + "입력 프록시 — 이동/드롭 불가"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(proxyKey, PersistentDataType.STRING, name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack quickItem(String name, int count) {
        Material material = count > 0 ? Material.COOKED_BEEF : Material.GRAY_DYE;
        ItemStack item = proxy(material, name, "숫자 6 / 보유 " + count);
        item.setAmount(Math.max(1, Math.min(64, count)));
        return item;
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static final class EquipmentHolder implements InventoryHolder {
        private final UUID owner;

        private EquipmentHolder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
