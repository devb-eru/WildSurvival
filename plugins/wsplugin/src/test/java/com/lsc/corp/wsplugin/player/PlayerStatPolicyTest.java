package com.lsc.corp.wsplugin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlayerStatPolicyTest {
    @Test
    void grantsThreePointsPerLevelAndAppliesPrototypeEffects() {
        Map<String, Integer> allocation = Map.of("HP", 10, "AP", 5, "ATK", 4, "DEF", 1);

        assertEquals(30, PlayerStatPolicy.totalPoints(10));
        assertEquals(10, PlayerStatPolicy.availablePoints(10, allocation));
        assertEquals(22.0, PlayerStatPolicy.maxHealth(allocation));
        assertEquals(105, PlayerStatPolicy.baseMaxAp(allocation));
        assertEquals(1.08, PlayerStatPolicy.attackMultiplier(allocation));
    }

    @Test
    void rejectsBudgetAndPerStatCapViolations() {
        assertThrows(IllegalArgumentException.class,
                () -> PlayerStatPolicy.validate(1, Map.of("HP", 4)));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerStatPolicy.validate(50, Map.of("AP", 26)));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerStatPolicy.validate(50, Map.of("UNKNOWN", 1)));
    }
}
