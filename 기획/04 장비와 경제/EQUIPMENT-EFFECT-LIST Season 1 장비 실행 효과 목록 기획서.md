# WildSurvival Season 1 장비 실행 효과 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `EQUIPMENT-EFFECT-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 역할 | 등록 장비·도구 214개의 고유 효과를 설명문 파싱 없이 실행하는 프로필 원장 |
| ID 권위 | `TOOL-LIST-001`, `WEAPON-EQUIPMENT-LIST-001`, `ARMOR-ACCESSORY-LIST-001`, `UTILITY-TOOL-LIST-001` |
| 수치 권위 | `EQUIP-LIST-001`, `EQUIP-DATA-D20-001`, `EQUIP-DATA-D50-001` |
| 데이터 리비전 | `equipment-effects-s1-r1` |
| 최종 수정일 | 2026-08-24 |

## 1. 실행 계약

각 장비 템플릿은 `effectProfileId`를 정확히 하나 가진다. 프로필은 아래 구조로 생산 데이터에 전개한다.

```yaml
equipment-effect-profile:
  id: EQFX-EQD20-TR-R01
  trigger: RECALL_PATH_ALLY_PASS
  conditions: [OWNER_UNARMED_OR_MATCHING_WEAPON]
  target: PASSED_ALLY
  operations:
    - opcode: STAT_ADD
      stat: RES
      value: 6
      durationTicks: 80
  cooldown:
    scope: TARGET
    ticks: 200
  stackPolicy: HIGHEST_ONLY
```

- `trigger`, `conditions`, `target`, `operations`, `cooldown`, `stackPolicy`는 문자열 설명이 아니라 구조화 필드다.
- 표의 `selector`는 정규식 런타임 조회가 아니다. 빌드 시 `TOOL-LIST-001`의 실제 ID로 전개하고 각 템플릿에 프로필 ID를 기록한다.
- `STATIC_STATS_ONLY`는 `stats`, `baseTemplateId`, 강화·재련 연산만 사용하며 이벤트 리스너를 등록하지 않는다.
- 부모 템플릿의 고유 효과는 상속하지 않는다. 현재 템플릿의 프로필 하나만 활성화한다.
- 세트 프로필은 각 부위에 같은 `effectProfileId`를 기록하되 파티가 아니라 착용자별 `setId` 부위 수로 한 번만 집계한다.
- `UNARMED_SUPPORT`는 주무기와 실제 slot 0이 모두 비어 권투가 활성일 때만 효과를 낸다.
- `NO_RETRIGGER`는 동일 실행에서 해당 장비 효과가 만든 결과가 다시 같은 장비 효과를 호출하지 않는다는 뜻이다.

### 1.1 공통 연산 opcode

| opcode | 필수 매개변수 | 의미 |
|---|---|---|
| `STAT_ADD` | stat, value, duration | 스탯 가산 |
| `DAMAGE_MULTIPLY` | value, damageScope | 주는 피해 배율 |
| `DAMAGE_TAKEN_MULTIPLY` | value, damageScope | 받는 피해 배율 |
| `BREAK_MULTIPLY` | value, actionScope | 브레이크 배율 |
| `BREAK_ADD` | value, actionScope | 브레이크 고정 가산 |
| `PEN_ADD` | value | PEN 가산 |
| `AP_ADD` | value | 현재 AP 가감 |
| `AP_COST_ADD` | value, actionScope | 행동 AP 비용 가감 |
| `AP_REGEN_MULTIPLY` | value | AP 회복 배율 |
| `COOLDOWN_MULTIPLY` | value, actionScope | 쿨다운 배율 |
| `DURATION_MULTIPLY` | value, statusScope | 상태·장판·표식 지속 배율 |
| `STATUS_APPLY` | statusId, strength, duration | 상태 적용 |
| `STATUS_REMOVE` | statusScope, count | 허용 상태 제거 |
| `STATUS_CHANCE_ADD` | value, statusScope | 상태 적중 %p 가산 |
| `STATUS_RES_ADD` | value, duration | 상태 저항 가산 |
| `CORRUPTION_GAIN_MULTIPLY` | value, duration | 개인 오염 획득 배율 |
| `CORRUPTION_ADD` | value | 개인 오염 수치 가감 |
| `RELOAD_TIME_MULTIPLY` | value | 석궁 장전시간 배율 |
| `RECALL_SPEED_MULTIPLY` | value | 삼지창 회수속도 배율 |
| `RECALL_WIDTH_MULTIPLY` | value | 삼지창 회수 판정 폭 배율 |
| `RESCUE_MODIFY` | field, value, duration | 구조 시간·속도·중단 임계 보정 |
| `INTERACTION_TOLERANCE_ADD` | value | 교정 상호작용 허용 오차 가산 |
| `TELEGRAPH_LEAD_ADD` | ticks | 전조 조기 표시 |
| `DIRECTION_WARNING` | range, cooldown | 방향 경고 표시 |
| `PATH_MARK` | duration | 피해 없는 경로 표식 생성 |
| `EVENT_PROGRESS_ADD` | eventTag, value | 허용 사건 진행도 가산 |
| `INTERRUPT_METHOD_REGISTER` | methodId, duration | 중단 수단 기록 |
| `REPETITION_DECAY_DELAY` | stages | 반복 수단 감쇠 지연 |
| `SHARE_DAMAGE` | ratio, floorHp, duration | 두 플레이어 피해 분담 |
| `VIRTUAL_STORAGE_ADD` | category, slots | 가상 보관 칸 추가 |
| `CONSUMABLE_MODIFY` | itemTag, field, value | 소모품 사용 보정 |
| `FACILITY_ACTION_MODIFY` | field, value, range | 시설·중계 작업 보정 |
| `STAT_MULTIPLY` | stat, value, duration | 스탯 배율 보정 |
| `AP_COST_MULTIPLY` | value, actionScope | 행동 AP 비용 배율 |
| `AP_REGEN_ADD` | value | 초당 AP 회복 고정 가산 |
| `AP_REGEN_DELAY_ADD` | ticks | AP 자연회복 재개 지연 가산 |
| `CANCEL_CONSUME` | seededChance | 이미 예약한 소모 1회 취소 |
| `CONTRIBUTION_MULTIPLY` | value, objectiveScope | 중단·목표 기여량 배율 |
| `CORRUPTION_PRESSURE_ADD` | value | 사건 단위 오염 압력 가감 |
| `DURATION_ADD` | ticks, statusScope | 상태·장판 지속 틱 가산 |
| `EFFECT_MULTIPLY` | value, effectScope | 비피해 효과량 배율 |
| `INTERRUPT_THRESHOLD_MULTIPLY` | value | 채널 피격 중단 임계 배율 |
| `MEASURE_TIME_MULTIPLY` | value | 자원 노드 측정시간 배율 |
| `NEXT_WEAK_CONTROL_DURATION_MULTIPLY` | value, duration | 대상의 다음 약한 제어 지속 배율 |
| `PARRY_ARC_ADD` | degrees | 패링 허용 각도 가산 |
| `POISON_SOURCE_CAP_ADD` | value | 소유자 독 출처 상한 가산 |
| `RECEIVED_PURIFY_MULTIPLY` | value | 받는 정화량 배율 |
| `RESCUE_WINDOW_ADD` | ticks | 빈사 구조 가능시간 가산 |
| `SIGNAL_ERROR_MULTIPLY` | value | 신호 측정 오차 배율 |
| `SOURCE_STATUS_CHANCE_ADD` | value, statusScope, duration | 특정 공격 출처의 상태 적중 %p 가산 |

