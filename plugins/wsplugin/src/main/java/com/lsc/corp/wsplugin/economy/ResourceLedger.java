package com.lsc.corp.wsplugin.economy;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ResourceLedger {
    private ResourceLedger() {}

    public enum Scope { PERSONAL, SHARED }

    public enum ReserveResult {
        RESERVED,
        ALREADY_RESERVED,
        ALREADY_COMMITTED,
        INSUFFICIENT,
        INVALID
    }

    public static ReserveResult reserve(RunSnapshot snapshot, String transactionId, String costId,
                                        String targetId, Scope scope, String ownerUuid,
                                        Map<String, Integer> costs, long nowEpochMs) {
        if (snapshot == null || transactionId == null || transactionId.isBlank()
                || costId == null || costId.isBlank() || costs == null) {
            return ReserveResult.INVALID;
        }
        if (snapshot.resourceTransactions == null) snapshot.resourceTransactions = new LinkedHashMap<>();
        if (snapshot.committedKeys.contains(transactionId)) return ReserveResult.ALREADY_COMMITTED;
        RunSnapshot.ResourceTransactionState existing = snapshot.resourceTransactions.get(transactionId);
        int reservationAttempt = 1;
        List<String> cancellationReasons = new ArrayList<>();
        if (existing != null) {
            if ("COMMITTED".equals(existing.state)) return ReserveResult.ALREADY_COMMITTED;
            if (!"CANCELLED".equals(existing.state)) return ReserveResult.ALREADY_RESERVED;
            reservationAttempt = Math.max(1, existing.reservationAttempt) + 1;
            if (existing.cancellationReasons != null) {
                cancellationReasons.addAll(existing.cancellationReasons);
            }
            if (cancellationReasons.isEmpty() && existing.failureReason != null
                    && !existing.failureReason.isBlank()) {
                cancellationReasons.add(existing.failureReason);
            }
        }
        Map<String, Integer> normalized = normalizedCosts(costs);
        if (normalized == null) return ReserveResult.INVALID;
        Map<String, Integer> balance = balance(snapshot, scope, ownerUuid);
        for (var entry : normalized.entrySet()) {
            if (balance.getOrDefault(entry.getKey(), 0) < entry.getValue()) return ReserveResult.INSUFFICIENT;
        }
        RunSnapshot.ResourceTransactionState transaction = new RunSnapshot.ResourceTransactionState();
        transaction.transactionId = transactionId;
        transaction.costId = costId;
        transaction.targetId = targetId;
        transaction.ledgerScope = scope.name();
        transaction.ownerUuid = ownerUuid;
        transaction.state = "VALIDATED";
        transaction.reservationAttempt = reservationAttempt;
        transaction.cancellationReasons.addAll(cancellationReasons);
        transaction.validatedAtEpochMs = nowEpochMs;
        transaction.reservedResources.putAll(normalized);
        snapshot.resourceTransactions.put(transactionId, transaction);
        for (var entry : normalized.entrySet()) {
            balance.put(entry.getKey(), balance.getOrDefault(entry.getKey(), 0) - entry.getValue());
        }
        transaction.state = "RESERVED";
        transaction.reservedAtEpochMs = nowEpochMs;
        return ReserveResult.RESERVED;
    }

    public static boolean beginProcessing(RunSnapshot snapshot, String transactionId, long nowEpochMs) {
        RunSnapshot.ResourceTransactionState transaction = transaction(snapshot, transactionId);
        if ("PROCESSING".equals(transaction.state) || "COMMITTED".equals(transaction.state)) return false;
        if (!"RESERVED".equals(transaction.state)) {
            throw new IllegalStateException("Transaction is not reserved: " + transactionId + " state=" + transaction.state);
        }
        transaction.state = "PROCESSING";
        transaction.processingStartedAtEpochMs = nowEpochMs;
        return true;
    }

    public static boolean commit(RunSnapshot snapshot, String transactionId, long nowEpochMs,
                                 Consumer<RunSnapshot> mutation) {
        RunSnapshot.ResourceTransactionState transaction = transaction(snapshot, transactionId);
        if ("COMMITTED".equals(transaction.state) || snapshot.committedKeys.contains(transactionId)) return false;
        if (!"PROCESSING".equals(transaction.state)) {
            throw new IllegalStateException("Transaction is not processing: " + transactionId + " state=" + transaction.state);
        }
        mutation.accept(snapshot);
        transaction.state = "COMMITTED";
        transaction.committedAtEpochMs = nowEpochMs;
        snapshot.committedKeys.add(transactionId);
        return true;
    }

    public static boolean cancelReservation(RunSnapshot snapshot, String transactionId, String reason) {
        RunSnapshot.ResourceTransactionState transaction = transaction(snapshot, transactionId);
        if ("CANCELLED".equals(transaction.state)) return false;
        if (!"RESERVED".equals(transaction.state) && !"PROCESSING".equals(transaction.state)) {
            throw new IllegalStateException("Only an uncommitted reservation can be fully refunded: "
                    + transactionId + " state=" + transaction.state);
        }
        Scope scope = Scope.valueOf(transaction.ledgerScope);
        Map<String, Integer> balance = balance(snapshot, scope, transaction.ownerUuid);
        transaction.reservedResources.forEach((id, amount) -> balance.merge(id, amount, Integer::sum));
        transaction.state = "CANCELLED";
        transaction.failureReason = reason == null || reason.isBlank() ? "UNSPECIFIED" : reason;
        if (transaction.cancellationReasons == null) transaction.cancellationReasons = new ArrayList<>();
        transaction.cancellationReasons.add(transaction.failureReason);
        return true;
    }

    public static boolean reserveAndMutate(RunSnapshot snapshot, String idempotencyKey,
                                           Map<String, Integer> costs, Consumer<RunSnapshot> mutation) {
        ReserveResult reserved = reserve(snapshot, idempotencyKey, idempotencyKey, idempotencyKey,
                Scope.SHARED, null, costs, 0L);
        if (reserved != ReserveResult.RESERVED) return false;
        beginProcessing(snapshot, idempotencyKey, 0L);
        try {
            return commit(snapshot, idempotencyKey, 0L, mutation);
        } catch (RuntimeException exception) {
            RunSnapshot.ResourceTransactionState transaction = snapshot.resourceTransactions.remove(idempotencyKey);
            if (transaction != null) transaction.reservedResources.forEach((id, amount) ->
                    snapshot.resources.merge(id, amount, Integer::sum));
            snapshot.committedKeys.remove(idempotencyKey);
            throw exception;
        }
    }

    private static Map<String, Integer> normalizedCosts(Map<String, Integer> costs) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (var entry : costs.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
                    || entry.getValue() < 0) return null;
            if (entry.getValue() > 0) normalized.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        return normalized;
    }

    private static Map<String, Integer> balance(RunSnapshot snapshot, Scope scope, String ownerUuid) {
        if (scope == Scope.SHARED) {
            if (snapshot.resources == null) snapshot.resources = new LinkedHashMap<>();
            return snapshot.resources;
        }
        if (ownerUuid == null || ownerUuid.isBlank()) {
            throw new IllegalArgumentException("A personal transaction requires ownerUuid");
        }
        RunSnapshot.PlayerState player = snapshot.players.get(ownerUuid);
        if (player == null) throw new IllegalArgumentException("Unknown transaction owner " + ownerUuid);
        if (player.personalResources == null) player.personalResources = new LinkedHashMap<>();
        return player.personalResources;
    }

    private static RunSnapshot.ResourceTransactionState transaction(RunSnapshot snapshot, String transactionId) {
        RunSnapshot.ResourceTransactionState transaction = snapshot.resourceTransactions.get(transactionId);
        if (transaction == null) throw new IllegalArgumentException("Unknown resource transaction " + transactionId);
        return transaction;
    }
}
