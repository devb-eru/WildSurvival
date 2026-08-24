package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApStimPulsePolicyTest {
    @Test
    void completesFivePersistablePulsesWithoutDuplication() {
        ApStimPulsePolicy.State state = ApStimPulsePolicy.start(0.0, 100.0);
        assertEquals(10.0, state.ap());
        assertTrue(state.durableCheckpoint());

        int checkpoints = 1;
        for (int tick = 0; tick < 100; tick++) {
            state = ApStimPulsePolicy.tick(state.ap(), state.maxAp(), state.pulsesRemaining(),
                    state.ticksUntilNextPulse());
            if (state.durableCheckpoint()) checkpoints++;
        }
        assertEquals(25.0, state.ap());
        assertEquals(0, state.pulsesRemaining());
        assertEquals(6, checkpoints);
    }

    @Test
    void resumedCheckpointKeepsExactlyTheRemainingPulses() {
        ApStimPulsePolicy.State state = ApStimPulsePolicy.start(20.0, 100.0);
        for (int tick = 0; tick < 40; tick++) {
            state = ApStimPulsePolicy.tick(state.ap(), state.maxAp(), state.pulsesRemaining(),
                    state.ticksUntilNextPulse());
        }
        assertEquals(36.0, state.ap());
        assertEquals(3, state.pulsesRemaining());

        ApStimPulsePolicy.State restored = new ApStimPulsePolicy.State(state.ap(), state.maxAp(),
                state.pulsesRemaining(), state.ticksUntilNextPulse(), false);
        for (int tick = 0; tick <= 60; tick++) {
            restored = ApStimPulsePolicy.tick(restored.ap(), restored.maxAp(), restored.pulsesRemaining(),
                    restored.ticksUntilNextPulse());
        }
        assertEquals(45.0, restored.ap());
        assertEquals(0, restored.pulsesRemaining());
        assertFalse(restored.durableCheckpoint());
    }
}