모든 배율은 `1.00` 기준이다. `+12%`는 `1.12`, `-8%`는 `0.92`, AP·PEN·RES처럼 고정값은 부호 있는 정수로 기록한다.

## 2. 유틸리티 도구 16개

| selector | 수 | `effectProfileId` | 실행 |
|---|---:|---|---|
| `EQL-UT-(RI/RS/HD/RC)-(PICKAXE/AXE/SHOVEL/HOE)` | 16 | `EQFX-UTILITY-<tier>-<tool>` | `UTILITY-TOOL-LIST-001`의 `VANILLA_HARVEST_WITH_WS_TIER_GATE`; T3/4/5/6, 수율 1.00, 성공 내구 1 |

## 3. Day 1~10 장비 71개

| selector | 수 | `effectProfileId` | trigger → conditions → operations → limit |
|---|---:|---|---|
| `EQL-W01~W09` | 9 | `EQFX-STATIC-CHASSIS` | `STAT_QUERY → ALWAYS → STATIC_STATS_ONLY` |
| `EQL-SW-U01` | 1 | `EQFX-EQL-SW-U01` | `STAT_QUERY/PARRY_HIT → ALWAYS → STAT_ADD(DEF,6), BREAK_MULTIPLY(1.04,PARRY)` |
| `EQL-SW-R01` | 1 | `EQFX-EQL-SW-R01` | `COMBO_HIT → comboStep=3 → STATUS_APPLY(VULNERABLE,4%,80t) → TARGET 120t` |
| `EQL-AX-U01` | 1 | `EQFX-EQL-AX-U01` | `ATTACK_RESOLVE → distinctTargets>=2 → AP_ADD(3) → EXECUTION_ONCE` |
| `EQL-AX-R01` | 1 | `EQFX-EQL-AX-R01` | `HEAVY_HIT → targetHpRatio<=0.30 → DAMAGE_MULTIPLY(1.12,HEAVY) → OWNER 100t` |
| `EQL-BO-U01` | 1 | `EQFX-EQL-BO-U01` | `RANGED_HIT → distance>=12 → DAMAGE_MULTIPLY(1.04,RANGED), STAT_ADD(HIT,4)` |
| `EQL-BO-R01` | 1 | `EQFX-EQL-BO-R01` | `PROJECTILE_HIT → sameTargetArrowOrdinal=3 → STATUS_APPLY(BLEED,1stack,authorityDuration)` |
| `EQL-CB-U01` | 1 | `EQFX-EQL-CB-U01` | `RELOAD_QUERY → CROSSBOW → RELOAD_TIME_MULTIPLY(0.92), MAGAZINE_CAPACITY=3` |
| `EQL-CB-R01` | 1 | `EQFX-EQL-CB-R01` | `MAGAZINE_FIRST_HIT → validHostile → STATUS_APPLY(ARMOR_BREAK,DEF-12,80t)` |
| `EQL-DG-U01` | 1 | `EQFX-EQL-DG-U01` | `HIT_QUERY → targetHas(BLEED) → STAT_ADD(HIT,6), DAMAGE_MULTIPLY(1.06,COMBO_STEP_4)` |
| `EQL-DG-R01` | 1 | `EQFX-EQL-DG-R01` | `POISON_QUERY → ownerSource → POISON_SOURCE_CAP_ADD(1), DAMAGE_MULTIPLY(1.08,POISON)` |
| `EQL-BL-U01` | 1 | `EQFX-EQL-BL-U01` | `BASIC_ATTACK_QUERY → MACE → BREAK_MULTIPLY(1.08,BASIC)` |
| `EQL-BL-R01` | 1 | `EQFX-EQL-BL-R01` | `BREAK_SUCCESS → ownerContributed → DAMAGE_MULTIPLY(1.08,ALL,100t)` |
| `EQL-ST-U01` | 1 | `EQFX-EQL-ST-U01` | `STATUS_QUERY → BURN → STATUS_CHANCE_ADD(6pp,BURN), DAMAGE_MULTIPLY(1.06,BURN_STACK_1)` |
| `EQL-ST-R01` | 1 | `EQFX-EQL-ST-R01` | `CORRUPTION_GAIN_QUERY → allyInsideOwnerSupportArea → CORRUPTION_GAIN_MULTIPLY(0.92)` |
| `EQL-PK-U01` | 1 | `EQFX-EQL-PK-U01` | `HIT_QUERY → targetTag=ARMORED → PEN_ADD(8)` |
| `EQL-PK-R01` | 1 | `EQFX-EQL-PK-R01` | `HIT_QUERY → targetBreakRatio>=0.80 → DAMAGE_MULTIPLY(1.10,ALL)` |
| `EQL-TR-U01` | 1 | `EQFX-EQL-TR-U01` | `RECALL_QUERY → CUSTOM_RECALL → AP_COST_ADD(-3,RECALL), RECALL_SPEED_MULTIPLY(1.10)` |
| `EQL-TR-R01` | 1 | `EQFX-EQL-TR-R01` | `THROW_HIT/RECALL_HIT → hostile → STATUS_APPLY(MARK,authority,80t), BREAK_MULTIPLY(1.10,RECALL_PATH)` |
| `EQL-UA-U01` | 1 | `EQFX-EQL-UA-U01` | `STAT_QUERY/COUNTER_COST → UNARMED_ACTIVE → STAT_ADD(DEF,4), AP_COST_ADD(-2,UNARMED_COUNTER)` |
| `EQL-UA-R01` | 1 | `EQFX-EQL-UA-R01` | `EXACT_DODGE_SUCCESS → UNARMED_ACTIVE → BREAK_MULTIPLY(1.20,NEXT_UNARMED_HIT)` |
| `EQL-OH-C01` | 1 | `EQFX-EQL-OH-C01` | `GUARD_QUERY → equippedOffhand → COMBAT-004_BASE_GUARD_PARRY` |
| `EQL-OH-U01` | 1 | `EQFX-EQL-OH-U01` | `PARRY_QUERY/PARRY_SUCCESS → equippedOffhand → PARRY_ARC_ADD(10deg), AP_ADD(1)` |
| `EQL-OH-R01` | 1 | `EQFX-EQL-OH-R01` | `PROJECTILE_DAMAGE_QUERY → guardingOwner, allyBehind<=3m → DAMAGE_TAKEN_MULTIPLY(0.92,PROJECTILE)` |
| `EQL-AC-C01` | 1 | `EQFX-EQL-AC-C01` | `STAT_QUERY/INVENTORY_QUERY → equipped → STAT_ADD(RES,2), VIRTUAL_STORAGE_ADD(SAMPLE,4)` |
| `EQL-AC-U01` | 1 | `EQFX-EQL-AC-U01` | `STAT_QUERY/SIGNAL_QUERY → equipped → STAT_ADD(HIT,4), SIGNAL_ERROR_MULTIPLY(0.90)` |
| `EQL-AC-U02` | 1 | `EQFX-EQL-AC-U02` | `CONSUMABLE_USE_QUERY → hpRatio<=0.25,itemTag=BANDAGE,firstPerDay → CONSUMABLE_MODIFY(USE_TIME,0.50) → OWNER_DAY 1` |
| `EQL-AC-R01` | 1 | `EQFX-EQL-AC-R01` | `HIT_QUERY → targetDistinctDebuffs>=2 → BREAK_MULTIPLY(1.06,ALL)` |
| `EQL-AC-R02` | 1 | `EQFX-EQL-AC-R02` | `AMMO_BUNDLE_CONSUME → firstUnitOfBundle → CANCEL_CONSUME(0.12 seededChance)` |
| `EQL-CH-C01` | 1 | `EQFX-EQL-CH-C01` | `STAT_QUERY → equipped → STAT_ADD(HP,40)` |
| `EQL-CH-U01` | 1 | `EQFX-EQL-CH-U01` | `AP_REGEN_QUERY → outOfCombat → AP_REGEN_ADD(0.5_per_second)` |
| `EQL-CH-U02` | 1 | `EQFX-EQL-CH-U02` | `STAT_QUERY → equipped → STAT_ADD(BREAK_DAMAGE,4%)` |
| `EQL-CH-R01` | 1 | `EQFX-EQL-CH-R01` | `CONSUMABLE_EFFECT_QUERY → item=PURIFY_AMPOULE → EFFECT_MULTIPLY(1.12), forbidResourceCreation` |
| `EQL-AR-C01~C04` | 4 | `EQFX-SET-PIONEER` | `SET_QUERY → pieces>=2/4 → DAMAGE_TAKEN_MULTIPLY(0.97,ENVIRONMENT) / CONSUMABLE_MODIFY(FIELD_REPAIR_EFFECT,1.10)` |
| `EQL-AR-U-SCOUT-*` | 4 | `EQFX-SET-SCOUT` | `DAMAGE_QUERY/NODE_MEASURE_QUERY → movingFirstHit → DAMAGE_TAKEN_MULTIPLY(0.96,80t); MEASURE_TIME_MULTIPLY(0.85)` |
| `EQL-AR-U-VANGUARD-*` | 4 | `EQFX-SET-VANGUARD` | `GUARD_COST/DAMAGE_QUERY → guarding or rescuing → AP_COST_MULTIPLY(0.95,GUARD_IMPACT); DAMAGE_TAKEN_MULTIPLY(0.92,RESCUE)` |
| `EQL-AR-R-OBSERVER-*` | 4 | `EQFX-SET-OBSERVER` | `CORRUPTION_GAIN_QUERY/SAMPLE_CHANNEL_QUERY → equipped → CORRUPTION_GAIN_MULTIPLY(0.94); INTERRUPT_THRESHOLD_MULTIPLY(1.20)` |
| `EQL-D10-W-(SW/AX/BO/CB/DG/BL/ST/PK/TR)` | 9 | `EQFX-EQL-D10-W` | `CORE_ACTIVE_HIT → sameWeapon,nextCoreActive<=120t → BREAK_MULTIPLY(1.18,NEXT_CORE_ACTIVE) → OWNER 200t` |
| `EQL-D10-AC` | 1 | `EQFX-EQL-D10-AC` | `STATUS_CHANCE_QUERY → targetStatusesFromOtherPlayersDistinct=1..3 → STATUS_CHANCE_ADD(3pp_each,max9pp)` |
| `EQL-D10-AR` | 1 | `EQFX-EQL-D10-AR` | `TELEGRAPH_RESPONSE_SUCCESS → validBossPattern → DAMAGE_TAKEN_MULTIPLY(0.90,ALL,100t)` |
| `EQL-D10-CH` | 1 | `EQFX-EQL-D10-CH` | `BREAK_QUERY → targetBreakRatio>=0.80 → BREAK_MULTIPLY(1.12,ALL) → deactivate100tAfterBreakSuccess` |
| `EQL-D10-OH` | 1 | `EQFX-EQL-D10-OH` | `BOSS_PARRY_SUCCESS → allies<=4m → AP_ADD(3) → PATTERN 160t` |
| `EQL-D10-UA` | 1 | `EQFX-EQL-D10-UA` | `EXACT_DODGE_COUNTER → UNARMED_ACTIVE → PEN_ADD(12,80t) → OWNER 160t` |

