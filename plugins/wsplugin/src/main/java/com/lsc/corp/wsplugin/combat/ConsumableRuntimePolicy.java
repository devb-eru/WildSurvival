package com.lsc.corp.wsplugin.combat;

import java.util.List;

/** Exact, data-authoritative constants for Season 1 quick consumables. */
public final class ConsumableRuntimePolicy {
    public static final double BIO_SHIELD_MAX_HP_FRACTION = 0.08;
    public static final long BIO_SHIELD_DURATION_TICKS = 120L;
    public static final long COOLING_PROTECTION_MILLIS = 5_000L;
    public static final double COOLING_BURN_DURATION_MULTIPLIER = 0.75;

    private ConsumableRuntimePolicy() { }

    public static int combatLimit(String itemId) {
        return switch (itemId) {
            case "WSI-CONS-PURIFY_AMPOULE" -> 2;
            case "WSI-CONS-AP_STIM", "WSI-CONS-BIO_SHIELD_AMPOULE" -> 1;
            default -> Integer.MAX_VALUE;
        };
    }

    public static int removableStacks(String itemId) {
        return switch (itemId) {
            case "WSI-CONS-BANDAGE" -> 1;
            case "WSI-CONS-ANTIDOTE_INJECTION", "WSI-CONS-COOLING_SALVE", "WSI-CONS-TOURNIQUET" -> 2;
            default -> 0;
        };
    }

    public static long rationUseTicks(boolean inCombat) {
        return inCombat ? 240L : 160L;
    }

    public static List<String> neuralCleansePriority() {
        return List.of("ROOT", "SILENCE", "DISARM");
    }

    public static boolean neuralSelfUseAllowed(boolean hardControlled) {
        return !hardControlled;
    }
}
