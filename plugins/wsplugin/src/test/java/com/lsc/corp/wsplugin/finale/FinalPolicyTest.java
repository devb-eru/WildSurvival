package com.lsc.corp.wsplugin.finale;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FinalPolicyTest {
    @Test
    void majoritySupportsOneToFourSurvivors() {
        assertEquals(1, FinalPolicy.majority(1));
        assertEquals(2, FinalPolicy.majority(2));
        assertEquals(2, FinalPolicy.majority(3));
        assertEquals(3, FinalPolicy.majority(4));
    }

    @Test
    void purificationNeverRegressesBelowReachedCheckpoint() {
        assertEquals(0.0, FinalPolicy.applyPurificationOutput(0.0, 20));
        assertEquals(1200.0, FinalPolicy.applyPurificationOutput(1200.0, 20));
        assertEquals(2400.0, FinalPolicy.applyPurificationOutput(2400.25, 20));
        assertEquals(2401.25, FinalPolicy.applyPurificationOutput(2400.25, 70));
    }
}
