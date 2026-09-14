package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import org.junit.jupiter.api.Test;

class CombatCostMutationTest {
    @Test
    void crossbowShotCommitsApAndLoadedRoundTogether() {
        RunSnapshot.PlayerState state = activeState(40.0);
        state.crossbowLoadedAmmo = 2;

        CombatCostMutation.Result result = CombatCostMutation.apply(state,
                new CombatCostMutation.Request(7.5, CombatCostMutation.AmmoSource.MAGAZINE,
                        1, false, 0, 0, false));

        assertEquals(32.5, state.ap);
        assertEquals(1, state.crossbowLoadedAmmo);
        assertTrue(result.ammoConsumed());
        assertNull(result.physicalArrowExpected());
    }

    @Test
    void sourcePriorityIsMagazineThenLedgerThenPlainArrow() {
        RunSnapshot.PlayerState state = activeState(40.0);
        state.crossbowLoadedAmmo = 1;
        state.ammoLedger.put(AmmoLedgerPolicy.GENERAL_ARROW, 4);

        assertEquals(CombatCostMutation.AmmoSource.MAGAZINE,
                CombatCostMutation.selectArrowSource(state, 8, 1, true));
        assertEquals(CombatCostMutation.AmmoSource.LEDGER,
                CombatCostMutation.selectArrowSource(state, 8, 2, true));
        state.ammoLedger.clear();
        assertEquals(CombatCostMutation.AmmoSource.VANILLA_ARROW,
                CombatCostMutation.selectArrowSource(state, 8, 2, false));
    }

    @Test
    void vanillaArrowFallbackStoresAbsolutePhysicalCheckpoint() {
        RunSnapshot.PlayerState state = activeState(50.0);

        CombatCostMutation.Result result = CombatCostMutation.apply(state,
                new CombatCostMutation.Request(10.0, CombatCostMutation.AmmoSource.VANILLA_ARROW,
                        2, false, 7, 2, true));

        assertEquals(40.0, state.ap);
        assertEquals(2, state.crossbowLoadedAmmo);
        assertEquals(5, result.physicalArrowExpected());
        assertEquals(5, state.pendingPhysicalItemCounts.get(CombatCostMutation.VANILLA_ARROW_CHECKPOINT));
        assertTrue(state.tutorialSignals.contains("USED_SKILL"));
    }

    @Test
    void conservationStillRequiresASelectedSourceButDoesNotConsumeIt() {
        RunSnapshot.PlayerState state = activeState(30.0);
        state.ammoLedger.put(AmmoLedgerPolicy.GENERAL_ARROW, 1);

        CombatCostMutation.Result result = CombatCostMutation.apply(state,
                new CombatCostMutation.Request(5.0, CombatCostMutation.AmmoSource.LEDGER,
                        1, true, 0, 0, false));

        assertEquals(25.0, state.ap);
        assertEquals(1, state.ammoLedger.get(AmmoLedgerPolicy.GENERAL_ARROW));
        assertFalse(result.ammoConsumed());
        assertTrue(state.pendingPhysicalItemCounts.isEmpty());
    }

    @Test
    void exactTwoRoundReloadRejectsWhenMagazineHasOnlyOneFreeSlotWithoutPartialCost() {
        RunSnapshot.PlayerState state = activeState(30.0);
        state.crossbowLoadedAmmo = 5;
        state.ammoLedger.put(AmmoLedgerPolicy.GENERAL_ARROW, 4);

        assertThrows(IllegalStateException.class, () -> CombatCostMutation.apply(state,
                new CombatCostMutation.Request(8.0, CombatCostMutation.AmmoSource.LEDGER,
                        2, false, 0, 2, false)));

        assertEquals(30.0, state.ap);
        assertEquals(5, state.crossbowLoadedAmmo);
        assertEquals(4, state.ammoLedger.get(AmmoLedgerPolicy.GENERAL_ARROW));
    }

    @Test
    void insufficientApRejectsBeforeAmmoOrCheckpointMutation() {
        RunSnapshot.PlayerState state = activeState(4.0);
        state.ammoLedger.put(AmmoLedgerPolicy.GENERAL_ARROW, 3);

        assertThrows(IllegalStateException.class, () -> CombatCostMutation.apply(state,
                new CombatCostMutation.Request(5.0, CombatCostMutation.AmmoSource.LEDGER,
                        1, false, 0, 0, false)));

        assertEquals(4.0, state.ap);
        assertEquals(3, state.ammoLedger.get(AmmoLedgerPolicy.GENERAL_ARROW));
        assertTrue(state.pendingPhysicalItemCounts.isEmpty());
    }

    private static RunSnapshot.PlayerState activeState(double ap) {
        RunSnapshot.PlayerState state = new RunSnapshot.PlayerState();
        state.lifeState = "ACTIVE";
        state.ap = ap;
        state.maxAp = 100;
        return state;
    }
}
