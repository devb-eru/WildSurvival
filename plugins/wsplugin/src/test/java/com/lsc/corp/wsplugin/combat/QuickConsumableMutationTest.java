package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import org.junit.jupiter.api.Test;

class QuickConsumableMutationTest {
    @Test
    void commitsApStimCostUseCountAndPulseStateTogether() {
        RunSnapshot.PlayerState state = activeState(50.0);

        QuickConsumableMutation.commitUseCost(state, "WSI-CONS-AP_STIM", "combat-4", 10.0);
        QuickConsumableMutation.applyDirectDurableEffect(state, "WSI-CONS-AP_STIM");

        assertEquals(50.0, state.ap);
        assertEquals(1, state.quickItemUsesByCombat.get("combat-4:WSI-CONS-AP_STIM"));
        assertEquals(5, state.apStimPulsesRemaining);
        assertEquals(20, state.apStimTicksUntilNextPulse);
    }

    @Test
    void directBraceUseKeepsOnlyTheHigherPendingBonus() {
        RunSnapshot.PlayerState state = activeState(30.0);

        QuickConsumableMutation.commitUseCost(state, "WSI-CONS-RESCUE_BRACE", "combat-2", 0.0);
        QuickConsumableMutation.applyDirectDurableEffect(state, "WSI-CONS-RESCUE_BRACE");
        QuickConsumableMutation.applyDirectDurableEffect(state, "WSI-CONS-REINFORCED_RESCUE_BRACE");

        assertEquals(1, state.rescueBraceCharges);
        assertEquals(0.25, state.rescueInterruptThresholdBonus);
    }

    @Test
    void rejectedApCostLeavesEveryDurableFieldUntouched() {
        RunSnapshot.PlayerState state = activeState(4.0);

        assertThrows(IllegalStateException.class, () -> QuickConsumableMutation.commitUseCost(
                state, "WSI-CONS-BANDAGE", "combat-1", 18.0));

        assertEquals(4.0, state.ap);
        assertEquals(0, state.quickItemUsesByCombat.size());
    }

    @Test
    void rejectedMissingCombatScopeDoesNotSpendAp() {
        RunSnapshot.PlayerState state = activeState(30.0);

        assertThrows(IllegalArgumentException.class, () -> QuickConsumableMutation.commitUseCost(
                state, "WSI-CONS-BANDAGE", "", 18.0));

        assertEquals(30.0, state.ap);
        assertEquals(0, state.quickItemUsesByCombat.size());
    }

    private static RunSnapshot.PlayerState activeState(double ap) {
        RunSnapshot.PlayerState state = new RunSnapshot.PlayerState();
        state.lifeState = "ACTIVE";
        state.ap = ap;
        state.maxAp = 100;
        return state;
    }
}
