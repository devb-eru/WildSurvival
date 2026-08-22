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
    public Map<String, Integer> partyAugmentVotes = new LinkedHashMap<>();
    public boolean craftUnlocked;
    public boolean sharedLedgerUnlocked;
    public BossState boss;
    public FacilityState facility;
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
        public long apRegenBlockedUntilEpochMs;
        public String mainWeaponId;
        public String offhandId;
        public Set<String> ownedEquipment = new LinkedHashSet<>();
        public Map<String, Integer> quickItems = new LinkedHashMap<>();
        public Map<Integer, String> quickBindings = new LinkedHashMap<>();
        public Map<String, Map<Integer, String>> weaponSkillLoadouts = new LinkedHashMap<>();
        public Set<String> discoveredItemIds = new LinkedHashSet<>();
        public Set<String> completedTutorialQuests = new LinkedHashSet<>();
        public Set<String> tutorialSignals = new LinkedHashSet<>();
        public Map<String, Integer> investedStats = new LinkedHashMap<>();
        public boolean damageNumbersEnabled = true;
        public boolean detailedTooltips = true;
        public List<String> personalAugments = new ArrayList<>();
        public Set<Integer> resolvedPersonalMilestones = new LinkedHashSet<>();
        public String tridentState = "HELD";
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
