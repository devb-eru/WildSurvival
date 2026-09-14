package com.lsc.corp.wsplugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalResourcePolicyTest {
    @Test
    void legacyPhysicalBalanceIsAdoptedOnceBeforeCredit() {
        RunSnapshot.PlayerState state = new RunSnapshot.PlayerState();
        int balance = PersonalResourcePolicy.credit(state, "WSR-WOOD", 2, 7,
                Map.of("WSR-WOOD", 5, "WSR-STONE", 3));

        assertEquals(7, balance);
        assertEquals(7, state.personalResources.get("WSR-WOOD"));
        assertEquals(3, state.personalResources.get("WSR-STONE"));
        assertEquals(7, state.pendingPhysicalItemCounts.get("RESOURCE:WSR-WOOD"));
        assertTrue(state.personalResourcesInitialized);
    }

    @Test
    void initializedLedgerNeverReimportsUnexpectedPhysicalItems() {
        RunSnapshot.PlayerState state = new RunSnapshot.PlayerState();
        state.personalResourcesInitialized = true;
        state.personalResources.put("WSR-WOOD", 4);

        assertEquals(5, PersonalResourcePolicy.credit(state, "WSR-WOOD", 1, 5,
                Map.of("WSR-WOOD", 99)));
        assertEquals(5, state.personalResources.get("WSR-WOOD"));
    }

    @Test
    void debitCannotGoNegativeAndLeavesStateUntouched() {
        RunSnapshot.PlayerState state = new RunSnapshot.PlayerState();
        state.personalResourcesInitialized = true;
        state.personalResources.put("WSR-IRON", 2);

        assertThrows(IllegalStateException.class, () -> PersonalResourcePolicy.debit(
                state, "WSR-IRON", 3, 0, Map.of()));
        assertEquals(2, state.personalResources.get("WSR-IRON"));
        assertTrue(state.pendingPhysicalItemCounts.isEmpty());
    }
}
