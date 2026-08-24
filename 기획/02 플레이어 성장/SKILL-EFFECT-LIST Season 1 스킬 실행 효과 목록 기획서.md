# WildSurvival Season 1 스킬 실행 효과 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `SKILL-EFFECT-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 적용 범위 | 기본 공격 10·무기 액티브 40·공용 액티브 10·상황 스킬 4의 operation 배열 |
| 상위 기준 | `SKILL-LIST-001`, `COMBAT-001`, `CORE-001`, `STATUS`, `BREAK` |
| 데이터 리비전 | `skill-effect-s1-r1` |
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
