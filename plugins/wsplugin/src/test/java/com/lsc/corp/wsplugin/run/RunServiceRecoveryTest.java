package com.lsc.corp.wsplugin.run;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static RunSnapshot testRun(String state, boolean restorePending) {
        RunSnapshot run = new RunSnapshot();
        run.runType = "TEST";
        run.state = state;
        run.test = new RunSnapshot.TestState();
        run.test.restorePending = restorePending;
        return run;
    }
}
