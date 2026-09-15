package com.lsc.corp.wsplugin.run;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Prevents unrelated reconcilers from bypassing a Test Lab durable COMMITTED pause. */
final class CraftOutputPausePolicy {
    private CraftOutputPausePolicy() { }

    static boolean held(Map<String, RunSnapshot.ResourceTransactionState> transactions,
                        String ownerUuid, String outputType, String outputId,
                        String outputInstanceId, Predicate<String> pausedTransaction) {
        if (transactions == null || ownerUuid == null || outputType == null || outputId == null) return false;
        return transactions.values().stream()
                .filter(transaction -> "CRAFT".equals(transaction.transactionKind))
                .filter(transaction -> "COMMITTED".equals(transaction.state))
                .filter(transaction -> ownerUuid.equals(transaction.ownerUuid))
                .filter(transaction -> outputType.equals(transaction.outputType))
                .filter(transaction -> outputId.equals(transaction.outputId))
                .filter(transaction -> !"EQUIPMENT".equals(outputType)
                        || Objects.equals(outputInstanceId, transaction.outputInstanceId))
                .anyMatch(transaction -> pausedTransaction.test(transaction.transactionId));
    }
}
