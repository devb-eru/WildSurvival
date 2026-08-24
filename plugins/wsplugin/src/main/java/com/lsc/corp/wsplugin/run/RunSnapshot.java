package com.lsc.corp.wsplugin.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RunSnapshot {
    public int schemaVersion = 2;
    public long version = 0;
    public String runId;
    public String contentRevision;
    public String runType = "SEASON_1";
    public String state = "LOBBY";
    public int day = 1;
    public long createdAtEpochMs;
    public long startedAtEpochMs;
    public long checkpointStartedAtEpochMs;
    public long seed;
    public List<String> registeredPlayers = new ArrayList<>();
    public Map<String, PlayerState> players = new LinkedHashMap<>();
    public Map<String, Integer> resources = new LinkedHashMap<>();
    public Set<String> committedKeys = new LinkedHashSet<>();
    public List<OutboxEvent> outbox = new ArrayList<>();
    public Map<String, MilestoneLock> milestoneLocks = new LinkedHashMap<>();
    public String partyAugmentId;
    public List<String> partyAugmentIds = new ArrayList<>();
    public Set<Integer> resolvedPartyAugmentMilestones = new LinkedHashSet<>();
    public Integer activePartyAugmentMilestone;
    public Map<String, Integer> partyAugmentVotes = new LinkedHashMap<>();
    public boolean craftUnlocked;
    public boolean sharedLedgerUnlocked;
    public BossState boss;
    public FacilityState facility;
    public Map<String, FacilityInstanceState> facilities = new LinkedHashMap<>();
    public Set<String> facilityTypesEverActivated = new LinkedHashSet<>();
    public Map<String, Integer> facilityRecoveryLedger = new LinkedHashMap<>();
    public Map<String, LootTransactionState> lootTransactions = new LinkedHashMap<>();
    public Map<String, Integer> lootPityCounters = new LinkedHashMap<>();
    public Map<String, RemainsState> remains = new LinkedHashMap<>();
    public DayState seasonDay = new DayState();
    public Map<String, EncounterState> encounters = new LinkedHashMap<>();
    public Map<String, ResearchNodeState> researchNodes = new LinkedHashMap<>();
    public Map<String, DiscoveryNodeState> discoveryNodes = new LinkedHashMap<>();
    public StoryState story = new StoryState();
    public Set<String> defeatedBossIds = new LinkedHashSet<>();
    public Set<String> reconstructionPartIds = new LinkedHashSet<>();
    public Set<String> discoveryIds = new LinkedHashSet<>();
    public FinalState finalObjective = new FinalState();
    public TestState test;
    public String endReason;

    public static final class PlayerState {
        public String uuid;
        public String lastKnownName;
        public String lifeState = "ACTIVE";
        public int level = 1;
        public int exp = 0;
        public double ap = 100.0;
        public int maxAp = 100;
        public int equipmentMaxApBonus;
        public long apRegenBlockedUntilEpochMs;
        public long lastDamageAtEpochMs;
        public String mainWeaponId;
        public String mainWeaponInstanceId;
        public String offhandId;
        public String offhandInstanceId;
        public Map<String, String> equippedTemplateBySlot = new LinkedHashMap<>();
        public Map<String, String> equippedInstanceBySlot = new LinkedHashMap<>();
        public Set<String> ownedEquipment = new LinkedHashSet<>();
        public Map<String, EquipmentInstanceState> equipmentInstances = new LinkedHashMap<>();
        public Map<String, Integer> quickItems = new LinkedHashMap<>();
        public Map<Integer, String> quickBindings = new LinkedHashMap<>();
        public Map<String, Integer> quickItemUsesByDay = new LinkedHashMap<>();
        public Map<String, Integer> quickItemUsesByCombat = new LinkedHashMap<>();
        public Map<String, Integer> ammoLedger = new LinkedHashMap<>();
        public long personalCombatSequence;
        public long personalCombatScopeExpiresAtEpochMs;
        public int apStimPulsesRemaining;
        public int apStimTicksUntilNextPulse;
        public int rescueBraceCharges;
        public double rescueInterruptThresholdBonus;
        public Map<String, Map<Integer, String>> weaponSkillLoadouts = new LinkedHashMap<>();
        public Map<Integer, String> commonSkillLoadout = new LinkedHashMap<>();
        public boolean productionWeaponSkillLoadoutsInitialized;
        public boolean productionCommonSkillLoadoutInitialized;
        public Set<String> discoveredItemIds = new LinkedHashSet<>();
        public Map<String, Integer> pendingRegisteredItems = new LinkedHashMap<>();
        public List<String> pendingEquipmentRewards = new ArrayList<>();
        public Set<String> pendingBlueprintUnlocks = new LinkedHashSet<>();
        public int lootValueReceived;
        public Set<String> completedTutorialQuests = new LinkedHashSet<>();
        public Set<String> tutorialSignals = new LinkedHashSet<>();
        public Map<String, Integer> investedStats = new LinkedHashMap<>();
        public boolean damageNumbersEnabled = true;
        public boolean detailedTooltips = false;
        public List<String> personalAugments = new ArrayList<>();
        public Set<Integer> resolvedPersonalMilestones = new LinkedHashSet<>();
        public String tridentState = "HELD";
        public int crossbowLoadedAmmo;
        public String tridentEntityUuid;
        public String tridentWorld;
        public double tridentX;
        public double tridentY;
        public double tridentZ;
        public long tridentThrownAtEpochMs;
        public long downedAtEpochMs;
        public long downedGraceUntilEpochMs;
        public double downedHp;
        public double downedMaxHp;
        public int injuryStacks;
        public double reviveProgress;
        public long reviveLastContributionAtEpochMs;
        public Map<String, Double> reviveContributions = new LinkedHashMap<>();
        public long reviveProtectionUntilEpochMs;
        public long reviveTailProtectionUntilEpochMs;
        public long helpSignalCooldownUntilEpochMs;
        public String lastSafeWorld;
        public double lastSafeX;
        public double lastSafeY;
        public double lastSafeZ;
        public long lastSafeAtEpochMs;
        public boolean voidRescueUsed;
        public int deathCount;
        public String remainsId;
        public DeathRecordState death;
        public Map<String, PendingRemainsDeliveryState> pendingRemainsDeliveries = new LinkedHashMap<>();
        public int resurrectionUsedCount;
        public String pendingResurrectionType;
        public double testDamageDealtMultiplier = 1.0;
        public double testBreakMultiplier = 1.0;
        public double testDamageTakenMultiplier = 1.0;
        public double testDamageReductionRate = 0.0;
        public double testCooldownMultiplier = 1.0;
        public double testApCostMultiplier = 1.0;
        public double testApRegenMultiplier = 1.0;
        public double testMoveSpeedMultiplier = 1.0;
        public double testMaxHealth = 20.0;
        public int testBaseMaxAp = 100;
        public boolean testInvulnerable;
        public String world;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
    }

    public static final class EquipmentInstanceState {
        public String instanceId;
        public String templateId;
        public int currentDurability;
        public int maxDurability;
        public String condition = "ACTIVE";
        public int breakCount;
    }

    public static final class DeathRecordState {
        public int day;
        public String cause;
        public String world;
        public double x;
        public double y;
        public double z;
        public long occurredAtEpochMs;
    }

    public static final class RemainsState {
        public String remainsId;
        public String ownerUuid;
        public String ownerName;
        public int day;
        public String state = "AVAILABLE";
        public String world;
        public double x;
        public double y;
        public double z;
        public long createdAtEpochMs;
        public int lostConsumableCount;
        public String markerEntityUuid;
        public Map<String, RemainsItemState> contents = new LinkedHashMap<>();
        public Map<String, EquipmentInstanceState> equipmentInstances = new LinkedHashMap<>();
    }

    public static final class RemainsItemState {
        public String entryId;
        public String encodedItem;
        public String equipmentInstanceId;
        public String state = "AVAILABLE";
        public String claimedBy;
        public long claimedAtEpochMs;
    }

    public static final class PendingRemainsDeliveryState {
        public String deliveryId;
        public String remainsId;
        public String entryId;
        public String encodedItem;
        public long claimedAtEpochMs;
    }

    public static final class MilestoneLock {
        public String milestone;
        public String tier;
        public String firstPlayer;
        public long drawSeed;
        public String drawRevision = "prototype-draw-r1";
    }

    public static final class OutboxEvent {
        public String eventId;
        public String type;
        public String payload;
        public long createdAtEpochMs;
        public boolean delivered;
    }

    public static final class BossState {
        public String bossId;
        public String entityUuid;
        public String world;
        public double x;
        public double y;
        public double z;
        public String state = "ACTIVE";
        public int phase = 1;
        public double hp;
        public double maxHp;
        public double breakCurrent;
        public double breakMax;
        public boolean phaseTwoChannelResolved;
        public Set<String> channelParticipants = new LinkedHashSet<>();
        public boolean rewardCommitted;
        public long patternSequence;
    }

    public static final class FacilityState {
        public String id;
        public String world;
        public int x;
        public int y;
        public int z;
        public boolean active;
    }

    public static final class FacilityInstanceState {
        public String instanceId;
        /** Stable identity of the portable device item or deployment that owns this runtime instance. */
        public String portableInstanceId;
        public String facilityType;
        public String revision = "facility-data-d11-d50-r1";
        public int level = 1;
        public String state = "ACTIVE";
        public double hp;
        public double maxHp;
        public String networkId;
        public String world;
        public int x;
        public int y;
        public int z;
        public String coreMaterial;
        public String installedBy;
        public long installedAtEpochMs;
        public long lastUsedAtEpochMs;
        public long expiresAtEpochMs;
        public int triggerCharges;
        public List<FacilityWorkState> queue = new ArrayList<>();
        public Map<String, Integer> outputLedger = new LinkedHashMap<>();
        /** Slot-indexed Base64 Bukkit ItemStack payloads for facilities such as FAC-C03. */
        public Map<String, String> storageSlots = new LinkedHashMap<>();
    }

    public static final class FacilityWorkState {
        public String workId;
        public String operation;
        public String ownerUuid;
        public String state = "QUEUED";
        public long queuedAtEpochMs;
        public long processingStartedAtEpochMs;
        public long durationMillis;
        /** Active server time only. Offline wall-clock time must not advance facility work. */
        public long processedMillis;
        public Map<String, Integer> reservedInputs = new LinkedHashMap<>();
        public Map<String, Integer> outputs = new LinkedHashMap<>();
    }

    public static final class LootTransactionState {
        public String transactionId;
        public String sourceId;
        public String lootTableId;
        public String revision = "loot-s1-r1";
        public List<String> eligibleContributors = new ArrayList<>();
        public List<LootRollState> rolledEntries = new ArrayList<>();
        public long rolledAtEpochMs;
        public boolean claimed;
    }

    public static final class LootRollState {
        public String kind;
        public String itemId;
        public int amount;
        public String ownerUuid;
        public boolean queued;
    }

    public static final class DayState {
        public String dayId = "DAY-01";
        public int day = 1;
        public String state = "PREPARING";
        public String lockedBudgetProfileId = "STD-BALANCED";
        public int lockedThreatBudget3;
        public List<Integer> lockedResourceBudgets = new ArrayList<>();
        public List<String> eventQueue = new ArrayList<>();
        public String activeEventId;
        public long startedAtEpochMs;
        public long pressureStartedAtEpochMs;
        public long resolvedAtEpochMs;
        public long sequence;
        public boolean progressionExpCommitted;
        public boolean activityExpCommitted;
    }

    public static final class EncounterState {
        public String encounterId;
        public String eventId;
        public int day;
        public String executionOpcode;
        public String state = "QUEUED";
        public String budgetProfileId;
        public int threatBudget;
        public int waveIndex;
        public int waveCount;
        public List<String> plannedEnemyIds = new ArrayList<>();
        public int nextEnemyIndex;
        public Set<String> spawnedEntityUuids = new LinkedHashSet<>();
        public Set<String> participantUuids = new LinkedHashSet<>();
        public long startedAtEpochMs;
        public long resolvedAtEpochMs;
        public boolean rewardCommitted;
        public String failureReason;
    }

    public static final class ResearchNodeState {
        public String researchId;
        public String state = "HIDDEN";
        public String startedByUuid;
        public long startedAtEpochMs;
        public long completesAtEpochMs;
        public long completedAtEpochMs;
        public Map<String, Integer> reservedCost = new LinkedHashMap<>();
        public boolean unlockCommitted;
    }

    public static final class DiscoveryNodeState {
        public String discoveryId;
        public String state = "HIDDEN";
        public int hintLevel;
        public Set<String> evidence = new LinkedHashSet<>();
        public String firstCluePlayerUuid;
        public long firstClueAtEpochMs;
        public long discoveredAtEpochMs;
        public long masteredAtEpochMs;
        public boolean unlockCommitted;
    }

    public static final class StoryState {
        public Set<String> queuedSceneIds = new LinkedHashSet<>();
        public Set<String> playedSceneIds = new LinkedHashSet<>();
        public Set<String> unlockedLogIds = new LinkedHashSet<>();
        public String activeSceneId;
        public long sequence;
    }

    public static final class FinalState {
        public String objectiveId = "FINAL-D50-FIRST-RECONSTRUCTION-SIGNAL";
        public String state = "LOCKED";
        public int stage;
        public Map<String, Integer> componentProgress = new LinkedHashMap<>();
        public Set<String> activeEntityUuids = new LinkedHashSet<>();
        public Set<String> completedTransactionSteps = new LinkedHashSet<>();
        public long activatedAtEpochMs;
        public long resolvedAtEpochMs;
        public boolean uniqueInputReserved;
        public boolean completionCommitted;
        public String failureReason;
        public int lockedPartySize;
        public Set<String> activationVotes = new LinkedHashSet<>();
        public long activationVoteStartedAtEpochMs;
        public String uniqueInputOwnerUuid;
        public String arenaManifestId;
        public String arenaWorld;
        public double arenaX;
        public double arenaY;
        public double arenaZ;
        public List<Integer> stage1WaveBudgets = new ArrayList<>();
        public int stage1WaveIndex;
        public String finalBossEntityUuid;
        public double finalBossHp;
        public double finalBossMaxHp;
        public double finalBossBreakMax;
        public int finalBossPhase;
        public long finalBossPatternSequence;
        public long nextPatternAtTick;
        public boolean coreSubdued;
        public double purificationTicks;
        public int purificationCheckpointSeconds;
        public Set<String> confirmationUuids = new LinkedHashSet<>();
        public long confirmationOpenedAtEpochMs;
    }

    public static final class TestState {
        public String ownerUuid;
        public int virtualPartySize = 1;
        public long deterministicSeed;
        public boolean timeFrozen = true;
        public double timeScale = 1.0;
        public long logicalNowEpochMs;
        public long logicalTick;
        public long snapshotSequence;
        public String activePreset = "DEFAULT";
        public String activeScenario = "SANDBOX";
        public boolean restorePending;
        public Map<String, Integer> virtualContributions = new LinkedHashMap<>();
    }
}
