# WildSurvival Season 1 스킬 실행 효과 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `SKILL-EFFECT-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 적용 범위 | 기본 공격 10·무기 액티브 40·공용 액티브 10·상황 스킬 4의 operation 배열 |
| 상위 기준 | `SKILL-LIST-001`, `COMBAT-001`, `CORE-001`, `STATUS`, `BREAK` |
| 데이터 리비전 | `skill-effect-s1-r2` |
| 최종 수정일 | 2026-08-24 |

## 1. 실행 투영 규칙

- 각 스킬은 아래 `operationIds`를 배열 순서대로 실행하되, 비용 예약→대상 확정→효과 commit→표현 발행의 공통 트랜잭션을 사용한다.
- operation은 공통 실행기 종류다. 피해·브레이크·거리·상태·지속·최대 대상·조건 수치는 `SKILL-LIST-001` 같은 ID 행에서 명시 필드로 복사하며 런타임이 설명문을 읽지 않는다.
- 조건 operation이 실패하면 그 분기만 생략한다. 스킬 전체의 AP·탄약·소모품·내구 소비 시점은 `SKILL-LIST-001`의 실패 계약을 따른다.
- 모든 타격은 `castId+operationIndex+hitIndex+targetId`로 멱등 처리한다. 다단·왕복·연쇄가 같은 target hit 상한을 초과하면 후속 타격을 거부한다.
- `SPAWN_*`는 `ENTITY-LIST-001`의 고정 엔티티 ID를 요구한다. 임시 Display·Particle도 parent cast 종료 시 정리한다.

## 2. 기본 공격 10개

| skillId | operationIds |
|---|---|
| `ws.basic.sword.v1` | `DAMAGE`, `COMBO_ADVANCE`, `DURABILITY_SPEND` |
| `ws.basic.axe.v1` | `DAMAGE`, `BREAK_DAMAGE`, `HEAVY_TAG_EMIT`, `DURABILITY_SPEND` |
| `ws.basic.bow.v1` | `CHARGE_STATE_RESOLVE`, `AMMO_CONSUME`, `SPAWN_PROJECTILE`, `DAMAGE`, `DURABILITY_SPEND` |
| `ws.basic.crossbow.v1` | `LOADED_STATE_REQUIRE`, `AMMO_CONSUME`, `SPAWN_PROJECTILE`, `DAMAGE`, `BREAK_DAMAGE`, `DURABILITY_SPEND` |
| `ws.basic.dagger.v1` | `DAMAGE`, `COMBO_ADVANCE`, `DURABILITY_SPEND` |
| `ws.basic.blunt.v1` | `DAMAGE`, `BREAK_DAMAGE`, `IMPACT_RESOLVE`, `DURABILITY_SPEND` |
| `ws.basic.staff.v1` | `SPAWN_PROJECTILE`, `DAMAGE`, `BREAK_DAMAGE`, `DURABILITY_SPEND` |
| `ws.basic.pickaxe.v1` | `TARGET_OR_MINING_BRANCH`, `DAMAGE`, `BREAK_DAMAGE`, `DURABILITY_SPEND` |
| `ws.basic.trident.v1` | `DAMAGE`, `BREAK_DAMAGE`, `DURABILITY_SPEND` |
| `ws.basic.unarmed.v1` | `DAMAGE`, `COMBO_ADVANCE` |

## 3. 무기 액티브 40개

### 3.1 검·도끼

| skillId | operationIds |
|---|---|
| `ws.sword.guard_cut.v1` | `DAMAGE`, `BREAK_DAMAGE`, `PARRY_SUCCESS_DAMAGE_BRANCH` |
| `ws.sword.rally_lunge.v1` | `MOVE_SELF`, `DAMAGE`, `APPLY_MARK` |
| `ws.sword.turning_guard.v1` | `AREA_DAMAGE`, `BREAK_DAMAGE`, `APPLY_SELF_DAMAGE_REDUCTION` |
| `ws.sword.focused_duel.v1` | `APPLY_MARK`, `APPLY_SELF_TARGET_DAMAGE_MODIFIER`, `CANCEL_ON_OTHER_TARGET_HIT` |
| `ws.axe.cleaving_charge.v1` | `DAMAGE`, `BREAK_DAMAGE`, `APPLY_DEFENCE_MODIFIER` |
| `ws.axe.execution_arc.v1` | `DAMAGE`, `LOW_HP_DAMAGE_SCALE` |
| `ws.axe.hook_sweep.v1` | `AREA_DAMAGE`, `BREAK_DAMAGE`, `MOVE_TARGET` |
| `ws.axe.resolute_hew.v1` | `APPLY_STARTUP_CONTROL_IMMUNITY`, `APPLY_STARTUP_DAMAGE_CAP`, `DAMAGE` |

### 3.2 활·석궁

