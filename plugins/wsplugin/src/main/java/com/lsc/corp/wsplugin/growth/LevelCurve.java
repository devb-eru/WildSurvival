package com.lsc.corp.wsplugin.growth;

public final class LevelCurve {
    private LevelCurve() {}

    public static int nextLevelExp(int currentLevel) {
        if (currentLevel >= 50) {
            return 0;
        }
        int delta = currentLevel - 1;
        return 100 + 25 * delta + 5 * delta * delta;
    }

    public static int cumulativeExpForLevel(int level) {
        int total = 0;
        for (int current = 1; current < Math.min(level, 50); current++) {
            total += nextLevelExp(current);
        }
        return total;
    }

    public static int levelForExp(int exp) {
        int level = 1;
        while (level < 50 && exp >= cumulativeExpForLevel(level + 1)) {
            level++;
        }
        return level;
    }
}
