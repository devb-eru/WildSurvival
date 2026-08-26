package com.lsc.corp.wsplugin.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.economy.ResourceLedger;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DurableRunMutationTest {
    @Test
    void callbackFailureCannotLeakReservedCostOrDomainMutationIntoAuthoritativeSnapshot() {
        RunSnapshot source = runningSnapshot();
        source.resources.put("WOOD", 5);
        RunSnapshot.ResearchNodeState research = new RunSnapshot.ResearchNodeState();
        research.researchId = "RS-01";
        research.state = "AVAILABLE";
        source.researchNodes.put(research.researchId, research);

        assertThrows(IllegalStateException.class, () -> DurableRunMutation.execute(source, candidate -> {
            assertEquals(ResourceLedger.ReserveResult.RESERVED, ResourceLedger.reserve(candidate,
                    "cost:research:RCOST-01", "RCOST-01", "RS-01", ResourceLedger.Scope.SHARED,
                    null, Map.of("WOOD", 3), 10L));
            candidate.researchNodes.get("RS-01").state = "QUEUED";
            throw new IllegalStateException("simulated callback failure");
        }, result -> true, ignored -> { }));

        assertEquals(5, source.resources.get("WOOD"));
        assertTrue(source.resourceTransactions.isEmpty());
        assertEquals("AVAILABLE", source.researchNodes.get("RS-01").state);
    }

    @Test
    void persistenceFailureCannotPublishCandidateIntoAuthoritativeSnapshot() {
        RunSnapshot source = runningSnapshot();
        source.resources.put("FRAME", 4);

        assertThrows(IOException.class, () -> DurableRunMutation.execute(source, candidate -> {
            assertEquals(ResourceLedger.ReserveResult.RESERVED, ResourceLedger.reserve(candidate,
                    "cost:facility:FCOST-01", "FCOST-01", "FAC-01", ResourceLedger.Scope.SHARED,
                    null, Map.of("FRAME", 2), 20L));
            return true;
        }, Boolean.TRUE::equals, candidate -> {
            candidate.version++;
            throw new IOException("simulated save failure");
        }));

        assertEquals(0L, source.version);
        assertEquals(4, source.resources.get("FRAME"));
        assertTrue(source.resourceTransactions.isEmpty());
    }

    @Test
    void successfulPersistenceReturnsDetachedCandidateForAtomicAdoption() throws IOException {
        RunSnapshot source = runningSnapshot();
        source.resources.put("IRON", 5);
        AtomicBoolean saved = new AtomicBoolean();

        DurableRunMutation.Outcome<ResourceLedger.ReserveResult> outcome = DurableRunMutation.execute(source,
                candidate -> ResourceLedger.reserve(candidate, "cost:one", "RCOST-ONE", "RS-ONE",
                        ResourceLedger.Scope.SHARED, null, Map.of("IRON", 3), 30L),
                result -> result == ResourceLedger.ReserveResult.RESERVED,
                candidate -> {
                    candidate.version++;
                    saved.set(true);
                });

        assertTrue(outcome.persisted());
        assertTrue(saved.get());
        assertNotSame(source, outcome.snapshot());
        assertEquals(5, source.resources.get("IRON"));
        assertEquals(2, outcome.snapshot().resources.get("IRON"));
        assertEquals(1L, outcome.snapshot().version);
    }

    @Test
    void rejectedTransitionSkipsPersistenceAndKeepsOriginalIdentity() throws IOException {
        RunSnapshot source = runningSnapshot();
        AtomicBoolean saved = new AtomicBoolean();

        DurableRunMutation.Outcome<Boolean> outcome = DurableRunMutation.execute(source, candidate -> false,
                Boolean.TRUE::equals, candidate -> saved.set(true));

        assertFalse(outcome.persisted());
        assertFalse(saved.get());
        assertSame(source, outcome.snapshot());
    }

    private static RunSnapshot runningSnapshot() {
        RunSnapshot snapshot = new RunSnapshot();
        snapshot.runId = "test";
        snapshot.contentRevision = "ws-content-r2.1";
        snapshot.runType = "TEST";
        snapshot.state = "RUNNING";
        return snapshot;
    }
}
