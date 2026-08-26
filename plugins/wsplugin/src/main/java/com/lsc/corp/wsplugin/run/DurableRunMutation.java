package com.lsc.corp.wsplugin.run;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Stages a run mutation on a detached snapshot and exposes it only after the snapshot is durable.
 *
 * <p>Resource-cost transitions are infrequent and correctness matters more than the JSON copy cost.
 * A failed callback or save therefore leaves the authoritative in-memory snapshot untouched.</p>
 */
final class DurableRunMutation {
    private static final Gson GSON = new Gson();

    private DurableRunMutation() {}

    @FunctionalInterface
    interface Saver {
        void save(RunSnapshot snapshot) throws IOException;
    }

    record Outcome<T>(RunSnapshot snapshot, T result, boolean persisted) {}

    static <T> Outcome<T> execute(RunSnapshot source, Function<RunSnapshot, T> mutation,
                                  Predicate<T> shouldPersist, Saver saver) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(shouldPersist, "shouldPersist");
        Objects.requireNonNull(saver, "saver");

        RunSnapshot candidate = GSON.fromJson(GSON.toJson(source), RunSnapshot.class);
        T result = mutation.apply(candidate);
        if (!shouldPersist.test(result)) {
            return new Outcome<>(source, result, false);
        }
        saver.save(candidate);
        return new Outcome<>(candidate, result, true);
    }
}
