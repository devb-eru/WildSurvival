package com.lsc.corp.wsplugin.content;

import java.util.List;
import java.util.Map;

public record ProductionContentCatalog(
        List<CatalogEntry> codexEntries,
        Map<String, CatalogEntry> itemsById,
        List<RecipeEntry> recipes,
        Map<String, List<RecipeEntry>> recipesByOutput,
        Map<String, Integer> counts
) {
    public CatalogEntry item(String id) {
        CatalogEntry entry = itemsById.get(id);
        if (entry == null) throw new IllegalArgumentException("Unknown production item " + id);
        return entry;
    }

    public record CatalogEntry(String id, int codexIndex, String name, String displayMaterial,
                               String domain, int firstDay, String equipmentType, String equipmentSlot) {
        public boolean equipment() {
            return equipmentType != null && !equipmentType.isBlank();
        }
    }

    public record RecipeEntry(String id, String outputId, int outputAmount, String recipeType, String inputAuthority,
                              String layout, List<IngredientEntry> ingredients, List<String> raw) {
    }

    public record IngredientEntry(int slot, String kind, String key, int amount, boolean consume) {
    }
}
