package com.lsc.corp.wsplugin.finale;

public final class FinalPolicy {
    private FinalPolicy() { }

    public static int majority(int eligiblePlayers) {
        return Math.max(1, eligiblePlayers / 2 + 1);
    }

    public static double purificationDeltaTicks(int effectiveOutput) {
        if (effectiveOutput >= 70) return 1.0;
        if (effectiveOutput >= 40) return 0.0;
        return -0.5;
    }

    public static int checkpointSeconds(double purificationTicks) {
        int seconds = (int) Math.floor(Math.max(0.0, purificationTicks) / 20.0);
        if (seconds >= 180) return 180;
        if (seconds >= 120) return 120;
        if (seconds >= 60) return 60;
        return 0;
    }

    public static double applyPurificationOutput(double currentTicks, int effectiveOutput) {
        double next = Math.min(180.0 * 20.0, currentTicks + purificationDeltaTicks(effectiveOutput));
        if (next >= currentTicks) return next;
        return Math.max(checkpointSeconds(currentTicks) * 20.0, next);
    }
}
