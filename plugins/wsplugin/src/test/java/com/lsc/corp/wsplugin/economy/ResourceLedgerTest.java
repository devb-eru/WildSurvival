package com.lsc.corp.wsplugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceLedgerTest {
    @Test
    void debitsWithoutGoingNegativeAndRejectsDuplicate() {
        RunSnapshot snapshot = runningSnapshot();
        snapshot.resources.put("IRON", 3);

        assertTrue(ResourceLedger.reserveAndMutate(snapshot, "craft-1", Map.of("IRON", 3),
                run -> run.committedKeys.add("craft-1")));
        assertEquals(0, snapshot.resources.get("IRON"));
        assertFalse(ResourceLedger.reserveAndMutate(snapshot, "craft-1", Map.of("IRON", 3), run -> { }));
        assertFalse(ResourceLedger.reserveAndMutate(snapshot, "craft-2", Map.of("IRON", 1), run -> { }));
        assertEquals(0, snapshot.resources.get("IRON"));
    }

    @Test
    void restoresReservedBalanceWhenMutationFails() {
        RunSnapshot snapshot = runningSnapshot();
        snapshot.resources.put("WOOD", 4);

        assertThrows(IllegalStateException.class, () -> ResourceLedger.reserveAndMutate(snapshot, "craft-fail",
                Map.of("WOOD", 3), run -> { throw new IllegalStateException("simulated failure"); }));
        assertEquals(4, snapshot.resources.get("WOOD"));
        assertFalse(snapshot.committedKeys.contains("craft-fail"));
    }

    private static RunSnapshot runningSnapshot() {
        RunSnapshot snapshot = new RunSnapshot();
        snapshot.runId = "test";
        snapshot.contentRevision = "ws-prototype-r1";
        snapshot.state = "RUNNING";
        return snapshot;
    }
}
