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
        assertEquals(70, result.verifiedFileCount());
        assertEquals(334, result.catalog().codexEntries().size());
        assertEquals(315, result.catalog().recipes().size());
        assertEquals(64, result.catalog().skills().size());
        assertEquals(40, result.catalog().skills().stream().filter(ProductionContentCatalog.SkillEntry::weaponActive).count());
        assertEquals(10, result.catalog().skills().stream().filter(ProductionContentCatalog.SkillEntry::commonActive).count());
        assertEquals("TRIDENT_TOGGLE", result.catalog().skillsById().get("ws.trident.cast_recall.v1").effect());
        assertEquals("WSI-CONS-BANDAGE", result.catalog().skillsById().get("ws.common.field_bandage.v1").consumableId());
        assertEquals("CORRUPTION_REDUCE", result.catalog().skillsById().get("ws.common.quick_purify.v1").effect());
        assertEquals("WSI-CONS-PURIFY_AMPOULE", result.catalog().skillsById().get("ws.common.quick_purify.v1").consumableId());
        assertTrue(result.catalog().skillsById().get("ws.common.quick_purify.v1").description().contains("오염 -15"));
        assertTrue(result.catalog().skillsById().get("ws.common.ap_stim.v1").description().contains("AP +30"));
        assertEquals("CONTROL_CLEANSE", result.catalog().skillsById().get("ws.common.control_break.v1").effect());
        assertEquals("WSI-CONS-NEURAL_STABILIZER", result.catalog().skillsById().get("ws.common.control_break.v1").consumableId());
        assertEquals(0.65, result.catalog().skillsById().get("ws.bow.barbed_rain.v1").damageCoefficient());
        assertEquals(50, result.catalog().personalAugments().size());
        assertEquals(16, result.catalog().partyAugments().size());
        assertEquals(21, result.catalog().statusesById().size());
        assertEquals("WEAKEN", result.catalog().statusesById().get("WEAKNESS").canonicalId());
        assertEquals("TEMPLATE_LOCKED", result.catalog().statusesById().get("FREEZE").authorityState());
        assertEquals(List.of("AUG-G-002"), result.catalog().augmentsById().get("AUG-G-001").exclusiveWith());
        assertEquals(59, result.catalog().materialsById().size());
        assertEquals(61, result.catalog().nonEquipmentItemsById().size());
        assertEquals(13, result.catalog().nonEquipmentItemsById().values().stream()
                .filter(ProductionContentCatalog.ItemEntry::quickConsumable).count());
        var portableCraftKit = result.catalog().nonEquipmentItemsById().get("WSI-PORTABLE-CRAFT_KIT");
        assertEquals(1, portableCraftKit.stackLimit());
        assertEquals("WSRCP-F01", portableCraftKit.recipeId());
        assertEquals("FAC-P01", portableCraftKit.connectedFacilityId());
        assertEquals("PORTABLE_FACILITY_ACTION", portableCraftKit.usePolicy());
        var campCraftKit = result.catalog().nonEquipmentItemsById().get("WSI-FAC-C01-KIT");
        assertEquals(1, campCraftKit.firstDay());
        assertEquals(4, campCraftKit.stackLimit());
        assertEquals("WSRCP-F02", campCraftKit.recipeId());
        assertEquals("FAC-C01", campCraftKit.connectedFacilityId());
        var settlementCraftKit = result.catalog().nonEquipmentItemsById().get("WSI-FAC-S01-KIT");
        assertEquals(11, settlementCraftKit.firstDay());
        assertEquals(2, settlementCraftKit.stackLimit());
        assertEquals("WSRCP-FAC-S01", settlementCraftKit.recipeId());
        var bossCall = result.catalog().nonEquipmentItemsById().get("WSI-CALL-D10");
        assertEquals(1, bossCall.stackLimit());
        assertEquals("PARTY_BOUND", bossCall.ownership());
        assertEquals("WSRCP-G03", bossCall.recipeId());
        assertEquals("item.wildsurvival.wsi_call_d10", bossCall.textKey());
        assertTrue(result.catalog().recipes().stream().allMatch(recipe -> !recipe.ingredients().isEmpty()));
        assertTrue(result.catalog().recipes().stream().noneMatch(recipe -> recipe.layout().contains("AUTHORITY_DEFINED")));
        assertTrue(result.catalog().recipes().stream().flatMap(recipe -> recipe.ingredients().stream())
                .filter(ingredient -> "PROOF".equals(ingredient.kind())).allMatch(ingredient -> !ingredient.consume()));
        assertEquals(214, result.catalog().codexEntries().stream().filter(ProductionContentCatalog.CatalogEntry::equipment).count());
        assertEquals(214, result.catalog().equipmentById().size());
        assertEquals(33, result.catalog().equipmentById().values().stream()
                .filter(equipment -> equipment.baseTemplateId().isBlank()).count());
        assertEquals(181, result.catalog().equipmentById().values().stream()
                .filter(equipment -> !equipment.baseTemplateId().isBlank()).count());
        assertEquals(108, result.catalog().codexEntries().stream().filter(entry -> "MAIN_WEAPON".equals(entry.equipmentSlot())).count());
        var utilityPickaxe = result.catalog().equipmentById().get("EQL-UT-RI-PICKAXE");
        assertEquals(3, utilityPickaxe.toolTier());
        assertEquals(720, utilityPickaxe.maxDurability());
        assertEquals("VANILLA_HARVEST_WITH_WS_TIER_GATE", utilityPickaxe.executionOpcode());
        assertEquals("HARVEST_PICKAXE", utilityPickaxe.harvestProfileId());
        assertEquals(1.0, utilityPickaxe.resourceYieldMultiplier());
        assertEquals(1, utilityPickaxe.durabilityCostPerSuccess());
        assertTrue(utilityPickaxe.vanillaActionPassthrough());
        assertTrue(result.catalog().equipmentById().values().stream()
                .filter(ProductionContentCatalog.EquipmentEntry::utility)
                .noneMatch(equipment -> equipment.effectText().contains("본 문서")
                        || equipment.effectText().contains("§")));
        assertTrue(result.catalog().equipmentById().values().stream().flatMap(equipment -> equipment.tags().stream())
                .noneMatch(tag -> tag.matches("^(?:RARE|AP\\d+|AB\\d+|LG\\d+|SPD\\d+|UNBREAKABLE\\d+)$")));
        var pioneerSword = result.catalog().equipmentById().get("EQL-W01");
        assertEquals("NONE", pioneerSword.statInheritancePolicy());
        assertEquals("", pioneerSword.baseTemplateId());
        assertEquals("SWORD", pioneerSword.weaponClass());
        assertEquals("IRON_SWORD", pioneerSword.displayMaterial());
        assertEquals(8.0, pioneerSword.stat("ATK"));
        var pioneerChest = result.catalog().equipmentById().get("EQL-AR-C02");
        assertEquals(400, pioneerChest.maxDurability());
        assertEquals(40.0, pioneerChest.stat("HP"));
        assertEquals(10.0, pioneerChest.stat("DEF"));
        assertEquals("EQL-SW-U01", result.catalog().equipmentById().get("EQL-SW-R01").baseTemplateId());
        assertEquals("EQL-W01", result.catalog().equipmentById().get("EQL-SW-U01").baseTemplateId());
        assertEquals("EQL-AR-C02", result.catalog().equipmentById().get("EQD50-B30-AR01").baseTemplateId());
        assertEquals("ABYSSAL", result.catalog().equipmentById().get("EQD50-AX-A41").rarity());
        assertEquals(46, result.catalog().facilitiesById().size());
        assertEquals(8, result.catalog().facilitiesById().values().stream()
                .filter(ProductionContentCatalog.FacilityEntry::portableDevice).count());
        assertEquals(6, result.catalog().facilitiesById().values().stream()
                .filter(ProductionContentCatalog.FacilityEntry::reconstruction).count());
        assertEquals("SHARED_LEDGER", result.catalog().facilitiesById().get("FAC-S16").effectOpcode());
        assertMaterialExecution(result, "WSR-RESONANT_RESIDUE", 10, 1, "LOOT-BOSS-D10");
        assertMaterialExecution(result, "WSR-NEURAL_RESIDUE", 20, 1, "LOOT-BOSS-D20");
        assertMaterialExecution(result, "WSP-BOSS-D10-CORE", 10, 1, "LOOT-BOSS-D10");
        assertMaterialExecution(result, "WSP-REBUILD-PART-A", 10, 1, "LOOT-BOSS-D10");
        assertMaterialExecution(result, "WSP-REBUILD-PART-B", 20, 1, "LOOT-BOSS-D20");
        assertMaterialExecution(result, "WSP-REBUILD-PART-C", 30, 1, "LOOT-BOSS-D30");
        assertMaterialExecution(result, "WSP-REBUILD-PART-D", 40, 1, "LOOT-BOSS-D40");
        assertMaterialExecution(result, "WSR-BOSS_SIGNAL_CORE", 1, 1, "WSRCP-G02");
        assertEquals(1, result.catalog().materialsById().get("WSR-SIGNAL_LENS").registrationAmount());
        assertEquals(1, result.catalog().materialsById().get("WSR-STERILE_GEL").registrationAmount());
        assertEquals(4200, result.catalog().facilitiesById().get("FAC-S06").baseHp());
        assertEquals(30000, result.catalog().facilitiesById().get("FAC-R06").baseHp());
        assertEquals(49, result.catalog().facilitiesById().get("FAC-R06").firstDay());
        assertEquals(50, result.catalog().facilitiesById().get("FAC-R06").activationDay());
        assertEquals(53, result.catalog().enemiesById().size());
        assertEquals(4, result.catalog().bossesById().size());
        assertEquals(34, result.catalog().supportEntitiesById().size());
        assertEquals(57, result.catalog().actionBundlesById().size());
        assertEquals(101, result.catalog().actionBundlesById().values().stream()
                .mapToLong(bundle -> bundle.actions().size()).sum());
        assertEquals(List.of("B10-SWIPE", "B10-CHARGE", "B10-PULSE_MARK", "B10-SHARD_VOLLEY"),
                result.catalog().actionBundlesById().get("ACTSET-BOSS-D10").actions().stream()
                        .limit(4).map(ProductionContentCatalog.ActionEntry::id).toList());
        assertTrue(result.catalog().bossesById().values().stream().allMatch(boss ->
                result.catalog().actionBundlesById().get(boss.actionBundleId()).actions().size() == 12));
        var day21Carrier = result.catalog().enemiesById().get("EN-D21-01");
        assertEquals("HUSK", day21Carrier.bukkitType());
        assertEquals(1200.0, day21Carrier.baseHp());
        assertEquals(18.0, day21Carrier.penetration());
        assertEquals("CORRUPTION", day21Carrier.statusId());
        assertTrue(day21Carrier.rewardsPlayers());
        var boss20Summon = result.catalog().enemiesById().get("EN-D20-A01");
        assertEquals(80.0, boss20Summon.attackDamage());
        assertEquals(0, boss20Summon.activityExp());
        assertTrue(boss20Summon.flags().containsAll(List.of("NO_REWARD", "NO_SAMPLE", "NO_AUGMENT_TRIGGER")));
        var boss40 = result.catalog().bossesById().get("BOSS-D40");
        assertEquals("RAVAGER", boss40.bukkitType());
        assertEquals(650000.0, boss40.baseHp());
        assertEquals(30000.0, boss40.breakMax());
        assertEquals("BLOCK_DISPLAY", result.catalog().supportEntitiesById()
                .get("ENT-DEPLOY-EMERGENCY-COVER").bukkitType());
        assertEquals("ENEMY_ACTION_BUNDLE", result.catalog().actionBundlesById()
                .get("ACT-EN-D21-01").kind());
        assertEquals(62, result.catalog().lootById().size());
        var day30Loot = result.catalog().lootById().get("LOOT-EN-D21-01");
        assertEquals(List.of("WSR-REINFORCED_ALLOY", "WSR-NEURAL_CIRCUIT"), day30Loot.guaranteedPool());
        assertEquals(18, day30Loot.pityLimit());
        assertEquals(0.04, day30Loot.equipmentChances().get("RARE"));
        var boss30Loot = result.catalog().lootById().get("LOOT-BOSS-D30");
        assertEquals(3, boss30Loot.fixedEntries().size());
        assertEquals(10, boss30Loot.fixedEntries().get(1).amountForPartySize(3));
        assertEquals(12, boss30Loot.fixedEntries().get(1).amountForPartySize(4));
        assertTrue(result.catalog().lootById().get("LOOT-NONE").noReward());
        assertEquals(91, result.counts().get("enemies") + result.counts().get("bosses") + result.counts().get("support"));
        assertEquals(25, result.counts().get("research"));
        assertEquals(73, result.counts().get("storyScenes"));
        assertEquals(9, result.counts().get("storyLogs"));
        assertEquals(34, result.counts().get("eventsD10"));
        assertEquals(18, result.counts().get("eventsD20"));
        assertEquals(55, result.counts().get("eventsD50"));
        assertEquals(32, result.counts().get("final"));
        assertEquals(19, result.counts().get("budget"));
        assertEquals(14, result.counts().get("drawLocks"));
        assertEquals(7, result.counts().get("softlocks"));
        assertEquals(107, result.catalog().eventsById().size());
        assertEquals("PR50-FINAL-WAIT", result.catalog().mainEventsByDay().get(50).getFirst().pressureProfileId());
        assertEquals(25, result.catalog().researchById().size());
        assertEquals(49, result.catalog().discoveriesById().size());
        assertEquals(30, result.catalog().discoveriesById().values().stream()
                .filter(discovery -> "CORE".equals(discovery.kind())).count());
        assertEquals(4, result.catalog().discoveriesById().values().stream()
                .filter(discovery -> "CORE_SUB".equals(discovery.kind())).count());
        assertEquals(15, result.catalog().discoveriesById().values().stream()
                .filter(discovery -> "OPTIONAL".equals(discovery.kind())).count());
        assertEquals(List.of("C28-A", "C28-B", "C28-C", "C28-D"),
                result.catalog().discoveriesById().get("C29").prerequisiteIds());
        assertEquals(73, result.catalog().storyScenesById().size());
        assertEquals(9, result.catalog().storyLogsById().size());
        assertEquals(32, result.catalog().finalRecordsById().size());
        assertEquals(12, result.catalog().budgetProfilesById().size());
        assertEquals(14, result.catalog().drawLocksById().size());
        assertEquals(10.0, result.catalog().budgetProfilesById().get("CH-BOSS-LAB").multiplier("bossPattern"));
        assertEquals(50, result.catalog().daysByNumber().size());
        var day2 = result.catalog().daysByNumber().get(2);
        assertEquals(10, day2.threatBudget3());
        assertEquals(List.of(50, 23, 12, 0, 0), day2.resourceBudgetTotals());
        var day40 = result.catalog().daysByNumber().get(40);
        assertTrue(day40.bossDay());
        assertEquals("BOSS-D40", day40.bossId());
        var day50 = result.catalog().daysByNumber().get(50);
        assertEquals(130, day50.threatBudget3());
        assertEquals(224_420, day50.cumulativeExp());
        assertEquals(50, day50.expectedEndLevel());
        assertTrue(day50.finalAvailable());
        assertTrue(day50.completionAllowed());
        assertTrue(result.catalog().daysByNumber().entrySet().stream()
                .filter(entry -> entry.getKey() < 50)
                .noneMatch(entry -> entry.getValue().finalAvailable() || entry.getValue().completionAllowed()));
    }

    private static void assertMaterialExecution(ProductionBundleValidator.ValidationResult result, String id,
                                                int firstDay, int registrationAmount, String sourceText) {
        var material = result.catalog().materialsById().get(id);
        assertEquals(firstDay, material.firstDay(), id);
        assertEquals(registrationAmount, material.registrationAmount(), id);
        assertEquals(sourceText, material.sourceText(), id);
    }
}
