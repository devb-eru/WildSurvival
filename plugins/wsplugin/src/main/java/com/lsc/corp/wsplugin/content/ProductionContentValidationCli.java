package com.lsc.corp.wsplugin.content;

import java.nio.file.Path;

public final class ProductionContentValidationCli {
    private ProductionContentValidationCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ProductionContentValidationCli <bundle-root>");
        ProductionBundleValidator.ValidationResult result = new ProductionBundleValidator().validateDirectory(Path.of(args[0]));
        System.out.printf("%s L0 catalog validated: files=%d codex=%d recipes=%d entities=%d%n",
                ProductionBundleValidator.REVISION, result.verifiedFileCount(), result.catalog().codexEntries().size(),
                result.catalog().recipes().size(), result.counts().get("enemies") + result.counts().get("bosses") + result.counts().get("support"));
    }
}
