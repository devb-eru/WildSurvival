package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RescueBracePolicyTest {
    @Test
    void reservesOnlyTheHigherBraceWithoutStackingCharges() {
        RescueBracePolicy.Pending base = RescueBracePolicy.reserve(0.0, 0.10);
        assertEquals(1, base.charges());
        assertEquals(0.10, base.bonus());

        RescueBracePolicy.Pending reinforced = RescueBracePolicy.reserve(base.bonus(), 0.25);
        assertEquals(1, reinforced.charges());
        assertEquals(0.25, reinforced.bonus());

        RescueBracePolicy.Pending lowerCannotOverwrite = RescueBracePolicy.reserve(reinforced.bonus(), 0.10);
        assertEquals(1, lowerCannotOverwrite.charges());
        assertEquals(0.25, lowerCannotOverwrite.bonus());
    }

    @Test
    void consumesReservationOnlyOnFirstPositiveProgress() {
        RescueBracePolicy.Activation waiting = RescueBracePolicy.activate(1, 0.10, 0.0, 0.0);
        assertFalse(waiting.consumed());
        assertEquals(1, waiting.charges());

        RescueBracePolicy.Activation active = RescueBracePolicy.activate(1, 0.10, 0.0, 0.001);
        assertTrue(active.consumed());
        assertEquals(0, active.charges());
        assertEquals(0.0, active.pendingBonus());
        assertEquals(0.10, active.activeBonus());
        assertEquals(0.15, RescueBracePolicy.interruptFraction(active.activeBonus()), 1.0e-9);
    }
}
