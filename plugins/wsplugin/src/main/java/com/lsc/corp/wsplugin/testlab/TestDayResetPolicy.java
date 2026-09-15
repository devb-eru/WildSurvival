package com.lsc.corp.wsplugin.testlab;

import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.world.SeasonDayPolicy;
import java.util.Map;

/** Restores an executable Day clock whenever Test Lab replaces its run snapshot. */
public final class TestDayResetPolicy {
    private static final long PREPARATION_MILLIS = 30_000L;

    private TestDayResetPolicy() { }

    public static RunSnapshot.DayState prepare(ProductionContentCatalog.DayEntry definition,
                                               Map<String, ProductionContentCatalog.EventEntry> eventsById,
                                               int partySize, long now, long sequence) {
        if (definition == null || now < 0L || sequence < 1L) {
            throw new IllegalArgumentException("Test Lab Day definition, clock, and sequence are required");
        }
        RunSnapshot.DayState state = new RunSnapshot.DayState();
        state.day = definition.day();
        state.dayId = definition.id();
        state.state = "PREPARING";
        state.lockedBudgetProfileId = "STD-BALANCED";
        state.lockedThreatBudget3 = SeasonDayPolicy.threatForParty(definition, eventsById, partySize);
        state.lockedResourceBudgets.addAll(definition.resourceBudgetTotals());
        state.eventQueue.addAll(definition.eventIds());
        state.activeEventId = definition.eventIds().isEmpty() ? null : definition.eventIds().getFirst();
        state.startedAtEpochMs = now;
        state.pressureStartedAtEpochMs = Math.addExact(now, PREPARATION_MILLIS);
        state.sequence = sequence;
        return state;
    }
}
