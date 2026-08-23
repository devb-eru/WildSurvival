package com.lsc.corp.wsplugin.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProductionBundleValidator {
    public static final String REVISION = "ws-content-r2";
    private static final int MANIFEST_ENTRIES = 64;

    public ValidationResult validateDirectory(Path root) throws ContentValidationException {
        Path normalized = root.toAbsolutePath().normalize();
        return validate(relative -> {
            Path resolved = normalized.resolve(relative).normalize();
            if (!resolved.startsWith(normalized)) throw new IOException("Path escapes bundle root: " + relative);
            return Files.readAllBytes(resolved);
        });
    }

    public ValidationResult validate(ResourceReader reader) throws ContentValidationException {
        try {
            byte[] lockBytes = reader.read("content-lock.yaml");
            byte[] manifestBytes = reader.read("manifest.json");
            Map<String, String> lock = parseYamlLock(new String(lockBytes, StandardCharsets.UTF_8));
            requireEquals("content-revision", REVISION, lock.get("content-revision"));
            requireEquals("activation-policy", "NEW_RUN_ONLY", lock.get("activation-policy"));
            requireHash("manifest.json", lock.get("manifest-sha256"), manifestBytes);

            JsonObject manifest = JsonParser.parseString(new String(manifestBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            requireNumber(manifest, "schemaVersion", 2);
            requireString(manifest, "contentRevision", REVISION);
            requireString(manifest, "activationPolicy", "NEW_RUN_ONLY");
            requireString(manifest, "storyRevision", "ws-story-s1-r1");
            JsonArray files = manifest.getAsJsonArray("files");
            if (files == null || files.size() != MANIFEST_ENTRIES) {
                throw new ContentValidationException("Production manifest must contain 64 schema/data entries");
            }
            Set<String> paths = new HashSet<>();
            int schemaFiles = 0;
            int dataFiles = 0;
            for (JsonElement value : files) {
                JsonObject entry = value.getAsJsonObject();
                String path = requiredString(entry, "path");
                if (path.startsWith("/") || path.contains("..") || !paths.add(path)) {
                    throw new ContentValidationException("Invalid or duplicate production path " + path);
                }
                if (path.startsWith("schemas/")) schemaFiles++; else dataFiles++;
                requireHash(path, requiredString(entry, "sha256"), reader.read(path));
            }
            if (schemaFiles != 23 || dataFiles != 41) {
                throw new ContentValidationException("Production bundle must contain schema=23 and data=41");
            }

            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("materials", count(reader, "items/materials.json"));
            counts.put("items", count(reader, "items/non-equipment-items.json"));
            counts.put("tools", count(reader, "equipment/day01-10.json")
                    + count(reader, "equipment/day11-20.json") + count(reader, "equipment/day21-50.json"));
            counts.put("recipes", count(reader, "recipes/season1-recipes.json"));
            counts.put("codex", count(reader, "items/codex-index.json"));
            counts.put("skills", count(reader, "skills/player-skills.json"));
            counts.put("personalAugments", count(reader, "augments/personal-augments.json"));
            counts.put("partyAugments", count(reader, "augments/party-augments.json"));
            counts.put("enemies", count(reader, "enemies/day01-10.json")
                    + count(reader, "enemies/day11-20.json") + count(reader, "enemies/day21-50.json"));
            counts.put("bosses", count(reader, "bosses/day10.json") + count(reader, "bosses/day20.json")
                    + count(reader, "bosses/day30.json") + count(reader, "bosses/day40.json"));
            counts.put("support", count(reader, "entities/support-entities.json"));
            counts.put("facilities", count(reader, "facilities/season1-facilities.json"));
            counts.put("loot", count(reader, "loot/season1-loot.json"));
            counts.put("days", count(reader, "days/season1-days-01-50.json"));
            counts.put("research", count(reader, "research/season1-research.json"));
            counts.put("storyScenes", count(reader, "story/season1-scenes.json"));
            counts.put("storyLogs", count(reader, "story/season1-logs.json"));
            Map<String, Integer> expected = Map.ofEntries(
                    Map.entry("materials", 59), Map.entry("items", 61), Map.entry("tools", 214),
                    Map.entry("recipes", 315), Map.entry("codex", 334), Map.entry("skills", 64),
                    Map.entry("personalAugments", 50), Map.entry("partyAugments", 16),
                    Map.entry("enemies", 53), Map.entry("bosses", 4), Map.entry("support", 34),
                    Map.entry("facilities", 46), Map.entry("loot", 62), Map.entry("days", 50),
                    Map.entry("research", 25), Map.entry("storyScenes", 73), Map.entry("storyLogs", 9));
            for (Map.Entry<String, Integer> entry : expected.entrySet()) {
                if (!entry.getValue().equals(counts.get(entry.getKey()))) {
                    throw new ContentValidationException("Cardinality mismatch " + entry.getKey()
                            + " expected=" + entry.getValue() + " actual=" + counts.get(entry.getKey()));
                }
            }

            ProductionContentCatalog catalog = loadCatalog(reader, counts);
            validateStructuredAuthorityData(reader);
            validateReferences(reader, catalog);
            validateEquipmentCatalog(catalog);
            validateSkillCatalog(catalog);
            validateAugmentCatalog(catalog);
            validateItemCatalog(catalog);
            validateEquipmentProfiles(catalog);
            validateFacilityProfiles(catalog);
            validateEntityProfiles(catalog);
            validateLootProfiles(catalog);
            return new ValidationResult(catalog, Map.copyOf(counts), paths.size() + 2,
                    ContentBundleValidator.sha256(manifestBytes));
        } catch (ContentValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ContentValidationException("Cannot validate production bundle", exception);
        }
    }

    private ProductionContentCatalog loadCatalog(ResourceReader reader, Map<String, Integer> counts) throws Exception {
        Map<String, ProductionContentCatalog.MaterialEntry> materialsById = new LinkedHashMap<>();
        for (JsonObject record : records(reader, "items/materials.json")) {
            ProductionContentCatalog.MaterialEntry material = new ProductionContentCatalog.MaterialEntry(
                    requiredString(record, "id"), requiredString(record, "name"), requiredString(record, "tier"),
                    requiredInt(record, "firstDay"), requiredString(record, "displayMaterial"),
                    requiredString(record, "ledgerScope"), requiredString(record, "acquisitionKind"),
                    requiredInt(record, "registrationAmount"), stringArray(record, "harvestSources"),
                    optionalString(record, "sourceText", ""), optionalString(record, "usageText", ""));
            materialsById.put(material.id(), material);
        }
        Map<String, ProductionContentCatalog.ItemEntry> nonEquipmentItemsById = new LinkedHashMap<>();
        for (JsonObject record : records(reader, "items/non-equipment-items.json")) {
            ProductionContentCatalog.ItemEntry item = new ProductionContentCatalog.ItemEntry(requiredString(record, "id"),
                    requiredString(record, "name"), requiredString(record, "category"), requiredInt(record, "firstDay"),
                    requiredString(record, "displayMaterial"), requiredInt(record, "stackLimit"),
                    requiredString(record, "effectText"), optionalString(record, "recipeId", ""));
            nonEquipmentItemsById.put(item.id(), item);
        }
        Map<String, ProductionContentCatalog.EquipmentEntry> equipmentById = new LinkedHashMap<>();
        for (String path : List.of("equipment/day01-10.json", "equipment/day11-20.json", "equipment/day21-50.json")) {
            for (JsonObject record : records(reader, path)) {
                ProductionContentCatalog.EquipmentEntry equipment = new ProductionContentCatalog.EquipmentEntry(
                        requiredString(record, "id"), requiredString(record, "name"),
                        requiredString(record, "equipmentType"), requiredString(record, "equipmentSlot"),
                        optionalString(record, "weaponClass", ""), requiredString(record, "displayMaterial"),
                        requiredString(record, "rarity"), requiredInt(record, "itemLevel"),
                        requiredInt(record, "firstDay"), requiredInt(record, "maxDurability"),
                        requiredInt(record, "toolTier"), optionalString(record, "setId", ""),
                        stringArray(record, "tags"), numberMap(record, "stats"),
                        requiredString(record, "effectText"));
                if (equipmentById.putIfAbsent(equipment.id(), equipment) != null) {
                    throw new ContentValidationException("Duplicate equipment profile " + equipment.id());
                }
            }
        }
        Map<String, ProductionContentCatalog.FacilityEntry> facilitiesById = new LinkedHashMap<>();
        for (JsonObject record : records(reader, "facilities/season1-facilities.json")) {
            ProductionContentCatalog.FacilityEntry facility = new ProductionContentCatalog.FacilityEntry(
                    requiredString(record, "id"), requiredString(record, "name"),
                    requiredString(record, "facilityTier"), requiredString(record, "representation"),
                    requiredString(record, "coreMaterial"), requiredString(record, "networkPolicy"),
                    optionalString(record, "itemId", ""), requiredString(record, "recipeId"),
                    requiredInt(record, "firstDay"), requiredInt(record, "activationDay"), requiredInt(record, "maxLevel"),
                    requiredInt(record, "baseHp"), requiredString(record, "hpAuthority"),
                    requiredInt(record, "workSlots"), requiredInt(record, "threatValue"),
                    requiredString(record, "costProfile"), requiredString(record, "unlockText"),
                    requiredString(record, "effectOpcode"), requiredString(record, "effectText"),
                    requiredString(record, "maintenanceText"), optionalString(record, "portableFallback", ""),
                    requiredString(record, "stateMachine"));
            if (facilitiesById.putIfAbsent(facility.id(), facility) != null) {
                throw new ContentValidationException("Duplicate facility profile " + facility.id());
            }
        }
        Map<String, JsonObject> details = new HashMap<>();
        for (String path : List.of("items/materials.json", "items/non-equipment-items.json",
                "equipment/day01-10.json", "equipment/day11-20.json", "equipment/day21-50.json")) {
            for (JsonObject record : records(reader, path)) details.put(requiredString(record, "id"), record);
        }
        List<ProductionContentCatalog.CatalogEntry> codex = new ArrayList<>();
        Set<Integer> indices = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (JsonObject record : records(reader, "items/codex-index.json")) {
            String id = requiredString(record, "id");
            int index = requiredInt(record, "codexIndex");
            if (!ids.add(id) || !indices.add(index)) throw new ContentValidationException("Duplicate codex ID/index " + id);
            JsonObject detail = details.get(id);
            if (detail == null) throw new ContentValidationException("Codex detail missing " + id);
            String name = optionalString(detail, "name", id);
            String material = optionalString(record, "displayMaterial", optionalString(detail, "displayMaterial", "PAPER"));
            String domain = optionalString(record, "domain", "UNKNOWN");
            int firstDay = optionalInt(detail, "firstDay", inferFirstDay(id));
            codex.add(new ProductionContentCatalog.CatalogEntry(id, index, name, material, domain, firstDay,
                    optionalString(detail, "equipmentType", ""), optionalString(detail, "equipmentSlot", "")));
        }
        codex.sort(Comparator.comparingInt(ProductionContentCatalog.CatalogEntry::codexIndex));
        Map<String, ProductionContentCatalog.CatalogEntry> byId = new LinkedHashMap<>();
        codex.forEach(entry -> byId.put(entry.id(), entry));
        List<ProductionContentCatalog.RecipeEntry> recipes = new ArrayList<>();
        Map<String, List<ProductionContentCatalog.RecipeEntry>> byOutput = new LinkedHashMap<>();
        Set<String> recipeIds = new HashSet<>();
        for (JsonObject record : records(reader, "recipes/season1-recipes.json")) {
            String id = requiredString(record, "id");
            if (!recipeIds.add(id)) throw new ContentValidationException("Duplicate recipe ID " + id);
            List<String> raw = new ArrayList<>();
            if (record.has("raw")) record.getAsJsonArray("raw").forEach(value -> raw.add(value.getAsString()));
            List<ProductionContentCatalog.IngredientEntry> ingredients = new ArrayList<>();
            JsonArray ingredientArray = record.getAsJsonArray("ingredients");
            if (ingredientArray == null || ingredientArray.isEmpty()) {
                throw new ContentValidationException("Executable recipe inputs missing " + id);
            }
            for (JsonElement value : ingredientArray) {
                JsonObject ingredient = value.getAsJsonObject();
                ingredients.add(new ProductionContentCatalog.IngredientEntry(requiredInt(ingredient, "slot"),
                        requiredString(ingredient, "kind"), requiredString(ingredient, "key"),
                        requiredInt(ingredient, "amount"), ingredient.get("consume").getAsBoolean()));
            }
            ProductionContentCatalog.RecipeEntry recipe = new ProductionContentCatalog.RecipeEntry(id,
                    requiredString(record, "outputId"), requiredInt(record, "outputAmount"),
                    optionalString(record, "recipeType", "UNKNOWN"), optionalString(record, "inputAuthority", "UNKNOWN"),
                    optionalString(record, "layout", "UNKNOWN"), List.copyOf(ingredients), List.copyOf(raw));
            recipes.add(recipe);
            byOutput.computeIfAbsent(recipe.outputId(), ignored -> new ArrayList<>()).add(recipe);
        }
        byOutput.replaceAll((ignored, values) -> List.copyOf(values));
        List<ProductionContentCatalog.SkillEntry> skills = new ArrayList<>();
        Map<String, ProductionContentCatalog.SkillEntry> skillsById = new LinkedHashMap<>();
        for (JsonObject record : records(reader, "skills/player-skills.json")) {
            List<String> tags = new ArrayList<>();
            JsonArray tagArray = record.getAsJsonArray("tags");
            if (tagArray != null) tagArray.forEach(value -> tags.add(value.getAsString()));
            ProductionContentCatalog.SkillEntry skill = new ProductionContentCatalog.SkillEntry(
                    requiredString(record, "id"), requiredString(record, "kind"), requiredString(record, "name"),
                    requiredString(record, "weaponClass"), requiredDouble(record, "apCost"),
                    requiredInt(record, "cooldownTicks"), requiredDouble(record, "damageCoefficient"),
                    requiredDouble(record, "breakDamage"), requiredDouble(record, "range"),
                    requiredDouble(record, "arcDegrees"), requiredInt(record, "maxTargets"),
                    requiredString(record, "effect"), List.copyOf(tags), requiredInt(record, "unlockLevel"),
                    optionalString(record, "consumableId", ""), requiredString(record, "description"));
            if (skillsById.putIfAbsent(skill.id(), skill) != null) {
                throw new ContentValidationException("Duplicate skill ID " + skill.id());
            }
            skills.add(skill);
        }
        List<ProductionContentCatalog.AugmentEntry> personalAugments = loadAugments(reader,
                "augments/personal-augments.json");
        List<ProductionContentCatalog.AugmentEntry> partyAugments = loadAugments(reader,
                "augments/party-augments.json");
        Map<String, ProductionContentCatalog.AugmentEntry> augmentsById = new LinkedHashMap<>();
        for (ProductionContentCatalog.AugmentEntry augment : personalAugments) augmentsById.put(augment.id(), augment);
        for (ProductionContentCatalog.AugmentEntry augment : partyAugments) {
            if (augmentsById.putIfAbsent(augment.id(), augment) != null) {
                throw new ContentValidationException("Duplicate augment ID " + augment.id());
            }
        }
        Map<String, ProductionContentCatalog.EnemyEntry> enemiesById = new LinkedHashMap<>();
        for (String path : List.of("enemies/day01-10.json", "enemies/day11-20.json", "enemies/day21-50.json")) {
            for (JsonObject record : records(reader, path)) {
                ProductionContentCatalog.EnemyEntry enemy = new ProductionContentCatalog.EnemyEntry(
                        requiredString(record, "id"), requiredString(record, "name"),
                        requiredString(record, "bukkitType"), requiredString(record, "displayFallback"),
                        requiredInt(record, "firstDay"), requiredString(record, "dayProfile"),
                        requiredString(record, "role"), requiredInt(record, "budgetCost"),
                        requiredDouble(record, "baseHp"), requiredDouble(record, "defence"),
                        requiredDouble(record, "attackDamage"), requiredDouble(record, "penetration"),
                        requiredDouble(record, "breakMax"), requiredInt(record, "augmentSlots"),
                        record.get("elite").getAsBoolean(), requiredString(record, "actionBundleId"),
                        requiredInt(record, "telegraphTicks"), requiredInt(record, "cooldownTicks"),
                        requiredDouble(record, "attackRange"), optionalString(record, "statusId", ""),
                        requiredString(record, "spawnPolicy"), optionalString(record, "parentId", ""),
                        requiredString(record, "cleanupPolicy"), requiredString(record, "rewardOwner"),
                        requiredString(record, "lootTableId"), requiredInt(record, "activityExp"),
                        stringArray(record, "flags"));
                if (enemiesById.putIfAbsent(enemy.id(), enemy) != null) {
                    throw new ContentValidationException("Duplicate enemy profile " + enemy.id());
                }
            }
        }
        Map<String, ProductionContentCatalog.BossEntry> bossesById = new LinkedHashMap<>();
        for (String path : List.of("bosses/day10.json", "bosses/day20.json", "bosses/day30.json", "bosses/day40.json")) {
            for (JsonObject record : records(reader, path)) {
                ProductionContentCatalog.BossEntry boss = new ProductionContentCatalog.BossEntry(
                        requiredString(record, "id"), requiredString(record, "name"),
                        requiredString(record, "bukkitType"), requiredString(record, "displayFallback"),
                        requiredInt(record, "firstDay"), requiredString(record, "dayProfile"),
                        requiredDouble(record, "baseHp"), requiredDouble(record, "defence"),
                        requiredDouble(record, "attackDamage"), requiredDouble(record, "penetration"),
                        requiredDouble(record, "breakMax"), requiredString(record, "actionBundleId"),
                        requiredString(record, "entitySetId"), requiredInt(record, "telegraphTicks"),
                        requiredInt(record, "cooldownTicks"), requiredDouble(record, "attackRange"),
                        requiredString(record, "spawnPolicy"), requiredString(record, "cleanupPolicy"),
                        requiredString(record, "rewardOwner"), requiredString(record, "lootTableId"),
                        requiredInt(record, "activityExp"), stringArray(record, "flags"));
                if (bossesById.putIfAbsent(boss.id(), boss) != null) {
                    throw new ContentValidationException("Duplicate boss profile " + boss.id());
                }
            }
        }
        Map<String, ProductionContentCatalog.SupportEntityEntry> supportEntitiesById = new LinkedHashMap<>();
        for (JsonObject record : records(reader, "entities/support-entities.json")) {
            ProductionContentCatalog.SupportEntityEntry support = new ProductionContentCatalog.SupportEntityEntry(
                    requiredString(record, "id"), requiredString(record, "kind"),
                    requiredString(record, "bukkitType"), requiredString(record, "displayFallback"),
                    requiredString(record, "cleanupPolicy"), requiredString(record, "lootTableId"),
                    requiredString(record, "ownerPolicy"), record.get("persistent").getAsBoolean());
            if (supportEntitiesById.putIfAbsent(support.id(), support) != null) {
                throw new ContentValidationException("Duplicate support entity profile " + support.id());
            }
        }
        Map<String, ProductionContentCatalog.ActionBundleEntry> actionBundlesById = new LinkedHashMap<>();
        for (JsonObject record : records(reader, "skills/entity-actions.json")) {
            List<ProductionContentCatalog.ActionEntry> actions = new ArrayList<>();
            JsonArray values = record.getAsJsonArray("actions");
            if (values == null || values.isEmpty()) {
                throw new ContentValidationException("Entity action bundle is empty " + requiredString(record, "id"));
            }
            for (JsonElement value : values) {
                JsonObject action = value.getAsJsonObject();
                actions.add(new ProductionContentCatalog.ActionEntry(requiredString(action, "id"),
                        requiredString(action, "name"), requiredInt(action, "telegraphTicks"),
                        requiredInt(action, "startupTicks"), requiredInt(action, "activeTicks"),
                        requiredInt(action, "recoveryTicks"), requiredInt(action, "cooldownTicks"),
                        requiredDouble(action, "range"), requiredDouble(action, "damage"),
                        requiredDouble(action, "penetration"), requiredDouble(action, "breakDamage"),
                        optionalString(action, "statusId", "")));
            }
            ProductionContentCatalog.ActionBundleEntry bundle = new ProductionContentCatalog.ActionBundleEntry(
                    requiredString(record, "id"), requiredString(record, "ownerId"),
                    requiredString(record, "kind"), List.copyOf(actions), stringArray(record, "stateMachine"));
            if (actionBundlesById.putIfAbsent(bundle.id(), bundle) != null) {
                throw new ContentValidationException("Duplicate entity action bundle " + bundle.id());
            }
        }
        Map<String, ProductionContentCatalog.LootEntry> lootById = new LinkedHashMap<>();
        for (JsonObject record : records(reader, "loot/season1-loot.json")) {
            List<ProductionContentCatalog.LootFixedEntry> fixedEntries = new ArrayList<>();
            JsonArray fixedValues = record.getAsJsonArray("fixedEntries");
            if (fixedValues != null) {
                for (JsonElement value : fixedValues) {
                    JsonObject fixed = value.getAsJsonObject();
                    fixedEntries.add(new ProductionContentCatalog.LootFixedEntry(
                            requiredString(fixed, "itemId"), integerArray(fixed, "amounts"),
                            requiredString(fixed, "scope")));
                }
            }
            ProductionContentCatalog.LootEntry loot = new ProductionContentCatalog.LootEntry(
                    requiredString(record, "id"), requiredString(record, "sourceId"),
                    requiredString(record, "profile"), requiredString(record, "distribution"),
                    requiredString(record, "executionOpcode"), stringArray(record, "guaranteedPool"),
                    requiredInt(record, "guaranteedMin"), requiredInt(record, "guaranteedMax"),
                    stringArray(record, "specialtyPool"), requiredInt(record, "specialtyMin"),
                    requiredInt(record, "specialtyMax"), numberMap(record, "equipmentChances"),
                    requiredInt(record, "pityLimit"), List.copyOf(fixedEntries),
                    optionalString(record, "equipmentSelectionProfile", ""),
                    requiredInt(record, "partyAugmentMilestone"), requiredInt(record, "requiredToolTierMin"),
                    requiredInt(record, "requiredToolTierMax"), record.get("noReward").getAsBoolean(),
                    stringArray(record, "flags"));
            if (lootById.putIfAbsent(loot.id(), loot) != null) {
                throw new ContentValidationException("Duplicate loot profile " + loot.id());
            }
        }
        return new ProductionContentCatalog(List.copyOf(codex), Map.copyOf(byId), Map.copyOf(materialsById),
                Map.copyOf(nonEquipmentItemsById), Map.copyOf(equipmentById), Map.copyOf(facilitiesById), List.copyOf(recipes),
                Map.copyOf(byOutput), List.copyOf(skills), Map.copyOf(skillsById),
                List.copyOf(personalAugments), List.copyOf(partyAugments), Map.copyOf(augmentsById),
                Map.copyOf(enemiesById), Map.copyOf(bossesById), Map.copyOf(supportEntitiesById),
                Map.copyOf(actionBundlesById), Map.copyOf(lootById), Map.copyOf(counts));
    }

    private List<ProductionContentCatalog.AugmentEntry> loadAugments(ResourceReader reader, String path) throws Exception {
        List<ProductionContentCatalog.AugmentEntry> result = new ArrayList<>();
        for (JsonObject record : records(reader, path)) {
            List<String> tags = stringArray(record, "tags");
            List<String> exclusiveWith = stringArray(record, "exclusiveWith");
            result.add(new ProductionContentCatalog.AugmentEntry(requiredString(record, "id"),
                    requiredString(record, "name"), requiredString(record, "tier"),
                    requiredString(record, "scope"), tags, requiredString(record, "effectOpcode"),
                    requiredString(record, "effectText"), optionalString(record, "constraintText", ""),
                    optionalString(record, "weightingText", ""), exclusiveWith,
                    record.has("evolution") && record.get("evolution").getAsBoolean()));
        }
        return result;
    }

    private static List<String> stringArray(JsonObject object, String key) throws ContentValidationException {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return List.of();
        if (!value.isJsonArray()) {
            throw new ContentValidationException(key + " must be an array");
        }
        JsonArray array = value.getAsJsonArray();
        List<String> result = new ArrayList<>();
        array.forEach(element -> result.add(element.getAsString()));
        return List.copyOf(result);
    }

    private static Map<String, Double> numberMap(JsonObject object, String key) throws ContentValidationException {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return Map.of();
        if (!value.isJsonObject()) throw new ContentValidationException(key + " must be an object");
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                throw new ContentValidationException(key + "." + entry.getKey() + " must be numeric");
            }
            result.put(entry.getKey(), entry.getValue().getAsDouble());
        }
        return Map.copyOf(result);
    }

    private static List<Integer> integerArray(JsonObject object, String key) throws ContentValidationException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) throw new ContentValidationException(key + " must be an array");
        List<Integer> result = new ArrayList<>();
        value.getAsJsonArray().forEach(element -> result.add(element.getAsInt()));
        return List.copyOf(result);
    }

    private void validateReferences(ResourceReader reader, ProductionContentCatalog catalog) throws Exception {
        Set<String> knownOutputs = new HashSet<>(catalog.itemsById().keySet());
        for (ProductionContentCatalog.RecipeEntry recipe : catalog.recipes()) {
            String output = recipe.outputId();
            if (!knownOutputs.contains(output) && !output.matches("FAC-[SR][0-9]{2}@[A-Z0-9_]+")) {
                throw new ContentValidationException("Unknown recipe output " + recipe.id() + " -> " + output);
            }
            if (recipe.outputAmount() < 1 || recipe.outputAmount() > 64) {
                throw new ContentValidationException("Invalid recipe output amount " + recipe.id());
            }
            if (!Set.of("ORDERED_3X3", "CALL_FRAME", "EQUIPMENT_FRAME", "FACILITY_FRAME", "REBUILD_FRAME").contains(recipe.layout())) {
                throw new ContentValidationException("Unresolved recipe layout " + recipe.id() + " -> " + recipe.layout());
            }
            Set<Integer> slots = new HashSet<>();
            for (ProductionContentCatalog.IngredientEntry ingredient : recipe.ingredients()) {
                if (ingredient.slot() < 0 || ingredient.slot() > 8 || !slots.add(ingredient.slot())) {
                    throw new ContentValidationException("Invalid/duplicate ingredient slot " + recipe.id() + " -> " + ingredient.slot());
                }
                if (ingredient.amount() < 1 || ingredient.amount() > 64 || ingredient.key().isBlank()) {
                    throw new ContentValidationException("Invalid ingredient " + recipe.id() + " -> " + ingredient);
                }
                if (!Set.of("ITEM", "TAG", "VANILLA", "PROOF").contains(ingredient.kind())) {
                    throw new ContentValidationException("Unknown ingredient kind " + recipe.id() + " -> " + ingredient.kind());
                }
                if ("ITEM".equals(ingredient.kind()) && !knownOutputs.contains(ingredient.key())) {
                    throw new ContentValidationException("Unknown recipe input " + recipe.id() + " -> " + ingredient.key());
                }
                if ("PROOF".equals(ingredient.kind()) && ingredient.consume()) {
                    throw new ContentValidationException("Proof cannot be consumed " + recipe.id() + " -> " + ingredient.key());
                }
            }
        }
        Set<String> lootIds = ids(reader, "loot/season1-loot.json");
        for (String path : List.of("enemies/day01-10.json", "enemies/day11-20.json", "enemies/day21-50.json",
                "entities/support-entities.json", "bosses/day10.json", "bosses/day20.json", "bosses/day30.json", "bosses/day40.json")) {
            for (JsonObject record : records(reader, path)) {
                if (!record.has("raw")) continue;
                for (JsonElement cell : record.getAsJsonArray("raw")) {
                    String value = cell.getAsString();
                    if (value.startsWith("LOOT-") && !lootIds.contains(value)) {
                        throw new ContentValidationException("Unknown loot reference " + value + " in " + requiredString(record, "id"));
                    }
                }
            }
        }
    }

    private void validateEquipmentCatalog(ProductionContentCatalog catalog) throws ContentValidationException {
        List<ProductionContentCatalog.CatalogEntry> equipment = catalog.codexEntries().stream()
                .filter(ProductionContentCatalog.CatalogEntry::equipment).toList();
        if (equipment.size() != 214) throw new ContentValidationException("Equipment catalog must contain 214 templates");
        Set<String> types = Set.of("UTILITY", "SW", "AX", "BO", "CB", "DG", "BL", "ST", "PK", "TR",
                "UNARMED_SUPPORT", "OFF", "ARMOR", "ACCESSORY", "CHARM");
        Set<String> slots = Set.of("INVENTORY", "MAIN_WEAPON", "OFF_WEAPON", "ARMOR_HEAD", "ARMOR_CHEST",
                "ARMOR_LEGS", "ARMOR_FEET", "ACCESSORY", "CHARM");
        for (ProductionContentCatalog.CatalogEntry entry : equipment) {
            if (!types.contains(entry.equipmentType())) {
                throw new ContentValidationException("Unsupported equipment type " + entry.id() + " -> " + entry.equipmentType());
            }
            if (!slots.contains(entry.equipmentSlot())) {
                throw new ContentValidationException("Unsupported equipment slot " + entry.id() + " -> " + entry.equipmentSlot());
            }
        }
        Map<String, Long> slotCounts = equipment.stream().collect(java.util.stream.Collectors.groupingBy(
                ProductionContentCatalog.CatalogEntry::equipmentSlot, java.util.stream.Collectors.counting()));
        Map<String, Long> expected = Map.ofEntries(
                Map.entry("MAIN_WEAPON", 108L), Map.entry("OFF_WEAPON", 11L), Map.entry("INVENTORY", 16L),
                Map.entry("ARMOR_HEAD", 10L), Map.entry("ARMOR_CHEST", 15L), Map.entry("ARMOR_LEGS", 10L),
                Map.entry("ARMOR_FEET", 10L), Map.entry("ACCESSORY", 24L), Map.entry("CHARM", 10L));
        if (!slotCounts.equals(expected)) throw new ContentValidationException("Equipment slot cardinality mismatch " + slotCounts);
    }

    private void validateSkillCatalog(ProductionContentCatalog catalog) throws ContentValidationException {
        if (catalog.skills().size() != 64 || catalog.skillsById().size() != 64) {
            throw new ContentValidationException("Skill catalog must contain 64 unique skills");
        }
        Set<String> kinds = Set.of("BASIC", "WEAPON_ACTIVE", "COMMON_ACTIVE", "CONTEXT");
        Set<String> weaponClasses = Set.of("SWORD", "AXE", "BOW", "CROSSBOW", "DAGGER", "MACE",
                "STAFF", "PICKAXE", "TRIDENT", "UNARMED");
        Map<String, Long> kindCounts = catalog.skills().stream().collect(java.util.stream.Collectors.groupingBy(
                ProductionContentCatalog.SkillEntry::kind, java.util.stream.Collectors.counting()));
        Map<String, Long> expectedKinds = Map.of("BASIC", 10L, "WEAPON_ACTIVE", 40L,
                "COMMON_ACTIVE", 10L, "CONTEXT", 4L);
        if (!kindCounts.equals(expectedKinds)) {
            throw new ContentValidationException("Skill kind cardinality mismatch " + kindCounts);
        }
        for (ProductionContentCatalog.SkillEntry skill : catalog.skills()) {
            if (!kinds.contains(skill.kind()) || skill.name().isBlank() || skill.description().isBlank()) {
                throw new ContentValidationException("Invalid skill identity " + skill.id());
            }
            if ((skill.weaponActive() || "BASIC".equals(skill.kind())) && !weaponClasses.contains(skill.weaponClass())) {
                throw new ContentValidationException("Invalid weapon class " + skill.id() + " -> " + skill.weaponClass());
            }
            if (skill.apCost() < 0.0 || skill.apCost() > 100.0 || skill.cooldownTicks() < 0
                    || skill.cooldownTicks() > 20 * 120 || skill.damageCoefficient() < 0.0
                    || skill.breakDamage() < 0.0 || skill.range() <= 0.0 || skill.range() > 64.0
                    || skill.arcDegrees() <= 0.0 || skill.arcDegrees() > 360.0 || skill.maxTargets() < 1
                    || skill.maxTargets() > 32 || skill.unlockLevel() < 1) {
                throw new ContentValidationException("Invalid skill tuning " + skill.id());
            }
            if (skill.weaponActive() && skill.tags().isEmpty()) {
                throw new ContentValidationException("Weapon skill tags missing " + skill.id());
            }
            if (!skill.consumableId().isBlank() && !catalog.itemsById().containsKey(skill.consumableId())) {
                throw new ContentValidationException("Unknown skill consumable " + skill.id() + " -> " + skill.consumableId());
            }
        }
        for (String weaponClass : weaponClasses) {
            long basics = catalog.skills().stream().filter(skill -> "BASIC".equals(skill.kind())
                    && weaponClass.equals(skill.weaponClass())).count();
            long actives = catalog.skills().stream().filter(skill -> skill.weaponActive()
                    && weaponClass.equals(skill.weaponClass())).count();
            if (basics != 1 || actives != 4) {
                throw new ContentValidationException("Weapon skill set mismatch " + weaponClass
                        + " basics=" + basics + " actives=" + actives);
            }
        }
    }

    private void validateAugmentCatalog(ProductionContentCatalog catalog) throws ContentValidationException {
        if (catalog.personalAugments().size() != 50 || catalog.partyAugments().size() != 16
                || catalog.augmentsById().size() != 66) {
            throw new ContentValidationException("Augment catalog cardinality mismatch");
        }
        Map<String, Long> tiers = catalog.personalAugments().stream().collect(java.util.stream.Collectors.groupingBy(
                ProductionContentCatalog.AugmentEntry::tier, java.util.stream.Collectors.counting()));
        if (!tiers.equals(Map.of("SILVER", 18L, "GOLD", 18L, "PRISM", 14L))) {
            throw new ContentValidationException("Personal augment tier mismatch " + tiers);
        }
        for (ProductionContentCatalog.AugmentEntry augment : catalog.augmentsById().values()) {
            if (augment.name().isBlank() || augment.effectOpcode().isBlank() || augment.effectText().isBlank()
                    || augment.tags().isEmpty()) {
                throw new ContentValidationException("Incomplete augment " + augment.id());
            }
            if (augment.personal() != augment.id().startsWith("AUG-")
                    || (!augment.personal() && !"PARTY".equals(augment.tier()))) {
                throw new ContentValidationException("Augment scope/tier mismatch " + augment.id());
            }
            for (String exclusive : augment.exclusiveWith()) {
                ProductionContentCatalog.AugmentEntry other = catalog.augmentsById().get(exclusive);
                if (other == null || !other.exclusiveWith().contains(augment.id())) {
                    throw new ContentValidationException("Asymmetric augment exclusion " + augment.id() + " -> " + exclusive);
                }
            }
        }
    }

    private void validateItemCatalog(ProductionContentCatalog catalog) throws ContentValidationException {
        if (catalog.materialsById().size() != 59 || catalog.nonEquipmentItemsById().size() != 61) {
            throw new ContentValidationException("Material/item catalog cardinality mismatch");
        }
        Set<String> acquisitionKinds = Set.of("HARVEST", "CRAFTED", "ENCOUNTER", "PARTY_REWARD", "PROOF");
        for (ProductionContentCatalog.MaterialEntry material : catalog.materialsById().values()) {
            if (!acquisitionKinds.contains(material.acquisitionKind()) || material.registrationAmount() < 1
                    || material.firstDay() < 1 || material.firstDay() > 50) {
                throw new ContentValidationException("Invalid material profile " + material.id());
            }
            if ("HARVEST".equals(material.acquisitionKind()) && material.harvestSources().isEmpty()) {
                throw new ContentValidationException("Harvest source missing " + material.id());
            }
            for (String source : material.harvestSources()) {
                if (!"#LOGS".equals(source) && !source.matches("[A-Z][A-Z0-9_]*")) {
                    throw new ContentValidationException("Invalid harvest material key " + material.id() + " -> " + source);
                }
            }
        }
        Set<String> recipeIds = catalog.recipes().stream().map(ProductionContentCatalog.RecipeEntry::id)
                .collect(java.util.stream.Collectors.toSet());
        for (ProductionContentCatalog.ItemEntry item : catalog.nonEquipmentItemsById().values()) {
            if (item.stackLimit() < 1 || item.stackLimit() > 64 || item.effectText().isBlank()
                    || item.firstDay() < 1 || item.firstDay() > 50) {
                throw new ContentValidationException("Invalid item profile " + item.id());
            }
            if (!item.recipeId().isBlank() && !recipeIds.contains(item.recipeId())) {
                throw new ContentValidationException("Unknown item recipe " + item.id() + " -> " + item.recipeId());
            }
        }
    }

    private void validateEquipmentProfiles(ProductionContentCatalog catalog) throws ContentValidationException {
        if (catalog.equipmentById().size() != 214) {
            throw new ContentValidationException("Equipment profile cardinality mismatch");
        }
        Set<String> rarities = Set.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "ABYSSAL");
        Set<String> slots = Set.of("INVENTORY", "MAIN_WEAPON", "OFF_WEAPON", "ARMOR_HEAD", "ARMOR_CHEST",
                "ARMOR_LEGS", "ARMOR_FEET", "ACCESSORY", "CHARM");
        Set<String> weaponClasses = Set.of("", "SWORD", "AXE", "BOW", "CROSSBOW", "DAGGER",
                "MACE", "STAFF", "PICKAXE", "TRIDENT");
        Set<String> statIds = Set.of("ATK", "DEF", "HP", "AP", "HIT", "EVA", "PEN", "RES",
                "TENACITY", "STAGGER_RES", "SPD", "BREAK_DAMAGE");
        Map<Integer, Long> utilityTiers = catalog.equipmentById().values().stream()
                .filter(ProductionContentCatalog.EquipmentEntry::utility)
                .collect(java.util.stream.Collectors.groupingBy(ProductionContentCatalog.EquipmentEntry::toolTier,
                        java.util.stream.Collectors.counting()));
        if (!utilityTiers.equals(Map.of(3, 4L, 4, 4L, 5, 4L, 6, 4L))) {
            throw new ContentValidationException("Utility tool tier mismatch " + utilityTiers);
        }
        for (ProductionContentCatalog.EquipmentEntry equipment : catalog.equipmentById().values()) {
            if (!catalog.itemsById().containsKey(equipment.id()) || !rarities.contains(equipment.rarity())
                    || !slots.contains(equipment.equipmentSlot()) || !weaponClasses.contains(equipment.weaponClass())
                    || equipment.itemLevel() < 1 || equipment.itemLevel() > 50 || equipment.firstDay() < 1
                    || equipment.firstDay() > 50 || equipment.maxDurability() < 1 || equipment.effectText().isBlank()) {
                throw new ContentValidationException("Invalid equipment profile " + equipment.id());
            }
            if (!statIds.containsAll(equipment.stats().keySet())
                    || equipment.stats().values().stream().anyMatch(value -> !Double.isFinite(value)
                    || value < -1000 || value > 10000)) {
                throw new ContentValidationException("Invalid equipment stats " + equipment.id());
            }
            if (equipment.utility() && (!"INVENTORY".equals(equipment.equipmentSlot())
                    || equipment.toolTier() < 3 || equipment.toolTier() > 6)) {
                throw new ContentValidationException("Invalid utility profile " + equipment.id());
            }
        }
    }

    private void validateFacilityProfiles(ProductionContentCatalog catalog) throws ContentValidationException {
        if (catalog.facilitiesById().size() != 46) {
            throw new ContentValidationException("Facility profile cardinality mismatch");
        }
        Set<String> tiers = Set.of("PORTABLE", "CAMP", "SETTLEMENT", "DEFENSE", "RECONSTRUCTION");
        Set<String> networks = Set.of("INDEPENDENT", "CONNECTED_24", "CONNECTED_96");
        Map<String, Long> expectedTiers = Map.of("PORTABLE", 8L, "CAMP", 8L,
                "SETTLEMENT", 20L, "DEFENSE", 4L, "RECONSTRUCTION", 6L);
        Map<String, Long> actualTiers = catalog.facilitiesById().values().stream().collect(
                java.util.stream.Collectors.groupingBy(ProductionContentCatalog.FacilityEntry::facilityTier,
                        java.util.stream.Collectors.counting()));
        if (!expectedTiers.equals(actualTiers)) {
            throw new ContentValidationException("Facility tier cardinality mismatch " + actualTiers);
        }
        Set<String> recipeIds = catalog.recipes().stream().map(ProductionContentCatalog.RecipeEntry::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> itemIds = new HashSet<>();
        for (ProductionContentCatalog.FacilityEntry facility : catalog.facilitiesById().values()) {
            if (!tiers.contains(facility.facilityTier()) || !networks.contains(facility.networkPolicy())
                    || facility.name().isBlank() || facility.effectOpcode().isBlank() || facility.effectText().isBlank()
                    || facility.coreMaterial().isBlank() || facility.firstDay() < 1 || facility.firstDay() > 50
                    || facility.activationDay() < facility.firstDay() || facility.activationDay() > 50
                    || facility.maxLevel() < 1 || facility.maxLevel() > 5 || facility.baseHp() < 1
                    || facility.workSlots() < 0 || facility.workSlots() > 4 || facility.threatValue() < 0) {
                throw new ContentValidationException("Invalid facility profile " + facility.id());
            }
            if (!recipeIds.contains(facility.recipeId())) {
                throw new ContentValidationException("Unknown facility recipe " + facility.id() + " -> " + facility.recipeId());
            }
            if (facility.portableDevice() || !facility.reconstruction()) {
                if (facility.itemId().isBlank() || !catalog.nonEquipmentItemsById().containsKey(facility.itemId())
                        || !itemIds.add(facility.itemId())) {
                    throw new ContentValidationException("Invalid/duplicate facility item " + facility.id()
                            + " -> " + facility.itemId());
                }
            } else if (!facility.itemId().isBlank()) {
                throw new ContentValidationException("Reconstruction facility must be virtual " + facility.id());
            }
            if (facility.reconstruction() && !"DOCUMENT_LOCK".equals(facility.hpAuthority())) {
                throw new ContentValidationException("Reconstruction HP authority mismatch " + facility.id());
            }
        }
    }

    private void validateEntityProfiles(ProductionContentCatalog catalog) throws ContentValidationException {
        if (catalog.enemiesById().size() != 53 || catalog.bossesById().size() != 4
                || catalog.supportEntitiesById().size() != 34 || catalog.actionBundlesById().size() != 57) {
            throw new ContentValidationException("Entity profile cardinality mismatch enemies="
                    + catalog.enemiesById().size() + " bosses=" + catalog.bossesById().size()
                    + " support=" + catalog.supportEntitiesById().size() + " actions="
                    + catalog.actionBundlesById().size());
        }
        Set<String> roles = Set.of("CHASER", "FLANKER", "RANGED", "BRUISER", "CONTROLLER",
                "ARTILLERY", "DEFENDER", "SUPPORT", "ELITE", "SIEGE", "SUMMON");
        Set<String> dayProfiles = Set.of("INTRO", "PRESSURE", "ADAPT", "ELITE_SIGNAL", "BOSS_DAY",
                "STATUS_INTRO", "STATUS_CHAIN", "AUGMENT_ADAPT", "ELITE_STATUS", "STATUS_BOSS_DAY",
                "CORRUPTION", "BREAK_LINE", "RECONSTRUCTION_PRESSURE");
        Set<String> statuses = Set.of("", "POISON", "WEAKNESS", "BURN", "BLEED", "ROOT",
                "SILENCE", "DISARM", "CORRUPTION", "SLOW");
        long noReward = 0;
        for (ProductionContentCatalog.EnemyEntry enemy : catalog.enemiesById().values()) {
            if (!roles.contains(enemy.role()) || !dayProfiles.contains(enemy.dayProfile())
                    || !statuses.contains(enemy.statusId()) || enemy.name().isBlank() || enemy.bukkitType().isBlank()
                    || enemy.firstDay() < 1 || enemy.firstDay() > 50 || enemy.baseHp() <= 0
                    || enemy.defence() < 0 || enemy.attackDamage() <= 0 || enemy.penetration() < 0
                    || enemy.breakMax() < 0 || enemy.augmentSlots() < 0 || enemy.augmentSlots() > 3
                    || enemy.telegraphTicks() < 5 || enemy.cooldownTicks() <= enemy.telegraphTicks()
                    || enemy.attackRange() <= 0 || enemy.activityExp() < 0 || enemy.flags().isEmpty()) {
                throw new ContentValidationException("Invalid enemy profile " + enemy.id());
            }
            if (!catalog.actionBundlesById().containsKey(enemy.actionBundleId())) {
                throw new ContentValidationException("Enemy action bundle missing " + enemy.id()
                        + " -> " + enemy.actionBundleId());
            }
            if (!enemy.rewardsPlayers()) {
                noReward++;
                if (enemy.activityExp() != 0 || !enemy.flags().containsAll(
                        List.of("NO_REWARD", "NO_SAMPLE", "NO_AUGMENT_TRIGGER"))) {
                    throw new ContentValidationException("No-reward enemy leaks rewards " + enemy.id());
                }
            }
        }
        if (noReward != 8) throw new ContentValidationException("No-reward summon count must be 8");
        Map<Integer, Double> expectedBossHp = Map.of(10, 105000.0, 20, 175000.0, 30, 360000.0, 40, 650000.0);
        for (ProductionContentCatalog.BossEntry boss : catalog.bossesById().values()) {
            if (!expectedBossHp.containsKey(boss.firstDay())
                    || Double.compare(expectedBossHp.get(boss.firstDay()), boss.baseHp()) != 0
                    || boss.name().isBlank() || boss.bukkitType().isBlank() || boss.defence() <= 0
                    || boss.attackDamage() <= 0 || boss.breakMax() <= 0 || boss.telegraphTicks() < 20
                    || boss.cooldownTicks() <= boss.telegraphTicks() || boss.attackRange() <= 0
                    || !catalog.actionBundlesById().containsKey(boss.actionBundleId())) {
                throw new ContentValidationException("Invalid boss profile " + boss.id());
            }
        }
        Set<String> supportKinds = Set.of("PROJECTILE", "DEPLOYABLE", "UI_TRANSIENT", "RESOURCE_NODE",
                "SUMMON", "OBJECTIVE", "TELEGRAPH", "AREA", "FINAL_BOSS");
        for (ProductionContentCatalog.SupportEntityEntry support : catalog.supportEntitiesById().values()) {
            if (!supportKinds.contains(support.kind()) || support.bukkitType().isBlank()
                    || support.displayFallback().isBlank() || support.cleanupPolicy().isBlank()
                    || !Set.of("PLAYER", "PARENT", "SYSTEM").contains(support.ownerPolicy())) {
                throw new ContentValidationException("Invalid support entity profile " + support.id());
            }
        }
        Set<String> owners = new HashSet<>(catalog.enemiesById().keySet());
        owners.addAll(catalog.bossesById().keySet());
        for (ProductionContentCatalog.ActionBundleEntry bundle : catalog.actionBundlesById().values()) {
            if (!owners.contains(bundle.ownerId()) || bundle.actions().isEmpty()
                    || !bundle.stateMachine().equals(List.of("READY", "TELEGRAPH", "STARTUP", "ACTIVE", "RECOVERY"))) {
                throw new ContentValidationException("Invalid entity action bundle " + bundle.id());
            }
            for (ProductionContentCatalog.ActionEntry action : bundle.actions()) {
                if (action.id().isBlank() || action.name().isBlank() || action.telegraphTicks() < 5
                        || action.startupTicks() < 0 || action.activeTicks() <= 0 || action.recoveryTicks() < 0
                        || action.cooldownTicks() <= action.telegraphTicks() || action.range() <= 0
                        || action.damage() <= 0 || action.penetration() < 0 || action.breakDamage() < 0) {
                    throw new ContentValidationException("Invalid entity action " + bundle.id() + " -> " + action.id());
                }
            }
        }
    }

    private void validateLootProfiles(ProductionContentCatalog catalog) throws ContentValidationException {
        if (catalog.lootById().size() != 62) {
            throw new ContentValidationException("Loot profile cardinality mismatch " + catalog.lootById().size());
        }
        long enemyTables = catalog.lootById().keySet().stream().filter(id -> id.startsWith("LOOT-EN-")).count();
        long bossTables = catalog.lootById().keySet().stream().filter(id -> id.startsWith("LOOT-BOSS-")).count();
        long nodeTables = catalog.lootById().keySet().stream().filter(id -> id.startsWith("LOOT-NODE-")).count();
        if (enemyTables != 45 || bossTables != 4 || nodeTables != 6) {
            throw new ContentValidationException("Loot category cardinality mismatch enemy=" + enemyTables
                    + " boss=" + bossTables + " node=" + nodeTables);
        }
        Set<String> profiles = Set.of("EMPTY", "COMBAT-D10", "COMBAT-D20", "COMBAT-D30",
                "COMBAT-D40", "COMBAT-D50", "ELITE", "BOSS", "RESOURCE_NODE", "EVENT");
        Set<String> distributions = Set.of("NONE", "CONTRIBUTOR_ROUND_ROBIN", "HARVESTER_PERSONAL", "OWNER");
        Set<String> equipmentKeys = Set.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY",
                "ABYSSAL", "CURRENT_CAP", "BLUEPRINT");
        Set<String> knownItems = new HashSet<>(catalog.itemsById().keySet());
        for (ProductionContentCatalog.LootEntry loot : catalog.lootById().values()) {
            if (!profiles.contains(loot.profile()) || !distributions.contains(loot.distribution())
                    || loot.executionOpcode().isBlank() || loot.sourceId().isBlank()
                    || loot.guaranteedMin() < 0 || loot.guaranteedMax() < loot.guaranteedMin()
                    || loot.specialtyMin() < 0 || loot.specialtyMax() < loot.specialtyMin()
                    || loot.pityLimit() < 0 || loot.requiredToolTierMin() < 0
                    || loot.requiredToolTierMax() < loot.requiredToolTierMin()
                    || !equipmentKeys.containsAll(loot.equipmentChances().keySet())
                    || loot.equipmentChances().values().stream().anyMatch(value -> value < 0 || value > 1)) {
                throw new ContentValidationException("Invalid loot profile " + loot.id());
            }
            for (String itemId : java.util.stream.Stream.concat(loot.guaranteedPool().stream(),
                    loot.specialtyPool().stream()).toList()) {
                if (!catalog.materialsById().containsKey(itemId)) {
                    throw new ContentValidationException("Unknown loot material " + loot.id() + " -> " + itemId);
                }
            }
            for (ProductionContentCatalog.LootFixedEntry fixed : loot.fixedEntries()) {
                if (!knownItems.contains(fixed.itemId()) || fixed.amounts().size() != 3
                        || fixed.amounts().stream().anyMatch(amount -> amount < 1)
                        || !Set.of("RUN_PROOF", "PARTY_DISTRIBUTED").contains(fixed.scope())) {
                    throw new ContentValidationException("Invalid fixed loot " + loot.id() + " -> " + fixed.itemId());
                }
            }
            if (loot.id().startsWith("LOOT-EN-") && (!catalog.enemiesById().containsKey(loot.sourceId())
                    || !"COMBAT_ROLL".equals(loot.executionOpcode()))) {
                throw new ContentValidationException("Enemy loot source mismatch " + loot.id());
            }
            if (loot.id().startsWith("LOOT-BOSS-") && (!catalog.bossesById().containsKey(loot.sourceId())
                    || loot.fixedEntries().isEmpty() || loot.equipmentSelectionProfile().isBlank()
                    || loot.partyAugmentMilestone() < 1 || loot.partyAugmentMilestone() > 4)) {
                throw new ContentValidationException("Boss loot contract mismatch " + loot.id());
            }
            if (loot.id().startsWith("LOOT-NODE-") && (!catalog.supportEntitiesById().containsKey(loot.sourceId())
                    || loot.guaranteedPool().isEmpty() || loot.requiredToolTierMax() > 6)) {
                throw new ContentValidationException("Node loot contract mismatch " + loot.id());
            }
        }
        ProductionContentCatalog.LootEntry none = catalog.lootById().get("LOOT-NONE");
        if (none == null || !none.noReward() || !none.guaranteedPool().isEmpty()
                || !none.specialtyPool().isEmpty() || !none.fixedEntries().isEmpty()
                || !none.flags().containsAll(List.of("NO_REWARD", "NO_ROLL", "NO_CLAIM"))) {
            throw new ContentValidationException("LOOT-NONE must be an explicit empty table");
        }
        for (ProductionContentCatalog.EnemyEntry enemy : catalog.enemiesById().values()) {
            if (!catalog.lootById().containsKey(enemy.lootTableId())) {
                throw new ContentValidationException("Enemy typed loot reference missing " + enemy.id());
            }
        }
        for (ProductionContentCatalog.BossEntry boss : catalog.bossesById().values()) {
            if (!catalog.lootById().containsKey(boss.lootTableId())) {
                throw new ContentValidationException("Boss typed loot reference missing " + boss.id());
            }
        }
        for (ProductionContentCatalog.SupportEntityEntry support : catalog.supportEntitiesById().values()) {
            if (!catalog.lootById().containsKey(support.lootTableId())) {
                throw new ContentValidationException("Support typed loot reference missing " + support.id());
            }
        }
    }

    private void validateStructuredAuthorityData(ResourceReader reader) throws Exception {
        Set<String> researchIds = new HashSet<>();
        List<String> researchStates = List.of("LOCKED", "AVAILABLE", "READY_LOCKED", "RUNNING", "COMPLETED", "MASTERED");
        for (JsonObject record : records(reader, "research/season1-research.json")) {
            rejectAuthorityStub(record);
            String id = requiredString(record, "id");
            JsonObject cost = record.getAsJsonObject("cost");
            if (!id.matches("^RS-[A-Z0-9-]+$") || !researchIds.add(id)
                    || !"RESEARCH-001".equals(requiredString(record, "sourceDocumentId"))
                    || !record.get("enabled").getAsBoolean()
                    || requiredInt(record, "minimumDay") < 1 || requiredInt(record, "minimumDay") > 50
                    || requiredString(record, "prerequisiteText").isBlank()
                    || requiredString(record, "comparisonInput").isBlank()
                    || cost == null || requiredInt(cost, "general") < 0 || requiredInt(cost, "metal") < 0
                    || requiredInt(cost, "signal") < 0 || requiredInt(cost, "specialist") < 0
                    || requiredInt(record, "durationSeconds") <= 0
                    || requiredString(record, "unlockText").isBlank()
                    || !stringArray(record, "stateMachine").equals(researchStates)
                    || record.getAsJsonArray("raw") == null || record.getAsJsonArray("raw").size() != 5) {
                throw new ContentValidationException("Invalid research authority record " + id);
            }
        }

        Set<String> sceneIds = new HashSet<>();
        Set<String> sceneGroups = Set.of("PROLOGUE", "CHAPTER", "FINAL");
        Set<String> priorities = Set.of("STORY", "STORY_MAJOR", "SYSTEM");
        for (JsonObject record : records(reader, "story/season1-scenes.json")) {
            rejectAuthorityStub(record);
            String id = requiredString(record, "id");
            if (!id.matches("^ST[0-9]-[A-Z0-9-]+$") || !sceneIds.add(id)
                    || !"STORY-DATA-S1-001".equals(requiredString(record, "sourceDocumentId"))
                    || !record.get("enabled").getAsBoolean()
                    || !sceneGroups.contains(requiredString(record, "sceneGroup"))
                    || requiredString(record, "triggerKey").isBlank()
                    || requiredString(record, "triggerEvent").isBlank()
                    || !"PARTY".equals(requiredString(record, "audience"))
                    || !priorities.contains(requiredString(record, "priority"))
                    || record.get("blocking").getAsBoolean()
                    || !record.get("replayableText").getAsBoolean()
                    || record.get("worldEffectReplayable").getAsBoolean()
                    || !"RUN".equals(requiredString(record, "idempotencyScope"))
                    || record.getAsJsonArray("raw") == null || record.getAsJsonArray("raw").size() != 4) {
                throw new ContentValidationException("Invalid Story scene authority record " + id);
            }
        }

        Set<String> logIds = new HashSet<>();
        for (JsonObject record : records(reader, "story/season1-logs.json")) {
            rejectAuthorityStub(record);
            String id = requiredString(record, "id");
            if (!id.matches("^LOG-O\\d{2}$") || !logIds.add(id)
                    || !"STORY-DATA-S1-001".equals(requiredString(record, "sourceDocumentId"))
                    || !record.get("enabled").getAsBoolean()
                    || requiredString(record, "triggerText").isBlank()
                    || requiredString(record, "payloadKey").isBlank()
                    || requiredString(record, "progressionEffect").isBlank()
                    || record.get("progressionRequired").getAsBoolean()
                    || record.getAsJsonArray("raw") == null || record.getAsJsonArray("raw").size() != 4) {
                throw new ContentValidationException("Invalid Story log authority record " + id);
            }
        }
    }

    private static void rejectAuthorityStub(JsonObject record) throws ContentValidationException {
        if (record.toString().contains("AUTHORITY_DATA")) {
            throw new ContentValidationException("Authority stub is forbidden: " + optionalString(record, "id", "unknown"));
        }
    }

    private int count(ResourceReader reader, String path) throws Exception { return records(reader, path).size(); }
    private Set<String> ids(ResourceReader reader, String path) throws Exception {
        Set<String> result = new HashSet<>();
        for (JsonObject record : records(reader, path)) {
            String id = requiredString(record, "id");
            if (!result.add(id)) throw new ContentValidationException("Duplicate ID " + id + " in " + path);
        }
        return result;
    }
    private List<JsonObject> records(ResourceReader reader, String path) throws Exception {
        JsonObject root = JsonParser.parseString(new String(reader.read(path), StandardCharsets.UTF_8)).getAsJsonObject();
        requireNumber(root, "schemaVersion", 2);
        requireString(root, "contentRevision", REVISION);
        JsonArray array = root.getAsJsonArray("records");
        if (array == null) throw new ContentValidationException("records missing in " + path);
        List<JsonObject> result = new ArrayList<>();
        array.forEach(value -> result.add(value.getAsJsonObject()));
        return result;
    }
    private static Map<String, String> parseYamlLock(String yaml) {
        Map<String, String> values = new HashMap<>();
        for (String line : yaml.split("\\R")) {
            int separator = line.indexOf(':');
            if (separator <= 0) continue;
            values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim().replace("\"", ""));
        }
        return values;
    }
    private static int inferFirstDay(String id) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^|-)D(\\d{1,2})(?:-|$)").matcher(id);
        return matcher.find() ? Math.min(50, Integer.parseInt(matcher.group(1))) : 1;
    }
    private static void requireEquals(String field, String expected, String actual) throws ContentValidationException {
        if (!expected.equals(actual)) throw new ContentValidationException(field + " must be " + expected);
    }
    private static void requireHash(String path, String expected, byte[] bytes) throws ContentValidationException {
        if (expected == null || !expected.matches("[0-9a-f]{64}") || !expected.equals(ContentBundleValidator.sha256(bytes))) {
            throw new ContentValidationException("SHA-256 mismatch for " + path);
        }
    }
    private static String requiredString(JsonObject object, String key) throws ContentValidationException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) throw new ContentValidationException("Missing string " + key);
        return object.get(key).getAsString();
    }
    private static int requiredInt(JsonObject object, String key) throws ContentValidationException {
        if (!object.has(key)) throw new ContentValidationException("Missing integer " + key);
        return object.get(key).getAsInt();
    }
    private static double requiredDouble(JsonObject object, String key) throws ContentValidationException {
        if (!object.has(key)) throw new ContentValidationException("Missing number " + key);
        return object.get(key).getAsDouble();
    }
    private static String optionalString(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }
    private static int optionalInt(JsonObject object, String key, int fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
    }
    private static void requireString(JsonObject object, String key, String expected) throws ContentValidationException {
        requireEquals(key, expected, requiredString(object, key));
    }
    private static void requireNumber(JsonObject object, String key, int expected) throws ContentValidationException {
        if (!object.has(key) || object.get(key).getAsInt() != expected) throw new ContentValidationException(key + " must be " + expected);
    }

    @FunctionalInterface public interface ResourceReader { byte[] read(String path) throws IOException; }
    public record ValidationResult(ProductionContentCatalog catalog, Map<String, Integer> counts,
                                   int verifiedFileCount, String manifestSha256) { }
}
