package com.lsc.corp.wsplugin.player;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
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
import org.bukkit.EntityEffect;
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
import org.bukkit.event.player.PlayerItemBreakEvent;
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
import org.bukkit.Sound;

public final class EquipmentService implements Listener {
    private static final int GUI_MAIN_WEAPON = 4;
    private static final int GUI_OFFHAND = 6;
    private static final java.util.Map<String, Integer> GUI_AUXILIARY = java.util.Map.of(
            "ARMOR_HEAD", 0, "ARMOR_CHEST", 1, "ARMOR_LEGS", 2, "ARMOR_FEET", 3,
            "ACCESSORY", 5, "CHARM", 7);
    private static final int GUI_CANDIDATE_START = 9;
    private static final int[] GUI_QUICK = {45, 46, 47, 48};
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent content;
    private final ProductionContentCatalog production;
    private final TelemetryService telemetry;
    private final ItemCodexService codex;
    private final NamespacedKey weaponIdKey;
    private final NamespacedKey equipmentInstanceIdKey;
    private final java.util.Map<UUID, String> statSignatures = new java.util.HashMap<>();
    private java.util.function.Consumer<Player> statRefresher = ignored -> { };
    private int tickCounter;

    public EquipmentService(JavaPlugin plugin, RunService runs, PrototypeContent content,
                            ProductionContentCatalog production,
                            TelemetryService telemetry, ItemCodexService codex) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.production = production;
        this.telemetry = telemetry;
        this.codex = codex;
        this.weaponIdKey = new NamespacedKey(plugin, "weapon_id");
        this.equipmentInstanceIdKey = new NamespacedKey(plugin, "equipment_instance_id");
    }

    public void setStatRefresher(java.util.function.Consumer<Player> statRefresher) {
        this.statRefresher = Objects.requireNonNull(statRefresher);
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
        for (java.util.Map.Entry<String, Integer> entry : GUI_AUXILIARY.entrySet()) {
            String slot = entry.getKey();
            String templateId = equippedTemplates(state).get(slot);
            String instanceId = equippedInstancesBySlot(state).get(slot);
            inventory.setItem(entry.getValue(), templateId == null
                    ? named(slotIcon(slot), ChatColor.GRAY + slotLabel(slot) + ": 비어 있음",
                    List.of(ChatColor.WHITE + "인벤토리의 알맞은 장비를 클릭해 장착"))
                    : equippedIcon(player, templateId, instanceId, slotLabel(slot), "클릭: 해제"));
        }

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
                String targetSlot = equipmentSlot(weaponId);
                lore.add(ChatColor.YELLOW + ("MAIN_WEAPON".equals(targetSlot) ? "좌클릭: 주무기 장착"
                        : "OFF_WEAPON".equals(targetSlot) ? "우클릭: 보조무기 장착"
                        : "클릭: " + slotLabel(targetSlot) + " 장착"));
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
            ProductionContentCatalog.ItemEntry quick = bound == null ? null : production.nonEquipmentItemsById().get(bound);
            PrototypeContent.ItemDefinition legacy = bound == null || quick != null ? null
                    : content.items().stream().filter(item -> item.id().equals(bound)).findFirst().orElse(null);
            Material material = bound == null ? Material.GRAY_DYE : Material.matchMaterial(
                    quick != null ? quick.displayMaterial() : legacy == null ? "PAPER" : legacy.material());
            String name = quick != null ? quick.name() : legacy == null ? bound : legacy.name();
            inventory.setItem(GUI_QUICK[i], named(material == null ? Material.PAPER : material,
                    ChatColor.AQUA + "Q" + (i + 1) + ": " + (bound == null ? "비어 있음" : name),
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

    public void reconcilePendingRewards(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return;
        if (state.pendingBlueprintUnlocks != null && !state.pendingBlueprintUnlocks.isEmpty()) {
            for (String templateId : new java.util.LinkedHashSet<>(state.pendingBlueprintUnlocks)) {
                codex.discover(player, templateId, "LOOT_BLUEPRINT");
                runs.mutate(run -> run.players.get(player.getUniqueId().toString())
                        .pendingBlueprintUnlocks.remove(templateId));
            }
        }
        while (canGrantEquipment(player)) {
            RunSnapshot.PlayerState current = runs.playerState(player.getUniqueId()).orElse(null);
            if (current == null || current.pendingEquipmentRewards == null
                    || current.pendingEquipmentRewards.isEmpty()) break;
            String templateId = current.pendingEquipmentRewards.getFirst();
            grantEquipment(player, templateId);
            runs.mutate(run -> run.players.get(player.getUniqueId().toString())
                    .pendingEquipmentRewards.remove(templateId));
            player.sendMessage(ChatColor.GOLD + "전리품 장비가 보상함에서 지급되었습니다.");
        }
        RunSnapshot.PlayerState remaining = runs.playerState(player.getUniqueId()).orElse(null);
        if (remaining != null && remaining.pendingEquipmentRewards != null
                && !remaining.pendingEquipmentRewards.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "인벤토리 공간이 없어 장비 전리품 "
                    + remaining.pendingEquipmentRewards.size() + "개를 보상함에 보관 중입니다.");
        }
    }

    public void grantEquipment(Player player, String rawWeaponId) {
        String weaponId = rawWeaponId.toUpperCase(java.util.Locale.ROOT);
        requireEquipmentTemplate(weaponId);
        RunSnapshot.EquipmentInstanceState instance = newEquipmentInstance(weaponId);
        if (!storeInventory(player, weaponItem(instance))) throw new IllegalStateException("장비를 받을 인벤토리 공간이 없습니다.");
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.ownedEquipment.add(weaponId);
            equipmentInstances(state).put(instance.instanceId, instance);
        });
        codex.discover(player, weaponId, "CRAFT");
        player.sendMessage(ChatColor.GREEN + templateName(weaponId) + " 제작 완료. 인벤토리에서 보관하거나 장비 GUI로 장착하세요.");
    }

    public void forgeEquipment(Player player, String rawOutputId, ItemStack baseItem) {
        String outputId = rawOutputId.toUpperCase(java.util.Locale.ROOT);
        requireEquipmentTemplate(outputId);
        String instanceId = equipmentInstanceId(baseItem);
        if (instanceId == null) {
            grantEquipment(player, outputId);
            return;
        }
        RunSnapshot.PlayerState before = runs.playerState(player.getUniqueId()).orElseThrow();
        RunSnapshot.EquipmentInstanceState source = equipmentInstances(before).get(instanceId);
        if (source == null) {
            grantEquipment(player, outputId);
            return;
        }
        Material outputMaterial = templateMaterial(outputId);
        int newMaximum = outputMaterial == null || outputMaterial.getMaxDurability() < 1 ? 100 : outputMaterial.getMaxDurability();
        double durabilityRatio = source.maxDurability <= 0 ? 1.0
                : Math.max(0.0, Math.min(1.0, source.currentDurability / (double) source.maxDurability));
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            RunSnapshot.EquipmentInstanceState target = equipmentInstances(state).get(instanceId);
            if (target == null) return;
            target.templateId = outputId;
            target.maxDurability = newMaximum;
            target.currentDurability = Math.max(1, (int) Math.round(newMaximum * durabilityRatio));
            target.condition = "ACTIVE";
            state.ownedEquipment.add(outputId);
        });
        RunSnapshot.EquipmentInstanceState forged = equipmentInstances(
                runs.playerState(player.getUniqueId()).orElseThrow()).get(instanceId);
        if (forged == null || !storeInventory(player, weaponItem(forged))) {
            throw new IllegalStateException("제작된 장비를 받을 인벤토리 공간이 없습니다.");
        }
        codex.discover(player, outputId, "FORGE");
        telemetry.event(runs.current().orElseThrow().runId, "EQUIPMENT_FORGED",
                "{\"instanceId\":\"" + instanceId + "\",\"outputId\":\"" + outputId + "\"}");
        player.sendMessage(ChatColor.GREEN + "장비 계보·내구 비율을 계승해 제작: " + templateName(outputId));
    }

    public void setEquipmentOwned(Player player, String rawWeaponId, boolean owned) {
        String weaponId = rawWeaponId.toUpperCase(java.util.Locale.ROOT);
        requireEquipmentTemplate(weaponId);
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
        String auxiliarySlot = equippedTemplates(before).entrySet().stream()
                .filter(entry -> weaponId.equals(entry.getValue())).map(java.util.Map.Entry::getKey).findFirst().orElse(null);
        if (auxiliarySlot != null) clearPhysicalSlot(player, auxiliarySlot);
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.ownedEquipment.remove(weaponId);
            equipmentInstances(state).entrySet().removeIf(entry -> weaponId.equals(entry.getValue().templateId));
            if (weaponId.equals(state.mainWeaponId)) { state.mainWeaponId = null; state.mainWeaponInstanceId = null; }
            if (weaponId.equals(state.offhandId)) { state.offhandId = null; state.offhandInstanceId = null; }
            equippedTemplates(state).entrySet().removeIf(entry -> weaponId.equals(entry.getValue()));
            equippedInstancesBySlot(state).entrySet().removeIf(entry -> !equippedTemplates(state).containsKey(entry.getKey()));
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
        equipFromInventory(player, slot, "OFF_WEAPON".equals(equipmentSlot(weaponId)));
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
        for (ProductionContentCatalog.ItemEntry item : production.nonEquipmentItemsById().values()) if (item.quickConsumable()) {
            codex.takeItem(player, item.id(), codex.countItem(player, item.id()));
        }
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.mainWeaponId = null;
            state.mainWeaponInstanceId = null;
            state.offhandId = null;
            state.offhandInstanceId = null;
            equippedTemplates(state).clear();
            equippedInstancesBySlot(state).clear();
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
        return codex.takeItem(player, id, 1);
    }

    public boolean consumeQuickItem(Player player, String id,
                                    java.util.function.Consumer<RunSnapshot.PlayerState> stateMutation) {
        return codex.takeQuickItemWithStateMutation(player, id, 1, stateMutation);
    }

    public boolean hasRegisteredItem(Player player, String id) {
        return codex.countItem(player, id) > 0;
    }

    public boolean canRepairEquipped(Player player) {
        return mostDamagedEquipped(player) != null;
    }

    public boolean repairMostDamagedWithConsumedKit(Player player) {
        String instanceId = mostDamagedEquipped(player);
        if (instanceId == null) return false;
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
                "{\"instanceId\":\"" + instanceId + "\",\"method\":\"QUICK_KIT\"}");
        return true;
    }

    public boolean repairMostDamagedFull(Player player) {
        String instanceId = mostDamagedEquipped(player);
        if (instanceId == null) return false;
        runs.mutate(run -> {
            RunSnapshot.EquipmentInstanceState target = equipmentInstances(
                    run.players.get(player.getUniqueId().toString())).get(instanceId);
            if (target == null) return;
            target.currentDurability = target.maxDurability;
            target.condition = "ACTIVE";
        });
        syncAuthoritativeEquipment(player);
        telemetry.event(runs.current().orElseThrow().runId, "EQUIPMENT_REPAIRED",
                "{\"instanceId\":\"" + instanceId + "\",\"method\":\"FAC-S02\"}");
        return true;
    }

    private String mostDamagedEquipped(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return null;
        java.util.Set<String> equipped = new java.util.LinkedHashSet<>();
        if (state.mainWeaponInstanceId != null) equipped.add(state.mainWeaponInstanceId);
        if (state.offhandInstanceId != null) equipped.add(state.offhandInstanceId);
        equipped.addAll(equippedInstancesBySlot(state).values());
        return equipped.stream().map(id -> equipmentInstances(state).get(id)).filter(java.util.Objects::nonNull)
                .filter(instance -> instance.currentDurability < instance.maxDurability)
                .min(java.util.Comparator.comparingDouble(instance -> instance.currentDurability
                        / (double) Math.max(1, instance.maxDurability)))
                .map(instance -> instance.instanceId).orElse(null);
    }

    public String resolveWeaponId(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        return state == null || state.mainWeaponId == null || state.mainWeaponId.isBlank()
                ? "UNARMED" : weaponClass(state.mainWeaponId);
    }

    public String equipmentTemplateId(ItemStack item) {
        return weaponId(item);
    }

    public ActiveEquipmentStats activeStats(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return new ActiveEquipmentStats(java.util.Map.of(), java.util.Map.of());
        java.util.Set<String> instanceIds = new java.util.LinkedHashSet<>();
        if (state.mainWeaponInstanceId != null) instanceIds.add(state.mainWeaponInstanceId);
        if (state.offhandInstanceId != null) instanceIds.add(state.offhandInstanceId);
        instanceIds.addAll(equippedInstancesBySlot(state).values());
        java.util.Map<String, Double> stats = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> sets = new java.util.LinkedHashMap<>();
        for (String instanceId : instanceIds) {
            RunSnapshot.EquipmentInstanceState instance = equipmentInstances(state).get(instanceId);
            if (instance == null || "BROKEN".equals(instance.condition)) continue;
            ProductionContentCatalog.EquipmentEntry profile = production.equipmentById().get(instance.templateId);
            if (profile == null || profile.utility()) continue;
            profile.stats().forEach((id, value) -> stats.merge(id, value, Double::sum));
            if (!profile.setId().isBlank()) sets.merge(profile.setId(), 1, Integer::sum);
        }
        return new ActiveEquipmentStats(java.util.Map.copyOf(stats), java.util.Map.copyOf(sets));
    }

    public double activeStat(Player player, String id) {
        return activeStats(player).value(id);
    }

    public boolean isMainWeaponUsable(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.mainWeaponId == null) return true;
        RunSnapshot.EquipmentInstanceState instance = equipmentInstances(state).get(state.mainWeaponInstanceId);
        return instance == null || !"BROKEN".equals(instance.condition);
    }

    public boolean isOffhandShieldUsable(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.offhandId == null || state.offhandId.isBlank()) return false;
        ProductionContentCatalog.EquipmentEntry profile = production.equipmentById().get(state.offhandId);
        if (profile == null || !"OFF_WEAPON".equals(profile.equipmentSlot())) return false;
        RunSnapshot.EquipmentInstanceState instance = equipmentInstances(state).get(state.offhandInstanceId);
        return instance == null || !"BROKEN".equals(instance.condition);
    }

    public boolean consumeOffhandDurability(Player player, int amount, String reason) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.offhandInstanceId == null) return false;
        return consumeDurability(player, state.offhandInstanceId, amount, reason);
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
        state = runs.playerState(player.getUniqueId()).orElseThrow();
        for (String slot : GUI_AUXILIARY.keySet()) {
            ensureAuxiliaryInstance(player, slot, equippedTemplates(state).get(slot), equippedInstancesBySlot(state).get(slot));
            state = runs.playerState(player.getUniqueId()).orElseThrow();
            repairAuxiliarySlot(player, slot, equippedTemplates(state).get(slot), equippedInstancesBySlot(state).get(slot));
        }
        refreshStatsIfChanged(player);
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
        String weaponClass = weaponClassOrNull(instance.templateId);
        PrototypeContent.WeaponDefinition weapon = weaponClass == null ? null : content.weapon(weaponClass);
        Material material = templateMaterial(instance.templateId);
        if (material == null || material.isAir()) return null;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "[WS] " + templateName(instance.templateId));
        String combatLine = weapon == null ? "장비 슬롯 " + equipmentSlot(instance.templateId)
                : "무기군 " + weapon.id() + " · 간격 " + weapon.intervalTicks() + "틱 / 사거리 " + weapon.range();
        ProductionContentCatalog.EquipmentEntry profile = production.equipmentById().get(instance.templateId);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "등록 장비 · 장비 GUI에서 장착");
        lore.add(ChatColor.WHITE + combatLine);
        if (profile != null) {
            lore.add(ChatColor.LIGHT_PURPLE + profile.rarity() + " · iLv " + profile.itemLevel()
                    + (profile.toolTier() >= 0 ? " · 채집 T" + profile.toolTier() : ""));
            if (!profile.stats().isEmpty()) lore.add(ChatColor.AQUA + "스탯 " + profile.stats().entrySet().stream()
                    .map(entry -> entry.getKey() + " +" + roundStat(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(", ")));
            String effect = profile.effectText().replace(" | ", " · ");
            lore.add(ChatColor.GRAY + (effect.length() > 180 ? effect.substring(0, 177) + "..." : effect));
            if (!profile.tags().isEmpty()) lore.add(ChatColor.DARK_AQUA + "태그 " + String.join(", ", profile.tags()));
        }
        lore.add("BROKEN".equals(instance.condition) ? ChatColor.RED + "BROKEN · 수리가 필요합니다."
                : ChatColor.GREEN + "내구 " + instance.currentDurability + "/" + instance.maxDurability);
        lore.add(ChatColor.DARK_GRAY + "ID: " + instance.templateId);
        meta.setLore(lore);
        meta.setUnbreakable(false);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(weaponIdKey, PersistentDataType.STRING, instance.templateId);
        meta.getPersistentDataContainer().set(equipmentInstanceIdKey, PersistentDataType.STRING, instance.instanceId);
        if (meta instanceof Damageable damageable && material.getMaxDurability() > 0) {
            damageable.setDamage(EquipmentDurabilityPolicy.mirrorDamage(instance.currentDurability,
                    instance.maxDurability, material.getMaxDurability()));
        }
        item.setItemMeta(meta);
        return codex.tagRegisteredItem(item, instance.templateId);
    }

    public String weaponId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(weaponIdKey, PersistentDataType.STRING);
    }

    public String equipmentInstanceId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(equipmentInstanceIdKey, PersistentDataType.STRING);
    }

    public ItemStack itemForInstance(RunSnapshot.EquipmentInstanceState instance) {
        if (instance == null) return null;
        return weaponItem(copyInstance(instance));
    }

    private static RunSnapshot.EquipmentInstanceState copyInstance(RunSnapshot.EquipmentInstanceState source) {
        RunSnapshot.EquipmentInstanceState copy = new RunSnapshot.EquipmentInstanceState();
        copy.instanceId = source.instanceId;
        copy.templateId = source.templateId;
        copy.currentDurability = source.currentDurability;
        copy.maxDurability = source.maxDurability;
        copy.condition = source.condition;
        copy.breakCount = source.breakCount;
        return copy;
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
            String auxiliarySlot = GUI_AUXILIARY.entrySet().stream().filter(entry -> entry.getValue() == raw)
                    .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
            if (auxiliarySlot != null) {
                if (event.isShiftClick()) fieldRepair(player, auxiliarySlot);
                else unequipAuxiliary(player, auxiliarySlot);
                open(player);
                return;
            }
            Integer inventorySlot = holder.inventorySlotByGui.get(raw);
            if (inventorySlot != null) {
                String templateId = weaponId(player.getInventory().getItem(inventorySlot));
                String targetSlot = templateId == null ? "" : equipmentSlot(templateId);
                if ("MAIN_WEAPON".equals(targetSlot) || "OFF_WEAPON".equals(targetSlot)) {
                    equipFromInventory(player, inventorySlot, "OFF_WEAPON".equals(targetSlot));
                } else {
                    equipAuxiliaryFromInventory(player, inventorySlot, targetSlot);
                }
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
                && event.getClickedInventory() instanceof PlayerInventory
                && (event.getSlot() == 0 || event.getSlot() == 40 || event.getSlot() >= 36 && event.getSlot() <= 39)) {
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
        EquipmentDurabilityPolicy.NativeDamageDecision decision =
                EquipmentDurabilityPolicy.interceptNativeDamage(event.getDamage());
        // Preserve the Bukkit event for observers, but never let native durability delete a managed ItemStack.
        event.setDamage(decision.nativeDamage());
        String instanceId = equipmentInstanceId(event.getItem());
        if (instanceId == null) {
            syncAuthoritativeEquipment(event.getPlayer());
            instanceId = equipmentInstanceId(event.getPlayer().getInventory().getItemInMainHand());
        }
        if (instanceId != null) {
            consumeDurability(event.getPlayer(), instanceId, decision.ledgerCost(), "VANILLA_ITEM_DAMAGE");
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (runs.isMember(event.getPlayer())) {
            runs.restorePlayer(event.getPlayer());
            Bukkit.getScheduler().runTask(plugin, () -> reconcilePendingRewards(event.getPlayer()));
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (runs.isMember(event.getPlayer())) runs.savePlayer(event.getPlayer());
        statSignatures.remove(event.getPlayer().getUniqueId());
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
        String requiredSlot = offhand ? "OFF_WEAPON" : "MAIN_WEAPON";
        if (!supportsSlot(weaponId, requiredSlot)) {
            player.sendMessage(ChatColor.RED + (offhand ? "보조무기" : "주무기") + " 슬롯에 장착할 수 없는 장비입니다.");
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
        player.sendMessage(ChatColor.GREEN + (offhand ? "보조무기" : "주무기") + " 장착: " + templateName(weaponId));
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
        player.sendMessage(ChatColor.YELLOW + templateName(equipped) + " 장착 해제");
    }

    private void equipAuxiliaryFromInventory(Player player, int inventorySlot, String slot) {
        if (!GUI_AUXILIARY.containsKey(slot)) {
            player.sendMessage(ChatColor.RED + "장착 슬롯이 없는 장비입니다.");
            return;
        }
        ItemStack candidate = player.getInventory().getItem(inventorySlot);
        String templateId = weaponId(candidate);
        if (templateId == null || !supportsSlot(templateId, slot)) {
            player.sendMessage(ChatColor.RED + slotLabel(slot) + " 슬롯에 장착할 수 없는 장비입니다.");
            return;
        }
        String instanceId = equipmentInstanceId(candidate);
        if (instanceId == null) {
            RunSnapshot.EquipmentInstanceState migrated = newEquipmentInstance(templateId);
            instanceId = migrated.instanceId;
            String committedId = instanceId;
            runs.mutate(run -> equipmentInstances(run.players.get(player.getUniqueId().toString())).put(committedId, migrated));
        }
        RunSnapshot.PlayerState before = runs.playerState(player.getUniqueId()).orElseThrow();
        String previousInstanceId = equippedInstancesBySlot(before).get(slot);
        RunSnapshot.EquipmentInstanceState previous = previousInstanceId == null ? null : equipmentInstances(before).get(previousInstanceId);
        player.getInventory().setItem(inventorySlot, previous == null ? null : weaponItem(previous));
        clearPhysicalSlot(player, slot);
        String committedInstanceId = instanceId;
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.ownedEquipment.add(templateId);
            equippedTemplates(state).put(slot, templateId);
            equippedInstancesBySlot(state).put(slot, committedInstanceId);
        });
        syncAuthoritativeEquipment(player);
        player.sendMessage(ChatColor.GREEN + slotLabel(slot) + " 장착: " + templateName(templateId));
        telemetry.event(runs.current().orElseThrow().runId, "EQUIPMENT_COMMITTED",
                "{\"slot\":\"" + slot + "\",\"equipmentId\":\"" + templateId + "\"}");
    }

    private void unequipAuxiliary(Player player, String slot) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        String templateId = equippedTemplates(state).get(slot);
        String instanceId = equippedInstancesBySlot(state).get(slot);
        if (templateId == null || instanceId == null) return;
        if (!canGrantEquipment(player)) {
            player.sendMessage(ChatColor.RED + "장비를 해제할 인벤토리 공간이 없습니다.");
            return;
        }
        RunSnapshot.EquipmentInstanceState instance = equipmentInstances(state).get(instanceId);
        ItemStack returned = physicalItem(player, slot);
        clearPhysicalSlot(player, slot);
        if (returned == null || returned.getType().isAir()) returned = instance == null ? null : weaponItem(instance);
        runs.mutate(run -> {
            RunSnapshot.PlayerState current = run.players.get(player.getUniqueId().toString());
            equippedTemplates(current).remove(slot);
            equippedInstancesBySlot(current).remove(slot);
        });
        if (returned != null && !storeInventory(player, returned)) throw new IllegalStateException("장비 해제 저장 실패");
        syncAuthoritativeEquipment(player);
        player.sendMessage(ChatColor.YELLOW + templateName(templateId) + " 장착 해제");
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

    private void fieldRepair(Player player, String slot) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        repairInstance(player, equippedInstancesBySlot(state).get(slot));
    }

    private void repairInstance(Player player, String instanceId) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
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

    private void repairAuxiliarySlot(Player player, String slot, String expectedTemplateId, String expectedInstanceId) {
        if (!isPhysicalSlot(slot)) return;
        ItemStack actual = physicalItem(player, slot);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        RunSnapshot.EquipmentInstanceState instance = expectedInstanceId == null
                ? null : equipmentInstances(state).get(expectedInstanceId);
        ItemStack expected = expectedTemplateId == null || instance == null ? null : weaponItem(instance);
        if (sameAuthoritativeItem(actual, expected)) {
            if (actual != null && expected != null && !actual.isSimilar(expected)) setPhysicalItem(player, slot, expected);
            return;
        }
        if (actual != null && !actual.getType().isAir() && !storeInventory(player, actual)) {
            player.sendMessage(ChatColor.RED + slotLabel(slot) + " 슬롯을 복구할 인벤토리 공간이 없습니다.");
            return;
        }
        setPhysicalItem(player, slot, expected);
        auditSlotRepair(player, slot);
    }

    private void cycleQuickBinding(Player player, int quickSlot, boolean clear) {
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            if (clear) {
                state.quickBindings.remove(quickSlot);
                return;
            }
            List<String> available = production.nonEquipmentItemsById().values().stream()
                    .filter(ProductionContentCatalog.ItemEntry::quickConsumable)
                    .filter(item -> codex.countItem(player, item.id()) > 0)
                    .sorted(java.util.Comparator.comparingInt(ProductionContentCatalog.ItemEntry::firstDay)
                            .thenComparing(ProductionContentCatalog.ItemEntry::id))
                    .map(ProductionContentCatalog.ItemEntry::id).toList();
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

    private void ensureAuxiliaryInstance(Player player, String slot, String templateId, String instanceId) {
        if (templateId == null) return;
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        if (instanceId != null && equipmentInstances(state).containsKey(instanceId)) return;
        RunSnapshot.EquipmentInstanceState created = newEquipmentInstance(templateId);
        runs.mutate(run -> {
            RunSnapshot.PlayerState current = run.players.get(player.getUniqueId().toString());
            equipmentInstances(current).put(created.instanceId, created);
            equippedInstancesBySlot(current).put(slot, created.instanceId);
        });
    }

    private boolean consumeDurability(Player player, String instanceId, int amount, String reason) {
        if (amount < 0) throw new IllegalArgumentException("Durability amount cannot be negative");
        RunSnapshot.PlayerState beforeState = runs.playerState(player.getUniqueId()).orElse(null);
        RunSnapshot.EquipmentInstanceState before = beforeState == null
                ? null : equipmentInstances(beforeState).get(instanceId);
        if (before == null || before.currentDurability == 0 || amount == 0) return false;
        int expectedCurrent = Math.max(0, before.currentDurability - amount);
        boolean expectedBreak = EquipmentDurabilityPolicy.isBreakTransition(
                equipmentCondition(before.condition), expectedCurrent == 0
                        ? EquipmentDurabilityPolicy.Condition.BROKEN : EquipmentDurabilityPolicy.Condition.ACTIVE);
        EquipmentDurabilityPolicy.BreakSlot breakSlot = EquipmentDurabilityPolicy.resolveBreakSlot(
                beforeState.mainWeaponInstanceId, beforeState.offhandInstanceId,
                equippedInstancesBySlot(beforeState), instanceId);
        ItemStack breakSnapshot = expectedBreak ? compatibilityBreakSnapshot(player, instanceId, before, breakSlot) : null;
        if (expectedBreak && breakSnapshot == null) {
            throw new IllegalStateException("파손 이벤트용 장비 스냅샷을 만들 수 없습니다: " + instanceId);
        }
        String payload = "{\"instanceId\":\"" + instanceId + "\",\"templateId\":\"" + before.templateId
                + "\",\"amount\":" + amount + ",\"current\":" + expectedCurrent
                + ",\"reason\":\"" + reason + "\"}";
        boolean[] changed = {false};
        boolean[] broke = {false};
        String[] templateId = {before.templateId};
        java.util.function.Consumer<RunSnapshot> mutation = run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            RunSnapshot.EquipmentInstanceState instance = equipmentInstances(state).get(instanceId);
            if (instance == null) return;
            templateId[0] = instance.templateId;
            boolean wasBroken = "BROKEN".equals(instance.condition);
            EquipmentDurabilityPolicy.SpendResult result = EquipmentDurabilityPolicy.spend(
                    instance.currentDurability, instance.maxDurability, amount);
            instance.currentDurability = result.current();
            instance.condition = result.condition().name();
            changed[0] = result.changed();
            broke[0] = !wasBroken && result.condition() == EquipmentDurabilityPolicy.Condition.BROKEN;
            if (broke[0]) instance.breakCount++;
        };
        if (expectedBreak) {
            String key = EquipmentDurabilityPolicy.breakCommitKey(instanceId, before.breakCount + 1);
            if (!runs.commitOnce(key, "EQUIPMENT_BROKEN", payload, mutation)) return false;
        } else {
            runs.mutate(mutation);
        }
        syncAuthoritativeEquipment(player);
        if (broke[0]) {
            player.sendMessage(ChatColor.RED + "장비가 파손되었습니다. 장비는 보존되며 수리 전까지 사용할 수 없습니다.");
            Bukkit.getPluginManager().callEvent(new EquipmentBrokenEvent(
                    player, templateId[0], instanceId, reason, breakSlot));
            Bukkit.getPluginManager().callEvent(new PlayerItemBreakEvent(player, breakSnapshot));
            playBreakFeedback(player, breakSlot);
            Bukkit.getScheduler().runTask(plugin, () -> syncAuthoritativeEquipment(player));
        }
        return changed[0];
    }

    private ItemStack compatibilityBreakSnapshot(Player player, String instanceId,
                                                  RunSnapshot.EquipmentInstanceState before,
                                                  EquipmentDurabilityPolicy.BreakSlot slot) {
        ItemStack authoritative = switch (slot) {
            case MAIN_HAND -> player.getInventory().getItem(0);
            case OFF_HAND -> player.getInventory().getItemInOffHand();
            case HEAD -> player.getInventory().getHelmet();
            case CHEST -> player.getInventory().getChestplate();
            case LEGS -> player.getInventory().getLeggings();
            case FEET -> player.getInventory().getBoots();
            case OTHER -> null;
        };
        if (authoritative != null && instanceId.equals(equipmentInstanceId(authoritative))) {
            return authoritative.clone();
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && instanceId.equals(equipmentInstanceId(item))) return item.clone();
        }
        ItemStack fallback = weaponItem(before);
        return fallback == null ? null : fallback.clone();
    }

    private static void playBreakFeedback(Player player, EquipmentDurabilityPolicy.BreakSlot slot) {
        EntityEffect effect = switch (slot) {
            case MAIN_HAND -> EntityEffect.BREAK_EQUIPMENT_MAIN_HAND;
            case OFF_HAND -> EntityEffect.BREAK_EQUIPMENT_OFF_HAND;
            case HEAD -> EntityEffect.BREAK_EQUIPMENT_HELMET;
            case CHEST -> EntityEffect.BREAK_EQUIPMENT_CHESTPLATE;
            case LEGS -> EntityEffect.BREAK_EQUIPMENT_LEGGINGS;
            case FEET -> EntityEffect.BREAK_EQUIPMENT_BOOTS;
            case OTHER -> null;
        };
        if (effect != null) player.playEffect(effect);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
    }

    private static EquipmentDurabilityPolicy.Condition equipmentCondition(String condition) {
        try {
            return EquipmentDurabilityPolicy.Condition.valueOf(condition);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return EquipmentDurabilityPolicy.Condition.ACTIVE;
        }
    }

    private RunSnapshot.EquipmentInstanceState newEquipmentInstance(String rawWeaponId) {
        String weaponId = rawWeaponId.toUpperCase(java.util.Locale.ROOT);
        requireEquipmentTemplate(weaponId);
        ProductionContentCatalog.EquipmentEntry profile = production.equipmentById().get(weaponId);
        Material material = templateMaterial(weaponId);
        int maximum = profile == null
                ? (material == null || material.getMaxDurability() < 1 ? 100 : material.getMaxDurability())
                : profile.maxDurability();
        RunSnapshot.EquipmentInstanceState state = new RunSnapshot.EquipmentInstanceState();
        state.instanceId = UUID.randomUUID().toString();
        state.templateId = weaponId;
        state.currentDurability = maximum;
        state.maxDurability = maximum;
        state.condition = "ACTIVE";
        return state;
    }

    private void requireEquipmentTemplate(String id) {
        if (content.weapons().stream().anyMatch(weapon -> weapon.id().equals(id) && !"UNARMED".equals(id))) return;
        ProductionContentCatalog.CatalogEntry entry = production.itemsById().get(id);
        if (entry == null || !entry.equipment()) {
            throw new IllegalArgumentException("Unknown or non-item equipment " + id);
        }
    }

    private String templateName(String id) {
        return content.weapons().stream().filter(weapon -> weapon.id().equals(id)).findFirst()
                .map(PrototypeContent.WeaponDefinition::name)
                .orElseGet(() -> production.item(id).name());
    }

    private String equipmentSlot(String id) {
        if (content.weapons().stream().anyMatch(weapon -> weapon.id().equals(id))) return "MAIN_WEAPON";
        ProductionContentCatalog.EquipmentEntry profile = production.equipmentById().get(id);
        if (profile != null) return profile.equipmentSlot();
        ProductionContentCatalog.CatalogEntry entry = production.item(id);
        if ("ARMOR".equals(entry.equipmentType())) {
            for (String slot : List.of("HEAD", "CHEST", "LEGS", "FEET")) {
                if (id.endsWith("-" + slot)) return "ARMOR_" + slot;
            }
        }
        if ("CHARM".equals(entry.equipmentType())) return "CHARM";
        if ("ACCESSORY".equals(entry.equipmentType()) || "UNARMED_SUPPORT".equals(entry.equipmentType())) return "ACCESSORY";
        return entry.equipmentSlot();
    }

    private boolean supportsSlot(String id, String slot) {
        if (content.weapons().stream().anyMatch(weapon -> weapon.id().equals(id))) return "MAIN_WEAPON".equals(slot) || "OFF_WEAPON".equals(slot);
        return slot.equals(equipmentSlot(id));
    }

    private String weaponClass(String id) {
        String result = weaponClassOrNull(id);
        return result == null ? "UNARMED" : result;
    }

    private String weaponClassOrNull(String id) {
        if (content.weapons().stream().anyMatch(weapon -> weapon.id().equals(id))) return id;
        ProductionContentCatalog.EquipmentEntry profile = production.equipmentById().get(id);
        if (profile != null && !profile.weaponClass().isBlank()) return profile.weaponClass();
        ProductionContentCatalog.CatalogEntry entry = production.itemsById().get(id);
        if (entry == null) return null;
        return switch (entry.equipmentType()) {
            case "SW" -> "SWORD";
            case "AX" -> "AXE";
            case "BO" -> "BOW";
            case "CB" -> "CROSSBOW";
            case "DG" -> "DAGGER";
            case "BL" -> "MACE";
            case "ST" -> "STAFF";
            case "PK" -> "PICKAXE";
            case "TR" -> "TRIDENT";
            default -> null;
        };
    }

    private Material templateMaterial(String id) {
        PrototypeContent.WeaponDefinition prototype = content.weapons().stream()
                .filter(weapon -> weapon.id().equals(id)).findFirst().orElse(null);
        if (prototype != null) return Material.matchMaterial(prototype.material());
        ProductionContentCatalog.EquipmentEntry profile = production.equipmentById().get(id);
        ProductionContentCatalog.CatalogEntry entry = production.item(id);
        if (profile != null) {
            Material configured = Material.matchMaterial(profile.displayMaterial());
            if (configured != null && !configured.isAir()) return configured;
        }
        Material direct = Material.matchMaterial(entry.displayMaterial());
        if (direct != null && !direct.isAir()) return direct;
        String weaponClass = weaponClassOrNull(id);
        if (weaponClass != null) return Material.matchMaterial(content.weapon(weaponClass).material());
        return switch (entry.equipmentSlot()) {
            case "OFF_WEAPON" -> Material.SHIELD;
            case "ARMOR_HEAD" -> Material.IRON_HELMET;
            case "ARMOR_CHEST" -> Material.IRON_CHESTPLATE;
            case "ARMOR_LEGS" -> Material.IRON_LEGGINGS;
            case "ARMOR_FEET" -> Material.IRON_BOOTS;
            case "ACCESSORY/CHARM" -> Material.AMETHYST_SHARD;
            default -> Material.IRON_PICKAXE;
        };
    }

    private void refreshStatsIfChanged(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return;
        int equipmentAp = Math.max(0, (int) Math.round(activeStats(player).value("AP")));
        if (state.equipmentMaxApBonus != equipmentAp) {
            runs.mutate(run -> run.players.get(player.getUniqueId().toString()).equipmentMaxApBonus = equipmentAp);
            state = runs.playerState(player.getUniqueId()).orElseThrow();
        }
        List<String> parts = new ArrayList<>();
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        if (state.mainWeaponInstanceId != null) ids.add(state.mainWeaponInstanceId);
        if (state.offhandInstanceId != null) ids.add(state.offhandInstanceId);
        ids.addAll(equippedInstancesBySlot(state).values());
        RunSnapshot.PlayerState currentState = state;
        ids.stream().sorted().forEach(id -> {
            RunSnapshot.EquipmentInstanceState instance = equipmentInstances(currentState).get(id);
            if (instance != null) parts.add(id + ":" + instance.templateId + ":" + instance.condition);
        });
        String signature = String.join("|", parts);
        if (!signature.equals(statSignatures.put(player.getUniqueId(), signature))) statRefresher.accept(player);
    }

    private static String roundStat(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value))
                : String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    public record ActiveEquipmentStats(java.util.Map<String, Double> values,
                                       java.util.Map<String, Integer> setPieces) {
        public double value(String id) {
            return values.getOrDefault(id, 0.0);
        }
    }

    private static java.util.Map<String, String> equippedTemplates(RunSnapshot.PlayerState state) {
        if (state.equippedTemplateBySlot == null) state.equippedTemplateBySlot = new java.util.LinkedHashMap<>();
        return state.equippedTemplateBySlot;
    }

    private static java.util.Map<String, String> equippedInstancesBySlot(RunSnapshot.PlayerState state) {
        if (state.equippedInstanceBySlot == null) state.equippedInstanceBySlot = new java.util.LinkedHashMap<>();
        return state.equippedInstanceBySlot;
    }

    private static String slotLabel(String slot) {
        return switch (slot) {
            case "MAIN_WEAPON" -> "주무기";
            case "OFF_WEAPON" -> "보조무기";
            case "ARMOR_HEAD" -> "머리 방어구";
            case "ARMOR_CHEST" -> "가슴 방어구";
            case "ARMOR_LEGS" -> "다리 방어구";
            case "ARMOR_FEET" -> "발 방어구";
            case "ACCESSORY" -> "장신구";
            case "CHARM" -> "부적";
            default -> slot;
        };
    }

    private static Material slotIcon(String slot) {
        return switch (slot) {
            case "ARMOR_HEAD" -> Material.IRON_HELMET;
            case "ARMOR_CHEST" -> Material.IRON_CHESTPLATE;
            case "ARMOR_LEGS" -> Material.IRON_LEGGINGS;
            case "ARMOR_FEET" -> Material.IRON_BOOTS;
            case "CHARM" -> Material.AMETHYST_SHARD;
            default -> Material.RECOVERY_COMPASS;
        };
    }

    private static boolean isPhysicalSlot(String slot) {
        return slot != null && slot.startsWith("ARMOR_");
    }

    private static ItemStack physicalItem(Player player, String slot) {
        return switch (slot) {
            case "ARMOR_HEAD" -> player.getInventory().getHelmet();
            case "ARMOR_CHEST" -> player.getInventory().getChestplate();
            case "ARMOR_LEGS" -> player.getInventory().getLeggings();
            case "ARMOR_FEET" -> player.getInventory().getBoots();
            default -> null;
        };
    }

    private static void setPhysicalItem(Player player, String slot, ItemStack item) {
        switch (slot) {
            case "ARMOR_HEAD" -> player.getInventory().setHelmet(item);
            case "ARMOR_CHEST" -> player.getInventory().setChestplate(item);
            case "ARMOR_LEGS" -> player.getInventory().setLeggings(item);
            case "ARMOR_FEET" -> player.getInventory().setBoots(item);
            default -> { }
        }
    }

    private static void clearPhysicalSlot(Player player, String slot) {
        setPhysicalItem(player, slot, null);
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