## 4. Day 11~20 장비 55개

| selector | 수 | `effectProfileId` | trigger → conditions → operations → limit |
|---|---:|---|---|
| `EQD20-SW-R01` | 1 | `EQFX-EQD20-SW-R01` | `PARRY_SUCCESS → next3Hits<=100t → DURATION_MULTIPLY(1.08,TARGET_EXISTING_WEAK_STATUS) → TARGET 160t` |
| `EQD20-AX-R01` | 1 | `EQFX-EQD20-AX-R01` | `HEAVY_HIT → targetDistinctStatuses>=2 → DAMAGE_MULTIPLY(1.10,HEAVY) → EXECUTION_ONCE` |
| `EQD20-BO-R01` | 1 | `EQFX-EQD20-BO-R01` | `RANGED_HIT → distance>=12,targetHas(BURN) → STATUS_REMOVE(BURN,1), BREAK_MULTIPLY(1.12,HIT)` |
| `EQD20-CB-R01` | 1 | `EQFX-EQD20-CB-R01` | `MAGAZINE_FIRST_HIT → targetHas(POISON or BLEED) → SOURCE_STATUS_CHANCE_ADD(-8pp,POISON_BLEED,100t)` |
| `EQD20-DG-R01` | 1 | `EQFX-EQD20-DG-R01` | `EXACT_DODGE_SUCCESS → for80t,targetHas(BLEED) → STAT_ADD(HIT,8),BREAK_MULTIPLY(1.10,COMBO_STEP_4)` |
| `EQD20-BL-R01` | 1 | `EQFX-EQD20-BL-R01` | `HEAVY_HIT → hostile → NEXT_WEAK_CONTROL_DURATION_MULTIPLY(0.80,160t); effectBenefitsAllies` |
| `EQD20-ST-R01` | 1 | `EQFX-EQD20-ST-R01` | `AREA_DAMAGE_QUERY → targetDistinctStatuses>=3 → DAMAGE_MULTIPLY(1.08,AREA) → CAST_TARGET_ONCE` |
| `EQD20-PK-R01` | 1 | `EQFX-EQD20-PK-R01` | `HIT_QUERY → targetTag=BIOLOGICAL or ARMORED → PEN_ADD(10), additional PEN_ADD(4) if breakRatio>=0.80` |
| `EQD20-TR-R01` | 1 | `EQFX-EQD20-TR-R01` | `RECALL_PATH_ALLY_PASS → ally → STATUS_RES_ADD(6,80t) → TARGET 200t` |
| `EQD20-UA-R01` | 1 | `EQFX-EQD20-UA-R01` | `EXACT_DODGE_COUNTER → UNARMED_ACTIVE → STATUS_REMOVE(WEAK_SLOW_OR_WEAKEN,1) → OWNER 240t` |
| `EQD20-EP-W01-(9 classes)` | 9 | `EQFX-EQD20-EP-W01` | `COOP_STATUS_REACTION → sourceOtherPlayer,nextCoreActive<=120t → BREAK_MULTIPLY(1.14,NEXT_CORE_ACTIVE) → OWNER 200t` |
| `EQD20-EP-OH01` | 1 | `EQFX-EQD20-EP-OH01` | `RESCUE_INTERRUPT_QUERY/PARRY_SUCCESS → guarding,allyBehind<=4m → RESCUE_MODIFY(INTERRUPT_THRESHOLD,1.20); onParry duration60t` |
| `EQD20-EP-AR01` | 1 | `EQFX-EQD20-EP-AR01` | `STRONG_STATUS_APPLIED → firstFamilyPerCombat → DAMAGE_TAKEN_MULTIPLY(0.88,ALL,40t)` |
| `EQD20-EP-AC01` | 1 | `EQFX-EQD20-EP-AC01` | `ALLY_STATUS_CLEANSED_BY_ALLOWED_CONSUMABLE → cleanser+target → AP_ADD(4 each) → TARGET 240t` |
| `EQD20-EP-CH01` | 1 | `EQFX-EQD20-EP-CH01` | `PARTY_STATUS_WINDOW → 3 distinct statuses within60t,boss → STAT_ADD(STATUS_RES,-6,100t) → TARGET 240t` |
| `EQD20-EP-UA01` | 1 | `EQFX-EQD20-EP-UA01` | `RESCUE_START → exactCounterWithin80t,UNARMED_ACTIVE → RESCUE_MODIFY(MOVE_PENALTY,0.50) → OWNER 240t` |
| `EQD20-AR-MED-*` | 4 | `EQFX-SET-D20-MED` | `SET_QUERY → 2pc CONSUMABLE_MODIFY(ALLY_USE_TIME,0.90); 4pc RESCUE_SUCCESS STATUS_REMOVE(REMOVABLE_WEAK,1) → TARGET 400t` |
| `EQD20-AR-CTRL-*` | 4 | `EQFX-SET-D20-CTRL` | `SET_QUERY → 2pc DURATION_MULTIPLY(0.90,SILENCE_DISARM); 4pc STRONG_CC_END DURATION_MULTIPLY(0.75,NEXT_WEAK_CC,40t) → OWNER 300t` |
| `EQD20-AR-GUARD-*` | 4 | `EQFX-SET-D20-GUARD` | `SET_QUERY → 2pc DAMAGE_TAKEN_MULTIPLY(0.92,HIGHEST_POISON_BURN_BLEED_STACK); 4pc distinctStatuses>=2 DAMAGE_TAKEN_MULTIPLY(0.92,NON_STRONG_CC)` |
| `EQD20-OH-R01` | 1 | `EQFX-EQD20-OH-R01` | `PARRY_SUCCESS → alliesBehind<=3m → DURATION_ADD(-20t,POISON_BURN) → PATTERN 160t` |
| `EQD20-AC-R01` | 1 | `EQFX-EQD20-AC-R01` | `INVENTORY_QUERY → equipped → VIRTUAL_STORAGE_ADD(STATUS_SAMPLE,8), noQuotaOrDropBonus` |
| `EQD20-AC-R02` | 1 | `EQFX-EQD20-AC-R02` | `ALLY_DOWNED → distance<=8m → RESCUE_MODIFY(START_SPEED,1.12,100t) → OWNER 600t` |
| `EQD20-CH-R01` | 1 | `EQFX-EQD20-CH-R01` | `STATUS_CLEANSE_CONSUMABLE_COMPLETE → sameStatusReapplyWithin80t → DURATION_MULTIPLY(0.80) → OWNER 300t` |
| `EQD20-CH-R02` | 1 | `EQFX-EQD20-CH-R02` | `STRONG_CC_TELEGRAPH_START → hostileDistance>8m → DIRECTION_WARNING → TARGET 200t` |
| `EQD20-LG-W01-(9 classes)` | 9 | `EQFX-EQD20-LG-W01` | `CORE_ACTIVE_HIT → coopReactionSameTargetWithin160t → BREAK_MULTIPLY(1.20),doNotConsumeReaction → OWNER 240t` |
| `EQD20-LG-UA01` | 1 | `EQFX-EQD20-LG-UA01` | `TRACKING_PATTERN_INTERRUPTED_BY_EXACT_COUNTER → targetWasAlly → AP_ADD(5,PARTY) → PATTERN 300t` |
| `EQD20-LG-OH01` | 1 | `EQFX-EQD20-LG-OH01` | `PARRY_STRONG_STATUS_PATTERN → allies<=4m → STATUS_RES_ADD(12,60t),notImmunity → OWNER 300t` |
| `EQD20-LG-AR01` | 1 | `EQFX-EQD20-LG-AR01` | `DOWNED_TRANSITION → firstPerCombat → RESCUE_WINDOW_ADD(80t), PAUSE_OWN_WEAK_STATUS; notReviveOrPreventDowned` |
| `EQD20-LG-AC01` | 1 | `EQFX-EQD20-LG-AC01` | `PARTY_STATUS_RECORD → distinctStatuses>=4 → DAMAGE_MULTIPLY(1.10,STATUS_REACTION),BREAK_MULTIPLY(1.10,STATUS_REACTION,160t) → PARTY 400t` |
| `EQD20-LG-CH01` | 1 | `EQFX-EQD20-LG-CH01` | `RESCUE_SUCCESS → rescuer+target → SHARE_DAMAGE(0.10,floorHp=1,120t) → PAIR 600t` |

