package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import java.util.List;
import java.util.Optional;

public final class RecipeGridPolicy {
    private RecipeGridPolicy() {}

    public static Optional<PrototypeContent.RecipeDefinition> match(
            List<PrototypeContent.RecipeDefinition> recipes, List<String> grid) {
        if (grid == null || grid.size() != 9) {
            return Optional.empty();
        }
        return recipes.stream().filter(recipe -> recipe.shape() != null && recipe.shape().equals(grid)).findFirst();
    }
}
