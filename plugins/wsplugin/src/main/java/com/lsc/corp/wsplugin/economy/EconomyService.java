package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.content.RecipeTagCatalog;
import com.lsc.corp.wsplugin.facility.FacilityStateAccess;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.ui.ActionBarService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerJoinEvent;
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
    private static final String CRAFT_UNLOCK_LOG_CHECKPOINT = "VANILLA:ANY_LOG";
    private static final int[] LEDGER_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent content;
    private final ProductionContentCatalog production;
    private final EquipmentService equipment;
    private final GrowthService growth;
    private final TelemetryService telemetry;
    private final ItemCodexService codex;
    private final NamespacedKey facilityKey;
    private BiPredicate<Player, String> virtualPreflight = (player, outputId) -> true;
    private BiConsumer<Player, String> virtualCommit;

    public EconomyService(JavaPlugin plugin, RunService runs, PrototypeContent content,
                          ProductionContentCatalog production, EquipmentService equipment,
                          GrowthService growth, TelemetryService telemetry, ItemCodexService codex) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.production = production;
        this.equipment = equipment;
        this.growth = growth;
        this.telemetry = telemetry;
        this.codex = codex;
        this.facilityKey = new NamespacedKey(plugin, "facility_id");
        this.virtualCommit = (player, outputId) -> runs.commitOnce("virtual-output:" + outputId,
                "VIRTUAL_RECIPE_COMMITTED", "{\"outputId\":\"" + outputId + "\"}",
                run -> run.committedKeys.add("proof:" + outputId));
    }

    public void setVirtualFacilityHandler(BiPredicate<Player, String> preflight,
                                          BiConsumer<Player, String> commit) {
        this.virtualPreflight = java.util.Objects.requireNonNull(preflight);
        this.virtualCommit = java.util.Objects.requireNonNull(commit);
    }

    public void openCraft(Player player) {
        if (!runs.isRunningMember(player)) {
            player.sendMessage(ChatColor.RED + "진행 중인 회차에서만 제작할 수 있습니다.");
            return;
        }
        codex.reconcilePersonalResources(player);
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
        ItemStack border = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
        inventory.setItem(4, named(Material.CRAFTING_TABLE, ChatColor.AQUA + "3×3 조합 제작",
                List.of(ChatColor.GRAY + "칸별 요구 수량·대체 태그 가치를 정확히 검사합니다.",
                        ChatColor.GRAY + "도감에서 고정 조합법을 확인하세요.")));
        inventory.setItem(23, named(Material.ARROW, ChatColor.GRAY + "→", List.of()));
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        for (int slot : INPUTS) inventory.setItem(slot, null);
        player.openInventory(inventory);
        renderCraft(player, inventory);
    }

    public void openLedger(Player player) {
        openLedger(player, 0);
    }

    private void openLedger(Player player, int requestedPage) {
        codex.reconcilePersonalResources(player);
        RunSnapshot run = runs.current().orElse(null);
        boolean prototypeDepot = run != null && run.facility != null && run.facility.active;
        boolean productionDepot = run != null && FacilityStateAccess.active(run, "FAC-S16");
        if (!runs.isRunningMember(player) || run == null || !run.sharedLedgerUnlocked
                || (!prototypeDepot && !productionDepot)) {
            player.sendMessage(ChatColor.RED + "FAC-S16 공용 물류고를 활성 상태로 설치해야 합니다.");
            return;
        }
        List<ProductionContentCatalog.MaterialEntry> visible = production.materialsById().values().stream()
                .filter(resource -> resource.firstDay() <= run.day)
                .sorted(Comparator.comparingInt(resource -> production.item(resource.id()).codexIndex()))
                .toList();
        int pageCount = Math.max(1, (visible.size() + LEDGER_SLOTS.length - 1) / LEDGER_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        LedgerHolder holder = new LedgerHolder(player.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_GREEN + "공용 자원 원장 " + (page + 1) + "/" + pageCount);
        int offset = page * LEDGER_SLOTS.length;
        for (int i = 0; i < LEDGER_SLOTS.length && offset + i < visible.size(); i++) {
            ProductionContentCatalog.MaterialEntry resource = visible.get(offset + i);
            int slot = LEDGER_SLOTS[i];
            holder.resourceBySlot.put(slot, resource.id());
            int personal = codex.countResource(player, resource.id());
            int shared = run.resources.getOrDefault(resource.id(), 0);
            ItemStack icon = codex.resourceItem(resource.id(), Math.max(1, Math.min(64, personal)));
            ItemMeta meta = icon.getItemMeta();
            meta.setLore(List.of(ChatColor.WHITE + "개인 " + personal + " / 공용 " + shared,
                    ChatColor.GRAY + "좌클릭: 개인→공용 1", ChatColor.GRAY + "Shift+좌클릭: 전량 입금",
                    ChatColor.GRAY + "우클릭: 공용→개인 1", ChatColor.GRAY + "Shift+우클릭: 최대 64 출금"));
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
        }
        if (page > 0) inventory.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "이전 페이지", List.of()));
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        if (page + 1 < pageCount) inventory.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "다음 페이지", List.of()));
        player.openInventory(inventory);
    }

    public void grantPersonalResource(Player player, String resourceId, int amount) {
        codex.grantResource(player, resourceId, amount);
    }

    public void grantPersonalItem(Player player, String itemId, int amount) {
        codex.grantItem(player, itemId, amount);
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
        if (!"COMMON_RESOURCE_DEPOT".equals(snapshot.facility.id)
                || !snapshot.sharedLedgerUnlocked || !snapshot.craftUnlocked) {
            runs.mutate(run -> {
                run.facility.id = "COMMON_RESOURCE_DEPOT";
                run.sharedLedgerUnlocked = true;
                run.craftUnlocked = true;
            });
        }
        org.bukkit.World world = Bukkit.getWorld(snapshot.facility.world);
        if (world != null) markFacility(world.getBlockAt(snapshot.facility.x, snapshot.facility.y, snapshot.facility.z));
    }

    public void restore() {
        for (Player player : runs.onlineMembers()) recoverCraftTransactions(player);
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

    public void placeFacility(Player player, Block target) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (snapshot.facility != null && snapshot.facility.active) {
            player.sendMessage(ChatColor.RED + "공용 보급 저장소는 회차당 하나만 설치할 수 있습니다.");
            return;
        }
        if (target == null || !target.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "선택한 위치에 저장소를 설치할 수 없습니다.");
            return;
        }
        boolean consumed = codex.takeRegisteredItemWithRunMutation(player, "COMMON_RESOURCE_DEPOT", 1, run -> {
            RunSnapshot.FacilityState facility = new RunSnapshot.FacilityState();
            facility.id = "COMMON_RESOURCE_DEPOT";
            facility.world = target.getWorld().getName();
            facility.x = target.getX(); facility.y = target.getY(); facility.z = target.getZ(); facility.active = true;
            run.facility = facility;
            run.sharedLedgerUnlocked = true;
        });
        if (!consumed) {
            player.sendMessage(ChatColor.RED + "공용 보급 저장소 아이템이 필요합니다.");
            return;
        }
        markFacility(target);
        codex.discover(player, "COMMON_RESOURCE_DEPOT", "PLACE");
        runs.broadcast(ChatColor.GREEN + "공용 보급 저장소 설치: " + target.getX() + ", " + target.getY() + ", " + target.getZ());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResourceBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!runs.isRunningMember(player)) return;
        RunSnapshot snapshot = runs.current().orElseThrow();
        Material blockMaterial = event.getBlock().getType();
        // Craft 해금에 필요한 첫 원목 4개는 반드시 바닐라 아이템으로 남긴다.
        if (!snapshot.craftUnlocked && Tag.LOGS.isTagged(blockMaterial)) return;
        ProductionContentCatalog.MaterialEntry resource = findResource(blockMaterial, snapshot.day);
        if (resource == null) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (!canHarvest(player, resource, blockMaterial)) {
            event.setCancelled(true);
            ActionBarService.notice(player, net.kyori.adventure.text.Component.text(
                    requiredToolMessage(resource), net.kyori.adventure.text.format.NamedTextColor.RED), 40);
            return;
        }
        int amount = Math.max(1, (int) Math.floor(growth.resourceMultiplier(player)));
        int personal = codex.grantResource(player, resource.id(), amount);
        String key = "node:" + event.getBlock().getWorld().getUID() + ":" + event.getBlock().getX() + ":"
                + event.getBlock().getY() + ":" + event.getBlock().getZ();
        int beforeExp = runs.playerState(player.getUniqueId()).map(state -> state.exp).orElse(0);
        int activityExp = content.resources().stream().filter(value -> value.id().equals(resource.id()))
                .map(PrototypeContent.ResourceDefinition::activityExp).findFirst().orElse(0);
        if (activityExp > 0) growth.awardExp(player, activityExp, "node-exp:" + key + ":" + player.getUniqueId());
        int gainedExp = Math.max(0, runs.playerState(player.getUniqueId()).map(state -> state.exp).orElse(beforeExp) - beforeExp);
        ActionBarService.show(player, net.kyori.adventure.text.Component.text(
                resource.name() + " +" + amount + " · EXP +" + gainedExp + " · 개인 " + personal,
                net.kyori.adventure.text.format.NamedTextColor.GREEN), 45, 25);
    }

    private boolean canHarvest(Player player, ProductionContentCatalog.MaterialEntry resource, Material source) {
        int requiredTier = resourceTier(resource.tier());
        if (requiredTier == 0 && !requiresPickaxe(source)) return true;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!toolMatchesSource(tool, source)) return false;
        return toolTier(tool) >= requiredTier;
    }

    private String requiredToolMessage(ProductionContentCatalog.MaterialEntry resource) {
        return resource.name() + " 채집에는 " + toolTierName(resourceTier(resource.tier())) + " 등급의 알맞은 도구가 필요합니다.";
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
    public void onRegisteredItemUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !runs.isRunningMember(event.getPlayer())) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String itemId = codex.itemId(event.getItem());
        if ("SURVIVAL-CLOCK".equals(itemId)) {
            event.setCancelled(true);
            showClock(event.getPlayer());
            return;
        }
        if (!"COMMON_RESOURCE_DEPOT".equals(itemId) || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) return;
        event.setCancelled(true);
        if (runs.current().orElseThrow().facility != null && runs.current().orElseThrow().facility.active) {
            event.getPlayer().sendMessage(ChatColor.RED + "공용 보급 저장소는 회차당 하나만 설치할 수 있습니다.");
            return;
        }
        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!target.getType().isAir()) {
            event.getPlayer().sendMessage(ChatColor.RED + "선택한 면에 저장소를 설치할 빈 공간이 없습니다.");
            return;
        }
        placeFacility(event.getPlayer(), target);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKeyItemDrop(PlayerDropItemEvent event) {
        if (runs.isMember(event.getPlayer())
                && "SURVIVAL-CLOCK".equals(codex.itemId(event.getItemDrop().getItemStack()))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.YELLOW + "생존 시계는 버릴 수 없습니다.");
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
            if (event.getRawSlot() == 45) { openLedger(player, holder.page - 1); return; }
            if (event.getRawSlot() == 49) { player.closeInventory(); return; }
            if (event.getRawSlot() == 53) { openLedger(player, holder.page + 1); return; }
            String id = holder.resourceBySlot.get(event.getRawSlot());
            if (id != null) transferLedger(player, id, event.getClick(), holder.page);
            return;
        }
        if (!(top.getHolder() instanceof CraftHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) {
            event.setCancelled(true); return;
        }
        int raw = event.getRawSlot();
        if (raw == RESULT) {
            event.setCancelled(true);
            if (event.isRightClick()) {
                holder.selectedRecipeIndex++;
                holder.selectedRecipeId = null;
            }
            else craft(player, top);
            renderCraft(player, top);
            return;
        }
        if (raw == 49) { event.setCancelled(true); player.closeInventory(); return; }
        if (raw < top.getSize()) {
            if (!INPUT_SET.contains(raw)) { event.setCancelled(true); return; }
            if (event.getClick() == ClickType.NUMBER_KEY) { event.setCancelled(true); return; }
            Bukkit.getScheduler().runTask(plugin, () -> renderCraft(player, top));
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
        if (!INPUT_SET.containsAll(topSlots)) {
            event.setCancelled(true); return;
        }
        Player player = (Player) event.getWhoClicked();
        Bukkit.getScheduler().runTask(plugin, () -> renderCraft(player, top));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CraftHolder)) return;
        Player player = (Player) event.getPlayer();
        boolean dropped = false;
        for (int slot : INPUTS) {
            ItemStack item = event.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            dropped |= returnCraftInput(player, item);
            event.getInventory().setItem(slot, null);
        }
        if (dropped) {
            player.sendMessage(ChatColor.YELLOW + "인벤토리가 가득 차 일부 제작 입력을 발밑에 본인 전용으로 반환했습니다.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            reconcileCraftUnlockLogs(event.getPlayer());
            recoverCraftTransactions(event.getPlayer());
        });
    }

    private void recoverCraftTransactions(Player player) {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !runs.isMember(player)) return;
        List<String> transactionIds = snapshot.resourceTransactions.values().stream()
                .filter(transaction -> CraftTransactionPolicy.KIND.equals(transaction.transactionKind))
                .filter(transaction -> player.getUniqueId().toString().equals(transaction.ownerUuid))
                .filter(transaction -> Set.of("RESERVED", "PROCESSING", "COMMITTED").contains(transaction.state))
                .map(transaction -> transaction.transactionId).toList();
        for (String transactionId : transactionIds) {
            RunSnapshot.ResourceTransactionState transaction = runs.current().orElseThrow()
                    .resourceTransactions.get(transactionId);
            if (transaction == null) continue;
            if ("RESERVED".equals(transaction.state)) {
                if (!runs.beginResourceTransaction(transactionId)) continue;
                transaction = runs.current().orElseThrow().resourceTransactions.get(transactionId);
            }
            if ("PROCESSING".equals(transaction.state)) commitCraftOutput(player, transaction);
            else if ("COMMITTED".equals(transaction.state)) {
                materializeCommittedCraftOutput(player, transaction);
                codex.discover(player, transaction.outputId, "CRAFT_RECOVERY");
            }
        }
    }

    private void reconcileCraftUnlockLogs(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.pendingPhysicalItemCounts == null
                || !state.pendingPhysicalItemCounts.containsKey(CRAFT_UNLOCK_LOG_CHECKPOINT)) return;
        int target = Math.max(0, state.pendingPhysicalItemCounts.get(CRAFT_UNLOCK_LOG_CHECKPOINT));
        int physical = countLogs(player);
        if (physical > target) {
            takeLogs(player, physical - target);
        } else if (physical < target) {
            addRecoveryLogs(player, target - physical);
        }
        player.saveData();
        if (countLogs(player) == target) {
            runs.mutateAtomically(run -> run.players.get(player.getUniqueId().toString())
                    .pendingPhysicalItemCounts.remove(CRAFT_UNLOCK_LOG_CHECKPOINT));
        }
    }

    private void addRecoveryLogs(Player player, int amount) {
        int remaining = amount;
        for (int slot = 1; slot <= 35 && remaining > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir() || item.getType() != Material.OAK_LOG) continue;
            int moved = Math.min(item.getMaxStackSize() - item.getAmount(), remaining);
            item.setAmount(item.getAmount() + moved);
            remaining -= moved;
        }
        for (int slot = 1; slot <= 35 && remaining > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir()) continue;
            int moved = Math.min(64, remaining);
            player.getInventory().setItem(slot, new ItemStack(Material.OAK_LOG, moved));
            remaining -= moved;
        }
    }

    private void unlockCraft(Player player) {
        if (runs.current().orElseThrow().craftUnlocked) { openCraft(player); return; }
        int before = countLogs(player);
        if (before < 4) { player.sendMessage(ChatColor.RED + "원목이 4개 필요합니다."); return; }
        RunSnapshot snapshot = runs.current().orElseThrow();
        boolean committed = runs.commitOnceAtomically("craft-unlock:" + snapshot.runId,
                "CRAFT_UNLOCKED", "{\"cost\":\"ANY_LOG:4\"}", run -> {
                    run.craftUnlocked = true;
                    RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                    state.pendingPhysicalItemCounts.put(CRAFT_UNLOCK_LOG_CHECKPOINT, before - 4);
                });
        if (!committed && !runs.current().orElseThrow().craftUnlocked) {
            player.sendMessage(ChatColor.RED + "Craft 해금 저장에 실패했습니다.");
            return;
        }
        takeLogs(player, 4);
        player.saveData();
        reconcileCraftUnlockLogs(player);
        runs.broadcast(ChatColor.GREEN + player.getName() + "이(가) Craft를 해금했습니다.");
        telemetry.event(runs.current().orElseThrow().runId, "CRAFT_UNLOCKED", "{\"cost\":\"ANY_LOG:4\"}");
        openCraft(player);
    }

    private void craft(Player player, Inventory inventory) {
        PrototypeContent.RecipeDefinition recipe = RecipeGridPolicy.match(content.recipes(), grid(inventory)).orElse(null);
        if (recipe == null) { craftProduction(player, inventory); return; }
        RunSnapshot snapshot = runs.current().orElseThrow();
        if ("COMMON_RESOURCE_DEPOT".equals(recipe.rewardId()) && snapshot.facility != null && snapshot.facility.active) {
            player.sendMessage(ChatColor.RED + "공용 보급 저장소는 하나만 설치할 수 있습니다."); return;
        }
        if ("EQUIPMENT".equals(recipe.rewardType()) && !equipment.canGrantEquipment(player)) {
            player.sendMessage(ChatColor.RED + "장비를 받을 인벤토리 공간이 없습니다.");
            return;
        }
        Map<Integer, Integer> consumed = new java.util.LinkedHashMap<>();
        for (int index = 0; index < INPUTS.length; index++) {
            ItemStack item = inventory.getItem(INPUTS[index]);
            if (item != null && !item.getType().isAir()) consumed.put(index, 1);
        }
        String outputType = "EQUIPMENT".equals(recipe.rewardType()) ? "EQUIPMENT" : "ITEM";
        executeCraftTransaction(player, inventory, recipe.id(), outputType,
                recipe.rewardId(), recipe.rewardAmount(), consumed, null);
    }

    private void renderCraft(Player player, Inventory inventory) {
        PrototypeContent.RecipeDefinition recipe = RecipeGridPolicy.match(content.recipes(), grid(inventory)).orElse(null);
        if (recipe != null) {
            inventory.setItem(RESULT, named(rewardMaterial(recipe), ChatColor.GOLD + recipe.name(),
                    List.of(ChatColor.YELLOW + "좌클릭하여 제작", ChatColor.DARK_GRAY + recipe.id())));
            return;
        }
        ProductionRecipePolicy.Match match = productionMatch(player, inventory).orElse(null);
        if (match == null) {
            inventory.setItem(RESULT, named(Material.GRAY_DYE, ChatColor.GRAY + "조합 결과 없음", List.of()));
            return;
        }
        ProductionContentCatalog.RecipeEntry productionRecipe = match.recipe();
        boolean dayReady = recipeFirstDay(productionRecipe) <= runs.current().orElseThrow().day;
        List<String> lore = new ArrayList<>();
        lore.add(dayReady ? ChatColor.YELLOW + "좌클릭하여 제작" : ChatColor.RED + "Day " + recipeFirstDay(productionRecipe) + "부터 제작 가능");
        if (match.candidateCount() > 1) lore.add(ChatColor.AQUA + "우클릭: 같은 배열 결과 순환 (" + match.candidateCount() + "개)");
        lore.add(ChatColor.DARK_GRAY + productionRecipe.id());
        inventory.setItem(RESULT, named(productionRewardMaterial(productionRecipe),
                (dayReady ? ChatColor.GOLD : ChatColor.RED) + productionName(productionRecipe.outputId()), lore));
    }

    private void craftProduction(Player player, Inventory inventory) {
        ProductionRecipePolicy.Match match = productionMatch(player, inventory).orElse(null);
        if (match == null) {
            ActionBarService.notice(player, net.kyori.adventure.text.Component.text("유효한 조합법이 아닙니다."), 35);
            return;
        }
        ProductionContentCatalog.RecipeEntry recipe = match.recipe();
        RunSnapshot snapshot = runs.current().orElseThrow();
        int firstDay = recipeFirstDay(recipe);
        if (snapshot.day < firstDay) {
            player.sendMessage(ChatColor.RED + "이 조합법은 Day " + firstDay + "부터 실행할 수 있습니다.");
            return;
        }
        ProductionContentCatalog.CatalogEntry output = production.itemsById().get(recipe.outputId());
        if (output == null && !virtualPreflight.test(player, recipe.outputId())) return;
        if (output != null && output.equipment() && !equipment.canGrantEquipment(player)) {
            player.sendMessage(ChatColor.RED + "장비 결과를 받을 인벤토리 공간이 없습니다.");
            return;
        }
        ItemStack baseEquipment = null;
        for (ProductionContentCatalog.IngredientEntry ingredient : recipe.ingredients()) {
            if (!"ITEM".equals(ingredient.kind())) continue;
            ProductionContentCatalog.CatalogEntry input = production.itemsById().get(ingredient.key());
            if (input != null && input.equipment()) {
                baseEquipment = inventory.getItem(INPUTS[ingredient.slot()]).clone();
                break;
            }
        }
        String outputType = output == null ? "VIRTUAL" : output.equipment() ? "EQUIPMENT"
                : "MATERIAL".equals(output.domain()) ? "MATERIAL" : "ITEM";
        executeCraftTransaction(player, inventory, recipe.id(), outputType, recipe.outputId(),
                recipe.outputAmount(), match.consumedBySlot(), baseEquipment);
    }

    private void executeCraftTransaction(Player player, Inventory inventory, String recipeId,
                                         String outputType, String outputId, int outputAmount,
                                         Map<Integer, Integer> consumedByGridSlot,
                                         ItemStack baseEquipment) {
        Map<String, Integer> resourceCosts = new java.util.LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> consumed : consumedByGridSlot.entrySet()) {
            ItemStack item = inventory.getItem(INPUTS[consumed.getKey()]);
            if (item == null || item.getAmount() < consumed.getValue()) {
                throw new IllegalStateException("검증 이후 조합 입력이 변경되었습니다.");
            }
            String itemId = codex.itemId(item);
            if (itemId != null && production.materialsById().containsKey(itemId)) {
                resourceCosts.merge(itemId, consumed.getValue(), Math::addExact);
            }
        }
        String owner = player.getUniqueId().toString();
        RunSnapshot.PlayerState playerState = runs.playerState(player.getUniqueId()).orElseThrow();
        for (Map.Entry<String, Integer> cost : resourceCosts.entrySet()) {
            if (playerState.personalResources.getOrDefault(cost.getKey(), 0) < cost.getValue()) {
                player.sendMessage(ChatColor.RED + "개인 자원 원장과 제작 입력이 일치하지 않습니다: " + cost.getKey());
                codex.reconcilePersonalResources(player);
                return;
            }
        }
        String transactionId = "craft:" + player.getUniqueId() + ":" + UUID.randomUUID();
        String inputSignature = ProductionRecipePolicy.fingerprint(productionGrid(inventory));
        String baseInstanceId = equipment.equipmentInstanceId(baseEquipment);
        String outputInstanceId = "EQUIPMENT".equals(outputType)
                ? (baseInstanceId == null ? UUID.randomUUID().toString() : baseInstanceId) : null;
        double durabilityRatio = 1.0;
        if (baseInstanceId != null) {
            RunSnapshot.EquipmentInstanceState base = playerState.equipmentInstances.get(baseInstanceId);
            if (base != null && base.maxDurability > 0) {
                durabilityRatio = Math.max(0.0, Math.min(1.0,
                        base.currentDurability / (double) base.maxDurability));
            }
        }
        String outputSignature = CraftTransactionPolicy.outputSignature(recipeId, outputType, outputId,
                outputAmount, outputInstanceId);
        double savedDurabilityRatio = durabilityRatio;
        ResourceLedger.ReserveResult reserved = runs.reserveResourceTransaction(transactionId,
                recipeId, outputId, ResourceLedger.Scope.PERSONAL, owner, resourceCosts, run -> {
                    RunSnapshot.ResourceTransactionState transaction = run.resourceTransactions.get(transactionId);
                    CraftTransactionPolicy.stamp(transaction, inputSignature, outputType, outputId,
                            outputAmount, outputInstanceId, baseInstanceId, savedDurabilityRatio);
                });
        if (reserved != ResourceLedger.ReserveResult.RESERVED) {
            player.sendMessage(ChatColor.RED + "제작 재료 예약 실패: " + reserved);
            return;
        }
        removeCraftInputs(inventory, consumedByGridSlot);
        player.saveData();
        if (!runs.beginResourceTransaction(transactionId)) {
            player.sendMessage(ChatColor.YELLOW + "제작 재료가 예약되었습니다. 재접속 시 같은 출력으로 재개됩니다.");
            return;
        }
        RunSnapshot.ResourceTransactionState transaction = runs.current().orElseThrow()
                .resourceTransactions.get(transactionId);
        if (!commitCraftOutput(player, transaction)) return;
        telemetry.event(runs.current().orElseThrow().runId, "CRAFT_COMMITTED",
                "{\"recipeId\":\"" + recipeId + "\",\"transactionId\":\"" + transactionId
                        + "\",\"outputSignature\":\"" + outputSignature + "\"}");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.4f);
    }

    private boolean commitCraftOutput(Player player, RunSnapshot.ResourceTransactionState transaction) {
        if (transaction == null || !CraftTransactionPolicy.KIND.equals(transaction.transactionKind)) return false;
        if ("COMMITTED".equals(transaction.state)) {
            materializeCommittedCraftOutput(player, transaction);
            return true;
        }
        if (!"PROCESSING".equals(transaction.state)) return false;
        if ("VIRTUAL".equals(transaction.outputType)) {
            RunSnapshot run = runs.current().orElseThrow();
            if (!run.committedKeys.contains("proof:" + transaction.outputId)) {
                if (!virtualPreflight.test(player, transaction.outputId)) return false;
                virtualCommit.accept(player, transaction.outputId);
            }
        }
        int physicalBefore = codex.countItem(player, transaction.outputId);
        RunSnapshot.EquipmentInstanceState prepared = null;
        if ("EQUIPMENT".equals(transaction.outputType)) {
            prepared = equipment.prepareCraftedInstance(transaction.outputId,
                    transaction.outputInstanceId, transaction.outputDurabilityRatio);
        }
        RunSnapshot.EquipmentInstanceState equipmentOutput = prepared;
        boolean committed = runs.commitResourceTransaction(transaction.transactionId,
                "CRAFT_COMMITTED", "{\"recipeId\":\"" + transaction.costId
                        + "\",\"outputSignature\":\"" + transaction.outputSignature + "\"}", run -> {
                    RunSnapshot.ResourceTransactionState candidateTransaction = run.resourceTransactions
                            .get(transaction.transactionId);
                    CraftTransactionPolicy.applyOutput(run.players.get(candidateTransaction.ownerUuid),
                            candidateTransaction, physicalBefore, equipmentOutput);
                });
        if (!committed) {
            player.sendMessage(ChatColor.YELLOW + "제작 출력이 처리 중입니다. 재접속 시 자동 복구됩니다.");
            return false;
        }
        RunSnapshot.ResourceTransactionState saved = runs.current().orElseThrow()
                .resourceTransactions.get(transaction.transactionId);
        materializeCommittedCraftOutput(player, saved);
        codex.discover(player, transaction.outputId, "CRAFT");
        return true;
    }

    private void materializeCommittedCraftOutput(Player player, RunSnapshot.ResourceTransactionState transaction) {
        switch (transaction.outputType) {
            case "MATERIAL" -> codex.materializeCommittedResource(player, transaction.outputId);
            case "ITEM" -> codex.flushPending(player);
            case "EQUIPMENT" -> equipment.reconcilePendingRewards(player);
            case "VIRTUAL" -> { }
            default -> throw new IllegalStateException("Unknown committed craft output " + transaction.outputType);
        }
    }

    private void removeCraftInputs(Inventory inventory, Map<Integer, Integer> consumedByGridSlot) {
        for (Map.Entry<Integer, Integer> consumed : consumedByGridSlot.entrySet()) {
            int inventorySlot = INPUTS[consumed.getKey()];
            ItemStack item = inventory.getItem(inventorySlot);
            if (item == null || item.getAmount() < consumed.getValue()) {
                throw new IllegalStateException("예약된 제작 입력이 사라졌습니다.");
            }
            item.setAmount(item.getAmount() - consumed.getValue());
            if (item.getAmount() <= 0) inventory.setItem(inventorySlot, null);
        }
    }

    private java.util.Optional<ProductionRecipePolicy.Match> productionMatch(Player player, Inventory inventory) {
        CraftHolder holder = inventory.getHolder() instanceof CraftHolder value ? value : null;
        List<ProductionRecipePolicy.GridCell> grid = productionGrid(inventory);
        String fingerprint = ProductionRecipePolicy.identityFingerprint(grid);
        if (holder != null && !fingerprint.equals(holder.gridFingerprint)) {
            holder.gridFingerprint = fingerprint;
            holder.selectedRecipeIndex = 0;
            holder.selectedRecipeId = null;
        }
        int selected = holder == null ? 0 : holder.selectedRecipeIndex;
        java.util.Optional<ProductionRecipePolicy.Match> result = ProductionRecipePolicy.match(production.recipes(), grid,
                this::tagValue, (proof, amount) -> proofPresent(player, proof, amount),
                holder == null ? null : holder.selectedRecipeId, selected);
        if (holder != null) result.ifPresent(match -> holder.selectedRecipeId = match.recipe().id());
        return result;
    }

    private List<ProductionRecipePolicy.GridCell> productionGrid(Inventory inventory) {
        List<ProductionRecipePolicy.GridCell> result = new ArrayList<>(9);
        for (int slot : INPUTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) result.add(ProductionRecipePolicy.GridCell.emptyCell());
            else result.add(new ProductionRecipePolicy.GridCell(codex.itemId(item), item.getType().name(), item.getAmount()));
        }
        return result;
    }

    private int tagValue(String tag, ProductionRecipePolicy.GridCell cell) {
        return RecipeTagCatalog.value(tag, cell.itemId());
    }

    private boolean proofPresent(Player player, String proof, int amount) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        if (codex.countItem(player, proof) >= amount || snapshot.resources.getOrDefault(proof, 0) >= amount) return true;
        if (snapshot.committedKeys.contains("proof:" + proof) || snapshot.committedKeys.contains(proof)) return true;
        if (proof.endsWith("_ACTIVE")) {
            String facilityType = proof.substring(0, proof.length() - "_ACTIVE".length());
            if (FacilityStateAccess.active(snapshot, facilityType)) return true;
            return snapshot.facility != null && snapshot.facility.active && facilityType.equals(snapshot.facility.id);
        }
        return false;
    }

    private int recipeFirstDay(ProductionContentCatalog.RecipeEntry recipe) {
        ProductionContentCatalog.CatalogEntry output = production.itemsById().get(recipe.outputId());
        if (output != null) return output.firstDay();
        String facilityType = recipe.outputId().contains("@")
                ? recipe.outputId().substring(0, recipe.outputId().indexOf('@')) : recipe.outputId();
        ProductionContentCatalog.FacilityEntry facility = production.facilitiesById().get(facilityType);
        if (facility != null) return facility.firstDay();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("D(\\d{1,2})").matcher(recipe.id());
        return matcher.find() ? Math.min(50, Integer.parseInt(matcher.group(1))) : 1;
    }

    private Material productionRewardMaterial(ProductionContentCatalog.RecipeEntry recipe) {
        ProductionContentCatalog.CatalogEntry output = production.itemsById().get(recipe.outputId());
        if (output == null) return Material.BEACON;
        Material material = Material.matchMaterial(output.displayMaterial());
        return material == null || material.isAir() ? Material.PAPER : material;
    }

    private String productionName(String outputId) {
        ProductionContentCatalog.CatalogEntry output = production.itemsById().get(outputId);
        return output == null ? outputId : output.name();
    }

    private boolean returnCraftInput(Player player, ItemStack returned) {
        ItemStack remaining = returned.clone();
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
        if (remaining.getAmount() <= 0) return false;
        org.bukkit.entity.Item dropped = player.getWorld().dropItem(player.getLocation(), remaining.clone());
        dropped.setOwner(player.getUniqueId());
        dropped.setPickupDelay(0);
        dropped.setUnlimitedLifetime(true);
        return true;
    }

    private void showClock(Player player) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        long now = runs.clockNowMillis();
        long dayStarted = snapshot.seasonDay == null ? snapshot.checkpointStartedAtEpochMs
                : snapshot.seasonDay.startedAtEpochMs;
        long elapsedSeconds = Math.max(0L, (now - dayStarted) / 1000L);
        long duration = Math.max(60L, plugin.getConfig().getLong("season.day-duration-seconds", 1200L));
        String next = snapshot.day >= 50 ? "최종 목표 단계 · 자동 완료 없음"
                : "다음 Day까지 최소 " + formatDuration(Math.max(0L, duration - elapsedSeconds));
        player.sendMessage(ChatColor.GOLD + "[생존 시계] Day " + snapshot.day + " · 경과 " + formatDuration(elapsedSeconds)
                + " · " + next + " · 상태 " + (snapshot.seasonDay == null ? "UNKNOWN" : snapshot.seasonDay.state));
    }

    private static String formatDuration(long seconds) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private List<String> grid(Inventory inventory) {
        List<String> result = new ArrayList<>(9);
        for (int slot : INPUTS) result.add(codex.itemId(inventory.getItem(slot)));
        return result;
    }

    private void transferLedger(Player player, String id, ClickType click, int page) {
        RunSnapshot snapshot = runs.current().orElse(null);
        boolean prototypeDepot = snapshot != null && snapshot.facility != null && snapshot.facility.active;
        boolean productionDepot = snapshot != null && FacilityStateAccess.active(snapshot, "FAC-S16");
        if (snapshot == null || !snapshot.sharedLedgerUnlocked || (!prototypeDepot && !productionDepot)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "공용 물류고가 비활성화되어 원장 거래를 중단했습니다.");
            return;
        }
        boolean deposit = click.isLeftClick();
        boolean withdraw = click.isRightClick();
        if (!deposit && !withdraw) return;
        if (deposit) {
            int amount = click.isShiftClick() ? codex.countResource(player, id) : 1;
            RunSnapshot.PlayerState owner = runs.playerState(player.getUniqueId()).orElseThrow();
            if (amount <= 0 || owner.personalResources.getOrDefault(id, 0) < amount) {
                player.sendMessage(ChatColor.RED + "개인 자원이 부족합니다."); return;
            }
            String key = "ledger-deposit:" + UUID.randomUUID();
            boolean committed = runs.commitOnceAtomically(key, "LEDGER_COMMITTED",
                    "{\"resource\":\"" + id + "\",\"direction\":\"PERSONAL_TO_SHARED\",\"amount\":" + amount + "}", run -> {
                        RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                        int personalAfter = state.personalResources.getOrDefault(id, 0) - amount;
                        if (personalAfter < 0) throw new IllegalStateException("Personal resource changed during deposit");
                        PersonalResourcePolicy.debit(state, id, amount, personalAfter, Map.of());
                        run.resources.merge(id, amount, Math::addExact);
                    });
            if (!committed) { player.sendMessage(ChatColor.RED + "공용 원장 입금이 커밋되지 않았습니다."); return; }
            codex.reconcilePersonalResources(player);
        } else {
            int balance = runs.current().orElseThrow().resources.getOrDefault(id, 0);
            int amount = click.isShiftClick() ? Math.min(64, balance) : 1;
            if (amount <= 0 || balance < amount) {
                player.sendMessage(ChatColor.RED + "공용 자원이 부족합니다."); return;
            }
            String key = "ledger-withdraw:" + UUID.randomUUID();
            boolean committed = runs.commitOnceAtomically(key, "LEDGER_COMMITTED",
                    "{\"resource\":\"" + id + "\",\"direction\":\"SHARED_TO_PERSONAL\",\"amount\":" + amount + "}", run -> {
                        int sharedAfter = run.resources.getOrDefault(id, 0) - amount;
                        if (sharedAfter < 0) throw new IllegalStateException("Shared resource changed during withdrawal");
                        run.resources.put(id, sharedAfter);
                        RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                        int personalAfter = Math.addExact(state.personalResources.getOrDefault(id, 0), amount);
                        PersonalResourcePolicy.credit(state, id, amount, personalAfter, Map.of());
                    });
            if (!committed) { player.sendMessage(ChatColor.RED + "공용 원장 출금이 커밋되지 않았습니다."); return; }
            codex.reconcilePersonalResources(player);
            codex.discover(player, id, "LEDGER_WITHDRAW");
        }
        openLedger(player, page);
    }

    private int countLogs(Player player) {
        int count = 0;
        for (int slot = 1; slot <= 35; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && Tag.LOGS.isTagged(item.getType())) count += item.getAmount();
        }
        return count;
    }

    private void takeLogs(Player player, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int remaining = amount;
        for (int i = 1; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !Tag.LOGS.isTagged(item.getType())) continue;
            int take = Math.min(remaining, item.getAmount()); item.setAmount(item.getAmount() - take); remaining -= take;
            if (item.getAmount() <= 0) contents[i] = null;
        }
        player.getInventory().setStorageContents(contents);
    }

    private ProductionContentCatalog.MaterialEntry findResource(Material material, int day) {
        return production.materialsById().values().stream()
                .filter(resource -> "HARVEST".equals(resource.acquisitionKind()) && resource.firstDay() <= day)
                .filter(resource -> resource.harvestSources().stream().anyMatch(source -> harvestSourceMatches(source, material)))
                .max(Comparator.comparingInt(ProductionContentCatalog.MaterialEntry::firstDay)
                        .thenComparingInt(resource -> resourceTier(resource.tier())))
                .orElse(null);
    }

    private boolean harvestSourceMatches(String source, Material material) {
        if ("#LOGS".equals(source)) return Tag.LOGS.isTagged(material);
        Material configured = Material.matchMaterial(source.toUpperCase(Locale.ROOT));
        return material.equals(configured);
    }

    private boolean toolMatchesSource(ItemStack tool, Material source) {
        String name = tool == null ? "AIR" : tool.getType().name();
        if (requiresPickaxe(source)) return name.endsWith("_PICKAXE");
        if (Tag.LOGS.isTagged(source)) return name.endsWith("_AXE");
        if (source == Material.COBWEB) return name.endsWith("_SWORD") || tool != null && tool.getType() == Material.SHEARS;
        return name.endsWith("_HOE") || tool != null && tool.getType() == Material.SHEARS;
    }

    private boolean requiresPickaxe(Material source) {
        String name = source.name();
        return name.endsWith("_ORE") || name.contains("STONE") || name.contains("DEEPSLATE")
                || source == Material.AMETHYST_CLUSTER;
    }

    private int toolTier(ItemStack tool) {
        if (tool == null || tool.getType().isAir()) return -1;
        String templateId = equipment.equipmentTemplateId(tool);
        if (templateId != null) {
            if (templateId.startsWith("EQL-UT-RI-")) return 3;
            if (templateId.startsWith("EQL-UT-RS-")) return 4;
            if (templateId.startsWith("EQL-UT-HD-")) return 5;
            if (templateId.startsWith("EQL-UT-RC-")) return 6;
            ProductionContentCatalog.CatalogEntry entry = production.itemsById().get(templateId);
            if (entry != null && "PK".equals(entry.equipmentType())) return dayToolTier(entry.firstDay());
        }
        String itemId = codex.itemId(tool);
        if (Set.of("TOOL-CRUDE-PICKAXE", "TOOL-CRUDE-AXE").contains(itemId)) return 0;
        if (Set.of("TOOL-STONE-PICKAXE", "TOOL-STONE-AXE").contains(itemId)) return 1;
        if (Set.of("TOOL-IRON-PICKAXE", "TOOL-IRON-AXE").contains(itemId)) return 2;
        String name = tool.getType().name();
        if (name.startsWith("WOODEN_") || name.startsWith("GOLDEN_")) return 0;
        if (name.startsWith("STONE_")) return 1;
        if (name.startsWith("IRON_") || name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_")
                || tool.getType() == Material.SHEARS) return 2;
        return -1;
    }

    private int dayToolTier(int firstDay) {
        if (firstDay >= 41) return 6;
        if (firstDay >= 31) return 5;
        if (firstDay >= 21) return 4;
        if (firstDay >= 11) return 3;
        return 2;
    }

    private int resourceTier(String tier) {
        if (tier == null || !tier.startsWith("T")) return 6;
        try { return Integer.parseInt(tier.substring(1)); }
        catch (NumberFormatException ignored) { return 6; }
    }

    private String toolTierName(int tier) {
        return switch (tier) {
            case 0 -> "목재";
            case 1 -> "석재";
            case 2 -> "철";
            case 3 -> "강화 철";
            case 4 -> "공명";
            case 5 -> "경화";
            default -> "재건";
        };
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

    static final class CraftHolder implements InventoryHolder {
        private final UUID owner;
        private int selectedRecipeIndex;
        private String selectedRecipeId;
        private String gridFingerprint = "";
        private CraftHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
    private static final class UnlockHolder implements InventoryHolder {
        private final UUID owner; private UnlockHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
    private static final class LedgerHolder implements InventoryHolder {
        private final UUID owner; private final int page;
        private final Map<Integer, String> resourceBySlot = new java.util.HashMap<>();
        private LedgerHolder(UUID owner, int page) { this.owner = owner; this.page = page; }
        @Override public Inventory getInventory() { return null; }
    }
}