## 5. Day 21~50 장비 72개

### 5.1 Day 21~30 10개

| selector | `effectProfileId` | trigger → conditions → operations → limit |
|---|---|---|
| `EQD50-SW-E21` | `EQFX-EQD50-SW-E21` | `PARRY_SUCCESS → owner → CORRUPTION_GAIN_MULTIPLY(0.92,160t) → OWNER 240t` |
| `EQD50-AX-E21` | `EQFX-EQD50-AX-E21` | `HEAVY_HIT → targetDistinctMutations>=2 → DAMAGE_MULTIPLY(1.12,HEAVY) → EXECUTION_ONCE` |
| `EQD50-BO-E21` | `EQFX-EQD50-BO-E21` | `STATUS_CHANCE_QUERY → targetOutsidePurifyArea → STATUS_CHANCE_ADD(8pp,ALL) → TARGET 160t authorityCap` |
| `EQD50-CB-E21` | `EQFX-EQD50-CB-E21` | `MAGAZINE_FIRST_HIT → targetChannel=PURIFY_DISRUPT → BREAK_ADD(180) → CHANNEL_ONCE` |
| `EQD50-DG-E21` | `EQFX-EQD50-DG-E21` | `COMBO_STEP_4_HIT → behindMutationWeakpoint → STATUS_APPLY(REFINED_MARK,1,200t),forbidResourceCreation` |
| `EQD50-BL-E21` | `EQFX-EQD50-BL-E21` | `BREAK_QUERY → targetHas(PURIFY_SHIELD) → BREAK_MULTIPLY(1.14) → TARGET 160t` |
| `EQD50-ST-E21` | `EQFX-EQD50-ST-E21` | `PURIFY_FIELD_REPOSITION_QUERY → ownerField → COOLDOWN_MULTIPLY(0.90),globalFloor` |
| `EQD50-PK-E21` | `EQFX-EQD50-PK-E21` | `HIT_QUERY → targetTag=MUTATION_CARAPACE → PEN_ADD(12),globalPenCap` |
| `EQD50-TR-E21` | `EQFX-EQD50-TR-E21` | `RECALL_PATH_QUERY → intersectsPurifyArea → RECALL_WIDTH_MULTIPLY(1.10) → RECALL_ONCE` |
| `EQD50-UA-E21` | `EQFX-EQD50-UA-E21` | `EXACT_DODGE_SUCCESS → UNARMED_ACTIVE,outsidePurifyArea → CORRUPTION_ADD(-1pp) → OWNER 300t,noReward` |

