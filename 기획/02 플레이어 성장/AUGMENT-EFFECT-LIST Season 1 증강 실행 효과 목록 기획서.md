# WildSurvival Season 1 증강 실행 효과 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `AUGMENT-EFFECT-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 적용 범위 | 개인 증강 50개·파티 증강 16개의 고유 effect opcode, 명시 trigger와 상태 범위 |
| 상위 기준 | `AUG-LIST-001`, `AUG-LIST-002`, `PARTY-SYNERGY-001` |
| 데이터 리비전 | `augment-effect-s1-r2` |
| 최종 수정일 | 2026-08-24 |

## 1. 실행 규칙

- `effectOpcode`는 첫 태그나 표시명에서 추론하지 않는다. 아래 ID별 opcode가 유일한 실행 디스패치 키다.
- `triggerIds`는 구독 후보의 완전한 목록이다. 런타임은 등록되지 않은 이벤트에서 효과를 발동하지 않는다.
- `stateScope`는 ICD·스택·예약 상태의 저장 키 범위다. `PLAYER_TARGET`과 `PARTY_TARGET`에는 target instance ID가 포함된다.
- 모든 파생 이벤트는 `sourceAugmentId, rootEventId, effectChainId`를 보존하고 `NO_AUGMENT_RETRIGGER, NO_REWARD, NO_CONTRIBUTION`을 가진다.
- 정확한 수치·ICD·상한·후보 제한은 `AUG-LIST-001~002`의 같은 ID 행이 권위다. 이 문서의 opcode가 수치를 암시하거나 덮어쓰지 않는다.

## 2. SILVER 개인 증강 18개

| ID | effectOpcode | triggerIds | stateScope |
|---|---|---|---|
| `AUG-S-001` | `DODGE_AP_COST_FLAT_REDUCE` | `DODGE_COST_QUERY` | `PLAYER` |
| `AUG-S-002` | `AP_REGEN_AFTER_DAMAGE_FREE_WINDOW` | `DAMAGE_TAKEN_COMMITTED`, `COMBAT_TICK` | `PLAYER_ENCOUNTER` |
| `AUG-S-003` | `BLEED_DURATION_REDUCE_AND_CLEAR_HEAL` | `STATUS_DURATION_QUERY`, `STATUS_CLEARED` | `PLAYER` |
| `AUG-S-004` | `CORRUPTION_GAIN_REDUCE_AND_PURIFY_SCALE` | `CORRUPTION_GAIN_QUERY`, `CONSUMABLE_EFFECT_QUERY` | `PLAYER` |
| `AUG-S-005` | `GUARD_IMPACT_REDUCE_AND_FAILED_PARRY_SHORT_GUARD_EXTEND` | `GUARD_RESOLVED`, `PARRY_RESOLVED`, `SHORT_GUARD_DURATION_QUERY` | `PLAYER_ACTION` |
| `AUG-S-006` | `TARGET_HIT_STREAK_BREAK_WINDOW` | `COMBAT_HIT_COMMITTED` | `PLAYER_TARGET` |
| `AUG-S-007` | `COMBO_FINISHER_NEXT_BASIC_HIT_BUFF` | `COMBO_FINISHER_HIT`, `BASIC_ATTACK_RESOLVED` | `PLAYER` |
| `AUG-S-008` | `NORMAL_AMMO_CONSERVE_ROLL` | `AMMO_CONSUME_REQUESTED` | `PLAYER_CAST` |
| `AUG-S-009` | `RELOAD_TIME_AND_MOVE_PENALTY_REDUCE` | `RELOAD_PROFILE_QUERY` | `PLAYER` |
| `AUG-S-010` | `PERSISTENT_AREA_END_AP_RESTORE` | `AREA_EFFECT_ENDED` | `PLAYER_AREA_INSTANCE` |
| `AUG-S-011` | `SHIELD_DEPLETED_NEXT_DAMAGE_REDUCE` | `SHIELD_DEPLETED`, `DAMAGE_TAKEN_COMMITTED` | `PLAYER` |
| `AUG-S-012` | `PRECISION_DODGE_NEXT_ATTACK_BUFF` | `PRECISION_DODGE_COMMITTED`, `COMBAT_HIT_COMMITTED` | `PLAYER` |
| `AUG-S-013` | `DOT_TARGET_HIT_AND_BREAK_BONUS` | `COMBAT_HIT_QUERY` | `PLAYER_TARGET` |
| `AUG-S-014` | `RESCUE_TIME_REDUCE_AND_COMPLETE_AP` | `RESCUE_PROFILE_QUERY`, `RESCUE_COMPLETED` | `PLAYER_RESCUE` |
| `AUG-S-015` | `EMERGENCY_REPAIR_COST_AND_INTERRUPT_BONUS` | `REPAIR_COST_QUERY`, `REPAIR_INTERRUPT_QUERY` | `PLAYER_REPAIR` |
| `AUG-S-016` | `FIRST_PATTERN_RESPONSE_BREAK_EXPOSE` | `PATTERN_RESPONSE_COMMITTED` | `PLAYER_TARGET_PATTERN` |
| `AUG-S-017` | `HARD_CC_DURATION_AND_FORCED_MOVE_REDUCE` | `STATUS_DURATION_QUERY`, `FORCED_MOVE_QUERY` | `PLAYER` |
| `AUG-S-018` | `PORTABLE_CRAFT_CONSERVE_ROLL` | `CRAFT_COST_COMMITTING` | `PLAYER_CRAFT_TX` |

