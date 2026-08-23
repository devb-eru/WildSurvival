package com.lsc.corp.wsplugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipeGridPolicyTest {
    private final PrototypeContent.RecipeDefinition recipe = new PrototypeContent.RecipeDefinition(
            "R", "검", Map.of("IRON", 3, "WOOD", 2),
            List.of("IRON", "IRON", "IRON", "", "WOOD", "", "", "WOOD", ""),
            false, "EQUIPMENT", "SWORD", 1);

    @Test
    void exactShapeMatches() {
        assertEquals("R", RecipeGridPolicy.match(List.of(recipe), recipe.shape()).orElseThrow().id());
    }

    @Test
    void extraOrRotatedInputsDoNotMatch() {
        assertTrue(RecipeGridPolicy.match(List.of(recipe),
                List.of("IRON", "IRON", "IRON", "WOOD", "", "", "WOOD", "", "")).isEmpty());
    }

    @Test
    void shapelessRecipeMatchesOnlyTheSameNumberOfOccupiedCells() {
        var bandage = new PrototypeContent.RecipeDefinition("B", "붕대", Map.of("FIBER", 3),
                List.of("FIBER", "FIBER", "FIBER", "", "", "", "", "", ""),
                true, "ITEM", "BANDAGE", 2);
        assertTrue(RecipeGridPolicy.match(List.of(bandage),
                List.of("", "FIBER", "", "", "", "FIBER", "FIBER", "", "")).isPresent());
        assertTrue(RecipeGridPolicy.match(List.of(bandage),
                List.of("", "FIBER", "", "", "", "", "", "", "")).isEmpty());
    }
}
