package com.lsc.corp.wsplugin.facility;

import com.lsc.corp.wsplugin.boss.ArenaManifestPolicy;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.economy.CostValuePolicy;
import com.lsc.corp.wsplugin.economy.ItemCodexService;
import com.lsc.corp.wsplugin.economy.ResourceLedger;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class FacilityService implements Listener {
    private static final String REVISION = "facility-data-d11-d50-r1";
    private static final Map<String, Integer> TYPE_LIMITS = Map.ofEntries(
            Map.entry("FAC-S10", 1), Map.entry("FAC-S13", 1), Map.entry("FAC-S19", 1), Map.entry("FAC-S20", 1),
            Map.entry("FAC-D02", 8), Map.entry("FAC-D03", 6), Map.entry("FAC-D04", 2),
            Map.entry("FAC-R01", 1), Map.entry("FAC-R02", 1), Map.entry("FAC-R03", 1),
            Map.entry("FAC-R04", 1), Map.entry("FAC-R05", 3), Map.entry("FAC-R06", 1));

    private final JavaPlugin plugin;
    private final RunService runs;
    private final ProductionContentCatalog production;
    private final ItemCodexService codex;
    private final EquipmentService equipment;
    private final TelemetryService telemetry;
    private final NamespacedKey instanceKey;
    private final NamespacedKey portableInstanceKey;
    private final Map<String, ProductionContentCatalog.FacilityEntry> facilityByItem = new HashMap<>();
    private Consumer<Player> craftOpener = ignored -> { };
    private Consumer<Player> ledgerOpener = ignored -> { };
    private Consumer<Player> codexOpener = ignored -> { };
    private Consumer<Player> statsOpener = ignored -> { };
    private Consumer<Player> researchOpener = ignored -> { };
    private Consumer<Player> augmentOpener = ignored -> { };
    private CombatService combat;
    private final Map<UUID, PendingVirtualBuild> pendingVirtualBuilds = new HashMap<>();
    private final Map<String, UUID> storageSessions = new HashMap<>();
    private long lastTrapTick;
    private long lastWorkTick;

    public FacilityService(JavaPlugin plugin, RunService runs, ProductionContentCatalog production,
                           ItemCodexService codex, EquipmentService equipment, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.production = production;
        this.codex = codex;
        this.equipment = equipment;
        this.telemetry = telemetry;
        this.instanceKey = new NamespacedKey(plugin, "facility_instance_id");
        this.portableInstanceKey = new NamespacedKey(plugin, "portable_instance_id");
        production.facilitiesById().values().stream().filter(value -> !value.itemId().isBlank())
                .forEach(value -> facilityByItem.put(value.itemId(), value));
    }

    public void setOpeners(Consumer<Player> craftOpener, Consumer<Player> ledgerOpener,
                           Consumer<Player> codexOpener, Consumer<Player> statsOpener,
                           Consumer<Player> researchOpener, Consumer<Player> augmentOpener) {
        this.craftOpener = Objects.requireNonNull(craftOpener);
        this.ledgerOpener = Objects.requireNonNull(ledgerOpener);
        this.codexOpener = Objects.requireNonNull(codexOpener);
        this.statsOpener = Objects.requireNonNull(statsOpener);
        this.researchOpener = Objects.requireNonNull(researchOpener);
        this.augmentOpener = Objects.requireNonNull(augmentOpener);
    }

    public void setCombatService(CombatService combat) {
        this.combat = Objects.requireNonNull(combat);
    }

    public void restore() {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null) return;
        long now = runs.clockNowMillis();
        List<String> expiredIds = new ArrayList<>();
        List<String> disabledIds = new ArrayList<>();
        for (RunSnapshot.FacilityInstanceState instance : new ArrayList<>(FacilityStateAccess.instances(run).values())) {
            if (FacilityStateAccess.expired(instance, now)) {
                if (instance.world != null) clearRepresentation(instance);
                expiredIds.add(instance.instanceId);
                continue;
            }
            if ("PORTABLE".equals(profile(instance).facilityTier()) && !"FAC-P06".equals(instance.facilityType)) continue;
            org.bukkit.World world = Bukkit.getWorld(instance.world);
            if (world == null) continue;
            Block block = world.getBlockAt(instance.x, instance.y, instance.z);
            Material expected = material(profile(instance));
            if (block.getType().isAir()) markRepresentation(block, expected, instance.instanceId);
            else if (block.getType() != expected) {
                disabledIds.add(instance.instanceId);
            } else markRepresentation(block, expected, instance.instanceId);
        }
        if (!expiredIds.isEmpty() || !disabledIds.isEmpty()) runs.mutate(snapshot -> {
            expiredIds.forEach(id -> FacilityStateAccess.instances(snapshot).remove(id));
            disabledIds.forEach(id -> {
                RunSnapshot.FacilityInstanceState instance = FacilityStateAccess.instances(snapshot).get(id);
                if (instance != null) instance.state = "DISABLED";
            });
        });
        recoverFacilityCostTransactions();
    }

    public String placeForTest(Player player, String rawFacilityId, int level) {
        if (!runs.isTestRun() || !runs.isRunningMember(player)) {
            throw new IllegalStateException("Test facilities require an active owned Test Lab run");
        }
        String facilityId = rawFacilityId.toUpperCase(java.util.Locale.ROOT);
        ProductionContentCatalog.FacilityEntry profile = production.facilitiesById().get(facilityId);
        if (profile == null || profile.portableDevice() || profile.reconstruction()) {
            throw new IllegalArgumentException("Test placement requires a non-portable normal facility: " + rawFacilityId);
        }
        if (level < 1 || level > profile.maxLevel()) {
            throw new IllegalArgumentException("Facility level must be 1 to " + profile.maxLevel());
        }
        Location target = testPlacement(player);
        String instanceId = "facility:" + runs.current().orElseThrow().runId + ":test:" + UUID.randomUUID();
        markRepresentation(target.getBlock(), material(profile), instanceId);
        RunSnapshot.FacilityInstanceState instance = createInstance(player, profile, instanceId, target, 0L);
        instance.level = level;
        instance.maxHp = profile.baseHp() * FacilityPolicy.hpMultiplier(level);
        instance.hp = instance.maxHp;
        runs.mutate(snapshot -> {
            FacilityStateAccess.instances(snapshot).put(instanceId, instance);
            if (snapshot.facilityTypesEverActivated == null) {
                snapshot.facilityTypesEverActivated = new java.util.LinkedHashSet<>();
            }
            snapshot.facilityTypesEverActivated.add(facilityId);
            if ("FAC-S16".equals(facilityId)) snapshot.sharedLedgerUnlocked = true;
        });
        recomputeNetworks();
        telemetry.event(runs.current().orElseThrow().runId, "TEST_FACILITY_PLACED", "{\"instanceId\":\""
                + instanceId + "\",\"facilityType\":\"" + facilityId + "\",\"level\":" + level + "}");
        player.sendMessage(ChatColor.GREEN + "[Test Lab] " + profile.name() + " Lv " + level
                + " 배치 · 우클릭으로 실제 시설 GUI를 여세요.");
        return instanceId;
    }

    private static Location testPlacement(Player player) {
        Block origin = player.getLocation().getBlock();
        for (int radius = 2; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    Block target = origin.getRelative(dx, 0, dz);
                    if (target.getType().isAir()
                            && target.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
                        return target.getLocation();
                    }
                }
            }
        }
        throw new IllegalStateException("주변 6블록 안에 시설 테스트용 바닥 위 빈 공간이 없습니다.");
    }

    private void recoverFacilityCostTransactions() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state)) return;
        List<String> transactionIds = snapshot.resourceTransactions.values().stream()
                .filter(transaction -> transaction.costId != null && transaction.costId.startsWith("FCOST-")
                        && ("RESERVED".equals(transaction.state) || "PROCESSING".equals(transaction.state)))
                .map(transaction -> transaction.transactionId).toList();
        for (String transactionId : transactionIds) {
            RunSnapshot.ResourceTransactionState transaction = runs.current().orElseThrow()
                    .resourceTransactions.get(transactionId);
            RunSnapshot.FacilityInstanceState instance = transaction == null ? null
                    : FacilityStateAccess.instances(runs.current().orElseThrow()).get(transaction.targetId);
            if (transaction == null) continue;
            if (instance == null) {
                failOrRefundFacilityCost(transaction, "FACILITY_MISSING_DURING_RECOVERY");
                continue;
            }
            RunSnapshot.FacilityWorkState work = instance.queue.stream()
                    .filter(value -> transactionId.equals(value.workId)).findFirst().orElse(null);
            if (work == null || work.operation == null || !work.operation.startsWith("UPGRADE_L")) {
                failOrRefundFacilityCost(transaction, "FACILITY_WORK_MISSING_DURING_RECOVERY");
                continue;
            }
            int target;
            try {
                target = Integer.parseInt(work.operation.substring("UPGRADE_L".length()));
            } catch (NumberFormatException exception) {
                failOrRefundFacilityCost(transaction, "FACILITY_TARGET_INVALID_DURING_RECOVERY");
                continue;
            }
            if ("RESERVED".equals(transaction.state)) {
                runs.beginResourceTransaction(transactionId,
                        run -> facilityWork(run, instance.instanceId, transactionId).state = "PROCESSING");
            }
            ProductionContentCatalog.FacilityEntry profile = profile(instance);
            runs.commitResourceTransaction(transactionId, "FACILITY_UPGRADED_RECOVERED", "{\"instanceId\":\""
                    + instance.instanceId + "\",\"costId\":\"" + transaction.costId + "\",\"level\":"
                    + target + "}", run -> {
                        RunSnapshot.FacilityInstanceState current = FacilityStateAccess.instances(run)
                                .get(instance.instanceId);
                        if (current.level < target) {
                            double ratio = current.hp / Math.max(1.0, current.maxHp);
                            current.level = target;
                            current.maxHp = profile.baseHp() * FacilityPolicy.hpMultiplier(target);
                            current.hp = Math.max(1.0, current.maxHp * ratio);
                            current.state = FacilityPolicy.healthState(current.hp, current.maxHp);
                        }
                        facilityWork(run, instance.instanceId, transactionId).state = "COMPLETED";
                    });
        }
    }

    private void failOrRefundFacilityCost(RunSnapshot.ResourceTransactionState transaction, String reason) {
        if ("RESERVED".equals(transaction.state)) {
            runs.cancelResourceReservation(transaction.transactionId, reason);
            return;
        }
        runs.mutate(run -> run.resourceTransactions.get(transaction.transactionId).failureReason = reason);
        telemetry.event(runs.current().orElseThrow().runId, "FACILITY_COST_RECOVERY_FAILED",
                "{\"transactionId\":\"" + transaction.transactionId + "\",\"reason\":\"" + reason + "\"}");
    }

    public void tick() {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null || !"RUNNING".equals(run.state)) return;
        long now = runs.clockNowMillis();
        long workDelta = lastWorkTick == 0L ? 0L : Math.max(0L, Math.min(2_000L, now - lastWorkTick));
        lastWorkTick = now;
        List<String> expired = FacilityStateAccess.instances(run).values().stream()
                .filter(instance -> FacilityStateAccess.expired(instance, now)).map(instance -> instance.instanceId).toList();
        if (!expired.isEmpty()) {
            expired.stream().map(id -> FacilityStateAccess.instances(run).get(id)).filter(Objects::nonNull)
                    .forEach(this::clearRepresentation);
            runs.mutate(snapshot -> expired.forEach(id -> FacilityStateAccess.instances(snapshot).remove(id)));
        }
        for (RunSnapshot.FacilityInstanceState instance : FacilityStateAccess.instances(run).values()) {
            if (!"ACTIVE".equals(instance.state) || !"FAC-P05".equals(instance.facilityType)) continue;
            org.bukkit.World world = Bukkit.getWorld(instance.world);
            if (world != null) world.spawnParticle(Particle.END_ROD,
                    new Location(world, instance.x + 0.5, instance.y + 1.0, instance.z + 0.5), 3, 1.8, 0.5, 1.8, 0.01);
        }
        if (now - lastTrapTick >= 1000L) {
            lastTrapTick = now;
            tickDefenceFacilities(run);
        }
        if (workDelta > 0L) tickFacilityWork(workDelta);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !runs.isRunningMember(event.getPlayer())) return;
        if (!event.getAction().isRightClick()) return;
        String itemId = codex.itemId(event.getItem());
        ProductionContentCatalog.FacilityEntry facility = itemId == null ? null : facilityByItem.get(itemId);
        if (facility == null) return;
        event.setCancelled(true);
        if (facility.portableDevice()) executePortable(event.getPlayer(), facility, event.getItem(),
                event.getClickedBlock(), event.getBlockFace());
        else placeFromKit(event.getPlayer(), facility, event.getClickedBlock(), event.getBlockFace());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
                || !event.getAction().isRightClick() || !runs.isRunningMember(event.getPlayer())) return;
        RunSnapshot.FacilityInstanceState instance = instanceAt(event.getClickedBlock());
        if (instance == null) return;
        event.setCancelled(true);
        if ("FAC-P06".equals(instance.facilityType)) {
            recoverSignalStake(event.getPlayer(), instance);
            return;
        }
        open(event.getPlayer(), instance.instanceId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (instanceAt(event.getBlock()) == null) return;
        event.setCancelled(true);
        event.setDropItems(false);
        event.getPlayer().sendMessage(ChatColor.RED + "시설은 일반 채굴로 파괴·회수할 수 없습니다. 시설 GUI의 안전 철거를 사용하세요.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> instanceAt(block) != null)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> instanceAt(block) != null)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> instanceAt(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> instanceAt(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof StorageHolder holder) {
            if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> persistStorage(holder, event.getView().getTopInventory()));
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof FacilityHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        RunSnapshot.FacilityInstanceState instance = FacilityStateAccess.instances(runs.current().orElseThrow()).get(holder.instanceId);
        if (instance == null) { player.closeInventory(); return; }
        if (event.getRawSlot() == 20) executeFacility(player, instance);
        else if (event.getRawSlot() == 22) upgrade(player, instance,
                event.isRightClick() ? ResourceLedger.Scope.SHARED : ResourceLedger.Scope.PERSONAL);
        else if (event.getRawSlot() == 24) {
            recomputeNetworks();
            player.sendMessage(ChatColor.GREEN + "시설 네트워크 연결을 다시 계산했습니다.");
        } else if (event.getRawSlot() == 31 && event.isShiftClick()) dismantle(player, instance);
        else if (event.getRawSlot() == 49) player.closeInventory();
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof FacilityHolder) open(player, holder.instanceId);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof StorageHolder holder) {
            Bukkit.getScheduler().runTask(plugin, () -> persistStorage(holder, event.getView().getTopInventory()));
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof FacilityHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof StorageHolder holder) {
            persistStorage(holder, event.getView().getTopInventory());
            storageSessions.remove(holder.instanceId, holder.owner);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof StorageHolder holder) {
            persistStorage(holder, event.getPlayer().getOpenInventory().getTopInventory());
            storageSessions.remove(holder.instanceId, holder.owner);
        }
    }

    public void open(Player player, String instanceId) {
        RunSnapshot.FacilityInstanceState instance = FacilityStateAccess.instances(runs.current().orElseThrow()).get(instanceId);
        if (instance == null) { player.sendMessage(ChatColor.RED + "시설 인스턴스를 찾을 수 없습니다."); return; }
        ProductionContentCatalog.FacilityEntry profile = profile(instance);
        FacilityHolder holder = new FacilityHolder(player.getUniqueId(), instanceId);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_GREEN + profile.name());
        double hpRatio = instance.maxHp <= 0 ? 0.0 : instance.hp / instance.maxHp;
        inventory.setItem(4, named(material(profile), ChatColor.GOLD + profile.name(), List.of(
                ChatColor.WHITE + "Lv " + instance.level + "/" + profile.maxLevel() + " · " + instance.state,
                ChatColor.WHITE + "HP " + Math.round(instance.hp) + "/" + Math.round(instance.maxHp)
                        + " · " + Math.round(hpRatio * 100.0) + "%",
                ChatColor.GRAY + "망 " + instance.networkId + " · 위협 " + profile.threatValue(),
                ChatColor.GRAY + "작업 슬롯 " + FacilityPolicy.workSlots(profile.workSlots(), instance.level),
                ChatColor.DARK_GRAY + instance.instanceId)));
        List<String> functionLore = new ArrayList<>(List.of(ChatColor.WHITE + profile.effectText(),
                ChatColor.GRAY + profile.maintenanceText()));
        String workText = activeWorkText(instance);
        if (workText != null) functionLore.add(ChatColor.YELLOW + workText);
        inventory.setItem(20, named(Material.LIME_DYE, ChatColor.GREEN + "기능 실행", functionLore));
        if (instance.level < profile.maxLevel() && profile.costProfile().startsWith("FP-")) {
            int target = instance.level + 1;
            ProductionContentCatalog.FacilityCostEntry cost = levelCost(profile, target);
            inventory.setItem(22, named(Material.ANVIL, ChatColor.AQUA + "Lv " + target + " 업그레이드",
                    List.of(ChatColor.GRAY + costText(cost.cost()), dayReadyText(target),
                            ChatColor.YELLOW + "좌클릭 개인 / 우클릭 공용 결제")));
        } else inventory.setItem(22, named(Material.GRAY_DYE, ChatColor.GRAY + "업그레이드 없음", List.of()));
        inventory.setItem(24, named(Material.COMPASS, ChatColor.YELLOW + "네트워크 재검사",
                List.of(ChatColor.GRAY + "설치·철거 시 자동 계산되며 수동 갱신도 가능합니다.")));
        inventory.setItem(31, named(Material.STRUCTURE_VOID, ChatColor.RED + "안전 철거",
                List.of(ChatColor.YELLOW + "Shift+클릭 필요", ChatColor.GRAY + "키트는 복구 원장에 보존됩니다.")));
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
        player.openInventory(inventory);
    }

    public void extendPortablePurifier(Player player, long extensionMillis) {
        RunSnapshot run = runs.current().orElseThrow();
        RunSnapshot.FacilityInstanceState purifier = FacilityStateAccess.instances(run).values().stream()
                .filter(value -> "FAC-P05".equals(value.facilityType) && player.getUniqueId().toString().equals(value.installedBy)
                        && "ACTIVE".equals(value.state) && !FacilityStateAccess.expired(value, runs.clockNowMillis()))
                .min(Comparator.comparingDouble((RunSnapshot.FacilityInstanceState value) ->
                                distanceSquared(player.getLocation(), value))
                        .thenComparing(value -> value.instanceId)).orElse(null);
        if (purifier == null) throw new IllegalStateException("가동 중인 FAC-P05가 없습니다.");
        runs.mutate(snapshot -> {
            RunSnapshot.FacilityInstanceState current = FacilityStateAccess.instances(snapshot).get(purifier.instanceId);
            current.expiresAtEpochMs = Math.max(current.expiresAtEpochMs, runs.clockNowMillis()) + extensionMillis;
        });
    }

    public boolean hasActivePortablePurifier(Player player) {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null) return false;
        String owner = player.getUniqueId().toString();
        long now = runs.clockNowMillis();
        return FacilityStateAccess.instances(run).values().stream().anyMatch(value -> "FAC-P05".equals(value.facilityType)
                && owner.equals(value.installedBy) && "ACTIVE".equals(value.state)
                && !FacilityStateAccess.expired(value, now));
    }

    public boolean canAssembleVirtual(Player player, String outputId) {
        String facilityType = facilityType(outputId);
        ProductionContentCatalog.FacilityEntry profile = production.facilitiesById().get(facilityType);
        if (profile == null || !profile.reconstruction()) {
            player.sendMessage(ChatColor.RED + "가상 건설 출력이 재건 시설이 아닙니다: " + outputId);
            return false;
        }
        RunSnapshot run = runs.current().orElseThrow();
        if (run.day < profile.firstDay()) {
            player.sendMessage(ChatColor.RED + "Day " + profile.firstDay() + "부터 조립할 수 있습니다.");
            return false;
        }
        int count = "FAC-R05".equals(facilityType) ? 3 : 1;
        long existing = FacilityStateAccess.instances(run).values().stream()
                .filter(value -> facilityType.equals(value.facilityType)).count();
        if (existing + count > TYPE_LIMITS.getOrDefault(facilityType, 1)) {
            player.sendMessage(ChatColor.RED + "이미 필요한 수량의 " + profile.name() + "이 회차에 등록되어 있습니다.");
            return false;
        }
        List<Location> targets = virtualBuildTargets(player, count);
        if (targets.size() != count) {
            player.sendMessage(ChatColor.RED + "주변에 재건 시설을 배치할 안전한 빈 공간이 부족합니다.");
            return false;
        }
        pendingVirtualBuilds.put(player.getUniqueId(), new PendingVirtualBuild(outputId, targets));
        return true;
    }

    public void assembleVirtual(Player player, String outputId) {
        PendingVirtualBuild pending = pendingVirtualBuilds.remove(player.getUniqueId());
        if (pending == null || !pending.outputId.equals(outputId)) {
            throw new IllegalStateException("재건 시설 사전 배치 검사가 만료되었습니다.");
        }
        String facilityType = facilityType(outputId);
        ProductionContentCatalog.FacilityEntry profile = production.facilitiesById().get(facilityType);
        RunSnapshot run = runs.current().orElseThrow();
        List<RunSnapshot.FacilityInstanceState> created = new ArrayList<>();
        for (Location target : pending.targets) {
            String instanceId = "facility:" + run.runId + ":" + UUID.randomUUID();
            markRepresentation(target.getBlock(), material(profile), instanceId);
            RunSnapshot.FacilityInstanceState instance = createInstance(player, profile, instanceId, target, 0L);
            instance.state = FacilityPolicy.initialReconstructionState(facilityType);
            created.add(instance);
        }
        runs.mutate(snapshot -> {
            for (RunSnapshot.FacilityInstanceState instance : created) {
                FacilityStateAccess.instances(snapshot).put(instance.instanceId, instance);
            }
            if (snapshot.facilityTypesEverActivated == null) snapshot.facilityTypesEverActivated = new java.util.LinkedHashSet<>();
            snapshot.facilityTypesEverActivated.add(facilityType);
            snapshot.committedKeys.add("proof:" + outputId);
        });
        recomputeNetworks();
        telemetry.event(run.runId, "RECONSTRUCTION_ASSEMBLED", "{\"facilityType\":\""
                + facilityType + "\",\"count\":" + created.size() + "}");
    }

    private void executePortable(Player player, ProductionContentCatalog.FacilityEntry profile, ItemStack sourceItem,
                                 Block clicked, org.bukkit.block.BlockFace face) {
        String portableInstanceId = "FAC-P06".equals(profile.id())
                ? null : ensurePortableInstanceId(sourceItem);
        switch (profile.effectOpcode()) {
            case "CRAFT_PORTABLE" -> craftOpener.accept(player);
            case "REPAIR_FIELD" -> {
                if (!equipment.canRepairEquipped(player)) { player.sendMessage(ChatColor.YELLOW + "수리가 필요한 장비가 없습니다."); return; }
                if (!codex.takeItem(player, profile.itemId(), 1)) return;
                if (!equipment.repairMostDamagedWithConsumedKit(player)) codex.grantItem(player, profile.itemId(), 1);
            }
            case "SAMPLE_EXTRACT" -> player.sendMessage(ChatColor.AQUA + "표본 채취 모드: 적·오염·광물 상호작용 데이터가 도감에 기록됩니다.");
            case "ANALYZE_PORTABLE" -> showAnalysis(player);
            case "PURIFY_PORTABLE" -> activateTimedPortable(player, profile, portableInstanceId, 60_000L, 5.0);
            case "SIGNAL_STAKE" -> placeSignalStake(player, profile, clicked, face);
            case "RESCUE_BEACON" -> activateTimedPortable(player, profile, portableInstanceId, 300_000L, 12.0);
            case "LEDGER_REMOTE" -> ledgerOpener.accept(player);
            default -> player.sendMessage(ChatColor.RED + "지원하지 않는 휴대 시설 기능입니다: " + profile.effectOpcode());
        }
    }

    private void executeFacility(Player player, RunSnapshot.FacilityInstanceState instance) {
        ProductionContentCatalog.FacilityEntry profile = profile(instance);
        if (!"ACTIVE".equals(instance.state) && !profile.reconstruction()) {
            player.sendMessage(ChatColor.RED + "ACTIVE 상태의 시설만 사용할 수 있습니다."); return;
        }
        runs.mutate(run -> FacilityStateAccess.instances(run).get(instance.instanceId).lastUsedAtEpochMs = runs.clockNowMillis());
        switch (profile.effectOpcode()) {
            case "CRAFT_BASIC", "CRAFT_ADVANCED", "SMELT", "AMMO_BASIC", "AMMO_ADVANCED" -> craftOpener.accept(player);
            case "REPAIR_FULL" -> {
                if (!equipment.repairMostDamagedFull(player)) player.sendMessage(ChatColor.YELLOW + "완전 수리할 장비가 없습니다.");
            }
            case "RESEARCH" -> researchOpener.accept(player);
            case "PATTERN_ANALYZE", "SALVAGE" -> codexOpener.accept(player);
            case "TRAINING" -> statsOpener.accept(player);
            case "SHARED_LEDGER" -> ledgerOpener.accept(player);
            case "REST", "MEDICAL_BASIC", "MEDICAL", "PURIFY" -> treat(player, profile.effectOpcode());
            case "FORECAST", "ALERT", "ASSAULT_OBSERVE" -> showAnalysis(player);
            case "TRAVEL" -> travel(player, instance);
            case "SESSION_RELAY" -> player.sendMessage(ChatColor.AQUA + "안전 중단·체크포인트는 /ws 명령에서 파티 투표로 요청합니다.");
            case "REBUILD_POWER" -> startTimedReconstruction(player, instance);
            case "REBUILD_LENS" -> requireReconstructionEvidence(player, instance,
                    "세 방향 오차 시험 결과가 아직 연결되지 않았습니다.");
            case "REBUILD_PURIFY" -> requireReconstructionEvidence(player, instance,
                    "환경 2종 반응 시험 결과가 아직 연결되지 않았습니다.");
            case "REBUILD_FINAL" -> activateFinalFacility(player, instance);
            case "PURIFY_RELAY", "ENVIRONMENT_SHIELD" -> player.sendMessage(ChatColor.GREEN
                    + profile.name() + " 보호 범위가 시설망에 적용 중입니다.");
            case "AUGMENT_MANAGE" -> augmentOpener.accept(player);
            case "STORAGE" -> openStorage(player, instance);
            case "SLOW_TRAP", "IMPACT_TRAP" -> player.sendMessage(ChatColor.GREEN + profile.name()
                    + " 잔여 발동 " + instance.triggerCharges + "회");
            case "REBUILD_FRAME" -> startTimedReconstruction(player, instance);
            case "REBUILD_STAKES" -> requireReconstructionEvidence(player, instance,
                    "세 말뚝 순차 교정 결과가 아직 연결되지 않았습니다.");
            case "REFORGE", "GRAVE_RECOVERY", "POWER_DISTRIBUTE", "BARRICADE", "WALL_REGISTER",
                    "TAUNT_BEACON" -> player.sendMessage(ChatColor.RED + profile.name()
                    + "은(는) 구체 자원 ID 또는 대상 등록 계약이 미확정되어 BLOCKED_DATA 상태입니다.");
            default -> player.sendMessage(ChatColor.RED + "지원하지 않는 시설 기능입니다: " + profile.effectOpcode());
        }
        telemetry.event(runs.current().orElseThrow().runId, "FACILITY_USED", "{\"instanceId\":\""
                + instance.instanceId + "\",\"opcode\":\"" + profile.effectOpcode() + "\"}");
    }

    private void placeFromKit(Player player, ProductionContentCatalog.FacilityEntry profile,
                              Block clicked, org.bukkit.block.BlockFace face) {
        RunSnapshot run = runs.current().orElseThrow();
        if (run.day < profile.firstDay()) { player.sendMessage(ChatColor.RED + "Day " + profile.firstDay() + "부터 설치할 수 있습니다."); return; }
        if (!safeForFacilityAction(player)) { player.sendMessage(ChatColor.RED + "최근 피격·보스 전투 중에는 시설을 설치할 수 없습니다."); return; }
        if (clicked == null || face == null) { player.sendMessage(ChatColor.RED + "설치할 블록 면을 우클릭하세요."); return; }
        Block target = clicked.getRelative(face);
        if (!target.getType().isAir() || !target.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
            player.sendMessage(ChatColor.RED + "바닥 위 빈 공간에만 설치할 수 있습니다."); return;
        }
        long sameType = FacilityStateAccess.instances(run).values().stream().filter(value -> profile.id().equals(value.facilityType)).count();
        if (sameType >= TYPE_LIMITS.getOrDefault(profile.id(), Integer.MAX_VALUE)) {
            player.sendMessage(ChatColor.RED + "이 시설 유형의 설치 상한에 도달했습니다."); return;
        }
        if ("DEFENSE".equals(profile.facilityTier())) {
            long defenceCount = FacilityStateAccess.instances(run).values().stream()
                    .filter(value -> "DEFENSE".equals(profile(value).facilityTier())).count();
            if (defenceCount >= FacilityPolicy.facilityLimit("DEFENSE")) {
                player.sendMessage(ChatColor.RED + "방어 시설 설치 상한 64개에 도달했습니다."); return;
            }
        } else if ("SETTLEMENT".equals(profile.facilityTier())) {
            String candidateNetwork = networkFor(profile, target.getLocation());
            long networkCount = FacilityStateAccess.instances(run).values().stream()
                    .filter(value -> candidateNetwork.equals(value.networkId))
                    .filter(value -> !"DEFENSE".equals(profile(value).facilityTier())).count();
            if (networkCount >= FacilityPolicy.facilityLimit(profile.facilityTier())) {
                player.sendMessage(ChatColor.RED + "해당 시설망의 활성 기능 시설 상한 24개에 도달했습니다."); return;
            }
        }
        if (codex.countItem(player, profile.itemId()) < 1) return;
        String instanceId = "facility:" + run.runId + ":" + UUID.randomUUID();
        Material original = target.getType();
        try {
            if (!codex.takeItem(player, profile.itemId(), 1)) return;
            markRepresentation(target, material(profile), instanceId);
            RunSnapshot.FacilityInstanceState instance = createInstance(player, profile, instanceId, target.getLocation(), 0L);
            runs.mutate(snapshot -> {
                FacilityStateAccess.instances(snapshot).put(instanceId, instance);
                if (snapshot.facilityTypesEverActivated == null) snapshot.facilityTypesEverActivated = new java.util.LinkedHashSet<>();
                snapshot.facilityTypesEverActivated.add(profile.id());
                if ("FAC-S16".equals(profile.id())) snapshot.sharedLedgerUnlocked = true;
            });
            recomputeNetworks();
            runs.broadcast(ChatColor.GREEN + profile.name() + " 설치: " + target.getX() + ", " + target.getY() + ", " + target.getZ());
            telemetry.event(run.runId, "FACILITY_PLACED", "{\"instanceId\":\"" + instanceId
                    + "\",\"facilityType\":\"" + profile.id() + "\"}");
        } catch (RuntimeException exception) {
            target.setType(original, false);
            codex.grantItem(player, profile.itemId(), 1);
            throw exception;
        }
    }

    private void startTimedReconstruction(Player player, RunSnapshot.FacilityInstanceState instance) {
        if ("READY".equals(instance.state)) {
            player.sendMessage(ChatColor.GREEN + profile(instance).name() + " 작업은 이미 완료되었습니다.");
            return;
        }
        long duration = FacilityPolicy.reconstructionDurationMillis(instance.facilityType);
        if (duration <= 0L) {
            requireReconstructionEvidence(player, instance, "자동 시간 작업이 정의되지 않았습니다.");
            return;
        }
        RunSnapshot.FacilityWorkState existing = instance.queue == null ? null : instance.queue.stream()
                .filter(work -> work.operation.equals(profile(instance).effectOpcode())
                        && !List.of("COMPLETED", "CLAIMED", "CANCELLED").contains(work.state))
                .findFirst().orElse(null);
        if (existing != null) {
            player.sendMessage(ChatColor.YELLOW + "작업 진행 " + existing.processedMillis / 1000 + "/"
                    + existing.durationMillis / 1000 + "초");
            return;
        }
        RunSnapshot.FacilityWorkState work = new RunSnapshot.FacilityWorkState();
        work.workId = "facility-work:" + runs.current().orElseThrow().runId + ":" + UUID.randomUUID();
        work.operation = profile(instance).effectOpcode();
        work.ownerUuid = player.getUniqueId().toString();
        work.state = "PROCESSING";
        work.queuedAtEpochMs = runs.clockNowMillis();
        work.processingStartedAtEpochMs = work.queuedAtEpochMs;
        work.durationMillis = duration;
        runs.mutate(run -> {
            RunSnapshot.FacilityInstanceState current = FacilityStateAccess.instances(run).get(instance.instanceId);
            if (current.queue == null) current.queue = new ArrayList<>();
            current.queue.add(work);
            current.state = "FAC-R01".equals(current.facilityType) ? "ASSEMBLED" : "TESTING";
        });
        player.sendMessage(ChatColor.GREEN + profile(instance).name() + " 작업 시작 · " + duration / 1000 + "초");
    }

    private void requireReconstructionEvidence(Player player, RunSnapshot.FacilityInstanceState instance, String reason) {
        player.sendMessage(ChatColor.RED + profile(instance).name() + " BLOCKED_EVIDENCE · " + reason);
    }

    private void activateFinalFacility(Player player, RunSnapshot.FacilityInstanceState instance) {
        ProductionContentCatalog.FacilityEntry profile = profile(instance);
        int day = runs.current().orElseThrow().day;
        if (day < profile.activationDay()) {
            player.sendMessage(ChatColor.RED + "Day " + profile.activationDay() + " 전에는 READY_LOCKED를 해제할 수 없습니다.");
            return;
        }
        RunSnapshot run = runs.current().orElseThrow();
        boolean systemsReady = List.of("FAC-R01", "FAC-R02", "FAC-R03", "FAC-R04").stream()
                .allMatch(type -> FacilityStateAccess.instances(run).values().stream()
                        .anyMatch(value -> type.equals(value.facilityType) && "READY".equals(value.state)));
        long stakes = FacilityStateAccess.instances(run).values().stream()
                .filter(value -> "FAC-R05".equals(value.facilityType) && "CALIBRATED".equals(value.state)).count();
        if (!systemsReady || stakes < 3) {
            player.sendMessage(ChatColor.RED + "R01~R04 READY와 교정 말뚝 3개 CALIBRATED가 필요합니다.");
            return;
        }
        runs.mutate(snapshot -> {
            RunSnapshot.FacilityInstanceState current = FacilityStateAccess.instances(snapshot).get(instance.instanceId);
            current.state = "READY";
            snapshot.committedKeys.add("proof:FAC-R06_READY");
        });
        player.sendMessage(ChatColor.GREEN + "첫 재건 장치가 READY 상태로 전환되었습니다.");
    }

    private void placeSignalStake(Player player, ProductionContentCatalog.FacilityEntry profile,
                                  Block clicked, org.bukkit.block.BlockFace face) {
        RunSnapshot run = runs.current().orElseThrow();
        if (run.day < profile.firstDay()) {
            player.sendMessage(ChatColor.RED + "Day " + profile.firstDay() + "부터 설치할 수 있습니다."); return;
        }
        if (!safeForFacilityAction(player)) {
            player.sendMessage(ChatColor.RED + "최근 피격·보스 전투 중에는 신호 말뚝을 설치할 수 없습니다."); return;
        }
        if (clicked == null || face == null) { player.sendMessage(ChatColor.RED + "말뚝을 설치할 블록 면을 우클릭하세요."); return; }
        Block target = clicked.getRelative(face);
        if (!target.getType().isAir() || !target.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
            player.sendMessage(ChatColor.RED + "바닥 위 빈 공간에만 신호 말뚝을 설치할 수 있습니다."); return;
        }
        if (codex.countItem(player, profile.itemId()) < 1) return;
        String portableInstanceId = UUID.randomUUID().toString();
        String instanceId = "portable:" + run.runId + ":" + portableInstanceId;
        Material original = target.getType();
        try {
            if (!codex.takeItem(player, profile.itemId(), 1)) return;
            markRepresentation(target, material(profile), instanceId);
            RunSnapshot.FacilityInstanceState instance = createInstance(player, profile, instanceId, target.getLocation(), 0L);
            instance.portableInstanceId = portableInstanceId;
            runs.mutate(snapshot -> FacilityStateAccess.instances(snapshot).put(instanceId, instance));
            player.sendMessage(ChatColor.GREEN + "신호 말뚝 설치 완료 · 활성 말뚝 "
                    + activePortableCount("FAC-P06") + "개. 말뚝을 우클릭하면 회수합니다.");
            previewArenaTriangle(player);
        } catch (RuntimeException exception) {
            target.setType(original, false);
            codex.grantItem(player, profile.itemId(), 1);
            throw exception;
        }
    }

    private void recoverSignalStake(Player player, RunSnapshot.FacilityInstanceState instance) {
        if (!player.getUniqueId().toString().equals(instance.installedBy)) {
            player.sendMessage(ChatColor.RED + "배치한 플레이어만 신호 말뚝을 회수할 수 있습니다."); return;
        }
        clearRepresentation(instance);
        runs.mutate(run -> FacilityStateAccess.instances(run).remove(instance.instanceId));
        codex.grantItem(player, "WSI-PORTABLE-SIGNAL_STAKE", 1);
        player.sendMessage(ChatColor.GREEN + "신호 말뚝을 회수했습니다.");
    }

    private void activateTimedPortable(Player player, ProductionContentCatalog.FacilityEntry profile,
                                       String portableInstanceId, long durationMillis, double radius) {
        if (portableInstanceId == null || portableInstanceId.isBlank()) {
            throw new IllegalStateException("휴대 장치 인스턴스 ID를 만들 수 없습니다.");
        }
        RunSnapshot run = runs.current().orElseThrow();
        String instanceId = "portable:" + run.runId + ":" + portableInstanceId;
        RunSnapshot.FacilityInstanceState existing = FacilityStateAccess.instances(run).get(instanceId);
        if (existing != null && "ACTIVE".equals(existing.state)
                && !FacilityStateAccess.expired(existing, runs.clockNowMillis())) {
            player.sendMessage(ChatColor.YELLOW + profile.name() + "은 이미 가동 중입니다. 충전 아이템으로 시간을 연장하세요.");
            return;
        }
        Location location = player.getLocation();
        RunSnapshot.FacilityInstanceState activated = createInstance(player, profile, instanceId, location,
                runs.clockNowMillis() + durationMillis);
        activated.portableInstanceId = portableInstanceId;
        runs.mutate(snapshot -> FacilityStateAccess.instances(snapshot).put(instanceId, activated));
        if ("FAC-P05".equals(profile.id())) {
            for (LivingEntity entity : location.getNearbyLivingEntities(radius)) if (entity instanceof Player member
                    && runs.isMember(member)) removeNegativeEffects(member);
        }
        player.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.4f);
        player.sendMessage(ChatColor.GREEN + profile.name() + " 활성화 · " + durationMillis / 1000 + "초");
    }

    private String ensurePortableInstanceId(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        String existing = meta.getPersistentDataContainer().get(portableInstanceKey, PersistentDataType.STRING);
        if (existing != null && !existing.isBlank()) return existing;
        String created = UUID.randomUUID().toString();
        meta.getPersistentDataContainer().set(portableInstanceKey, PersistentDataType.STRING, created);
        item.setItemMeta(meta);
        return created;
    }

    private long activePortableCount(String facilityType) {
        RunSnapshot run = runs.current().orElseThrow();
        return FacilityStateAccess.instances(run).values().stream()
                .filter(value -> facilityType.equals(value.facilityType) && "ACTIVE".equals(value.state)).count();
    }

    private void previewArenaTriangle(Player player) {
        RunSnapshot run = runs.current().orElseThrow();
        String callItemId = switch (run.day) {
            case 10 -> "WSI-CALL-D10";
            case 20 -> "WSI-CALL-D20";
            case 30 -> "WSI-CALL-D30";
            case 40 -> "WSI-CALL-D40";
            default -> null;
        };
        if (callItemId == null || activePortableCount("FAC-P06") < 3) return;
        List<ArenaManifestPolicy.Stake> stakes = FacilityStateAccess.instances(run).values().stream()
                .filter(value -> "FAC-P06".equals(value.facilityType))
                .map(value -> new ArenaManifestPolicy.Stake(value.instanceId, value.world,
                        value.x + 0.5, value.y, value.z + 0.5, value.state)).toList();
        ArenaManifestPolicy.Selection selection = ArenaManifestPolicy.select(callItemId, stakes);
        if (selection.accepted()) {
            ArenaManifestPolicy.Candidate candidate = selection.candidate();
            player.sendMessage(ChatColor.AQUA + "전장 삼각 측정 통과 · 중심 "
                    + Math.round(candidate.x()) + ", " + Math.round(candidate.y()) + ", "
                    + Math.round(candidate.z()) + " · 지형 스캔 전 상태");
        } else {
            player.sendMessage(ChatColor.YELLOW + "말뚝 3개 이상이 있으나 현재 Day의 거리 계약을 만족하는 조합이 없습니다.");
        }
    }

    private static double distanceSquared(Location location, RunSnapshot.FacilityInstanceState instance) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(instance.world)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = location.getX() - (instance.x + 0.5);
        double dy = location.getY() - (instance.y + 0.5);
        double dz = location.getZ() - (instance.z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private RunSnapshot.FacilityInstanceState createInstance(Player player,
            ProductionContentCatalog.FacilityEntry profile, String instanceId, Location location, long expiry) {
        RunSnapshot.FacilityInstanceState instance = new RunSnapshot.FacilityInstanceState();
        instance.instanceId = instanceId;
        instance.facilityType = profile.id();
        instance.revision = REVISION;
        instance.level = 1;
        instance.state = "ACTIVE";
        instance.hp = profile.baseHp();
        instance.maxHp = profile.baseHp();
        instance.networkId = networkFor(profile, location);
        instance.world = location.getWorld().getName();
        instance.x = location.getBlockX(); instance.y = location.getBlockY(); instance.z = location.getBlockZ();
        instance.coreMaterial = material(profile).name();
        instance.installedBy = player.getUniqueId().toString();
        instance.installedAtEpochMs = runs.clockNowMillis();
        instance.lastUsedAtEpochMs = instance.installedAtEpochMs;
        instance.expiresAtEpochMs = expiry;
        instance.triggerCharges = profile.id().equals("FAC-D02") ? 12 : profile.id().equals("FAC-D03") ? 8 : 0;
        return instance;
    }

    private void upgrade(Player player, RunSnapshot.FacilityInstanceState instance, ResourceLedger.Scope scope) {
        ProductionContentCatalog.FacilityEntry profile = profile(instance);
        int target = instance.level + 1;
        if (target > profile.maxLevel() || !profile.costProfile().startsWith("FP-")) return;
        int requiredDay = switch (target) { case 2 -> 18; case 3 -> 28; case 4 -> 38; default -> 44; };
        if (runs.current().orElseThrow().day < requiredDay) {
            player.sendMessage(ChatColor.RED + "Lv " + target + " 업그레이드는 Day " + requiredDay + "부터 가능합니다."); return;
        }
        ProductionContentCatalog.FacilityCostEntry cost = levelCost(profile, target);
        if (!CostValuePolicy.runtimeDebitRequired(cost.paymentMode(), target)) {
            player.sendMessage(ChatColor.RED + "Lv2 이상 시설 비용 레코드가 RESOURCE_VALUE가 아닙니다: " + cost.id());
            return;
        }
        RunSnapshot run = runs.current().orElseThrow();
        String owner = player.getUniqueId().toString();
        if (scope == ResourceLedger.Scope.SHARED && (!run.sharedLedgerUnlocked
                || !FacilityStateAccess.active(run, "FAC-S16"))) {
            player.sendMessage(ChatColor.RED + "공용 결제에는 활성 FAC-S16이 필요합니다.");
            return;
        }
        Map<String, Integer> balance = scope == ResourceLedger.Scope.SHARED
                ? run.resources : run.players.get(owner).personalResources;
        Map<String, Integer> spend = CostValuePolicy.plan(cost.cost(), partySize(), balance);
        if (spend == null) {
            player.sendMessage(ChatColor.RED + "정확한 FCOST 동가치 조합이 부족합니다: " + cost.id());
            return;
        }
        String transactionId = "cost:facility:" + instance.instanceId + ":" + cost.id();
        ResourceLedger.ReserveResult reserved = runs.reserveResourceTransaction(transactionId, cost.id(),
                instance.instanceId, scope, scope == ResourceLedger.Scope.PERSONAL ? owner : null, spend, snapshot -> {
                    RunSnapshot.FacilityInstanceState current = FacilityStateAccess.instances(snapshot).get(instance.instanceId);
                    RunSnapshot.FacilityWorkState work = new RunSnapshot.FacilityWorkState();
                    work.workId = transactionId;
                    work.operation = "UPGRADE_L" + target;
                    work.ownerUuid = owner;
                    work.state = "QUEUED";
                    work.queuedAtEpochMs = runs.clockNowMillis();
                    work.reservedInputs.putAll(spend);
                    current.queue.add(work);
        });
        if (reserved != ResourceLedger.ReserveResult.RESERVED) {
            player.sendMessage(reserved == ResourceLedger.ReserveResult.ALREADY_COMMITTED
                    ? ChatColor.GREEN + "이미 완료된 업그레이드입니다."
                    : ChatColor.RED + "시설 비용 예약 실패: " + reserved);
            return;
        }
        boolean processingStarted = runs.beginResourceTransaction(transactionId,
                snapshot -> facilityWork(snapshot, instance.instanceId, transactionId).state = "PROCESSING");
        if (!processingStarted) {
            player.sendMessage(ChatColor.YELLOW + profile.name()
                    + " 업그레이드 비용이 예약되었습니다. 처리 재개를 기다리는 중입니다.");
            return;
        }
        boolean committed = runs.commitResourceTransaction(transactionId, "FACILITY_UPGRADED", "{\"instanceId\":\""
                + instance.instanceId + "\",\"costId\":\"" + cost.id() + "\",\"level\":" + target + "}", snapshot -> {
                    RunSnapshot.FacilityInstanceState current = FacilityStateAccess.instances(snapshot).get(instance.instanceId);
                    double ratio = current.hp / Math.max(1.0, current.maxHp);
                    current.level = target;
                    current.maxHp = profile.baseHp() * FacilityPolicy.hpMultiplier(target);
                    current.hp = Math.max(1.0, current.maxHp * ratio);
                    current.state = FacilityPolicy.healthState(current.hp, current.maxHp);
                    facilityWork(snapshot, instance.instanceId, transactionId).state = "COMPLETED";
                });
        if (!committed) {
            player.sendMessage(ChatColor.YELLOW + profile.name()
                    + " 업그레이드가 처리 중 상태로 저장되었습니다. 복구 완료 전에는 적용되지 않습니다.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + profile.name() + " Lv " + target + " 업그레이드 완료");
    }

    private static RunSnapshot.FacilityWorkState facilityWork(RunSnapshot run, String instanceId,
                                                               String transactionId) {
        return FacilityStateAccess.instances(run).get(instanceId).queue.stream()
                .filter(work -> transactionId.equals(work.workId)).findFirst().orElseThrow();
    }

    private static ProductionContentCatalog.FacilityCostEntry levelCost(
            ProductionContentCatalog.FacilityEntry profile, int targetLevel) {
        return profile.levelCosts().stream().filter(cost -> cost.targetLevel() == targetLevel)
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Missing facility cost " + profile.id() + " L" + targetLevel));
    }

    private void dismantle(Player player, RunSnapshot.FacilityInstanceState instance) {
        if (!safeForFacilityAction(player)) { player.sendMessage(ChatColor.RED + "최근 피격·보스 전투 중에는 철거할 수 없습니다."); return; }
        ProductionContentCatalog.FacilityEntry profile = profile(instance);
        if ("FAC-C03".equals(instance.facilityType) && instance.storageSlots != null && !instance.storageSlots.isEmpty()) {
            player.sendMessage(ChatColor.RED + "임시 보관함을 비운 뒤 철거하세요. 저장 물품은 버리지 않습니다.");
            return;
        }
        if (profile.reconstruction()) { player.sendMessage(ChatColor.RED + "재건 시설은 60초 이전 절차가 필요해 현재 GUI에서 즉시 철거할 수 없습니다."); return; }
        clearRepresentation(instance);
        runs.mutate(run -> {
            FacilityStateAccess.instances(run).remove(instance.instanceId);
            if (run.facilityRecoveryLedger == null) run.facilityRecoveryLedger = new LinkedHashMap<>();
            run.facilityRecoveryLedger.merge(profile.itemId(), 1, Integer::sum);
        });
        recomputeNetworks();
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + profile.name() + " 철거 완료 · 키트 1개가 시설 복구 원장에 보존되었습니다.");
    }

    private void recomputeNetworks() {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null) return;
        runs.mutate(snapshot -> {
            List<RunSnapshot.FacilityInstanceState> placed = FacilityStateAccess.instances(snapshot).values().stream()
                    .filter(value -> !profile(value).portableDevice()).toList();
            Map<String, String> parent = new HashMap<>();
            placed.forEach(instance -> parent.put(instance.instanceId, instance.instanceId));
            for (int first = 0; first < placed.size(); first++) for (int second = first + 1; second < placed.size(); second++) {
                RunSnapshot.FacilityInstanceState a = placed.get(first);
                RunSnapshot.FacilityInstanceState b = placed.get(second);
                ProductionContentCatalog.FacilityEntry aProfile = profile(a);
                ProductionContentCatalog.FacilityEntry bProfile = profile(b);
                if ("INDEPENDENT".equals(aProfile.networkPolicy()) || "INDEPENDENT".equals(bProfile.networkPolicy())) continue;
                double range = "CONNECTED_96".equals(aProfile.networkPolicy()) || "CONNECTED_96".equals(bProfile.networkPolicy())
                        ? 96.0 : 24.0;
                if (FacilityPolicy.directlyConnected(a.world, a.x, a.y, a.z, b.world, b.x, b.y, b.z, range)) {
                    union(parent, a.instanceId, b.instanceId);
                }
            }
            for (RunSnapshot.FacilityInstanceState instance : placed) {
                instance.networkId = "network:" + snapshot.runId + ":" + find(parent, instance.instanceId);
            }
        });
    }

    private static String find(Map<String, String> parent, String id) {
        String root = parent.get(id);
        if (root.equals(id)) return root;
        root = find(parent, root);
        parent.put(id, root);
        return root;
    }

    private static void union(Map<String, String> parent, String first, String second) {
        String a = find(parent, first);
        String b = find(parent, second);
        if (a.equals(b)) return;
        if (a.compareTo(b) <= 0) parent.put(b, a); else parent.put(a, b);
    }

    private String networkFor(ProductionContentCatalog.FacilityEntry profile, Location location) {
        RunSnapshot run = runs.current().orElseThrow();
        if ("INDEPENDENT".equals(profile.networkPolicy())) return "network:" + run.runId + ":" + UUID.randomUUID();
        double range = "CONNECTED_96".equals(profile.networkPolicy()) ? 96.0 : 24.0;
        return FacilityStateAccess.instances(run).values().stream().filter(value -> value.networkId != null)
                .filter(value -> FacilityPolicy.directlyConnected(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                        value.world, value.x, value.y, value.z, range))
                .min(Comparator.comparingDouble(value -> location.distanceSquared(
                        new Location(location.getWorld(), value.x, value.y, value.z))))
                .map(value -> value.networkId).orElse("network:" + run.runId + ":" + UUID.randomUUID());
    }

    private List<Location> virtualBuildTargets(Player player, int count) {
        List<Location> result = new ArrayList<>();
        Block looked = player.getTargetBlockExact(8);
        Location origin = looked != null ? looked.getLocation().add(0, 1, 0) : player.getLocation().getBlock().getLocation();
        int[][] offsets = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}};
        for (int[] offset : offsets) {
            if (result.size() >= count) break;
            Block candidate = origin.clone().add(offset[0], 0, offset[1]).getBlock();
            while (candidate.getY() > candidate.getWorld().getMinHeight() + 1 && candidate.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isAir()) {
                candidate = candidate.getRelative(org.bukkit.block.BlockFace.DOWN);
            }
            if (!candidate.getType().isAir() || !candidate.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()
                    || instanceAt(candidate) != null) continue;
            result.add(candidate.getLocation());
        }
        return result;
    }

    private String facilityType(String virtualOutputId) {
        int separator = virtualOutputId.indexOf('@');
        return separator < 0 ? virtualOutputId : virtualOutputId.substring(0, separator);
    }

    private void tickDefenceFacilities(RunSnapshot run) {
        for (RunSnapshot.FacilityInstanceState instance : FacilityStateAccess.instances(run).values()) {
            if (!"ACTIVE".equals(instance.state) || instance.triggerCharges == 0
                    || !("FAC-D02".equals(instance.facilityType) || "FAC-D03".equals(instance.facilityType))) continue;
            org.bukkit.World world = Bukkit.getWorld(instance.world);
            if (world == null) continue;
            Location center = new Location(world, instance.x + 0.5, instance.y + 0.5, instance.z + 0.5);
            Monster target = center.getNearbyLivingEntities(2.5).stream().filter(Monster.class::isInstance)
                    .map(Monster.class::cast).findFirst().orElse(null);
            if (target == null) continue;
            if ("FAC-D02".equals(instance.facilityType)) target.addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, true, true));
            else {
                Vector push = target.getLocation().toVector().subtract(center.toVector()).setY(0.25).normalize().multiply(0.6);
                target.setVelocity(push);
                if (combat != null) combat.applyBreak(target, 12.0 * Math.max(1, instance.level));
            }
            runs.mutate(snapshot -> {
                RunSnapshot.FacilityInstanceState current = FacilityStateAccess.instances(snapshot).get(instance.instanceId);
                current.triggerCharges = Math.max(0, current.triggerCharges - 1);
                if (current.triggerCharges == 0) current.state = "DISABLED";
            });
        }
    }

    private void tickFacilityWork(long deltaMillis) {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || FacilityStateAccess.instances(snapshot).values().stream()
                .flatMap(instance -> instance.queue == null ? java.util.stream.Stream.empty() : instance.queue.stream())
                .noneMatch(work -> "PROCESSING".equals(work.state))) return;
        runs.mutate(run -> {
            for (RunSnapshot.FacilityInstanceState instance : FacilityStateAccess.instances(run).values()) {
                if (instance.queue == null) continue;
                for (RunSnapshot.FacilityWorkState work : instance.queue) {
                    if (!"PROCESSING".equals(work.state)) continue;
                    work.processedMillis = Math.min(work.durationMillis, work.processedMillis + deltaMillis);
                    if (work.processedMillis < work.durationMillis) continue;
                    work.state = "COMPLETED";
                    instance.state = "READY";
                    run.committedKeys.add("proof:" + instance.facilityType + "_READY");
                    telemetry.event(run.runId, "FACILITY_WORK_COMPLETED", "{\"instanceId\":\""
                            + instance.instanceId + "\",\"workId\":\"" + work.workId + "\"}");
                }
            }
        });
    }

    private void openStorage(Player player, RunSnapshot.FacilityInstanceState instance) {
        UUID current = storageSessions.get(instance.instanceId);
        if (current != null && !current.equals(player.getUniqueId())) {
            Player viewer = Bukkit.getPlayer(current);
            if (viewer != null && viewer.isOnline()) {
                player.sendMessage(ChatColor.RED + "다른 파티원이 이 보관함을 사용 중입니다.");
                return;
            }
            storageSessions.remove(instance.instanceId);
        }
        StorageHolder holder = new StorageHolder(player.getUniqueId(), instance.instanceId);
        Inventory inventory = Bukkit.createInventory(holder, 9, ChatColor.DARK_GREEN + "임시 보관함");
        holder.inventory = inventory;
        if (instance.storageSlots != null) for (Map.Entry<String, String> entry : instance.storageSlots.entrySet()) {
            try {
                int slot = Integer.parseInt(entry.getKey());
                if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot,
                        ItemStack.deserializeBytes(Base64.getDecoder().decode(entry.getValue())));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("손상된 시설 보관 슬롯을 건너뜁니다: " + instance.instanceId + "/" + entry.getKey());
            }
        }
        storageSessions.put(instance.instanceId, player.getUniqueId());
        player.openInventory(inventory);
    }

    private void persistStorage(StorageHolder holder, Inventory inventory) {
        UUID session = storageSessions.get(holder.instanceId);
        if (session == null || !session.equals(holder.owner)) return;
        Map<String, String> encoded = new LinkedHashMap<>();
        for (int slot = 0; slot < Math.min(9, inventory.getSize()); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            encoded.put(Integer.toString(slot), Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        runs.mutate(run -> {
            RunSnapshot.FacilityInstanceState current = FacilityStateAccess.instances(run).get(holder.instanceId);
            if (current != null) current.storageSlots = encoded;
        });
    }

    private void treat(Player player, String opcode) {
        removeNegativeEffects(player);
        var healthAttribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double maximum = healthAttribute == null ? 20.0 : healthAttribute.getValue();
        if (!"PURIFY".equals(opcode)) player.setHealth(Math.min(maximum,
                player.getHealth() + ("MEDICAL".equals(opcode) ? 10.0 : 4.0)));
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 1.2f);
        player.sendMessage(ChatColor.GREEN + "치료·정화 처리가 완료되었습니다.");
    }

    private void removeNegativeEffects(Player player) {
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            PotionEffectType type = effect.getType();
            if (type.equals(PotionEffectType.POISON) || type.equals(PotionEffectType.WITHER)
                    || type.equals(PotionEffectType.SLOWNESS) || type.equals(PotionEffectType.WEAKNESS)
                    || type.equals(PotionEffectType.BLINDNESS) || type.equals(PotionEffectType.NAUSEA)
                    || type.equals(PotionEffectType.MINING_FATIGUE) || type.equals(PotionEffectType.HUNGER)) {
                player.removePotionEffect(type);
            }
        }
    }

    private void travel(Player player, RunSnapshot.FacilityInstanceState origin) {
        if (!safeForFacilityAction(player)) { player.sendMessage(ChatColor.RED + "전투·추적 상태에서는 이동 앵커를 사용할 수 없습니다."); return; }
        RunSnapshot.FacilityInstanceState target = FacilityStateAccess.instances(runs.current().orElseThrow()).values().stream()
                .filter(value -> "FAC-S18".equals(value.facilityType) && "ACTIVE".equals(value.state)
                        && !origin.instanceId.equals(value.instanceId)).findFirst().orElse(null);
        if (target == null) { player.sendMessage(ChatColor.YELLOW + "연결된 다른 이동 앵커가 없습니다."); return; }
        org.bukkit.World world = Bukkit.getWorld(target.world);
        if (world == null) return;
        player.teleport(new Location(world, target.x + 0.5, target.y + 1.0, target.z + 0.5, player.getYaw(), player.getPitch()));
        player.sendMessage(ChatColor.GREEN + "이동 앵커 전송 완료");
    }

    private void showAnalysis(Player player) {
        RunSnapshot run = runs.current().orElseThrow();
        int active = (int) FacilityStateAccess.instances(run).values().stream().filter(value -> "ACTIVE".equals(value.state)).count();
        int threat = FacilityStateAccess.instances(run).values().stream().filter(value -> "ACTIVE".equals(value.state))
                .mapToInt(value -> profile(value).threatValue()).sum();
        player.sendMessage(ChatColor.AQUA + "[시설 분석] Day " + run.day + " · ACTIVE " + active + " · 총 위협 " + threat);
    }

    private boolean safeForFacilityAction(Player player) {
        RunSnapshot run = runs.current().orElseThrow();
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        return state != null && runs.clockNowMillis() - state.lastDamageAtEpochMs >= 10_000L
                && (run.boss == null || !"ACTIVE".equals(run.boss.state));
    }

    private int partySize() {
        return Math.max(1, Math.min(4, runs.current().orElseThrow().registeredPlayers.size()));
    }

    private RunSnapshot.FacilityInstanceState instanceAt(Block block) {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null || block == null) return null;
        return FacilityStateAccess.instances(run).values().stream().filter(value -> value.world != null
                && value.world.equals(block.getWorld().getName()) && value.x == block.getX()
                && value.y == block.getY() && value.z == block.getZ()).findFirst().orElse(null);
    }

    private ProductionContentCatalog.FacilityEntry profile(RunSnapshot.FacilityInstanceState instance) {
        ProductionContentCatalog.FacilityEntry profile = production.facilitiesById().get(instance.facilityType);
        if (profile == null) throw new IllegalStateException("Unknown facility type " + instance.facilityType);
        return profile;
    }

    private Material material(ProductionContentCatalog.FacilityEntry profile) {
        Material material = Material.matchMaterial(profile.coreMaterial());
        return material == null || material.isAir() ? Material.LODESTONE : material;
    }

    private void markRepresentation(Block block, Material material, String instanceId) {
        block.setType(material, false);
        if (block.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instanceId);
            tile.update(true, false);
        }
    }

    private void clearRepresentation(RunSnapshot.FacilityInstanceState instance) {
        if (instance.world == null) return;
        org.bukkit.World world = Bukkit.getWorld(instance.world);
        if (world == null) return;
        Block block = world.getBlockAt(instance.x, instance.y, instance.z);
        RunSnapshot.FacilityInstanceState current = instanceAt(block);
        if (current != null && current.instanceId.equals(instance.instanceId)) block.setType(Material.AIR, false);
    }

    private String costText(Map<String, Integer> cost) {
        return "건 " + cost.getOrDefault("construction", 0) + " / 생 " + cost.getOrDefault("survival", 0)
                + " / 금 " + cost.getOrDefault("metal", 0) + " / 신 " + cost.getOrDefault("signal", 0)
                + " / 특 " + cost.getOrDefault("specialist", 0);
    }

    private String dayReadyText(int targetLevel) {
        int day = switch (targetLevel) { case 2 -> 18; case 3 -> 28; case 4 -> 38; default -> 44; };
        return ChatColor.DARK_GRAY + "Day " + day + "+ · 개인 자원에서 소비";
    }

    private String activeWorkText(RunSnapshot.FacilityInstanceState instance) {
        if (instance.queue == null) return null;
        return instance.queue.stream().filter(work -> "PROCESSING".equals(work.state)).findFirst()
                .map(work -> "진행 " + work.processedMillis / 1000 + "/" + work.durationMillis / 1000 + "초")
                .orElse(null);
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null || material.isAir() ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item;
    }

    private record PendingVirtualBuild(String outputId, List<Location> targets) { }

    private static final class FacilityHolder implements InventoryHolder {
        private final UUID owner;
        private final String instanceId;
        private FacilityHolder(UUID owner, String instanceId) { this.owner = owner; this.instanceId = instanceId; }
        @Override public Inventory getInventory() { return null; }
    }

    private static final class StorageHolder implements InventoryHolder {
        private final UUID owner;
        private final String instanceId;
        private Inventory inventory;
        private StorageHolder(UUID owner, String instanceId) { this.owner = owner; this.instanceId = instanceId; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
