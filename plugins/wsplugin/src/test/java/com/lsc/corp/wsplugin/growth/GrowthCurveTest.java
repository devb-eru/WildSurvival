package com.lsc.corp.wsplugin.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GrowthCurveTest {
    @Test
    void matchesAuthoritativeLevelCurve() {
        assertEquals(100, LevelCurve.nextLevelExp(1));
        assertEquals(280, LevelCurve.nextLevelExp(5));
        assertEquals(730, LevelCurve.nextLevelExp(10));
        assertEquals(2_820, LevelCurve.cumulativeExpForLevel(10));
        assertEquals(10, LevelCurve.levelForExp(2_820));
        assertEquals(9, LevelCurve.levelForExp(2_819));
    }
}
