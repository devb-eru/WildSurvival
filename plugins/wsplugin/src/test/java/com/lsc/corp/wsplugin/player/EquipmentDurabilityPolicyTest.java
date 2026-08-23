package com.lsc.corp.wsplugin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
