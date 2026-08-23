package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
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
    private final ProductionContentCatalog production;
    private final TelemetryService telemetry;
    private final NamespacedKey itemIdKey;
    private final List<CodexEntry> entries;
    private final Map<UUID, Long> pendingNoticeAt = new ConcurrentHashMap<>();

    private static final Map<String, String> PROTOTYPE_ALIASES = Map.ofEntries(
            Map.entry("SWORD", "EQL-W01"), Map.entry("AXE", "EQL-W02"), Map.entry("BOW", "EQL-W03"),
            Map.entry("PICKAXE", "EQL-W08"), Map.entry("MACE", "EQL-W06"), Map.entry("TRIDENT", "EQL-W09"),
            Map.entry("RATION", "WSI-CONS-RATION_PACK"), Map.entry("BANDAGE", "WSI-CONS-BANDAGE"),
            Map.entry("ANTIDOTE", "WSI-CONS-ANTIDOTE_INJECTION"),
            Map.entry("COMMON_RESOURCE_DEPOT", "WSI-FAC-S16-KIT"),
            Map.entry("TOOL-CRUDE-PICKAXE", "EQL-UT-RI-PICKAXE"),
            Map.entry("TOOL-STONE-PICKAXE", "EQL-UT-RI-PICKAXE"),
            Map.entry("TOOL-IRON-PICKAXE", "EQL-UT-RI-PICKAXE"));

    public ItemCodexService(JavaPlugin plugin, RunService runs, PrototypeContent content,
                            ProductionContentCatalog production, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.production = production;
        this.telemetry = telemetry;
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
        this.entries = buildEntries(production);
    }

    public ItemStack resourceItem(String rawId, int amount) {
        String id = rawId.toUpperCase(java.util.Locale.ROOT);
        ProductionContentCatalog.CatalogEntry resource = production.item(id);
        ProductionContentCatalog.MaterialEntry definition = production.materialsById().get(id);
        if (!"MATERIAL".equals(resource.domain()) || !id.startsWith("WSR-")) throw new IllegalArgumentException("Not a countable resource " + rawId);
        Material display = Material.matchMaterial(resource.displayMaterial());
        if (display == null || display.isAir()) display = resourceMaterial(id);
        ItemStack item = new ItemStack(display, Math.max(1, Math.min(display.getMaxStackSize(), amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "[WS] " + resource.name());
        meta.setLore(List.of(
                ChatColor.GRAY + "개인 소지 자원",
                ChatColor.WHITE + "공용 보급 저장소에서 원장으로 전환 가능",
                ChatColor.GRAY + (definition == null ? "획득 정보 없음"
                        : definition.acquisitionKind() + " · " + definition.usageText()),
                ChatColor.DARK_GRAY + "ID: " + resource.id()
        ));
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, resource.id());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack contentItem(String rawId, int amount) {
        String id = rawId.toUpperCase(java.util.Locale.ROOT);
        PrototypeContent.ItemDefinition prototype = content.items().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        ProductionContentCatalog.CatalogEntry productionEntry = prototype == null ? production.item(id) : null;
        ProductionContentCatalog.ItemEntry itemDefinition = prototype == null
                ? production.nonEquipmentItemsById().get(id) : null;
        String materialName = prototype == null ? productionEntry.displayMaterial() : prototype.material();
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir()) throw new IllegalArgumentException("Invalid item material " + materialName);
        int stackLimit = itemDefinition == null ? material.getMaxStackSize()
                : Math.min(material.getMaxStackSize(), itemDefinition.stackLimit());
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(stackLimit, amount)));
        ItemMeta meta = item.getItemMeta();
        String name = prototype == null ? productionEntry.name() : prototype.name();
        String category = itemDefinition == null ? (prototype == null ? productionEntry.domain() : prototype.category())
                : itemDefinition.category();
        String description = itemDefinition == null ? (prototype == null ? "ws-content-r2 등록 아이템" : prototype.description())
                : itemDefinition.effectText();
        if (itemDefinition != null) meta.setMaxStackSize(stackLimit);
        meta.setDisplayName(ChatColor.GOLD + "[WS] " + name);
        meta.setLore(List.of(ChatColor.WHITE + description, ChatColor.GRAY + "분류: " + category,
                ChatColor.DARK_GRAY + "ID: " + id));
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack tagRegisteredItem(ItemStack item, String rawId) {
        if (item == null || item.getType().isAir()) throw new IllegalArgumentException("Cannot register an empty item");
        String id = rawId.toUpperCase(java.util.Locale.ROOT);
        if (!hasEntry(id)) throw new IllegalArgumentException("Unknown codex item " + rawId);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public void grantItem(Player player, String itemId, int amount) {
        String id = itemId.toUpperCase(java.util.Locale.ROOT);
        ItemStack example = contentItem(id, 1);
        Material material = example.getType();
        ProductionContentCatalog.ItemEntry definition = production.nonEquipmentItemsById().get(id);
        int stackLimit = definition == null ? (material == null ? 64 : material.getMaxStackSize()) : definition.stackLimit();
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(stackLimit, remaining);
            int overflow = addWithoutReservedSlot(player, contentItem(id, stack));
            if (overflow > 0) queueRegisteredItem(player, id, overflow);
            remaining -= stack;
        }
        discover(player, id, "ACQUIRE_ITEM");
    }

    public int grantResource(Player player, String resourceId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        String normalized = production.item(resourceId.toUpperCase(java.util.Locale.ROOT)).id();
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
        String normalized = production.item(resourceId.toUpperCase(java.util.Locale.ROOT)).id();
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
        ProductionContentCatalog.CatalogEntry entry = id == null ? null : production.itemsById().get(id);
        return entry != null && "MATERIAL".equals(entry.domain()) && id.startsWith("WSR-");
    }

    public String itemId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    public ItemStack registeredItem(String rawId, int amount) {
        String id = rawId.toUpperCase(java.util.Locale.ROOT);
        ProductionContentCatalog.CatalogEntry entry = production.itemsById().get(id);
        boolean resource = entry != null && "MATERIAL".equals(entry.domain()) && id.startsWith("WSR-");
        return resource ? resourceItem(id, Math.min(64, amount)) : contentItem(id, amount);
    }

    public void discover(Player player, String rawId, String source) {
        if (!runs.isMember(player)) {
            return;
        }
        String id = normalizeCodexId(rawId);
        if (!hasEntry(id)) {
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
            if (id != null && hasEntry(id)) {
                discover(player, id, "INVENTORY_RECONCILE");
            }
        }
        for (String equipment : state.ownedEquipment) {
            if (hasEntry(equipment)) {
                discover(player, equipment, "OWNED_EQUIPMENT_RECONCILE");
            }
        }
        state.quickItems.forEach((id, amount) -> {
            if (amount > 0 && hasEntry(id)) {
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
        open(player, 0);
    }

    private void open(Player player, int requestedPage) {
        if (!runs.isMember(player)) {
            player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다.");
            return;
        }
        reconcile(player);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        int pageCount = Math.max(1, (entries.size() + ENTRY_SLOTS.length - 1) / ENTRY_SLOTS.length);
        int page = Math.max(0, Math.min(pageCount - 1, requestedPage));
        CodexHolder holder = new CodexHolder(player.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "아이템 도감");
        int unlocked = (int) entries.stream().filter(entry -> state.discoveredItemIds.contains(entry.id)).count();
        int start = page * ENTRY_SLOTS.length;
        for (int localIndex = 0; localIndex < ENTRY_SLOTS.length && start + localIndex < entries.size(); localIndex++) {
            CodexEntry entry = entries.get(start + localIndex);
            boolean known = state.discoveredItemIds.contains(entry.id);
            inventory.setItem(ENTRY_SLOTS[localIndex], known ? knownItem(entry, state.detailedTooltips) : unknownItem(entry));
        }
        inventory.setItem(4, named(Material.KNOWLEDGE_BOOK, ChatColor.AQUA + "아이템 도감 " + unlocked + "/" + entries.size(),
                List.of(ChatColor.GRAY + "항목 위치와 ID는 고정됩니다.", ChatColor.WHITE + "페이지 " + (page + 1) + "/" + pageCount)));
        if (page > 0) inventory.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "이전 페이지", List.of()));
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        if (page + 1 < pageCount) inventory.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "다음 페이지", List.of()));
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
        if (id != null && hasEntry(id)) {
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
        if (event.getWhoClicked() instanceof Player player && player.getUniqueId().equals(holder.owner)) {
            if (event.getRawSlot() == 45) open(player, holder.page - 1);
            else if (event.getRawSlot() == 53) open(player, holder.page + 1);
            else if (event.getRawSlot() == 49) player.closeInventory();
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
        lore.add(ChatColor.DARK_GRAY + "도감 " + String.format(java.util.Locale.ROOT, "%04d", entry.codexIndex) + " · ID: " + entry.id);
        lore.add(ChatColor.WHITE + "분류: " + entry.category);
        lore.addAll(entry.details.stream().map(line -> ChatColor.GRAY + line).toList());
        if (detailed) {
            List<ProductionContentCatalog.RecipeEntry> recipes = production.recipesByOutput()
                    .getOrDefault(entry.id, List.of());
            if (recipes.isEmpty()) {
                lore.add(ChatColor.GRAY + "획득 전용 · 등록 조합법 없음");
            } else {
                lore.add(ChatColor.YELLOW + "등록 조합법 " + recipes.size() + "개:");
                for (ProductionContentCatalog.RecipeEntry recipe : recipes) {
                    lore.add(ChatColor.WHITE + "- " + recipe.id() + " [" + recipe.layout() + "] → ×" + recipe.outputAmount());
                    for (ProductionContentCatalog.IngredientEntry ingredient : recipe.ingredients()) {
                        String marker = "PROOF".equals(ingredient.kind()) ? "검사·비소비 "
                                : "TAG".equals(ingredient.kind()) ? "대체 가치 " : "";
                        lore.add(ChatColor.GRAY + "  " + (ingredient.slot() + 1) + "번: " + marker
                                + ingredient.key() + " ×" + ingredient.amount());
                    }
                }
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack unknownItem(CodexEntry entry) {
        return named(Material.BLACK_DYE, ChatColor.BLACK + "???", List.of(
                ChatColor.DARK_GRAY + "도감 " + String.format(java.util.Locale.ROOT, "%04d", entry.codexIndex),
                ChatColor.GRAY + "처음 획득하면 정보가 해금됩니다."
        ));
    }

    private CodexEntry entry(String id) {
        String normalized = normalizeCodexId(id);
        return entries.stream().filter(entry -> entry.id.equals(normalized)).findFirst().orElseThrow();
    }

    private static List<CodexEntry> buildEntries(ProductionContentCatalog production) {
        return production.codexEntries().stream().map(entry -> {
            Material material = Material.matchMaterial(entry.displayMaterial());
            return new CodexEntry(entry.id(), entry.codexIndex(), entry.name(), entry.domain(),
                    material == null || material.isAir() ? Material.PAPER : material,
                    List.of("최초 Day " + entry.firstDay(), "고정 도감 위치 · ws-content-r2"));
        }).toList();
    }

    private String normalizeCodexId(String rawId) {
        String id = rawId.toUpperCase(java.util.Locale.ROOT);
        return PROTOTYPE_ALIASES.getOrDefault(id, id);
    }

    private boolean hasEntry(String rawId) {
        String id = normalizeCodexId(rawId);
        return entries.stream().anyMatch(entry -> entry.id.equals(id));
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

    private record CodexEntry(String id, int codexIndex, String name, String category, Material material, List<String> details) {
    }

    private static final class CodexHolder implements InventoryHolder {
        private final UUID owner;
        private final int page;

        private CodexHolder(UUID owner, int page) {
            this.owner = owner;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