### 5.2 Day 31~40 10개

| selector | `effectProfileId` | trigger → conditions → operations → limit·대가 |
|---|---|---|
| `EQD50-SW-L31` | `EQFX-EQD50-SW-L31` | `PARRY_BREAK_QUERY → previousInterruptMethodDifferent → BREAK_MULTIPLY(1.14) → PATTERN_ONCE; samePatternConsecutive BREAK_MULTIPLY(0.90)` |
| `EQD50-AX-L31` | `EQFX-EQD50-AX-L31` | `HEAVY_HIT → interruptSuccessWithin120t → DAMAGE_MULTIPLY(1.15) → EXECUTION_ONCE; AP_COST_ADD(8,HEAVY)` |
| `EQD50-BO-L31` | `EQFX-EQD50-BO-L31` | `BREAK_QUERY → targetChanneling,distance>=12m → BREAK_MULTIPLY(1.12) → TARGET 160t; distance<12m DAMAGE_MULTIPLY(0.94)` |
| `EQD50-CB-L31` | `EQFX-EQD50-CB-L31` | `MAGAZINE_HIT → distinctTargets>=2 → BREAK_MULTIPLY(1.10) → MAGAZINE_ONCE; sameTargetOrdinal=3 DAMAGE_MULTIPLY(0.92)` |
| `EQD50-DG-L31` | `EQFX-EQD50-DG-L31` | `BREAK_QUERY → exactDodgeWithin160t,targetWeakpoint → BREAK_MULTIPLY(1.16); nonWeakpoint BREAK_MULTIPLY(0.92)` |
| `EQD50-BL-L31` | `EQFX-EQD50-BL-L31` | `REPETITION_DECAY_QUERY → partyHasNoSameProfile → REPETITION_DECAY_DELAY(1) → PARTY_HIGHEST_ONLY; STAT_MULTIPLY(SPD,0.95)` |
| `EQD50-ST-L31` | `EQFX-EQD50-ST-L31` | `HOSTILE_AREA_DURATION_QUERY → interruptSuccess → DURATION_MULTIPLY(0.85,HOSTILE_RESIDUAL_AREA) → AREA_ONCE; DAMAGE_MULTIPLY(0.95,DIRECT)` |
| `EQD50-PK-L31` | `EQFX-EQD50-PK-L31` | `BREAK_QUERY → targetChannelingOrHeavyArmor → BREAK_MULTIPLY(1.15) → TARGET 120t; nonArmored DAMAGE_MULTIPLY(0.94)` |
| `EQD50-TR-L31` | `EQFX-EQD50-TR-L31` | `RECALL_EXECUTION_RESOLVE → throwAndRecallHitDifferentTargets → BREAK_MULTIPLY(1.12) → EXECUTION_ONCE; recallFailed AP_ADD(-6)` |
| `EQD50-UA-L31` | `EQFX-EQD50-UA-L31` | `EXACT_DODGE_COUNTER → UNARMED_ACTIVE → BREAK_MULTIPLY(1.18),patternDecayApplies; DAMAGE_TAKEN resetsPendingBonus` |

