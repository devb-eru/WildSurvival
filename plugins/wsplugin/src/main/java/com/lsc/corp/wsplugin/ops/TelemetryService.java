package com.lsc.corp.wsplugin.ops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TelemetryService implements AutoCloseable {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Path telemetryDirectory;
    private final Path eventLog;
    private final Path auditLog;
    private final ArrayDeque<Long> tickNanos = new ArrayDeque<>();

    public TelemetryService(Path dataDirectory) throws IOException {
        telemetryDirectory = dataDirectory.resolve("telemetry");
        Files.createDirectories(telemetryDirectory);
        eventLog = telemetryDirectory.resolve("events.jsonl");
        auditLog = telemetryDirectory.resolve("audit.jsonl");
    }

    public synchronized void event(String runId, String type, String payloadJson) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("runId", runId);
        event.put("type", type);
        event.put("payload", com.google.gson.JsonParser.parseString(payloadJson));
        append(eventLog, gson.toJson(event));
    }

    public synchronized void audit(String runId, String actor, String action, String reason) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("runId", runId);
        event.put("actor", actor);
        event.put("action", action);
        event.put("reason", reason);
        append(auditLog, gson.toJson(event));
    }

    public synchronized void tick(long nanos) {
        tickNanos.addLast(nanos);
        while (tickNanos.size() > 1200) {
            tickNanos.removeFirst();
        }
    }

    public synchronized double p95PluginTickMs() {
        if (tickNanos.isEmpty()) {
            return 0.0;
        }
        List<Long> sorted = new ArrayList<>(tickNanos);
        sorted.sort(Long::compareTo);
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(Math.max(0, index)) / 1_000_000.0;
    }

    public synchronized Path sessionReport(RunSnapshot snapshot) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runId", snapshot.runId);
        report.put("contentRevision", snapshot.contentRevision);
        report.put("runType", snapshot.runType);
        report.put("state", snapshot.state);
        report.put("endReason", snapshot.endReason);
        report.put("day", snapshot.day);
        report.put("memberCount", snapshot.registeredPlayers.size());
        report.put("survivorCount", snapshot.players.values().stream().filter(value -> "ACTIVE".equals(value.lifeState)).count());
        report.put("resources", snapshot.resources);
        report.put("partyAugmentId", snapshot.partyAugmentId);
        report.put("bossRewardCommitted", snapshot.boss != null && snapshot.boss.rewardCommitted);
        report.put("ledgerCommitCount", snapshot.committedKeys.size());
        report.put("pluginTickP95Ms", p95PluginTickMs());
        report.put("generatedAt", Instant.now().toString());
        Path file = telemetryDirectory.resolve("session-" + snapshot.runId + ".json");
        try {
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write session report", exception);
        }
        return file;
    }

    private static void append(Path file, String line) {
        try {
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot append telemetry " + file, exception);
        }
    }

    @Override
    public void close() {
        // Files are opened per append, so there is no buffered handle to close.
    }
}
