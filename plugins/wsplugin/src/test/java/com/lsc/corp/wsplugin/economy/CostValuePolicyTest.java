package com.lsc.corp.wsplugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CostValuePolicyTest {
    @Test
    void scalesEachPositiveAxisForEverySupportedPartySize() {
        Map<String, Integer> base = Map.of("general", 4, "metal", 2, "signal", 0);

        assertEquals(Map.of("general", 3, "metal", 2, "signal", 0), CostValuePolicy.scaleAxes(base, 1));
        assertEquals(Map.of("general", 4, "metal", 2, "signal", 0), CostValuePolicy.scaleAxes(base, 2));
        assertEquals(Map.of("general", 4, "metal", 2, "signal", 0), CostValuePolicy.scaleAxes(base, 3));
        assertEquals(Map.of("general", 5, "metal", 3, "signal", 0), CostValuePolicy.scaleAxes(base, 4));
    }

    @Test
    void choosesAnExactMinimumItemPlanAndNeverOverpays() {
        Map<String, Integer> planned = CostValuePolicy.plan(Map.of("general", 9), 3, Map.of(
                "WSR-STABILIZED_FRAME", 1, "WSR-HARD_AGGREGATE", 9,
                "WSR-WOOD", 9, "WSR-STONE", 9));

        assertEquals(Map.of("WSR-STABILIZED_FRAME", 1, "WSR-STONE", 1), planned);
        assertNull(CostValuePolicy.plan(Map.of("specialist", 1), 3,
                Map.of("WSR-MAGIC_CRYSTAL", 10)));
    }

    @Test
    void levelOneFacilityReferencesTheCraftRecipeWithoutAnotherDebit() {
        assertFalse(CostValuePolicy.runtimeDebitRequired("CRAFT_RECIPE_REFERENCE", 1));
        assertEquals(true, CostValuePolicy.runtimeDebitRequired("RESOURCE_VALUE", 2));
    }

    @Test
    void sharedResourcesCannotBeSpentTwiceAcrossCostAxes() {
        assertNull(CostValuePolicy.plan(Map.of("general", 8, "construction", 8), 3,
                Map.of("WSR-STABILIZED_FRAME", 1)));
        assertEquals(Map.of("WSR-STABILIZED_FRAME", 2),
                CostValuePolicy.plan(Map.of("general", 8, "construction", 8), 3,
                        Map.of("WSR-STABILIZED_FRAME", 2)));
    }

    @Test
    void plansExactSoloFacilityS16LevelTwoCost() {
        Map<String, Integer> cost = new java.util.LinkedHashMap<>();
        cost.put("construction", 10);
        cost.put("survival", 0);
        cost.put("metal", 12);
        cost.put("signal", 12);
        cost.put("specialist", 2);

        assertEquals(Map.of(
                "WSR-WOOD", 7,
                "WSR-IRON", 9,
                "WSR-REDSTONE", 9,
                "WSR-MAGIC_CRYSTAL", 1), CostValuePolicy.plan(cost, 1, Map.of(
                "WSR-WOOD", 7,
                "WSR-IRON", 9,
                "WSR-REDSTONE", 9,
                "WSR-MAGIC_CRYSTAL", 1)));
    }
}