### 5.3 Day 41~50 10개

| selector | `effectProfileId` | trigger → conditions → operations → limit·대가 |
|---|---|---|
| `EQD50-SW-A41` | `EQFX-EQD50-SW-A41` | `COOP_ACTION_PAIR → rescue+parry,differentPlayers,within160t → PARTY STAT_ADD(RES,6,120t) → OWNER 240t,noSelfChain` |
| `EQD50-AX-A41` | `EQFX-EQD50-AX-A41` | `DAMAGE_QUERY → targetTag=SIEGE or BOSS_ECHO_MARK → DAMAGE_MULTIPLY(1.10); otherwise DAMAGE_MULTIPLY(0.96)` |
| `EQD50-BO-A41` | `EQFX-EQD50-BO-A41` | `PROJECTILE_PATH_QUERY → crossesActiveRelayLine → PATH_MARK(120t) → RELAY_LINE 200t,noDirectDamage` |
| `EQD50-CB-A41` | `EQFX-EQD50-CB-A41` | `OBJECTIVE_OWNER_SWITCH → facilityOrPartyObjective → RELOAD_TIME_MULTIPLY(0.88,NEXT_RELOAD,200t),noDamageBonus` |
| `EQD50-DG-A41` | `EQFX-EQD50-DG-A41` | `DODGE_COUNTER_RESOLVE → movementPressureActive → AP_ADD(5) → OWNER 20t,NO_RETRIGGER` |
| `EQD50-BL-A41` | `EQFX-EQD50-BL-A41` | `BREAK_QUERY → targetTag=SPLICE_CORE or SIEGE → BREAK_MULTIPLY(1.12); targetTag=NORMAL DAMAGE_MULTIPLY(0.96)` |
| `EQD50-ST-A41` | `EQFX-EQD50-ST-A41` | `STAT_QUERY → allyInsideStatusOrCorruptionReactionArea → STAT_ADD(RES,6) → AREA_ONE,HIGHEST_ONLY` |
| `EQD50-PK-A41` | `EQFX-EQD50-PK-A41` | `HIT_QUERY → targetTag=SIEGE,distanceToReconstructionFacility<=20m → PEN_ADD(10),noHarvestBonus` |
| `EQD50-TR-A41` | `EQFX-EQD50-TR-A41` | `RECALL_PATH_QUERY → followsActiveRelayLine → RECALL_SPEED_MULTIPLY(1.15),vanillaRiptideLoyaltyDisabled` |
| `EQD50-UA-A41` | `EQFX-EQD50-UA-A41` | `RESCUE_MOVE_QUERY → UNARMED_ACTIVE,rescueCounterAlternationWithin160t → RESCUE_MODIFY(MOVE_PENALTY,0.50); disableImmediatelyOnMainWeaponEquip` |

