package com.lsc.corp.wsplugin.combat;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/** Stable combat-use keys for encounter, boss, and five-second personal combat scopes. */
public final class CombatUseScopePolicy {
    public static final long PERSONAL_SCOPE_MILLIS = 5_000L;

    private CombatUseScopePolicy() { }

    public static Optional<String> sharedScope(RunSnapshot run) {
        if (run == null) return Optional.empty();
        if (run.boss != null && "ACTIVE".equals(run.boss.state)) {
            return Optional.of(run.runId + ":BOSS:" + run.day + ":" + run.boss.bossId);
        }
        return run.encounters.values().stream()
                .filter(value -> Set.of("SPAWNING", "ACTIVE").contains(value.state))
                .sorted(Comparator.comparingLong((RunSnapshot.EncounterState value) -> value.startedAtEpochMs)
                        .thenComparing(value -> value.encounterId))
                .map(value -> run.runId + ":ENCOUNTER:" + value.encounterId)
                .findFirst();
    }

    public static PersonalScope personalScope(long currentSequence, long currentExpiryEpochMs, long nowEpochMs) {
        if (currentSequence > 0L && currentExpiryEpochMs >= nowEpochMs) {
            return new PersonalScope(currentSequence, currentExpiryEpochMs, false);
        }
        return new PersonalScope(Math.max(0L, currentSequence) + 1L,
                nowEpochMs + PERSONAL_SCOPE_MILLIS, true);
    }

    public static PersonalScope onCombatAction(long currentSequence, long currentExpiryEpochMs, long nowEpochMs) {
        PersonalScope current = personalScope(currentSequence, currentExpiryEpochMs, nowEpochMs);
        return new PersonalScope(current.sequence(), nowEpochMs + PERSONAL_SCOPE_MILLIS, current.newlyOpened());
    }

    public record PersonalScope(long sequence, long expiresAtEpochMs, boolean newlyOpened) {
        public String key(String runId) {
            return runId + ":PERSONAL:" + sequence;
        }
    }
}
