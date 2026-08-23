package com.lsc.corp.wsplugin.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AugmentMilestonePolicyTest {
    @Test
    void exposesTenPersonalAndFourPartyMilestones() {
        assertEquals(java.util.List.of(3, 6, 10, 15, 20, 25, 30, 35, 40, 45),
                AugmentMilestonePolicy.personalMilestones());
        assertEquals(java.util.List.of(10, 20, 30, 40), AugmentMilestonePolicy.partyMilestones());
    }

    @Test
    void fixesOpeningTiersAndUsesConfiguredLateWeights() {
        assertEquals("SILVER", AugmentMilestonePolicy.tier(1L, 3));
        assertEquals("GOLD", AugmentMilestonePolicy.tier(1L, 6));
        assertEquals("PRISM", AugmentMilestonePolicy.tier(1L, 10));
        Map<String, Integer> counts = new HashMap<>();
        for (long seed = 0; seed < 10_000; seed++) {
            counts.merge(AugmentMilestonePolicy.tier(seed, 15), 1, Integer::sum);
        }
        assertTrue(counts.get("SILVER") > 4_500 && counts.get("SILVER") < 5_500, counts.toString());
        assertTrue(counts.get("GOLD") > 2_500 && counts.get("GOLD") < 3_500, counts.toString());
        assertTrue(counts.get("PRISM") > 1_500 && counts.get("PRISM") < 2_500, counts.toString());
    }
}
