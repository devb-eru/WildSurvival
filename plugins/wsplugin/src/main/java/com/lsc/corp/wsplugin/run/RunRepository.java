package com.lsc.corp.wsplugin.run;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public final class RunRepository {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path runsDirectory;
    private final Path currentFile;
    private final Set<String> allowedRunTypes;
    private final String repositoryLabel;

    public RunRepository(Path dataDirectory) {
        this(dataDirectory.resolve("runs"), Set.of("PROTOTYPE"), "prototype");
    }

    private RunRepository(Path runsDirectory, Set<String> allowedRunTypes, String repositoryLabel) {
        this.runsDirectory = runsDirectory;
        currentFile = runsDirectory.resolve("current.json");
        this.allowedRunTypes = Set.copyOf(allowedRunTypes);
        this.repositoryLabel = repositoryLabel;
    }

    public static RunRepository testLab(Path dataDirectory) {
        return new RunRepository(dataDirectory.resolve("test-lab").resolve("runs"), Set.of("TEST"), "test-lab");
    }

    public synchronized Optional<RunSnapshot> load() throws IOException {
        if (!Files.exists(currentFile)) {
            return Optional.empty();
        }
        RunSnapshot snapshot = gson.fromJson(Files.readString(currentFile, StandardCharsets.UTF_8), RunSnapshot.class);
        if (snapshot == null) {
            throw new IOException("Run snapshot is empty");
        }
        migrate(snapshot);
        return Optional.of(snapshot);
    }

    public synchronized void save(RunSnapshot snapshot) throws IOException {
        validateIdentity(snapshot);
        Files.createDirectories(runsDirectory);
        snapshot.version++;
        Path temporary = runsDirectory.resolve("current.json.tmp");
        Files.writeString(temporary, gson.toJson(snapshot) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, currentFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, currentFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized void archiveAndClear(RunSnapshot snapshot) throws IOException {
        validateIdentity(snapshot);
        Files.createDirectories(runsDirectory.resolve("history"));
        String safeId = snapshot.runId == null ? "unknown" : snapshot.runId.replaceAll("[^A-Za-z0-9._-]", "_");
        Path archived = runsDirectory.resolve("history").resolve(safeId + "-" + Instant.now().toEpochMilli() + ".json");
        Files.writeString(archived, gson.toJson(snapshot) + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.deleteIfExists(currentFile);
        Files.deleteIfExists(runsDirectory.resolve("current.json.tmp"));
    }

    private void migrate(RunSnapshot snapshot) throws IOException {
        if (snapshot.schemaVersion != 1) {
            throw new IOException("Unsupported run schema version " + snapshot.schemaVersion);
        }
        validateIdentity(snapshot);
    }

    private void validateIdentity(RunSnapshot snapshot) throws IOException {
        if (!allowedRunTypes.contains(snapshot.runType) || !"ws-prototype-r1".equals(snapshot.contentRevision)) {
            throw new IOException(repositoryLabel + " repository refuses runType=" + snapshot.runType
                    + " revision=" + snapshot.contentRevision);
        }
    }

    public Path currentFile() {
        return currentFile;
    }
}
