package com.lsc.corp.wsplugin.player;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.economy.ItemCodexService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.ui.ActionBarService;
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
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class EquipmentService implements Listener {
    private static final int GUI_MAIN_WEAPON = 4;
    private static final int GUI_OFFHAND = 6;
    private static final int GUI_CANDIDATE_START = 9;
    private static final int[] GUI_QUICK = {45, 46, 47, 48};
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent content;
    private final TelemetryService telemetry;
    private final ItemCodexService codex;
    private final NamespacedKey weaponIdKey;
    private final NamespacedKey equipmentInstanceIdKey;
    private int tickCounter;

    public EquipmentService(JavaPlugin plugin, RunService runs, PrototypeContent content,
                            TelemetryService telemetry, ItemCodexService codex) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.telemetry = telemetry;
        this.codex = codex;
        this.weaponIdKey = new NamespacedKey(plugin, "weapon_id");
        this.equipmentInstanceIdKey = new NamespacedKey(plugin, "equipment_instance_id");
    }

    public void open(Player player) {
        if (!runs.isMember(player)) {
            player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다.");
            return;
        }
        syncAuthoritativeEquipment(player);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        EquipmentHolder holder = new EquipmentHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_GREEN + "WildSurvival 장비");
        ItemStack border = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
        inventory.setItem(GUI_MAIN_WEAPON, state.mainWeaponId == null
                ? named(Material.PLAYER_HEAD, ChatColor.YELLOW + "주무기: 권투(빈 슬롯)",
                List.of(ChatColor.GRAY + "실제 슬롯 0", ChatColor.WHITE + "장착 아이템은 아래 인벤토리 목록에서 선택"))
                : equippedIcon(player, state.mainWeaponId, state.mainWeaponInstanceId, "주무기", "클릭: 해제"));
        inventory.setItem(GUI_OFFHAND, state.offhandId == null
                ? named(Material.SHIELD, ChatColor.GRAY + "보조무기: 비어 있음",
                List.of(ChatColor.GRAY + "실제 슬롯 -106", ChatColor.WHITE + "후보 우클릭: 보조무기 장착"))
                : equippedIcon(player, state.offhandId, state.offhandInstanceId, "보조무기", "클릭: 해제"));

        for (int storageSlot = 0; storageSlot <= 35; storageSlot++) {
            int guiSlot = GUI_CANDIDATE_START + storageSlot;
            ItemStack actual = player.getInventory().getItem(storageSlot);
            String weaponId = storageSlot == 0 ? null : weaponId(actual);
            if (weaponId == null) {
                inventory.setItem(guiSlot, named(Material.BARRIER, ChatColor.DARK_GRAY + "장착 불가 · 인벤토리 " + storageSlot,
                        List.of(ChatColor.GRAY + (storageSlot == 0 ? "slot 0은 장착 전용입니다." : "장착 가능한 WS 장비가 없습니다."))));
            } else {
                ItemStack icon = actual.clone();
                ItemMeta meta = icon.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();
                lore.add(ChatColor.YELLOW + "좌클릭: 주무기 / 우클릭: 보조무기");
                lore.add(ChatColor.DARK_GRAY + "인벤토리 슬롯 " + storageSlot);
                meta.setLore(lore);
                icon.setItemMeta(meta);
                inventory.setItem(guiSlot, icon);
                holder.inventorySlotByGui.put(guiSlot, storageSlot);
            }
        }
        for (int i = 0; i < GUI_QUICK.length; i++) {
            String bound = state.quickBindings.get(i + 1);
            int amount = bound == null ? 0 : codex.countItem(player, bound);
            Material material = bound == null ? Material.GRAY_DYE : Material.matchMaterial(content.item(bound).material());
            inventory.setItem(GUI_QUICK[i], named(material == null ? Material.PAPER : material,
                    ChatColor.AQUA + "Q" + (i + 1) + ": " + (bound == null ? "비어 있음" : content.item(bound).name()),
                    List.of(ChatColor.WHITE + "인벤토리 보유 " + amount,
                            ChatColor.GRAY + "좌클릭: 보유 소모품 순환", ChatColor.GRAY + "우클릭: 바인딩 해제")));
        }
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        player.openInventory(inventory);
    }

    public boolean canGrantEquipment(Player player) {
        for (int slot = 1; slot <= 35; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) return true;
        }
        return false;
    }

    public void grantEquipment(Player player, String rawWeaponId) {
        String weaponId = rawWeaponId.toUpperCase(java.util.Locale.ROOT);
        content.weapon(weaponId);
        RunSnapshot.EquipmentInstanceState instance = newEquipmentInstance(weaponId);
        if (!storeInventory(player, weaponItem(instance))) throw new IllegalStateException("장비를 받을 인벤토리 공간이 없습니다.");
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.ownedEquipment.add(weaponId);
            equipmentInstances(state).put(instance.instanceId, instance);
        });
        codex.discover(player, weaponId, "CRAFT");
        player.sendMessage(ChatColor.GREEN + content.weapon(weaponId).name() + " 제작 완료. 인벤토리에서 보관하거나 장비 GUI로 장착하세요.");
    }

    public void setEquipmentOwned(Player player, String rawWeaponId, boolean owned) {
        String weaponId = rawWeaponId.toUpperCase(java.util.Locale.ROOT);
        content.weapon(weaponId);
        if (owned) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
            boolean present = weaponId.equals(state.mainWeaponId) || weaponId.equals(state.offhandId)
                    || findInventoryWeapon(player, weaponId) >= 0;
            if (!present) grantEquipment(player, weaponId);
            else runs.mutate(run -> run.players.get(player.getUniqueId().toString()).ownedEquipment.add(weaponId));
            return;
        }
        removeInventoryWeapons(player, weaponId);
        RunSnapshot.PlayerState before = runs.playerState(player.getUniqueId()).orElseThrow();
        if (weaponId.equals(before.mainWeaponId)) player.getInventory().setItem(0, null);
        if (weaponId.equals(before.offhandId)) player.getInventory().setItemInOffHand(null);
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.ownedEquipment.remove(weaponId);
            equipmentInstances(state).entrySet().removeIf(entry -> weaponId.equals(entry.getValue().templateId));
            if (weaponId.equals(state.mainWeaponId)) { state.mainWeaponId = null; state.mainWeaponInstanceId = null; }
            if (weaponId.equals(state.offhandId)) { state.offhandId = null; state.offhandInstanceId = null; }
        });
        syncAuthoritativeEquipment(player);
    }

    public void equipForTest(Player player, String rawWeaponId) {
        if (rawWeaponId == null || "UNARMED".equalsIgnoreCase(rawWeaponId)) {
            unequip(player, false);
            unequip(player, true);
            return;
        }
        String weaponId = rawWeaponId.toUpperCase(java.util.Locale.ROOT);
        int slot = findInventoryWeapon(player, weaponId);
        if (slot < 0) {
            grantEquipment(player, weaponId);
            slot = findInventoryWeapon(player, weaponId);
        }
        equipFromInventory(player, slot, false);
    }

    public void clearTestLoadout(Player player) {
        player.getInventory().setItem(0, null);
        player.getInventory().setItemInOffHand(null);
        for (int slot = 1; slot <= 35; slot++) if (weaponId(player.getInventory().getItem(slot)) != null) {
            player.getInventory().setItem(slot, null);
        }
        for (PrototypeContent.ItemDefinition item : content.items()) if ("QUICK_ITEM".equals(item.category())) {
            codex.takeItem(player, item.id(), codex.countItem(player, item.id()));
        }
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.mainWeaponId = null;
            state.mainWeaponInstanceId = null;
            state.offhandId = null;
            state.offhandInstanceId = null;
            state.ownedEquipment.clear();
            equipmentInstances(state).clear();
            state.quickItems.clear();
            state.quickBindings.clear();
        });
        syncAuthoritativeEquipment(player);
    }

    public void grantQuickItem(Player player, String id, int amount) {
        codex.grantItem(player, id, amount);
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.quickItems.put(id, codex.countItem(player, id));
            if (!state.quickBindings.containsValue(id)) for (int slot = 1; slot <= 4; slot++) {
                if (!state.quickBindings.containsKey(slot)) {
                    state.quickBindings.put(slot, id);
                    break;
                }
            }
        });
    }

    public String quickBinding(Player player, int slot) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        return state == null ? null : state.quickBindings.get(slot);
    }

    public void setQuickItem(Player player, String id, int amount) {
        if (amount < 0 || amount > 1_000_000) throw new IllegalArgumentException("Quick item amount must be 0 to 1000000");
        int current = codex.countItem(player, id);
        if (current < amount) codex.grantItem(player, id, amount - current);
        else if (current > amount) codex.takeItem(player, id, current - amount);
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.quickItems.put(id, codex.countItem(player, id));
            if (amount == 0) state.quickBindings.entrySet().removeIf(entry -> id.equals(entry.getValue()));
        });
    }

    public boolean consumeQuickItem(Player player, String id) {
        if (!codex.takeItem(player, id, 1)) return false;
        runs.mutate(run -> run.players.get(player.getUniqueId().toString()).quickItems.put(id, codex.countItem(player, id)));
        return true;
    }

    public String resolveWeaponId(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        return state == null || state.mainWeaponId == null || state.mainWeaponId.isBlank() ? "UNARMED" : state.mainWeaponId;
    }

    public boolean isMainWeaponUsable(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.mainWeaponId == null) return true;
        RunSnapshot.EquipmentInstanceState instance = equipmentInstances(state).get(state.mainWeaponInstanceId);
        return instance == null || !"BROKEN".equals(instance.condition);
    }

    public boolean consumeMainWeaponDurability(Player player, int amount, String reason) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.mainWeaponId == null || state.mainWeaponInstanceId == null) return true;
        return consumeDurability(player, state.mainWeaponInstanceId, amount, reason);
    }

    public void syncAuthoritativeEquipment(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return;
        ensureEquippedInstance(player, false, state.mainWeaponId, state.mainWeaponInstanceId);
        state = runs.playerState(player.getUniqueId()).orElseThrow();
        ensureEquippedInstance(player, true, state.offhandId, state.offhandInstanceId);
        state = runs.playerState(player.getUniqueId()).orElseThrow();
        repairSlot(player, false, state.mainWeaponId, state.mainWeaponInstanceId);
        repairSlot(player, true, state.offhandId, state.offhandInstanceId);
    }

    public void tick() {
        if (++tickCounter % 5 != 0) return;
        for (Player player : runs.onlineMembers()) syncAuthoritativeEquipment(player);
    }

    public ItemStack weaponItem(String rawWeaponId) {
        String weaponId = rawWeaponId.toUpperCase(java.util.Locale.ROOT);
        return weaponItem(newEquipmentInstance(weaponId));
    }

    private ItemStack weaponItem(RunSnapshot.EquipmentInstanceState instance) {
        PrototypeContent.WeaponDefinition weapon = content.weapon(instance.templateId);
        Material material = Material.matchMaterial(weapon.material());
        if (material == null || material.isAir()) return null;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "[WS] " + weapon.name());
        meta.setLore(List.of(ChatColor.GRAY + "등록 장비 · 장비 GUI에서 장착",
                ChatColor.WHITE + "간격 " + weapon.intervalTicks() + "틱 / 사거리 " + weapon.range(),
                ("BROKEN".equals(instance.condition) ? ChatColor.RED + "BROKEN · 수리가 필요합니다."
                        : ChatColor.GREEN + "내구 " + instance.currentDurability + "/" + instance.maxDurability),
                ChatColor.DARK_GRAY + "ID: " + weapon.id()));
        meta.setUnbreakable(false);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(weaponIdKey, PersistentDataType.STRING, weapon.id());
        meta.getPersistentDataContainer().set(equipmentInstanceIdKey, PersistentDataType.STRING, instance.instanceId);
        if (meta instanceof Damageable damageable && material.getMaxDurability() > 0) {
            damageable.setDamage(EquipmentDurabilityPolicy.mirrorDamage(instance.currentDurability,
                    instance.maxDurability, material.getMaxDurability()));
        }
        item.setItemMeta(meta);
        return codex.tagRegisteredItem(item, weapon.id());
    }

    public String weaponId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(weaponIdKey, PersistentDataType.STRING);
    }

    public String equipmentInstanceId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(equipmentInstanceIdKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof EquipmentHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner)) return;
            int raw = event.getRawSlot();
            if (raw == 49) { player.closeInventory(); return; }
            if (raw == GUI_MAIN_WEAPON) { if (event.isShiftClick()) fieldRepair(player, false); else unequip(player, false); open(player); return; }
            if (raw == GUI_OFFHAND) { if (event.isShiftClick()) fieldRepair(player, true); else unequip(player, true); open(player); return; }
            Integer inventorySlot = holder.inventorySlotByGui.get(raw);
            if (inventorySlot != null) {
                equipFromInventory(player, inventorySlot, event.isRightClick());
                open(player);
                return;
            }
            for (int i = 0; i < GUI_QUICK.length; i++) if (raw == GUI_QUICK[i]) {
                cycleQuickBinding(player, i + 1, event.isRightClick());
                open(player);
                return;
            }
            return;
        }
        if (event.getWhoClicked() instanceof Player player && runs.isMember(player)
                && event.getClickedInventory() instanceof PlayerInventory && (event.getSlot() == 0 || event.getSlot() == 40)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> syncAuthoritativeEquipment(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EquipmentHolder) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player && runs.isMember(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> syncAuthoritativeEquipment(player));
        }
    }

    @EventHandler public void onEquipmentClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof EquipmentHolder && event.getPlayer() instanceof Player player) {
            syncAuthoritativeEquipment(player);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrop(PlayerDropItemEvent event) {
        if (runs.isMember(event.getPlayer()) && weaponId(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> syncAuthoritativeEquipment(event.getPlayer()));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!runs.isRunningMember(event.getPlayer()) || weaponId(event.getItem()) == null) return;
        event.setCancelled(true);
        String instanceId = equipmentInstanceId(event.getItem());
        if (instanceId == null) {
            syncAuthoritativeEquipment(event.getPlayer());
            instanceId = equipmentInstanceId(event.getPlayer().getInventory().getItemInMainHand());
        }
        if (instanceId != null) {
            consumeDurability(event.getPlayer(), instanceId, Math.max(1, event.getDamage()), "VANILLA_ITEM_DAMAGE");
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (runs.isMember(event.getPlayer())) runs.restorePlayer(event.getPlayer());
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (runs.isMember(event.getPlayer())) runs.savePlayer(event.getPlayer());
        ActionBarService.clear(event.getPlayer());
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        if (runs.isMember(event.getPlayer())) Bukkit.getScheduler().runTask(plugin, () -> syncAuthoritativeEquipment(event.getPlayer()));
    }

    private void equipFromInventory(Player player, int inventorySlot, boolean offhand) {
        ItemStack candidate = player.getInventory().getItem(inventorySlot);
        String weaponId = weaponId(candidate);
        if (inventorySlot == 0 || weaponId == null) {
            player.sendMessage(ChatColor.RED + "해당 인벤토리 칸에는 장착 가능한 장비가 없습니다.");
            return;
        }
        String instanceId = equipmentInstanceId(candidate);
        if (instanceId == null) {
            RunSnapshot.EquipmentInstanceState migrated = newEquipmentInstance(weaponId);
            instanceId = migrated.instanceId;
            String finalInstanceId = instanceId;
            runs.mutate(run -> equipmentInstances(run.players.get(player.getUniqueId().toString())).put(finalInstanceId, migrated));
        }
        String equippedInstanceId = instanceId;
        player.getInventory().setItem(inventorySlot, null);
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.ownedEquipment.add(weaponId);
            if (offhand) { state.offhandId = weaponId; state.offhandInstanceId = equippedInstanceId; }
            else { state.mainWeaponId = weaponId; state.mainWeaponInstanceId = equippedInstanceId; }
        });
        syncAuthoritativeEquipment(player);
        player.sendMessage(ChatColor.GREEN + (offhand ? "보조무기" : "주무기") + " 장착: " + content.weapon(weaponId).name());
        telemetry.event(runs.current().orElseThrow().runId, "EQUIPMENT_COMMITTED",
                "{\"slot\":\"" + (offhand ? "-106" : "0") + "\",\"weaponId\":\"" + weaponId + "\"}");
    }

    private void unequip(Player player, boolean offhand) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        String equipped = offhand ? state.offhandId : state.mainWeaponId;
        if (equipped == null) return;
        if (!canGrantEquipment(player)) {
            player.sendMessage(ChatColor.RED + "장비를 해제할 인벤토리 공간이 없습니다.");
            return;
        }
        runs.mutate(run -> {
            RunSnapshot.PlayerState value = run.players.get(player.getUniqueId().toString());
            if (offhand) { value.offhandId = null; value.offhandInstanceId = null; }
            else { value.mainWeaponId = null; value.mainWeaponInstanceId = null; }
        });
        syncAuthoritativeEquipment(player);
        player.sendMessage(ChatColor.YELLOW + content.weapon(equipped).name() + " 장착 해제");
    }

    private void fieldRepair(Player player, boolean offhand) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        String instanceId = offhand ? state.offhandInstanceId : state.mainWeaponInstanceId;
        RunSnapshot.EquipmentInstanceState instance = equipmentInstances(state).get(instanceId);
        if (instance == null || instance.currentDurability >= instance.maxDurability) {
            player.sendMessage(ChatColor.YELLOW + "수리가 필요한 장비가 아닙니다.");
            return;
        }
        if (!codex.takeItem(player, "WSI-CONS-REPAIR_KIT", 1)) {
            player.sendMessage(ChatColor.RED + "야전 수리 키트가 필요합니다.");
            return;
        }
        runs.mutate(run -> {
            RunSnapshot.EquipmentInstanceState target = equipmentInstances(
                    run.players.get(player.getUniqueId().toString())).get(instanceId);
            if (target == null) return;
            int recovery = Math.max(1, (int) Math.ceil(target.maxDurability * 0.40));
            target.currentDurability = "BROKEN".equals(target.condition)
                    ? recovery : Math.min(target.maxDurability, target.currentDurability + recovery);
            target.condition = "ACTIVE";
        });
        syncAuthoritativeEquipment(player);
        telemetry.event(runs.current().orElseThrow().runId, "EQUIPMENT_REPAIRED",
                "{\"instanceId\":\"" + instanceId + "\",\"method\":\"FIELD_KIT\"}");
        player.sendMessage(ChatColor.GREEN + "같은 장비 인스턴스를 야전 수리했습니다.");
    }

    private void repairSlot(Player player, boolean offhand, String expectedWeaponId, String expectedInstanceId) {
        PlayerInventory inventory = player.getInventory();
        ItemStack actual = offhand ? inventory.getItemInOffHand() : inventory.getItem(0);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        RunSnapshot.EquipmentInstanceState instance = expectedInstanceId == null
                ? null : equipmentInstances(state).get(expectedInstanceId);
        ItemStack expected = expectedWeaponId == null || instance == null ? null : weaponItem(instance);
        if (sameAuthoritativeItem(actual, expected)) {
            if (actual != null && expected != null && !actual.isSimilar(expected)) {
                if (offhand) inventory.setItemInOffHand(expected);
                else inventory.setItem(0, expected);
            }
            return;
        }
        if (actual != null && !actual.getType().isAir() && !storeInventory(player, actual)) {
            player.sendMessage(ChatColor.RED + "장착 슬롯을 복구할 인벤토리 공간이 없습니다.");
            return;
        }
        if (offhand) inventory.setItemInOffHand(expected);
        else inventory.setItem(0, expected);
        auditSlotRepair(player, offhand ? "-106" : "0");
    }

    private void cycleQuickBinding(Player player, int quickSlot, boolean clear) {
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            if (clear) {
                state.quickBindings.remove(quickSlot);
                return;
            }
            List<String> available = content.items().stream().filter(item -> "QUICK_ITEM".equals(item.category())
                    && codex.countItem(player, item.id()) > 0).map(PrototypeContent.ItemDefinition::id).toList();
            if (available.isEmpty()) state.quickBindings.remove(quickSlot);
            else {
                int current = available.indexOf(state.quickBindings.get(quickSlot));
                state.quickBindings.put(quickSlot, available.get((current + 1) % available.size()));
            }
        });
    }

    private ItemStack equippedIcon(Player player, String weaponId, String instanceId, String slotName, String instruction) {
        RunSnapshot.EquipmentInstanceState instance = runs.current().stream()
                .flatMap(run -> run.players.values().stream())
                .map(state -> equipmentInstances(state).get(instanceId))
                .filter(Objects::nonNull).findFirst().orElseGet(() -> newEquipmentInstance(weaponId));
        ItemStack item = weaponItem(instance);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>(Objects.requireNonNull(meta.getLore()));
        lore.add(ChatColor.GREEN + slotName + " 장착 중");
        lore.add(ChatColor.YELLOW + instruction + " / Shift+클릭: 야전 수리");
        lore.add(ChatColor.WHITE + "수리 키트 " + codex.countItem(player, "WSI-CONS-REPAIR_KIT"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private int findInventoryWeapon(Player player, String weaponId) {
        for (int slot = 1; slot <= 35; slot++) if (weaponId.equals(weaponId(player.getInventory().getItem(slot)))) return slot;
        return -1;
    }
    private void removeInventoryWeapons(Player player, String weaponId) {
        for (int slot = 1; slot <= 35; slot++) if (weaponId.equals(weaponId(player.getInventory().getItem(slot)))) {
            player.getInventory().setItem(slot, null);
        }
    }

    private boolean storeInventory(Player player, ItemStack offered) {
        if (offered == null || offered.getType().isAir()) return true;
        ItemStack remaining = offered.clone();
        for (int slot = 1; slot <= 35 && remaining.getAmount() > 0; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(remaining)) continue;
            int moved = Math.min(existing.getMaxStackSize() - existing.getAmount(), remaining.getAmount());
            if (moved <= 0) continue;
            existing.setAmount(existing.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
        }
        for (int slot = 1; slot <= 35 && remaining.getAmount() > 0; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing != null && !existing.getType().isAir()) continue;
            ItemStack placed = remaining.clone();
            int moved = Math.min(placed.getMaxStackSize(), remaining.getAmount());
            placed.setAmount(moved);
            player.getInventory().setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - moved);
        }
        return remaining.getAmount() == 0;
    }

    private boolean sameAuthoritativeItem(ItemStack actual, ItemStack expected) {
        if (actual == null || actual.getType().isAir()) return expected == null || expected.getType().isAir();
        if (expected == null || expected.getType().isAir()) return false;
        String actualWeapon = weaponId(actual);
        String expectedWeapon = weaponId(expected);
        if (actualWeapon != null || expectedWeapon != null) {
            return Objects.equals(actualWeapon, expectedWeapon)
                    && Objects.equals(equipmentInstanceId(actual), equipmentInstanceId(expected));
        }
        return actual.isSimilar(expected);
    }

    private void ensureEquippedInstance(Player player, boolean offhand, String weaponId, String instanceId) {
        if (weaponId == null) return;
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        if (instanceId != null && equipmentInstances(state).containsKey(instanceId)) return;
        RunSnapshot.EquipmentInstanceState created = newEquipmentInstance(weaponId);
        runs.mutate(run -> {
            RunSnapshot.PlayerState value = run.players.get(player.getUniqueId().toString());
            equipmentInstances(value).put(created.instanceId, created);
            if (offhand) value.offhandInstanceId = created.instanceId;
            else value.mainWeaponInstanceId = created.instanceId;
        });
    }

    private boolean consumeDurability(Player player, String instanceId, int amount, String reason) {
        if (amount < 0) throw new IllegalArgumentException("Durability amount cannot be negative");
        boolean[] changed = {false};
        boolean[] broke = {false};
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            RunSnapshot.EquipmentInstanceState instance = equipmentInstances(state).get(instanceId);
            if (instance == null) return;
            boolean wasBroken = "BROKEN".equals(instance.condition);
            EquipmentDurabilityPolicy.SpendResult result = EquipmentDurabilityPolicy.spend(
                    instance.currentDurability, instance.maxDurability, amount);
            instance.currentDurability = result.current();
            instance.condition = result.condition().name();
            changed[0] = result.changed();
            broke[0] = !wasBroken && result.condition() == EquipmentDurabilityPolicy.Condition.BROKEN;
        });
        syncAuthoritativeEquipment(player);
        if (broke[0]) {
            player.sendMessage(ChatColor.RED + "장비가 파손되었습니다. 장비는 보존되며 수리 전까지 사용할 수 없습니다.");
            telemetry.event(runs.current().orElseThrow().runId, "EQUIPMENT_BROKEN",
                    "{\"instanceId\":\"" + instanceId + "\",\"reason\":\"" + reason + "\"}");
        }
        return changed[0];
    }

    private RunSnapshot.EquipmentInstanceState newEquipmentInstance(String rawWeaponId) {
        String weaponId = rawWeaponId.toUpperCase(java.util.Locale.ROOT);
        PrototypeContent.WeaponDefinition weapon = content.weapon(weaponId);
        Material material = Material.matchMaterial(weapon.material());
        int maximum = material == null || material.getMaxDurability() < 1 ? 100 : material.getMaxDurability();
        RunSnapshot.EquipmentInstanceState state = new RunSnapshot.EquipmentInstanceState();
        state.instanceId = UUID.randomUUID().toString();
        state.templateId = weaponId;
        state.currentDurability = maximum;
        state.maxDurability = maximum;
        state.condition = "ACTIVE";
        return state;
    }

    private static java.util.Map<String, RunSnapshot.EquipmentInstanceState> equipmentInstances(RunSnapshot.PlayerState state) {
        if (state.equipmentInstances == null) state.equipmentInstances = new java.util.LinkedHashMap<>();
        return state.equipmentInstances;
    }

    private void auditSlotRepair(Player player, String slot) {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot != null) telemetry.audit(snapshot.runId, player.getUniqueId().toString(), "equipment.slot_repair", "slot=" + slot);
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null || material.isAir() ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static final class EquipmentHolder implements InventoryHolder {
        private final UUID owner;
        private final java.util.Map<Integer, Integer> inventorySlotByGui = new java.util.HashMap<>();
        private EquipmentHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
}
