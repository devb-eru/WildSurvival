package com.lsc.corp.wsplugin.research;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ResearchPolicyTest {
    @Test
    void keepsUnknownInputContractsHardBlockedWithoutConsumingResources() {
        assertEquals("HIDDEN", ResearchPolicy.availability(2, 3, "D3, C03", Set.of(), Set.of(), false));
        assertEquals("OBSERVABLE", ResearchPolicy.availability(3, 3, "D3, C03", Set.of(), Set.of(), false));
        assertEquals("HYPOTHESIZED", ResearchPolicy.availability(3, 3, "D3, C03", Set.of("C03"), Set.of(), false));
        assertEquals("READY", ResearchPolicy.availability(3, 3, "D3, C03", Set.of("C03"), Set.of(), true));
    }

    @Test
    void previousResearchReferencesAreNotMistakenForDiscoveryIds() {
        assertEquals("OBSERVABLE", ResearchPolicy.availability(15, 15, "D15, RS-D05-DOCTRINE-I",
                Set.of(), Set.of(), false));
        assertEquals("HYPOTHESIZED", ResearchPolicy.availability(15, 15, "D15, RS-D05-DOCTRINE-I",
                Set.of(), Set.of("RS-D05-DOCTRINE-I"), false));
    }
}
