package com.lsc.corp.wsplugin.status;

import java.util.Locale;
import java.util.UUID;

/** Pure calculations shared by the server-authoritative status runtime. */
public final class StatusRuntimePolicy {
    private StatusRuntimePolicy() {
    }

    public static double applicationChance(String resistPolicy, double baseChance,
                                           double hitBonus, double resistance) {
        String policy = normalize(resistPolicy);
        if ("GUARANTEED".equals(policy) || "SCRIPTED".equals(policy)) return 1.0;
        if ("CONDITIONAL".equals(policy) && baseChance <= 0.0) return 0.0;
        if (!"RESISTIBLE".equals(policy) && !"CONDITIONAL".equals(policy)) {
            throw new IllegalArgumentException("Unknown resist policy " + resistPolicy);
        }
        double value = baseChance + hitBonus - clamp(resistance, 0.0, 90.0) / 100.0;
        return clamp(value, 0.10, 0.95);
    }

    public static long durationMillis(double baseSeconds, double maximumPreResistSeconds,
                                      double targetGradeMultiplier, double tenacity,
                                      boolean tenacityApplies, double chainMultiplier,
                                      boolean hardControl) {
        if (baseSeconds < 0.0 || maximumPreResistSeconds < 0.0) {
            throw new IllegalArgumentException("Status duration cannot be negative");
        }
        double capped = maximumPreResistSeconds <= 0.0
                ? baseSeconds : Math.min(baseSeconds, maximumPreResistSeconds);
        double multiplier = Math.max(0.0, targetGradeMultiplier) * Math.max(0.0, chainMultiplier);
        if (tenacityApplies) {
            double safeTenacity = clamp(tenacity, 0.0, 80.0);
            double reduction = Math.min(0.60, safeTenacity / (100.0 + safeTenacity));
            multiplier *= 1.0 - reduction;
        }
        double milliseconds = capped * multiplier * 1000.0;
        if (milliseconds <= 0.0) return 0L;
        return Math.max(hardControl ? 250L : 50L, Math.round(milliseconds));
    }

    public static double hardControlMultiplier(int nextStage) {
        if (nextStage < 0) throw new IllegalArgumentException("Control stage cannot be negative");
        if (nextStage <= 1) return 1.0;
        if (nextStage == 2) return 0.5;
        return 0.0;
    }

    public static double actionLockMultiplier(int nextStage) {
        if (nextStage < 1) throw new IllegalArgumentException("Action lock stage starts at one");
        return switch (nextStage) {
            case 1 -> 1.0;
            case 2 -> 0.70;
            case 3 -> 0.40;
            default -> 0.0;
        };
    }

    public static int cleansePriority(int visualPriority, boolean hardControl, boolean dot) {
        int category = hardControl ? 0 : dot ? 1 : 2;
        return category * 10 + Math.max(0, visualPriority);
    }

    public static double bossBreakFraction(double preResistDurationSeconds, double strengthMultiplier,
                                           boolean chaos) {
        if (preResistDurationSeconds < 0.0 || strengthMultiplier < 0.0) {
            throw new IllegalArgumentException("Boss conversion values cannot be negative");
        }
        return (chaos ? 0.010 : 0.015) * preResistDurationSeconds
                * clamp(strengthMultiplier, 0.50, 2.00);
    }

    public static boolean tauntAllows(UUID lockedTarget, UUID attemptedTarget) {
        return lockedTarget == null || lockedTarget.equals(attemptedTarget);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
