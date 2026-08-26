package com.lsc.corp.wsplugin.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TestTransactionPauseGateTest {
    @Test
    void armsAndConsumesOnlyTheMatchingCheckpoint() {
        TestTransactionPauseGate gate = new TestTransactionPauseGate();
        gate.arm("reserved");

        assertFalse(gate.checkpoint("cost:one", TestTransactionPauseGate.Phase.PROCESSING));
        assertTrue(gate.checkpoint("cost:one", TestTransactionPauseGate.Phase.RESERVED));
        assertTrue(gate.blocks("cost:one"));
        assertFalse(gate.blocks("cost:two"));
        assertFalse(gate.checkpoint("cost:two", TestTransactionPauseGate.Phase.RESERVED));

        TestTransactionPauseGate.Status status = gate.status();
        assertNull(status.armedPhase());
        assertEquals("cost:one", status.pausedTransactionId());
        assertEquals("RESERVED", status.pausedPhase());
    }

    @Test
    void rearmingAndClearingRemoveThePreviousPause() {
        TestTransactionPauseGate gate = new TestTransactionPauseGate();
        gate.arm("PROCESSING");
        gate.checkpoint("cost:one", TestTransactionPauseGate.Phase.PROCESSING);
        gate.arm("RESERVED");

        assertFalse(gate.blocks("cost:one"));
        assertEquals("RESERVED", gate.status().armedPhase());

        gate.clear();
        assertNull(gate.status().armedPhase());
        assertNull(gate.status().pausedTransactionId());
        assertNull(gate.status().pausedPhase());
    }

    @Test
    void rejectsUnknownPhase() {
        TestTransactionPauseGate gate = new TestTransactionPauseGate();
        assertThrows(IllegalArgumentException.class, () -> gate.arm("COMMITTED"));
    }
}
