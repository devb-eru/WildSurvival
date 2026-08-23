package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemCodexService implements Listener {
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent content;
    private final TelemetryService telemetry;
    private final NamespacedKey itemIdKey;
    private final List<CodexEntry> entries;
    private final Map<UUID, Long> pendingNoticeAt = new ConcurrentHashMap<>();

    public ItemCodexService(JavaPlugin plugin, RunService runs, PrototypeContent content, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.telemetry = telemetry;
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
        this.entries = buildEntries(content);
    }

    public ItemStack resourceItem(String rawId, int amount) {
        PrototypeContent.ResourceDefinition resource = content.resource(rawId.toUpperCase(java.util.Locale.ROOT));
        ItemStack item = new ItemStack(resourceMaterial(resource.id()), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "[WS] " + resource.name());
        meta.setLore(List.of(
                ChatColor.GRAY + "개인 소지 자원",
                ChatColor.WHITE + "공용 보급 저장소에서 원장으로 전환 가능",
                ChatColor.DARK_GRAY + "ID: " + resource.id()
        ));
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, resource.id());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack contentItem(String rawId, int amount) {
        PrototypeContent.ItemDefinition definition = content.item(rawId.toUpperCase(java.util.Locale.ROOT));
        Material material = Material.matchMaterial(definition.material());
        if (material == null || material.isAir()) throw new IllegalArgumentException("Invalid item material " + definition.material());
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(material.getMaxStackSize(), amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "[WS] " + definition.name());
        meta.setLore(List.of(ChatColor.WHITE + definition.description(), ChatColor.GRAY + "분류: " + definition.category(),
                ChatColor.DARK_GRAY + "ID: " + definition.id()));
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, definition.id());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack tagRegisteredItem(ItemStack item, String rawId) {
        if (item == null || item.getType().isAir()) throw new IllegalArgumentException("Cannot register an empty item");
        String id = rawId.toUpperCase(java.util.Locale.ROOT);
        if (entries.stream().noneMatch(entry -> entry.id.equals(id))) throw new IllegalArgumentException("Unknown codex item " + rawId);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public void grantItem(Player player, String itemId, int amount) {
        PrototypeContent.ItemDefinition definition = content.item(itemId.toUpperCase(java.util.Locale.ROOT));
        Material material = Material.matchMaterial(definition.material());
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(material == null ? 64 : material.getMaxStackSize(), remaining);
            int overflow = addWithoutReservedSlot(player, contentItem(definition.id(), stack));
            if (overflow > 0) queueRegisteredItem(player, definition.id(), overflow);
            remaining -= stack;
        }
        discover(player, definition.id(), "ACQUIRE_ITEM");
    }

    public int grantResource(Player player, String resourceId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        String normalized = content.resource(resourceId.toUpperCase(java.util.Locale.ROOT)).id();
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(64, remaining);
            int overflow = addWithoutReservedSlot(player, resourceItem(normalized, stackAmount));
            if (overflow > 0) queueRegisteredItem(player, normalized, overflow);
            remaining -= stackAmount;
        }
        discover(player, normalized, "ACQUIRE_RESOURCE");
        return countResource(player, normalized);
    }

    public int countResource(Player player, String resourceId) {
        String normalized = resourceId.toUpperCase(java.util.Locale.ROOT);
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (normalized.equals(itemId(item))) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public boolean takeResource(Player player, String resourceId, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Resource amount cannot be negative");
        }
        String normalized = content.resource(resourceId.toUpperCase(java.util.Locale.ROOT)).id();
        if (countResource(player, normalized) < amount) {
            return false;
        }
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!normalized.equals(itemId(item))) {
                continue;
            }
            int removed = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - removed);
            if (item.getAmount() <= 0) {
                contents[slot] = null;
            }
            remaining -= removed;
        }
        player.getInventory().setStorageContents(contents);
        return true;
    }

    public int countItem(Player player, String rawId) {
        String id = rawId.toUpperCase(java.util.Locale.ROOT);
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (id.equals(itemId(item))) count += item.getAmount();
        }
        return count;
    }

    public boolean takeItem(Player player, String rawId, int amount) {
        String id = rawId.toUpperCase(java.util.Locale.ROOT);
        if (amount < 0 || countItem(player, id) < amount) return false;
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 1; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!id.equals(itemId(item))) continue;
            int removed = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - removed);
            if (item.getAmount() <= 0) contents[slot] = null;
            remaining -= removed;
        }
        player.getInventory().setStorageContents(contents);
        return remaining == 0;
    }

    public boolean isResourceItem(ItemStack item) {
        String id = itemId(item);
        return id != null && content.resources().stream().anyMatch(resource -> resource.id().equals(id));
    }

    public String itemId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    public void discover(Player player, String rawId, String source) {
        if (!runs.isMember(player)) {
            return;
        }
        String id = rawId.toUpperCase(java.util.Locale.ROOT);
        if (entries.stream().noneMatch(entry -> entry.id.equals(id))) {
            throw new IllegalArgumentException("Unknown codex item " + rawId);
        }
        boolean added = runs.commitOnce("codex:" + player.getUniqueId() + ":" + id, "ITEM_CODEX_UNLOCKED",
                "{\"player\":\"" + player.getUniqueId() + "\",\"itemId\":\"" + id + "\",\"source\":\"" + source + "\"}",
                run -> run.players.get(player.getUniqueId().toString()).discoveredItemIds.add(id));
        if (added) {
            player.sendMessage(ChatColor.DARK_AQUA + "[아이템 도감] " + ChatColor.WHITE + entry(id).name + " 해금");
        }
    }

    public void reconcile(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) {
            return;
        }
        for (ItemStack item : player.getInventory().getStorageContents()) {
            String id = itemId(item);
            if (id != null && entries.stream().anyMatch(entry -> entry.id.equals(id))) {
                discover(player, id, "INVENTORY_RECONCILE");
            }
        }
        for (String equipment : state.ownedEquipment) {
            if (entries.stream().anyMatch(entry -> entry.id.equals(equipment))) {
                discover(player, equipment, "OWNED_EQUIPMENT_RECONCILE");
            }
        }
        state.quickItems.forEach((id, amount) -> {
            if (amount > 0 && entries.stream().anyMatch(entry -> entry.id.equals(id))) {
                discover(player, id, "QUICK_ITEM_RECONCILE");
            }
        });
        flushPending(player);
    }

    public void flushPending(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.pendingRegisteredItems == null || state.pendingRegisteredItems.isEmpty()) return;
        Map<String, Integer> remainingById = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : new LinkedHashMap<>(state.pendingRegisteredItems).entrySet()) {
            int remaining = Math.max(0, entry.getValue());
            while (remaining > 0) {
                ItemStack item = registeredItem(entry.getKey(), remaining);
                int attempted = item.getAmount();
                int overflow = addWithoutReservedSlot(player, item);
                remaining -= attempted - overflow;
                if (overflow == attempted) break;
            }
            if (remaining > 0) remainingById.put(entry.getKey(), remaining);
        }
        if (!state.pendingRegisteredItems.equals(remainingById)) {
            runs.mutate(run -> run.players.get(player.getUniqueId().toString()).pendingRegisteredItems = remainingById);
        }
        if (!remainingById.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - pendingNoticeAt.getOrDefault(player.getUniqueId(), 0L) >= 5_000L) {
                pendingNoticeAt.put(player.getUniqueId(), now);
                player.sendMessage(ChatColor.YELLOW + "인벤토리 공간이 없어 등록 아이템을 보관 중입니다. 공간을 비우면 자동 지급됩니다.");
            }
        } else {
            pendingNoticeAt.remove(player.getUniqueId());
        }
    }

    public void open(Player player) {
        if (!runs.isMember(player)) {
            player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다.");
            return;
        }
        reconcile(player);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        CodexHolder holder = new CodexHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "아이템 도감");
        int unlocked = 0;
        for (int index = 0; index < entries.size() && index < ENTRY_SLOTS.length; index++) {
            CodexEntry entry = entries.get(index);
            boolean known = state.discoveredItemIds.contains(entry.id);
            inventory.setItem(ENTRY_SLOTS[index], known ? knownItem(entry, state.detailedTooltips) : unknownItem(entry.id));
            if (known) {
                unlocked++;
            }
        }
        inventory.setItem(4, named(Material.KNOWLEDGE_BOOK, ChatColor.AQUA + "아이템 도감 " + unlocked + "/" + entries.size(),
                List.of(ChatColor.GRAY + "항목 위치와 ID는 고정됩니다.")));
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        player.openInventory(inventory);
    }

    public List<String> entryIds() {
        return entries.stream().map(entry -> entry.id).toList();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item item = event.getItem();
        String id = itemId(item.getItemStack());
        if (id != null && entries.stream().anyMatch(entry -> entry.id.equals(id))) {
            discover(player, id, "PICKUP");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> flushPending(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (runs.isMember(event.getPlayer())) {
            Bukkit.getScheduler().runTask(plugin, () -> flushPending(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnyInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && runs.isMember(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> flushPending(player));
        }
    }

    @EventHandler
    public void onPrepareVanillaCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isResourceItem(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onCodexClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CodexHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && player.getUniqueId().equals(holder.owner)
                && event.getRawSlot() == 49) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onCodexDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CodexHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack knownItem(CodexEntry entry, boolean detailed) {
        ItemStack item = new ItemStack(entry.material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + entry.name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "ID: " + entry.id);
        lore.add(ChatColor.WHITE + "분류: " + entry.category);
        lore.addAll(entry.details.stream().map(line -> ChatColor.GRAY + line).toList());
        if (detailed) {
            recipeForOutput(entry.id).ifPresent(recipe -> {
                lore.add(ChatColor.YELLOW + "조합법: " + recipe.name());
                recipe.costs().forEach((id, amount) -> lore.add(ChatColor.WHITE + "- " + content.resource(id).name() + " " + amount));
                if (recipe.shapeless()) {
                    lore.add(ChatColor.YELLOW + "배치: 위치 무관, 재료마다 서로 다른 칸 사용");
                } else {
                    lore.add(ChatColor.YELLOW + "3×3 배치:");
                    for (int row = 0; row < 3; row++) {
                        List<String> cells = new ArrayList<>();
                        for (int column = 0; column < 3; column++) {
                            String resourceId = recipe.shape().get(row * 3 + column);
                            cells.add(resourceId == null ? "빈칸" : content.resource(resourceId).name());
                        }
                        lore.add(ChatColor.WHITE + "[" + String.join("][", cells) + "]");
                    }
                }
            });
            List<PrototypeContent.RecipeDefinition> uses = content.recipes().stream()
                    .filter(recipe -> recipe.costs().containsKey(entry.id)).toList();
            if (!uses.isEmpty()) {
                lore.add(ChatColor.YELLOW + "사용처:");
                uses.forEach(recipe -> lore.add(ChatColor.WHITE + "- " + recipe.name()));
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack unknownItem(String id) {
        return named(Material.BLACK_DYE, ChatColor.BLACK + "???", List.of(
                ChatColor.DARK_GRAY + "ID: " + id,
                ChatColor.GRAY + "처음 획득하면 정보가 해금됩니다."
        ));
    }

    private java.util.Optional<PrototypeContent.RecipeDefinition> recipeForOutput(String id) {
        return content.recipes().stream().filter(recipe -> recipe.rewardId().equals(id)).findFirst();
    }

    private CodexEntry entry(String id) {
        return entries.stream().filter(entry -> entry.id.equals(id)).findFirst().orElseThrow();
    }

    private static List<CodexEntry> buildEntries(PrototypeContent content) {
        Map<String, CodexEntry> result = new LinkedHashMap<>();
        for (PrototypeContent.ResourceDefinition resource : content.resources()) {
            List<String> details = resource.sourceMaterials().isEmpty()
                    ? List.of("전투·오염 개체에서 획득")
                    : List.of("채집: " + String.join(", ", resource.sourceMaterials()));
            result.put(resource.id(), new CodexEntry(resource.id(), resource.name(), "RESOURCE",
                    resourceMaterial(resource.id()), details));
        }
        for (PrototypeContent.ItemDefinition definition : content.items()) {
            Material material = Material.matchMaterial(definition.material());
            result.put(definition.id(), new CodexEntry(definition.id(), definition.name(), definition.category(),
                    material == null || material.isAir() ? Material.PAPER : material, List.of(definition.description())));
        }
        for (PrototypeContent.RecipeDefinition recipe : content.recipes()) {
            Material material = switch (recipe.rewardType()) {
                case "EQUIPMENT" -> Material.matchMaterial(content.weapon(recipe.rewardId()).material());
                case "ITEM", "QUICK_ITEM" -> Material.matchMaterial(content.item(recipe.rewardId()).material());
                case "FACILITY" -> Material.BARREL;
                default -> Material.PAPER;
            };
            result.putIfAbsent(recipe.rewardId(), new CodexEntry(recipe.rewardId(), recipe.name(), recipe.rewardType(),
                    material == null || material.isAir() ? Material.PAPER : material,
                    List.of("WildSurvival Craft GUI 전용 제작")));
        }
        return List.copyOf(result.values());
    }

    private static Material resourceMaterial(String id) {
        return switch (id) {
            case "WSR-WOOD" -> Material.OAK_LOG;
            case "WSR-STONE" -> Material.COBBLESTONE;
            case "WSR-FIBER" -> Material.STRING;
            case "WSR-IRON" -> Material.RAW_IRON;
            case "WSR-TISSUE" -> Material.FERMENTED_SPIDER_EYE;
            case "WSR-COAL" -> Material.COAL;
            default -> Material.PAPER;
        };
    }

    private ItemStack registeredItem(String id, int amount) {
        boolean resource = content.resources().stream().anyMatch(value -> value.id().equals(id));
        return resource ? resourceItem(id, Math.min(64, amount)) : contentItem(id, amount);
    }

    private void queueRegisteredItem(Player player, String id, int amount) {
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            if (state.pendingRegisteredItems == null) state.pendingRegisteredItems = new LinkedHashMap<>();
            state.pendingRegisteredItems.merge(id, amount, Integer::sum);
        });
    }

    private int addWithoutReservedSlot(Player player, ItemStack offered) {
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
            int moved = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
            ItemStack placed = remaining.clone(); placed.setAmount(moved);
            player.getInventory().setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - moved);
        }
        return remaining.getAmount();
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private record CodexEntry(String id, String name, String category, Material material, List<String> details) {
    }

    private static final class CodexHolder implements InventoryHolder {
        private final UUID owner;

        private CodexHolder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
