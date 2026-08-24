package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AmmoLedgerPolicyTest {
    @Test
    void depositsAndConsumesExactRoundCounts() {
        Map<String, Integer> ledger = new LinkedHashMap<>();
        assertEquals(16, AmmoLedgerPolicy.deposit(ledger, AmmoLedgerPolicy.GENERAL_ARROW, 16));
        assertTrue(AmmoLedgerPolicy.consume(ledger, AmmoLedgerPolicy.GENERAL_ARROW, 2));
        assertEquals(14, AmmoLedgerPolicy.balance(ledger, AmmoLedgerPolicy.GENERAL_ARROW));
        assertFalse(AmmoLedgerPolicy.consume(ledger, AmmoLedgerPolicy.GENERAL_ARROW, 15));
        assertEquals(14, AmmoLedgerPolicy.balance(ledger, AmmoLedgerPolicy.GENERAL_ARROW));
        assertTrue(AmmoLedgerPolicy.consume(ledger, AmmoLedgerPolicy.GENERAL_ARROW, 14));
        assertFalse(ledger.containsKey(AmmoLedgerPolicy.GENERAL_ARROW));
    }

    @Test
    void specialAmmoRemainsRegisteredButNotExecutableUntilItsContractIsLocked() {
        assertTrue(AmmoLedgerPolicy.registered("WSI-AMMO-PIERCING_BOLT_BUNDLE"));
        assertFalse(AmmoLedgerPolicy.executable("WSI-AMMO-PIERCING_BOLT_BUNDLE"));
        assertTrue(AmmoLedgerPolicy.executable(AmmoLedgerPolicy.GENERAL_ARROW));
        assertThrows(IllegalArgumentException.class,
                () -> AmmoLedgerPolicy.deposit(new LinkedHashMap<>(), "UNKNOWN", 1));
    }
}
