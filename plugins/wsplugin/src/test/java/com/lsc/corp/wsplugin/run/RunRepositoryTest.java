package com.lsc.corp.wsplugin.run;

import com.lsc.corp.wsplugin.combat.ApStimPulsePolicy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunRepositoryTest {
    @Test
    void restoresPrototypeLockAndLedger(@TempDir Path temporary) throws Exception {
        RunRepository repository = new RunRepository(temporary);
        RunSnapshot snapshot = snapshot();
        snapshot.resources.put("WSR-IRON", 6);
        snapshot.committedKeys.add("reward-1");
        repository.save(snapshot);

        RunSnapshot restored = repository.load().orElseThrow();
        assertEquals("ws-prototype-r1", restored.contentRevision);
        assertEquals(6, restored.resources.get("WSR-IRON"));
        assertTrue(restored.committedKeys.contains("reward-1"));
        assertEquals(1, restored.version);
        assertEquals(3, restored.schemaVersion);
    }

    @Test
    void refusesLiveRevisionInPrototypeRepository(@TempDir Path temporary) throws Exception {
        RunRepository repository = new RunRepository(temporary);
        RunSnapshot snapshot = snapshot();
        snapshot.contentRevision = "ws-content-r2";

        assertThrows(IOException.class, () -> repository.save(snapshot));
    }

    @Test
    void persistsSeasonOneInAnIsolatedProductionRepository(@TempDir Path temporary) throws Exception {
        RunRepository repository = RunRepository.season1(temporary);
        RunSnapshot snapshot = snapshot();
        snapshot.runId = "s1-production";
        snapshot.runType = "SEASON_1";
        snapshot.contentRevision = "ws-content-r2";

        repository.save(snapshot);

        RunSnapshot restored = repository.load().orElseThrow();
        assertEquals("SEASON_1", restored.runType);
        assertEquals("ws-content-r2", restored.contentRevision);
        assertTrue(new RunRepository(temporary).load().isEmpty());
        assertThrows(IOException.class, () -> repository.save(snapshot()));
    }

    @Test
    void testLabAcceptsLegacyRestoreAndNewProductionRevision(@TempDir Path temporary) throws Exception {
        RunRepository repository = RunRepository.testLab(temporary);
        RunSnapshot snapshot = snapshot();
        snapshot.runId = "test-production";
        snapshot.runType = "TEST";
        snapshot.contentRevision = "ws-content-r2";
        snapshot.test = new RunSnapshot.TestState();

        repository.save(snapshot);

        assertEquals("ws-content-r2", repository.load().orElseThrow().contentRevision);

        snapshot.contentRevision = "ws-content-r2.1";
        repository.save(snapshot);
        assertEquals("ws-content-r2.1", repository.load().orElseThrow().contentRevision);
    }

    @Test
    void isolatesTestLabRunsFromPrototypeRepository(@TempDir Path temporary) throws Exception {
        RunRepository testRepository = RunRepository.testLab(temporary);
        RunSnapshot snapshot = snapshot();
        snapshot.runId = "test-isolated";
        snapshot.runType = "TEST";
        snapshot.test = new RunSnapshot.TestState();
        snapshot.test.ownerUuid = "owner";
        testRepository.save(snapshot);

        assertTrue(new RunRepository(temporary).load().isEmpty());
        assertEquals("TEST", testRepository.load().orElseThrow().runType);
        assertThrows(IOException.class, () -> new RunRepository(temporary).save(snapshot));
    }

    @Test
    void archivesAndClearsFinishedTestRun(@TempDir Path temporary) throws Exception {
        RunRepository repository = RunRepository.testLab(temporary);
        RunSnapshot snapshot = snapshot();
        snapshot.runId = "test-archive";
        snapshot.runType = "TEST";
        snapshot.state = "ABORTED";
        repository.save(snapshot);

        repository.archiveAndClear(snapshot);

        assertTrue(repository.load().isEmpty());
        assertTrue(java.nio.file.Files.list(temporary.resolve("test-lab/runs/history")).findAny().isPresent());
    }

    @Test
    void rollsBackVersionAndCleansUniqueTemporaryFileWhenReplacementFails(@TempDir Path temporary) {
        RunRepository repository = new RunRepository(temporary,
                (source, destination) -> { throw new AccessDeniedException(destination.toString()); });
        RunSnapshot snapshot = snapshot();
        snapshot.version = 7;

        assertThrows(AccessDeniedException.class, () -> repository.save(snapshot));

        assertEquals(7, snapshot.version);
        assertTrue(Files.notExists(repository.currentFile()));
        assertTrue(listCurrentTemporaryFiles(temporary.resolve("runs")).isEmpty());
    }

    @Test
    void archiveRemovesLegacyAndUniqueTemporarySnapshots(@TempDir Path temporary) throws Exception {
        RunRepository repository = new RunRepository(temporary);
        RunSnapshot snapshot = snapshot();
        repository.save(snapshot);
        Path runs = temporary.resolve("runs");
        Files.writeString(runs.resolve("current.json.tmp"), "legacy", StandardCharsets.UTF_8);
        Files.writeString(runs.resolve("current.json.123.tmp"), "unique", StandardCharsets.UTF_8);
        Files.writeString(runs.resolve("unrelated.tmp"), "keep", StandardCharsets.UTF_8);

        repository.archiveAndClear(snapshot);

        assertTrue(listCurrentTemporaryFiles(runs).isEmpty());
        assertTrue(Files.exists(runs.resolve("unrelated.tmp")));
    }

    @Test
    void archiveRetainsCurrentWhenTemporaryCleanupFails(@TempDir Path temporary) throws Exception {
        RunRepository repository = new RunRepository(temporary);
        RunSnapshot snapshot = snapshot();
        repository.save(snapshot);
        Path runs = temporary.resolve("runs");
        Path lockedTemporary = runs.resolve("current.json.locked.tmp");
        Files.createDirectories(lockedTemporary);
        Files.writeString(lockedTemporary.resolve("held"), "locked", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> repository.archiveAndClear(snapshot));

        assertTrue(Files.exists(repository.currentFile()));
        assertTrue(repository.load().isPresent());
    }

    @Test
    void migratesLegacyPrototypeIntoRecoverableSeasonState(@TempDir Path temporary) throws Exception {
        Path current = temporary.resolve("runs/current.json");
        Files.createDirectories(current.getParent());
        Files.writeString(current, """
                {
                  "schemaVersion": 1,
                  "runId": "legacy-proto",
                  "contentRevision": "ws-prototype-r1",
                  "runType": "PROTOTYPE",
                  "state": "RUNNING",
                  "day": 20
                }
                """, StandardCharsets.UTF_8);

        RunSnapshot restored = new RunRepository(temporary).load().orElseThrow();

        assertEquals(3, restored.schemaVersion);
        assertEquals(20, restored.seasonDay.day);
        assertEquals("DAY-20", restored.seasonDay.dayId);
        assertEquals("LOCKED", restored.finalObjective.state);
        assertTrue(restored.encounters.isEmpty());
        assertTrue(restored.researchNodes.isEmpty());
        assertTrue(restored.story.playedSceneIds.isEmpty());
        assertTrue(restored.defeatedBossIds.isEmpty());
    }

    @Test
    void migratesSchemaTwoPersonalLedgersAndRuntimeCounters(@TempDir Path temporary) throws Exception {
        Path current = temporary.resolve("runs/current.json");
        Files.createDirectories(current.getParent());
        Files.writeString(current, """
                {
                  "schemaVersion": 2,
                  "runId": "legacy-proto",
                  "contentRevision": "ws-prototype-r1",
                  "runType": "PROTOTYPE",
                  "state": "RUNNING",
                  "players": {
                    "player-1": {
                      "uuid": "player-1",
                      "apStimPulsesRemaining": 99,
                      "apStimTicksUntilNextPulse": 0,
                      "rescueBraceCharges": 4,
                      "rescueInterruptThresholdBonus": 0.21
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        RunSnapshot restored = new RunRepository(temporary).load().orElseThrow();
        RunSnapshot.PlayerState player = restored.players.get("player-1");

        assertEquals(3, restored.schemaVersion);
        assertTrue(restored.resourceTransactions.isEmpty());
        assertTrue(player.personalResources.isEmpty());
        assertEquals(5, player.apStimPulsesRemaining);
        assertEquals(1, player.apStimTicksUntilNextPulse);
        assertEquals(1, player.rescueBraceCharges);
        assertEquals(0.10, player.rescueInterruptThresholdBonus);
    }

    @Test
    void persistsSeasonExecutionDomains(@TempDir Path temporary) throws Exception {
        RunRepository repository = new RunRepository(temporary);
        RunSnapshot snapshot = snapshot();
        snapshot.day = 50;
        snapshot.seasonDay.day = 50;
        snapshot.seasonDay.dayId = "DAY-50";
        snapshot.seasonDay.state = "ACTIVE";
        snapshot.seasonDay.eventQueue.add("EV50-D50-LAST-SIGNAL");
        RunSnapshot.EncounterState encounter = new RunSnapshot.EncounterState();
        encounter.encounterId = "encounter-50";
        encounter.eventId = "EV50-D50-LAST-SIGNAL";
        encounter.day = 50;
        snapshot.encounters.put(encounter.encounterId, encounter);
        snapshot.defeatedBossIds.add("BOSS-D40");
        snapshot.reconstructionPartIds.add("D");
        snapshot.discoveryIds.add("C29");
        snapshot.story.playedSceneIds.add("ST5-FINAL-READY");
        snapshot.finalObjective.state = "AVAILABLE";
        RunSnapshot.FacilityInstanceState purifier = new RunSnapshot.FacilityInstanceState();
        purifier.instanceId = "portable:proto-test:device-1";
        purifier.portableInstanceId = "device-1";
        purifier.facilityType = "FAC-P05";
        snapshot.facilities.put(purifier.instanceId, purifier);
        repository.save(snapshot);

        RunSnapshot restored = repository.load().orElseThrow();

        assertEquals("ACTIVE", restored.seasonDay.state);
        assertEquals("EV50-D50-LAST-SIGNAL", restored.encounters.get("encounter-50").eventId);
        assertTrue(restored.defeatedBossIds.contains("BOSS-D40"));
        assertTrue(restored.reconstructionPartIds.contains("D"));
        assertTrue(restored.discoveryIds.contains("C29"));
        assertTrue(restored.story.playedSceneIds.contains("ST5-FINAL-READY"));
        assertEquals("AVAILABLE", restored.finalObjective.state);
        assertEquals("device-1", restored.facilities.get("portable:proto-test:device-1").portableInstanceId);
    }

    @Test
    void apStimCheckpointSurvivesForcedReloadWithOnlyRemainingPulses(@TempDir Path temporary) throws Exception {
        RunRepository repository = new RunRepository(temporary);
        RunSnapshot snapshot = snapshot();
        RunSnapshot.PlayerState player = new RunSnapshot.PlayerState();
        player.uuid = "player-1";
        player.maxAp = 100;
        ApStimPulsePolicy.State pulse = ApStimPulsePolicy.start(20.0, player.maxAp);
        for (int tick = 0; tick < 40; tick++) {
            pulse = ApStimPulsePolicy.tick(pulse.ap(), pulse.maxAp(), pulse.pulsesRemaining(),
                    pulse.ticksUntilNextPulse());
        }
        player.ap = pulse.ap();
        player.apStimPulsesRemaining = pulse.pulsesRemaining();
        player.apStimTicksUntilNextPulse = pulse.ticksUntilNextPulse();
        player.quickItems.put("WSI-CONS-AP_STIM", 0);
        snapshot.players.put(player.uuid, player);
        repository.save(snapshot);

        RunSnapshot.PlayerState restored = repository.load().orElseThrow().players.get(player.uuid);
        assertEquals(36.0, restored.ap);
        assertEquals(3, restored.apStimPulsesRemaining);
        assertEquals(20, restored.apStimTicksUntilNextPulse);
        assertEquals(0, restored.quickItems.get("WSI-CONS-AP_STIM"));

        ApStimPulsePolicy.State resumed = new ApStimPulsePolicy.State(restored.ap, restored.maxAp,
                restored.apStimPulsesRemaining, restored.apStimTicksUntilNextPulse, false);
        for (int tick = 0; tick < 60; tick++) {
            resumed = ApStimPulsePolicy.tick(resumed.ap(), resumed.maxAp(), resumed.pulsesRemaining(),
                    resumed.ticksUntilNextPulse());
        }
        assertEquals(45.0, resumed.ap());
        assertEquals(0, resumed.pulsesRemaining());
    }

    private static RunSnapshot snapshot() {
        RunSnapshot snapshot = new RunSnapshot();
        snapshot.runId = "proto-test";
        snapshot.contentRevision = "ws-prototype-r1";
        snapshot.runType = "PROTOTYPE";
        snapshot.state = "RUNNING";
        return snapshot;
    }

    private static java.util.List<Path> listCurrentTemporaryFiles(Path runs) {
        try (java.util.stream.Stream<Path> paths = Files.list(runs)) {
            return paths.filter(path -> {
                String name = path.getFileName().toString();
                return name.equals("current.json.tmp") || name.startsWith("current.json.") && name.endsWith(".tmp");
            }).toList();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