| skillId | operationIds |
|---|---|
| `ws.bow.pin_shot.v1` | `SPAWN_PROJECTILE`, `DAMAGE`, `NORMAL_ROOT_OR_BOSS_BREAK_BRANCH` |
| `ws.bow.barbed_rain.v1` | `SPAWN_PROJECTILE_VOLLEY`, `DAMAGE`, `APPLY_BLEED`, `PER_TARGET_HIT_CAP` |
| `ws.bow.piercing_lane.v1` | `SPAWN_PIERCING_PROJECTILE`, `DAMAGE`, `PENETRATION_TARGET_FALLOFF` |
| `ws.bow.rescue_flare.v1` | `SPAWN_PROJECTILE`, `SPAWN_PARTY_AREA`, `APPLY_PARTY_SPEED`, `APPLY_RESCUE_SPEED` |
| `ws.crossbow.breach_bolt.v1` | `AMMO_CONSUME`, `SPAWN_PROJECTILE`, `DAMAGE`, `BREAK_DAMAGE`, `APPLY_DEFENCE_MODIFIER` |
| `ws.crossbow.magazine_volley.v1` | `AMMO_CONSUME`, `SPAWN_PROJECTILE_VOLLEY`, `DAMAGE`, `SAME_TARGET_DAMAGE_FALLOFF`, `APPLY_SLOW` |
| `ws.crossbow.suppressive_bolt.v1` | `AMMO_CONSUME`, `SPAWN_PROJECTILE`, `SPAWN_ENEMY_AREA`, `APPLY_OUTGOING_DAMAGE_MODIFIER` |
| `ws.crossbow.snap_reload.v1` | `AMMO_CONSUME`, `LOADED_STATE_ADD` |

### 3.3 단검·둔기

| skillId | operationIds |
|---|---|
| `ws.dagger.shadowstep.v1` | `MOVE_SELF_TO_FLANK`, `DAMAGE`, `PRECISION_DODGE_AP_DISCOUNT` |
| `ws.dagger.venom_flurry.v1` | `MULTIHIT_DAMAGE`, `APPLY_POISON_WITH_CAST_CAP` |
| `ws.dagger.hemorrhage_cut.v1` | `DAMAGE`, `BLEED_STACK_DAMAGE_SCALE_NO_REFRESH` |
| `ws.dagger.fading_feint.v1` | `MOVE_SELF_BACK`, `APPLY_THREAT_PRIORITY_MODIFIER`, `QUEUE_NEXT_ATTACK_DAMAGE` |
| `ws.blunt.quake_break.v1` | `AREA_DAMAGE`, `BREAK_DAMAGE`, `NORMAL_AIRBORNE_OR_BOSS_BREAK_BRANCH` |
| `ws.blunt.shattering_guard.v1` | `DAMAGE`, `BREAK_DAMAGE`, `APPLY_VULNERABLE` |
| `ws.blunt.bulwark_strike.v1` | `DAMAGE`, `BREAK_DAMAGE`, `APPLY_SELF_DAMAGE_REDUCTION` |
| `ws.blunt.resonance_bell.v1` | `AREA_DAMAGE`, `BREAK_DAMAGE`, `HIGH_BREAK_BONUS_BRANCH` |

### 3.4 지팡이·곡괭이

| skillId | operationIds |
|---|---|
| `ws.staff.ember_orb.v1` | `SPAWN_PROJECTILE`, `AREA_DAMAGE`, `APPLY_BURN` |
| `ws.staff.purifying_field.v1` | `SPAWN_PARTY_AREA`, `HEAL`, `CORRUPTION_GAIN_MODIFIER`, `CLEANSE_WEAK_STATUS` |
| `ws.staff.frost_ring.v1` | `AREA_DAMAGE`, `APPLY_SLOW`, `CENTER_NORMAL_ROOT_BRANCH` |
| `ws.staff.chain_spark.v1` | `CHAIN_TARGET_SELECT`, `MULTITARGET_DAMAGE_SEQUENCE`, `BREAK_DAMAGE` |
| `ws.pickaxe.armor_drill.v1` | `MULTIHIT_DAMAGE`, `BREAK_DAMAGE`, `APPLY_DEFENCE_MODIFIER` |
| `ws.pickaxe.fault_line.v1` | `SPAWN_LINE_AREA`, `DAMAGE`, `BREAK_DAMAGE` |
| `ws.pickaxe.expose_seam.v1` | `DAMAGE`, `APPLY_MARK`, `APPLY_PARTY_PENETRATION_MODIFIER` |
| `ws.pickaxe.anchor_breaker.v1` | `OBJECTIVE_OR_ARMOR_TARGET_BRANCH`, `DAMAGE`, `BREAK_DAMAGE`, `NORMAL_TARGET_DAMAGE_SCALE` |

### 3.5 삼지창·권투

