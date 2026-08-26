package com.lsc.corp.wsplugin.run;

import java.util.Locale;

/** One-shot, memory-only checkpoint gate used by isolated Test Lab crash recovery evidence. */
public final class TestTransactionPauseGate {
    public enum Phase { RESERVED, PROCESSING }

    private Phase armedPhase;
    private String pausedTransactionId;
    private Phase pausedPhase;

    public void arm(String rawPhase) {
        Phase requested;
        try {
            requested = Phase.valueOf(rawPhase.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("Transaction pause phase must be RESERVED or PROCESSING");
        }
        armedPhase = requested;
        pausedTransactionId = null;
        pausedPhase = null;
    }

    public boolean checkpoint(String transactionId, Phase phase) {
        if (armedPhase != phase) return false;
        armedPhase = null;
        pausedTransactionId = transactionId;
        pausedPhase = phase;
        return true;
    }

    public boolean blocks(String transactionId) {
        return transactionId != null && transactionId.equals(pausedTransactionId);
    }

    public void clear() {
        armedPhase = null;
        pausedTransactionId = null;
        pausedPhase = null;
    }

    public Status status() {
        return new Status(armedPhase == null ? null : armedPhase.name(), pausedTransactionId,
                pausedPhase == null ? null : pausedPhase.name());
    }

    public record Status(String armedPhase, String pausedTransactionId, String pausedPhase) { }
}
