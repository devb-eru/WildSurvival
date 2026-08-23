package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeathRuntimePolicyTest {
    @Test
    void injuryStacksSetDownedAndReviveHealth() {
        assertEquals(0.70, DeathRuntimePolicy.downedHealthFraction(1));
        assertEquals(0.525, DeathRuntimePolicy.downedHealthFraction(2));
        assertEquals(0.35, DeathRuntimePolicy.downedHealthFraction(3));
        assertEquals(0.30, DeathRuntimePolicy.reviveHealthFraction(1));
        assertEquals(0.25, DeathRuntimePolicy.reviveHealthFraction(2));
        assertEquals(0.20, DeathRuntimePolicy.reviveHealthFraction(3));
    }

    @Test
    void reviveTimeAndDownedDamageRespectCaps() {
        assertEquals(5_000L, DeathRuntimePolicy.reviveDurationMillis(1, 1.0));
        assertEquals(7_000L, DeathRuntimePolicy.reviveDurationMillis(2, 1.0));
        assertEquals(9_000L, DeathRuntimePolicy.reviveDurationMillis(3, 1.0));
        assertEquals(2_500L, DeathRuntimePolicy.reviveDurationMillis(1, 4.0));
        assertEquals(20.0, DeathRuntimePolicy.downedDamage(100.0, 0.75, 40.0));
        assertEquals(10.0, DeathRuntimePolicy.downedDamage(20.0, 0.50, 40.0));
    }

    @Test
    void sharedReviveProgressUsesWeightedContributorsAndSafeTickCaps() {
        assertEquals(0.032, DeathRuntimePolicy.reviveProgressDelta(1, 100L, 1.60), 0.000001);
        assertEquals(0.10, DeathRuntimePolicy.reviveProgressDelta(1, 500L, 10.0), 0.000001);
        assertEquals(0.05, DeathRuntimePolicy.reviveProgressDecay(500L), 0.000001);
        assertEquals(1.25, DeathRuntimePolicy.reviveApCost(500L), 0.000001);
        assertEquals(1.0, DeathRuntimePolicy.contributorWeight(0));
        assertEquals(0.60, DeathRuntimePolicy.contributorWeight(1));
        assertEquals(0.0, DeathRuntimePolicy.contributorWeight(2));
    }

    @Test
    void postReviveProtectionHasFullAndTailWindows() {
        assertEquals(0.20, DeathRuntimePolicy.recoveryDamageMultiplier(1_000L, 2_000L, 3_000L));
        assertEquals(0.70, DeathRuntimePolicy.recoveryDamageMultiplier(2_000L, 2_000L, 3_000L));
        assertEquals(1.0, DeathRuntimePolicy.recoveryDamageMultiplier(3_000L, 2_000L, 3_000L));
    }

    @Test
    void onlySafeNonBossDayEndRecoversOneInjury() {
        assertEquals(1, DeathRuntimePolicy.injuryAfterDayEnd(2, false, false));
        assertEquals(0, DeathRuntimePolicy.injuryAfterDayEnd(0, false, false));
        assertEquals(2, DeathRuntimePolicy.injuryAfterDayEnd(2, true, false));
        assertEquals(2, DeathRuntimePolicy.injuryAfterDayEnd(2, false, true));
    }

    @Test
    void helpAndVoidSafetyWindowsUseAuthoritativeTimes() {
        assertFalse(DeathRuntimePolicy.helpSignalReady(9_999L, 10_000L));
        assertTrue(DeathRuntimePolicy.helpSignalReady(10_000L, 10_000L));
        assertTrue(DeathRuntimePolicy.recentSafeLocation(11_000L, 1_000L));
        assertFalse(DeathRuntimePolicy.recentSafeLocation(11_001L, 1_000L));
    }

    @Test
    void downedMovementAndKnockbackUseLockedFractions() {
        assertEquals(0.0, DeathRuntimePolicy.movementFraction("DOWNED_GRACE"));
        assertEquals(0.20, DeathRuntimePolicy.movementFraction("DOWNED"));
        assertEquals(0.20, DeathRuntimePolicy.movementFraction("BEING_REVIVED"));
        assertEquals(1.0, DeathRuntimePolicy.movementFraction("ACTIVE"));
        assertEquals(0.0, DeathRuntimePolicy.knockbackFraction("DOWNED_GRACE"));
        assertEquals(0.50, DeathRuntimePolicy.knockbackFraction("DOWNED"));
        assertEquals(1.0, DeathRuntimePolicy.knockbackFraction("DEAD"));
    }

    @Test
    void spectatorBoundariesUseEncounterAndInclusiveEdge() {
        assertEquals(48.0, DeathRuntimePolicy.spectatorArenaRadius("BOSS-D10"));
        assertEquals(52.0, DeathRuntimePolicy.spectatorArenaRadius("BOSS-D20"));
        assertEquals(40.0, DeathRuntimePolicy.spectatorArenaRadius("BOSS-D30"));
        assertEquals(44.0, DeathRuntimePolicy.spectatorArenaRadius("BOSS-D40"));
        assertEquals(50.0, DeathRuntimePolicy.spectatorArenaRadius(
                "FINAL-D50-FIRST-RECONSTRUCTION-SIGNAL"));
        assertTrue(DeathRuntimePolicy.insideBoundary(48.0 * 48.0, 48.0));
        assertFalse(DeathRuntimePolicy.insideBoundary(48.0 * 48.0 + 0.001, 48.0));
    }

    @Test
    void enemyTargetPriorityProtectsGraceAndLetsExecutorsPressureDownedPlayers() {
        assertEquals(0, DeathRuntimePolicy.enemyTargetPriority(true, "DOWNED", true));
        assertEquals(1, DeathRuntimePolicy.enemyTargetPriority(true, "ACTIVE", true));
        assertEquals(0, DeathRuntimePolicy.enemyTargetPriority(false, "ACTIVE", true));
        assertEquals(Integer.MAX_VALUE,
                DeathRuntimePolicy.enemyTargetPriority(false, "DOWNED", true));
        assertEquals(1, DeathRuntimePolicy.enemyTargetPriority(false, "DOWNED", false));
        assertEquals(Integer.MAX_VALUE,
                DeathRuntimePolicy.enemyTargetPriority(true, "DOWNED_GRACE", false));
        assertEquals(Integer.MAX_VALUE,
                DeathRuntimePolicy.enemyTargetPriority(true, "DEAD", false));
    }

    @Test
    void fourthFatalHitSkipsDowned() {
        assertFalse(DeathRuntimePolicy.fatalInsteadOfDowned(2));
        assertTrue(DeathRuntimePolicy.fatalInsteadOfDowned(3));
        assertThrows(IllegalArgumentException.class, () -> DeathRuntimePolicy.downedHealthFraction(0));
    }
}
