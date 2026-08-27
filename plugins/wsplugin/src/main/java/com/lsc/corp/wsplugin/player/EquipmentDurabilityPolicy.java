package com.lsc.corp.wsplugin.player;

import java.util.Map;

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

    public static RepairResult repairByFraction(int current, int maximum, Condition condition, double fraction) {
        if (maximum < 1 || current < 0 || current > maximum || condition == null
                || !Double.isFinite(fraction) || fraction <= 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("Invalid durability repair state");
        }
        int recovery = Math.max(1, (int) Math.ceil(maximum * fraction));
        int repaired = condition == Condition.BROKEN ? recovery : Math.min(maximum, current + recovery);
        return new RepairResult(repaired, Condition.ACTIVE);
    }

    /** Keeps the Bukkit damage event observable while preventing native deletion of a managed item. */
    public static NativeDamageDecision interceptNativeDamage(int requestedDamage) {
        if (requestedDamage < 0) {
            throw new IllegalArgumentException("Native item damage cannot be negative");
        }
        return new NativeDamageDecision(Math.max(1, requestedDamage), 0);
    }

    /** True only for the single authoritative ACTIVE -> BROKEN transition. */
    public static boolean isBreakTransition(Condition previous, Condition current) {
        if (previous == null || current == null) {
            throw new IllegalArgumentException("Equipment conditions are required");
        }
        return previous != Condition.BROKEN && current == Condition.BROKEN;
    }

    public static BreakSlot resolveBreakSlot(String mainHandInstanceId, String offHandInstanceId,
                                             Map<String, String> equippedBySlot, String brokenInstanceId) {
        if (brokenInstanceId == null || brokenInstanceId.isBlank()) {
            throw new IllegalArgumentException("Broken equipment instance ID is required");
        }
        if (brokenInstanceId.equals(mainHandInstanceId)) return BreakSlot.MAIN_HAND;
        if (brokenInstanceId.equals(offHandInstanceId)) return BreakSlot.OFF_HAND;
        Map<String, String> equipped = equippedBySlot == null ? Map.of() : equippedBySlot;
        if (brokenInstanceId.equals(equipped.get("ARMOR_HEAD"))) return BreakSlot.HEAD;
        if (brokenInstanceId.equals(equipped.get("ARMOR_CHEST"))) return BreakSlot.CHEST;
        if (brokenInstanceId.equals(equipped.get("ARMOR_LEGS"))) return BreakSlot.LEGS;
        if (brokenInstanceId.equals(equipped.get("ARMOR_FEET"))) return BreakSlot.FEET;
        return BreakSlot.OTHER;
    }

    public static String breakCommitKey(String instanceId, int breakOrdinal) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("Equipment instance ID is required");
        }
        if (breakOrdinal < 1) {
            throw new IllegalArgumentException("Break ordinal must be positive");
        }
        return "equipment-broken:" + instanceId + ":" + breakOrdinal;
    }

    public enum Condition { ACTIVE, BROKEN }

    public enum BreakSlot { MAIN_HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET, OTHER }

    public record SpendResult(int current, Condition condition, boolean changed) {
    }

    public record RepairResult(int current, Condition condition) {
    }

    public record NativeDamageDecision(int ledgerCost, int nativeDamage) {
    }
}
