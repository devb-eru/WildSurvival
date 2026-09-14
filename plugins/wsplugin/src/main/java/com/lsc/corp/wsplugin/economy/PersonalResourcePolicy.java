package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;

final class PersonalResourcePolicy {
    private PersonalResourcePolicy() { }

    static void adoptIfNeeded(RunSnapshot.PlayerState state, Map<String, Integer> observedPhysical) {
        normalize(state);
        if (state.personalResourcesInitialized) return;
        state.personalResources.clear();
        observedPhysical.forEach((id, amount) -> {
            if (amount != null && amount > 0) state.personalResources.put(id, amount);
        });
        state.personalResourcesInitialized = true;
    }

    static int credit(RunSnapshot.PlayerState state, String id, int amount,
                      int expectedPhysicalCount, Map<String, Integer> observedPhysical) {
        if (amount <= 0 || expectedPhysicalCount < 0) {
            throw new IllegalArgumentException("Resource credit must be positive");
        }
        adoptIfNeeded(state, observedPhysical);
        int next = Math.addExact(state.personalResources.getOrDefault(id, 0), amount);
        state.personalResources.put(id, next);
        state.pendingPhysicalItemCounts.put(CraftTransactionPolicy.resourceCheckpoint(id),
                expectedPhysicalCount);
        return next;
    }

    static int debit(RunSnapshot.PlayerState state, String id, int amount,
                     int expectedPhysicalCount, Map<String, Integer> observedPhysical) {
        if (amount < 0 || expectedPhysicalCount < 0) {
            throw new IllegalArgumentException("Resource debit cannot be negative");
        }
        adoptIfNeeded(state, observedPhysical);
        int before = state.personalResources.getOrDefault(id, 0);
        if (before < amount) throw new IllegalStateException("Insufficient personal resource " + id);
        int next = before - amount;
        state.personalResources.put(id, next);
        state.pendingPhysicalItemCounts.put(CraftTransactionPolicy.resourceCheckpoint(id),
                expectedPhysicalCount);
        return next;
    }

    private static void normalize(RunSnapshot.PlayerState state) {
        if (state.personalResources == null) state.personalResources = new LinkedHashMap<>();
        if (state.pendingPhysicalItemCounts == null) state.pendingPhysicalItemCounts = new LinkedHashMap<>();
    }
}
