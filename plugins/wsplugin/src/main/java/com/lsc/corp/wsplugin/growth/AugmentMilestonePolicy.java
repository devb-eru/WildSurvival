package com.lsc.corp.wsplugin.growth;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public final class AugmentMilestonePolicy {
    private static final Map<Integer, String> FIXED_TIERS = Map.of(3, "SILVER", 6, "GOLD", 10, "PRISM");
    private static final List<Integer> PERSONAL = List.of(3, 6, 10, 15, 20, 25, 30, 35, 40, 45);
    private static final List<Integer> PARTY = List.of(10, 20, 30, 40);

    private AugmentMilestonePolicy() { }

    public static List<Integer> personalMilestones() {
        return PERSONAL;
    }

    public static List<Integer> partyMilestones() {
        return PARTY;
    }

    public static String tier(long runSeed, int milestone) {
        if (!PERSONAL.contains(milestone)) throw new IllegalArgumentException("Unknown personal milestone " + milestone);
        String fixed = FIXED_TIERS.get(milestone);
        if (fixed != null) return fixed;
        double roll = new SplittableRandom(runSeed ^ (milestone * 0x9E3779B97F4A7C15L)).nextDouble();
        if (roll < 0.50) return "SILVER";
        if (roll < 0.80) return "GOLD";
        return "PRISM";
    }
}
