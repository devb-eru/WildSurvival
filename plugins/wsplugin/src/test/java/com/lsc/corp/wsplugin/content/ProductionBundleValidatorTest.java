package com.lsc.corp.wsplugin.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionBundleValidatorTest {
    @Test
    void validatesAllProductionCatalogCardinalitiesAndReferences() throws Exception {
        var result = new ProductionBundleValidator().validateDirectory(
                Path.of("src/main/resources/content/ws-content-r2"));
        assertEquals(66, result.verifiedFileCount());
        assertEquals(334, result.catalog().codexEntries().size());
        assertEquals(315, result.catalog().recipes().size());
        assertEquals(91, result.counts().get("enemies") + result.counts().get("bosses") + result.counts().get("support"));
    }
}
