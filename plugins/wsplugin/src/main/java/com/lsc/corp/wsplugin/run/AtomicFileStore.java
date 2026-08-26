package com.lsc.corp.wsplugin.run;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class AtomicFileStore {
    private static final long[] DEFAULT_RETRY_DELAYS_MILLIS = {5L, 10L, 20L, 40L};

    private AtomicFileStore() { }

    public static void replace(Path source, Path destination) throws IOException {
        replace(source, destination, AtomicFileStore::move, Thread::sleep, DEFAULT_RETRY_DELAYS_MILLIS);
    }

    static void replace(Path source, Path destination, MoveOperation moveOperation, Sleeper sleeper,
                        long... retryDelaysMillis) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(moveOperation, "moveOperation");
        Objects.requireNonNull(sleeper, "sleeper");
        Objects.requireNonNull(retryDelaysMillis, "retryDelaysMillis");

        for (int attempt = 0; ; attempt++) {
            try {
                moveOnce(source, destination, moveOperation);
                return;
            } catch (AccessDeniedException denied) {
                if (attempt >= retryDelaysMillis.length) {
                    throw denied;
                }
                long delay = retryDelaysMillis[attempt];
                if (delay < 0L) {
                    throw new IllegalArgumentException("Retry delay must not be negative");
                }
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    IOException failure = new IOException("Interrupted while retrying snapshot replacement", interrupted);
                    failure.addSuppressed(denied);
                    throw failure;
                }
            }
        }
    }

    private static void moveOnce(Path source, Path destination, MoveOperation moveOperation) throws IOException {
        try {
            moveOperation.move(source, destination, true);
        } catch (AtomicMoveNotSupportedException ignored) {
            moveOperation.move(source, destination, false);
        }
    }

    private static void move(Path source, Path destination, boolean atomic) throws IOException {
        if (atomic) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        }
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    @FunctionalInterface
    interface Replacer {
        void replace(Path source, Path destination) throws IOException;
    }

    @FunctionalInterface
    interface MoveOperation {
        void move(Path source, Path destination, boolean atomic) throws IOException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
