package com.lsc.corp.wsplugin.content;

import java.io.IOException;
import java.io.InputStream;

public final class ContentBundleService {
    private static final String ROOT = "content/ws-prototype-r1/";
    private final ClassLoader classLoader;
    private ContentBundleValidator.ValidationResult validation;

    public ContentBundleService(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ContentBundleValidator.ValidationResult loadAndValidate() throws ContentValidationException {
        ContentBundleValidator validator = new ContentBundleValidator();
        validation = validator.validate(path -> {
            try (InputStream input = classLoader.getResourceAsStream(ROOT + path)) {
                if (input == null) {
                    throw new IOException("Missing bundled resource " + path);
                }
                return input.readAllBytes();
            }
        });
        return validation;
    }

    public PrototypeContent content() {
        if (validation == null) {
            throw new IllegalStateException("Content was not validated");
        }
        return validation.content();
    }

    public ContentBundleValidator.ValidationResult validation() {
        if (validation == null) {
            throw new IllegalStateException("Content was not validated");
        }
        return validation;
    }
}
