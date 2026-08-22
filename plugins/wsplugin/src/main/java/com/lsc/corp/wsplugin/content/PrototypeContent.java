package com.lsc.corp.wsplugin.content;

import java.util.List;
import java.util.Map;

public record PrototypeContent(
        int schemaVersion,
        String contentRevision,
        String runType,
        boolean promotionForbidden,
        int minimumPlayers,
        int maximumPlayers,
        List<Integer> dayCheckpoints,
        Map<Integer, Integer> progressExpByDay,
        List<ResourceDefinition> resources,
        List<RecipeDefinition> recipes,
        List<WeaponDefinition> weapons,
        List<EnemyDefinition> enemies,
        List<AugmentDefinition> personalAugments,
        List<AugmentDefinition> partyAugments,
        BossDefinition boss
) {
    public ResourceDefinition resource(String id) {
        return resources.stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown resource " + id));
    }

    public WeaponDefinition weapon(String id) {
        return weapons.stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown weapon " + id));
    }

    public EnemyDefinition enemy(String id) {
        return enemies.stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown enemy " + id));
    }

    public record ResourceDefinition(
            String id,
            String name,
            List<String> sourceMaterials,
            int amountPerNode,
            int activityExp
    ) {}

    public record RecipeDefinition(
            String id,
            String name,
            Map<String, Integer> costs,
            String rewardType,
            String rewardId,
            int rewardAmount
    ) {}

    public record WeaponDefinition(
            String id,
            String name,
            String material,
            int intervalTicks,
            double range,
            double arcDegrees,
            List<Double> attackCoefficients,
            List<Double> breakDamage,
            String statusId,
            double statusChance
    ) {}

    public record EnemyDefinition(
            String id,
            String name,
            String entityType,
            String role,
            int firstDay,
            double hp,
            double defence,
            double breakMax,
            double attackDamage,
            int activityExp,
            Map<String, Integer> drops
    ) {}

    public record AugmentDefinition(
            String id,
            String name,
            String tier,
            String scope,
            double attackMultiplier,
            double breakMultiplier,
            double resourceMultiplier,
            int maxApBonus,
            double reviveSpeedMultiplier
    ) {}

    public record BossDefinition(
            String id,
            String name,
            String entityType,
            int day,
            double hp,
            double breakMax,
            int groggyTicks,
            double groggyDamageMultiplier,
            int phaseTwoHpPercent,
            int cooperationChannelTicks,
            double cooperationBreak,
            int rewardExp
    ) {}
}
