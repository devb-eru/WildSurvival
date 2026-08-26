package com.lsc.corp.wsplugin.testlab;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestLabSessionPolicyTest {
    @Test
    void runningOwnerMayMutateAndRecover() {
        assertTrue(TestLabSessionPolicy.activeOwner("RUNNING", "owner", "owner"));
        assertTrue(TestLabSessionPolicy.recoverableOwner("RUNNING", true, "owner", "owner"));
    }

    @Test
    void abortedRestorePendingOwnerMayRecoverButNotMutate() {
        assertFalse(TestLabSessionPolicy.activeOwner("ABORTED", "owner", "owner"));
        assertTrue(TestLabSessionPolicy.recoverableOwner("ABORTED", true, "owner", "owner"));
    }

    @Test
    void terminalSessionWithoutPendingRestoreIsClosed() {
        assertFalse(TestLabSessionPolicy.recoverableOwner("ABORTED", false, "owner", "owner"));
        assertFalse(TestLabSessionPolicy.recoverableOwner("ENDED", false, "owner", "owner"));
    }

    @Test
    void anotherPlayerCannotMutateOrRecoverOwnersSession() {
        assertFalse(TestLabSessionPolicy.activeOwner("RUNNING", "owner", "other"));
        assertFalse(TestLabSessionPolicy.recoverableOwner("ABORTED", true, "owner", "other"));
    }
}
