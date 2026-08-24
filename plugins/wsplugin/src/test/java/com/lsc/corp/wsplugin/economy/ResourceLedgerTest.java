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

    @Test
    void persistsPersonalValidateReserveProcessCommitState() {
        RunSnapshot snapshot = runningSnapshot();
        RunSnapshot.PlayerState player = new RunSnapshot.PlayerState();
        player.uuid = "player-1";
        player.personalResources.put("IRON", 5);
        snapshot.players.put(player.uuid, player);

        assertEquals(ResourceLedger.ReserveResult.RESERVED, ResourceLedger.reserve(snapshot,
                "cost:one", "RCOST-ONE", "RS-ONE", ResourceLedger.Scope.PERSONAL,
                player.uuid, Map.of("IRON", 3), 10L));
        assertEquals(2, player.personalResources.get("IRON"));
        assertEquals("RESERVED", snapshot.resourceTransactions.get("cost:one").state);
        assertTrue(ResourceLedger.beginProcessing(snapshot, "cost:one", 20L));
        assertTrue(ResourceLedger.commit(snapshot, "cost:one", 30L,
                run -> run.researchNodes.put("RS-ONE", new RunSnapshot.ResearchNodeState())));
        assertEquals("COMMITTED", snapshot.resourceTransactions.get("cost:one").state);
        assertTrue(snapshot.committedKeys.contains("cost:one"));
        assertEquals(ResourceLedger.ReserveResult.ALREADY_COMMITTED, ResourceLedger.reserve(snapshot,
                "cost:one", "RCOST-ONE", "RS-ONE", ResourceLedger.Scope.PERSONAL,
                player.uuid, Map.of("IRON", 3), 40L));
        assertEquals(2, player.personalResources.get("IRON"));
    }

    @Test
    void cancelledReservationReturnsTheExactSourceResources() {
        RunSnapshot snapshot = runningSnapshot();
        snapshot.resources.put("FRAME", 2);

        assertEquals(ResourceLedger.ReserveResult.RESERVED, ResourceLedger.reserve(snapshot,
                "facility:one", "FCOST-ONE", "FAC-ONE", ResourceLedger.Scope.SHARED,
                null, Map.of("FRAME", 2), 10L));
        assertEquals(0, snapshot.resources.get("FRAME"));
        assertTrue(ResourceLedger.cancelReservation(snapshot, "facility:one", "USER_CANCEL"));
        assertEquals(2, snapshot.resources.get("FRAME"));
        assertEquals("CANCELLED", snapshot.resourceTransactions.get("facility:one").state);
    }

    private static RunSnapshot runningSnapshot() {
        RunSnapshot snapshot = new RunSnapshot();
        snapshot.runId = "test";
        snapshot.contentRevision = "ws-prototype-r1";
        snapshot.state = "RUNNING";
        return snapshot;
    }
}