| skillId | operationIds |
|---|---|
| `ws.trident.cast_recall.v1` | `TRIDENT_STATE_BRANCH`, `TRIDENT_THROW`, `TRIDENT_RECALL_PATH_DAMAGE` |
| `ws.trident.anchor_thrust.v1` | `DAMAGE`, `BREAK_DAMAGE`, `MARK_TARGET_ROOT_BRANCH` |
| `ws.trident.returning_crescent.v1` | `SPAWN_OUTBOUND_RETURN_PROJECTILE`, `DAMAGE`, `PER_TARGET_HIT_CAP` |
| `ws.trident.current_cage.v1` | `SPAWN_ENEMY_AREA`, `APPLY_SLOW`, `BOUNDARY_CROSS_DAMAGE` |
| `ws.unarmed.slip_counter.v1` | `PRECISION_DEFENCE_REQUIRE`, `DAMAGE`, `AP_RESTORE_ON_HIT` |
| `ws.unarmed.breaker_rush.v1` | `MULTIHIT_DAMAGE`, `BREAK_DAMAGE` |
| `ws.unarmed.guard_intercept.v1` | `QUEUE_PARTY_DIRECT_DAMAGE_SHARE` |
| `ws.unarmed.centered_stance.v1` | `APPLY_SELF_DEFENCE_MODIFIER`, `COMBO_FINISHER_EXTEND_DURATION` |

## 4. 공용 액티브 10개

| skillId | operationIds |
|---|---|
| `ws.common.field_bandage.v1` | `CONSUMABLE_RESERVE`, `CHANNEL`, `HEAL`, `CLEANSE_BLEED_STACK`, `CONSUMABLE_COMMIT` |
| `ws.common.quick_purify.v1` | `CONSUMABLE_RESERVE`, `CHANNEL`, `REDUCE_PERSONAL_CORRUPTION`, `CONSUMABLE_COMMIT` |
| `ws.common.ap_stim.v1` | `CONSUMABLE_RESERVE`, `AP_RESTORE`, `CONSUMABLE_COMMIT` |
| `ws.common.threat_ping.v1` | `APPLY_MARK`, `APPLY_PARTY_BREAK_MODIFIER` |
| `ws.common.guard_step.v1` | `MOVE_SELF`, `APPLY_SELF_DAMAGE_REDUCTION` |
| `ws.common.break_call.v1` | `APPLY_PARTY_BREAK_MODIFIER` |
| `ws.common.rescue_line.v1` | `CONSUMABLE_RESERVE`, `MOVE_DOWNED_PARTY_MEMBER`, `CONSUMABLE_COMMIT` |
| `ws.common.emergency_cover.v1` | `CONSUMABLE_RESERVE`, `SPAWN_COVER_ENTITY`, `CONSUMABLE_COMMIT` |
| `ws.common.shared_breath.v1` | `SELECT_NEARBY_OTHER_PARTY`, `AP_RESTORE_WITH_ENCOUNTER_CAP` |
| `ws.common.control_break.v1` | `CONSUMABLE_RESERVE`, `HARD_CC_SELF_USE_REJECT`, `CLEANSE_CONTROL_STATUS_PRIORITY`, `CONSUMABLE_COMMIT` |

정화 앰풀은 `REDUCE_PERSONAL_CORRUPTION`만 실행하고 STATUS를 제거하지 않는다. `CLEANSE_CONTROL_STATUS_PRIORITY`는 신경 안정제를 소비하며 ROOT→SILENCE→DISARM 중 하나만 제거한다.

## 5. 상황 스킬 4개

| skillId | operationIds |
|---|---|
| `ws.context.rescue.v1` | `RESCUE_TARGET_VALIDATE`, `RESCUE_CHANNEL_JOIN`, `RESCUE_PROGRESS_COMMIT` |
| `ws.context.field_repair.v1` | `CONSUMABLE_RESERVE`, `CHANNEL`, `EQUIPMENT_DURABILITY_REPAIR`, `CONSUMABLE_COMMIT` |
| `ws.context.sample.v1` | `SAMPLE_TARGET_VALIDATE`, `CHANNEL`, `SAMPLE_LEDGER_REGISTER` |
| `ws.context.ammo_share.v1` | `PARTY_AMMO_TRANSFER_VALIDATE`, `AMMO_OWNERSHIP_TRANSFER_TX` |

## 6. 합계·검증

| 범위 | 스킬 수 | 빈 operation 배열 |
|---|---:|---:|
| 기본 공격 | 10 | 0 |
| 무기 액티브 | 40 | 0 |
| 공용 액티브 | 10 | 0 |
| 상황 스킬 | 4 | 0 |
| 전체 | 64 | 0 |

L0는 skill ID 64개 1:1, operation ID 허용 목록, 비용 자원에 대응하는 `*_RESERVE/*_COMMIT`, 자식 개체 참조, 상태 ID 참조와 빈 배열 0을 검사한다. L1은 operation별 정상·거부·롤백과 같은 cast 멱등성을 검사한다. L2는 실제 slot 0 입력, projectile/area 표현, 다단 hit 상한, 리소스 팩 거부 폴백을 검증한다. 64개 중 하나라도 설명문 파싱이나 `effect=DAMAGE/SUPPORT` 단일 분기로만 실행되면 스킬 도메인을 `VERIFIED`로 승격하지 않는다.

