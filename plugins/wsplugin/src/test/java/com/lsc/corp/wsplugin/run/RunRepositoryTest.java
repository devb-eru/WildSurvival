package com.lsc.corp.wsplugin.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
    }

    @Test
    void refusesLiveRevisionInPrototypeRepository(@TempDir Path temporary) throws Exception {
        RunRepository repository = new RunRepository(temporary);
        RunSnapshot snapshot = snapshot();
        snapshot.contentRevision = "ws-content-r2";

        assertThrows(IOException.class, () -> repository.save(snapshot));
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

    private static RunSnapshot snapshot() {
        RunSnapshot snapshot = new RunSnapshot();
        snapshot.runId = "proto-test";
        snapshot.contentRevision = "ws-prototype-r1";
        snapshot.runType = "PROTOTYPE";
        snapshot.state = "RUNNING";
        return snapshot;
    }
}