## 3. GOLD 개인 증강 18개

| ID | effectOpcode | triggerIds | stateScope |
|---|---|---|---|
| `AUG-G-001` | `PRECISION_DODGE_DAMAGE_AND_BREAK_COUNTER` | `PRECISION_DODGE_COMMITTED`, `COMBAT_HIT_COMMITTED` | `PLAYER` |
| `AUG-G-002` | `DODGE_AP_REFUND_WITH_DECAY` | `DODGE_COMMITTED` | `PLAYER_ENCOUNTER` |
| `AUG-G-003` | `PARRY_BREAK_AND_NEXT_SKILL_AP_DISCOUNT` | `PARRY_COMMITTED`, `SKILL_COST_QUERY` | `PLAYER_TARGET` |
| `AUG-G-004` | `BREAK_SUCCESS_TARGET_VULNERABILITY` | `BREAK_SUCCESS_COMMITTED` | `PLAYER_TARGET` |
| `AUG-G-005` | `TWO_DEBUFF_DURATION_CONSUME_DAMAGE` | `COMBAT_HIT_COMMITTED`, `STATUS_DURATION_COMMITTING` | `PLAYER_TARGET` |
| `AUG-G-006` | `MAX_BLEED_BASIC_FINISHER_DAMAGE` | `BASIC_COMBO_FINISHER_HIT` | `PLAYER_TARGET` |
| `AUG-G-007` | `POISON_HP_DAMAGE_AP_RESTORE` | `STATUS_DAMAGE_COMMITTED` | `PLAYER_SECOND` |
| `AUG-G-008` | `BURN_THREE_STACK_AREA_SPREAD` | `STATUS_STACK_CHANGED` | `PLAYER_TARGET` |
| `AUG-G-009` | `SPECIAL_AMMO_STATUS_DAMAGE_TRADE` | `AMMO_EFFECT_QUERY`, `PROJECTILE_DAMAGE_QUERY` | `PLAYER_PROJECTILE` |
| `AUG-G-010` | `PROJECTILE_PENETRATION_FALLOFF_REDUCE` | `PROJECTILE_PENETRATION_HIT` | `PLAYER_PROJECTILE` |
| `AUG-G-011` | `HIGH_AP_MULTI_TARGET_REFUND` | `SKILL_HIT_COMMITTED` | `PLAYER_CAST` |
| `AUG-G-012` | `HEAVY_BREAK_AREA_SPLASH` | `BREAK_DAMAGE_COMMITTED` | `PLAYER_TARGET` |
| `AUG-G-013` | `LOW_HP_EMERGENCY_SHIELD` | `HP_THRESHOLD_ENTERED` | `PLAYER` |
| `AUG-G-014` | `CORRUPTION_STAGE_DOWN_AP_AND_RESIST` | `CORRUPTION_STAGE_CHANGED` | `PLAYER` |
| `AUG-G-015` | `RESCUE_COMPLETE_NEARBY_PARTY_HEAL` | `RESCUE_COMPLETED` | `PLAYER_RESCUE` |
| `AUG-G-016` | `LOADOUT_TAG_ADAPT_ATTACK_OR_DEFENCE` | `LOADOUT_REEVALUATED` | `PLAYER` |
| `AUG-G-017` | `PATTERN_RESPONSE_EARLY_TELEGRAPH` | `PATTERN_RESPONSE_COMMITTED`, `TELEGRAPH_PRESENTING` | `PLAYER_PATTERN` |
| `AUG-G-018` | `HARD_CC_END_AP_AND_DODGE_DISCOUNT` | `HARD_CC_ENDED`, `DODGE_COST_QUERY` | `PLAYER` |

