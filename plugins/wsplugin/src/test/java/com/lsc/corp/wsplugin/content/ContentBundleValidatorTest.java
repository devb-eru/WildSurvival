package com.lsc.corp.wsplugin.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentBundleValidatorTest {
    private static final Path BUNDLE = Path.of("src", "main", "resources", "content", "ws-prototype-r1");

    @Test
    void validatesLockedPrototypeBundle() throws Exception {
        var result = new ContentBundleValidator().validateDirectory(BUNDLE);

        assertEquals("ws-prototype-r1", result.manifest().contentRevision());
        assertTrue(result.manifest().promotionForbidden());
        assertEquals(6, result.content().resources().size());
        assertEquals(10, result.content().items().size());
        assertEquals(7, result.content().weapons().size());
        assertEquals(13, result.content().skills().size());
        assertEquals(15, result.content().recipes().size());
    }

    @Test
    void rejectsTamperedDomainFile(@TempDir Path temporary) throws Exception {
        copyTree(BUNDLE, temporary);
        Path data = temporary.resolve("prototype/prototype-data.json");
        Files.writeString(data, Files.readString(data, StandardCharsets.UTF_8).replace("105000.0", "1.0"), StandardCharsets.UTF_8);

        ContentValidationException exception = assertThrows(ContentValidationException.class,
                () -> new ContentBundleValidator().validateDirectory(temporary));
        assertTrue(exception.getMessage().contains("SHA-256 mismatch") || exception.getCause() != null);
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = destination.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
