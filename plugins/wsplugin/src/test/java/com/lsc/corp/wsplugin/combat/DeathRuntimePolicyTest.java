package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeathRuntimePolicyTest {
    @Test
    void injuryStacksSetDownedAndReviveHealth() {
        assertEquals(0.70, DeathRuntimePolicy.downedHealthFraction(1));
        assertEquals(0.525, DeathRuntimePolicy.downedHealthFraction(2));
        assertEquals(0.35, DeathRuntimePolicy.downedHealthFraction(3));
        assertEquals(0.30, DeathRuntimePolicy.reviveHealthFraction(1));
        assertEquals(0.25, DeathRuntimePolicy.reviveHealthFraction(2));
        assertEquals(0.20, DeathRuntimePolicy.reviveHealthFraction(3));
    }

    @Test
    void reviveTimeAndDownedDamageRespectCaps() {
        assertEquals(5_000L, DeathRuntimePolicy.reviveDurationMillis(1, 1.0));
        assertEquals(7_000L, DeathRuntimePolicy.reviveDurationMillis(2, 1.0));
        assertEquals(9_000L, DeathRuntimePolicy.reviveDurationMillis(3, 1.0));
        assertEquals(2_500L, DeathRuntimePolicy.reviveDurationMillis(1, 4.0));
        assertEquals(20.0, DeathRuntimePolicy.downedDamage(100.0, 0.75, 40.0));
        assertEquals(10.0, DeathRuntimePolicy.downedDamage(20.0, 0.50, 40.0));
    }

    @Test
    void fourthFatalHitSkipsDowned() {
        assertFalse(DeathRuntimePolicy.fatalInsteadOfDowned(2));
        assertTrue(DeathRuntimePolicy.fatalInsteadOfDowned(3));
        assertThrows(IllegalArgumentException.class, () -> DeathRuntimePolicy.downedHealthFraction(0));
    }
}
