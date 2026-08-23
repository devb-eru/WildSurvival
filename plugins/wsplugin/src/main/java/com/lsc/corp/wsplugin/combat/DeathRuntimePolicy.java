package com.lsc.corp.wsplugin.combat;

/** Pure DEATH-001/002 calculations used by the server life-state runtime. */
public final class DeathRuntimePolicy {
    public static final int MAX_INJURY_STACKS = 3;
    public static final long DOWNED_GRACE_MILLIS = 1_500L;

    private DeathRuntimePolicy() {
    }

    public static double downedHealthFraction(int injuryStacks) {
        return switch (requireInjury(injuryStacks)) {
            case 1 -> 0.70;
            case 2 -> 0.525;
            default -> 0.35;
        };
    }

    public static double reviveHealthFraction(int injuryStacks) {
        return switch (requireInjury(injuryStacks)) {
            case 1 -> 0.30;
            case 2 -> 0.25;
            default -> 0.20;
        };
    }

    public static long reviveDurationMillis(int injuryStacks, double speedMultiplier) {
        double baseSeconds = baseReviveDurationMillis(injuryStacks) / 1000.0;
        double safeSpeed = Double.isFinite(speedMultiplier) ? Math.max(0.05, speedMultiplier) : 1.0;
        return Math.max(2_500L, Math.round(baseSeconds * 1000.0 / safeSpeed));
    }

    public static long baseReviveDurationMillis(int injuryStacks) {
        return switch (requireInjury(injuryStacks)) {
            case 1 -> 5_000L;
            case 2 -> 7_000L;
            default -> 9_000L;
        };
    }

    public static double reviveProgressDelta(int injuryStacks, long elapsedMillis, double weightedSpeed) {
        if (elapsedMillis <= 0L || !Double.isFinite(weightedSpeed) || weightedSpeed <= 0.0) return 0.0;
        double elapsed = Math.min(250L, elapsedMillis);
        double calculated = elapsed / baseReviveDurationMillis(injuryStacks) * weightedSpeed;
        return Math.min(calculated, elapsed / 2_500.0);
    }

    public static double reviveProgressDecay(long elapsedMillis) {
        if (elapsedMillis <= 0L) return 0.0;
        return Math.min(250L, elapsedMillis) / 1000.0 * 0.20;
    }

    public static double reviveApCost(long elapsedMillis) {
        if (elapsedMillis <= 0L) return 0.0;
        return Math.min(250L, elapsedMillis) / 1000.0 * 5.0;
    }

    public static double contributorWeight(int joinedOrder) {
        if (joinedOrder == 0) return 1.0;
        if (joinedOrder == 1) return 0.60;
        return 0.0;
    }

    public static double recoveryDamageMultiplier(long nowEpochMs, long fullUntilEpochMs,
                                                   long tailUntilEpochMs) {
        if (nowEpochMs < fullUntilEpochMs) return 0.20;
        if (nowEpochMs < tailUntilEpochMs) return 0.70;
        return 1.0;
    }

    public static int injuryAfterDayEnd(int currentStacks, boolean bossDay, boolean residualAssault) {
        int safeStacks = Math.max(0, Math.min(MAX_INJURY_STACKS, currentStacks));
        return bossDay || residualAssault ? safeStacks : Math.max(0, safeStacks - 1);
    }

    public static boolean helpSignalReady(long nowEpochMs, long cooldownUntilEpochMs) {
        return nowEpochMs >= cooldownUntilEpochMs;
    }

    public static boolean recentSafeLocation(long nowEpochMs, long recordedAtEpochMs) {
        return recordedAtEpochMs > 0L && nowEpochMs >= recordedAtEpochMs
                && nowEpochMs - recordedAtEpochMs <= 10_000L;
    }

    public static double movementFraction(String lifeState) {
        return switch (lifeState == null ? "" : lifeState) {
            case "DOWNED_GRACE" -> 0.0;
            case "DOWNED", "BEING_REVIVED" -> 0.20;
            default -> 1.0;
        };
    }

    public static double knockbackFraction(String lifeState) {
        return switch (lifeState == null ? "" : lifeState) {
            case "DOWNED_GRACE" -> 0.0;
            case "DOWNED", "BEING_REVIVED" -> 0.50;
            default -> 1.0;
        };
    }

    public static double spectatorArenaRadius(String encounterId) {
        return switch (encounterId == null ? "" : encounterId) {
            case "BOSS-D10" -> 48.0;
            case "BOSS-D20" -> 52.0;
            case "BOSS-D30" -> 40.0;
            case "BOSS-D40" -> 44.0;
            case "FINAL-D50-FIRST-RECONSTRUCTION-SIGNAL" -> 50.0;
            default -> 48.0;
        };
    }

    public static boolean insideBoundary(double squaredDistance, double radius) {
        return Double.isFinite(squaredDistance) && squaredDistance >= 0.0
                && Double.isFinite(radius) && radius >= 0.0
                && squaredDistance <= radius * radius;
    }

    public static int enemyTargetPriority(boolean downedExecutor, String lifeState,
                                          boolean activeTargetAvailable) {
        if ("ACTIVE".equals(lifeState)) return downedExecutor ? 1 : 0;
        if ("DOWNED".equals(lifeState) || "BEING_REVIVED".equals(lifeState)) {
            if (downedExecutor) return 0;
            return activeTargetAvailable ? Integer.MAX_VALUE : 1;
        }
        return Integer.MAX_VALUE;
    }

    public static double downedDamage(double finalDamage, double typeMultiplier, double downedMaximum) {
        if (!Double.isFinite(finalDamage) || finalDamage <= 0.0 || downedMaximum <= 0.0) return 0.0;
        return Math.min(downedMaximum * 0.50, finalDamage * Math.max(0.0, typeMultiplier));
    }

    public static boolean fatalInsteadOfDowned(int currentInjuryStacks) {
        return currentInjuryStacks >= MAX_INJURY_STACKS;
    }

    private static int requireInjury(int injuryStacks) {
        if (injuryStacks < 1 || injuryStacks > MAX_INJURY_STACKS) {
            throw new IllegalArgumentException("Injury stacks must be 1 to " + MAX_INJURY_STACKS);
        }
        return injuryStacks;
    }
}
