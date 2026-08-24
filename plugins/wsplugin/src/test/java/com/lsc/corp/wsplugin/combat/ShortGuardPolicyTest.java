package com.lsc.corp.wsplugin.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShortGuardPolicyTest {
    @Test
    void normalFIsOneStartupFourParryAndTwelveGuardTicks() {
        ShortGuardPolicy.Start start = ShortGuardPolicy.start(100L, 40.0, false, 0L, 7L);
        assertTrue(start.started());
        assertEquals(25.0, start.apAfter());
        assertEquals(ShortGuardPolicy.Phase.PARRY_STARTUP, start.state().phase());

        ShortGuardPolicy.State parry = ShortGuardPolicy.advance(start.state(), 101L);
        assertEquals(ShortGuardPolicy.Phase.PARRY_ACTIVE, parry.phase());
        assertEquals(105L, parry.phaseEndsAtTick());

        ShortGuardPolicy.State guard = ShortGuardPolicy.advance(parry, 105L);
        assertEquals(ShortGuardPolicy.Phase.SHORT_GUARD, guard.phase());
        assertEquals(117L, guard.phaseEndsAtTick());
        assertNull(ShortGuardPolicy.advance(guard, 117L));
    }

    @Test
    void lowApUsesThreeTickGuardStartupAndZeroApDenies() {
        ShortGuardPolicy.Start low = ShortGuardPolicy.start(10L, 14.0, false, 0L, 1L);
        assertEquals("GUARD_ONLY", low.result());
        assertEquals(ShortGuardPolicy.Phase.GUARD_STARTUP, low.state().phase());
        assertEquals(ShortGuardPolicy.Phase.SHORT_GUARD,
                ShortGuardPolicy.advance(low.state(), 13L).phase());
        assertFalse(ShortGuardPolicy.start(10L, 0.0, false, 0L, 2L).started());
    }

    @Test
    void attackClassificationMatchesGuardContract() {
        assertTrue(ShortGuardPolicy.parryable(List.of("PARRY"), List.of("MELEE")));
        assertFalse(ShortGuardPolicy.guardable(List.of("DODGE"), List.of("AREA")));
        assertEquals(0.30, ShortGuardPolicy.guardedDamageMultiplier(List.of("MELEE")));
        assertEquals(0.20, ShortGuardPolicy.guardedDamageMultiplier(List.of("PROJECTILE")));
        assertEquals(0.60, ShortGuardPolicy.guardedDamageMultiplier(List.of("MAGIC")));
        assertEquals(6.0, ShortGuardPolicy.normalizedGuardImpact(0.0, List.of("LIGHT")));
    }
}
