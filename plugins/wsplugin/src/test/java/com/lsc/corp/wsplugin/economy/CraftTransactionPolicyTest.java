package com.lsc.corp.wsplugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CraftTransactionPolicyTest {
    @Test
    void reserveAndCommitMaterialOutputAreOneIdempotentEnvelope() {
        RunSnapshot run = running();
        RunSnapshot.PlayerState player = player(run);
        player.personalResources.put("WSR-WOOD", 5);

        String id = "craft:p:one";
        assertEquals(ResourceLedger.ReserveResult.RESERVED, ResourceLedger.reserve(run, id,
                "R-ONE", "WSR-STONE", ResourceLedger.Scope.PERSONAL, "p",
                Map.of("WSR-WOOD", 3), 10L));
        CraftTransactionPolicy.stamp(run.resourceTransactions.get(id), "grid", "MATERIAL",
                "WSR-STONE", 2, null, null, 1.0);
        assertEquals(2, player.personalResources.get("WSR-WOOD"));
        assertTrue(ResourceLedger.beginProcessing(run, id, 20L));
        assertTrue(ResourceLedger.commit(run, id, 30L, snapshot ->
                CraftTransactionPolicy.applyOutput(snapshot.players.get("p"),
                        snapshot.resourceTransactions.get(id), 0, null)));

        assertEquals(2, player.personalResources.get("WSR-STONE"));
        assertEquals(2, player.pendingPhysicalItemCounts.get("RESOURCE:WSR-STONE"));
        assertFalse(ResourceLedger.commit(run, id, 40L, snapshot ->
                CraftTransactionPolicy.applyOutput(snapshot.players.get("p"),
                        snapshot.resourceTransactions.get(id), 0, null)));
        assertEquals(2, player.personalResources.get("WSR-STONE"));
    }

    @Test
    void registeredAndEquipmentOutputsKeepExactRecoveryIdentity() {
        RunSnapshot run = running();
        RunSnapshot.PlayerState player = player(run);
        RunSnapshot.ResourceTransactionState item = transaction("R-I", "ITEM", "WSI-ONE", 3, null);
        CraftTransactionPolicy.applyOutput(player, item, 4, null);
        assertEquals(3, player.pendingRegisteredItems.get("WSI-ONE"));
        assertEquals(7, player.pendingPhysicalItemCounts.get("REGISTERED:WSI-ONE"));

        RunSnapshot.ResourceTransactionState equipment = transaction(
                "R-E", "EQUIPMENT", "EQL-W01", 1, "instance-1");
        RunSnapshot.EquipmentInstanceState instance = new RunSnapshot.EquipmentInstanceState();
        instance.instanceId = "instance-1";
        instance.templateId = "EQL-W01";
        CraftTransactionPolicy.applyOutput(player, equipment, 0, instance);
        assertTrue(player.pendingEquipmentInstanceIds.contains("instance-1"));
        assertEquals("EQL-W01", player.equipmentInstances.get("instance-1").templateId);
    }

    @Test
    void alteredOutputSignatureIsRejectedBeforeGrant() {
        RunSnapshot.PlayerState player = new RunSnapshot.PlayerState();
        RunSnapshot.ResourceTransactionState transaction = transaction(
                "R-I", "ITEM", "WSI-ONE", 1, null);
        transaction.outputAmount = 2;
        assertThrows(IllegalStateException.class,
                () -> CraftTransactionPolicy.applyOutput(player, transaction, 0, null));
        assertTrue(player.pendingRegisteredItems.isEmpty());
    }

    private static RunSnapshot.ResourceTransactionState transaction(String recipeId, String type,
                                                                     String outputId, int amount,
                                                                     String instanceId) {
        RunSnapshot.ResourceTransactionState transaction = new RunSnapshot.ResourceTransactionState();
        transaction.costId = recipeId;
        CraftTransactionPolicy.stamp(transaction, "grid", type, outputId, amount,
                instanceId, null, 1.0);
        return transaction;
    }

    private static RunSnapshot running() {
        RunSnapshot run = new RunSnapshot();
        run.runId = "run";
        run.state = "RUNNING";
        return run;
    }

    private static RunSnapshot.PlayerState player(RunSnapshot run) {
        RunSnapshot.PlayerState player = new RunSnapshot.PlayerState();
        player.uuid = "p";
        player.personalResourcesInitialized = true;
        run.players.put("p", player);
        return player;
    }
}
