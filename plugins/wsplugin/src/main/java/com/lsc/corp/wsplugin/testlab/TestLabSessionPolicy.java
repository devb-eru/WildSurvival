package com.lsc.corp.wsplugin.testlab;

import java.util.Set;

public final class TestLabSessionPolicy {
    private static final Set<String> TERMINAL_STATES = Set.of("ENDED", "ABORTED");

    private TestLabSessionPolicy() { }

    public static boolean activeOwner(String state, String ownerUuid, String actorUuid) {
        return "RUNNING".equals(state) && sameOwner(ownerUuid, actorUuid);
    }

    public static boolean recoverableOwner(String state, boolean restorePending,
                                           String ownerUuid, String actorUuid) {
        return sameOwner(ownerUuid, actorUuid)
                && (!TERMINAL_STATES.contains(state) || restorePending);
    }

    private static boolean sameOwner(String ownerUuid, String actorUuid) {
        return ownerUuid != null && ownerUuid.equals(actorUuid);
    }
}
