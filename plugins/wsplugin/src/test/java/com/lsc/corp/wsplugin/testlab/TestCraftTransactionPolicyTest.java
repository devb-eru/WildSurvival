package com.lsc.corp.wsplugin.testlab;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class TestCraftTransactionPolicyTest {
    @Test
    void frozenClockTieSelectsLastDurablyInsertedCraftForOwner() {
        Map<String, RunSnapshot.ResourceTransactionState> transactions = new LinkedHashMap<>();
        RunSnapshot.ResourceTransactionState first = transaction("first", "owner", "CRAFT", 10L);
        RunSnapshot.ResourceTransactionState unrelated = transaction("research", "owner", null, 20L);
        RunSnapshot.ResourceTransactionState otherOwner = transaction("other", "other", "CRAFT", 30L);
        RunSnapshot.ResourceTransactionState latest = transaction("latest", "owner", "CRAFT", 10L);
        transactions.put(first.transactionId, first);
        transactions.put(unrelated.transactionId, unrelated);
        transactions.put(otherOwner.transactionId, otherOwner);
        transactions.put(latest.transactionId, latest);

        assertSame(latest, TestCraftTransactionPolicy.latest(transactions, "owner"));
        assertSame(otherOwner, TestCraftTransactionPolicy.latest(transactions, "other"));
        assertNull(TestCraftTransactionPolicy.latest(transactions, "missing"));
    }

    private static RunSnapshot.ResourceTransactionState transaction(
            String id, String owner, String kind, long validatedAt) {
        RunSnapshot.ResourceTransactionState state = new RunSnapshot.ResourceTransactionState();
        state.transactionId = id;
        state.ownerUuid = owner;
        state.transactionKind = kind;
        state.validatedAtEpochMs = validatedAt;
        return state;
    }
}
