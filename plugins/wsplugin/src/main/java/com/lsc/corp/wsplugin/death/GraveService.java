package com.lsc.corp.wsplugin.death;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.economy.ItemCodexService;
import com.lsc.corp.wsplugin.facility.FacilityStateAccess;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Persistent, idempotent DEATH-003 remains storage and delivery runtime. */
public final class GraveService implements Listener {
    private static final int PAGE_SIZE = 45;
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent prototype;
    private final ProductionContentCatalog production;
    private final ItemCodexService codex;
    private final EquipmentService equipment;
    private final TelemetryService telemetry;
    private final NamespacedKey remainsIdKey;
    private final NamespacedKey remainsRunKey;
    private final NamespacedKey deliveryIdKey;

    public GraveService(JavaPlugin plugin, RunService runs, PrototypeContent prototype,
                        ProductionContentCatalog production, ItemCodexService codex,
                        EquipmentService equipment, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.prototype = prototype;
        this.production = production;
        this.codex = codex;
        this.equipment = equipment;
        this.telemetry = telemetry;
        this.remainsIdKey = new NamespacedKey(plugin, "remains_id");
        this.remainsRunKey = new NamespacedKey(plugin, "remains_run_id");
        this.deliveryIdKey = new NamespacedKey(plugin, "remains_delivery_id");
    }

    public boolean prepareDeath(Player player, String cause) {
        RunSnapshot snapshot = runs.current().orElse(null);
        RunSnapshot.PlayerState owner = runs.playerState(player.getUniqueId()).orElse(null);
        if (snapshot == null || owner == null) return false;
        if (owner.remainsId != null && snapshot.remains.containsKey(owner.remainsId)) {
            clearCommittedOwnerInventory(player);
            restoreMarker(snapshot.remains.get(owner.remainsId));
            return true;
        }

        Location safe = safeRemainsLocation(player.getLocation(), snapshot, owner);
        long now = Instant.now().toEpochMilli();
        int deathNumber = owner.deathCount + 1;
        String remainsId = "remains-" + player.getUniqueId() + "-" + deathNumber;
        CapturedContents captured = capture(player, owner, remainsId);
        RunSnapshot.RemainsState remains = new RunSnapshot.RemainsState();
        remains.remainsId = remainsId;
        remains.ownerUuid = player.getUniqueId().toString();
        remains.ownerName = player.getName();
        remains.day = snapshot.day;
        remains.world = safe.getWorld().getName();
        remains.x = safe.getX();
        remains.y = safe.getY();
        remains.z = safe.getZ();
        remains.createdAtEpochMs = now;
        remains.lostConsumableCount = captured.lostConsumables;
        remains.contents.putAll(captured.items);
        remains.equipmentInstances.putAll(captured.equipmentInstances);

        boolean committed = runs.commitOnce("remains-create:" + remainsId, "REMAINS_CREATED",
                "{\"remainsId\":\"" + remainsId + "\",\"owner\":\"" + player.getUniqueId()
                        + "\",\"entries\":" + captured.items.size() + ",\"lostConsumables\":"
                        + captured.lostConsumables + "}", run -> {
                    run.remains.put(remainsId, remains);
                    RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                    state.deathCount = deathNumber;
                    state.remainsId = remainsId;
                    RunSnapshot.DeathRecordState death = new RunSnapshot.DeathRecordState();
                    death.day = run.day;
                    death.cause = cause;
                    death.world = safe.getWorld().getName();
                    death.x = safe.getX();
                    death.y = safe.getY();
                    death.z = safe.getZ();
                    death.occurredAtEpochMs = now;
                    state.death = death;
                    state.mainWeaponId = null;
                    state.mainWeaponInstanceId = null;
                    state.offhandId = null;
                    state.offhandInstanceId = null;
                    state.equippedTemplateBySlot.clear();
                    state.equippedInstanceBySlot.clear();
                    state.ownedEquipment.clear();
                    state.equipmentInstances.clear();
                    state.equipmentMaxApBonus = 0;
                    state.quickItems.clear();
                    state.pendingRegisteredItems.clear();
                });
        if (!committed) return runs.current().map(run -> run.remains.containsKey(remainsId)).orElse(false);
        clearCommittedOwnerInventory(player);
        restoreMarker(remains);
        telemetry.audit(snapshot.runId, player.getUniqueId().toString(), "death.remains_created",
                "remainsId=" + remainsId + ",entries=" + remains.contents.size());
        return true;
    }

