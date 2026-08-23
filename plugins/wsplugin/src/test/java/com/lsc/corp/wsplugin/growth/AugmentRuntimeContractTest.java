package com.lsc.corp.wsplugin.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AugmentRuntimeContractTest {
    @Test
    void coversTheClosedSeasonOneOpcodeVocabulary() {
        assertEquals(43, AugmentRuntimeContract.opcodes().size());
        assertEquals(AugmentRuntimeContract.Owner.COMBAT, AugmentRuntimeContract.owner("DODGE"));
        assertEquals(AugmentRuntimeContract.Owner.ECONOMY, AugmentRuntimeContract.owner("CRAFT_CONSERVE"));
        assertEquals(AugmentRuntimeContract.Owner.FACILITY, AugmentRuntimeContract.owner("RECONSTRUCTION"));
        assertFalse(AugmentRuntimeContract.supports("FUTURE_SILENT_EFFECT"));
        assertThrows(IllegalArgumentException.class,
                () -> AugmentRuntimeContract.owner("FUTURE_SILENT_EFFECT"));
    }
}
