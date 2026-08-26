package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProductionEnemyTestActionPolicyTest {
    @Test
    void parryAndGuardWaitForTheNextRealFInputAtDeterministicOffsets() {
        ProductionEnemyTestActionPolicy.Plan parry = ProductionEnemyTestActionPolicy.plan("parry");
        ProductionEnemyTestActionPolicy.Plan guard = ProductionEnemyTestActionPolicy.plan("GUARD");

        assertEquals(ProductionEnemyTestActionPolicy.Mode.PARRY, parry.mode());
        assertEquals(2, parry.offsetTicks());
        assertTrue(parry.waitsForGuardInput());
        assertEquals(ProductionEnemyTestActionPolicy.Mode.GUARD, guard.mode());
        assertEquals(8, guard.offsetTicks());
        assertTrue(guard.waitsForGuardInput());
    }

    @Test
    void hitSchedulesWithoutSynthesizingGuardInput() {
        ProductionEnemyTestActionPolicy.Plan hit = ProductionEnemyTestActionPolicy.plan("hit");

        assertEquals(20, hit.offsetTicks());
        assertFalse(hit.waitsForGuardInput());
    }

    @Test
    void unknownModesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ProductionEnemyTestActionPolicy.plan("critical"));
    }

    @Test
    void forcedActionUsesServerTickWhileLogicalClockIsFrozen() {
        assertEquals(814L, ProductionEnemyTestActionPolicy.actionTick(true, 0L, 814L));
    }

    @Test
    void productionActionKeepsUsingLogicalClock() {
        assertEquals(27L, ProductionEnemyTestActionPolicy.actionTick(false, 27L, 814L));
    }
}
