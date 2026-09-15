package com.lsc.corp.wsplugin.testlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.content.ProductionBundleValidator;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.world.SeasonDayPolicy;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class TestDayResetPolicyTest {
    private static ProductionContentCatalog catalog;

    @BeforeAll
    static void load() throws Exception {
        catalog = new ProductionBundleValidator().validateDirectory(
                Path.of("src/main/resources/content/ws-content-r2")).catalog();
    }

    @Test
    void frozenResetBeginsAtRealLogicalNowAndCannotImmediatelyAdvance() {
        long now = 1_789_444_761_369L;
        var day = catalog.daysByNumber().get(1);
        var state = TestDayResetPolicy.prepare(day, catalog.eventsById(), 1, now, 3);

        assertEquals(1, state.day);
        assertEquals("PREPARING", state.state);
        assertEquals(now, state.startedAtEpochMs);
        assertEquals(now + 30_000L, state.pressureStartedAtEpochMs);
        assertEquals(3L, state.sequence);
        assertTrue(state.lockedThreatBudget3 > 0);
        assertEquals(day.eventIds(), state.eventQueue);
    }

    @Test
    void manualDaySelectionUsesPartyScaledThreatNotThreePlayerBudget() {
        var day = catalog.daysByNumber().get(18);
        var state = TestDayResetPolicy.prepare(day, catalog.eventsById(), 4, 12_000L, 8);

        assertEquals(18, state.day);
        assertEquals(SeasonDayPolicy.threatForParty(day, catalog.eventsById(), 4),
                state.lockedThreatBudget3);
        assertEquals(42_000L, state.pressureStartedAtEpochMs);
        assertEquals(day.resourceBudgetTotals(), state.lockedResourceBudgets);
    }

    @Test
    void rejectsMissingDefinitionOrInvalidClock() {
        assertThrows(IllegalArgumentException.class, () ->
                TestDayResetPolicy.prepare(null, catalog.eventsById(), 1, 1L, 1L));
        assertThrows(IllegalArgumentException.class, () ->
                TestDayResetPolicy.prepare(catalog.daysByNumber().get(1),
                        catalog.eventsById(), 1, -1L, 1L));
    }
}
