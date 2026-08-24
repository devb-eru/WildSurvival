package com.lsc.corp.wsplugin.combat;

import java.util.Map;
import java.util.Set;

public final class AmmoLedgerPolicy {
    public static final String GENERAL_ARROW = "WSI-AMMO-ARROW_BUNDLE";
    public static final Set<String> REGISTERED_AMMO = Set.of(
            GENERAL_ARROW,
            "WSI-AMMO-PIERCING_BOLT_BUNDLE",
            "WSI-AMMO-PURIFY_ARROW_BUNDLE",
            "WSI-AMMO-RESONANCE_BOLT_BUNDLE",
            "WSI-AMMO-STABILIZER_DART_BUNDLE");

    private AmmoLedgerPolicy() {
    }

    public static boolean registered(String itemId) {
        return itemId != null && REGISTERED_AMMO.contains(itemId);
    }

    public static boolean executable(String itemId) {
        return GENERAL_ARROW.equals(itemId);
    }

    public static int balance(Map<String, Integer> ledger, String itemId) {
        if (ledger == null || itemId == null) return 0;
        return Math.max(0, ledger.getOrDefault(itemId, 0));
    }

    public static int deposit(Map<String, Integer> ledger, String itemId, int amount) {
        requireLedger(ledger);
        if (!registered(itemId) || amount < 1) throw new IllegalArgumentException("Invalid ammo deposit");
        int updated = Math.addExact(balance(ledger, itemId), amount);
        ledger.put(itemId, updated);
        return updated;
    }

    public static boolean consume(Map<String, Integer> ledger, String itemId, int amount) {
        requireLedger(ledger);
        if (!registered(itemId) || amount < 1) throw new IllegalArgumentException("Invalid ammo consumption");
        int current = balance(ledger, itemId);
        if (current < amount) return false;
        int updated = current - amount;
        if (updated == 0) ledger.remove(itemId); else ledger.put(itemId, updated);
        return true;
    }

    private static void requireLedger(Map<String, Integer> ledger) {
        if (ledger == null) throw new IllegalArgumentException("Ammo ledger is required");
    }
}
