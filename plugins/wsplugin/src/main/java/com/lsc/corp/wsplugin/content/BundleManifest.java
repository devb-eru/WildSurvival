package com.lsc.corp.wsplugin.content;

import java.util.List;

public record BundleManifest(
        int schemaVersion,
        String contentRevision,
        String activationPolicy,
        String runType,
        boolean promotionForbidden,
        List<FileEntry> files
) {
    public record FileEntry(String path, String domain, String schema, String sha256) {}
}
