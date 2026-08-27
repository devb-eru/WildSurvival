package com.lsc.corp.wsplugin.research;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ResearchTransactionRecovery {
    private ResearchTransactionRecovery() {}

    static void reconcile(RunSnapshot run,
                          Map<String, ProductionContentCatalog.ResearchEntry> researchById) {
        if (run.resourceTransactions == null) return;
        for (RunSnapshot.ResourceTransactionState transaction : run.resourceTransactions.values()) {
            if (!isResearchCost(transaction)) continue;
            ProductionContentCatalog.ResearchEntry entry = transaction.targetId == null
                    ? null : researchById.get(transaction.targetId);
            RunSnapshot.ResearchNodeState state = entry == null ? null : run.researchNodes.get(transaction.targetId);
            if (state == null) continue;
            switch (transaction.state == null ? "" : transaction.state) {
                case "COMMITTED" -> {
                    state.state = "UNLOCKED";
                    state.unlockCommitted = true;
                }
                case "PROCESSING", "RESERVED" -> restoreActiveState(state, transaction, entry);
                case "CANCELLED" -> resetCancelledState(state);
                default -> { }
            }
        }
    }

    static List<String> refundableOrphanIds(RunSnapshot run,
                                             Map<String, ProductionContentCatalog.ResearchEntry> researchById) {
        List<String> result = new ArrayList<>();
        if (run.resourceTransactions == null) return result;
        for (RunSnapshot.ResourceTransactionState transaction : run.resourceTransactions.values()) {
            if (isResearchCost(transaction)
                    && ("RESERVED".equals(transaction.state) || "PROCESSING".equals(transaction.state))
                    && (transaction.targetId == null || !researchById.containsKey(transaction.targetId))) {
                result.add(transaction.transactionId);
            }
        }
        return result;
    }

    private static void restoreActiveState(RunSnapshot.ResearchNodeState state,
                                           RunSnapshot.ResourceTransactionState transaction,
                                           ProductionContentCatalog.ResearchEntry entry) {
        if (state.durationMillis <= 0L) {
            state.durationMillis = Math.max(1L, entry.durationSeconds() * 1_000L);
        }
        state.processedMillis = Math.max(0L, Math.min(state.durationMillis, state.processedMillis));
        if (state.startedAtEpochMs <= 0L) state.startedAtEpochMs = transaction.reservedAtEpochMs;
        if ((state.startedByUuid == null || state.startedByUuid.isBlank())
                && transaction.ownerUuid != null && !transaction.ownerUuid.isBlank()) {
            state.startedByUuid = transaction.ownerUuid;
        }
        if (state.reservedCost == null) state.reservedCost = new LinkedHashMap<>();
        if (state.reservedCost.isEmpty() && transaction.reservedResources != null) {
            state.reservedCost.putAll(transaction.reservedResources);
        }
        state.state = "PROCESSING".equals(transaction.state) ? "PROCESSING" : "QUEUED";
    }

    private static void resetCancelledState(RunSnapshot.ResearchNodeState state) {
        if (state.unlockCommitted) {
            state.state = "UNLOCKED";
            return;
        }
        state.state = "HIDDEN";
        state.startedByUuid = null;
        state.startedAtEpochMs = 0L;
        state.completesAtEpochMs = 0L;
        state.completedAtEpochMs = 0L;
        state.durationMillis = 0L;
        state.processedMillis = 0L;
        if (state.reservedCost == null) state.reservedCost = new LinkedHashMap<>();
        else state.reservedCost.clear();
    }

    private static boolean isResearchCost(RunSnapshot.ResourceTransactionState transaction) {
        return transaction != null && transaction.costId != null && transaction.costId.startsWith("RCOST-");
    }
}
