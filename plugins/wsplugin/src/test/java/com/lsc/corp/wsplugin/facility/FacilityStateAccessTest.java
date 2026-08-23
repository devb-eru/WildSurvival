package com.lsc.corp.wsplugin.facility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.run.RunSnapshot;
import org.junit.jupiter.api.Test;

class FacilityStateAccessTest {
    @Test
    void portableAndSettlementPurifiersProtectOnlyTheirRadius() {
        RunSnapshot run = new RunSnapshot();
        run.facilities.put("portable", facility("FAC-P05", "ACTIVE", "a", 0, 64, 0, 1));
        assertTrue(FacilityStateAccess.corruptionProtected(run, "world", 4.9, 64, 0));
        assertFalse(FacilityStateAccess.corruptionProtected(run, "world", 7.0, 64, 0));
        run.facilities.put("purifier", facility("FAC-S13", "ACTIVE", "network-a", 20, 64, 0, 2));
        assertTrue(FacilityStateAccess.corruptionProtected(run, "world", 29.5, 64, 0));
    }

    @Test
    void relayRequiresAnActivePurifierOnTheSameNetwork() {
        RunSnapshot run = new RunSnapshot();
        run.facilities.put("relay", facility("FAC-S14", "ACTIVE", "network-a", 0, 64, 0, 1));
        assertFalse(FacilityStateAccess.corruptionProtected(run, "world", 4, 64, 0));
        run.facilities.put("wrong", facility("FAC-S13", "ACTIVE", "network-b", 30, 64, 0, 1));
        assertFalse(FacilityStateAccess.corruptionProtected(run, "world", 4, 64, 0));
        run.facilities.put("power", facility("FAC-S13", "ACTIVE", "network-a", 30, 64, 0, 1));
        assertTrue(FacilityStateAccess.corruptionProtected(run, "world", 4, 64, 0));
    }

    private static RunSnapshot.FacilityInstanceState facility(String type, String state, String network,
                                                               int x, int y, int z, int level) {
        RunSnapshot.FacilityInstanceState value = new RunSnapshot.FacilityInstanceState();
        value.facilityType = type;
        value.state = state;
        value.networkId = network;
        value.world = "world";
        value.x = x;
        value.y = y;
        value.z = z;
        value.level = level;
        return value;
    }
}
