package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlayerLifePolicyTest {
    @Test
    void fatalDamageTransitionsAnActivePlayerToDowned() {
        assertEquals(PlayerLifePolicy.DamageDisposition.ENTER_DOWNED,
                PlayerLifePolicy.evaluate("ACTIVE", 10.0, 10.0));
        assertEquals(PlayerLifePolicy.DamageDisposition.ENTER_DOWNED,
                PlayerLifePolicy.evaluate("ACTIVE", 10.0, 100.0));
        assertEquals(PlayerLifePolicy.DamageDisposition.ENTER_DOWNED,
                PlayerLifePolicy.evaluate("ACTIVE", 10.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void downedAndDeadPlayersCannotTakeAdditionalDamage() {
        assertEquals(PlayerLifePolicy.DamageDisposition.BLOCK,
                PlayerLifePolicy.evaluate("DOWNED", 1.0, 0.25));
        assertEquals(PlayerLifePolicy.DamageDisposition.BLOCK,
                PlayerLifePolicy.evaluate("DEAD", 1.0, 20.0));
        assertEquals(PlayerLifePolicy.DamageDisposition.BLOCK,
                PlayerLifePolicy.evaluate("DOWNED_GRACE", 1.0, 20.0));
        assertEquals(PlayerLifePolicy.DamageDisposition.BLOCK,
                PlayerLifePolicy.evaluate(null, 1.0, 20.0));
    }

    @Test
    void nonFatalDamageRemainsAllowed() {
        assertEquals(PlayerLifePolicy.DamageDisposition.ALLOW,
                PlayerLifePolicy.evaluate("ACTIVE", 10.0, 9.0));
    }
}