### 5.4 방어구·보조 16개

| selector | 수 | `effectProfileId` | trigger → conditions → operations → limit·대가 |
|---|---:|---|---|
| `EQD50-AR-PURIFIER-*` | 4 | `EQFX-SET-D50-PURIFIER` | `SET_QUERY → 2pc CORRUPTION_GAIN_MULTIPLY(0.94); 4pc withinPurifier8m DAMAGE_TAKEN_MULTIPLY(0.92); outsidePurifyArea STAT_ADD(SPD,-3)` |
| `EQD50-AR-INTERRUPT-*` | 4 | `EQFX-SET-D50-INTERRUPT` | `SET_QUERY → 2pc TELEGRAPH_LEAD_ADD(3t,INTERRUPTIBLE); 4pc differentMethod BREAK_MULTIPLY(1.08); sameMethodConsecutive DAMAGE_MULTIPLY(0.96)` |
| `EQD50-AR-REBUILD-*` | 4 | `EQFX-SET-D50-REBUILD` | `SET_QUERY → 2pc facilityOrRelayWork DAMAGE_TAKEN_MULTIPLY(0.90); 4pc RESCUE_MODIFY(INTERRUPT_THRESHOLD,1.20,RESCUE_REPAIR_CALIBRATE); DAMAGE_MULTIPLY(0.95,DIRECT)` |
| `EQD50-OH-PURIFY` | 1 | `EQFX-EQD50-OH-PURIFY` | `PARRY_CORRUPTION_PATTERN → alliesBehind → CORRUPTION_PRESSURE_ADD(-1) → OWNER 300t` |
| `EQD50-OH-INTERRUPT` | 1 | `EQFX-EQD50-OH-INTERRUPT` | `PARRY_SUCCESS → interruptiblePattern → INTERRUPT_METHOD_REGISTER(PARRY,240t) → PATTERN` |
| `EQD50-AC-CALIBRATE` | 1 | `EQFX-EQD50-AC-CALIBRATE` | `CALIBRATION_QUERY → objective=C29 → INTERACTION_TOLERANCE_ADD(5pp),HIGHEST_ONLY,noAutoComplete` |
| `EQD50-CH-RELAY` | 1 | `EQFX-EQD50-CH-RELAY` | `RELAY_LINE_BREAK/FIRST_REPAIR_QUERY → range<=20m → DIRECTION_WARNING, FACILITY_ACTION_MODIFY(REPAIR_TIME,0.90) → FIRST_REPAIR_ONLY` |

### 5.5 Day 30 보스 고유 13개

