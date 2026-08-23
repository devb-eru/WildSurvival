package com.lsc.corp.wsplugin.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.content.ProductionBundleValidator;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.finale.FinalPolicy;
import com.lsc.corp.wsplugin.finale.FinalReadinessPolicy;
import com.lsc.corp.wsplugin.growth.AugmentMilestonePolicy;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SeasonHeadlessSimulationTest {
    private static ProductionContentCatalog content;

    @BeforeAll
    static void loadContent() throws Exception {
        content = new ProductionBundleValidator().validateDirectory(
                Path.of("src/main/resources/content/ws-content-r2")).catalog();
    }

    @Test
    void oneThroughFourPlayerRunsHaveExecutableDayPlansThroughDayFifty() {
        assertEquals(50, content.daysByNumber().size());
        for (int partySize = 1; partySize <= 4; partySize++) {
            int ordinaryDays = 0;
            for (int dayNumber = 1; dayNumber <= 50; dayNumber++) {
                ProductionContentCatalog.DayEntry day = content.daysByNumber().get(dayNumber);
                assertEquals(dayNumber, day.day());
                if (day.bossDay()) {
                    assertEquals(-1, SeasonDayPolicy.threatForParty(day, content.eventsById(), partySize));
                    assertTrue(Set.of(10, 20, 30, 40).contains(dayNumber));
                    continue;
                }
                ordinaryDays++;
                int budget = SeasonDayPolicy.threatForParty(day, content.eventsById(), partySize);
                assertTrue(budget > 0, "day=" + dayNumber + " party=" + partySize);
                var plan = SeasonDayPolicy.enemyPlan(content.enemiesById(), dayNumber, budget,
                        0x51A50L + partySize * 100L + dayNumber);
                assertFalse(plan.isEmpty(), "day=" + dayNumber + " party=" + partySize);
                int spent = plan.stream().map(content.enemiesById()::get)
                        .mapToInt(ProductionContentCatalog.EnemyEntry::budgetCost).sum();
                assertTrue(spent <= budget, "day=" + dayNumber + " party=" + partySize);
                assertTrue(plan.size() <= 64);
                assertTrue(SeasonDayPolicy.activeCap(dayNumber, partySize) >= 4);
            }
            assertEquals(46, ordinaryDays);
            assertEquals(partySize / 2 + 1, FinalPolicy.majority(partySize));
        }
    }

    @Test
    void growthAndFinalGatesRemainOrderedAcrossTheSimulatedSeason() {
        assertEquals(java.util.List.of(3, 6, 10, 15, 20, 25, 30, 35, 40, 45),
                AugmentMilestonePolicy.personalMilestones());
        assertEquals(java.util.List.of(10, 20, 30, 40), AugmentMilestonePolicy.partyMilestones());
        var day49 = completeFinalState(49);
        var day50 = completeFinalState(50);
        assertEquals(1, FinalReadinessPolicy.missing(day49).size());
        assertTrue(FinalReadinessPolicy.missing(day50).isEmpty());
    }

    private static FinalReadinessPolicy.State completeFinalState(int day) {
        return new FinalReadinessPolicy.State(day,
                Set.of("BOSS-D10", "BOSS-D20", "BOSS-D30", "BOSS-D40"),
                Set.of("A", "B", "C", "D"),
                Set.of("C27", "C28-A", "C28-B", "C28-C", "C28-D", "C29"),
                Set.of("FAC-R01", "FAC-R02", "FAC-R03", "FAC-R04", "FAC-R06"),
                3, true, false, false);
    }
}
