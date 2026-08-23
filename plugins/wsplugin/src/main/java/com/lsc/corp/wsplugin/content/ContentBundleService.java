package com.lsc.corp.wsplugin.content;

import java.io.IOException;
import java.io.InputStream;

public final class ContentBundleService {
    private static final String PROTOTYPE_ROOT = "content/ws-prototype-r1/";
    private static final String PRODUCTION_ROOT = "content/ws-content-r2/";
    private final ClassLoader classLoader;
    private ContentBundleValidator.ValidationResult validation;
    private ProductionBundleValidator.ValidationResult productionValidation;

    public ContentBundleService(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public ContentBundleValidator.ValidationResult loadAndValidate() throws ContentValidationException {
        ContentBundleValidator validator = new ContentBundleValidator();
        validation = validator.validate(path -> read(PROTOTYPE_ROOT, path));
        ProductionBundleValidator productionValidator = new ProductionBundleValidator();
        productionValidation = productionValidator.validate(path -> read(PRODUCTION_ROOT, path));
        return validation;
    }

    private byte[] read(String root, String path) throws IOException {
        try (InputStream input = classLoader.getResourceAsStream(root + path)) {
            if (input == null) throw new IOException("Missing bundled resource " + root + path);
            return input.readAllBytes();
        }
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

    public ProductionBundleValidator.ValidationResult productionValidation() {
        if (productionValidation == null) throw new IllegalStateException("Production content was not validated");
        return productionValidation;
    }

    public ProductionContentCatalog productionCatalog() {
        return productionValidation().catalog();
    }
}
