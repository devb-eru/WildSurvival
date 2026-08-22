package com.lsc.corp.wsplugin.combat;

public final class CombatMath {
    private CombatMath() {
    }

    public static double outgoingDamage(double rawDamage, double defence, double augmentMultiplier,
                                        double groggyMultiplier, double testMultiplier) {
        requireFiniteNonNegative("rawDamage", rawDamage);
        requireFiniteNonNegative("defence", defence);
        requireFiniteNonNegative("augmentMultiplier", augmentMultiplier);
        requireFiniteNonNegative("groggyMultiplier", groggyMultiplier);
        requireFiniteNonNegative("testMultiplier", testMultiplier);
        return rawDamage * (100.0 / (100.0 + defence)) * augmentMultiplier * groggyMultiplier * testMultiplier;
    }

    public static double incomingDamage(double rawDamage, double takenMultiplier, double reductionRate) {
        requireFiniteNonNegative("rawDamage", rawDamage);
        requireFiniteNonNegative("takenMultiplier", takenMultiplier);
        if (!Double.isFinite(reductionRate) || reductionRate < 0.0 || reductionRate > 0.95) {
            throw new IllegalArgumentException("reductionRate must be between 0 and 0.95");
        }
        return rawDamage * takenMultiplier * (1.0 - reductionRate);
    }

    public static long cooldownTicks(int baseTicks, double multiplier) {
        if (baseTicks < 1 || !Double.isFinite(multiplier) || multiplier < 0.05 || multiplier > 10.0) {
            throw new IllegalArgumentException("Invalid cooldown inputs");
        }
        return Math.max(1L, Math.round(baseTicks * multiplier));
    }

    private static void requireFiniteNonNegative(String label, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
