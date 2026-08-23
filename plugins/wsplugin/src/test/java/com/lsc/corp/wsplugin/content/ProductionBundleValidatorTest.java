package com.lsc.corp.wsplugin.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(64, result.catalog().skills().size());
        assertEquals(40, result.catalog().skills().stream().filter(ProductionContentCatalog.SkillEntry::weaponActive).count());
        assertEquals(10, result.catalog().skills().stream().filter(ProductionContentCatalog.SkillEntry::commonActive).count());
        assertEquals("TRIDENT_TOGGLE", result.catalog().skillsById().get("ws.trident.cast_recall.v1").effect());
        assertEquals("WSI-CONS-BANDAGE", result.catalog().skillsById().get("ws.common.field_bandage.v1").consumableId());
        assertEquals(0.65, result.catalog().skillsById().get("ws.bow.barbed_rain.v1").damageCoefficient());
        assertTrue(result.catalog().recipes().stream().allMatch(recipe -> !recipe.ingredients().isEmpty()));
        assertTrue(result.catalog().recipes().stream().noneMatch(recipe -> recipe.layout().contains("AUTHORITY_DEFINED")));
        assertTrue(result.catalog().recipes().stream().flatMap(recipe -> recipe.ingredients().stream())
                .filter(ingredient -> "PROOF".equals(ingredient.kind())).allMatch(ingredient -> !ingredient.consume()));
        assertEquals(214, result.catalog().codexEntries().stream().filter(ProductionContentCatalog.CatalogEntry::equipment).count());
        assertEquals(108, result.catalog().codexEntries().stream().filter(entry -> "MAIN_WEAPON".equals(entry.equipmentSlot())).count());
        assertEquals(91, result.counts().get("enemies") + result.counts().get("bosses") + result.counts().get("support"));
    }
}
