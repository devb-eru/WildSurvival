package com.lsc.corp.wsplugin.combat;

/** Pure rules for reserving and activating a rescue-brace threshold bonus. */
public final class RescueBracePolicy {
    private static final double EPSILON = 1.0e-9;

    private RescueBracePolicy() { }

    public static Pending reserve(double currentBonus, double offeredBonus) {
        double normalizedCurrent = normalize(currentBonus);
        double normalizedOffered = normalize(offeredBonus);
        double selected = Math.max(normalizedCurrent, normalizedOffered);
        return new Pending(selected > 0.0 ? 1 : 0, selected);
    }

    public static Activation activate(int charges, double pendingBonus, double activeBonus,
                                      double creditedProgress) {
        double normalizedActive = normalize(activeBonus);
        if (charges <= 0 || !Double.isFinite(creditedProgress) || creditedProgress <= EPSILON) {
            return new Activation(Math.max(0, Math.min(1, charges)), normalize(pendingBonus),
                    normalizedActive, false);
        }
        return new Activation(0, 0.0, Math.max(normalizedActive, normalize(pendingBonus)), true);
    }

    public static double interruptFraction(double activeBonus) {
        return 0.05 + normalize(activeBonus);
    }

    public static double normalize(double bonus) {
        if (!Double.isFinite(bonus) || bonus <= 0.0) return 0.0;
        if (bonus >= ConsumableRuntimePolicy.REINFORCED_RESCUE_BRACE_THRESHOLD_BONUS - EPSILON) {
            return ConsumableRuntimePolicy.REINFORCED_RESCUE_BRACE_THRESHOLD_BONUS;
        }
        return ConsumableRuntimePolicy.BASE_RESCUE_BRACE_THRESHOLD_BONUS;
    }

    public record Pending(int charges, double bonus) { }

    public record Activation(int charges, double pendingBonus, double activeBonus, boolean consumed) { }
}
