package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class ResourceLedger {
    private ResourceLedger() {}

    public static boolean reserveAndMutate(RunSnapshot snapshot, String idempotencyKey,
                                           Map<String, Integer> costs, Consumer<RunSnapshot> mutation) {
        if (snapshot.committedKeys.contains(idempotencyKey)) {
            return false;
        }
        Map<String, Integer> before = new LinkedHashMap<>(snapshot.resources);
        for (var entry : costs.entrySet()) {
            if (entry.getValue() < 0 || before.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        for (var entry : costs.entrySet()) {
            snapshot.resources.put(entry.getKey(), before.getOrDefault(entry.getKey(), 0) - entry.getValue());
        }
        try {
            mutation.accept(snapshot);
            return true;
        } catch (RuntimeException exception) {
            snapshot.resources.clear();
            snapshot.resources.putAll(before);
            throw exception;
        }
    }
}
