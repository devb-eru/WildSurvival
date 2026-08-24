package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import org.junit.jupiter.api.Test;

class CombatUseScopePolicyTest {
    @Test
    void bossScopeOverridesEncounterScope() {
        RunSnapshot run = run();
        RunSnapshot.EncounterState encounter = new RunSnapshot.EncounterState();
        encounter.encounterId = "DAY-20-ENCOUNTER";
        encounter.state = "ACTIVE";
        run.encounters.put(encounter.encounterId, encounter);
        run.boss = new RunSnapshot.BossState();
        run.boss.bossId = "BOSS-D20";
        run.boss.state = "ACTIVE";

        assertEquals("run-1:BOSS:20:BOSS-D20", CombatUseScopePolicy.sharedScope(run).orElseThrow());
    }

    @Test
    void encounterScopeEndsWithEncounter() {
        RunSnapshot run = run();
        RunSnapshot.EncounterState encounter = new RunSnapshot.EncounterState();
        encounter.encounterId = "DAY-20-ENCOUNTER";
        encounter.state = "ACTIVE";
        run.encounters.put(encounter.encounterId, encounter);
        assertTrue(CombatUseScopePolicy.sharedScope(run).isPresent());
        encounter.state = "RESOLVED";
        assertTrue(CombatUseScopePolicy.sharedScope(run).isEmpty());
    }

    @Test
    void personalScopeRollsOnlyAfterFiveSeconds() {
        CombatUseScopePolicy.PersonalScope opened = CombatUseScopePolicy.personalScope(0L, 0L, 10_000L);
        assertTrue(opened.newlyOpened());
        assertEquals(1L, opened.sequence());
        CombatUseScopePolicy.PersonalScope retained = CombatUseScopePolicy.personalScope(
                opened.sequence(), opened.expiresAtEpochMs(), 15_000L);
        assertFalse(retained.newlyOpened());
        assertEquals(1L, retained.sequence());
        CombatUseScopePolicy.PersonalScope next = CombatUseScopePolicy.personalScope(
                retained.sequence(), retained.expiresAtEpochMs(), 15_001L);
        assertTrue(next.newlyOpened());
        assertEquals(2L, next.sequence());
    }

    @Test
    void combatActionRestartsTheFiveSecondExpiryWithoutChangingSequence() {
        CombatUseScopePolicy.PersonalScope scope = CombatUseScopePolicy.onCombatAction(3L, 20_000L, 19_000L);
        assertFalse(scope.newlyOpened());
        assertEquals(3L, scope.sequence());
        assertEquals(24_000L, scope.expiresAtEpochMs());
    }

    private static RunSnapshot run() {
        RunSnapshot run = new RunSnapshot();
        run.runId = "run-1";
        run.day = 20;
        return run;
    }
}