## 4. PRISM 개인 증강 14개

| ID | effectOpcode | triggerIds | stateScope |
|---|---|---|---|
| `AUG-P-001` | `LOW_HP_DAMAGE_BREAK_AND_HEALING_TRADE` | `HP_THRESHOLD_CHANGED`, `DAMAGE_OUTPUT_QUERY`, `HEALING_RECEIVED_QUERY` | `PLAYER` |
| `AUG-P-002` | `TWO_PRECISION_DODGES_NEXT_HIT_REDUCE` | `PRECISION_DODGE_COMMITTED`, `DAMAGE_TAKEN_COMMITTED` | `PLAYER` |
| `AUG-P-003` | `BOSS_PARRY_BREAK_AND_PARTY_AP` | `PARRY_COMMITTED` | `PLAYER_TARGET_PATTERN` |
| `AUG-P-004` | `BREAK_REVEAL_WEAKNESS_AND_PERSONAL_DAMAGE` | `BREAK_SUCCESS_COMMITTED` | `PLAYER_TARGET` |
| `AUG-P-005` | `POISON_CAP_DAMAGE_AND_PURIFY_TRADE` | `STATUS_CAP_QUERY`, `STATUS_DAMAGE_QUERY`, `PURIFY_EFFECT_QUERY` | `PLAYER` |
| `AUG-P-006` | `BLEED_LIFESTEAL_AND_OVERHEAL_SHIELD` | `STATUS_DAMAGE_COMMITTED` | `PLAYER_SECOND` |
| `AUG-P-007` | `TWO_ELEMENT_STATUS_FUSION` | `STATUS_APPLIED` | `PLAYER_TARGET` |
| `AUG-P-008` | `AMMO_CONSERVE_AND_ACTION_COST_TRADE` | `AMMO_CONSUME_REQUESTED`, `RELOAD_COST_QUERY`, `SHOT_COST_QUERY` | `PLAYER_CAST` |
| `AUG-P-009` | `TRIDENT_RECALL_PATH_REHIT` | `TRIDENT_RECALL_HIT` | `PLAYER_TARGET_CAST` |
| `AUG-P-010` | `UNARMED_COUNTER_DEFENCE_IGNORE_AND_BREAK` | `UNARMED_COUNTER_HIT` | `PLAYER_TARGET` |
| `AUG-P-011` | `SHORT_GUARD_PROJECTILE_PARTY_AURA_WITH_PARRY_SURCHARGE` | `SHORT_GUARD_STARTED`, `PROJECTILE_DAMAGE_QUERY`, `MOVEMENT_QUERY`, `PARRY_COST_QUERY` | `PLAYER_ACTION` |
| `AUG-P-012` | `CORRUPTION_STAGE_OFFENCE_AND_PURIFY_TRADE` | `CORRUPTION_STAGE_CHANGED`, `DAMAGE_OUTPUT_QUERY`, `PURIFY_EFFECT_QUERY` | `PLAYER` |
| `AUG-P-013` | `HIGH_AP_HIT_REDUCE_OTHER_COOLDOWNS` | `SKILL_HIT_COMMITTED`, `COOLDOWN_COMMITTING` | `PLAYER_CAST` |
| `AUG-P-014` | `OTHER_RESCUE_START_DAMAGE_REDUCE_AND_AP_ZERO` | `RESCUE_STARTED`, `RESCUE_COMPLETED`, `DAMAGE_TAKEN_QUERY` | `PLAYER_RESCUE` |

## 5. 파티 증강 16개

