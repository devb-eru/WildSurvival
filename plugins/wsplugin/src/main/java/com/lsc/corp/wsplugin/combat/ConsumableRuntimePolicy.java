package com.lsc.corp.wsplugin.combat;

import java.util.List;

/** Exact, data-authoritative constants for Season 1 quick consumables. */
public final class ConsumableRuntimePolicy {
    public static final double AP_STIM_INITIAL_AP = 10.0;
    public static final double AP_STIM_PULSE_AP = 3.0;
    public static final int AP_STIM_PULSE_COUNT = 5;
    public static final long AP_STIM_PULSE_INTERVAL_TICKS = 20L;
    public static final double BASE_RESCUE_BRACE_THRESHOLD_BONUS = 0.10;
    public static final double REINFORCED_RESCUE_BRACE_THRESHOLD_BONUS = 0.25;
    public static final double BIO_SHIELD_MAX_HP_FRACTION = 0.08;
    public static final long BIO_SHIELD_DURATION_TICKS = 120L;
    public static final long COOLING_PROTECTION_MILLIS = 5_000L;
    public static final double COOLING_BURN_DURATION_MULTIPLIER = 0.75;

    private ConsumableRuntimePolicy() { }

    public static double apStimTotalAp() {
        return AP_STIM_INITIAL_AP + AP_STIM_PULSE_AP * AP_STIM_PULSE_COUNT;
    }

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
