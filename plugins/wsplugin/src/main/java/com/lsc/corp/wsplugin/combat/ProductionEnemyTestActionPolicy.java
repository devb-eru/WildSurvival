package com.lsc.corp.wsplugin.combat;

import java.util.Locale;

/** Deterministic Test Lab timing layered over the production enemy-action path. */
public final class ProductionEnemyTestActionPolicy {
    private ProductionEnemyTestActionPolicy() { }

    public static Plan plan(String rawMode) {
        String mode = rawMode == null ? "" : rawMode.trim().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "PARRY" -> new Plan(Mode.PARRY, Trigger.NEXT_GUARD_INPUT, 2);
            case "GUARD" -> new Plan(Mode.GUARD, Trigger.NEXT_GUARD_INPUT, 8);
            case "HIT" -> new Plan(Mode.HIT, Trigger.IMMEDIATE_SCHEDULE, 20);
            default -> throw new IllegalArgumentException("attack mode must be PARRY, GUARD, or HIT");
        };
    }

    /** Forced Test Lab actions must keep advancing even while its logical clock is frozen. */
    public static long actionTick(boolean testForced, long logicalTick, long serverTick) {
        return testForced ? serverTick : logicalTick;
    }

    public enum Mode { PARRY, GUARD, HIT }

    public enum Trigger { NEXT_GUARD_INPUT, IMMEDIATE_SCHEDULE }

    public record Plan(Mode mode, Trigger trigger, int offsetTicks) {
        public boolean waitsForGuardInput() {
            return trigger == Trigger.NEXT_GUARD_INPUT;
        }
    }
}
