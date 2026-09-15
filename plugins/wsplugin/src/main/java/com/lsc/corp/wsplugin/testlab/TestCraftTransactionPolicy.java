package com.lsc.corp.wsplugin.testlab;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.Map;

/** The transaction map retains durable insertion order even when the Test Lab clock is frozen. */
final class TestCraftTransactionPolicy {
    private TestCraftTransactionPolicy() { }

    static RunSnapshot.ResourceTransactionState latest(
            Map<String, RunSnapshot.ResourceTransactionState> transactions, String ownerUuid) {
        if (transactions == null) return null;
        return transactions.values().stream()
                .filter(candidate -> "CRAFT".equals(candidate.transactionKind))
                .filter(candidate -> ownerUuid.equals(candidate.ownerUuid))
                .reduce((older, newer) -> newer)
                .orElse(null);
    }
}
