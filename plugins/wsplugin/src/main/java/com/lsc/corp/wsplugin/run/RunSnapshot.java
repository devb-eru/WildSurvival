package com.lsc.corp.wsplugin.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RunSnapshot {
    public int schemaVersion = 1;
    public long version = 0;
    public String runId;
    public String contentRevision;
    public String runType = "PROTOTYPE";
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
    }

    public static final class FacilityWorkState {
        public String workId;
        public String operation;
        public String ownerUuid;
        public String state = "QUEUED";
        public long queuedAtEpochMs;
        public long processingStartedAtEpochMs;
        public long durationMillis;
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
