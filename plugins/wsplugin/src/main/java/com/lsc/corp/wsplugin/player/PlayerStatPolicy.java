package com.lsc.corp.wsplugin.player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlayerStatPolicy {
    private static final double VANILLA_MOVEMENT_SPEED = 0.10;
    private static final double VANILLA_BLOCK_INTERACTION_RANGE = 4.50;
    private static final double VANILLA_ENTITY_INTERACTION_RANGE = 3.00;
    private static final double BASE_MOVEMENT_SPEED_MULTIPLIER = 1.10;
    private static final double BASE_INTERACTION_RANGE_MULTIPLIER = 1.30;
    public static final List<String> IDS = List.of("HP", "AP", "ATK", "DEF", "HIT", "EVA", "SPD", "EXP");
    private static final Map<String, Integer> CAPS = Map.of(
            "AP", 25,
            "HIT", 50,
            "EVA", 30,
            "SPD", 15,
            "EXP", 30
    );

    private PlayerStatPolicy() {
    }

    public static int totalPoints(int level) {
        if (level < 1 || level > 50) {
            throw new IllegalArgumentException("Level must be 1 to 50");
        }
        return level * 3;
    }

    public static int spentPoints(Map<String, Integer> allocation) {
        return normalized(allocation).values().stream().mapToInt(Integer::intValue).sum();
    }

    public static int availablePoints(int level, Map<String, Integer> allocation) {
        return totalPoints(level) - spentPoints(allocation);
    }

    public static Map<String, Integer> validate(int level, Map<String, Integer> allocation) {
        Map<String, Integer> result = normalized(allocation);
        if (spentPoints(result) > totalPoints(level)) {
            throw new IllegalArgumentException("Allocated stat points exceed the level budget");
        }
        for (var entry : CAPS.entrySet()) {
            if (result.get(entry.getKey()) > entry.getValue()) {
                throw new IllegalArgumentException(entry.getKey() + " investment cap is " + entry.getValue());
            }
        }
        return result;
    }

    public static double maxHealth(Map<String, Integer> allocation) {
        return 20.0 + points(allocation, "HP") * 0.2;
    }

    public static int baseMaxAp(Map<String, Integer> allocation) {
        return 100 + Math.min(25, points(allocation, "AP"));
    }

    public static double attackMultiplier(Map<String, Integer> allocation) {
        return 1.0 + points(allocation, "ATK") * 0.02;
    }

    public static double incomingDamageMultiplier(Map<String, Integer> allocation) {
        return 100.0 / (100.0 + points(allocation, "DEF"));
    }

    public static double statusChanceBonus(Map<String, Integer> allocation) {
        return Math.min(0.25, points(allocation, "HIT") * 0.005);
    }

    public static double dodgeCost(Map<String, Integer> allocation) {
        return Math.max(10.0, 20.0 - Math.floor(points(allocation, "EVA") / 3.0));
    }

    public static double dodgeDistanceMultiplier(Map<String, Integer> allocation) {
        return 1.0 + Math.min(0.30, points(allocation, "EVA") * 0.01);
    }

    public static double movementSpeed(Map<String, Integer> allocation) {
        return Math.min(0.16,
                VANILLA_MOVEMENT_SPEED * (BASE_MOVEMENT_SPEED_MULTIPLIER + points(allocation, "SPD") * 0.005));
    }

    public static double blockInteractionRange() {
        return VANILLA_BLOCK_INTERACTION_RANGE * BASE_INTERACTION_RANGE_MULTIPLIER;
    }

    public static double entityInteractionRange() {
        return VANILLA_ENTITY_INTERACTION_RANGE * BASE_INTERACTION_RANGE_MULTIPLIER;
    }

    public static double activityExpMultiplier(Map<String, Integer> allocation) {
        return 1.0 + Math.min(30, points(allocation, "EXP")) * 0.006;
    }

    public static int points(Map<String, Integer> allocation, String id) {
        String normalized = normalizeId(id);
        return Math.max(0, allocation == null ? 0 : allocation.getOrDefault(normalized, 0));
    }

    private static Map<String, Integer> normalized(Map<String, Integer> allocation) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String id : IDS) {
            int value = allocation == null ? 0 : allocation.getOrDefault(id, 0);
            if (value < 0) {
                throw new IllegalArgumentException(id + " investment cannot be negative");
            }
            result.put(id, value);
        }
        if (allocation != null) {
            for (String id : allocation.keySet()) {
                normalizeId(id);
            }
        }
        return result;
    }

    private static String normalizeId(String id) {
        String normalized = id == null ? "" : id.toUpperCase(java.util.Locale.ROOT);
        if (!IDS.contains(normalized)) {
            throw new IllegalArgumentException("Unknown player stat " + id);
        }
        return normalized;
    }
}
