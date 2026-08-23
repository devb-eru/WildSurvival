package com.lsc.corp.wsplugin.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lsc.corp.wsplugin.content.ProductionBundleValidator;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SeasonDayPolicyTest {
    private static ProductionContentCatalog content;

    @BeforeAll
    static void loadContent() throws Exception {
        content = new ProductionBundleValidator().validateDirectory(
                Path.of("src/main/resources/content/ws-content-r2")).catalog();
    }

    @Test
    void usesExplicitTwoThreeFourPlayerThreatAndSoloFallback() {
        var day21 = content.daysByNumber().get(21);
        assertEquals(List.of(22, 32, 42, 51), java.util.stream.IntStream.rangeClosed(1, 4)
                .map(size -> SeasonDayPolicy.threatForParty(day21, content.eventsById(), size)).boxed().toList());
    }

    @Test
    void derivesEarlyThreatFromThreePlayerAuthority() {
        var day2 = content.daysByNumber().get(2);
        assertEquals(List.of(4, 7, 10, 11), java.util.stream.IntStream.rangeClosed(1, 4)
                .map(size -> SeasonDayPolicy.threatForParty(day2, content.eventsById(), size)).boxed().toList());
    }

    @Test
    void buildsDeterministicAffordableRecentEnemyPlan() {
        int budget = SeasonDayPolicy.threatForParty(content.daysByNumber().get(49), content.eventsById(), 4);
        List<String> first = SeasonDayPolicy.enemyPlan(content.enemiesById(), 49, budget, 91234L);
        List<String> second = SeasonDayPolicy.enemyPlan(content.enemiesById(), 49, budget, 91234L);
        assertEquals(first, second);
        assertFalse(first.isEmpty());
        int spent = first.stream().map(content.enemiesById()::get)
                .mapToInt(ProductionContentCatalog.EnemyEntry::budgetCost).sum();
        int minimum = first.stream().map(content.enemiesById()::get)
                .mapToInt(ProductionContentCatalog.EnemyEntry::budgetCost).min().orElseThrow();
        assertTrue(spent <= budget);
        assertTrue(budget - spent < minimum);
        assertTrue(first.stream().map(content.enemiesById()::get)
                .allMatch(enemy -> enemy.firstDay() >= 40 && enemy.firstDay() <= 49
                        && enemy.parentId().isBlank() && !enemy.flags().contains("NO_REWARD")));
    }

    @Test
    void bossDaysDoNotCreateNormalThreat() {
        assertEquals(-1, SeasonDayPolicy.threatForParty(content.daysByNumber().get(40), content.eventsById(), 3));
    }
}