    public void restore() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null) return;
        cleanupForeignMarkers(snapshot);
        snapshot.remains.values().forEach(this::restoreMarker);
        runs.onlineMembers().forEach(this::reconcile);
    }

    public void reconcile(Player player) {
        RunSnapshot snapshot = runs.current().orElse(null);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (snapshot == null || state == null) return;
        if (state.remainsId != null && snapshot.remains.containsKey(state.remainsId)
                && ("DEAD_PENDING".equals(state.lifeState) || "DEAD".equals(state.lifeState))) {
            clearCommittedOwnerInventory(player);
        }
        flushDeliveries(player);
        stripOrphanDeliveryTags(player);
    }

    public void cleanup() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(remainsIdKey, PersistentDataType.STRING)) entity.remove();
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> reconcile(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        String remainsId = event.getRightClicked().getPersistentDataContainer()
                .get(remainsIdKey, PersistentDataType.STRING);
        if (remainsId == null) return;
        event.setCancelled(true);
        attemptOpen(event.getPlayer(), remainsId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMarkerDamage(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(remainsIdKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMarkerManipulate(PlayerArmorStandManipulateEvent event) {
        String remainsId = event.getRightClicked().getPersistentDataContainer()
                .get(remainsIdKey, PersistentDataType.STRING);
        if (remainsId == null) return;
        event.setCancelled(true);
        attemptOpen(event.getPlayer(), remainsId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GraveHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.viewer)) return;
        int raw = event.getRawSlot();
        if (raw == 49) {
            player.closeInventory();
        } else if (raw == 45) {
            open(player, holder.remainsId, holder.page - 1);
        } else if (raw == 53) {
            open(player, holder.remainsId, holder.page + 1);
        } else if (holder.entryBySlot.containsKey(raw)) {
            claim(player, holder.remainsId, holder.entryBySlot.get(raw));
            open(player, holder.remainsId, holder.page);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GraveHolder) event.setCancelled(true);
    }

    private CapturedContents capture(Player player, RunSnapshot.PlayerState owner, String remainsId) {
        List<ItemStack> raw = new ArrayList<>();
        Set<String> physicalEquipment = new HashSet<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            raw.add(item.clone());
            String instanceId = equipment.equipmentInstanceId(item);
            if (instanceId != null) physicalEquipment.add(instanceId);
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            raw.add(cursor.clone());
            String instanceId = equipment.equipmentInstanceId(cursor);
            if (instanceId != null) physicalEquipment.add(instanceId);
        }
        if (owner.pendingRegisteredItems != null) {
            owner.pendingRegisteredItems.forEach((id, amount) -> {
                int remaining = Math.max(0, amount);
                while (remaining > 0) {
                    ItemStack stack = codex.registeredItem(id, remaining);
                    raw.add(stack);
                    remaining -= stack.getAmount();
                }
            });
        }
        Map<String, RunSnapshot.EquipmentInstanceState> instances = new LinkedHashMap<>();
        if (owner.equipmentInstances != null) {
            owner.equipmentInstances.forEach((id, instance) -> {
                instances.put(id, copyInstance(instance));
                if (!physicalEquipment.contains(id)) {
                    ItemStack virtual = equipment.itemForInstance(instance);
                    if (virtual != null) raw.add(virtual);
                }
            });
        }

        Map<String, RunSnapshot.RemainsItemState> contents = new LinkedHashMap<>();
        int lost = 0;
        int index = 0;
        for (ItemStack original : raw) {
            ItemStack kept = original.clone();
            boolean consumable = isLossConsumable(kept);
            int removed = GravePolicy.lostConsumables(kept.getAmount(), consumable);
            kept.setAmount(GravePolicy.remainingAmount(kept.getAmount(), consumable));
            lost += removed;
            if (kept.getAmount() <= 0) continue;
            RunSnapshot.RemainsItemState item = new RunSnapshot.RemainsItemState();
            item.entryId = "%03d".formatted(index++);
            item.encodedItem = encode(kept);
            item.equipmentInstanceId = equipment.equipmentInstanceId(kept);
            contents.put(item.entryId, item);
        }
        return new CapturedContents(contents, instances, lost);
    }

    private boolean isLossConsumable(ItemStack item) {
        String id = codex.itemId(item);
        ProductionContentCatalog.ItemEntry registered = id == null ? null : production.nonEquipmentItemsById().get(id);
        if (registered != null) return registered.quickConsumable();
        PrototypeContent.ItemDefinition legacy = id == null ? null : prototype.items().stream()
                .filter(value -> value.id().equals(id)).findFirst().orElse(null);
        if (legacy != null) return "QUICK_ITEM".equals(legacy.category());
        Material type = item.getType();
        return type.isEdible() || type == Material.POTION || type == Material.SPLASH_POTION
                || type == Material.LINGERING_POTION || type == Material.MILK_BUCKET
                || type == Material.HONEY_BOTTLE;
    }

    private void open(Player player, String remainsId, int requestedPage) {
        RunSnapshot.RemainsState remains = runs.current().map(run -> run.remains.get(remainsId)).orElse(null);
        if (remains == null) {
            player.sendMessage(ChatColor.RED + "유품 기록을 찾을 수 없습니다.");
            return;
        }
        List<RunSnapshot.RemainsItemState> available = remains.contents.values().stream()
                .filter(item -> "AVAILABLE".equals(item.state)).sorted(Comparator.comparing(item -> item.entryId)).toList();
        int pages = Math.max(1, (available.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        GraveHolder holder = new GraveHolder(player.getUniqueId(), remainsId, page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_RED + remains.ownerName + "의 유품");
        int from = page * PAGE_SIZE;
        int to = Math.min(available.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            RunSnapshot.RemainsItemState entry = available.get(index);
            int slot = index - from;
            ItemStack icon = decode(entry.encodedItem);
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ChatColor.YELLOW + "클릭: 회수 대기함으로 이전");
            lore.add(ChatColor.DARK_GRAY + "유품 항목 " + entry.entryId);
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            holder.entryBySlot.put(slot, entry.entryId);
        }
        if (page > 0) inventory.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "이전 페이지"));
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기"));
        if (page + 1 < pages) inventory.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "다음 페이지"));
        if (available.isEmpty()) inventory.setItem(22, named(Material.GRAY_DYE, ChatColor.GRAY + "회수 가능한 유품 없음"));
        player.openInventory(inventory);
    }

    private void claim(Player player, String remainsId, String entryId) {
        if (!canRecover(player)) return;
        RunSnapshot snapshot = runs.current().orElseThrow();
        RunSnapshot.RemainsState remains = snapshot.remains.get(remainsId);
        RunSnapshot.RemainsItemState entry = remains == null ? null : remains.contents.get(entryId);
        if (entry == null || !"AVAILABLE".equals(entry.state)) return;
        String deliveryId = "remains-delivery:" + remainsId + ":" + entryId;
        RunSnapshot.EquipmentInstanceState transferred = entry.equipmentInstanceId == null ? null
                : remains.equipmentInstances.get(entry.equipmentInstanceId);
        long now = Instant.now().toEpochMilli();
        boolean committed = runs.commitOnce(deliveryId, "REMAINS_ITEM_CLAIMED",
                "{\"remainsId\":\"" + remainsId + "\",\"entryId\":\"" + entryId
                        + "\",\"player\":\"" + player.getUniqueId() + "\"}", run -> {
                    RunSnapshot.RemainsState stored = run.remains.get(remainsId);
                    RunSnapshot.RemainsItemState storedEntry = stored.contents.get(entryId);
                    storedEntry.state = "CLAIMED";
                    storedEntry.claimedBy = player.getUniqueId().toString();
                    storedEntry.claimedAtEpochMs = now;
                    RunSnapshot.PlayerState receiver = run.players.get(player.getUniqueId().toString());
                    RunSnapshot.PendingRemainsDeliveryState pending = new RunSnapshot.PendingRemainsDeliveryState();
                    pending.deliveryId = deliveryId;
                    pending.remainsId = remainsId;
                    pending.entryId = entryId;
                    pending.encodedItem = storedEntry.encodedItem;
                    pending.claimedAtEpochMs = now;
                    receiver.pendingRemainsDeliveries.put(deliveryId, pending);
                    if (transferred != null) {
                        receiver.equipmentInstances.put(transferred.instanceId, copyInstance(transferred));
                        receiver.ownedEquipment.add(transferred.templateId);
                    }
                    if (stored.contents.values().stream().noneMatch(item -> "AVAILABLE".equals(item.state))) {
                        stored.state = "RECOVERED";
                    }
                });
        if (!committed) return;
        flushDeliveries(player);
        refreshMarker(remainsId);
    }

    private void flushDeliveries(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.pendingRemainsDeliveries == null) return;
        for (RunSnapshot.PendingRemainsDeliveryState pending :
                new ArrayList<>(state.pendingRemainsDeliveries.values())) {
            ItemStack existing = findDelivery(player, pending.deliveryId);
            if (existing == null) {
                ItemStack item = decode(pending.encodedItem);
                tagDelivery(item, pending.deliveryId);
                if (!storeWithoutReservedSlot(player, item)) {
                    player.sendMessage(ChatColor.YELLOW + "인벤토리 공간이 없어 유품을 회수 대기 중입니다.");
                    continue;
                }
                existing = findDelivery(player, pending.deliveryId);
            }
            if (existing == null) continue;
            runs.mutate(run -> run.players.get(player.getUniqueId().toString())
                    .pendingRemainsDeliveries.remove(pending.deliveryId));
            removeDeliveryTag(existing);
            String itemId = codex.itemId(existing);
            if (itemId != null) codex.discover(player, itemId, "REMAINS_RECOVERY");
            telemetry.event(runs.current().orElseThrow().runId, "REMAINS_ITEM_DELIVERED",
                    "{\"deliveryId\":\"" + pending.deliveryId + "\",\"player\":\""
                            + player.getUniqueId() + "\"}");
        }
    }

    private boolean canRecover(Player player) {
        return runs.isRunningMember(player) && runs.playerState(player.getUniqueId())
                .map(state -> "ACTIVE".equals(state.lifeState)).orElse(false);
    }

    private void attemptOpen(Player player, String remainsId) {
        if (!canRecover(player)) {
            player.sendMessage(ChatColor.RED + "생존 중인 같은 회차 파티원만 유품을 회수할 수 있습니다.");
            return;
        }
        open(player, remainsId, 0);
    }

    private void restoreMarker(RunSnapshot.RemainsState remains) {
        if (remains == null) return;
        Entity existing = findEntity(remains.markerEntityUuid);
        if (existing instanceof ArmorStand marker && marker.isValid()) {
            updateMarkerName(marker, remains);
            return;
        }
        World world = Bukkit.getWorld(remains.world);
        if (world == null) return;
        Location location = new Location(world, remains.x, remains.y, remains.z);
        world.getChunkAt(location).load();
        ArmorStand marker = world.spawn(location.clone().add(0.5, 0.0, 0.5), ArmorStand.class, entity -> {
            entity.setVisible(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setBasePlate(false);
            entity.setArms(false);
            entity.setSmall(true);
            entity.setCollidable(false);
            entity.getEquipment().setHelmet(new ItemStack(Material.BARREL));
            entity.getPersistentDataContainer().set(remainsIdKey, PersistentDataType.STRING, remains.remainsId);
            entity.getPersistentDataContainer().set(remainsRunKey, PersistentDataType.STRING,
                    runs.current().orElseThrow().runId);
            updateMarkerName(entity, remains);
        });
        runs.mutate(run -> run.remains.get(remains.remainsId).markerEntityUuid = marker.getUniqueId().toString());
    }

    private void refreshMarker(String remainsId) {
        RunSnapshot.RemainsState remains = runs.current().map(run -> run.remains.get(remainsId)).orElse(null);
        if (remains == null) return;
        Entity entity = findEntity(remains.markerEntityUuid);
        if (entity instanceof ArmorStand marker) updateMarkerName(marker, remains);
    }

    private void updateMarkerName(ArmorStand marker, RunSnapshot.RemainsState remains) {
        long available = remains.contents.values().stream().filter(item -> "AVAILABLE".equals(item.state)).count();
        marker.setCustomName((available == 0 ? ChatColor.GRAY : ChatColor.GOLD)
                + remains.ownerName + "의 유품 · " + available + "개");
        marker.setCustomNameVisible(true);
    }

    private void cleanupForeignMarkers(RunSnapshot snapshot) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String id = entity.getPersistentDataContainer().get(remainsIdKey, PersistentDataType.STRING);
                String runId = entity.getPersistentDataContainer().get(remainsRunKey, PersistentDataType.STRING);
                if (id != null && (!snapshot.runId.equals(runId) || !snapshot.remains.containsKey(id))) entity.remove();
            }
        }
    }

    private Location safeRemainsLocation(Location requested, RunSnapshot snapshot, RunSnapshot.PlayerState owner) {
        if (isSafe(requested)) return requested.getBlock().getLocation();
        if (owner.lastSafeWorld != null) {
            World safeWorld = Bukkit.getWorld(owner.lastSafeWorld);
            Location recorded = safeWorld == null ? null
                    : new Location(safeWorld, owner.lastSafeX, owner.lastSafeY, owner.lastSafeZ);
            if (isSafe(recorded)) return recorded.getBlock().getLocation();
        }
        RunSnapshot.FacilityInstanceState recovery = FacilityStateAccess.firstActive(snapshot, "FAC-S12").orElse(null);
        if (recovery != null) {
            World world = Bukkit.getWorld(recovery.world);
            if (world != null) return new Location(world, recovery.x, recovery.y + 1, recovery.z);
        }
        World world = requested.getWorld();
        if (world == null) return Bukkit.getWorlds().getFirst().getSpawnLocation();
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Location candidate = new Location(world, requested.getBlockX() + dx,
                            world.getHighestBlockYAt(requested.getBlockX() + dx, requested.getBlockZ() + dz) + 1,
                            requested.getBlockZ() + dz);
                    if (isSafe(candidate)) return candidate;
                }
            }
        }
        return world.getSpawnLocation();
    }

    private boolean isSafe(Location location) {
        if (location == null || location.getWorld() == null) return false;
        return location.getBlock().isPassable() && location.clone().add(0, 1, 0).getBlock().isPassable()
                && location.clone().subtract(0, 1, 0).getBlock().getType().isSolid()
                && !location.getBlock().isLiquid();
    }

    private void clearCommittedOwnerInventory(Player player) {
        player.closeInventory();
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }

    private boolean storeWithoutReservedSlot(Player player, ItemStack offered) {
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

    private ItemStack findDelivery(Player player, String deliveryId) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String value = item.getItemMeta().getPersistentDataContainer()
                    .get(deliveryIdKey, PersistentDataType.STRING);
            if (deliveryId.equals(value)) return item;
        }
        return null;
    }

    private void tagDelivery(ItemStack item, String deliveryId) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(deliveryIdKey, PersistentDataType.STRING, deliveryId);
        item.setItemMeta(meta);
    }

    private void removeDeliveryTag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(deliveryIdKey);
        item.setItemMeta(meta);
    }

    private void stripOrphanDeliveryTags(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        Set<String> pending = state == null || state.pendingRemainsDeliveries == null
                ? Set.of() : state.pendingRemainsDeliveries.keySet();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String value = item.getItemMeta().getPersistentDataContainer()
                    .get(deliveryIdKey, PersistentDataType.STRING);
            if (value != null && !pending.contains(value)) removeDeliveryTag(item);
        }
    }

    private Entity findEntity(String uuidText) {
        if (uuidText == null || uuidText.isBlank()) return null;
        try {
            UUID uuid = UUID.fromString(uuidText);
            for (World world : Bukkit.getWorlds()) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) return entity;
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static ItemStack decode(String encoded) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
    }

    private static RunSnapshot.EquipmentInstanceState copyInstance(RunSnapshot.EquipmentInstanceState source) {
        RunSnapshot.EquipmentInstanceState copy = new RunSnapshot.EquipmentInstanceState();
        copy.instanceId = source.instanceId;
        copy.templateId = source.templateId;
        copy.currentDurability = source.currentDurability;
        copy.maxDurability = source.maxDurability;
        copy.condition = source.condition;
        return copy;
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private record CapturedContents(Map<String, RunSnapshot.RemainsItemState> items,
                                    Map<String, RunSnapshot.EquipmentInstanceState> equipmentInstances,
                                    int lostConsumables) {
    }

    private static final class GraveHolder implements InventoryHolder {
        private final UUID viewer;
        private final String remainsId;
        private final int page;
        private final Map<Integer, String> entryBySlot = new HashMap<>();

        private GraveHolder(UUID viewer, String remainsId, int page) {
            this.viewer = viewer;
            this.remainsId = remainsId;
            this.page = page;
        }

        @Override public Inventory getInventory() { return null; }
    }
}
