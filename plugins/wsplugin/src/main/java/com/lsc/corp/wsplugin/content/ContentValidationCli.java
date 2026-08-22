package com.lsc.corp.wsplugin.content;

import java.nio.file.Path;

public final class ContentValidationCli {
    private ContentValidationCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: ContentValidationCli <bundle-directory>");
        }
        ContentBundleValidator.ValidationResult result = new ContentBundleValidator().validateDirectory(Path.of(args[0]));
        System.out.printf("%s validated: %d files, %d resources, %d recipes%n",
                result.manifest().contentRevision(), result.verifiedFileCount(),
                result.content().resources().size(), result.content().recipes().size());
    }
}