## 7. 명시 실행 파라미터 계약

아래 64행은 `SKILL-LIST-001`의 자연어를 실행 단위로 정규화한 컴파일 권위다. `targetSpec`의 거리·반경은 block, 각도는 degree, 시간은 tick, 비율은 소수다. `costSpec`의 AP·탄약·아이템·내구는 공통 `validate→reserve→resolve→commit` 거래가 처리하며 operation 이름이나 태그에서 추론하지 않는다.

| failurePolicy | 규칙 |
|---|---|
| `SKFP-V0` | 대상·장비·AP·탄약·상태·경로의 사전 검증 실패는 비용·쿨다운·내구 0 |
| `SKFP-CAST` | startup을 통과해 cast가 발행된 뒤 빗나감·엄폐 충돌·대상 소멸은 예약 비용과 쿨다운 commit |
| `SKFP-PATH` | 경로 사전 검사 실패는 0, 이동 시작 뒤 충돌·ROOT는 cast 비용 commit하고 안전한 마지막 지점에서 종료 |
| `SKFP-CHANNEL` | AP·쿨다운은 채널 시작 commit, 아이템은 reserve 후 효과 성공 시 commit; 중단 시 아이템 반환 |
| `SKFP-CONTEXT` | 상황 행동의 대상 원장이 소유하며 성공 effect와 수량 이전을 같은 TX로 commit |

### 7.1 기본 공격 10행

| skillId | executionProfile·timing | targetSpec | costSpec | parameterPayload | failurePolicy |
|---|---|---|---|---|---|
| `ws.basic.sword.v1` | `BASIC_MELEE 3/2/7` | `ARC range=3.2 angle=90 max=1` | `AP=6 DUR=1 interval=12` | `damageCoeff=0.90; break=35; comboMax=3; comboWindow=24` | `SKFP-V0` |
| `ws.basic.axe.v1` | `BASIC_HEAVY 7/3/8` | `ARC range=3.5 angle=105 max=1` | `AP=10 DUR=1 interval=18` | `damageCoeff=1.25; break=80; heavy=true` | `SKFP-V0` |
| `ws.basic.bow.v1` | `CHARGE_RELEASE charge=7..20 recovery=13` | `RAY range=32 projectileRadius=0.25 max=1` | `AP=8 DUR=1 AMMO_NORMAL=1 interval=14` | `damageCoeff=0.65..1.10; break=25; velocity=2.4..3.2; projectileEntity=ENT-PROJ-PLAYER-BOW` | `SKFP-CAST` |
| `ws.basic.crossbow.v1` | `PROJECTILE 6/1/11` | `RAY range=32 projectileRadius=0.25 max=1` | `AP=9 DUR=1 AMMO_SELECTED=1 LOADED=1 interval=18` | `damageCoeff=1.15; break=65; magazineSpend=1; projectileEntity=ENT-PROJ-PLAYER-CROSSBOW` | `SKFP-CAST` |
| `ws.basic.dagger.v1` | `BASIC_MELEE 2/1/4` | `ARC range=2.8 angle=65 max=1` | `AP=4 DUR=1 interval=7` | `damageCoeff=0.55; break=18; comboMax=4; comboWindow=16` | `SKFP-V0` |
| `ws.basic.blunt.v1` | `BASIC_HEAVY 6/3/7` | `ARC range=3.3 angle=100 max=1` | `AP=9 DUR=1 interval=16` | `damageCoeff=1.10; break=120; impact=true` | `SKFP-V0` |
| `ws.basic.staff.v1` | `PROJECTILE 4/1/9` | `RAY range=24 projectileRadius=0.40 max=1` | `AP=7 DUR=1 interval=14` | `damageCoeff=0.95; break=40; velocity=2.0; blockCollision=DESPAWN; projectileEntity=ENT-PROJ-PLAYER-STAFF` | `SKFP-CAST` |
| `ws.basic.pickaxe.v1` | `BASIC_MELEE 5/2/8` | `ARC range=3.2 angle=70 max=1` | `AP=8 DUR=1 interval=15` | `damageCoeff=1.05; break=95; noTargetMineBranch=true` | `SKFP-V0` |
| `ws.basic.trident.v1` | `BASIC_MELEE 4/2/8` | `THRUST range=3.8 width=0.65 max=1` | `AP=7 DUR=1 interval=14` | `damageCoeff=0.95; break=50; vanillaThrow=false` | `SKFP-V0` |
| `ws.basic.unarmed.v1` | `BASIC_MELEE 2/1/3` | `ARC range=2.7 angle=60 max=1` | `AP=3 interval=6` | `damageCoeff=0.45; break=22; comboMax=4; comboWindow=14` | `SKFP-V0` |

### 7.2 무기 액티브 40행

