package com.lsc.corp.wsplugin.research;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResearchPolicy {
    private static final Pattern DISCOVERY_ID = Pattern.compile("(?<![A-Z0-9])C(\\d{2})(?:-[A-D])?(?![A-Z0-9])");
    private static final Pattern RESEARCH_ID = Pattern.compile("RS-D\\d{2}-[A-Z0-9-]+");

    private ResearchPolicy() {
    }

    public static String availability(int currentDay, int minimumDay, String prerequisiteText,
                                      Set<String> discoveries, Set<String> completedResearch,
                                      boolean executableInputContract) {
        if (currentDay < minimumDay) return "HIDDEN";
        if (!referencesSatisfied(prerequisiteText, discoveries, completedResearch)) return "OBSERVABLE";
        return executableInputContract ? "READY" : "HYPOTHESIZED";
    }

    /**
     * Availability discovery is monotonic in a live run. READY and every work/completion state
     * are owned by evidence or transaction execution and must never be downgraded by a later
     * catalogue refresh (including when Test Lab opens the research GUI).
     */
    static boolean refreshableState(String state) {
        return Set.of("HIDDEN", "OBSERVABLE", "HYPOTHESIZED").contains(state);
    }

    static boolean referencesSatisfied(String text, Set<String> discoveries, Set<String> completedResearch) {
        Matcher discoveriesInText = DISCOVERY_ID.matcher(text);
        while (discoveriesInText.find()) {
            String reference = discoveriesInText.group();
            if (!discoveries.contains(reference)) return false;
        }
        Matcher researchInText = RESEARCH_ID.matcher(text);
        while (researchInText.find()) {
            String reference = researchInText.group();
            if (!completedResearch.contains(reference)) return false;
        }
        return true;
    }
}
