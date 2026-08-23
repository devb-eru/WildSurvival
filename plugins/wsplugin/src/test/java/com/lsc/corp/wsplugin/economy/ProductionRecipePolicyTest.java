package com.lsc.corp.wsplugin.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.content.ProductionBundleValidator;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.content.RecipeTagCatalog;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void resetsCanBeDrivenByCompleteGridFingerprint() {
        List<ProductionRecipePolicy.GridCell> first = emptyGrid();
        List<ProductionRecipePolicy.GridCell> second = emptyGrid();
        first.set(0, new ProductionRecipePolicy.GridCell("WSR-WOOD", "OAK_LOG", 1));
        second.set(0, new ProductionRecipePolicy.GridCell("WSR-WOOD", "OAK_LOG", 2));
        assertNotEquals(ProductionRecipePolicy.fingerprint(first), ProductionRecipePolicy.fingerprint(second));
        assertEquals(ProductionRecipePolicy.identityFingerprint(first),
                ProductionRecipePolicy.identityFingerprint(second));
    }

    @Test
    void everyProductionRecipeAcceptsItsCanonicalExactGrid() throws Exception {
        ProductionContentCatalog catalog = new ProductionBundleValidator().validateDirectory(
                Path.of("src/main/resources/content/ws-content-r2")).catalog();
        int tested = 0;
        for (ProductionContentCatalog.RecipeEntry recipe : catalog.recipes()) {
            List<ProductionRecipePolicy.GridCell> grid = canonicalGrid(recipe);
            ProductionRecipePolicy.Match match = ProductionRecipePolicy.evaluate(recipe, grid,
                    (tag, cell) -> RecipeTagCatalog.value(tag, cell.itemId()), (proof, amount) -> true).orElseThrow(
                            () -> new AssertionError("Canonical grid did not match " + recipe.id()));
            long consumingPhysicalInputs = recipe.ingredients().stream()
                    .filter(ingredient -> !"PROOF".equals(ingredient.kind()) && ingredient.consume()).count();
            assertEquals(consumingPhysicalInputs, match.consumedBySlot().size(), recipe.id());
            tested++;
        }
        assertEquals(315, tested);
    }

    @Test
    void sharedGridCandidatesCycleDeterministically() throws Exception {
        ProductionContentCatalog catalog = new ProductionBundleValidator().validateDirectory(
                Path.of("src/main/resources/content/ws-content-r2")).catalog();
        ProductionContentCatalog.RecipeEntry facility = catalog.recipes().stream()
                .filter(recipe -> "WSRCP-FAC-S14".equals(recipe.id())).findFirst().orElseThrow();
        List<ProductionRecipePolicy.GridCell> grid = canonicalGrid(facility);
        ProductionRecipePolicy.Match first = ProductionRecipePolicy.match(catalog.recipes(), grid,
                (tag, cell) -> RecipeTagCatalog.value(tag, cell.itemId()), (proof, amount) -> true, 0).orElseThrow();
        int candidateCount = first.candidateCount();
        assertTrue(candidateCount >= 5);
        List<String> recipeIds = new ArrayList<>();
        for (int selection = 0; selection < candidateCount; selection++) {
            ProductionRecipePolicy.Match match = ProductionRecipePolicy.match(catalog.recipes(), grid,
                    (tag, cell) -> RecipeTagCatalog.value(tag, cell.itemId()), (proof, amount) -> true,
                    selection).orElseThrow();
            assertEquals(candidateCount, match.candidateCount());
            recipeIds.add(match.recipe().id());
        }
        assertEquals(candidateCount, recipeIds.stream().distinct().count());
        assertEquals(first.recipe().id(), ProductionRecipePolicy.match(catalog.recipes(), grid,
                (tag, cell) -> RecipeTagCatalog.value(tag, cell.itemId()), (proof, amount) -> true,
                candidateCount).orElseThrow().recipe().id());
        assertEquals("WSRCP-FAC-S14", ProductionRecipePolicy.match(catalog.recipes(), grid,
                (tag, cell) -> RecipeTagCatalog.value(tag, cell.itemId()), (proof, amount) -> true,
                "WSRCP-FAC-S14", 0).orElseThrow().recipe().id());
    }

    private static List<ProductionRecipePolicy.GridCell> canonicalGrid(
            ProductionContentCatalog.RecipeEntry recipe) {
        List<ProductionRecipePolicy.GridCell> grid = emptyGrid();
        Map<String, Integer> nextDistinctMember = new HashMap<>();
        for (ProductionContentCatalog.IngredientEntry ingredient : recipe.ingredients()) {
            switch (ingredient.kind()) {
                case "ITEM" -> grid.set(ingredient.slot(), new ProductionRecipePolicy.GridCell(
                        ingredient.key(), null, ingredient.amount()));
                case "VANILLA" -> grid.set(ingredient.slot(), new ProductionRecipePolicy.GridCell(
                        null, ingredient.key(), ingredient.amount()));
                case "TAG" -> {
                    List<String> members = RecipeTagCatalog.members(ingredient.key());
                    int memberIndex = ingredient.key().startsWith("DISTINCT_")
                            ? nextDistinctMember.merge(ingredient.key(), 1, Integer::sum) - 1 : 0;
                    String itemId = members.get(memberIndex);
                    int unitValue = RecipeTagCatalog.value(ingredient.key(), itemId);
                    int amount = (ingredient.amount() + unitValue - 1) / unitValue;
                    grid.set(ingredient.slot(), new ProductionRecipePolicy.GridCell(itemId, null, amount));
                }
                case "PROOF" -> { }
                default -> throw new AssertionError("Unsupported ingredient kind " + ingredient.kind());
            }
        }
        return grid;
    }

    private static List<ProductionRecipePolicy.GridCell> emptyGrid() {
        List<ProductionRecipePolicy.GridCell> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) grid.add(ProductionRecipePolicy.GridCell.emptyCell());
        return grid;
    }
}
