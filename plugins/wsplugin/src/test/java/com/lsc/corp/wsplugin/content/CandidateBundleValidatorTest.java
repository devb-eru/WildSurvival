package com.lsc.corp.wsplugin.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CandidateBundleValidatorTest {
    @Test
    void validatesLockedR21ProjectionWithoutChangingR2Defaults() throws Exception {
        var result = new ProductionBundleValidator(ProductionBundleValidator.CANDIDATE_REVISION)
                .validateDirectory(Path.of("src/main/resources/content/ws-content-r2.1"));

        assertEquals(70, result.verifiedFileCount());
        assertEquals(335, result.catalog().codexEntries().size());
        assertEquals(316, result.catalog().recipes().size());
        assertEquals(62, result.catalog().nonEquipmentItemsById().size());
        assertEquals(35, result.catalog().supportEntitiesById().size());
        assertEquals(92, result.counts().get("enemies") + result.counts().get("bosses") + result.counts().get("support"));

        assertEquals(64, result.catalog().skills().size());
        assertTrue(result.catalog().skills().stream().allMatch(skill -> !skill.operationIds().isEmpty()
                && !skill.executionProfile().isBlank() && !skill.targetSpec().isBlank()
                && !skill.costSpec().isBlank() && !skill.parameterPayload().isBlank()
                && skill.failurePolicy().startsWith("SKFP-")));
        assertEquals("apImmediate=10; pulseAmount=3; pulseInterval=20; pulseCount=5; overflowDiscard=true; exhaustionClear=false",
                result.catalog().skillsById().get("ws.common.ap_stim.v1").parameterPayload());

        assertEquals(66, result.catalog().augmentsById().size());
        assertEquals(66, result.catalog().augmentsById().values().stream()
                .map(ProductionContentCatalog.AugmentEntry::effectOpcode).distinct().count());
        assertTrue(result.catalog().augmentsById().values().stream().allMatch(augment ->
                !augment.triggerIds().isEmpty() && !augment.stateScope().isBlank()
                        && !augment.parameterPayload().isBlank() && !augment.limitFallback().isBlank()
                        && augment.derivedEventFlags().containsAll(List.of(
                        "NO_AUGMENT_RETRIGGER", "NO_REWARD", "NO_CONTRIBUTION"))));

        long enemyActions = result.catalog().actionBundlesById().values().stream()
                .filter(bundle -> "ENEMY_ACTION_BUNDLE".equals(bundle.kind()))
                .flatMap(bundle -> bundle.actions().stream()).count();
        assertEquals(69, enemyActions);
        assertFalse(result.catalog().actionBundlesById().values().stream()
                .flatMap(bundle -> bundle.actions().stream()).anyMatch(action -> action.id().endsWith("-PRIMARY")));
        assertEquals(Set.of("ED20-SUTURE-LINK", "ED20-STATUS-TRANSFER", "ED20-CLING-TRAIL"),
                result.catalog().actionBundlesById().get("ACT-EN-D18-E01").actions().stream()
                        .map(ProductionContentCatalog.ActionEntry::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals("SOURCE_PATTERN", result.catalog().actionBundlesById().get("ACT-EN-D46-E01")
                .actions().getFirst().childEntitySource());

        var follower = result.catalog().supportEntitiesById().get("ENT-D18-SILENT-FOLLOWER");
        assertTrue(follower.flags().containsAll(List.of("NO_REWARD", "NO_SAMPLE", "NO_AUGMENT_TRIGGER",
                "NO_CONTRIBUTION", "NO_COLLISION", "CHORUS_VISUALIZER")));

        var revival = result.catalog().nonEquipmentItemsById().get("WSI-CONS-REVIVAL_CORE");
        assertEquals("REVIVAL", revival.category());
        assertEquals("FACILITY_REVIVAL_TRANSACTION", revival.usePolicy());
        assertEquals("PROGRESSION_RESERVE_DAY40_FINAL", revival.progressionReservePolicy());
        var recipe = result.catalog().recipes().stream().filter(value -> "WSRCP-D31-S01".equals(value.id()))
                .findFirst().orElseThrow();
        assertEquals(List.of(0, 2, 3, 4, 5, 7), recipe.ingredients().stream()
                .map(ProductionContentCatalog.IngredientEntry::slot).toList());
        assertEquals(1200, recipe.processTicks());
    }
}
