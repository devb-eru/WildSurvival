package com.lsc.corp.wsplugin.testlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TestValuePolicyTest {
    @Test
    void normalizesAndBoundsPlayerStats() {
        assertEquals("damage-reduction", TestValuePolicy.statId("DAMAGE_REDUCTION"));
        assertEquals(0.75, TestValuePolicy.statValue("damage-reduction", 0.75));
        assertThrows(IllegalArgumentException.class,
                () -> TestValuePolicy.statValue("damage-reduction", 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> TestValuePolicy.statValue("unknown", 1.0));
    }

    @Test
    void rejectsUnsafeFileIdentifiers() {
        assertEquals("boss-phase-2", TestValuePolicy.fileId("boss_phase_2"));
        assertThrows(IllegalArgumentException.class, () -> TestValuePolicy.fileId("../outside"));
        assertThrows(IllegalArgumentException.class, () -> TestValuePolicy.fileId(""));
    }
}
