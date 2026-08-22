package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyService implements Listener {
    private static final int[] INPUTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    private static final Set<Integer> INPUT_SET = Set.of(11, 12, 13, 20, 21, 22, 29, 30, 31);
    private static final int RESULT = 24;
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent content;
    private final EquipmentService equipment;
    private final GrowthService growth;
    private final TelemetryService telemetry;
    private final ItemCodexService codex;
    private final NamespacedKey facilityKey;
    private final NamespacedKey placeholderKey;

    public EconomyService(JavaPlugin plugin, RunService runs, PrototypeContent content, EquipmentService equipment,
                          GrowthService growth, TelemetryService telemetry, ItemCodexService codex) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.equipment = equipment;
        this.growth = growth;
        this.telemetry = telemetry;
        this.codex = codex;
        this.facilityKey = new NamespacedKey(plugin, "facility_id");
        this.placeholderKey = new NamespacedKey(plugin, "craft_placeholder");
    }

    public void openCraft(Player player) {
        if (!runs.isRunningMember(player)) {
            player.sendMessage(ChatColor.RED + "진행 중인 회차에서만 제작할 수 있습니다.");
            return;
        }
        if (!runs.current().orElseThrow().craftUnlocked) {
            Inventory inventory = Bukkit.createInventory(new UnlockHolder(player.getUniqueId()), 27,
                    ChatColor.DARK_AQUA + "Craft 잠금 해제");
            inventory.setItem(13, named(Material.CRAFTING_TABLE, ChatColor.GOLD + "Craft 해금",
                    List.of(ChatColor.WHITE + "아무 나무 원목 4개 필요", ChatColor.GRAY + "클릭하면 파티 전체에 해금됩니다.")));
            player.openInventory(inventory);
            return;
        }
        CraftHolder holder = new CraftHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "WildSurvival Craft");
        ItemStack border = placeholder(Material.GRAY_STAINED_GLASS_PANE, " ", "border");
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
        inventory.setItem(4, named(Material.CRAFTING_TABLE, ChatColor.AQUA + "3×3 조합 제작",
                List.of(ChatColor.GRAY + "한 칸당 개인 자원 1개를 사용합니다.", ChatColor.GRAY + "도감에서 고정 조합법을 확인하세요.")));
        inventory.setItem(23, named(Material.ARROW, ChatColor.GRAY + "→", List.of()));
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        for (int index = 0; index < INPUTS.length; index++) {
            int row = index / 3 + 1;
            int column = index % 3 + 1;
            inventory.setItem(INPUTS[index], placeholder(Material.WHITE_STAINED_GLASS_PANE,
                    ChatColor.WHITE + "조합 칸 " + row + "-" + column, "input"));
        }
        player.openInventory(inventory);
        renderCraft(inventory);
    }

    public void openLedger(Player player) {
        RunSnapshot run = runs.current().orElse(null);
        if (!runs.isRunningMember(player) || run == null || !run.sharedLedgerUnlocked
                || run.facility == null || !run.facility.active) {
            player.sendMessage(ChatColor.RED + "공용 보급 저장소를 먼저 제작해야 합니다.");
            return;
        }
        LedgerHolder holder = new LedgerHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 45, ChatColor.DARK_GREEN + "공용 자원 원장");
        int[] slots = {11, 13, 15, 29, 31};
        for (int i = 0; i < content.resources().size() && i < slots.length; i++) {
            PrototypeContent.ResourceDefinition resource = content.resources().get(i);
            holder.resourceBySlot.put(slots[i], resource.id());
            int personal = codex.countResource(player, resource.id());
            int shared = run.resources.getOrDefault(resource.id(), 0);
            ItemStack icon = codex.resourceItem(resource.id(), Math.max(1, Math.min(64, personal)));
            ItemMeta meta = icon.getItemMeta();
            meta.setLore(List.of(ChatColor.WHITE + "개인 " + personal + " / 공용 " + shared,
                    ChatColor.GRAY + "좌클릭: 개인→공용 1", ChatColor.GRAY + "Shift+좌클릭: 전량 입금",
                    ChatColor.GRAY + "우클릭: 공용→개인 1", ChatColor.GRAY + "Shift+우클릭: 최대 64 출금"));
            icon.setItemMeta(meta);
            inventory.setItem(slots[i], icon);
        }
        inventory.setItem(40, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        player.openInventory(inventory);
    }

    public void grantPersonalResource(Player player, String resourceId, int amount) {
        codex.grantResource(player, resourceId, amount);
    }

    public void prepareCraftTest(Player player, int amountPerResource) {
        if (!runs.isTestRun()) throw new IllegalStateException("Test Lab run required");
        runs.mutate(run -> run.craftUnlocked = true);
        for (PrototypeContent.ResourceDefinition resource : content.resources()) {
            codex.grantResource(player, resource.id(), amountPerResource);
        }
    }

    public void restoreFacility() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.facility == null || !snapshot.facility.active) return;
        if (!"COMMON_RESOURCE_DEPOT".equals(snapshot.facility.id)) {
            runs.mutate(run -> {
                run.facility.id = "COMMON_RESOURCE_DEPOT";
                run.sharedLedgerUnlocked = true;
                run.craftUnlocked = true;
            });
        }
        org.bukkit.World world = Bukkit.getWorld(snapshot.facility.world);
        if (world != null) markFacility(world.getBlockAt(snapshot.facility.x, snapshot.facility.y, snapshot.facility.z));
    }

    public void removeFacility() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.facility == null || !snapshot.facility.active) return;
        org.bukkit.World world = Bukkit.getWorld(snapshot.facility.world);
        if (world != null) {
            Block block = world.getBlockAt(snapshot.facility.x, snapshot.facility.y, snapshot.facility.z);
            if (isFacility(block)) block.setType(Material.AIR, false);
        }
        snapshot.facility.active = false;
    }

    public void placeFacility(Player player) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (snapshot.facility != null && snapshot.facility.active) {
            player.sendMessage(ChatColor.RED + "공용 보급 저장소는 회차당 하나만 설치할 수 있습니다.");
            return;
        }
        Block target = findFacilityBlock(player.getLocation());
        markFacility(target);
        runs.mutate(run -> {
            RunSnapshot.FacilityState facility = new RunSnapshot.FacilityState();
            facility.id = "COMMON_RESOURCE_DEPOT";
            facility.world = target.getWorld().getName();
            facility.x = target.getX(); facility.y = target.getY(); facility.z = target.getZ(); facility.active = true;
            run.facility = facility;
            run.sharedLedgerUnlocked = true;
        });
        codex.discover(player, "COMMON_RESOURCE_DEPOT", "CRAFT");
        runs.broadcast(ChatColor.GREEN + "공용 보급 저장소 설치: " + target.getX() + ", " + target.getY() + ", " + target.getZ());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResourceBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!runs.isRunningMember(player)) return;
        PrototypeContent.ResourceDefinition resource = findResource(event.getBlock().getType());
        if (resource == null) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (!canHarvest(player, resource.id())) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    requiredToolMessage(resource.id()), net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        int amount = Math.max(1, (int) Math.floor(resource.amountPerNode() * growth.resourceMultiplier(player)));
        int personal = codex.grantResource(player, resource.id(), amount);
        String key = "node:" + event.getBlock().getWorld().getUID() + ":" + event.getBlock().getX() + ":"
                + event.getBlock().getY() + ":" + event.getBlock().getZ();
        growth.awardExp(player, resource.activityExp(), "node-exp:" + key + ":" + player.getUniqueId());
        player.sendActionBar(net.kyori.adventure.text.Component.text(resource.name() + " +" + amount + " / 개인 " + personal,
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    private boolean canHarvest(Player player, String resourceId) {
        if ("WSR-WOOD".equals(resourceId) || "WSR-FIBER".equals(resourceId)) return true;
        String toolId = codex.itemId(player.getInventory().getItemInMainHand());
        return switch (resourceId) {
            case "WSR-STONE", "WSR-COAL" -> Set.of(
                    "TOOL-CRUDE-PICKAXE", "TOOL-STONE-PICKAXE", "TOOL-IRON-PICKAXE").contains(toolId);
            case "WSR-IRON" -> Set.of("TOOL-STONE-PICKAXE", "TOOL-IRON-PICKAXE").contains(toolId);
            default -> true;
        };
    }

    private String requiredToolMessage(String resourceId) {
        return switch (resourceId) {
            case "WSR-STONE", "WSR-COAL" -> "급조 곡괭이 이상의 채집 도구가 필요합니다.";
            case "WSR-IRON" -> "석재 채집 곡괭이 이상의 채집 도구가 필요합니다.";
            default -> "알맞은 채집 도구가 필요합니다.";
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFacilityBreak(BlockBreakEvent event) {
        if (isFacility(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "공용 보급 저장소는 직접 파괴할 수 없습니다.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFacilityInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && isFacility(event.getClickedBlock()) && runs.isRunningMember(event.getPlayer())) {
            event.setCancelled(true);
            openLedger(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof UnlockHolder holder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && holder.owner.equals(player.getUniqueId()) && event.getRawSlot() == 13) unlockCraft(player);
            return;
        }
        if (top.getHolder() instanceof LedgerHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
            if (event.getRawSlot() == 40) { player.closeInventory(); return; }
            String id = holder.resourceBySlot.get(event.getRawSlot());
            if (id != null) transferLedger(player, id, event.getClick());
            return;
        }
        if (!(top.getHolder() instanceof CraftHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) {
            event.setCancelled(true); return;
        }
        int raw = event.getRawSlot();
        if (raw == RESULT) { event.setCancelled(true); craft(player, top); renderCraft(top); return; }
        if (raw == 49) { event.setCancelled(true); player.closeInventory(); return; }
        if (raw < top.getSize()) {
            if (!INPUT_SET.contains(raw)) { event.setCancelled(true); return; }
            if (event.getClick() == ClickType.NUMBER_KEY) { event.setCancelled(true); return; }
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !codex.isResourceItem(cursor)) event.setCancelled(true);
            if (isPlaceholder(event.getCurrentItem())) {
                event.setCancelled(true);
                if (cursor != null && !cursor.getType().isAir() && codex.isResourceItem(cursor)) {
                    int amount = event.isRightClick() ? 1 : cursor.getAmount();
                    ItemStack placed = cursor.clone(); placed.setAmount(amount); top.setItem(raw, placed);
                    cursor.setAmount(cursor.getAmount() - amount);
                    if (cursor.getAmount() <= 0) event.setCursor(null);
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> renderCraft(top));
            return;
        }
        if (event.isShiftClick() || event.getClick() == ClickType.NUMBER_KEY || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof UnlockHolder || top.getHolder() instanceof LedgerHolder) { event.setCancelled(true); return; }
        if (!(top.getHolder() instanceof CraftHolder)) return;
        Set<Integer> topSlots = new HashSet<>();
        for (int raw : event.getRawSlots()) if (raw < top.getSize()) topSlots.add(raw);
        if (topSlots.isEmpty()) return;
        if (!INPUT_SET.containsAll(topSlots) || event.getNewItems().values().stream().anyMatch(item -> !codex.isResourceItem(item))) {
            event.setCancelled(true); return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> renderCraft(top));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CraftHolder)) return;
        Player player = (Player) event.getPlayer();
        for (int slot : INPUTS) {
            ItemStack item = event.getInventory().getItem(slot);
            if (item == null || item.getType().isAir() || isPlaceholder(item)) continue;
            String id = codex.itemId(item);
            if (id != null) codex.grantResource(player, id, item.getAmount());
            else player.getWorld().dropItemNaturally(player.getLocation(), item);
            event.getInventory().setItem(slot, null);
        }
    }

    private void unlockCraft(Player player) {
        if (runs.current().orElseThrow().craftUnlocked) { openCraft(player); return; }
        if (countLogs(player) < 4) { player.sendMessage(ChatColor.RED + "원목이 4개 필요합니다."); return; }
        takeLogs(player, 4);
        runs.mutate(run -> run.craftUnlocked = true);
        runs.broadcast(ChatColor.GREEN + player.getName() + "이(가) Craft를 해금했습니다.");
        telemetry.event(runs.current().orElseThrow().runId, "CRAFT_UNLOCKED", "{\"cost\":\"ANY_LOG:4\"}");
        openCraft(player);
    }

    private void craft(Player player, Inventory inventory) {
        PrototypeContent.RecipeDefinition recipe = RecipeGridPolicy.match(content.recipes(), grid(inventory)).orElse(null);
        if (recipe == null) { player.sendActionBar(net.kyori.adventure.text.Component.text("유효한 조합법이 아닙니다.")); return; }
        RunSnapshot snapshot = runs.current().orElseThrow();
        if ("FACILITY".equals(recipe.rewardType()) && snapshot.facility != null && snapshot.facility.active) {
            player.sendMessage(ChatColor.RED + "공용 보급 저장소는 하나만 설치할 수 있습니다."); return;
        }
        if ("FACILITY".equals(recipe.rewardType())) {
            try { findFacilityBlock(player.getLocation()); }
            catch (IllegalStateException exception) { player.sendMessage(ChatColor.RED + exception.getMessage()); return; }
        }
        for (int slot : INPUTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || isPlaceholder(item)) continue;
            item.setAmount(item.getAmount() - 1);
            if (item.getAmount() <= 0) inventory.setItem(slot, null);
        }
        switch (recipe.rewardType()) {
            case "EQUIPMENT" -> { equipment.grantEquipment(player, recipe.rewardId()); codex.discover(player, recipe.rewardId(), "CRAFT"); }
            case "ITEM" -> codex.grantItem(player, recipe.rewardId(), recipe.rewardAmount());
            case "QUICK_ITEM" -> { equipment.grantQuickItem(player, recipe.rewardId(), recipe.rewardAmount()); codex.discover(player, recipe.rewardId(), "CRAFT"); }
            case "FACILITY" -> placeFacility(player);
            default -> throw new IllegalStateException("Unknown reward type " + recipe.rewardType());
        }
        telemetry.event(snapshot.runId, "CRAFT_COMMITTED", "{\"recipeId\":\"" + recipe.id() + "\"}");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.4f);
    }

    private void renderCraft(Inventory inventory) {
        PrototypeContent.RecipeDefinition recipe = RecipeGridPolicy.match(content.recipes(), grid(inventory)).orElse(null);
        inventory.setItem(RESULT, recipe == null
                ? named(Material.GRAY_DYE, ChatColor.GRAY + "조합 결과 없음", List.of())
                : named(rewardMaterial(recipe), ChatColor.GOLD + recipe.name(), List.of(ChatColor.YELLOW + "클릭하여 제작", ChatColor.DARK_GRAY + recipe.id())));
        for (int index = 0; index < INPUTS.length; index++) {
            if (inventory.getItem(INPUTS[index]) == null || inventory.getItem(INPUTS[index]).getType().isAir()) {
                int row = index / 3 + 1; int column = index % 3 + 1;
                inventory.setItem(INPUTS[index], placeholder(Material.WHITE_STAINED_GLASS_PANE,
                        ChatColor.WHITE + "조합 칸 " + row + "-" + column, "input"));
            }
        }
    }

    private List<String> grid(Inventory inventory) {
        List<String> result = new ArrayList<>(9);
        for (int slot : INPUTS) result.add(codex.itemId(inventory.getItem(slot)));
        return result;
    }

    private void transferLedger(Player player, String id, ClickType click) {
        boolean deposit = click.isLeftClick();
        boolean withdraw = click.isRightClick();
        if (!deposit && !withdraw) return;
        if (deposit) {
            int amount = click.isShiftClick() ? codex.countResource(player, id) : 1;
            if (amount <= 0 || !codex.takeResource(player, id, amount)) { player.sendMessage(ChatColor.RED + "개인 자원이 부족합니다."); return; }
            runs.addResource("ledger-deposit:" + UUID.randomUUID(), id, amount);
        } else {
            int balance = runs.current().orElseThrow().resources.getOrDefault(id, 0);
            int amount = click.isShiftClick() ? Math.min(64, balance) : 1;
            if (amount <= 0 || !runs.withdrawResource("ledger-withdraw:" + UUID.randomUUID(), id, amount)) {
                player.sendMessage(ChatColor.RED + "공용 자원이 부족합니다."); return;
            }
            codex.grantResource(player, id, amount);
        }
        openLedger(player);
    }

    private int countLogs(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) if (item != null && Tag.LOGS.isTagged(item.getType())) count += item.getAmount();
        return count;
    }

    private void takeLogs(Player player, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !Tag.LOGS.isTagged(item.getType())) continue;
            int take = Math.min(remaining, item.getAmount()); item.setAmount(item.getAmount() - take); remaining -= take;
            if (item.getAmount() <= 0) contents[i] = null;
        }
        player.getInventory().setStorageContents(contents);
    }

    private PrototypeContent.ResourceDefinition findResource(Material material) {
        return content.resources().stream().filter(resource -> resource.sourceMaterials().stream()
                .map(name -> Material.matchMaterial(name.toUpperCase(Locale.ROOT))).anyMatch(material::equals)).findFirst().orElse(null);
    }

    private Block findFacilityBlock(Location origin) {
        Block base = origin.getBlock();
        for (int radius = 0; radius <= 3; radius++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            Block candidate = base.getRelative(x, 0, z);
            if (candidate.getType().isAir()) return candidate;
        }
        throw new IllegalStateException("주변에 저장소를 설치할 빈 공간이 없습니다.");
    }

    private void markFacility(Block block) {
        block.setType(Material.BARREL, false);
        if (block.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().set(facilityKey, PersistentDataType.STRING, "COMMON_RESOURCE_DEPOT");
            tile.update(true, false);
        }
    }

    private boolean isFacility(Block block) {
        return block != null && block.getState() instanceof TileState tile
                && "COMMON_RESOURCE_DEPOT".equals(tile.getPersistentDataContainer().get(facilityKey, PersistentDataType.STRING));
    }

    private Material rewardMaterial(PrototypeContent.RecipeDefinition recipe) {
        return switch (recipe.rewardType()) {
            case "EQUIPMENT" -> Material.matchMaterial(content.weapon(recipe.rewardId()).material());
            case "ITEM", "QUICK_ITEM" -> Material.matchMaterial(content.item(recipe.rewardId()).material());
            case "FACILITY" -> Material.BARREL;
            default -> Material.PAPER;
        };
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null || material.isAir() ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item;
    }

    private ItemStack placeholder(Material material, String name, String type) {
        ItemStack item = named(material, name, List.of(type.equals("input") ? ChatColor.GRAY + "개인 WS 자원을 놓으세요." : ""));
        ItemMeta meta = item.getItemMeta(); meta.getPersistentDataContainer().set(placeholderKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta); return item;
    }

    private boolean isPlaceholder(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(placeholderKey, PersistentDataType.STRING);
    }

    private static final class CraftHolder implements InventoryHolder {
        private final UUID owner; private CraftHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
    private static final class UnlockHolder implements InventoryHolder {
        private final UUID owner; private UnlockHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
    private static final class LedgerHolder implements InventoryHolder {
        private final UUID owner; private final Map<Integer, String> resourceBySlot = new java.util.HashMap<>();
        private LedgerHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
}
