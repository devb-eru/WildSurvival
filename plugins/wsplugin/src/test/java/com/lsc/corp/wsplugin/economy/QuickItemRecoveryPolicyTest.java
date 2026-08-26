package com.lsc.corp.wsplugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuickItemRecoveryPolicyTest {
    @Test
    void legacyInventoryInitializesTheDurableCountWithoutMovingItems() {
        QuickItemRecoveryPolicy.Decision decision = QuickItemRecoveryPolicy.reconcile(null, 3);

        assertEquals(3, decision.targetCount());
        assertTrue(decision.initializeDurableCount());
        assertFalse(decision.inventoryChanged());
    }

    @Test
    void durableConsumptionRemovesAPlayerDataRewindDuplicate() {
        QuickItemRecoveryPolicy.Decision decision = QuickItemRecoveryPolicy.reconcile(0, 1);

        assertEquals(1, decision.removeCount());
        assertEquals(0, decision.grantCount());
    }

    @Test
    void durableOwnershipRestoresAPlayerDataAheadLoss() {
        QuickItemRecoveryPolicy.Decision decision = QuickItemRecoveryPolicy.reconcile(2, 0);

        assertEquals(2, decision.grantCount());
        assertEquals(0, decision.removeCount());
    }
}