| skillId | executionProfile | targetSpec | costSpec | parameterPayload | failurePolicy |
|---|---|---|---|---|---|
| `ws.sword.guard_cut.v1` | `MELEE_STANDARD` | `ARC range=3.5 angle=80 max=1` | `AP=22 CD=100 DUR=1` | `damageCoeff=1.40; break=80; parryAgeMax=20; parryDamageMultiplier=1.20` | `SKFP-V0` |
| `ws.sword.rally_lunge.v1` | `MOBILITY` | `PATH range=5 width=1.0 then ARC range=3.2 angle=70 max=1` | `AP=34 CD=160 DUR=1` | `damageCoeff=1.80; markTicks=100; stopBeforeSolid=0.35` | `SKFP-PATH` |
| `ws.sword.turning_guard.v1` | `AREA_CAST` | `SELF_RADIUS radius=3 max=8` | `AP=28 CD=140 DUR=2` | `damageCoeff=1.10; break=70; projectileDamageTakenMultiplier=0.60; buffTicks=20` | `SKFP-CAST` |
| `ws.sword.focused_duel.v1` | `UTILITY` | `RAY range=12 max=1 enemy` | `AP=32 CD=180 DUR=0` | `markTicks=160; bossMarkTicks=120; personalDamageMultiplier=1.10; cancelOnOtherTargetHit=true` | `SKFP-V0` |
| `ws.axe.cleaving_charge.v1` | `MELEE_HEAVY` | `ARC range=3.8 angle=85 max=1` | `AP=36 CD=120 DUR=2` | `damageCoeff=2.20; break=180; defenceDelta=-16; debuffTicks=120` | `SKFP-V0` |
| `ws.axe.execution_arc.v1` | `MELEE_HEAVY` | `ARC range=4.0 angle=110 max=3` | `AP=48 CD=200 DUR=2` | `damageCoeff=2.80; executeHpRatio=0.30; executeMultiplier=1.35` | `SKFP-V0` |
| `ws.axe.hook_sweep.v1` | `AREA_CAST` | `CONE range=4 angle=120 max=5` | `AP=30 CD=140 DUR=2` | `damageCoeff=1.35; break=110; normalPullDistance=2; bossPull=0` | `SKFP-CAST` |
| `ws.axe.resolute_hew.v1` | `MELEE_HEAVY` | `ARC range=3.8 angle=80 max=1` | `AP=42 CD=220 DUR=2` | `damageCoeff=2.40; startupControlImmunity=true; startupDamageCapMaxHp=0.12` | `SKFP-V0` |
| `ws.bow.pin_shot.v1` | `PROJECTILE` | `RAY range=32 max=1` | `AP=22 CD=80 DUR=1 AMMO_NORMAL=1` | `damageCoeff=1.25; normalRootTicks=24; bossBreak=100; projectileEntity=ENT-PROJ-PLAYER-BOW` | `SKFP-CAST` |
| `ws.bow.barbed_rain.v1` | `PROJECTILE` | `GROUND_RADIUS range=28 radius=4 projectiles=5 perTargetMax=2` | `AP=38 CD=180 DUR=1 AMMO_NORMAL=1` | `perArrowDamageCoeff=0.55; bleedStacksPerTarget=1; volleySpread=4; projectileEntity=ENT-PROJ-PLAYER-BOW` | `SKFP-CAST` |
| `ws.bow.piercing_lane.v1` | `PROJECTILE` | `LINE range=28 width=0.8 max=3` | `AP=30 CD=120 DUR=1 AMMO_NORMAL=1` | `damageCoeff=1.55; perTargetMultiplier=0.85; penetrationOrderCap=3; projectileEntity=ENT-PROJ-PLAYER-BOW` | `SKFP-CAST` |
| `ws.bow.rescue_flare.v1` | `PROJECTILE` | `GROUND_RADIUS range=28 radius=4 partyOnly` | `AP=35 CD=240 DUR=1 AMMO_NORMAL=1` | `damageCoeff=0; speedDelta=0.08; rescueSpeedDelta=0.15; durationTicks=120; projectileEntity=ENT-PROJ-PLAYER-RESCUE-FLARE; areaProfile=AREA-SKILL-RESCUE-FLARE` | `SKFP-CAST` |
| `ws.crossbow.breach_bolt.v1` | `PROJECTILE` | `RAY range=32 max=1` | `AP=32 CD=120 DUR=1 AMMO_SELECTED=1 LOADED=1` | `damageCoeff=1.80; break=160; defenceDelta=-20; debuffTicks=120; projectileEntity=ENT-PROJ-PLAYER-CROSSBOW` | `SKFP-CAST` |
| `ws.crossbow.magazine_volley.v1` | `PROJECTILE` | `RAY range=30 shots=3 perTargetMax=3` | `AP=46 CD=200 DUR=1 AMMO_SELECTED=3 LOADED=3` | `perShotDamageCoeff=0.75; sameTargetMultipliers=1.0,0.70,0.45; slowRatio=0.25; slowTicks=60; projectileEntity=ENT-PROJ-PLAYER-CROSSBOW` | `SKFP-CAST` |
| `ws.crossbow.suppressive_bolt.v1` | `PROJECTILE` | `GROUND_RADIUS range=28 radius=3 enemyOnly` | `AP=28 CD=140 DUR=1 AMMO_SELECTED=1 LOADED=1` | `damageCoeff=1.20; outgoingDamageMultiplier=0.88; areaTicks=100; projectileEntity=ENT-PROJ-PLAYER-CROSSBOW; areaProfile=AREA-SKILL-SUPPRESSIVE` | `SKFP-CAST` |
| `ws.crossbow.snap_reload.v1` | `UTILITY` | `SELF` | `AP=24 CD=280 DUR=0 AMMO_SELECTED=2` | `magazineAdd=2; magazineCapQuery=true; damageCoeff=0` | `SKFP-V0` |
| `ws.dagger.shadowstep.v1` | `MOBILITY` | `FLANK_PATH range=3 then ARC range=2.8 angle=60 max=1` | `AP=18 CD=80 DUR=1` | `damageCoeff=1.35; precisionDodgeWindow=20; apDiscount=4; noTeleport=true` | `SKFP-PATH` |
| `ws.dagger.venom_flurry.v1` | `MELEE_LIGHT` | `ARC range=2.8 angle=65 max=1 hits=5` | `AP=30 CD=160 DUR=1` | `perHitDamageCoeff=0.32; hitInterval=2; poisonApplyHitMax=2; castTargetHitMax=5` | `SKFP-V0` |
| `ws.dagger.hemorrhage_cut.v1` | `MELEE_LIGHT` | `ARC range=2.9 angle=70 max=1` | `AP=24 CD=120 DUR=1` | `damageCoeff=1.20; perBleedStackBonus=0.06; bonusCap=0.30; refreshBleed=false` | `SKFP-V0` |
| `ws.dagger.fading_feint.v1` | `MOBILITY` | `BACK_PATH range=4 self` | `AP=26 CD=200 DUR=1` | `threatPriorityMultiplier=0.40; threatTicks=40; nextAttackBonusCoeff=0.90; bonusExpiryTicks=100` | `SKFP-PATH` |
| `ws.blunt.quake_break.v1` | `MELEE_HEAVY` | `SELF_CONE radius=3 angle=120 max=6` | `AP=42 CD=160 DUR=2` | `damageCoeff=2.00; break=350; normalAirborneTicks=16; bossBreakConvert=120` | `SKFP-V0` |
| `ws.blunt.shattering_guard.v1` | `MELEE_HEAVY` | `ARC range=3.6 angle=75 max=1` | `AP=52 CD=240 DUR=2` | `damageCoeff=2.60; break=480; vulnerableStacks=1; vulnerableTicks=120` | `SKFP-V0` |
| `ws.blunt.bulwark_strike.v1` | `MELEE_STANDARD` | `ARC range=3.4 angle=80 max=1` | `AP=34 CD=160 DUR=1` | `damageCoeff=1.50; break=180; selfDamageTakenMultiplier=0.85; buffTicks=40` | `SKFP-V0` |
| `ws.blunt.resonance_bell.v1` | `AREA_CAST` | `SELF_RADIUS radius=5 max=10` | `AP=46 CD=260 DUR=2` | `damageCoeff=0.80; break=260; targetBreakRatioThreshold=0.80; damageAndBreakMultiplier=1.25` | `SKFP-CAST` |
| `ws.staff.ember_orb.v1` | `PROJECTILE` | `GROUND_RADIUS range=24 radius=3 max=8` | `AP=26 CD=100 DUR=1` | `damageCoeff=1.40; burnStacks=2; projectileEntity=ENT-PROJ-PLAYER-STAFF; areaProfile=AREA-SKILL-EMBER-IMPACT` | `SKFP-CAST` |
| `ws.staff.purifying_field.v1` | `AREA_CAST` | `SELF_RADIUS radius=4 partyOnly max=4` | `AP=50 CD=280 DUR=2` | `areaTicks=100; healPerSecondMaxHp=0.02; healFlatPerSecond=15; corruptionGainMultiplier=0.70; weakCleansePerTarget=1` | `SKFP-CAST` |
| `ws.staff.frost_ring.v1` | `AREA_CAST` | `SELF_RADIUS radius=4 centerRadius=1.5 max=10` | `AP=32 CD=160 DUR=2` | `damageCoeff=0.90; slowRatio=0.35; slowTicks=80; centerNormalRootTicks=16; bossRootConvertBreak=80` | `SKFP-CAST` |
| `ws.staff.chain_spark.v1` | `PROJECTILE` | `CHAIN firstRange=24 jumpRange=6 max=4` | `AP=40 CD=200 DUR=1` | `damageCoefficients=1.10,0.90,0.75,0.60; breakEach=55; uniqueTargets=true` | `SKFP-CAST` |
| `ws.pickaxe.armor_drill.v1` | `MELEE_HEAVY` | `THRUST range=3.5 width=0.7 max=1 hits=2` | `AP=28 CD=120 DUR=2` | `perHitDamageCoeff=0.70; totalBreak=220; defenceDelta=-24; debuffTicks=120` | `SKFP-V0` |
| `ws.pickaxe.fault_line.v1` | `AREA_CAST` | `LINE range=8 width=1.5 max=8` | `AP=44 CD=220 DUR=2` | `damageCoeff=2.20; break=320; areaProfile=AREA-SKILL-FAULT-LINE; blockDamage=false` | `SKFP-CAST` |
| `ws.pickaxe.expose_seam.v1` | `MELEE_STANDARD` | `THRUST range=3.5 width=0.8 max=1` | `AP=25 CD=140 DUR=1` | `damageCoeff=1.00; markTicks=120; partyPenetrationDelta=6` | `SKFP-V0` |
| `ws.pickaxe.anchor_breaker.v1` | `MELEE_HEAVY` | `ARC range=3.8 angle=70 max=1` | `AP=48 CD=240 DUR=2` | `damageCoeff=2.50; break=420; objectiveOrArmorRequiredForFull=true; normalDamageMultiplier=0.70; blockDropBonus=0` | `SKFP-V0` |
| `ws.trident.cast_recall.v1` | `PROJECTILE_STATEFUL` | `THROW_RAY range=24; RECALL_PATH width=1 maxPerTarget=1` | `HELD AP=34 CD=120 DUR=2; THROWN AP=20 CD=80 DUR=0` | `throwDamageCoeff=1.60; throwBreak=120; recallDamageCoeff=0.75; thrownMaxTicks=120; maxDistance=32; projectileEntity=ENT-PROJ-PLAYER-TRIDENT` | `SKFP-CAST` |
| `ws.trident.anchor_thrust.v1` | `MELEE_STANDARD` | `THRUST range=4 width=0.75 max=1` | `AP=30 CD=120 DUR=1` | `damageCoeff=1.65; break=130; markedTargetRootTicks=16; bossRootConvertBreak=80` | `SKFP-V0` |
| `ws.trident.returning_crescent.v1` | `PROJECTILE` | `OUTBOUND_RETURN range=18 width=1.2 perTargetMax=2` | `AP=38 CD=180 DUR=1` | `damageCoeffPerPass=0.90; passCount=2; projectileEntity=ENT-PROJ-PLAYER-TRIDENT` | `SKFP-CAST` |
| `ws.trident.current_cage.v1` | `AREA_CAST` | `GROUND_RADIUS range=16 radius=4 max=10` | `AP=46 CD=260 DUR=2` | `areaTicks=80; slowRatio=0.25; boundaryDamageCoeff=0.60; boundaryHitMaxPerTarget=1; areaProfile=AREA-SKILL-CURRENT-CAGE` | `SKFP-CAST` |
| `ws.unarmed.slip_counter.v1` | `MELEE_LIGHT` | `ARC range=2.9 angle=70 max=1` | `AP=14 CD=60` | `precisionDefenceAgeMax=20; damageCoeff=1.50; apRestoreOnHit=8` | `SKFP-V0` |
| `ws.unarmed.breaker_rush.v1` | `MELEE_LIGHT` | `ARC range=2.8 angle=70 max=1 hits=4` | `AP=26 CD=140` | `perHitDamageCoeff=0.30; perHitBreak=28; hitInterval=2; castTargetHitMax=4` | `SKFP-V0` |
| `ws.unarmed.guard_intercept.v1` | `UTILITY` | `PARTY range=3 max=1` | `AP=20 CD=160` | `durationTicks=40; nextDirectHitShareRatio=0.25; excluded=ENVIRONMENT,WIPE,POSITIONAL; forcedMove=false` | `SKFP-V0` |
| `ws.unarmed.centered_stance.v1` | `UTILITY` | `SELF` | `AP=32 CD=240` | `defenceDelta=16; baseTicks=100; comboFinishExtendTicks=10; maxTicks=140` | `SKFP-V0` |

