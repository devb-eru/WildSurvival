package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RecipeGridPolicy {
    private RecipeGridPolicy() {}

    public static Optional<PrototypeContent.RecipeDefinition> match(
            List<PrototypeContent.RecipeDefinition> recipes, List<String> grid) {
        if (grid == null || grid.size() != 9) {
            return Optional.empty();
        }
        return recipes.stream().filter(recipe -> recipe.shape() != null && matches(recipe, grid)).findFirst();
    }

    private static boolean matches(PrototypeContent.RecipeDefinition recipe, List<String> grid) {
        if (!recipe.shapeless()) return recipe.shape().equals(grid);
        return occupiedCells(recipe.shape()).equals(occupiedCells(grid));
    }

    private static Map<String, Integer> occupiedCells(List<String> cells) {
        Map<String, Integer> counts = new HashMap<>();
        for (String id : cells) if (id != null && !id.isBlank()) counts.merge(id, 1, Integer::sum);
        return counts;
    }
}