| ID | effectOpcode | triggerIds | stateScope |
|---|---|---|---|
| `PAUG-001` | `FORMATION_MOBILITY_AND_DODGE_COST` | `PARTY_FORMATION_CHANGED`, `MOVEMENT_QUERY`, `DODGE_COST_QUERY` | `PARTY_PLAYER` |
| `PAUG-002` | `ALTERNATING_GUARD_PARTY_DAMAGE_WINDOW` | `GUARD_RESOLVED`, `PARRY_RESOLVED`, `COMBAT_HIT_QUERY` | `PARTY_PLAYER` |
| `PAUG-003` | `MULTI_CONTRIBUTOR_TARGET_BREAK_WINDOW` | `COMBAT_HIT_COMMITTED` | `PARTY_TARGET` |
| `PAUG-004` | `RESCUE_COMPLETE_PARTY_RECOVERY` | `RESCUE_COMPLETED` | `PARTY_RESCUE` |
| `PAUG-005` | `MULTI_CATEGORY_COMMON_RESOURCE_BONUS` | `DAY_RESOURCE_SETTLEMENT_COMMITTING` | `PARTY_DAY` |
| `PAUG-006` | `THREE_STATUS_PARTY_DAMAGE_WINDOW` | `STATUS_APPLIED`, `STATUS_DAMAGE_QUERY` | `PARTY_TARGET` |
| `PAUG-007` | `PRESSURE_MODE_ADAPT_DAMAGE_REDUCTION` | `PRESSURE_MODE_CHANGED`, `DAMAGE_TAKEN_QUERY`, `FACILITY_DAMAGE_QUERY` | `PARTY` |
| `PAUG-008` | `PURIFY_HIGHEST_CORRUPTION_MEMBER` | `PLAYER_PURIFY_COMMITTED` | `PARTY` |
| `PAUG-009` | `AMMO_CRAFT_OUTPUT_AND_COST_MODIFIERS` | `CRAFT_OUTPUT_QUERY`, `CRAFT_COST_QUERY` | `PARTY_CRAFT_TX` |
| `PAUG-010` | `LOW_AP_MEMBER_OTHER_REGEN` | `PARTY_AP_STATE_CHANGED`, `AP_REGEN_QUERY` | `PARTY` |
| `PAUG-011` | `PATTERN_RESPONSE_HANDOFF_REDUCTION` | `PATTERN_RESPONSE_COMMITTED`, `PATTERN_DAMAGE_QUERY` | `PARTY_PATTERN` |
| `PAUG-012` | `LAST_MAJOR_ENEMY_BREAK_AND_CORRUPTION_REDUCE` | `ENCOUNTER_MAJOR_COUNT_CHANGED`, `BREAK_MAX_QUERY`, `CORRUPTION_GAIN_QUERY` | `PARTY_ENCOUNTER` |
| `PAUG-013` | `FIRST_BOSS_EQUIPMENT_COMMON_COST_DISCOUNT` | `CRAFT_COST_QUERY`, `CRAFT_COMMITTED` | `PARTY_TEMPLATE` |
| `PAUG-014` | `CONTRIBUTION_RECONSTRUCTION_SPEED` | `CONTRIBUTION_CATEGORY_COMMITTED`, `FACILITY_WORK_SPEED_QUERY` | `PARTY_DAY` |
| `PAUG-015` | `HALF_SURVIVORS_MAX_HP_AND_AP_REGEN` | `LIFE_STATE_CHANGED`, `MAX_HP_QUERY`, `AP_REGEN_QUERY` | `PARTY` |
| `PAUG-016` | `SPLIT_FRONT_PARTY_DAMAGE` | `PARTY_FORMATION_CHANGED`, `DAMAGE_OUTPUT_QUERY` | `PARTY_ENCOUNTER` |

## 6. 합계·검증

| 범위 | ID | 고유 opcode | 최소 trigger | 상태 범위 |
|---|---:|---:|---:|---:|
| SILVER | 18 | 18 | 18 | 18 |
| GOLD | 18 | 18 | 18 | 18 |
| PRISM | 14 | 14 | 14 | 14 |
| PARTY | 16 | 16 | 16 | 16 |
| 전체 | 66 | 66 | 66 | 66 |

L0는 ID·opcode 중복 0, 빈 trigger/stateScope 0, `effectOpcode == 첫 태그` 0을 검사한다. L1은 ID별 정상 발동·무효 조건·ICD·상한을 검사하고, L2는 같은 root event의 재귀 발동·보상 생성·재접속 ICD 초기화를 0으로 증명한다. 66개 중 하나라도 범용 태그 디스패치나 설명문 출력만 남으면 증강 도메인을 `VERIFIED`로 승격하지 않는다.

## 7. 명시 효과 파라미터 66행

