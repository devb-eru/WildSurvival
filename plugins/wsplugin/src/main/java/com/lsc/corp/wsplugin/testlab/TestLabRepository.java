package com.lsc.corp.wsplugin.testlab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class TestLabRepository {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path root;
    private final Path backups;
    private final Path snapshots;
    private final Path presets;
    private final Path exports;
    private final Path auditFile;

    public TestLabRepository(Path dataDirectory) {
        root = dataDirectory.resolve("test-lab");
        backups = root.resolve("backups");
        snapshots = root.resolve("snapshots");
        presets = root.resolve("presets");
        exports = root.resolve("exports");
        auditFile = root.resolve("audit.jsonl");
    }

    public synchronized void saveBackup(TestPlayerBackup backup) throws IOException {
        atomicWrite(backups.resolve(backup.playerUuid + ".json"), gson.toJson(backup));
    }

    public synchronized Optional<TestPlayerBackup> loadBackup(String playerUuid) throws IOException {
        return read(backups.resolve(playerUuid + ".json"), TestPlayerBackup.class);
    }

    public synchronized void deleteBackup(String playerUuid) throws IOException {
        Files.deleteIfExists(backups.resolve(playerUuid + ".json"));
    }

    public synchronized TestLabSnapshot snapshot(RunSnapshot run, String actorUuid, String reason, int maximum) throws IOException {
        return snapshot(run, actorUuid, reason, maximum, null);
    }

    public synchronized TestLabSnapshot snapshot(RunSnapshot run, String actorUuid, String reason, int maximum,
                                                 TestPlayerBackup player) throws IOException {
        if (!"TEST".equals(run.runType) || run.test == null) {
            throw new IOException("Only Test Lab runs can be snapshotted");
        }
        run.test.snapshotSequence++;
        TestLabSnapshot value = new TestLabSnapshot();
        value.snapshotId = run.runId + "-" + String.format("%06d", run.test.snapshotSequence);
        value.runId = run.runId;
        value.actorUuid = actorUuid;
        value.reason = reason;
        value.sequence = run.test.snapshotSequence;
        value.createdAtEpochMs = Instant.now().toEpochMilli();
        value.run = deepCopy(run);
        value.player = player;
        atomicWrite(snapshots.resolve(value.snapshotId + ".json"), gson.toJson(value));
        trimSnapshots(run.runId, maximum);
        return value;
    }

    public synchronized Optional<TestLabSnapshot> popLatestSnapshot(String runId) throws IOException {
        List<Path> matching = snapshotPaths(runId);
        if (matching.isEmpty()) {
            return Optional.empty();
        }
        Path latest = matching.get(matching.size() - 1);
        TestLabSnapshot value = read(latest, TestLabSnapshot.class).orElseThrow();
        Files.deleteIfExists(latest);
        return Optional.of(value);
    }

    public synchronized List<String> listSnapshots(String runId) throws IOException {
        return snapshotPaths(runId).stream().map(path -> path.getFileName().toString()).toList();
    }

    public synchronized void clearSnapshots(String runId) throws IOException {
        for (Path path : snapshotPaths(runId)) {
            Files.deleteIfExists(path);
        }
    }

    public synchronized void savePreset(TestPreset preset) throws IOException {
        String id = TestValuePolicy.fileId(preset.id).toUpperCase(java.util.Locale.ROOT);
        preset.id = id;
        atomicWrite(presets.resolve(id + ".json"), gson.toJson(preset));
    }

    public synchronized Optional<TestPreset> loadPreset(String id) throws IOException {
        String normalized = TestValuePolicy.fileId(id).toUpperCase(java.util.Locale.ROOT);
        return read(presets.resolve(normalized + ".json"), TestPreset.class);
    }

    public synchronized List<String> listPresets() throws IOException {
        if (!Files.isDirectory(presets)) {
            return List.of();
        }
        try (var stream = Files.list(presets)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .sorted().toList();
        }
    }

    public synchronized void deletePreset(String id) throws IOException {
        String normalized = TestValuePolicy.fileId(id).toUpperCase(java.util.Locale.ROOT);
        Files.deleteIfExists(presets.resolve(normalized + ".json"));
    }

    public synchronized Path export(String category, Object value) throws IOException {
        String safeCategory = TestValuePolicy.fileId(category);
        Path target = exports.resolve(safeCategory + "-" + Instant.now().toEpochMilli() + ".json");
        atomicWrite(target, gson.toJson(value));
        return target;
    }

    public synchronized void audit(String runId, String actor, String action, String before, String after) throws IOException {
        Files.createDirectories(root);
        String row = gson.toJson(new AuditRow(Instant.now().toEpochMilli(), runId, actor, action, before, after));
        Files.writeString(auditFile, row + System.lineSeparator(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    public RunSnapshot deepCopy(RunSnapshot run) {
        return gson.fromJson(gson.toJson(run), RunSnapshot.class);
    }

    public Path root() {
        return root;
    }

    private <T> Optional<T> read(Path path, Class<T> type) throws IOException {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        T value = gson.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        if (value == null) {
            throw new IOException("Empty Test Lab file " + path);
        }
        return Optional.of(value);
    }

    private void atomicWrite(Path target, String json) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, json + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<Path> snapshotPaths(String runId) throws IOException {
        if (!Files.isDirectory(snapshots)) {
            return List.of();
        }
        String prefix = runId + "-";
        try (var stream = Files.list(snapshots)) {
            return stream.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
    }

    private void trimSnapshots(String runId, int maximum) throws IOException {
        List<Path> paths = snapshotPaths(runId);
        for (int index = 0; index < Math.max(0, paths.size() - maximum); index++) {
            Files.deleteIfExists(paths.get(index));
        }
    }

    private record AuditRow(long timestamp, String runId, String actor, String action, String before, String after) {
    }
}
