package com.lsc.corp.wsplugin.death;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GravePolicyTest {
    @Test
    void consumableLossFloorsEachStackAtTwentyFivePercent() {
        assertEquals(0, GravePolicy.lostConsumables(1, true));
        assertEquals(0, GravePolicy.lostConsumables(3, true));
        assertEquals(1, GravePolicy.lostConsumables(4, true));
        assertEquals(4, GravePolicy.lostConsumables(19, true));
        assertEquals(15, GravePolicy.remainingAmount(19, true));
    }

    @Test
    void equipmentAndMaterialsArePreserved() {
        assertEquals(0, GravePolicy.lostConsumables(64, false));
        assertEquals(64, GravePolicy.remainingAmount(64, false));
    }
}