### 7.3 공용 액티브 10행

| skillId | executionProfile | targetSpec | costSpec | parameterPayload | failurePolicy |
|---|---|---|---|---|---|
| `ws.common.field_bandage.v1` | `CHANNEL` | `SELF` | `AP=18 CD=240 ITEM=WSI-CONS-BANDAGE×1 channel=25` | `healMaxHp=0.08; healFlat=40; cleanseBleedStacks=1` | `SKFP-CHANNEL` |
| `ws.common.quick_purify.v1` | `CHANNEL` | `SELF` | `AP=22 CD=300 ITEM=WSI-CONS-PURIFY_AMPOULE×1 channel=20` | `personalCorruptionDelta=-15; cleanseStatus=0` | `SKFP-CHANNEL` |
| `ws.common.ap_stim.v1` | `UTILITY` | `SELF` | `AP=10 CD=600 ITEM=WSI-CONS-AP_STIM×1` | `apImmediate=10; pulseAmount=3; pulseInterval=20; pulseCount=5; overflowDiscard=true; exhaustionClear=false` | `SKFP-V0` |
| `ws.common.threat_ping.v1` | `UTILITY` | `RAY range=24 enemy max=1` | `AP=15 CD=240` | `markTicks=120; partyBreakMultiplier=1.04; targetIcdTicks=240` | `SKFP-V0` |
| `ws.common.guard_step.v1` | `MOBILITY` | `PATH range=1.5 self` | `AP=20 CD=160` | `damageTakenMultiplier=0.80; buffTicks=16; invulnerable=false` | `SKFP-PATH` |
| `ws.common.break_call.v1` | `UTILITY` | `RAY range=24 enemy max=1` | `AP=25 CD=300` | `partyBreakMultiplier=1.06; durationTicks=80; bossGlobalCap=true` | `SKFP-V0` |
| `ws.common.rescue_line.v1` | `UTILITY` | `DOWNED_PARTY range=8 max=1` | `AP=24 CD=320 ITEM=WSI-CONS-RESCUE_BRACE×1` | `maxMoveDistance=3; direction=NEAREST_SAFE_FROM_THREAT; autoRescue=false` | `SKFP-V0` |
| `ws.common.emergency_cover.v1` | `UTILITY` | `GROUND range=6 width=3` | `AP=30 CD=400 ITEM=WSI-CONS-REPAIR_KIT×1` | `durationTicks=240; hp=600; projectileBlock=true; entityId=ENT-DEPLOY-EMERGENCY-COVER` | `SKFP-V0` |
| `ws.common.shared_breath.v1` | `UTILITY` | `PARTY_RADIUS radius=5 excludeSelf max=3` | `AP=28 CD=360` | `apRestore=8; encounterTargetCap=1; deadOrDownedExcluded=true` | `SKFP-V0` |
| `ws.common.control_break.v1` | `UTILITY` | `SELF` | `AP=35 CD=500 ITEM=WSI-CONS-NEURAL_STABILIZER×1` | `priority=ROOT,SILENCE,DISARM; removeCount=1; rejectWhileHardCc=true` | `SKFP-V0` |

