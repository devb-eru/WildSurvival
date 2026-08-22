package com.lsc.corp.wsplugin.run;

import java.util.List;

final class RunRecoveryPolicy {
    private RunRecoveryPolicy() {
    }

    static boolean isRecoverableTest(RunSnapshot snapshot) {
        return snapshot != null && "TEST".equals(snapshot.runType) && snapshot.test != null
                && (isActive(snapshot) || snapshot.test.restorePending);
    }

    private static boolean isActive(RunSnapshot snapshot) {
        return snapshot != null && !List.of("ENDED", "ABORTED").contains(snapshot.state);
    }
}
