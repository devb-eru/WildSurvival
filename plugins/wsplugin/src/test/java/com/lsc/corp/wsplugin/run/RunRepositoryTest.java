package com.lsc.corp.wsplugin.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
        assertEquals(2, restored.schemaVersion);
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

        assertEquals(2, restored.schemaVersion);
        assertEquals(20, restored.seasonDay.day);
        assertEquals("DAY-20", restored.seasonDay.dayId);
        assertEquals("LOCKED", restored.finalObjective.state);
        assertTrue(restored.encounters.isEmpty());
        assertTrue(restored.researchNodes.isEmpty());
        assertTrue(restored.story.playedSceneIds.isEmpty());
        assertTrue(restored.defeatedBossIds.isEmpty());
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

    private static RunSnapshot snapshot() {
        RunSnapshot snapshot = new RunSnapshot();
        snapshot.runId = "proto-test";
        snapshot.contentRevision = "ws-prototype-r1";
        snapshot.runType = "PROTOTYPE";
        snapshot.state = "RUNNING";
        return snapshot;
    }
}
