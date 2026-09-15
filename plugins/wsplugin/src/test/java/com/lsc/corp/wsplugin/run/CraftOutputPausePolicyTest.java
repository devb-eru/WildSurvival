package com.lsc.corp.wsplugin.run;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CraftOutputPausePolicyTest {
    @Test
    void committedItemIsHeldOnlyWhileItsOwnTransactionIsPaused() {
        Map<String, RunSnapshot.ResourceTransactionState> transactions = new LinkedHashMap<>();
        var item = craft("item", "owner", "ITEM", "TOOL-CRUDE-PICKAXE", null, "COMMITTED");
        transactions.put(item.transactionId, item);

        assertTrue(CraftOutputPausePolicy.held(transactions, "owner", "ITEM",
                "TOOL-CRUDE-PICKAXE", null, "item"::equals));
        assertFalse(CraftOutputPausePolicy.held(transactions, "owner", "ITEM",
                "TOOL-CRUDE-PICKAXE", null, id -> false));
        assertFalse(CraftOutputPausePolicy.held(transactions, "other", "ITEM",
                "TOOL-CRUDE-PICKAXE", null, "item"::equals));
        assertFalse(CraftOutputPausePolicy.held(transactions, "owner", "MATERIAL",
                "TOOL-CRUDE-PICKAXE", null, "item"::equals));
    }

    @Test
    void equipmentHoldMatchesInstanceAndDoesNotHoldUncommittedOutput() {
        Map<String, RunSnapshot.ResourceTransactionState> transactions = new LinkedHashMap<>();
        var equipment = craft("equipment", "owner", "EQUIPMENT", "AXE", "instance-a", "COMMITTED");
        var processing = craft("processing", "owner", "MATERIAL", "WSR-WOOD", null, "PROCESSING");
        transactions.put(equipment.transactionId, equipment);
        transactions.put(processing.transactionId, processing);

        assertTrue(CraftOutputPausePolicy.held(transactions, "owner", "EQUIPMENT",
                "AXE", "instance-a", "equipment"::equals));
        assertFalse(CraftOutputPausePolicy.held(transactions, "owner", "EQUIPMENT",
                "AXE", "instance-b", "equipment"::equals));
        assertFalse(CraftOutputPausePolicy.held(transactions, "owner", "MATERIAL",
                "WSR-WOOD", null, "processing"::equals));
    }

    private static RunSnapshot.ResourceTransactionState craft(String id, String owner,
                                                               String type, String outputId,
                                                               String instanceId, String state) {
        RunSnapshot.ResourceTransactionState transaction = new RunSnapshot.ResourceTransactionState();
        transaction.transactionId = id;
        transaction.ownerUuid = owner;
        transaction.transactionKind = "CRAFT";
        transaction.outputType = type;
        transaction.outputId = outputId;
        transaction.outputInstanceId = instanceId;
        transaction.state = state;
        return transaction;
    }
}
