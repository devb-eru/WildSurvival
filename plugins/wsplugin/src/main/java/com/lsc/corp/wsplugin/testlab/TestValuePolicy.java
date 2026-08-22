package com.lsc.corp.wsplugin.testlab;

import java.util.Locale;
import java.util.Map;

public final class TestValuePolicy {
    public static final Map<String, Range> PLAYER_STATS = Map.ofEntries(
            Map.entry("damage-dealt", new Range(0.0, 100.0)),
            Map.entry("break", new Range(0.0, 100.0)),
            Map.entry("damage-taken", new Range(0.0, 100.0)),
            Map.entry("damage-reduction", new Range(0.0, 0.95)),
            Map.entry("cooldown", new Range(0.05, 10.0)),
            Map.entry("ap-cost", new Range(0.0, 10.0)),
            Map.entry("ap-regen", new Range(0.0, 20.0)),
            Map.entry("move-speed", new Range(0.1, 5.0)),
            Map.entry("max-health", new Range(1.0, 2048.0)),
            Map.entry("max-ap", new Range(1.0, 10000.0))
    );

    private TestValuePolicy() {
    }

    public static String statId(String raw) {
        String id = identifier(raw);
        if (!PLAYER_STATS.containsKey(id)) {
            throw new IllegalArgumentException("Unknown stat '" + raw + "'. Expected " + PLAYER_STATS.keySet());
        }
        return id;
    }

    public static double statValue(String statId, double value) {
        Range range = PLAYER_STATS.get(statId(statId));
        return finiteInRange(statId, value, range.minimum(), range.maximum());
    }

    public static double finiteInRange(String label, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    public static int integerInRange(String label, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    public static String identifier(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Identifier is required");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (!normalized.matches("[a-z0-9][a-z0-9.-]{0,63}")) {
            throw new IllegalArgumentException("Invalid identifier '" + raw + "'");
        }
        return normalized;
    }

    public static String fileId(String raw) {
        return identifier(raw).replace('.', '-');
    }

    public record Range(double minimum, double maximum) {
    }
}
