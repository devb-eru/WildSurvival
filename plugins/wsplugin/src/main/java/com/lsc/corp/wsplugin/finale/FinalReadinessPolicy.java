package com.lsc.corp.wsplugin.finale;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class FinalReadinessPolicy {
    private static final Set<String> BOSSES = Set.of("BOSS-D10", "BOSS-D20", "BOSS-D30", "BOSS-D40");
    private static final Set<String> PARTS = Set.of("A", "B", "C", "D");
    private static final Set<String> DISCOVERIES = Set.of("C27", "C28-A", "C28-B", "C28-C", "C28-D", "C29");
    private static final Set<String> READY_FACILITIES = Set.of("FAC-R01", "FAC-R02", "FAC-R03", "FAC-R04", "FAC-R06");

    private FinalReadinessPolicy() {
    }

    public static List<String> missing(State state) {
        List<String> missing = new ArrayList<>();
        if (state.day < 50) missing.add("FINAL_DAY_LOCK · Day 50 필요");
        requireAll(missing, "보스", state.defeatedBossIds, BOSSES);
        requireAll(missing, "재건 부품", state.reconstructionPartIds, PARTS);
        requireAll(missing, "발견", state.discoveryIds, DISCOVERIES);
        for (String facility : READY_FACILITIES) {
            if (!state.readyFacilityTypes.contains(facility)) missing.add(facility + " READY");
        }
        if (state.calibratedStakes < 3) {
            missing.add("FAC-R05 CALIBRATED 3기 (현재 " + state.calibratedStakes + ")");
        }
        if (!state.finalSignalKeyOnline) missing.add("WSR-FINAL_SIGNAL_KEY 소지자 온라인");
        if (state.activeBoss) missing.add("활성 보스 종료");
        if (state.activeEncounter) missing.add("활성 공세 종료");
        return List.copyOf(missing);
    }

    private static void requireAll(List<String> missing, String label, Set<String> actual, Set<String> required) {
        required.stream().filter(value -> !actual.contains(value)).sorted()
                .forEach(value -> missing.add(label + " " + value));
    }

    public record State(int day, Set<String> defeatedBossIds, Set<String> reconstructionPartIds,
                        Set<String> discoveryIds, Set<String> readyFacilityTypes, long calibratedStakes,
                        boolean finalSignalKeyOnline, boolean activeBoss, boolean activeEncounter) {
    }
}
