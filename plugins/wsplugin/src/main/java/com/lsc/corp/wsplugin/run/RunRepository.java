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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class RunRepository {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path runsDirectory;
    private final Path currentFile;
    private final Set<RunIdentity> allowedIdentities;
    private final String repositoryLabel;

    public RunRepository(Path dataDirectory) {
        this(dataDirectory.resolve("runs"), Set.of(new RunIdentity("PROTOTYPE", "ws-prototype-r1")),
                "legacy-prototype");
    }

    private RunRepository(Path runsDirectory, Set<RunIdentity> allowedIdentities, String repositoryLabel) {
        this.runsDirectory = runsDirectory;
        currentFile = runsDirectory.resolve("current.json");
        this.allowedIdentities = Set.copyOf(allowedIdentities);
        this.repositoryLabel = repositoryLabel;
    }

    public static RunRepository season1(Path dataDirectory) {
        return new RunRepository(dataDirectory.resolve("season-1").resolve("runs"),
                Set.of(new RunIdentity("SEASON_1", "ws-content-r2")), "season-1");
    }

    public static RunRepository testLab(Path dataDirectory) {
        return new RunRepository(dataDirectory.resolve("test-lab").resolve("runs"), Set.of(
                new RunIdentity("TEST", "ws-prototype-r1"),
                new RunIdentity("TEST", "ws-content-r2")), "test-lab");
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
        if (snapshot.schemaVersion != 2) {
            throw new IOException("Cannot save unsupported run schema version " + snapshot.schemaVersion);
        }
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
        if (snapshot.schemaVersion == 1) {
            snapshot.schemaVersion = 2;
        } else if (snapshot.schemaVersion != 2) {
            throw new IOException("Unsupported run schema version " + snapshot.schemaVersion);
        }
        normalizeSeasonState(snapshot);
        validateIdentity(snapshot);
    }

    private static void normalizeSeasonState(RunSnapshot snapshot) {
        if (snapshot.encounters == null) snapshot.encounters = new LinkedHashMap<>();
        if (snapshot.remains == null) snapshot.remains = new LinkedHashMap<>();
        if (snapshot.researchNodes == null) snapshot.researchNodes = new LinkedHashMap<>();
        if (snapshot.discoveryNodes == null) snapshot.discoveryNodes = new LinkedHashMap<>();
        if (snapshot.defeatedBossIds == null) snapshot.defeatedBossIds = new LinkedHashSet<>();
        if (snapshot.reconstructionPartIds == null) snapshot.reconstructionPartIds = new LinkedHashSet<>();
        if (snapshot.discoveryIds == null) snapshot.discoveryIds = new LinkedHashSet<>();
        if (snapshot.seasonDay == null) snapshot.seasonDay = new RunSnapshot.DayState();
        snapshot.seasonDay.day = Math.max(1, Math.min(50, snapshot.day));
        snapshot.seasonDay.dayId = "DAY-%02d".formatted(snapshot.seasonDay.day);
        if (snapshot.seasonDay.state == null || snapshot.seasonDay.state.isBlank()) {
            snapshot.seasonDay.state = "PREPARING";
        }
        if (snapshot.seasonDay.lockedBudgetProfileId == null) {
            snapshot.seasonDay.lockedBudgetProfileId = "STD-BALANCED";
        }
        if (snapshot.seasonDay.lockedResourceBudgets == null) {
            snapshot.seasonDay.lockedResourceBudgets = new ArrayList<>();
        }
        if (snapshot.seasonDay.eventQueue == null) snapshot.seasonDay.eventQueue = new ArrayList<>();
        if (snapshot.story == null) snapshot.story = new RunSnapshot.StoryState();
        if (snapshot.story.queuedSceneIds == null) snapshot.story.queuedSceneIds = new LinkedHashSet<>();
        if (snapshot.story.playedSceneIds == null) snapshot.story.playedSceneIds = new LinkedHashSet<>();
        if (snapshot.story.unlockedLogIds == null) snapshot.story.unlockedLogIds = new LinkedHashSet<>();
        if (snapshot.finalObjective == null) snapshot.finalObjective = new RunSnapshot.FinalState();
        if (snapshot.finalObjective.componentProgress == null) {
            snapshot.finalObjective.componentProgress = new LinkedHashMap<>();
        }
        if (snapshot.finalObjective.activeEntityUuids == null) {
            snapshot.finalObjective.activeEntityUuids = new LinkedHashSet<>();
        }
        if (snapshot.finalObjective.completedTransactionSteps == null) {
            snapshot.finalObjective.completedTransactionSteps = new LinkedHashSet<>();
        }
        if (snapshot.finalObjective.activationVotes == null) snapshot.finalObjective.activationVotes = new LinkedHashSet<>();
        if (snapshot.finalObjective.stage1WaveBudgets == null) snapshot.finalObjective.stage1WaveBudgets = new ArrayList<>();
        if (snapshot.finalObjective.confirmationUuids == null) snapshot.finalObjective.confirmationUuids = new LinkedHashSet<>();
        for (RunSnapshot.EncounterState encounter : snapshot.encounters.values()) {
            if (encounter.plannedEnemyIds == null) encounter.plannedEnemyIds = new ArrayList<>();
            if (encounter.spawnedEntityUuids == null) encounter.spawnedEntityUuids = new LinkedHashSet<>();
            if (encounter.participantUuids == null) encounter.participantUuids = new LinkedHashSet<>();
        }
        for (RunSnapshot.RemainsState remains : snapshot.remains.values()) {
            if (remains.contents == null) remains.contents = new LinkedHashMap<>();
            if (remains.equipmentInstances == null) remains.equipmentInstances = new LinkedHashMap<>();
        }
        for (RunSnapshot.PlayerState player : snapshot.players.values()) {
            if (player.pendingRemainsDeliveries == null) player.pendingRemainsDeliveries = new LinkedHashMap<>();
            if (player.reviveContributions == null) player.reviveContributions = new LinkedHashMap<>();
        }
        for (RunSnapshot.ResearchNodeState research : snapshot.researchNodes.values()) {
            if (research.reservedCost == null) research.reservedCost = new LinkedHashMap<>();
            String researchState = research.state == null ? "HIDDEN" : research.state;
            research.state = switch (researchState) {
                case "LOCKED" -> "HIDDEN";
                case "AVAILABLE" -> "OBSERVABLE";
                case "READY_LOCKED" -> "HYPOTHESIZED";
                case "RUNNING" -> "PROCESSING";
                case "COMPLETED" -> "UNLOCKED";
                default -> researchState;
            };
        }
        for (RunSnapshot.DiscoveryNodeState discovery : snapshot.discoveryNodes.values()) {
            if (discovery.evidence == null) discovery.evidence = new LinkedHashSet<>();
            if (discovery.state == null || discovery.state.isBlank()) discovery.state = "HIDDEN";
        }
    }

    private void validateIdentity(RunSnapshot snapshot) throws IOException {
        if (!allowedIdentities.contains(new RunIdentity(snapshot.runType, snapshot.contentRevision))) {
            throw new IOException(repositoryLabel + " repository refuses runType=" + snapshot.runType
                    + " revision=" + snapshot.contentRevision);
        }
    }

    public Path currentFile() {
        return currentFile;
    }

    private record RunIdentity(String runType, String contentRevision) { }
}
