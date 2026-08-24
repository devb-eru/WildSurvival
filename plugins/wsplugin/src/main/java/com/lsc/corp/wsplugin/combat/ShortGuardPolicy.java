package com.lsc.corp.wsplugin.combat;

import java.util.List;

/** Server-tick state machine and attack classification for F short guard. */
public final class ShortGuardPolicy {
    public static final double PARRY_AP_COST = 15.0;
    public static final double PARRY_REFUND = 5.0;
    public static final int PARRY_STARTUP_TICKS = 1;
    public static final int PARRY_ACTIVE_TICKS = 4;
    public static final int GUARD_STARTUP_TICKS = 3;
    public static final int SHORT_GUARD_TICKS = 12;
    public static final int PARRY_FAILURE_COOLDOWN_TICKS = 15;
    public static final int PARRY_SUCCESS_COOLDOWN_TICKS = 8;
    public static final int GUARD_BROKEN_TICKS = 25;

    private ShortGuardPolicy() { }

    public static Start start(long nowTick, double ap, boolean exhausted, long parryReadyAtTick,
                              long inputSequence) {
        if (!Double.isFinite(ap) || ap <= 0.0) return new Start(null, Math.max(0.0, ap), "NO_AP");
        if (nowTick < parryReadyAtTick) return new Start(null, ap, "COOLDOWN");
        if (!exhausted && ap >= PARRY_AP_COST) {
            return new Start(new State(Phase.PARRY_STARTUP, nowTick + PARRY_STARTUP_TICKS,
                    inputSequence, parryReadyAtTick), ap - PARRY_AP_COST, "PARRY");
        }
        return new Start(new State(Phase.GUARD_STARTUP, nowTick + GUARD_STARTUP_TICKS,
                inputSequence, parryReadyAtTick), ap, "GUARD_ONLY");
    }

    public static State advance(State state, long nowTick) {
        if (state == null || nowTick < state.phaseEndsAtTick()) return state;
        return switch (state.phase()) {
            case PARRY_STARTUP -> new State(Phase.PARRY_ACTIVE, nowTick + PARRY_ACTIVE_TICKS,
                    state.inputSequence(), state.parryReadyAtTick());
            case PARRY_ACTIVE -> new State(Phase.SHORT_GUARD, nowTick + SHORT_GUARD_TICKS,
                    state.inputSequence(), nowTick + PARRY_FAILURE_COOLDOWN_TICKS);
            case GUARD_STARTUP -> new State(Phase.SHORT_GUARD, nowTick + SHORT_GUARD_TICKS,
                    state.inputSequence(), state.parryReadyAtTick());
            case SHORT_GUARD, GUARD_BROKEN -> null;
        };
    }

    public static State parrySuccess(State state, long nowTick) {
        if (state == null || state.phase() != Phase.PARRY_ACTIVE) return state;
        return new State(Phase.SHORT_GUARD, nowTick + SHORT_GUARD_TICKS, state.inputSequence(),
                nowTick + PARRY_SUCCESS_COOLDOWN_TICKS);
    }

    public static State parryFailure(State state, long nowTick) {
        if (state == null || state.phase() != Phase.PARRY_ACTIVE) return state;
        return new State(Phase.SHORT_GUARD, nowTick + SHORT_GUARD_TICKS, state.inputSequence(),
                nowTick + PARRY_FAILURE_COOLDOWN_TICKS);
    }

    public static State guardBroken(State state, long nowTick) {
        if (state == null) return null;
        return new State(Phase.GUARD_BROKEN, nowTick + GUARD_BROKEN_TICKS,
                state.inputSequence(), Math.max(state.parryReadyAtTick(), nowTick + GUARD_BROKEN_TICKS));
    }

    public static boolean parryable(List<String> responseTags, List<String> attackTags) {
        if (explicitlyUnguardable(responseTags, attackTags)) return false;
        return responseTags == null || responseTags.isEmpty() || responseTags.contains("PARRY");
    }

    public static boolean guardable(List<String> responseTags, List<String> attackTags) {
        if (explicitlyUnguardable(responseTags, attackTags)) return false;
        return responseTags == null || responseTags.isEmpty() || responseTags.contains("GUARD");
    }

    public static double guardedDamageMultiplier(List<String> attackTags) {
        if (attackTags != null && attackTags.contains("PROJECTILE")) return 0.20;
        if (attackTags != null && (attackTags.contains("MAGIC") || attackTags.contains("CORRUPTION"))) return 0.60;
        return 0.30;
    }

    public static double normalizedGuardImpact(double configured, List<String> attackTags) {
        if (Double.isFinite(configured) && configured > 0.0) return configured;
        return attackTags != null && attackTags.contains("HEAVY") ? 14.0 : 6.0;
    }

    private static boolean explicitlyUnguardable(List<String> responseTags, List<String> attackTags) {
        if (attackTags != null && (attackTags.contains("UNGUARDABLE")
                || attackTags.contains("POSITIONAL"))) return true;
        return responseTags != null && !responseTags.isEmpty()
                && !responseTags.contains("GUARD") && !responseTags.contains("PARRY");
    }

    public enum Phase { GUARD_STARTUP, PARRY_STARTUP, PARRY_ACTIVE, SHORT_GUARD, GUARD_BROKEN }

    public record State(Phase phase, long phaseEndsAtTick, long inputSequence, long parryReadyAtTick) { }

    public record Start(State state, double apAfter, String result) {
        public boolean started() { return state != null; }
    }
}
