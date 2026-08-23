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
            Map<String, Integer> expected = Map.ofEntries(
                    Map.entry("materials", 59), Map.entry("items", 61), Map.entry("tools", 214),
                    Map.entry("recipes", 315), Map.entry("codex", 334), Map.entry("skills", 64),
                    Map.entry("personalAugments", 50), Map.entry("partyAugments", 16),
                    Map.entry("enemies", 53), Map.entry("bosses", 4), Map.entry("support", 34),
                    Map.entry("facilities", 46), Map.entry("loot", 62), Map.entry("days", 50));
            for (Map.Entry<String, Integer> entry : expected.entrySet()) {
                if (!entry.getValue().equals(counts.get(entry.getKey()))) {
                    throw new ContentValidationException("Cardinality mismatch " + entry.getKey()
                            + " expected=" + entry.getValue() + " actual=" + counts.get(entry.getKey()));
                }
            }

            ProductionContentCatalog catalog = loadCatalog(reader, counts);
            validateReferences(reader, catalog);
            return new ValidationResult(catalog, Map.copyOf(counts), paths.size() + 2,
                    ContentBundleValidator.sha256(manifestBytes));
        } catch (ContentValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ContentValidationException("Cannot validate production bundle", exception);
        }
    }

    private ProductionContentCatalog loadCatalog(ResourceReader reader, Map<String, Integer> counts) throws Exception {
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
            codex.add(new ProductionContentCatalog.CatalogEntry(id, index, name, material, domain, firstDay));
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
            ProductionContentCatalog.RecipeEntry recipe = new ProductionContentCatalog.RecipeEntry(id,
                    requiredString(record, "outputId"), optionalString(record, "recipeType", "UNKNOWN"),
                    optionalString(record, "inputAuthority", "UNKNOWN"), optionalString(record, "layout", "UNKNOWN"), List.copyOf(raw));
            recipes.add(recipe);
            byOutput.computeIfAbsent(recipe.outputId(), ignored -> new ArrayList<>()).add(recipe);
        }
        byOutput.replaceAll((ignored, values) -> List.copyOf(values));
        return new ProductionContentCatalog(List.copyOf(codex), Map.copyOf(byId), List.copyOf(recipes),
                Map.copyOf(byOutput), Map.copyOf(counts));
    }

    private void validateReferences(ResourceReader reader, ProductionContentCatalog catalog) throws Exception {
        Set<String> knownOutputs = new HashSet<>(catalog.itemsById().keySet());
        for (ProductionContentCatalog.RecipeEntry recipe : catalog.recipes()) {
            String output = recipe.outputId();
            if (!knownOutputs.contains(output) && !output.matches("FAC-[SR][0-9]{2}@[A-Z0-9_]+")) {
                throw new ContentValidationException("Unknown recipe output " + recipe.id() + " -> " + output);
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
