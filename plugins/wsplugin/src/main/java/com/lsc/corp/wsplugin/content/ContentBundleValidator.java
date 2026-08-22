package com.lsc.corp.wsplugin.content;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class ContentBundleValidator {
    public static final String REVISION = "ws-prototype-r1";
    private static final Set<Integer> CHECKPOINTS = Set.of(1, 3, 6, 10);
    private final Gson gson = new Gson();

    public ValidationResult validateDirectory(Path root) throws ContentValidationException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        return validate(path -> {
            Path resolved = normalizedRoot.resolve(path).normalize();
            if (!resolved.startsWith(normalizedRoot)) {
                throw new IOException("Path escapes bundle root: " + path);
            }
            return Files.readAllBytes(resolved);
        });
    }

    public ValidationResult validate(ResourceReader reader) throws ContentValidationException {
        try {
            byte[] lockBytes = reader.read("content-lock.json");
            byte[] manifestBytes = reader.read("manifest.json");
            JsonObject lock = JsonParser.parseString(new String(lockBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            requireExact(lock, "contentRevision", REVISION);
            requireExact(lock, "runType", "PROTOTYPE");
            requireTrue(lock, "promotionForbidden");
            String expectedManifestHash = lock.get("manifestSha256").getAsString();
            requireHash("manifest.json", expectedManifestHash, manifestBytes);

            BundleManifest manifest = gson.fromJson(new String(manifestBytes, StandardCharsets.UTF_8), BundleManifest.class);
            if (manifest == null || manifest.schemaVersion() != 1) {
                throw new ContentValidationException("manifest schemaVersion must be 1");
            }
            if (!REVISION.equals(manifest.contentRevision())
                    || !"DEVELOPMENT_ONLY".equals(manifest.activationPolicy())
                    || !"PROTOTYPE".equals(manifest.runType())
                    || !manifest.promotionForbidden()) {
                throw new ContentValidationException("Prototype isolation contract is invalid");
            }
            if (manifest.files() == null || manifest.files().isEmpty()) {
                throw new ContentValidationException("manifest files must not be empty");
            }

            Set<String> paths = new HashSet<>();
            for (BundleManifest.FileEntry entry : manifest.files()) {
                if (entry.path() == null || entry.path().contains("..") || entry.path().startsWith("/") || !paths.add(entry.path())) {
                    throw new ContentValidationException("Invalid or duplicate manifest path: " + entry.path());
                }
                requireHash(entry.path(), entry.sha256(), reader.read(entry.path()));
            }

            byte[] data = reader.read("prototype/prototype-data.json");
            PrototypeContent content = gson.fromJson(new String(data, StandardCharsets.UTF_8), PrototypeContent.class);
            validateContent(content);
            return new ValidationResult(manifest, content, paths.size() + 2);
        } catch (ContentValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ContentValidationException("Cannot validate prototype bundle", exception);
        }
    }

    private void validateContent(PrototypeContent content) throws ContentValidationException {
        if (content == null || content.schemaVersion() != 1 || !REVISION.equals(content.contentRevision())) {
            throw new ContentValidationException("Prototype data revision mismatch");
        }
        if (!"PROTOTYPE".equals(content.runType()) || !content.promotionForbidden()) {
            throw new ContentValidationException("Prototype data can never be promoted to a live revision");
        }
        if (content.minimumPlayers() != 2 || content.maximumPlayers() != 4) {
            throw new ContentValidationException("Prototype party size must be 2..4");
        }
        if (!new HashSet<>(content.dayCheckpoints()).equals(CHECKPOINTS) || content.dayCheckpoints().size() != 4) {
            throw new ContentValidationException("Prototype checkpoints must be exactly 1,3,6,10");
        }
        if (content.resources().size() < 4 || content.recipes().size() < 6) {
            throw new ContentValidationException("Prototype requires at least four resources and six recipes");
        }
        Set<String> weaponIds = ids(content.weapons().stream().map(PrototypeContent.WeaponDefinition::id).toList());
        if (!weaponIds.containsAll(Set.of("SWORD", "BOW", "PICKAXE", "UNARMED", "TRIDENT"))) {
            throw new ContentValidationException("Representative weapon routes are incomplete");
        }
        for (PrototypeContent.RecipeDefinition recipe : content.recipes()) {
            if (recipe.costs().values().stream().anyMatch(value -> value <= 0)) {
                throw new ContentValidationException("Recipe costs must be positive: " + recipe.id());
            }
            if (recipe.shape() == null || recipe.shape().size() != 9) {
                throw new ContentValidationException("Recipe shape must contain exactly nine cells: " + recipe.id());
            }
            java.util.Map<String, Integer> shapeCosts = new HashMap<>();
            for (String resourceId : recipe.shape()) {
                if (resourceId != null) {
                    content.resource(resourceId);
                    shapeCosts.merge(resourceId, 1, Integer::sum);
                }
            }
            if (!shapeCosts.equals(recipe.costs())) {
                throw new ContentValidationException("Recipe shape and costs disagree: " + recipe.id());
            }
        }
        if (content.recipes().stream().noneMatch(recipe -> "COMMON_RESOURCE_DEPOT".equals(recipe.rewardId()))) {
            throw new ContentValidationException("Shared-resource depot recipe is required");
        }
        ids(content.personalAugments().stream().map(PrototypeContent.AugmentDefinition::id).toList());
        ids(content.partyAugments().stream().map(PrototypeContent.AugmentDefinition::id).toList());
        Set<String> tiers = new HashSet<>(content.personalAugments().stream().map(PrototypeContent.AugmentDefinition::tier).toList());
        if (!tiers.containsAll(Set.of("SILVER", "GOLD", "PRISM"))) {
            throw new ContentValidationException("Personal prototype augments must cover all fixed tiers");
        }
        if (content.partyAugments().size() < 3 || content.boss() == null || content.boss().day() != 10
                || content.boss().phaseTwoHpPercent() <= 0 || content.boss().phaseTwoHpPercent() >= 100) {
            throw new ContentValidationException("Party draw or two-phase Day 10 boss is incomplete");
        }
    }

    private static Set<String> ids(List<String> values) throws ContentValidationException {
        Set<String> result = new HashSet<>(values);
        if (result.size() != values.size()) {
            throw new ContentValidationException("Duplicate stable ID");
        }
        return result;
    }

    private static void requireExact(JsonObject object, String key, String expected) throws ContentValidationException {
        if (!object.has(key) || !expected.equals(object.get(key).getAsString())) {
            throw new ContentValidationException(key + " must be " + expected);
        }
    }

    private static void requireTrue(JsonObject object, String key) throws ContentValidationException {
        if (!object.has(key) || !object.get(key).getAsBoolean()) {
            throw new ContentValidationException(key + " must be true");
        }
    }

    private static void requireHash(String path, String expected, byte[] bytes) throws ContentValidationException {
        if (expected == null || !expected.matches("[0-9a-f]{64}")) {
            throw new ContentValidationException("Invalid SHA-256 in manifest for " + path);
        }
        String actual = sha256(bytes);
        if (!actual.equals(expected)) {
            throw new ContentValidationException("SHA-256 mismatch for " + path + ": " + actual);
        }
    }

    public static String sha256(byte[] bytes) throws ContentValidationException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new ContentValidationException("SHA-256 is unavailable", exception);
        }
    }

    @FunctionalInterface
    public interface ResourceReader {
        byte[] read(String path) throws IOException;
    }

    public record ValidationResult(BundleManifest manifest, PrototypeContent content, int verifiedFileCount) {}
}
