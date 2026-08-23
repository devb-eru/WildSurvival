package com.lsc.corp.wsplugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LootRollPolicyTest {
    @Test
    void sameTransactionSeedProducesSameRoll() {
        var table = table(1, 3, 0, 1, Map.of("RARE", 0.04), 18);
        long seed = LootRollPolicy.transactionSeed(991L, "enemy:fixed-uuid");
        var first = LootRollPolicy.roll(table, seed, List.of("WSR-A", "WSR-B"),
                List.of("WSR-S"), 0, 25);
        var second = LootRollPolicy.roll(table, seed, List.of("WSR-A", "WSR-B"),
                List.of("WSR-S"), 0, 25);
        assertEquals(first, second);
        int amount = first.resources().values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(amount >= 1 && amount <= 4);
    }

    @Test
    void pityForcesBlueprintAndResetsCounter() {
        var table = table(1, 1, 0, 0, Map.of("EPIC", Double.MIN_VALUE, "RARE", 0.0), 3);
        var result = LootRollPolicy.roll(table, 42L, List.of("WSR-A"), List.of(), 2, 30);
        assertTrue(result.blueprint());
        assertTrue(result.forcedByPity());
        assertEquals(0, result.nextPityCounter());
        assertFalse(result.equipmentRarity().isBlank());
    }

    @Test
    void rarityCapTracksSeasonBands() {
        assertEquals("COMMON", LootRollPolicy.rarityCap(1));
        assertEquals("UNCOMMON", LootRollPolicy.rarityCap(10));
        assertEquals("RARE", LootRollPolicy.rarityCap(20));
        assertEquals("EPIC", LootRollPolicy.rarityCap(30));
        assertEquals("LEGENDARY", LootRollPolicy.rarityCap(40));
        assertEquals("ABYSSAL", LootRollPolicy.rarityCap(50));
    }

    private static ProductionContentCatalog.LootEntry table(int guaranteedMin, int guaranteedMax,
                                                             int specialtyMin, int specialtyMax,
                                                             Map<String, Double> equipment, int pity) {
        return new ProductionContentCatalog.LootEntry("LOOT-TEST", "EN-TEST", "COMBAT-D30",
                "CONTRIBUTOR_ROUND_ROBIN", "COMBAT_ROLL", List.of("WSR-A"), guaranteedMin,
                guaranteedMax, List.of("WSR-S"), specialtyMin, specialtyMax, equipment, pity,
                List.of(), "", 0, 0, 0, false, List.of());
    }
}
