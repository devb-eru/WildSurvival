package com.lsc.corp.wsplugin.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RunServiceRecoveryTest {
    @Test
    void keepsAbortedTestRunWhenPlayerRestoreIsPending() {
        RunSnapshot run = testRun("ABORTED", true);

        assertTrue(RunRecoveryPolicy.isRecoverableTest(run));
    }

    @Test
    void ignoresAbortedTestRunAfterPlayerRestoreCompletes() {
        RunSnapshot run = testRun("ABORTED", false);

        assertFalse(RunRecoveryPolicy.isRecoverableTest(run));
    }

    @Test
    void keepsRunningTestRunEvenBeforeRestoreFlagMigration() {
        RunSnapshot run = testRun("RUNNING", false);

        assertTrue(RunRecoveryPolicy.isRecoverableTest(run));
    }

    @Test
    void selectsActiveSeasonBeforeInactiveLegacySnapshot() {
        RunSnapshot season = run("SEASON_1", "RUNNING");
        RunSnapshot legacy = run("PROTOTYPE", "ABORTED");
        assertEquals(season, RunRecoveryPolicy.select(season, legacy, null));
    }

    @Test
    void restoresAnActiveLegacyPrototypeWhenNoSeasonIsActive() {
        RunSnapshot season = run("SEASON_1", "ENDED");
        RunSnapshot legacy = run("PROTOTYPE", "RUNNING");
        assertEquals(legacy, RunRecoveryPolicy.select(season, legacy, null));
    }

    @Test
    void rejectsConflictingRecoverableRepositories() {
        RunSnapshot season = run("SEASON_1", "RUNNING");
        RunSnapshot test = testRun("RUNNING", true);
        assertThrows(IllegalStateException.class, () -> RunRecoveryPolicy.select(season, null, test));
    }

    private static RunSnapshot testRun(String state, boolean restorePending) {
        RunSnapshot run = new RunSnapshot();
        run.runType = "TEST";
        run.state = state;
        run.test = new RunSnapshot.TestState();
        run.test.restorePending = restorePending;
        return run;
    }

    private static RunSnapshot run(String type, String state) {
        RunSnapshot run = new RunSnapshot();
        run.runType = type;
        run.state = state;
        return run;
    }
}