아래 `parameterPayload`와 `limitFallback`은 opcode가 읽는 유일한 수치 입력이다. 시간은 tick, 거리는 block, 비율은 소수다. 상태 저장 키는 §2~5의 `stateScope + sourceAugmentId + run/player/party/target/cast/action instance` 조합이며 설명문에서 숫자를 추출하지 않는다.

### 7.1 SILVER 18행

| ID | parameterPayload | limitFallback |
|---|---|---|
| `AUG-S-001` | `dodgeApFlat=-4` | `finalCostMin=COMBAT_DODGE_COST_FLOOR` |
| `AUG-S-002` | `damageFreeTicks=80; apRegenPerSecond=3` | `combatAllowed=true; resetOnValidDamage=true` |
| `AUG-S-003` | `bleedDurationMultiplier=0.75; clearHealMaxHp=0.02` | `icdTicks=160; nonBleedClear=false` |
| `AUG-S-004` | `corruptionGainMultiplier=0.90; purifyConsumableMultiplier=1.10` | `corruptionFloor=0; statusCleanseBonus=0` |
| `AUG-S-005` | `guardImpactApMultiplier=0.88; failedParryShortGuardExtendTicks=2` | `perActionMax=1` |
| `AUG-S-006` | `requiredHits=3; hitWindowTicks=80; breakMultiplier=1.12; buffTicks=80` | `targetIcdTicks=120; noBreakTarget=INELIGIBLE` |
| `AUG-S-007` | `nextBasicHitFlat=12; expiryTicks=80` | `stacksMax=1; comboWeaponsWeighted=true` |
| `AUG-S-008` | `normalAmmoConserveChance=0.15` | `perCastRollMax=1; specialAmmo=false` |
| `AUG-S-009` | `reloadTimeMultiplier=0.90; reloadMovePenaltyMultiplier=0.90` | `reloadFloor=WEAPON_PROFILE_FLOOR; crossbowWeighted=true` |
| `AUG-S-010` | `areaEndApMaxRatio=0.04` | `perAreaMax=1; icdTicks=120; staffWeighted=true` |
| `AUG-S-011` | `nextDamageTakenMultiplier=0.90; expiryTicks=120` | `stacksMax=1; shieldSourceRequired=true` |
| `AUG-S-012` | `nextAttackDamageMultiplier=1.08; expiryTicks=40` | `perPrecisionDodgeMax=1` |
| `AUG-S-013` | `dotTargetHitFlat=8; dotTargetBreakMultiplier=1.05` | `requiresAnyDot=true` |
| `AUG-S-014` | `rescueTimeMultiplier=0.90; rescuerAp=5; targetAp=5` | `perRescueMax=1` |
| `AUG-S-015` | `emergencyRepairCommonCostMultiplier=0.92; interruptThresholdMultiplier=1.10` | `repairOnly=true` |
| `AUG-S-016` | `targetBreakTakenMultiplier=1.06; durationTicks=100` | `perTargetPatternMax=1; eliteOrBossOnly=true` |
| `AUG-S-017` | `hardCcDurationMultiplier=0.90; forcedMoveStrengthMultiplier=0.92` | `statusResistanceHardCap=true` |
| `AUG-S-018` | `portableCommonMaterialConserveChance=0.08` | `perWorkRollMax=1; combatReward=false` |

### 7.2 GOLD 18행

