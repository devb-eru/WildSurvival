package com.lsc.corp.wsplugin.content;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

public record ProductionContentCatalog(
        List<CatalogEntry> codexEntries,
        Map<String, CatalogEntry> itemsById,
        Map<String, MaterialEntry> materialsById,
        Map<String, ItemEntry> nonEquipmentItemsById,
        Map<String, EquipmentEntry> equipmentById,
        Map<String, FacilityEntry> facilitiesById,
        List<RecipeEntry> recipes,
        Map<String, List<RecipeEntry>> recipesByOutput,
        List<SkillEntry> skills,
        Map<String, SkillEntry> skillsById,
        List<AugmentEntry> personalAugments,
        List<AugmentEntry> partyAugments,
        Map<String, AugmentEntry> augmentsById,
        Map<String, StatusEntry> statusesById,
        Map<String, EnemyEntry> enemiesById,
        Map<String, BossEntry> bossesById,
        Map<String, SupportEntityEntry> supportEntitiesById,
        Map<String, ActionBundleEntry> actionBundlesById,
        Map<String, LootEntry> lootById,
        Map<Integer, DayEntry> daysByNumber,
        Map<String, EventEntry> eventsById,
        Map<Integer, List<EventEntry>> mainEventsByDay,
        Map<String, ResearchEntry> researchById,
        Map<String, DiscoveryEntry> discoveriesById,
        Map<String, StorySceneEntry> storyScenesById,
        Map<String, StoryLogEntry> storyLogsById,
        Map<String, FinalRecordEntry> finalRecordsById,
        Map<String, BudgetProfileEntry> budgetProfilesById,
        Map<String, DrawLockEntry> drawLocksById,
        Map<String, Integer> counts
) {
    public CatalogEntry item(String id) {
        CatalogEntry entry = itemsById.get(id);
        if (entry == null) throw new IllegalArgumentException("Unknown production item " + id);
        return entry;
    }

    public record CatalogEntry(String id, int codexIndex, String name, String displayMaterial,
                               String domain, int firstDay, String equipmentType, String equipmentSlot) {
        public boolean equipment() {
            return equipmentType != null && !equipmentType.isBlank();
        }
    }

    public record MaterialEntry(String id, String name, String tier, int firstDay, String displayMaterial,
                                String ledgerScope, String acquisitionKind, int registrationAmount,
                                List<String> harvestSources, String sourceText, String usageText) { }

    public record ItemEntry(String id, String name, String category, int firstDay, String displayMaterial,
                            int stackLimit, String effectText, String recipeId, String textKey,
                            String customModelKey, String ownership, String usePolicy,
                            String connectedFacilityId, String constraintText) {
        public boolean quickConsumable() {
            return "CONS".equals(category);
        }
    }

    public record EquipmentEntry(String id, String name, String equipmentType, String equipmentSlot,
                                 String weaponClass, String displayMaterial, String rarity, int itemLevel,
                                 int firstDay, int maxDurability, int toolTier, String setId,
                                 List<String> tags, Map<String, Double> stats, String executionOpcode,
                                 String harvestProfileId, double resourceYieldMultiplier,
                                 int durabilityCostPerSuccess, boolean vanillaActionPassthrough,
                                 String effectText) {
        public double stat(String id) {
            return stats.getOrDefault(id, 0.0);
        }

        public boolean utility() {
            return "UTILITY".equals(equipmentType);
        }
    }

    public record FacilityEntry(String id, String name, String facilityTier, String representation,
                                String coreMaterial, String networkPolicy, String itemId, String recipeId,
                                int firstDay, int activationDay, int maxLevel, int baseHp, String hpAuthority, int workSlots,
                                int threatValue, String costProfile, String unlockText, String effectOpcode,
                                String effectText, String maintenanceText, String portableFallback,
                                String stateMachine) {
        public boolean portableDevice() {
            return "PORTABLE".equals(facilityTier);
        }

        public boolean reconstruction() {
            return "RECONSTRUCTION".equals(facilityTier);
        }
    }

    public record RecipeEntry(String id, String outputId, int outputAmount, String recipeType, String inputAuthority,
                              String layout, List<IngredientEntry> ingredients, List<String> raw) {
    }

    public record IngredientEntry(int slot, String kind, String key, int amount, boolean consume) {
    }

    public record SkillEntry(String id, String kind, String name, String weaponClass, double apCost,
                             int cooldownTicks, double damageCoefficient, double breakDamage, double range,
                             double arcDegrees, int maxTargets, String effect, List<String> tags,
                             int unlockLevel, String consumableId, String description) {
        public boolean weaponActive() {
            return "WEAPON_ACTIVE".equals(kind);
        }

        public boolean commonActive() {
            return "COMMON_ACTIVE".equals(kind);
        }
    }

    public record AugmentEntry(String id, String name, String tier, String scope, List<String> tags,
                               String effectOpcode, String effectText, String constraintText,
                               String weightingText, List<String> exclusiveWith, boolean evolution) {
        public boolean personal() {
            return "PERSONAL".equals(scope);
        }
    }

    public record StatusEntry(String id, String canonicalId, String authorityState, String name,
                              List<String> tags, double standardDurationSeconds,
                              double chaosDurationSeconds, double standardMaxPreResistSeconds,
                              double chaosMaxPreResistSeconds, double baseStrength,
                              double tickIntervalSeconds, int maxStacks, String resistPolicy,
                              String tenacityPolicy, String bossPolicy, String stacking,
                              String cleanseCategory, int visualPriority, String zeroDamagePolicy,
                              String shieldPolicy, boolean requiresHpDamage) {
        public boolean executableBaseline() {
            return !"TEMPLATE_LOCKED".equals(authorityState);
        }
    }

    public record EnemyEntry(String id, String name, String bukkitType, String displayFallback,
                             int firstDay, String dayProfile, String role, int budgetCost,
                             double baseHp, double defence, double attackDamage, double penetration,
                             double breakMax, int augmentSlots, boolean elite, String actionBundleId,
                             int telegraphTicks, int cooldownTicks, double attackRange, String statusId,
                             String spawnPolicy, String parentId, String cleanupPolicy, String rewardOwner,
                             String lootTableId, int activityExp, List<String> flags) {
        public boolean rewardsPlayers() {
            return !"LOOT-NONE".equals(lootTableId) && !flags.contains("NO_REWARD");
        }
    }

    public record BossEntry(String id, String name, String bukkitType, String displayFallback,
                            int firstDay, String dayProfile, double baseHp, double defence,
                            double attackDamage, double penetration, double breakMax,
                            String actionBundleId, String entitySetId, int telegraphTicks,
                            int cooldownTicks, double attackRange, String spawnPolicy,
                            String cleanupPolicy, String rewardOwner, String lootTableId,
                            int activityExp, List<String> flags) { }

    public record SupportEntityEntry(String id, String kind, String bukkitType, String displayFallback,
                                     String cleanupPolicy, String lootTableId, String ownerPolicy,
                                     boolean persistent) { }

    public record ActionBundleEntry(String id, String ownerId, String kind,
                                    List<ActionEntry> actions, List<String> stateMachine) { }

    public record ActionEntry(String id, String name, int telegraphTicks, int startupTicks,
                              int activeTicks, int recoveryTicks, int cooldownTicks, double range,
                              double damage, double penetration, double breakDamage, String statusId) { }

    public record LootEntry(String id, String sourceId, String profile, String distribution,
                            String executionOpcode, List<String> guaranteedPool, int guaranteedMin,
                            int guaranteedMax, List<String> specialtyPool, int specialtyMin,
                            int specialtyMax, Map<String, Double> equipmentChances, int pityLimit,
                            List<LootFixedEntry> fixedEntries, String equipmentSelectionProfile,
                            int partyAugmentMilestone, int requiredToolTierMin,
                            int requiredToolTierMax, boolean noReward, List<String> flags) { }

    public record LootFixedEntry(String itemId, List<Integer> amounts, String scope) {
        public int amountForPartySize(int partySize) {
            int index = partySize >= 4 ? 2 : partySize == 3 ? 1 : 0;
            return amounts.get(index);
        }
    }

    public record DayEntry(String id, String sourceDocumentId, int day,
                           int progressExp, int activityExp, int totalExp, int cumulativeExp,
                           int expectedEndLevel, String endLevelText, int threatBudget3,
                           List<String> resourceBudgetAuthority, List<Integer> resourceBudgetTotals,
                           List<String> eventIds, String bossId, String milestoneText,
                           boolean finalAvailable, boolean completionAllowed,
                           List<String> stateMachine) {
        public boolean bossDay() {
            return !bossId.isBlank();
        }
    }

    public record EventEntry(String id, String kind, int firstDay, String executionOpcode,
                             String pressureProfileId, List<Integer> partyThreat,
                             String objectiveText, String telegraphText, String rewardText,
                             String failureText, JsonObject payload) {
        public boolean mainEvent() {
            return "MAIN_EVENT".equals(kind);
        }
    }

    public record ResearchEntry(String id, int minimumDay, String prerequisiteText,
                                String comparisonInput, Map<String, Integer> cost,
                                int durationSeconds, String unlockText, List<String> stateMachine) { }

    public record DiscoveryEntry(String id, String canonicalId, String kind, String name,
                                 int recommendedDayMin, int recommendedDayMax,
                                 List<String> prerequisiteIds, String primaryPath,
                                 String alternativePath, String unlockText, String clueText,
                                 List<String> stateMachine) {
        public boolean core() {
            return !"OPTIONAL".equals(kind);
        }
    }

    public record StorySceneEntry(String id, String sceneGroup, String triggerKey,
                                  String triggerEvent, String triggerRef, String priority,
                                  String payloadKey, String payloadText, String fallbackPolicy,
                                  boolean replayableText, boolean worldEffectReplayable) { }

    public record StoryLogEntry(String id, String triggerText, String payloadKey,
                                String progressionEffect, boolean progressionRequired) { }

    public record FinalRecordEntry(String id, String recordKind, String executionOpcode,
                                   JsonObject payload) { }

    public record BudgetProfileEntry(String id, String mode, String intent,
                                     Map<String, Double> multipliers, JsonObject selectionWeights) {
        public double multiplier(String domain) {
            return multipliers.getOrDefault(domain, 1.0);
        }
    }

    public record DrawLockEntry(String id, String scope, int milestone, String trigger,
                                String tierPolicy, int choiceCount, int selectionCount,
                                boolean returnToSlotZero, boolean sharedTierLock) { }
}
