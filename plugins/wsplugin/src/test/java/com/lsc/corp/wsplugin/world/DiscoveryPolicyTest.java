package com.lsc.corp.wsplugin.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiscoveryPolicyTest {
    private static final ProductionContentCatalog.DiscoveryEntry NODE =
            new ProductionContentCatalog.DiscoveryEntry("C02", "DISC-C02", "CORE", "현장 가공",
                    2, 4, List.of("C01"), "주 경로", "대체 경로", "해금", "단서",
                    List.of("HIDDEN", "CLUE", "HYPOTHESIS", "EXPERIMENT", "DISCOVERED", "MASTERED"));

    @Test
    void preservesEvidenceUntilDayAndPrerequisitesPermitCompletion() {
        RunSnapshot run = new RunSnapshot();
        run.day = 1;
        RunSnapshot.DiscoveryNodeState state = new RunSnapshot.DiscoveryNodeState();
        state.discoveryId = "C02";
        state.evidence.add("COMPLETE:PROCESSING_PATH");
        assertEquals("HIDDEN", DiscoveryPolicy.desiredState(NODE, run, state));

        run.day = 2;
        run.discoveryIds.add("C01");
        assertEquals("DISCOVERED", DiscoveryPolicy.desiredState(NODE, run, state));
    }

    @Test
    void exposesClueHypothesisAndExperimentWithoutAutoCompleting() {
        RunSnapshot run = new RunSnapshot();
        run.day = 2;
        run.discoveryIds.add("C01");
        RunSnapshot.DiscoveryNodeState state = new RunSnapshot.DiscoveryNodeState();
        assertEquals("CLUE", DiscoveryPolicy.desiredState(NODE, run, state));
        state.evidence.add("EVIDENCE:A");
        state.evidence.add("EVIDENCE:B");
        assertEquals("HYPOTHESIS", DiscoveryPolicy.desiredState(NODE, run, state));
        state.evidence.add("EVIDENCE:C");
        assertEquals("EXPERIMENT", DiscoveryPolicy.desiredState(NODE, run, state));
    }
}