| ID | parameterPayload | limitFallback |
|---|---|---|
| `AUG-G-001` | `nextAttackDamageMultiplier=1.18; nextAttackBreakMultiplier=1.15; expiryTicks=80` | `perPrecisionDodgeMax=1; exclusive=AUG-G-002` |
| `AUG-G-002` | `refundRatio=0.30; consecutiveDecayMultiplier=0.50; chainWindowTicks=120` | `chainMax=3; exclusive=AUG-G-001` |
| `AUG-G-003` | `parryBreakMultiplier=1.20; nextSkillApFlat=-8; expiryTicks=100` | `perParryMax=1; defenceEquipmentWeighted=true` |
| `AUG-G-004` | `targetDamageTakenMultiplier=1.08; bossMultiplier=1.05; durationTicks=120` | `targetIcdTicks=240` |
| `AUG-G-005` | `distinctWeakStatuses=2; consumedDurationTicks=20; extraDamageCoeff=0.35` | `targetIcdTicks=80; hardCcConsumable=false` |
| `AUG-G-006` | `maxBleedBasicFinisherMultiplier=1.22` | `perBasicExecutionMax=1; maxBleedRequired=true` |
| `AUG-G-007` | `apPerPoisonHpTick=1` | `apPerSecondMax=4; noRewardTarget=true` |
| `AUG-G-008` | `triggerBurnStacks=3; spreadRadius=3; spreadStacks=1` | `icdTicks=100; summonAndTrainingExcluded=true` |
| `AUG-G-009` | `specialAmmoStatusHitFlat=12; projectileDamageMultiplier=0.95` | `ammoWeaponRequired=true` |
| `AUG-G-010` | `postPenetrationFalloffRelief=0.20` | `perProjectileMax=1; projectileWeaponWeighted=true` |
| `AUG-G-011` | `skillApThreshold=30; uniqueTargetMinimum=2; apRefund=8` | `perCastMax=1; icdTicks=80; staffWeighted=true` |
| `AUG-G-012` | `singleHitBreakMinimum=20; splashRadius=3; splashBreakRatio=0.25` | `icdTicks=100; heavyWeaponWeighted=true` |
| `AUG-G-013` | `hpThreshold=0.30; shieldMaxHp=0.12; shieldTicks=100` | `icdTicks=700; stacksMax=1` |
| `AUG-G-014` | `stageDownAp=20; nextStatusResistanceFlat=15; buffExpiryTicks=300` | `icdTicks=300` |
| `AUG-G-015` | `partyRadius=5; healMaxHp=0.05` | `perRescueMax=1; selfRescueExcluded=true` |
| `AUG-G-016` | `matchingAugmentMinimum=2; attackMultiplier=1.07; fallbackDefenceFlat=7; reevaluateTicks=40` | `singleSource=true` |
| `AUG-G-017` | `telegraphAdvanceTicks=3` | `perPatternLearning=true; bossSafetyMinimum=true` |
| `AUG-G-018` | `hardCcEndAp=10; nextDodgeApFlat=-5; expiryTicks=160` | `icdTicks=160; immuneCcDoesNotTrigger=true` |

### 7.3 PRISM 14행

| ID | parameterPayload | limitFallback |
|---|---|---|
| `AUG-P-001` | `hpThreshold=0.40; damageMultiplier=1.22; breakMultiplier=1.15; healingReceivedMultiplier=0.80` | `exclusive=AUG-P-002` |
| `AUG-P-002` | `precisionDodgesRequired=2; nextDamageTakenMultiplier=0.30; outgoingDamageMultiplier=0.92; expiryTicks=240` | `stacksMax=1; exclusive=AUG-P-001` |
| `AUG-P-003` | `bossParryBreakMultiplier=1.35; partyAp=5; partyRadius=32` | `patternIcdTicks=200; sameExecutionMax=1; rewardAndFriendlyExcluded=true` |
| `AUG-P-004` | `weaknessTagsRevealed=1; durationTicks=160; personalDamageMultiplier=1.16; bossDamageMultiplier=1.10` | `targetMax=1; evolutionFamily=F01` |
| `AUG-P-005` | `poisonMaxStacksDelta=2; poisonDamageMultiplier=1.25; ownPurifyMultiplier=0.85` | `poisonBuildWeighted=true` |
| `AUG-P-006` | `bleedHealRatio=0.08; overhealShieldTicks=80; areaHealMultiplier=0.50` | `healPerSecondMaxHp=0.02; globalLifestealCap=true` |
| `AUG-P-007` | `distinctElementStatuses=2; fusionDamageCoeff=0.65` | `targetIcdTicks=120; extraHardCc=false; evolutionFamily=ELEMENT` |
| `AUG-P-008` | `ammoConserveChanceDelta=0.45; reloadApMultiplier=1.20; shotApMultiplier=1.20` | `finalConserveChanceCap=0.75; ammoWeaponRequired=true` |
| `AUG-P-009` | `recallRehitDamageCoeff=0.60; recallApFlat=8` | `perTargetCastMax=1; tridentOnly=true; evolutionFamily=TRIDENT` |
| `AUG-P-010` | `counterDefenceIgnoreRatio=0.35; counterBreakMultiplier=2.00; nonCounterDamageMultiplier=0.92` | `unarmedOnly=true; evolutionFamily=UNARMED` |
| `AUG-P-011` | `rearPartyRadius=4; projectileDamageTakenMultiplier=0.75; guardMoveMultiplier=0.80; parryApFlat=5` | `offShieldRequired=true; shortGuardOnly=true` |
| `AUG-P-012` | `perCorruptionStageAttack=0.04; perStageStatusHit=0.04; maximumBonus=0.16; purifyReceivedMultiplier=0.75` | `stage5AdditionalBonus=0` |
| `AUG-P-013` | `skillApThreshold=50; otherCooldownReduceTicks=20; skillApFlat=15` | `icdTicks=160; selfCooldownExcluded=true` |
| `AUG-P-014` | `rescuerAndTargetDamageTakenMultiplier=0.50; completionSelfAp=0` | `icdTicks=900; selfRescueExcluded=true` |

