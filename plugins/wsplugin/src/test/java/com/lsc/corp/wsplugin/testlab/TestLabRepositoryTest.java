package com.lsc.corp.wsplugin.testlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestLabRepositoryTest {
    @Test
    void snapshotsAreBoundedAndUndoIsLastInFirstOut(@TempDir Path temporary) throws Exception {
        TestLabRepository repository = new TestLabRepository(temporary);
        RunSnapshot run = testRun();
        run.day = 1;
        repository.snapshot(run, "owner", "one", 2);
        run.day = 3;
        repository.snapshot(run, "owner", "two", 2);
        run.day = 6;
        repository.snapshot(run, "owner", "three", 2);

        assertEquals(2, repository.listSnapshots(run.runId).size());
        TestLabSnapshot latest = repository.popLatestSnapshot(run.runId).orElseThrow();
        assertEquals(6, latest.run.day);
        assertEquals("three", latest.reason);
        assertEquals(1, repository.listSnapshots(run.runId).size());
    }

    @Test
    void persistsNamedPresetsAndAudit(@TempDir Path temporary) throws Exception {
        TestLabRepository repository = new TestLabRepository(temporary);
        TestPreset preset = new TestPreset();
        preset.id = "Glass_Cannon";
        preset.damageDealtMultiplier = 4.0;
        preset.damageTakenMultiplier = 3.0;
        repository.savePreset(preset);

        assertTrue(Files.exists(repository.root().resolve("presets/GLASS-CANNON.json")));
        TestPreset restored = repository.loadPreset("glass-cannon").orElseThrow();
        assertEquals("GLASS-CANNON", restored.id);
        assertEquals(4.0, restored.damageDealtMultiplier);
        repository.audit("test-run", "owner", "preset.apply", "DEFAULT", restored.id);
        assertTrue(Files.exists(repository.root().resolve("audit.jsonl")));
    }

    private static RunSnapshot testRun() {
        RunSnapshot run = new RunSnapshot();
        run.runId = "test-repository";
        run.runType = "TEST";
        run.contentRevision = "ws-prototype-r1";
        run.state = "RUNNING";
        run.test = new RunSnapshot.TestState();
        run.test.ownerUuid = "owner";
        return run;
    }
}
