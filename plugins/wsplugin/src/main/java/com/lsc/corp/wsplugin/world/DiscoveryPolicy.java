package com.lsc.corp.wsplugin.world;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.Set;

public final class DiscoveryPolicy {
    private DiscoveryPolicy() { }

    public static String desiredState(ProductionContentCatalog.DiscoveryEntry definition,
                                      RunSnapshot run, RunSnapshot.DiscoveryNodeState state) {
        if (run.discoveryIds.contains(definition.id()) || "MASTERED".equals(state.state)) {
            return "MASTERED".equals(state.state) ? "MASTERED" : "DISCOVERED";
        }
        boolean prerequisites = definition.prerequisiteIds().stream().allMatch(run.discoveryIds::contains);
        if (!prerequisites || run.day < definition.recommendedDayMin()) return "HIDDEN";
        if (state.evidence.stream().anyMatch(value -> value.startsWith("COMPLETE:"))) return "DISCOVERED";
        int evidenceCount = state.evidence.size();
        if (evidenceCount >= 3) return "EXPERIMENT";
        if (evidenceCount >= 2) return "HYPOTHESIS";
        return "CLUE";
    }

    public static boolean mayComplete(ProductionContentCatalog.DiscoveryEntry definition,
                                      RunSnapshot run, Set<String> evidence) {
        return run.day >= definition.recommendedDayMin()
                && definition.prerequisiteIds().stream().allMatch(run.discoveryIds::contains)
                && evidence.stream().anyMatch(value -> value.startsWith("COMPLETE:"));
    }
}
