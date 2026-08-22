package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyService implements Listener {
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent content;
    private final EquipmentService equipment;
    private final GrowthService growth;
    private final TelemetryService telemetry;
    private final NamespacedKey facilityKey;

    public EconomyService(JavaPlugin plugin, RunService runs, PrototypeContent content, EquipmentService equipment,
                          GrowthService growth, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.equipment = equipment;
        this.growth = growth;
        this.telemetry = telemetry;
        this.facilityKey = new NamespacedKey(plugin, "facility_id");
    }

    public void openCraft(Player player) {
        if (!runs.isRunningMember(player)) {
            player.sendMessage(ChatColor.RED + "진행 중인 프로토타입 회차에서만 제작할 수 있습니다.");
            return;
        }
        CraftHolder holder = new CraftHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "야전 제작 — 공용 원장");
        int[] slots = {10, 12, 14, 16, 20, 24};
        for (int i = 0; i < Math.min(slots.length, content.recipes().size()); i++) {
            inventory.setItem(slots[i], recipeItem(content.recipes().get(i)));
            holder.recipeBySlot.put(slots[i], content.recipes().get(i).id());
        }
        inventory.setItem(49, ledgerItem());
        player.openInventory(inventory);
    }

    public void restoreFacility() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.facility == null || !snapshot.facility.active) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(snapshot.facility.world);
        if (world == null) {
            return;
        }
        markFacility(world.getBlockAt(snapshot.facility.x, snapshot.facility.y, snapshot.facility.z));
    }

    public void removeFacility() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.facility == null || !snapshot.facility.active) {
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(snapshot.facility.world);
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(snapshot.facility.x, snapshot.facility.y, snapshot.facility.z);
        if (isFacility(block)) {
            block.setType(Material.AIR, false);
        }
        snapshot.facility.active = false;
    }

    public void placeFacility(Player player) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (snapshot.facility != null && snapshot.facility.active) {
            player.sendMessage(ChatColor.RED + "이미 야전 공작대가 설치되어 있습니다.");
            return;
        }
        Block target = findFacilityBlock(player.getLocation());
        markFacility(target);
        runs.mutate(run -> {
            RunSnapshot.FacilityState facility = new RunSnapshot.FacilityState();
            facility.id = "FIELD_WORKBENCH";
            facility.world = target.getWorld().getName();
            facility.x = target.getX();
            facility.y = target.getY();
            facility.z = target.getZ();
            facility.active = true;
            run.facility = facility;
        });
        runs.broadcast(ChatColor.GREEN + "야전 공작대가 설치되었습니다: " + target.getX() + ", " + target.getY() + ", " + target.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResourceBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!runs.isRunningMember(player)) {
            return;
        }
        PrototypeContent.ResourceDefinition resource = findResource(event.getBlock().getType());
        if (resource == null) {
            return;
        }
        int amount = Math.max(1, (int) Math.floor(resource.amountPerNode() * growth.resourceMultiplier(player)));
        String key = "node:" + event.getBlock().getWorld().getUID() + ":" + event.getBlock().getX() + ":"
                + event.getBlock().getY() + ":" + event.getBlock().getZ();
        int balance = runs.addResource(key, resource.id(), amount);
        growth.awardExp(player, resource.activityExp(), "node-exp:" + key + ":" + player.getUniqueId());
        player.sendActionBar(net.kyori.adventure.text.Component.text(resource.name() + " +" + amount + " / 공용 " + balance,
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFacilityBreak(BlockBreakEvent event) {
        if (isFacility(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "시설은 직접 파괴할 수 없습니다.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFacilityPlace(BlockPlaceEvent event) {
        if (runs.isRunningMember(event.getPlayer()) && event.getBlockPlaced().getType() == Material.BARREL
                && event.getItemInHand().hasItemMeta()
                && ChatColor.stripColor(event.getItemInHand().getItemMeta().getDisplayName()).contains("야전 공작대")) {
            event.setCancelled(true);
            placeFacility(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFacilityInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && isFacility(event.getClickedBlock()) && runs.isRunningMember(event.getPlayer())) {
            event.setCancelled(true);
            openCraft(event.getPlayer());
        }
    }

    @EventHandler
    public void onCraftClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CraftHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.owner)) {
            return;
        }
        String recipeId = holder.recipeBySlot.get(event.getRawSlot());
        if (recipeId == null || holder.pending) {
            return;
        }
        holder.pending = true;
        PrototypeContent.RecipeDefinition recipe = content.recipes().stream().filter(value -> value.id().equals(recipeId)).findFirst().orElseThrow();
        craft(player, recipe);
        Bukkit.getScheduler().runTask(plugin, () -> openCraft(player));
    }

    private void craft(Player player, PrototypeContent.RecipeDefinition recipe) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if ("FACILITY".equals(recipe.rewardType()) && snapshot.facility != null && snapshot.facility.active) {
            player.sendMessage(ChatColor.RED + "야전 공작대는 회차당 하나만 설치할 수 있습니다.");
            return;
        }
        String key = "craft:" + UUID.randomUUID();
        boolean success = runs.transactResources(key, recipe.costs(), "CRAFT_COMMITTED",
                "{\"recipeId\":\"" + recipe.id() + "\"}", run -> {
                    RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                    switch (recipe.rewardType()) {
                        case "EQUIPMENT" -> state.ownedEquipment.add(recipe.rewardId());
                        case "QUICK_ITEM" -> state.quickItems.merge(recipe.rewardId(), recipe.rewardAmount(), Integer::sum);
                        case "FACILITY" -> { }
                        default -> throw new IllegalStateException("Unknown recipe reward type " + recipe.rewardType());
                    }
                });
        if (!success) {
            player.sendMessage(ChatColor.RED + "재료가 부족합니다: " + recipe.costs());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
            return;
        }
        if ("FACILITY".equals(recipe.rewardType())) {
            placeFacility(player);
        }
        equipment.syncAuthoritativeEquipment(player);
        player.sendMessage(ChatColor.GREEN + "제작 완료: " + recipe.name());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.55f, 1.25f);
        growth.awardExp(player, 20, "craft-exp:" + key + ":" + player.getUniqueId());
        telemetry.event(runs.current().orElseThrow().runId, "CRAFT_UI_CONFIRMED", "{\"recipeId\":\"" + recipe.id() + "\"}");
    }

    private PrototypeContent.ResourceDefinition findResource(Material material) {
        String name = material.name();
        return content.resources().stream().filter(resource -> resource.sourceMaterials().contains(name)).findFirst().orElse(null);
    }

    private ItemStack recipeItem(PrototypeContent.RecipeDefinition recipe) {
        Material material = switch (recipe.rewardType()) {
            case "EQUIPMENT" -> Material.matchMaterial(content.weapon(recipe.rewardId()).material());
            case "QUICK_ITEM" -> Material.COOKED_BEEF;
            case "FACILITY" -> Material.BARREL;
            default -> Material.PAPER;
        };
        if (material == null || material.isAir()) {
            material = Material.PAPER;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + recipe.name());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "비용:");
        recipe.costs().forEach((id, amount) -> lore.add(ChatColor.WHITE + "- " + content.resource(id).name() + " " + amount));
        lore.add(ChatColor.YELLOW + "클릭하여 제작");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack ledgerItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "공용 자원 원장");
        List<String> lore = new ArrayList<>();
        RunSnapshot snapshot = runs.current().orElseThrow();
        snapshot.resources.forEach((id, amount) -> lore.add(ChatColor.WHITE + content.resource(id).name() + ": " + amount));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void markFacility(Block block) {
        block.setType(Material.BARREL, false);
        if (block.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().set(facilityKey, PersistentDataType.STRING, "FIELD_WORKBENCH");
            tile.update(true, false);
        }
    }

    private boolean isFacility(Block block) {
        return block.getState() instanceof TileState tile
                && tile.getPersistentDataContainer().has(facilityKey, PersistentDataType.STRING);
    }

    private static Block findFacilityBlock(Location origin) {
        Location location = origin.getBlock().getLocation();
        for (int y = 0; y <= 3; y++) {
            Block candidate = location.clone().add(2, y, 0).getBlock();
            if (candidate.getType().isAir()) {
                return candidate;
            }
        }
        Location surface = origin.clone().add(2, 0, 0);
        surface.setY(origin.getWorld().getHighestBlockYAt(surface) + 1.0);
        return surface.getBlock();
    }

    private static final class CraftHolder implements InventoryHolder {
        private final UUID owner;
        private final Map<Integer, String> recipeBySlot = new java.util.HashMap<>();
        private boolean pending;

        private CraftHolder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
