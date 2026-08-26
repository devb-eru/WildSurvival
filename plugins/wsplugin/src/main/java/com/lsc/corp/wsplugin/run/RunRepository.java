package com.lsc.corp.wsplugin.run;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class RunRepository {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path runsDirectory;
    private final Path currentFile;
    private final Set<RunIdentity> allowedIdentities;
    private final String repositoryLabel;
    private final AtomicFileStore.Replacer fileReplacer;

    public RunRepository(Path dataDirectory) {
        this(dataDirectory.resolve("runs"), Set.of(new RunIdentity("PROTOTYPE", "ws-prototype-r1")),
                "legacy-prototype", AtomicFileStore::replace);
    }

    RunRepository(Path dataDirectory, AtomicFileStore.Replacer fileReplacer) {
        this(dataDirectory.resolve("runs"), Set.of(new RunIdentity("PROTOTYPE", "ws-prototype-r1")),
                "legacy-prototype", fileReplacer);
    }

    private RunRepository(Path runsDirectory, Set<RunIdentity> allowedIdentities, String repositoryLabel,
                          AtomicFileStore.Replacer fileReplacer) {
        this.runsDirectory = runsDirectory;
        currentFile = runsDirectory.resolve("current.json");
        this.allowedIdentities = Set.copyOf(allowedIdentities);
        this.repositoryLabel = repositoryLabel;
        this.fileReplacer = fileReplacer;
    }

    public static RunRepository season1(Path dataDirectory) {
        return new RunRepository(dataDirectory.resolve("season-1").resolve("runs"),
                Set.of(new RunIdentity("SEASON_1", "ws-content-r2")), "season-1", AtomicFileStore::replace);
    }

    public static RunRepository testLab(Path dataDirectory) {
        return new RunRepository(dataDirectory.resolve("test-lab").resolve("runs"), Set.of(
                new RunIdentity("TEST", "ws-prototype-r1"),
                new RunIdentity("TEST", "ws-content-r2"),
                new RunIdentity("TEST", "ws-content-r2.1")), "test-lab", AtomicFileStore::replace);
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
        if (snapshot.schemaVersion != 3) {
            throw new IOException("Cannot save unsupported run schema version " + snapshot.schemaVersion);
        }
        validateIdentity(snapshot);
        long previousVersion = snapshot.version;
        try {
            snapshot.version = previousVersion + 1;
            atomicWrite(currentFile, "current.json.", gson.toJson(snapshot));
        } catch (IOException | RuntimeException exception) {
            snapshot.version = previousVersion;
            throw exception;
        }
    }

    public synchronized void archiveAndClear(RunSnapshot snapshot) throws IOException {
        validateIdentity(snapshot);
        Files.createDirectories(runsDirectory.resolve("history"));
        String safeId = snapshot.runId == null ? "unknown" : snapshot.runId.replaceAll("[^A-Za-z0-9._-]", "_");
        Path archived = runsDirectory.resolve("history").resolve(safeId + "-" + Instant.now().toEpochMilli() + ".json");
        atomicWrite(archived, archived.getFileName() + ".", gson.toJson(snapshot));
        // Cleanup can fail on Windows while a stale temporary file is still locked. Keep the
        // authoritative current snapshot until every fallible pre-clear step has succeeded.
        deleteTemporarySnapshots();
        Files.deleteIfExists(currentFile);
    }

    private void atomicWrite(Path target, String temporaryPrefix, String json) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = null;
        Throwable failure = null;
        try {
            temporary = Files.createTempFile(target.getParent(), temporaryPrefix, ".tmp");
            Files.writeString(temporary, json + System.lineSeparator(), StandardCharsets.UTF_8);
            fileReplacer.replace(temporary, target);
        } catch (IOException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    if (failure != null) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
        }
    }

    private void deleteTemporarySnapshots() throws IOException {
        try (Stream<Path> paths = Files.list(runsDirectory)) {
            for (Path path : paths.filter(RunRepository::isTemporarySnapshot).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static boolean isTemporarySnapshot(Path path) {
        String name = path.getFileName().toString();
        return name.equals("current.json.tmp") || name.startsWith("current.json.") && name.endsWith(".tmp");
    }

    private void migrate(RunSnapshot snapshot) throws IOException {
        if (snapshot.schemaVersion == 1 || snapshot.schemaVersion == 2) {
            snapshot.schemaVersion = 3;
        } else if (snapshot.schemaVersion != 3) {
            throw new IOException("Unsupported run schema version " + snapshot.schemaVersion);
        }
        normalizeSeasonState(snapshot);
        validateIdentity(snapshot);
    }

    private static void normalizeSeasonState(RunSnapshot snapshot) {
        if (snapshot.registeredPlayers == null) snapshot.registeredPlayers = new ArrayList<>();
        if (snapshot.players == null) snapshot.players = new LinkedHashMap<>();
        if (snapshot.resources == null) snapshot.resources = new LinkedHashMap<>();
        if (snapshot.resourceTransactions == null) snapshot.resourceTransactions = new LinkedHashMap<>();
        if (snapshot.committedKeys == null) snapshot.committedKeys = new LinkedHashSet<>();
        if (snapshot.outbox == null) snapshot.outbox = new ArrayList<>();
        if (snapshot.milestoneLocks == null) snapshot.milestoneLocks = new LinkedHashMap<>();
        if (snapshot.partyAugmentIds == null) snapshot.partyAugmentIds = new ArrayList<>();
        if (snapshot.resolvedPartyAugmentMilestones == null) snapshot.resolvedPartyAugmentMilestones = new LinkedHashSet<>();
        if (snapshot.partyAugmentVotes == null) snapshot.partyAugmentVotes = new LinkedHashMap<>();
        if (snapshot.facilities == null) snapshot.facilities = new LinkedHashMap<>();
        if (snapshot.facilityTypesEverActivated == null) snapshot.facilityTypesEverActivated = new LinkedHashSet<>();
        if (snapshot.facilityRecoveryLedger == null) snapshot.facilityRecoveryLedger = new LinkedHashMap<>();
        if (snapshot.lootTransactions == null) snapshot.lootTransactions = new LinkedHashMap<>();
        if (snapshot.lootPityCounters == null) snapshot.lootPityCounters = new LinkedHashMap<>();
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
            if (player.equippedTemplateBySlot == null) player.equippedTemplateBySlot = new LinkedHashMap<>();
            if (player.equippedInstanceBySlot == null) player.equippedInstanceBySlot = new LinkedHashMap<>();
            if (player.ownedEquipment == null) player.ownedEquipment = new LinkedHashSet<>();
            if (player.equipmentInstances == null) player.equipmentInstances = new LinkedHashMap<>();
            if (player.quickItems == null) player.quickItems = new LinkedHashMap<>();
            if (player.quickBindings == null) player.quickBindings = new LinkedHashMap<>();
            if (player.quickItemUsesByDay == null) player.quickItemUsesByDay = new LinkedHashMap<>();
            if (player.quickItemUsesByCombat == null) player.quickItemUsesByCombat = new LinkedHashMap<>();
            if (player.pendingRemainsDeliveries == null) player.pendingRemainsDeliveries = new LinkedHashMap<>();
            if (player.reviveContributions == null) player.reviveContributions = new LinkedHashMap<>();
            if (player.ammoLedger == null) player.ammoLedger = new LinkedHashMap<>();
            if (player.personalResources == null) player.personalResources = new LinkedHashMap<>();
            if (player.weaponSkillLoadouts == null) player.weaponSkillLoadouts = new LinkedHashMap<>();
            if (player.commonSkillLoadout == null) player.commonSkillLoadout = new LinkedHashMap<>();
            if (player.discoveredItemIds == null) player.discoveredItemIds = new LinkedHashSet<>();
            if (player.pendingRegisteredItems == null) player.pendingRegisteredItems = new LinkedHashMap<>();
            if (player.pendingEquipmentRewards == null) player.pendingEquipmentRewards = new ArrayList<>();
            if (player.pendingBlueprintUnlocks == null) player.pendingBlueprintUnlocks = new LinkedHashSet<>();
            if (player.completedTutorialQuests == null) player.completedTutorialQuests = new LinkedHashSet<>();
            if (player.tutorialSignals == null) player.tutorialSignals = new LinkedHashSet<>();
            if (player.investedStats == null) player.investedStats = new LinkedHashMap<>();
            if (player.personalAugments == null) player.personalAugments = new ArrayList<>();
            if (player.resolvedPersonalMilestones == null) player.resolvedPersonalMilestones = new LinkedHashSet<>();
            player.apStimPulsesRemaining = Math.max(0, Math.min(5, player.apStimPulsesRemaining));
            player.apStimTicksUntilNextPulse = player.apStimPulsesRemaining == 0 ? 0
                    : Math.max(1, Math.min(20, player.apStimTicksUntilNextPulse));
            player.rescueBraceCharges = Math.max(0, Math.min(1, player.rescueBraceCharges));
            player.rescueInterruptThresholdBonus = normalizeBraceBonus(player.rescueInterruptThresholdBonus);
            player.activeRescueInterruptThresholdBonus = normalizeBraceBonus(player.activeRescueInterruptThresholdBonus);
        }
        for (RunSnapshot.ResourceTransactionState transaction : snapshot.resourceTransactions.values()) {
            if (transaction.reservedResources == null) transaction.reservedResources = new LinkedHashMap<>();
            if (transaction.cancellationReasons == null) transaction.cancellationReasons = new ArrayList<>();
            transaction.reservationAttempt = Math.max(1, transaction.reservationAttempt);
            if (transaction.state == null || transaction.state.isBlank()) transaction.state = "VALIDATED";
        }
        for (RunSnapshot.FacilityInstanceState facility : snapshot.facilities.values()) {
            if (facility.queue == null) facility.queue = new ArrayList<>();
            if (facility.outputLedger == null) facility.outputLedger = new LinkedHashMap<>();
            if (facility.storageSlots == null) facility.storageSlots = new LinkedHashMap<>();
            for (RunSnapshot.FacilityWorkState work : facility.queue) {
                if (work.reservedInputs == null) work.reservedInputs = new LinkedHashMap<>();
                if (work.outputs == null) work.outputs = new LinkedHashMap<>();
            }
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

    private static double normalizeBraceBonus(double bonus) {
        if (!Double.isFinite(bonus) || bonus <= 0.0) return 0.0;
        return bonus >= 0.25 ? 0.25 : 0.10;
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
