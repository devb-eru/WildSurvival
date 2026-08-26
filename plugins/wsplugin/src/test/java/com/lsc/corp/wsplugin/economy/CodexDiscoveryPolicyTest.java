package com.lsc.corp.wsplugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lsc.corp.wsplugin.content.ContentBundleValidator;
import com.lsc.corp.wsplugin.content.ProductionBundleValidator;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CodexDiscoveryPolicyTest {
    private static final Path PROTOTYPE = Path.of(
            "src", "main", "resources", "content", "ws-prototype-r1");
    private static final Path PRODUCTION = Path.of(
            "src", "main", "resources", "content", "ws-content-r2");

    @Test
    void keepsSurvivalClockUsableWithoutChangingFixedProductionCodex() throws Exception {
        var prototype = new ContentBundleValidator().validateDirectory(PROTOTYPE).content();
        var production = new ProductionBundleValidator().validateDirectory(PRODUCTION).catalog();
        Set<String> fixedIds = production.codexEntries().stream()
                .map(entry -> entry.id()).collect(Collectors.toUnmodifiableSet());
        Set<String> runtimeIds = prototype.items().stream()
                .map(item -> item.id()).collect(Collectors.toUnmodifiableSet());

        assertEquals(334, fixedIds.size());
        assertEquals(CodexDiscoveryPolicy.Scope.RUNTIME_ONLY,
                CodexDiscoveryPolicy.scope("SURVIVAL-CLOCK", fixedIds, runtimeIds));
        assertEquals(CodexDiscoveryPolicy.Scope.FIXED_CODEX,
                CodexDiscoveryPolicy.scope("WSI-CONS-BANDAGE", fixedIds, runtimeIds));
        assertThrows(IllegalArgumentException.class,
                () -> CodexDiscoveryPolicy.scope("TYPO-ITEM", fixedIds, runtimeIds));
    }
}
