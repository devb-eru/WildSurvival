package com.lsc.corp.wsplugin.run;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class RunRepository {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path runsDirectory;
    private final Path currentFile;

    public RunRepository(Path dataDirectory) {
        runsDirectory = dataDirectory.resolve("runs");
        currentFile = runsDirectory.resolve("current.json");
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

    private void migrate(RunSnapshot snapshot) throws IOException {
        if (snapshot.schemaVersion != 1) {
            throw new IOException("Unsupported run schema version " + snapshot.schemaVersion);
        }
        if (!"PROTOTYPE".equals(snapshot.runType) || !"ws-prototype-r1".equals(snapshot.contentRevision)) {
            throw new IOException("Prototype repository refuses non-prototype revisions");
        }
    }

    public Path currentFile() {
        return currentFile;
    }
}
