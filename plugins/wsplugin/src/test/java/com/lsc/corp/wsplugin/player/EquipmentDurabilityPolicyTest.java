package com.lsc.corp.wsplugin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EquipmentDurabilityPolicyTest {
    @Test
    void spendsExactlyOnceAndTransitionsToBrokenWithoutNegativeValues() {
        var active = EquipmentDurabilityPolicy.spend(5, 10, 2);
        assertEquals(3, active.current());
        assertEquals(EquipmentDurabilityPolicy.Condition.ACTIVE, active.condition());
        assertTrue(active.changed());

        var broken = EquipmentDurabilityPolicy.spend(1, 10, 3);
        assertEquals(0, broken.current());
        assertEquals(EquipmentDurabilityPolicy.Condition.BROKEN, broken.condition());
        assertTrue(broken.changed());

        var unchanged = EquipmentDurabilityPolicy.spend(0, 10, 1);
        assertEquals(0, unchanged.current());
        assertFalse(unchanged.changed());
    }

    @Test
    void mirrorNeverReachesNativeBreakThreshold() {
        assertEquals(0, EquipmentDurabilityPolicy.mirrorDamage(250, 250, 250));
        assertEquals(249, EquipmentDurabilityPolicy.mirrorDamage(0, 250, 250));
        assertThrows(IllegalArgumentException.class, () -> EquipmentDurabilityPolicy.spend(11, 10, 1));
    }

    @Test
    void quickKitRepairsFortyPercentAndReactivatesBrokenEquipment() {
        var broken = EquipmentDurabilityPolicy.repairByFraction(0, 250,
                EquipmentDurabilityPolicy.Condition.BROKEN, 0.40);
        assertEquals(100, broken.current());
        assertEquals(EquipmentDurabilityPolicy.Condition.ACTIVE, broken.condition());

        var damaged = EquipmentDurabilityPolicy.repairByFraction(180, 250,
                EquipmentDurabilityPolicy.Condition.ACTIVE, 0.40);
        assertEquals(250, damaged.current());
        assertThrows(IllegalArgumentException.class, () -> EquipmentDurabilityPolicy.repairByFraction(
                0, 250, EquipmentDurabilityPolicy.Condition.BROKEN, 0.0));
    }

    @Test
    void nativeDamageEventRemainsObservableButCannotDeleteManagedItem() {
        var decision = EquipmentDurabilityPolicy.interceptNativeDamage(3);
        assertEquals(3, decision.ledgerCost());
        assertEquals(0, decision.nativeDamage());

        var zeroDamageDecision = EquipmentDurabilityPolicy.interceptNativeDamage(0);
        assertEquals(1, zeroDamageDecision.ledgerCost());
        assertEquals(0, zeroDamageDecision.nativeDamage());
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentDurabilityPolicy.interceptNativeDamage(-1));
    }

    @Test
    void emitsCompatibilityBreakOnlyForActiveToBrokenTransition() {
        assertTrue(EquipmentDurabilityPolicy.isBreakTransition(
                EquipmentDurabilityPolicy.Condition.ACTIVE, EquipmentDurabilityPolicy.Condition.BROKEN));
        assertFalse(EquipmentDurabilityPolicy.isBreakTransition(
                EquipmentDurabilityPolicy.Condition.ACTIVE, EquipmentDurabilityPolicy.Condition.ACTIVE));
        assertFalse(EquipmentDurabilityPolicy.isBreakTransition(
                EquipmentDurabilityPolicy.Condition.BROKEN, EquipmentDurabilityPolicy.Condition.BROKEN));
        assertThrows(IllegalArgumentException.class, () -> EquipmentDurabilityPolicy.isBreakTransition(
                null, EquipmentDurabilityPolicy.Condition.BROKEN));
    }

    @Test
    void resolvesSlotZeroAndUsesOneStableBreakCommitKey() {
        assertEquals(EquipmentDurabilityPolicy.BreakSlot.MAIN_HAND,
                EquipmentDurabilityPolicy.resolveBreakSlot("main-1", "off-1", Map.of(), "main-1"));
        assertEquals(EquipmentDurabilityPolicy.BreakSlot.OFF_HAND,
                EquipmentDurabilityPolicy.resolveBreakSlot("main-1", "off-1", Map.of(), "off-1"));
        assertEquals(EquipmentDurabilityPolicy.BreakSlot.CHEST,
                EquipmentDurabilityPolicy.resolveBreakSlot(null, null,
                        Map.of("ARMOR_CHEST", "chest-1"), "chest-1"));
        assertEquals("equipment-broken:main-1:1", EquipmentDurabilityPolicy.breakCommitKey("main-1", 1));
        assertEquals("equipment-broken:main-1:2", EquipmentDurabilityPolicy.breakCommitKey("main-1", 2));
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentDurabilityPolicy.resolveBreakSlot(null, null, Map.of(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentDurabilityPolicy.breakCommitKey("main-1", 0));
    }
}
