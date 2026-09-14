package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

final class CraftTransactionPolicy {
    static final String KIND = "CRAFT";
    private static final String RESOURCE_CHECKPOINT_PREFIX = "RESOURCE:";
    private static final String REGISTERED_CHECKPOINT_PREFIX = "REGISTERED:";

    private CraftTransactionPolicy() { }

    static String outputSignature(String recipeId, String outputType, String outputId,
                                  int outputAmount, String outputInstanceId) {
        return recipeId + "|" + outputType + "|" + outputId + "|" + outputAmount + "|"
                + (outputInstanceId == null ? "" : outputInstanceId);
    }

    static void stamp(RunSnapshot.ResourceTransactionState transaction, String inputSignature,
                      String outputType, String outputId, int outputAmount,
                      String outputInstanceId, String baseInstanceId, double durabilityRatio) {
        if (transaction == null || outputType == null || outputId == null || outputAmount < 1) {
            throw new IllegalArgumentException("Incomplete craft transaction envelope");
        }
        transaction.transactionKind = KIND;
        transaction.inputSignature = inputSignature;
        transaction.outputType = outputType;
        transaction.outputId = outputId;
        transaction.outputAmount = outputAmount;
        transaction.outputInstanceId = outputInstanceId;
        transaction.baseInstanceId = baseInstanceId;
        transaction.outputDurabilityRatio = durabilityRatio;
        transaction.outputSignature = outputSignature(transaction.costId, outputType, outputId,
                outputAmount, outputInstanceId);
    }

    static void applyOutput(RunSnapshot.PlayerState state,
                            RunSnapshot.ResourceTransactionState transaction,
                            int physicalBefore,
                            RunSnapshot.EquipmentInstanceState equipmentOutput) {
        if (!KIND.equals(transaction.transactionKind)) {
            throw new IllegalArgumentException("Not a craft transaction");
        }
        String expectedSignature = outputSignature(transaction.costId, transaction.outputType,
                transaction.outputId, transaction.outputAmount, transaction.outputInstanceId);
        if (!expectedSignature.equals(transaction.outputSignature)) {
            throw new IllegalStateException("Craft output signature changed");
        }
        if (state.personalResources == null) state.personalResources = new LinkedHashMap<>();
        if (state.pendingPhysicalItemCounts == null) state.pendingPhysicalItemCounts = new LinkedHashMap<>();
        if (state.pendingRegisteredItems == null) state.pendingRegisteredItems = new LinkedHashMap<>();
        if (state.equipmentInstances == null) state.equipmentInstances = new LinkedHashMap<>();
        if (state.ownedEquipment == null) state.ownedEquipment = new LinkedHashSet<>();
        if (state.pendingEquipmentInstanceIds == null) state.pendingEquipmentInstanceIds = new LinkedHashSet<>();
        switch (transaction.outputType) {
            case "MATERIAL" -> {
                int next = Math.addExact(state.personalResources.getOrDefault(transaction.outputId, 0),
                        transaction.outputAmount);
                state.personalResourcesInitialized = true;
                state.personalResources.put(transaction.outputId, next);
                state.pendingPhysicalItemCounts.put(resourceCheckpoint(transaction.outputId),
                        physicalBefore + transaction.outputAmount);
            }
            case "ITEM" -> queueRegisteredDelivery(state, transaction.outputId,
                    transaction.outputAmount, physicalBefore + transaction.outputAmount);
            case "EQUIPMENT" -> {
                if (equipmentOutput == null || !transaction.outputInstanceId.equals(equipmentOutput.instanceId)
                        || !transaction.outputId.equals(equipmentOutput.templateId)) {
                    throw new IllegalStateException("Craft equipment output does not match its signature");
                }
                state.equipmentInstances.put(equipmentOutput.instanceId, equipmentOutput);
                state.ownedEquipment.add(equipmentOutput.templateId);
                state.pendingEquipmentInstanceIds.add(equipmentOutput.instanceId);
            }
            case "VIRTUAL" -> { }
            default -> throw new IllegalStateException("Unknown craft output " + transaction.outputType);
        }
    }

    static void queueRegisteredDelivery(RunSnapshot.PlayerState state, String rawId,
                                        int amount, int expectedPhysicalCount) {
        String id = rawId.toUpperCase(java.util.Locale.ROOT);
        if (amount <= 0 || expectedPhysicalCount < 0) {
            throw new IllegalArgumentException("Invalid registered item delivery");
        }
        if (state.pendingRegisteredItems == null) state.pendingRegisteredItems = new LinkedHashMap<>();
        if (state.pendingPhysicalItemCounts == null) state.pendingPhysicalItemCounts = new LinkedHashMap<>();
        state.pendingRegisteredItems.merge(id, amount, Math::addExact);
        state.pendingPhysicalItemCounts.put(registeredCheckpoint(id), expectedPhysicalCount);
    }

    static String resourceCheckpoint(String id) {
        return RESOURCE_CHECKPOINT_PREFIX + id.toUpperCase(java.util.Locale.ROOT);
    }

    static String registeredCheckpoint(String id) {
        return REGISTERED_CHECKPOINT_PREFIX + id.toUpperCase(java.util.Locale.ROOT);
    }
}
