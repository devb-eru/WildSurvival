package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProductionRecipePolicy {
    private ProductionRecipePolicy() { }

    public static Optional<Match> match(List<ProductionContentCatalog.RecipeEntry> recipes,
                                        List<GridCell> grid, TagValueResolver tags, ProofResolver proofs,
                                        int selectedIndex) {
        if (grid.size() != 9) throw new IllegalArgumentException("Production craft grid must have nine cells");
        List<Match> matches = recipes.stream().map(recipe -> evaluate(recipe, grid, tags, proofs))
                .flatMap(Optional::stream).toList();
        if (matches.isEmpty()) return Optional.empty();
        return Optional.of(matches.get(Math.floorMod(selectedIndex, matches.size()))
                .withCandidateCount(matches.size()));
    }

    public static Optional<Match> evaluate(ProductionContentCatalog.RecipeEntry recipe, List<GridCell> grid,
                                           TagValueResolver tags, ProofResolver proofs) {
        Map<Integer, ProductionContentCatalog.IngredientEntry> physical = new HashMap<>();
        Map<String, Set<String>> distinct = new HashMap<>();
        for (ProductionContentCatalog.IngredientEntry ingredient : recipe.ingredients()) {
            if ("PROOF".equals(ingredient.kind())) {
                if (!proofs.present(ingredient.key(), ingredient.amount())) return Optional.empty();
                continue;
            }
            physical.put(ingredient.slot(), ingredient);
        }
        Map<Integer, Integer> consumed = new LinkedHashMap<>();
        for (int slot = 0; slot < grid.size(); slot++) {
            GridCell cell = grid.get(slot);
            ProductionContentCatalog.IngredientEntry ingredient = physical.get(slot);
            if (ingredient == null) {
                if (!cell.empty()) return Optional.empty();
                continue;
            }
            if (cell.empty()) return Optional.empty();
            int requiredItems;
            switch (ingredient.kind()) {
                case "ITEM" -> {
                    if (!ingredient.key().equals(cell.itemId()) || cell.amount() < ingredient.amount()) return Optional.empty();
                    requiredItems = ingredient.amount();
                }
                case "VANILLA" -> {
                    if (!ingredient.key().equals(cell.vanillaMaterial()) || cell.amount() < ingredient.amount()) return Optional.empty();
                    requiredItems = ingredient.amount();
                }
                case "TAG" -> {
                    int unitValue = tags.value(ingredient.key(), cell);
                    if (unitValue < 1 || (long) cell.amount() * unitValue < ingredient.amount()) return Optional.empty();
                    requiredItems = (ingredient.amount() + unitValue - 1) / unitValue;
                    if (ingredient.key().startsWith("DISTINCT_")) {
                        String identity = cell.itemId() == null ? cell.vanillaMaterial() : cell.itemId();
                        if (identity == null || !distinct.computeIfAbsent(ingredient.key(), ignored -> new HashSet<>()).add(identity)) {
                            return Optional.empty();
                        }
                    }
                }
                default -> throw new IllegalStateException("Unsupported physical ingredient kind " + ingredient.kind());
            }
            if (ingredient.consume()) consumed.put(slot, requiredItems);
        }
        return Optional.of(new Match(recipe, Map.copyOf(consumed), 1));
    }

    public record GridCell(String itemId, String vanillaMaterial, int amount) {
        public GridCell {
            if (amount < 0) throw new IllegalArgumentException("Grid amount cannot be negative");
        }
        public boolean empty() { return amount == 0; }
        public static GridCell emptyCell() { return new GridCell(null, null, 0); }
    }

    public record Match(ProductionContentCatalog.RecipeEntry recipe, Map<Integer, Integer> consumedBySlot,
                        int candidateCount) {
        private Match withCandidateCount(int count) { return new Match(recipe, consumedBySlot, count); }
    }

    @FunctionalInterface public interface TagValueResolver { int value(String tag, GridCell cell); }
    @FunctionalInterface public interface ProofResolver { boolean present(String proof, int amount); }
}
