package com.lsc.corp.wsplugin.world;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public final class SeasonDayPolicy {
    private SeasonDayPolicy() { }

    public static int threatForParty(ProductionContentCatalog.DayEntry day,
                                     Map<String, ProductionContentCatalog.EventEntry> eventsById,
                                     int partySize) {
        if (day.bossDay()) return -1;
        int size = Math.max(1, Math.min(4, partySize));
        List<Integer> explicit = day.eventIds().stream().map(eventsById::get)
                .filter(java.util.Objects::nonNull)
                .map(ProductionContentCatalog.EventEntry::partyThreat)
                .filter(values -> values.size() == 3).findFirst().orElse(List.of());
        if (!explicit.isEmpty()) {
            if (size >= 2) return explicit.get(size - 2);
            return Math.max(1, (int) Math.floor(explicit.getFirst() * 0.70));
        }
        int threePlayers = day.threatBudget3();
        int twoPlayers = Math.max(1, (int) Math.floor(threePlayers / 1.30));
        return switch (size) {
            case 1 -> Math.max(1, (int) Math.floor(twoPlayers * 0.70));
            case 2 -> twoPlayers;
            case 3 -> threePlayers;
            case 4 -> Math.max(threePlayers, (int) Math.floor(twoPlayers * 1.60));
            default -> throw new IllegalStateException("Unreachable party size " + size);
        };
    }

    public static List<String> enemyPlan(Map<String, ProductionContentCatalog.EnemyEntry> enemiesById,
                                         int day, int threatBudget, long seed) {
        if (day < 1 || day > 50 || threatBudget < 1) return List.of();
        int earliestDay = Math.max(1, day - 9);
        List<ProductionContentCatalog.EnemyEntry> pool = enemiesById.values().stream()
                .filter(enemy -> enemy.firstDay() >= earliestDay && enemy.firstDay() <= day)
                .filter(enemy -> enemy.budgetCost() > 0 && enemy.parentId().isBlank())
                .filter(enemy -> !enemy.flags().contains("NO_REWARD"))
                .sorted(Comparator.comparingInt(ProductionContentCatalog.EnemyEntry::firstDay).reversed()
                        .thenComparing(ProductionContentCatalog.EnemyEntry::id))
                .toList();
        if (pool.isEmpty()) throw new IllegalArgumentException("No executable enemy pool for Day " + day);
        int minimumCost = pool.stream().mapToInt(ProductionContentCatalog.EnemyEntry::budgetCost).min().orElseThrow();
        int remaining = threatBudget;
        List<String> result = new ArrayList<>();
        SplittableRandom random = new SplittableRandom(seed ^ ((long) day << 32) ^ threatBudget);
        while (remaining >= minimumCost && result.size() < 64) {
            int availableBudget = remaining;
            List<ProductionContentCatalog.EnemyEntry> affordable = pool.stream()
                    .filter(enemy -> enemy.budgetCost() <= availableBudget).toList();
            if (affordable.isEmpty()) break;
            int frontier = Math.min(4, affordable.size());
            ProductionContentCatalog.EnemyEntry selected = affordable.get(random.nextInt(frontier));
            result.add(selected.id());
            remaining -= selected.budgetCost();
        }
        if (result.isEmpty()) {
            result.add(pool.stream().min(Comparator.comparingInt(ProductionContentCatalog.EnemyEntry::budgetCost))
                    .orElseThrow().id());
        }
        return List.copyOf(result);
    }

    public static int activeCap(int day, int partySize) {
        int base = day <= 10 ? 8 : day <= 20 ? 10 : 12;
        return Math.max(4, base - Math.max(0, 3 - Math.max(1, Math.min(4, partySize))));
    }
}
