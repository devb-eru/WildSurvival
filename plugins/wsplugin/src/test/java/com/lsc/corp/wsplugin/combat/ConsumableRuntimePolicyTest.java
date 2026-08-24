package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConsumableRuntimePolicyTest {
    @Test
    void exposesExactCombatLimits() {
        assertEquals(2, ConsumableRuntimePolicy.combatLimit("WSI-CONS-PURIFY_AMPOULE"));
        assertEquals(1, ConsumableRuntimePolicy.combatLimit("WSI-CONS-AP_STIM"));
        assertEquals(1, ConsumableRuntimePolicy.combatLimit("WSI-CONS-BIO_SHIELD_AMPOULE"));
        assertEquals(Integer.MAX_VALUE, ConsumableRuntimePolicy.combatLimit("WSI-CONS-BANDAGE"));
    }

    @Test
    void preservesExactStackAndTimingContracts() {
        assertEquals(1, ConsumableRuntimePolicy.removableStacks("WSI-CONS-BANDAGE"));
        assertEquals(2, ConsumableRuntimePolicy.removableStacks("WSI-CONS-ANTIDOTE_INJECTION"));
        assertEquals(160L, ConsumableRuntimePolicy.rationUseTicks(false));
        assertEquals(240L, ConsumableRuntimePolicy.rationUseTicks(true));
        assertEquals(0.75, ConsumableRuntimePolicy.COOLING_BURN_DURATION_MULTIPLIER);
    }

    @Test
    void neuralStabilizerRejectsHardControlSelfUse() {
        assertFalse(ConsumableRuntimePolicy.neuralSelfUseAllowed(true));
        assertTrue(ConsumableRuntimePolicy.neuralSelfUseAllowed(false));
        assertEquals(java.util.List.of("ROOT", "SILENCE", "DISARM"),
                ConsumableRuntimePolicy.neuralCleansePriority());
    }
}