### 7.4 PARTY 16행

| ID | parameterPayload | limitFallback |
|---|---|---|
| `PAUG-001` | `nearbyRange=12; membersRequired=2; moveMultiplier=1.06; dodgeApFlat=-3; soloMoveMultiplier=1.03` | `downedAndDeadExcluded=true` |
| `PAUG-002` | `handoffWindowTicks=60; otherPlayerDamageMultiplier=1.10` | `targetIcdTicks=80; samePlayer=false` |
| `PAUG-003` | `contributorsRequired=2; contributionWindowTicks=80; breakTakenMultiplier=1.12; bossMultiplier=1.08; durationTicks=100` | `targetIcdTicks=160` |
| `PAUG-004` | `allSurvivorAp=10; rescuerHealMaxHp=0.08; targetHealMaxHp=0.08` | `perRescueMax=1` |
| `PAUG-005` | `distinctContributorCategories=2; commonResourceOutputMultiplier=1.10` | `bossUniqueProfessionalReconstructionExcluded=true` |
| `PAUG-006` | `distinctWeakStatusCategories=3; statusDamageMultiplier=1.15; durationTicks=100` | `targetIcdTicks=200; hardCcExcluded=true` |
| `PAUG-007` | `trackingMoveDamageTakenMultiplier=0.94; facilitySiegeDamageTakenMultiplier=0.90` | `modeExclusive=true` |
| `PAUG-008` | `highestCorruptionExtraDelta=-1` | `icdTicks=160; consumableWorksWithoutFacility=true` |
| `PAUG-009` | `normalAmmoOutputMultiplier=1.20; specialAmmoCostMultiplier=0.90` | `uniqueAndBossAmmoExcluded=true` |
| `PAUG-010` | `lowApThreshold=0.20; otherSurvivorRegenPerSecond=2` | `lowMemberTargetMax=1; stacksMax=1` |
| `PAUG-011` | `handoffTicks=80; otherPlayerSamePatternDamageTakenMultiplier=0.88` | `patternIcdTicks=160; originalResponderExcluded=true` |
| `PAUG-012` | `lastMajorEnemyBreakMaxMultiplier=0.90; remainingCorruptionGainMultiplier=0.80` | `normalAssaultOnly=true` |
| `PAUG-013` | `firstBossEquipmentCommonCostMultiplier=0.85` | `perTemplateMax=1; specialAndBossMaterialExcluded=true` |
| `PAUG-014` | `distinctContributionCategories=3; reconstructionSpeedMultiplier=1.12; soloMultiplier=1.04` | `minimumDay=41` |
| `PAUG-015` | `survivorRatioThreshold=0.50; maxHpMultiplier=1.10; apRegenMultiplier=1.10` | `rewardMultiplier=1.0; reversibleAtSafeRecount=true` |
| `PAUG-016` | `minimumSeparation=10; outgoingDamageMultiplier=1.06` | `bothInCombat=true; maxMultiplier=1.06; forcedSplitPatternDisabled=true` |

## 8. 파라미터 완료 검증

- ID 66개가 §2~5 및 `AUG-LIST-001~002`와 1:1이고 `parameterPayload/limitFallback` 공백이 0이다.
- 확률은 `[0,1]`, multiplier는 양수, tick·거리·상한은 음수가 아니며 최종 전역 하드캡을 우회하지 않는다.
- ICD·스택·패턴 학습·대상별 상태는 `stateScope`에 맞춰 저장하고 재접속·서버 재시작으로 초기화하지 않는다.
- 모든 파생 피해·회복·자원 변화는 `NO_AUGMENT_RETRIGGER/NO_REWARD/NO_CONTRIBUTION`을 유지한다.
