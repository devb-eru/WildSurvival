package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CombatMathTest {
    @Test
    void composesDefenceAugmentGroggyAndTestDamage() {
        assertEquals(200.0, CombatMath.outgoingDamage(100.0, 100.0, 2.0, 1.0, 2.0), 1.0e-9);
    }

    @Test
    void appliesIncomingMultiplierBeforeDamageReduction() {
        assertEquals(5.0, CombatMath.incomingDamage(10.0, 2.0, 0.75), 1.0e-9);
        assertThrows(IllegalArgumentException.class, () -> CombatMath.incomingDamage(10.0, 1.0, 1.0));
    }

    @Test
    void boundsCooldownMultiplier() {
        assertEquals(1L, CombatMath.cooldownTicks(13, 0.05));
        assertEquals(26L, CombatMath.cooldownTicks(13, 2.0));
        assertThrows(IllegalArgumentException.class, () -> CombatMath.cooldownTicks(13, 0.0));
    }
}
