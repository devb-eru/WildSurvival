package com.lsc.corp.wsplugin.player;

public final class EquipmentDurabilityPolicy {
    private EquipmentDurabilityPolicy() {
    }

    public static SpendResult spend(int current, int maximum, int cost) {
        if (maximum < 1 || current < 0 || current > maximum || cost < 0) {
            throw new IllegalArgumentException("Invalid durability state");
        }
        int remaining = Math.max(0, current - cost);
        return new SpendResult(remaining, remaining == 0 ? Condition.BROKEN : Condition.ACTIVE,
                remaining != current);
    }

    public static int mirrorDamage(int current, int maximum, int nativeMaximum) {
        if (maximum < 1 || current < 0 || current > maximum || nativeMaximum < 1) {
            throw new IllegalArgumentException("Invalid durability mirror state");
        }
        double spentRatio = (maximum - current) / (double) maximum;
        return Math.min(nativeMaximum - 1, Math.max(0, (int) Math.round(spentRatio * nativeMaximum)));
    }

    /** Keeps the Bukkit damage event observable while preventing native deletion of a managed item. */
    public static NativeDamageDecision interceptNativeDamage(int requestedDamage) {
        if (requestedDamage < 0) {
            throw new IllegalArgumentException("Native item damage cannot be negative");
        }
        return new NativeDamageDecision(Math.max(1, requestedDamage), 0);
    }

    public enum Condition { ACTIVE, BROKEN }

    public record SpendResult(int current, Condition condition, boolean changed) {
    }

    public record NativeDamageDecision(int ledgerCost, int nativeDamage) {
    }
}
