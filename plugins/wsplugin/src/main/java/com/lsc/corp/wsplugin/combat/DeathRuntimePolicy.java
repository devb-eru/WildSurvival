package com.lsc.corp.wsplugin.combat;

/** Pure DEATH-001/002 calculations used by the server life-state runtime. */
public final class DeathRuntimePolicy {
    public static final int MAX_INJURY_STACKS = 3;
    public static final long DOWNED_GRACE_MILLIS = 1_500L;

    private DeathRuntimePolicy() {
    }

    public static double downedHealthFraction(int injuryStacks) {
        return switch (requireInjury(injuryStacks)) {
            case 1 -> 0.70;
            case 2 -> 0.525;
            default -> 0.35;
        };
    }

    public static double reviveHealthFraction(int injuryStacks) {
        return switch (requireInjury(injuryStacks)) {
            case 1 -> 0.30;
            case 2 -> 0.25;
            default -> 0.20;
        };
    }

    public static long reviveDurationMillis(int injuryStacks, double speedMultiplier) {
        double baseSeconds = switch (requireInjury(injuryStacks)) {
            case 1 -> 5.0;
            case 2 -> 7.0;
            default -> 9.0;
        };
        double safeSpeed = Double.isFinite(speedMultiplier) ? Math.max(0.05, speedMultiplier) : 1.0;
        return Math.max(2_500L, Math.round(baseSeconds * 1000.0 / safeSpeed));
    }

    public static double downedDamage(double finalDamage, double typeMultiplier, double downedMaximum) {
        if (!Double.isFinite(finalDamage) || finalDamage <= 0.0 || downedMaximum <= 0.0) return 0.0;
        return Math.min(downedMaximum * 0.50, finalDamage * Math.max(0.0, typeMultiplier));
    }

    public static boolean fatalInsteadOfDowned(int currentInjuryStacks) {
        return currentInjuryStacks >= MAX_INJURY_STACKS;
    }

    private static int requireInjury(int injuryStacks) {
        if (injuryStacks < 1 || injuryStacks > MAX_INJURY_STACKS) {
            throw new IllegalArgumentException("Injury stacks must be 1 to " + MAX_INJURY_STACKS);
        }
        return injuryStacks;
    }
}
