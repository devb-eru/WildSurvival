package com.lsc.corp.wsplugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionRecipePolicyTest {
    @Test
    void matchesAmountsTagsAndVirtualProofWithoutConsumingProof() {
        var recipe = new ProductionContentCatalog.RecipeEntry("R", "OUT", 1, "CRAFT", "AUTH",
                "ORDERED_3X3", List.of(
                new ProductionContentCatalog.IngredientEntry(0, "ITEM", "IRON", 3, true),
                new ProductionContentCatalog.IngredientEntry(1, "TAG", "METAL", 5, true),
                new ProductionContentCatalog.IngredientEntry(2, "PROOF", "BOSS", 1, false)), List.of());
        List<ProductionRecipePolicy.GridCell> grid = emptyGrid();
        grid.set(0, new ProductionRecipePolicy.GridCell("IRON", null, 8));
        grid.set(1, new ProductionRecipePolicy.GridCell("ALLOY", null, 2));

        var result = ProductionRecipePolicy.evaluate(recipe, grid,
                (tag, cell) -> "ALLOY".equals(cell.itemId()) ? 3 : 0,
                (proof, amount) -> "BOSS".equals(proof)).orElseThrow();

        assertEquals(3, result.consumedBySlot().get(0));
        assertEquals(2, result.consumedBySlot().get(1));
        assertEquals(2, result.consumedBySlot().size());
    }

    @Test
    void rejectsDuplicateDistinctSamples() {
        var recipe = new ProductionContentCatalog.RecipeEntry("R", "OUT", 1, "PROCESS", "AUTH",
                "ORDERED_3X3", List.of(
                new ProductionContentCatalog.IngredientEntry(0, "TAG", "DISTINCT_SAMPLE", 1, true),
                new ProductionContentCatalog.IngredientEntry(1, "TAG", "DISTINCT_SAMPLE", 1, true)), List.of());
        List<ProductionRecipePolicy.GridCell> grid = emptyGrid();
        grid.set(0, new ProductionRecipePolicy.GridCell("SAMPLE-A", null, 1));
        grid.set(1, new ProductionRecipePolicy.GridCell("SAMPLE-A", null, 1));
        assertTrue(ProductionRecipePolicy.evaluate(recipe, grid, (tag, cell) -> 1, (proof, amount) -> true).isEmpty());
    }

    private static List<ProductionRecipePolicy.GridCell> emptyGrid() {
        List<ProductionRecipePolicy.GridCell> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) grid.add(ProductionRecipePolicy.GridCell.emptyCell());
        return grid;
    }
}
