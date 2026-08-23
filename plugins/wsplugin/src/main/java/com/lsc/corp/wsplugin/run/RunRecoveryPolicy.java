package com.lsc.corp.wsplugin.run;

import java.util.List;

final class RunRecoveryPolicy {
    private RunRecoveryPolicy() {
    }

    static boolean isRecoverableTest(RunSnapshot snapshot) {
        return snapshot != null && "TEST".equals(snapshot.runType) && snapshot.test != null
                && (isActive(snapshot) || snapshot.test.restorePending);
    }

    static RunSnapshot select(RunSnapshot season, RunSnapshot legacyPrototype, RunSnapshot test) {
        boolean seasonActive = isActive(season);
        boolean prototypeActive = isActive(legacyPrototype);
        boolean testRecoverable = isRecoverableTest(test);
        int recoverable = (seasonActive ? 1 : 0) + (prototypeActive ? 1 : 0) + (testRecoverable ? 1 : 0);
        if (recoverable > 1) {
            throw new IllegalStateException(
                    "Multiple recoverable runs conflict across Season 1, legacy prototype, and Test Lab");
        }
        if (seasonActive) return season;
        if (prototypeActive) return legacyPrototype;
        if (testRecoverable) return test;
        return season != null ? season : legacyPrototype;
    }

    private static boolean isActive(RunSnapshot snapshot) {
        return snapshot != null && !List.of("ENDED", "ABORTED").contains(snapshot.state);
    }
}