| selector | 수 | `effectProfileId` | trigger → conditions → operations → limit·대가 |
|---|---:|---|---|
| `EQD50-B30-W01-(9 classes)` | 9 | `EQFX-EQD50-B30-W01` | `CORE_ACTIVE_QUERY → targetPurifyVulnerableWithin160t → DAMAGE_MULTIPLY(1.14),BREAK_MULTIPLY(1.14); otherwise AP_REGEN_MULTIPLY(0.95)` |
| `EQD50-B30-UA01` | 1 | `EQFX-EQD50-B30-UA01` | `EXACT_DODGE_COUNTER → UNARMED_ACTIVE,eventTag=PURIFY_CHARGE → EVENT_PROGRESS_ADD(1) → OWNER 300t,noOutOfCombatOrResource` |
| `EQD50-B30-OH01` | 1 | `EQFX-EQD50-B30-OH01` | `PARRY_MUTATION_PATTERN → nextPartyDamageFromPattern DAMAGE_TAKEN_MULTIPLY(0.90,200t); ownerOnly uses0.95` |
| `EQD50-B30-AR01` | 1 | `EQFX-EQD50-B30-AR01` | `DAMAGE_QUERY/CLEANSE_QUERY → personalCorruptionStage>=3 → DAMAGE_TAKEN_MULTIPLY(0.90); RECEIVED_PURIFY_MULTIPLY(0.85)` |
| `EQD50-B30-AC01` | 1 | `EQFX-EQD50-B30-AC01` | `ALLY_MUTATION_RESPONSE_SUCCESS → sourceOtherPlayer → PEN_ADD(8,120t),selfSoloCannotTrigger` |

### 5.6 Day 40 보스 고유 13개

| selector | 수 | `effectProfileId` | trigger → conditions → operations → limit·대가 |
|---|---:|---|---|
| `EQD50-B40-W01-(9 classes)` | 9 | `EQFX-EQD50-B40-W01` | `INTERRUPT_CONTRIBUTION_QUERY → channelTag=INTERRUPTIBLE,firstOwnerContribution → CONTRIBUTION_MULTIPLY(1.18) → PATTERN_ONCE; UNBREAKABLE DAMAGE_MULTIPLY(0.94)` |
| `EQD50-B40-UA01` | 1 | `EQFX-EQD50-B40-UA01` | `EXACT_DODGE_COUNTER → UNARMED_ACTIVE → INTERRUPT_METHOD_REGISTER(COUNTER) independent; failedCounter AP_REGEN_DELAY_ADD(20t)` |
| `EQD50-B40-OH01` | 1 | `EQFX-EQD50-B40-OH01` | `RESIDUAL_AREA_DAMAGE_QUERY → patternResult=PARTIAL,guarding → DAMAGE_TAKEN_MULTIPLY(0.88); AP_COST_ADD(2_per_second,GUARD_HOLD)` |
| `EQD50-B40-AR01` | 1 | `EQFX-EQD50-B40-AR01` | `PHASE_TRANSITION → afterTransition80t BREAK_MULTIPLY(1.10); duringTransition AP_REGEN_MULTIPLY(0)` |
| `EQD50-B40-AC01` | 1 | `EQFX-EQD50-B40-AC01` | `INTERRUPT_METHOD_SUCCESS → 3 distinct methods → AP_ADD(8,PARTY) → PATTERN 400t,effectCannotCountItself` |

## 6. 전개 합계

| 범주 | 실제 템플릿 | 프로필 투영 |
|---|---:|---|
| 유틸리티 | 16 | Tier×도구 16개 |
| Day 1~10 | 71 | 표 §3의 모든 selector 전개 |
| Day 11~20 | 55 | 표 §4의 모든 selector 전개 |
| Day 21~50 | 72 | 표 §5의 모든 selector 전개 |
| 합계 | `214` | 누락·중복 `0` |

## 7. 검증 게이트

- 모든 `TOOL-LIST-001` ID가 정확히 한 `effectProfileId`를 가진다.
- 모든 selector를 전개한 실제 ID 합집합이 214개이고, 미등록 ID와 중복 소유가 0개다.
- 프로필의 trigger, target, operations가 비어 있지 않다. `STATIC_STATS_ONLY`와 유틸리티만 명시 예외다.
- cooldown scope는 `OWNER/TARGET/PAIR/PARTY/PATTERN/CHANNEL/AREA/EXECUTION` 중 하나다.
- 퍼센트와 틱은 숫자 필드로 저장하며 `effectText`를 파싱하지 않는다.
- 부모 효과 비상속, 세트 중복 1회, 파티 `HIGHEST_ONLY`, `NO_RETRIGGER`, 대상별 ICD를 검증한다.
- 심연 효과와 대가는 같은 프로필에 존재하며 재련·정화로 한쪽만 제거할 수 없다.
- 자원 생성 금지 프로필이 재료·보상 원장을 직접 증가시키지 않는다.
- 권투 장비는 주무기 장착 즉시 비활성화되고, 유틸리티 도구는 전투 스킬 리스너를 등록하지 않는다.

## 8. 구현 인계

- r2 장비 레코드에는 `effectProfileId`만 투영하고, 별도 `equipment/effect-profiles.json`에 구조화 프로필을 둔다.
- 서버는 이벤트 버스에서 trigger를 발행하고 프로필 실행기가 conditions를 검사한 뒤 operations를 한 번 커밋한다.
- 피해·브레이크·AP·상태·오염·구조·시설 결과는 각각의 권위 서비스에 명령을 위임한다. 장비 실행기가 수치를 직접 저장하지 않는다.
- 실행 키는 `runId + equipmentInstanceId + effectProfileId + sourceExecutionId + targetId + ordinal`이며 같은 결과를 두 번 처리하지 않는다.
- 실제 클라이언트 검증에는 장착 교체, 재접속, 빈사·사망, slot 0 파괴, 세트 부위 변화, 파티 중복 장착과 리소스 팩 거부가 포함된다.
