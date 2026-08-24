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
        assertEquals(10.0, ConsumableRuntimePolicy.AP_STIM_INITIAL_AP);
        assertEquals(3.0, ConsumableRuntimePolicy.AP_STIM_PULSE_AP);
        assertEquals(5, ConsumableRuntimePolicy.AP_STIM_PULSE_COUNT);
        assertEquals(20L, ConsumableRuntimePolicy.AP_STIM_PULSE_INTERVAL_TICKS);
        assertEquals(25.0, ConsumableRuntimePolicy.apStimTotalAp());
        assertEquals(0.10, ConsumableRuntimePolicy.BASE_RESCUE_BRACE_THRESHOLD_BONUS);
        assertEquals(0.25, ConsumableRuntimePolicy.REINFORCED_RESCUE_BRACE_THRESHOLD_BONUS);
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
