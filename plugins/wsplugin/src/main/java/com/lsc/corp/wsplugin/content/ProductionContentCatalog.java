package com.lsc.corp.wsplugin.content;

import java.util.List;
import java.util.Map;

public record ProductionContentCatalog(
        List<CatalogEntry> codexEntries,
        Map<String, CatalogEntry> itemsById,
        List<RecipeEntry> recipes,
        Map<String, List<RecipeEntry>> recipesByOutput,
        List<SkillEntry> skills,
        Map<String, SkillEntry> skillsById,
        List<AugmentEntry> personalAugments,
        List<AugmentEntry> partyAugments,
        Map<String, AugmentEntry> augmentsById,
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

    public record SkillEntry(String id, String kind, String name, String weaponClass, double apCost,
                             int cooldownTicks, double damageCoefficient, double breakDamage, double range,
                             double arcDegrees, int maxTargets, String effect, List<String> tags,
                             int unlockLevel, String consumableId, String description) {
        public boolean weaponActive() {
            return "WEAPON_ACTIVE".equals(kind);
        }

        public boolean commonActive() {
            return "COMMON_ACTIVE".equals(kind);
        }
    }

    public record AugmentEntry(String id, String name, String tier, String scope, List<String> tags,
                               String effectOpcode, String effectText, String constraintText,
                               String weightingText, List<String> exclusiveWith, boolean evolution) {
        public boolean personal() {
            return "PERSONAL".equals(scope);
        }
    }
}
