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
        List<ItemDefinition> items,
        List<RecipeDefinition> recipes,
        List<WeaponDefinition> weapons,
        List<SkillDefinition> skills,
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

    public ItemDefinition item(String id) {
        return items.stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown item " + id));
    }

    public SkillDefinition skill(String id) {
        return skills.stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill " + id));
    }

    public record ResourceDefinition(
            String id,
            String name,
            List<String> sourceMaterials,
            int amountPerNode,
            int activityExp
    ) {}

    public record ItemDefinition(
            String id,
            String name,
            String material,
            String category,
            String description
    ) {}

    public record RecipeDefinition(
            String id,
            String name,
            Map<String, Integer> costs,
            List<String> shape,
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

    public record SkillDefinition(
            String id,
            String name,
            List<String> weaponIds,
            String effect,
            double apCost,
            double damageCoefficient,
            double breakDamage,
            double range,
            double arcDegrees,
            int maxTargets,
            String particle,
            String sound,
            String description
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
