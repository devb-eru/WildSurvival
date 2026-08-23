package com.lsc.corp.wsplugin.facility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import org.junit.jupiter.api.Test;

class FacilityPolicyTest {
    @Test
    void appliesPartyScalingAndLevelProfiles() {
        assertEquals(new FacilityPolicy.UpgradeCost(12, 2, 10, 4, 0),
                FacilityPolicy.upgradeCost("FP-PRODUCTION", 1, 3));
        assertEquals(new FacilityPolicy.UpgradeCost(10, 0, 14, 7, 3),
                FacilityPolicy.upgradeCost("FP-PRODUCTION", 2, 4));
        assertEquals(0.75, FacilityPolicy.processingTimeMultiplier(5));
        assertEquals(1.60, FacilityPolicy.hpMultiplier(5));
        assertEquals(4, FacilityPolicy.workSlots(2, 5));
    }

    @Test
    void derivesHealthStateAndDirectNetworkEdges() {
        assertEquals("ACTIVE", FacilityPolicy.healthState(500, 1000));
        assertEquals("DEGRADED", FacilityPolicy.healthState(499, 1000));
        assertEquals("DISABLED", FacilityPolicy.healthState(250, 1000));
        assertTrue(FacilityPolicy.directlyConnected("world", 0, 64, 0,
                "world", 24, 64, 0, 24));
        assertFalse(FacilityPolicy.directlyConnected("world", 0, 64, 0,
                "world", 25, 64, 0, 24));
        assertFalse(FacilityPolicy.directlyConnected("world", 0, 64, 0,
                "nether", 0, 64, 0, 24));
    }

    @Test
    void distinguishesEverUnlockedFromCurrentlyActiveLedger() {
        RunSnapshot run = new RunSnapshot();
        run.facilityTypesEverActivated.add("FAC-S16");
        RunSnapshot.FacilityInstanceState depot = new RunSnapshot.FacilityInstanceState();
        depot.instanceId = "facility:1";
        depot.facilityType = "FAC-S16";
        depot.state = "DISABLED";
        run.facilities.put(depot.instanceId, depot);
        assertTrue(FacilityStateAccess.everActivated(run, "FAC-S16"));
        assertFalse(FacilityStateAccess.active(run, "FAC-S16"));
        depot.state = "ACTIVE";
        assertTrue(FacilityStateAccess.active(run, "FAC-S16"));
    }

    @Test
    void reconstructionNeverStartsAsCompleted() {
        assertEquals("ASSEMBLED", FacilityPolicy.initialReconstructionState("FAC-R01"));
        assertEquals("ASSEMBLED", FacilityPolicy.initialReconstructionState("FAC-R02"));
        assertEquals("ASSEMBLED", FacilityPolicy.initialReconstructionState("FAC-R03"));
        assertEquals("ASSEMBLED", FacilityPolicy.initialReconstructionState("FAC-R04"));
        assertEquals("PLACED", FacilityPolicy.initialReconstructionState("FAC-R05"));
        assertEquals("READY_LOCKED", FacilityPolicy.initialReconstructionState("FAC-R06"));
        assertEquals(120_000L, FacilityPolicy.reconstructionDurationMillis("FAC-R01"));
        assertEquals(60_000L, FacilityPolicy.reconstructionDurationMillis("FAC-R02"));
        assertEquals(0L, FacilityPolicy.reconstructionDurationMillis("FAC-R03"));
    }
}
