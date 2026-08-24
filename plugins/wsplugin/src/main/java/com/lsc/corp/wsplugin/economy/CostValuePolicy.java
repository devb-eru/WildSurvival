package com.lsc.corp.wsplugin.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact value conversion for DATA_LOCKED RCOST/FCOST category axes. */
public final class CostValuePolicy {
    private static final Map<String, List<ResourceValue>> VALUES = Map.of(
            "general", values(new ResourceValue("WSR-STABILIZED_FRAME", 8),
                    new ResourceValue("WSR-HARD_AGGREGATE", 4), new ResourceValue("WSR-STONE", 1),
                    new ResourceValue("WSR-WOOD", 1)),
            "construction", values(new ResourceValue("WSR-STABILIZED_FRAME", 8),
                    new ResourceValue("WSR-HARD_AGGREGATE", 4), new ResourceValue("WSR-STONE", 1),
                    new ResourceValue("WSR-WOOD", 1)),
            "survival", values(new ResourceValue("WSR-BIO_MEDIUM", 5),
                    new ResourceValue("WSR-STERILE_GEL", 3), new ResourceValue("WSR-HERB", 2),
                    new ResourceValue("WSR-RATION", 1)),
            "metal", values(new ResourceValue("WSR-HIGH_DENSITY_ALLOY", 6),
                    new ResourceValue("WSR-REINFORCED_ALLOY", 3), new ResourceValue("WSR-REFINED_ALLOY", 2),
                    new ResourceValue("WSR-METAL_PLATE", 1), new ResourceValue("WSR-IRON", 1)),
            "signal", values(new ResourceValue("WSR-RESONANCE_COIL", 6),
                    new ResourceValue("WSR-NEURAL_CIRCUIT", 4), new ResourceValue("WSR-COPPER_COIL", 2),
                    new ResourceValue("WSR-REDSTONE", 1)),
            "specialist", values(new ResourceValue("WSR-INTERRUPT_CORE", 8),
                    new ResourceValue("WSR-PATTERN_RESIDUE", 5), new ResourceValue("WSR-PURIFY_CATALYST", 3),
                    new ResourceValue("WSR-MAGIC_CRYSTAL", 2))
    );

    private CostValuePolicy() { }

    public static Map<String, Integer> scaleAxes(Map<String, Integer> baseAxes, int partySize) {
        double multiplier = switch (Math.max(1, Math.min(4, partySize))) {
            case 1 -> 0.70;
            case 2 -> 0.85;
            case 3 -> 1.00;
            default -> 1.15;
        };
        Map<String, Integer> scaled = new LinkedHashMap<>();
        baseAxes.forEach((axis, value) -> {
            if (value == null || value < 0) throw new IllegalArgumentException("Invalid cost axis " + axis);
            scaled.put(axis.toLowerCase(java.util.Locale.ROOT), value == 0 ? 0 : (int) Math.ceil(value * multiplier));
        });
        return scaled;
    }

    public static Map<String, Integer> plan(Map<String, Integer> baseAxes, int partySize,
                                            Map<String, Integer> available) {
        if (available == null) throw new IllegalArgumentException("Available resource balance is required");
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, Integer> remaining = new LinkedHashMap<>(available);
        for (var axis : scaleAxes(baseAxes, partySize).entrySet()) {
            if (axis.getValue() == 0) continue;
            List<ResourceValue> values = VALUES.get(axis.getKey());
            if (values == null) throw new IllegalArgumentException("Unknown cost axis " + axis.getKey());
            Map<String, Integer> axisPlan = exactPlan(axis.getValue(), values, remaining);
            if (axisPlan == null) return null;
            axisPlan.forEach((id, amount) -> {
                result.merge(id, amount, Integer::sum);
                remaining.merge(id, -amount, Integer::sum);
            });
        }
        return result;
    }

    public static boolean runtimeDebitRequired(String paymentMode, int targetLevel) {
        if (targetLevel == 1) return false;
        return "RESOURCE_VALUE".equals(paymentMode);
    }

    private static Map<String, Integer> exactPlan(int target, List<ResourceValue> values,
                                                   Map<String, Integer> available) {
        List<Plan> plans = new ArrayList<>(java.util.Collections.nCopies(target + 1, null));
        plans.set(0, new Plan(new int[values.size()], 0));
        for (int resourceIndex = 0; resourceIndex < values.size(); resourceIndex++) {
            ResourceValue value = values.get(resourceIndex);
            int maximum = Math.min(Math.max(0, available.getOrDefault(value.id, 0)), target / value.value);
            List<Plan> next = new ArrayList<>(plans);
            for (int sum = 0; sum <= target; sum++) {
                Plan base = plans.get(sum);
                if (base == null) continue;
                for (int amount = 1; amount <= maximum && sum + amount * value.value <= target; amount++) {
                    int[] counts = base.counts.clone();
                    counts[resourceIndex] += amount;
                    Plan candidate = new Plan(counts, base.itemCount + amount);
                    int nextSum = sum + amount * value.value;
                    if (better(candidate, next.get(nextSum))) next.set(nextSum, candidate);
                }
            }
            plans = next;
        }
        Plan selected = plans.get(target);
        if (selected == null) return null;
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < selected.counts.length; index++) {
            if (selected.counts[index] > 0) result.put(values.get(index).id, selected.counts[index]);
        }
        return result;
    }

    private static boolean better(Plan candidate, Plan current) {
        if (current == null || candidate.itemCount < current.itemCount) return true;
        if (candidate.itemCount > current.itemCount) return false;
        for (int index = 0; index < candidate.counts.length; index++) {
            if (candidate.counts[index] != current.counts[index]) {
                return candidate.counts[index] > current.counts[index];
            }
        }
        return false;
    }

    @SafeVarargs
    private static List<ResourceValue> values(ResourceValue... values) {
        return List.of(values);
    }

    private record ResourceValue(String id, int value) { }
    private record Plan(int[] counts, int itemCount) { }
}
