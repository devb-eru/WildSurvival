package com.lsc.corp.wsplugin.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatusRuntimePolicyTest {
    @Test
    void resistanceAndTenacityUseTheirIndependentCaps() {
        assertEquals(0.65, StatusRuntimePolicy.applicationChance("RESISTIBLE", 0.70, 0.15, 20.0), 0.0001);
        assertEquals(0.10, StatusRuntimePolicy.applicationChance("RESISTIBLE", 0.10, 0.0, 90.0), 0.0001);
        assertEquals(0.95, StatusRuntimePolicy.applicationChance("RESISTIBLE", 1.0, 0.2, 0.0), 0.0001);
        assertEquals(5556L, StatusRuntimePolicy.durationMillis(10.0, 10.0, 1.0, 80.0, true, 1.0, false));
    }

    @Test
    void controlChainsBecomeReducedThenImmune() {
        assertEquals(1.0, StatusRuntimePolicy.hardControlMultiplier(1));
        assertEquals(0.5, StatusRuntimePolicy.hardControlMultiplier(2));
        assertEquals(0.0, StatusRuntimePolicy.hardControlMultiplier(3));
        assertEquals(1.0, StatusRuntimePolicy.actionLockMultiplier(1));
        assertEquals(0.70, StatusRuntimePolicy.actionLockMultiplier(2));
        assertEquals(0.40, StatusRuntimePolicy.actionLockMultiplier(3));
        assertEquals(0.0, StatusRuntimePolicy.actionLockMultiplier(4));
    }

    @Test
    void hardControlKeepsQuarterSecondMinimumAndBossConversionIsStable() {
        assertEquals(250L, StatusRuntimePolicy.durationMillis(0.1, 1.0, 0.5, 80.0, true, 0.5, true));
        assertEquals(0.03, StatusRuntimePolicy.bossBreakFraction(2.0, 1.0, false), 0.0001);
        assertEquals(0.02, StatusRuntimePolicy.bossBreakFraction(2.0, 1.0, true), 0.0001);
        assertThrows(IllegalArgumentException.class,
                () -> StatusRuntimePolicy.durationMillis(-1.0, 1.0, 1.0, 0.0, true, 1.0, false));
    }

    @Test
    void cleanseOrdersControlBeforeDotBeforeDebuff() {
        assertEquals(1, StatusRuntimePolicy.cleansePriority(1, true, false));
        assertEquals(12, StatusRuntimePolicy.cleansePriority(2, false, true));
        assertEquals(23, StatusRuntimePolicy.cleansePriority(3, false, false));
    }

    @Test
    void tauntOnlyAllowsTheLockedHostileTarget() {
        UUID locked = UUID.randomUUID();
        assertTrue(StatusRuntimePolicy.tauntAllows(null, UUID.randomUUID()));
        assertTrue(StatusRuntimePolicy.tauntAllows(locked, locked));
        assertFalse(StatusRuntimePolicy.tauntAllows(locked, UUID.randomUUID()));
    }
}
