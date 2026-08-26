package com.lsc.corp.wsplugin.testlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestLabRepositoryTest {
    @Test
    void latestSnapshotIsRetainedUntilRestoreCommits(@TempDir Path temporary) throws Exception {
        TestLabRepository repository = new TestLabRepository(temporary);
        RunSnapshot run = testRun();
        run.day = 1;
        repository.snapshot(run, "owner", "one", 2);
        run.day = 3;
        repository.snapshot(run, "owner", "two", 2);
        run.day = 6;
        repository.snapshot(run, "owner", "three", 2);

        assertEquals(2, repository.listSnapshots(run.runId).size());
        TestLabSnapshot latest = repository.latestSnapshot(run.runId).orElseThrow();
        assertEquals(6, latest.run.day);
        assertEquals("three", latest.reason);
        assertEquals(2, repository.listSnapshots(run.runId).size());

        repository.deleteSnapshot(run.runId, latest.snapshotId);

        assertEquals(1, repository.listSnapshots(run.runId).size());
    }

    @Test
    void refusesToDeleteSnapshotThroughAnotherRunIdentity(@TempDir Path temporary) throws Exception {
        TestLabRepository repository = new TestLabRepository(temporary);
        RunSnapshot run = testRun();
        TestLabSnapshot snapshot = repository.snapshot(run, "owner", "safe", 2);

        assertThrows(IllegalArgumentException.class,
                () -> repository.deleteSnapshot("another-run", snapshot.snapshotId));
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
        repository.savePreset(preset);

        assertTrue(Files.exists(repository.root().resolve("presets/GLASS-CANNON.json")));
        TestPreset restored = repository.loadPreset("glass-cannon").orElseThrow();
        assertEquals("GLASS-CANNON", restored.id);
        assertEquals(4.0, restored.damageDealtMultiplier);
        try (var paths = Files.list(repository.root().resolve("presets"))) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
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
