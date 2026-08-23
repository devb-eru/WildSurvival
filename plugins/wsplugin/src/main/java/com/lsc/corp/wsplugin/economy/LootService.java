package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class LootService {
    private final RunService runs;
    private final ProductionContentCatalog content;
    private final ItemCodexService codex;
    private final EquipmentService equipment;
    private final GrowthService growth;
    private final TelemetryService telemetry;

    public LootService(RunService runs, ProductionContentCatalog content, ItemCodexService codex,
                       EquipmentService equipment, GrowthService growth, TelemetryService telemetry) {
        this.runs = runs;
        this.content = content;
        this.codex = codex;
        this.equipment = equipment;
        this.growth = growth;
        this.telemetry = telemetry;
    }

    public boolean rewardEnemy(String transactionId, ProductionContentCatalog.EnemyEntry enemy,
                               Collection<Player> contributors) {
        ProductionContentCatalog.LootEntry table = requireTable(enemy.lootTableId());
        if (!enemy.rewardsPlayers() || table.noReward()) return false;
        RunSnapshot before = runs.current().orElseThrow();
        List<String> eligible = eligibleContributors(before, contributors);
        if (eligible.isEmpty()) return false;
        List<String> owners = eligible.stream().sorted(Comparator
                .comparingInt((String id) -> before.players.get(id).lootValueReceived)
                .thenComparing(Comparator.naturalOrder())).toList();
        int day = before.day;
        List<String> guaranteedPool = table.guaranteedPool();
        List<String> specialtyPool = table.specialtyPool();
        if ("ELITE".equals(table.profile())) {
            ProductionContentCatalog.LootEntry dayTable = representativeDayTable(day);
            guaranteedPool = dayTable.guaranteedPool();
            specialtyPool = dayTable.specialtyPool();
        }
        int pity = before.lootPityCounters == null ? 0
                : before.lootPityCounters.getOrDefault(table.profile(), 0);
        long seed = LootRollPolicy.transactionSeed(before.seed, transactionId);
        LootRollPolicy.RollResult result = LootRollPolicy.roll(
                table, seed, guaranteedPool, specialtyPool, pity, day);
        List<PendingRoll> rolls = createRolls(result, owners, day, seed);
        boolean committed = runs.commitOnce("loot-roll:" + transactionId, "LOOT_ROLLED",
                "{\"transactionId\":\"" + transactionId + "\",\"lootTableId\":\""
                        + table.id() + "\",\"entries\":" + rolls.size() + "}", run -> {
                    if (run.lootTransactions == null) run.lootTransactions = new LinkedHashMap<>();
                    if (run.lootPityCounters == null) run.lootPityCounters = new LinkedHashMap<>();
                    RunSnapshot.LootTransactionState transaction = new RunSnapshot.LootTransactionState();
                    transaction.transactionId = transactionId;
                    transaction.sourceId = enemy.id();
                    transaction.lootTableId = table.id();
                    transaction.eligibleContributors.addAll(eligible);
                    transaction.rolledAtEpochMs = System.currentTimeMillis();
                    for (PendingRoll roll : rolls) queueRoll(run, transaction, roll);
                    transaction.claimed = transaction.rolledEntries.stream().allMatch(entry -> entry.queued);
                    run.lootTransactions.put(transactionId, transaction);
                    run.lootPityCounters.put(table.profile(), result.nextPityCounter());
                });
        if (!committed) return false;
        deliverOnline(eligible);
        telemetry.event(before.runId, "LOOT_CLAIM_QUEUED", "{\"transactionId\":\"" + transactionId
                + "\",\"owners\":" + eligible.size() + "}");
        return true;
    }

    public void deliverPending(Player player) {
        codex.flushPending(player);
        equipment.reconcilePendingRewards(player);
    }

    private void queueRoll(RunSnapshot run, RunSnapshot.LootTransactionState transaction, PendingRoll roll) {
        RunSnapshot.PlayerState owner = run.players.get(roll.ownerUuid);
        if (owner == null) return;
        if (owner.pendingRegisteredItems == null) owner.pendingRegisteredItems = new LinkedHashMap<>();
        if (owner.pendingEquipmentRewards == null) owner.pendingEquipmentRewards = new ArrayList<>();
        if (owner.pendingBlueprintUnlocks == null) owner.pendingBlueprintUnlocks = new LinkedHashSet<>();
        switch (roll.kind) {
            case "RESOURCE", "ITEM" -> owner.pendingRegisteredItems.merge(roll.itemId, roll.amount, Integer::sum);
            case "EQUIPMENT" -> owner.pendingEquipmentRewards.add(roll.itemId);
            case "BLUEPRINT" -> owner.pendingBlueprintUnlocks.add(roll.itemId);
            default -> throw new IllegalStateException("Unknown queued loot kind " + roll.kind);
        }
        owner.lootValueReceived += roll.value;
        RunSnapshot.LootRollState stored = new RunSnapshot.LootRollState();
        stored.kind = roll.kind;
        stored.itemId = roll.itemId;
        stored.amount = roll.amount;
        stored.ownerUuid = roll.ownerUuid;
        stored.queued = true;
        transaction.rolledEntries.add(stored);
    }

    private List<PendingRoll> createRolls(LootRollPolicy.RollResult result, List<String> owners,
                                          int day, long seed) {
        List<PendingRoll> rolls = new ArrayList<>();
        int cursor = 0;
        for (Map.Entry<String, Integer> resource : result.resources().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            String owner = owners.get(cursor++ % owners.size());
            double multiplier = player(owner).map(growth::resourceMultiplier).orElse(1.0);
            int amount = Math.max(1, (int) Math.floor(resource.getValue() * multiplier));
            rolls.add(new PendingRoll("RESOURCE", resource.getKey(), amount, owner, amount));
        }
        if (!result.equipmentRarity().isBlank()) {
            String equipmentId = chooseEquipment(result.equipmentRarity(), day, seed);
            if (equipmentId != null) {
                String owner = owners.get(cursor % owners.size());
                rolls.add(new PendingRoll(result.blueprint() ? "BLUEPRINT" : "EQUIPMENT",
                        equipmentId, 1, owner, result.blueprint() ? 30 : 50));
            }
        }
        return List.copyOf(rolls);
    }

    private String chooseEquipment(String requestedRarity, int day, long seed) {
        List<String> rarityOrder = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "ABYSSAL");
        int requested = Math.max(0, rarityOrder.indexOf(requestedRarity));
        for (int rarityIndex = requested; rarityIndex >= 0; rarityIndex--) {
            String rarity = rarityOrder.get(rarityIndex);
            List<String> candidates = content.equipmentById().values().stream()
                    .filter(entry -> !entry.utility() && entry.firstDay() <= day && rarity.equals(entry.rarity()))
                    .map(ProductionContentCatalog.EquipmentEntry::id).sorted().toList();
            if (!candidates.isEmpty()) {
                return candidates.get(new SplittableRandom(seed ^ rarity.hashCode()).nextInt(candidates.size()));
            }
        }
        return null;
    }

    private ProductionContentCatalog.LootEntry representativeDayTable(int day) {
        String profile = day >= 41 ? "COMBAT-D50" : day >= 31 ? "COMBAT-D40"
                : day >= 21 ? "COMBAT-D30" : day >= 11 ? "COMBAT-D20" : "COMBAT-D10";
        return content.lootById().values().stream().filter(entry -> profile.equals(entry.profile()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing loot profile " + profile));
    }

    private List<String> eligibleContributors(RunSnapshot run, Collection<Player> contributors) {
        Set<String> result = new LinkedHashSet<>();
        for (Player player : contributors) {
            String id = player.getUniqueId().toString();
            if (run.registeredPlayers.contains(id) && run.players.containsKey(id)) result.add(id);
        }
        return List.copyOf(result);
    }

    private void deliverOnline(List<String> eligible) {
        for (String owner : eligible) player(owner).ifPresent(player -> {
            deliverPending(player);
            player.sendMessage(ChatColor.GREEN + "전투 전리품이 개인 보상함에 정산되었습니다.");
        });
    }

    private java.util.Optional<Player> player(String uuid) {
        try {
            return java.util.Optional.ofNullable(org.bukkit.Bukkit.getPlayer(UUID.fromString(uuid)));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    private ProductionContentCatalog.LootEntry requireTable(String id) {
        ProductionContentCatalog.LootEntry table = content.lootById().get(id);
        if (table == null) throw new IllegalArgumentException("Unknown loot table " + id);
        return table;
    }

    private record PendingRoll(String kind, String itemId, int amount, String ownerUuid, int value) { }
}
