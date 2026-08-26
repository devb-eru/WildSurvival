package com.lsc.corp.wsplugin.economy;

import java.util.Set;

final class CodexDiscoveryPolicy {
    enum Scope {
        FIXED_CODEX,
        RUNTIME_ONLY
    }

    private CodexDiscoveryPolicy() {
    }

    static Scope scope(String itemId, Set<String> fixedCodexIds, Set<String> prototypeRuntimeItemIds) {
        if (fixedCodexIds.contains(itemId)) {
            return Scope.FIXED_CODEX;
        }
        if (prototypeRuntimeItemIds.contains(itemId)) {
            return Scope.RUNTIME_ONLY;
        }
        throw new IllegalArgumentException("Unknown codex item " + itemId);
    }
}
