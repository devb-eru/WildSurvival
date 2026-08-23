package com.lsc.corp.wsplugin.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
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
        assertEquals(50, result.catalog().personalAugments().size());
        assertEquals(16, result.catalog().partyAugments().size());
        assertEquals(List.of("AUG-G-002"), result.catalog().augmentsById().get("AUG-G-001").exclusiveWith());
        assertEquals(59, result.catalog().materialsById().size());
        assertEquals(61, result.catalog().nonEquipmentItemsById().size());
        assertEquals(13, result.catalog().nonEquipmentItemsById().values().stream()
                .filter(ProductionContentCatalog.ItemEntry::quickConsumable).count());
        assertTrue(result.catalog().recipes().stream().allMatch(recipe -> !recipe.ingredients().isEmpty()));
        assertTrue(result.catalog().recipes().stream().noneMatch(recipe -> recipe.layout().contains("AUTHORITY_DEFINED")));
        assertTrue(result.catalog().recipes().stream().flatMap(recipe -> recipe.ingredients().stream())
                .filter(ingredient -> "PROOF".equals(ingredient.kind())).allMatch(ingredient -> !ingredient.consume()));
        assertEquals(214, result.catalog().codexEntries().stream().filter(ProductionContentCatalog.CatalogEntry::equipment).count());
        assertEquals(214, result.catalog().equipmentById().size());
        assertEquals(108, result.catalog().codexEntries().stream().filter(entry -> "MAIN_WEAPON".equals(entry.equipmentSlot())).count());
        var utilityPickaxe = result.catalog().equipmentById().get("EQL-UT-RI-PICKAXE");
        assertEquals(3, utilityPickaxe.toolTier());
        assertEquals(720, utilityPickaxe.maxDurability());
        var pioneerSword = result.catalog().equipmentById().get("EQL-W01");
        assertEquals("SWORD", pioneerSword.weaponClass());
        assertEquals("IRON_SWORD", pioneerSword.displayMaterial());
        assertEquals(8.0, pioneerSword.stat("ATK"));
        var pioneerChest = result.catalog().equipmentById().get("EQL-AR-C02");
        assertEquals(400, pioneerChest.maxDurability());
        assertEquals(40.0, pioneerChest.stat("HP"));
        assertEquals(10.0, pioneerChest.stat("DEF"));
        assertEquals("ABYSSAL", result.catalog().equipmentById().get("EQD50-AX-A41").rarity());
        assertEquals(91, result.counts().get("enemies") + result.counts().get("bosses") + result.counts().get("support"));
    }
}
