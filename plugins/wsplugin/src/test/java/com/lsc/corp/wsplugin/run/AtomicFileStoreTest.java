package com.lsc.corp.wsplugin.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileStoreTest {
    @Test
    void retriesTransientAccessDeniedAndEventuallyReplaces(@TempDir Path temporary) throws Exception {
        Path source = Files.writeString(temporary.resolve("source.tmp"), "new", StandardCharsets.UTF_8);
        Path destination = Files.writeString(temporary.resolve("current.json"), "old", StandardCharsets.UTF_8);
        AtomicInteger attempts = new AtomicInteger();
        List<Long> delays = new ArrayList<>();

        AtomicFileStore.replace(source, destination, (from, to, atomic) -> {
            if (attempts.incrementAndGet() < 3) {
                throw new AccessDeniedException(to.toString());
            }
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }, delays::add, 5L, 10L, 20L, 40L);

        assertEquals(3, attempts.get());
        assertEquals(List.of(5L, 10L), delays);
        assertEquals("new", Files.readString(destination, StandardCharsets.UTF_8));
    }

    @Test
    void fallsBackWhenAtomicMoveIsUnsupported(@TempDir Path temporary) throws Exception {
        Path source = Files.writeString(temporary.resolve("source.tmp"), "new", StandardCharsets.UTF_8);
        Path destination = temporary.resolve("current.json");
        List<Boolean> modes = new ArrayList<>();

        AtomicFileStore.replace(source, destination, (from, to, atomic) -> {
            modes.add(atomic);
            if (atomic) {
                throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "test filesystem");
            }
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }, ignored -> { }, 5L, 10L, 20L, 40L);

        assertEquals(List.of(true, false), modes);
        assertEquals("new", Files.readString(destination, StandardCharsets.UTF_8));
    }

    @Test
    void givesUpAfterBoundedAccessDeniedRetriesWithoutTouchingDestination(@TempDir Path temporary) throws Exception {
        Path source = Files.writeString(temporary.resolve("source.tmp"), "new", StandardCharsets.UTF_8);
        Path destination = Files.writeString(temporary.resolve("current.json"), "old", StandardCharsets.UTF_8);
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(AccessDeniedException.class, () -> AtomicFileStore.replace(source, destination,
                (from, to, atomic) -> {
                    attempts.incrementAndGet();
                    throw new AccessDeniedException(to.toString());
                }, ignored -> { }, 5L, 10L, 20L, 40L));

        assertEquals(5, attempts.get());
        assertEquals("old", Files.readString(destination, StandardCharsets.UTF_8));
        assertTrue(Files.exists(source));
    }
}
