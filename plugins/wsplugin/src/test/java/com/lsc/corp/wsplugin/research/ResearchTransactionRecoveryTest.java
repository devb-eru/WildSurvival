package com.lsc.corp.wsplugin.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResearchTransactionRecoveryTest {
    @Test
    void reconstructsMissingProcessingMetadataFromLockedCatalogAndLedger() {
        RunSnapshot run = new RunSnapshot();
        RunSnapshot.ResearchNodeState state = new RunSnapshot.ResearchNodeState();
        state.researchId = "RS-ONE";
        run.researchNodes.put(state.researchId, state);
        RunSnapshot.ResourceTransactionState transaction = transaction("PROCESSING", "RS-ONE");
        transaction.ownerUuid = "player-one";
        transaction.reservedAtEpochMs = 100L;
        transaction.reservedResources.put("IRON", 3);
        run.resourceTransactions.put(transaction.transactionId, transaction);

        ResearchTransactionRecovery.reconcile(run, Map.of("RS-ONE", entry("RS-ONE", 12)));

        assertEquals("PROCESSING", state.state);
        assertEquals(12_000L, state.durationMillis);
        assertEquals(100L, state.startedAtEpochMs);
        assertEquals("player-one", state.startedByUuid);
        assertEquals(Map.of("IRON", 3), state.reservedCost);
    }

    @Test
    void cancelledAttemptReleasesResearchNodeForAvailabilityRefresh() {
        RunSnapshot run = new RunSnapshot();
        RunSnapshot.ResearchNodeState state = new RunSnapshot.ResearchNodeState();
        state.researchId = "RS-ONE";
        state.state = "PROCESSING";
        state.durationMillis = 12_000L;
        state.processedMillis = 4_000L;
        state.reservedCost.put("IRON", 3);
        run.researchNodes.put(state.researchId, state);
        RunSnapshot.ResourceTransactionState transaction = transaction("CANCELLED", "RS-ONE");
        run.resourceTransactions.put(transaction.transactionId, transaction);

        ResearchTransactionRecovery.reconcile(run, Map.of("RS-ONE", entry("RS-ONE", 12)));

        assertEquals("HIDDEN", state.state);
        assertEquals(0L, state.durationMillis);
        assertEquals(0L, state.processedMillis);
        assertTrue(state.reservedCost.isEmpty());
    }

    @Test
    void identifiesOnlyUncommittedTransactionsWhoseResearchTargetNoLongerExists() {
        RunSnapshot run = new RunSnapshot();
        RunSnapshot.ResourceTransactionState orphan = transaction("PROCESSING", "RS-MISSING");
        run.resourceTransactions.put(orphan.transactionId, orphan);
        RunSnapshot.ResourceTransactionState committed = transaction("COMMITTED", "RS-OLD");
        run.resourceTransactions.put(committed.transactionId, committed);
        RunSnapshot.ResourceTransactionState known = transaction("RESERVED", "RS-ONE");
        run.resourceTransactions.put(known.transactionId, known);

        assertEquals(List.of(orphan.transactionId),
                ResearchTransactionRecovery.refundableOrphanIds(run, Map.of("RS-ONE", entry("RS-ONE", 12))));
    }

    private static RunSnapshot.ResourceTransactionState transaction(String state, String targetId) {
        RunSnapshot.ResourceTransactionState transaction = new RunSnapshot.ResourceTransactionState();
        transaction.transactionId = "cost:research:" + targetId;
        transaction.costId = "RCOST-" + targetId;
        transaction.targetId = targetId;
        transaction.ledgerScope = "SHARED";
        transaction.state = state;
        return transaction;
    }

    private static ProductionContentCatalog.ResearchEntry entry(String id, int durationSeconds) {
        return new ProductionContentCatalog.ResearchEntry(id, "RCOST-" + id, 1, "NONE", "NONE",
                Map.of("IRON", 3), durationSeconds, "NONE", List.of("LOCKED", "PROCESSING", "UNLOCKED"));
    }
}
