package com.lsc.corp.wsplugin.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArenaManifestPolicyTest {
    @Test
    void selectsDeterministicDayTenTriangle() {
        var result = ArenaManifestPolicy.select("WSI-CALL-D10", List.of(
                stake("c", 10, 17.321), stake("a", -10, 17.321), stake("b", 0, 0)));

        assertTrue(result.accepted());
        assertEquals(List.of("a", "b", "c"), result.candidate().stakeInstanceIds());
        assertEquals("BOSS-D10", result.candidate().bossId());
        assertEquals(32, result.candidate().arenaRadius());
    }

    @Test
    void rejectsDayTenSpacingOutsideContract() {
        var tooClose = ArenaManifestPolicy.select("WSI-CALL-D10", List.of(
                stake("a", 0, 0), stake("b", 10, 0), stake("c", 0, 10)));
        var tooFar = ArenaManifestPolicy.select("WSI-CALL-D10", List.of(
                stake("a", 0, 0), stake("b", 43, 0), stake("c", 0, 43)));

        assertFalse(tooClose.accepted());
        assertEquals("NO_VALID_STAKE_TRIANGLE", tooClose.reason());
        assertFalse(tooFar.accepted());
    }

    @Test
    void searchesPastInvalidTriplesAndRequiresOneWorld() {
        var result = ArenaManifestPolicy.select("WSI-CALL-D20", List.of(
                stake("a", 0, 0), stake("b", 5, 0), stake("c", 0, 5),
                stake("d", 30, 0), stake("e", 15, 26)));
        assertTrue(result.accepted());
        assertEquals(List.of("a", "d", "e"), result.candidate().stakeInstanceIds());

        assertFalse(ArenaManifestPolicy.select("WSI-CALL-D20", List.of(
                stake("a", "world", 0, 0), stake("b", "nether", 30, 0),
                stake("c", "end", 15, 26))).accepted());
    }

    @Test
    void enforcesDayFortyCenterRadius() {
        var valid = ArenaManifestPolicy.select("WSI-CALL-D40", List.of(
                stake("a", 0, 17), stake("b", -14.722, -8.5), stake("c", 14.722, -8.5)));
        assertTrue(valid.accepted());

        var invalid = ArenaManifestPolicy.select("WSI-CALL-D40", List.of(
                stake("a", 0, 5), stake("b", -4, -3), stake("c", 4, -3)));
        assertFalse(invalid.accepted());
    }

    @Test
    void rejectsUnknownCallAndInsufficientStakes() {
        assertFalse(ArenaManifestPolicy.select("WSI-CALL-D10", List.of(stake("a", 0, 0))).accepted());
        assertThrows(IllegalArgumentException.class,
                () -> ArenaManifestPolicy.select("WSI-CALL-UNKNOWN", List.of()));
    }

    private static ArenaManifestPolicy.Stake stake(String id, double x, double z) {
        return stake(id, "world", x, z);
    }

    private static ArenaManifestPolicy.Stake stake(String id, String world, double x, double z) {
        return new ArenaManifestPolicy.Stake(id, world, x, 64, z, "ACTIVE");
    }
}