### 7.4 상황 스킬 4행

| skillId | executionProfile | targetSpec | costSpec | parameterPayload | failurePolicy |
|---|---|---|---|---|---|
| `ws.context.rescue.v1` | `CONTEXT_CHANNEL` | `DOWNED_PARTY interactionRange=3` | `AP_TOTAL_BY_INJURY=25,35,45` | `baseChannelTicksByInjury=100,140,180; joinerMax=2; progressGraceTicks=10; decayAfterGrace=true` | `SKFP-CONTEXT` |
| `ws.context.field_repair.v1` | `CONTEXT_CHANNEL` | `OWNED_EQUIPMENT_GUI max=1` | `ITEM=WSI-CONS-REPAIR_KIT×1 channel=40` | `normalRepairRatio=0.25; brokenRepairCapRatio=0.40; combatAllowed=false` | `SKFP-CONTEXT` |
| `ws.context.sample.v1` | `CONTEXT_CHANNEL` | `VALID_SAMPLE_TARGET range=3 max=1` | `channel=40..80 PORTABLE=WSI-PORTABLE-SAMPLE_EXTRACTOR` | `registerAmount=1; duplicateQuotaReject=true; reward=false` | `SKFP-CONTEXT` |
| `ws.context.ammo_share.v1` | `CONTEXT_TX` | `ONLINE_PARTY_GUI max=1` | `AP=0 combatAllowed=false` | `amountMin=1; amountMax=ownedLedger; preserveAmmoType=true; atomicOwnershipTransfer=true` | `SKFP-CONTEXT` |

## 8. 파라미터 완료 검증

- 위 표의 skill ID는 정확히 64개이며 `SKILL-LIST-001`·operation 표와 1:1이다.
- 모든 행은 `executionProfile`, `targetSpec`, `costSpec`, `parameterPayload`, `failurePolicy`를 가지며 공백이 없다.
- `AP`, `CD`, `DUR`, `AMMO`, `ITEM`, `LOADED`는 공통 거래 필드다. effect operation이 비용을 중복 소비하지 않는다.
- 자식 entity ID는 `ENTITY-LIST-001`에 존재해야 하며 P0 종료 전 고아를 0으로 만든다.
- `areaProfile`은 Bukkit entity ID가 아니라 cast 소유 `AreaInstance` 프로필이다. `AREA-SKILL-RESCUE-FLARE/SUPPRESSIVE/EMBER-IMPACT/FAULT-LINE/CURRENT-CAGE`는 각 행의 형태·지속·관계 필드를 그대로 사용하고 parent cast 종료·회차 종료·서버 복구에서 정리한다.
- 시간·거리·계수·상태·최대 대상·다단 상한을 표시명·설명·태그에서 재추론하지 않는다.
