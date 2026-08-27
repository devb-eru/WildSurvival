package com.lsc.corp.wsplugin.facility;

import java.util.Map;

public final class FacilityPolicy {
    private static final Map<String, int[][]> COSTS = Map.of(
            "FP-PRODUCTION", rows("12,2,10,4,0", "8,0,12,6,2", "10,0,18,10,6", "12,0,24,16,10", "14,0,30,20,16"),
            "FP-RESEARCH", rows("10,2,8,12,2", "6,0,10,14,4", "8,0,14,20,8", "10,0,18,28,12", "12,0,22,34,18"),
            "FP-SURVIVAL", rows("10,8,8,4,2", "8,8,10,5,4", "10,10,14,7,8", "12,12,18,10,12", "14,14,22,12,18"),
            "FP-LOGISTICS", rows("14,2,10,10,0", "10,0,12,12,2", "12,0,16,18,6", "16,0,20,24,10", "18,0,26,30,14"),
            "FP-DEFENSE", rows("8,2,12,4,0", "6,0,14,5,2", "8,0,18,8,4")
    );

    private FacilityPolicy() {
    }

    public static UpgradeCost upgradeCost(String profile, int targetLevel, int partySize) {
        int[][] rows = COSTS.get(profile);
        if (rows == null || targetLevel < 1 || targetLevel > rows.length) {
            throw new IllegalArgumentException("No facility cost for " + profile + " level " + targetLevel);
        }
        double scale = partySize <= 2 ? 0.85 : partySize >= 4 ? 1.15 : 1.0;
        int[] row = rows[targetLevel - 1];
        return new UpgradeCost(scale(row[0], scale), scale(row[1], scale), scale(row[2], scale),
                scale(row[3], scale), scale(row[4], scale));
    }

    public static double processingTimeMultiplier(int level) {
        return switch (requireLevel(level)) {
            case 1 -> 1.0;
            case 2 -> 0.90;
            case 3 -> 0.85;
            case 4 -> 0.80;
            default -> 0.75;
        };
    }

    public static double hpMultiplier(int level) {
        return switch (requireLevel(level)) {
            case 1 -> 1.0;
            case 2 -> 1.15;
            case 3 -> 1.30;
            case 4 -> 1.45;
            default -> 1.60;
        };
    }

    public static int workSlots(int baseSlots, int level) {
        int bonus = level >= 5 ? 2 : level >= 3 ? 1 : 0;
        return Math.min(4, Math.max(0, baseSlots) + bonus);
    }

    public static String healthState(double hp, double maximum) {
        if (maximum <= 0.0 || hp <= maximum * 0.25) return "DISABLED";
        if (hp < maximum * 0.50) return "DEGRADED";
        return "ACTIVE";
    }

    public static boolean directlyConnected(String firstWorld, double firstX, double firstY, double firstZ,
                                            String secondWorld, double secondX, double secondY, double secondZ,
                                            double range) {
        if (firstWorld == null || !firstWorld.equals(secondWorld)) return false;
        double dx = firstX - secondX;
        double dy = firstY - secondY;
        double dz = firstZ - secondZ;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    public static int facilityLimit(String tier) {
        return "DEFENSE".equals(tier) ? 64 : 24;
    }

    public static String initialReconstructionState(String facilityType) {
        return switch (facilityType) {
            case "FAC-R01", "FAC-R02", "FAC-R03", "FAC-R04" -> "ASSEMBLED";
            case "FAC-R05" -> "PLACED";
            case "FAC-R06" -> "READY_LOCKED";
            default -> throw new IllegalArgumentException("Not a reconstruction facility: " + facilityType);
        };
    }

    public static long reconstructionDurationMillis(String facilityType) {
        return switch (facilityType) {
            case "FAC-R01" -> 120_000L;
            case "FAC-R02" -> 60_000L;
            default -> 0L;
        };
    }

    public static long extendTimedExpiry(long currentExpiryEpochMs, long nowEpochMs, long extensionMillis) {
        if (currentExpiryEpochMs < 0L || nowEpochMs < 0L || extensionMillis <= 0L) {
            throw new IllegalArgumentException("Invalid timed facility extension");
        }
        return Math.addExact(Math.max(currentExpiryEpochMs, nowEpochMs), extensionMillis);
    }

    private static int scale(int value, double multiplier) {
        return (int) Math.ceil(value * multiplier);
    }

    private static int requireLevel(int level) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("Facility level must be 1 to 5");
        return level;
    }

    private static int[][] rows(String... values) {
        int[][] result = new int[values.length][];
        for (int row = 0; row < values.length; row++) {
            String[] cells = values[row].split(",");
            result[row] = new int[cells.length];
            for (int column = 0; column < cells.length; column++) result[row][column] = Integer.parseInt(cells[column]);
        }
        return result;
    }

    public record UpgradeCost(int construction, int survival, int metal, int signal, int special) {
        public int value(String category) {
            return switch (category) {
                case "CONSTRUCTION" -> construction;
                case "SURVIVAL" -> survival;
                case "METAL" -> metal;
                case "SIGNAL" -> signal;
                case "SPECIAL" -> special;
                default -> throw new IllegalArgumentException("Unknown facility cost category " + category);
            };
        }
    }
}
