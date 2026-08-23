package com.lsc.corp.wsplugin.finale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class FinalReadinessPolicyTest {
    private static final Set<String> BOSSES = Set.of("BOSS-D10", "BOSS-D20", "BOSS-D30", "BOSS-D40");
    private static final Set<String> PARTS = Set.of("A", "B", "C", "D");
    private static final Set<String> DISCOVERIES = Set.of("C27", "C28-A", "C28-B", "C28-C", "C28-D", "C29");
    private static final Set<String> FACILITIES = Set.of("FAC-R01", "FAC-R02", "FAC-R03", "FAC-R04", "FAC-R06");

    @Test
    void dayLockCannotBeBypassedByCompletingEveryOtherRequirement() {
        var state = new FinalReadinessPolicy.State(49, BOSSES, PARTS, DISCOVERIES, FACILITIES,
                3, true, false, false);
        assertEquals(java.util.List.of("FINAL_DAY_LOCK · Day 50 필요"), FinalReadinessPolicy.missing(state));
    }

    @Test
    void dayFiftyRequiresAQuietWorldAndAllPhysicalAuthorities() {
        var ready = new FinalReadinessPolicy.State(50, BOSSES, PARTS, DISCOVERIES, FACILITIES,
                3, true, false, false);
        assertTrue(FinalReadinessPolicy.missing(ready).isEmpty());
        var unsafe = new FinalReadinessPolicy.State(50, BOSSES, PARTS, DISCOVERIES, FACILITIES,
                3, true, true, true);
        assertEquals(2, FinalReadinessPolicy.missing(unsafe).size());
    }
}
