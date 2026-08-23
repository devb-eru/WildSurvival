package com.lsc.corp.wsplugin.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical item-value authority for TAG ingredients in the production 3x3 craft grid. */
public final class RecipeTagCatalog {
    private static final Map<String, Map<String, Integer>> VALUES = buildValues();
    private static final Set<String> VANILLA_ITEMS = Set.of(
            "BOOK", "IRON_AXE", "IRON_HOE", "IRON_PICKAXE", "IRON_SHOVEL", "WATER_BUCKET");

    private RecipeTagCatalog() { }

    public static boolean supports(String tag) {
        return VALUES.containsKey(tag);
    }

    public static int value(String tag, String itemId) {
        if (itemId == null) return 0;
        return VALUES.getOrDefault(tag, Map.of()).getOrDefault(itemId, 0);
    }

    public static List<String> members(String tag) {
        return List.copyOf(VALUES.getOrDefault(tag, Map.of()).keySet());
    }

    public static Set<String> tags() {
        return VALUES.keySet();
    }

    public static boolean supportsVanillaItem(String material) {
        return VANILLA_ITEMS.contains(material);
    }

    private static Map<String, Map<String, Integer>> buildValues() {
        Map<String, Map<String, Integer>> values = new LinkedHashMap<>();
        values.put("CONSTRUCTION", orderedValues(
                "WSR-WOOD", 1, "WSR-STONE", 1, "WSR-HARD_AGGREGATE", 4, "WSR-STABILIZED_FRAME", 8));
        values.put("SURVIVAL", orderedValues(
                "WSR-RATION", 1, "WSR-HERB", 2, "WSR-STERILE_GEL", 3, "WSR-BIO_MEDIUM", 5));
        values.put("METAL", orderedValues(
                "WSR-IRON", 1, "WSR-REFINED_ALLOY", 2, "WSR-REINFORCED_ALLOY", 3,
                "WSR-HIGH_DENSITY_ALLOY", 6, "WSR-METAL_PLATE", 1));
        values.put("SIGNAL", orderedValues(
                "WSR-REDSTONE", 1, "WSR-COPPER_COIL", 2, "WSR-NEURAL_CIRCUIT", 4,
                "WSR-RESONANCE_COIL", 6));
        values.put("SPECIAL", orderedValues(
                "WSR-MAGIC_CRYSTAL", 2, "WSR-PURIFY_CATALYST", 3, "WSR-PATTERN_RESIDUE", 5,
                "WSR-INTERRUPT_CORE", 8));
        values.put("CORRUPTION_SAMPLE", unitValues(
                "WSR-TISSUE", "WSR-TOXIN_SAMPLE", "WSR-THERMAL_SAMPLE", "WSR-HEMATIC_SAMPLE",
                "WSR-NEURAL_SAMPLE", "WSR-MUTATION_SHARD"));
        values.put("DISTINCT_MUTATION_SAMPLE", unitValues(
                "WSR-TOXIN_SAMPLE", "WSR-THERMAL_SAMPLE", "WSR-HEMATIC_SAMPLE",
                "WSR-NEURAL_SAMPLE", "WSR-MUTATION_SHARD"));
        return Collections.unmodifiableMap(values);
    }

    private static Map<String, Integer> unitValues(String... ids) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String id : ids) result.put(id, 1);
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Integer> orderedValues(Object... cells) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < cells.length; index += 2) {
            result.put((String) cells[index], (Integer) cells[index + 1]);
        }
        return Collections.unmodifiableMap(result);
    }
}
