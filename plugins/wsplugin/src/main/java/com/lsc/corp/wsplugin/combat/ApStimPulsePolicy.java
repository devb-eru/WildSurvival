package com.lsc.corp.wsplugin.combat;

/** Deterministic AP-stimulant pulse state used by runtime and recovery tests. */
public final class ApStimPulsePolicy {
    private ApStimPulsePolicy() { }

    public static State start(double ap, double maxAp) {
        double maximum = finiteMaximum(maxAp);
        return new State(Math.min(maximum, finiteAp(ap) + ConsumableRuntimePolicy.AP_STIM_INITIAL_AP),
                maximum, ConsumableRuntimePolicy.AP_STIM_PULSE_COUNT,
                (int) ConsumableRuntimePolicy.AP_STIM_PULSE_INTERVAL_TICKS, true);
    }

    public static State tick(double ap, double maxAp, int pulsesRemaining, int ticksUntilNextPulse) {
        double maximum = finiteMaximum(maxAp);
        int remaining = Math.max(0, Math.min(ConsumableRuntimePolicy.AP_STIM_PULSE_COUNT, pulsesRemaining));
        if (remaining == 0) return new State(Math.min(maximum, finiteAp(ap)), maximum, 0, 0, false);
        if (ticksUntilNextPulse > 1) {
            return new State(Math.min(maximum, finiteAp(ap)), maximum, remaining,
                    Math.min((int) ConsumableRuntimePolicy.AP_STIM_PULSE_INTERVAL_TICKS,
                            ticksUntilNextPulse - 1), false);
        }
        int nextRemaining = remaining - 1;
        return new State(Math.min(maximum, finiteAp(ap) + ConsumableRuntimePolicy.AP_STIM_PULSE_AP),
                maximum, nextRemaining, nextRemaining == 0 ? 0
                : (int) ConsumableRuntimePolicy.AP_STIM_PULSE_INTERVAL_TICKS, true);
    }

    private static double finiteAp(double ap) {
        return Double.isFinite(ap) ? Math.max(0.0, ap) : 0.0;
    }

    private static double finiteMaximum(double maxAp) {
        return Double.isFinite(maxAp) ? Math.max(0.0, maxAp) : 0.0;
    }

    public record State(double ap, double maxAp, int pulsesRemaining, int ticksUntilNextPulse,
                        boolean durableCheckpoint) { }
}
