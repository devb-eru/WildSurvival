package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public final class LootRollPolicy {
    private static final List<String> RARITIES = List.of(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "ABYSSAL");

    private LootRollPolicy() { }

    public static RollResult roll(ProductionContentCatalog.LootEntry table, long seed,
                                  List<String> guaranteedPool, List<String> specialtyPool,
                                  int pityCounter, int day) {
        SplittableRandom random = new SplittableRandom(seed);
        Map<String, Integer> resources = new LinkedHashMap<>();
        rollPool(resources, guaranteedPool, random, table.guaranteedMin(), table.guaranteedMax());
        rollPool(resources, specialtyPool, random, table.specialtyMin(), table.specialtyMax());

        String equipmentRarity = "";
        boolean blueprint = false;
        if (table.equipmentChances().containsKey("CURRENT_CAP")) {
            if (random.nextDouble() < table.equipmentChances().get("CURRENT_CAP")) {
                equipmentRarity = rarityCap(day);
            }
            blueprint = random.nextDouble() < table.equipmentChances().getOrDefault("BLUEPRINT", 0.0);
        } else {
            for (int index = RARITIES.size() - 1; index >= 0; index--) {
                String rarity = RARITIES.get(index);
                double chance = table.equipmentChances().getOrDefault(rarity, 0.0);
                if (chance > 0.0 && random.nextDouble() < chance) {
                    equipmentRarity = rarity;
                    break;
                }
            }
        }
        int nextPity = Math.max(0, pityCounter) + 1;
        boolean forcedByPity = table.pityLimit() > 0 && nextPity >= table.pityLimit()
                && equipmentRarity.isBlank() && !blueprint;
        if (forcedByPity) {
            blueprint = true;
            equipmentRarity = highestConfiguredRarity(table, day);
        }
        if (!equipmentRarity.isBlank() || blueprint) nextPity = 0;
        return new RollResult(Map.copyOf(resources), equipmentRarity, blueprint, forcedByPity, nextPity);
    }

    private static void rollPool(Map<String, Integer> output, List<String> pool, SplittableRandom random,
                                 int minimum, int maximum) {
        if (pool.isEmpty() || maximum <= 0) return;
        int count = minimum + (maximum == minimum ? 0 : random.nextInt(maximum - minimum + 1));
        for (int index = 0; index < count; index++) {
            String item = pool.get(random.nextInt(pool.size()));
            output.merge(item, 1, Integer::sum);
        }
    }

    private static String highestConfiguredRarity(ProductionContentCatalog.LootEntry table, int day) {
        if (table.equipmentChances().containsKey("CURRENT_CAP")) return rarityCap(day);
        for (int index = RARITIES.size() - 1; index >= 0; index--) {
            if (table.equipmentChances().getOrDefault(RARITIES.get(index), 0.0) > 0.0) return RARITIES.get(index);
        }
        return "";
    }

    public static String rarityCap(int day) {
        if (day >= 50) return "ABYSSAL";
        if (day >= 40) return "LEGENDARY";
        if (day >= 30) return "EPIC";
        if (day >= 20) return "RARE";
        if (day >= 10) return "UNCOMMON";
        return "COMMON";
    }

    public static long transactionSeed(long runSeed, String transactionId) {
        long value = runSeed ^ 0x9E3779B97F4A7C15L;
        for (int index = 0; index < transactionId.length(); index++) {
            value ^= transactionId.charAt(index);
            value *= 0x100000001B3L;
        }
        return value;
    }

    public record RollResult(Map<String, Integer> resources, String equipmentRarity,
                             boolean blueprint, boolean forcedByPity, int nextPityCounter) { }
}
