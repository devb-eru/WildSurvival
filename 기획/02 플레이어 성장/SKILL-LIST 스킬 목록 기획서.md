# WildSurvival 스킬 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `SKILL-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 최종 수정일 | 2026-08-23 |
| 상위 기준 | `SKILL-001~002`, `WEAPON-002`, `COMBAT`, `STATUS`, `BREAK`, `AUG-LIST` |
| 적용 범위 | 10개 무기군 선택 스킬과 비무기 공용 스킬 |
| 구현 | 커스텀 GUI 로드아웃, 서버 권위 실행, 인벤토리·키 입력 라우팅 |

## 1. 로드아웃 계약

| 구분 | 장착 수 | 변경 조건 |
|---|---:|---|
| 장착 무기 기본 공격 | 1, 자동 | 권투를 제외한 현재 주무기의 `BASIC_ATTACK`, 액티브 슬롯 미점유 |
| 권투 기본 공격 | 가상 프로필 1, 자동 | 주무기 원장과 슬롯 0이 모두 비었을 때 |
| 무기 액티브 | 후보 4개 중 최대 3개 | 비전투·비추적·비보스 안전 상태 |
| 비무기 공용 액티브 | 후보 중 최대 4개 | 비전투 안전 상태 |
| 퀵 아이템 | 소모품·음식·유틸리티 최대 4개 | 비전투 안전 상태, 액티브가 아님 |
| 회피 | 고정 | COMBAT-003 입력 설정만 변경 가능 |
| 보조무기 행동 | 장비에 따라 1개 | OFF_WEAPON 장착과 동기화 |
| 상황 상호작용 | 조건 충족 시 자동 | 구조·시설·소모품 GUI |

- 스킬 변경은 커스텀 GUI에서만 수행한다.
- 진행 중 시전, 투사체, 장판과 쿨다운이 있으면 장착 해제가 그 효과를 초기화하지 않는다.
- 같은 스킬을 두 슬롯에 중복 장착할 수 없다.
- 삼지창 `공명 투창/회수 전류`는 상태에 따라 입력이 바뀌는 연결 스킬이며 한 슬롯만 차지한다.
- 무기 스킬이 하나도 장착되지 않은 잘못된 저장값은 해당 무기 기본 추천 2개로 복구한다.
- 입력 바인드는 `BASIC=L`, `W1=R`, `W2=Shift+L`, `W3=Shift+R`, `OFF=F`를 사용한다.
- `HOLD_RELEASE` 스킬은 `W1=R`에만 배치하며 GUI가 호환되지 않는 바인드를 거부한다.
- 공용 액티브 `C1~C4`는 전투 자세에서 `Shift+2~5`, 퀵 아이템 `Q1~Q4`는 `Shift+6~9`로 실행한다.
- 모든 스킬·퀵 아이템 입력은 입력 직전 선택 슬롯이 0일 때만 유효하다. 숫자 조합 실행 뒤 서버가 선택 슬롯을 0으로 되돌리고, 자체 슬롯 변경 이벤트는 재귀 입력으로 해석하지 않는다.
- Shift 없는 `2~9`는 slot 1~8의 바닐라 아이템을 선택한다. slot 1~8에서 L/R/Shift+숫자는 WS 스킬로 가로채지 않는다.
- `F`는 보조무기 행동이며 바닐라 양손 교환을 취소한다. 보조무기가 없으면 실패 안내만 하고 주무기를 옮기지 않는다.

## 2. 해금과 선택

| 조건 | 결과 |
|---|---|
| 무기군 템플릿 최초 발견 | 해당 무기 기본 추천 2개 해금 |
| 레벨 3 + C03 | 해당 무기 후보 3번째 해금 |
| 레벨 6 + C05 | 해당 무기 후보 4번째 해금 |
| Day 10 보스 완료 | 선택된 영웅 장비의 고유 스킬 변형만 해금 |

해금은 후보 수를 늘릴 뿐 강제 교체하지 않는다. 레벨 3·6·10 개인 증강 선택과 스킬 선택은 별도 GUI와 트랜잭션을 사용한다.

## 2.1 기본 공격 10종

기본 공격은 액티브 슬롯을 차지하지 않으며, 권투를 제외하면 바닐라 피해를 전부 취소하고 아래 서버 실행으로 대체한다.

| ID | 무기군 | AP | 최소 간격 | 계수 | 브레이크 | 추가 비용·상태 | 실패 폴백 |
|---|---|---:|---:|---:|---:|---|---|
| `ws.basic.sword.v1` | SWORD | 6 | 0.62초 | ATK 0.90 | 35 | 내구 1, 3타 콤보 | 블록 손상 취소 |
| `ws.basic.axe.v1` | AXE | 10 | 0.92초 | ATK 1.25 | 80 | 내구 1, 강공 태그 | 블록 손상 취소 |
| `ws.basic.bow.v1` | BOW | 8 | 0.72초 | ATK 0.65~1.10 | 25 | 화살 1, 내구 1, 0.35~1.0초 차지 | 탄약 없으면 미실행 |
| `ws.basic.crossbow.v1` | CROSSBOW | 9 | 0.90초 | ATK 1.15 | 65 | 선택 볼트 1, 내구 1, 탄창 상태 | 탄약·장전 없으면 미실행 |
| `ws.basic.dagger.v1` | DAGGER | 4 | 0.34초 | ATK 0.55 | 18 | 내구 1, 4타 콤보 | 블록 손상 취소 |
| `ws.basic.blunt.v1` | BLUNT | 9 | 0.82초 | ATK 1.10 | 120 | 내구 1, IMPACT | 블록 손상 취소 |
| `ws.basic.staff.v1` | STAFF | 7 | 0.68초 | ATK 0.95 | 40 | 내구 1, 직선 구체 | 블록·장식 충돌 시 소멸 |
| `ws.basic.pickaxe.v1` | PICKAXE | 8 | 0.74초 | ATK 1.05 | 95 | 내구 1 | 대상 없음+허용 블록이면 AP 없이 채굴 |
| `ws.basic.trident.v1` | TRIDENT | 7 | 0.70초 | ATK 0.95 | 50 | 내구 1, 근접 찌르기 | 바닐라 투척 없음 |
| `ws.basic.unarmed.v1` | UNARMED | 3 | 0.32초 | ATK 0.45 | 22 | 내구 없음, 4타 콤보 | 대상 없음+허용 블록이면 손 파괴 |

- AP·탄약·내구는 `validate→reserve→hit/resolve→commit` 순서로 처리한다. AP 부족·탄약 없음·BROKEN 장비는 공격 애니메이션만 재생하지 않고 즉시 이유를 표시한다.
- 근접 기본 공격은 서버 ray/hitbox로 단일 대상을 확정한다. 아군·관전·사망·블록 뒤·사거리 밖 개체는 대상이 아니다.
- 활·석궁·지팡이 투사체는 서버 소유 projectile ID를 사용하며 바닐라 화살 피해 이벤트를 원본 공격으로 다시 계산하지 않는다.
- 곡괭이 채굴 폴백은 `PlayerItemDamageEvent`를 원장 내구에 한 번만 반영한다. 같은 스윙의 전투 적중과 블록 파괴 동시 commit을 금지한다.
- BASIC_ATTACK 결과도 상태·브레이크·증강·기여도·피해 숫자 표시의 정상 root event가 된다.

## 2.2 실행 프로필

| profile | startup/active/recovery | 기본 대상 | 액티브 내구 | 중단 |
|---|---|---|---:|---|
| `MELEE_LIGHT` | 4/2/7틱 | 3.2블록 단일·호 | 1 | HARD_CC, 무기 해제 |
| `MELEE_STANDARD` | 6/2/10틱 | 3.5블록 단일·호 | 1 | HARD_CC, 무기 해제 |
| `MELEE_HEAVY` | 11/3/17틱 | 3.8블록 단일·호 | 2 | 명시적 TENACITY 외 HARD_CC |
| `PROJECTILE` | 6/1/10틱 | 조준 ray 32블록 | 1+탄약 | startup HARD_CC |
| `AREA_CAST` | 9/1/14틱 | 자신/지점 반경 | 2 | startup HARD_CC |
| `MOBILITY` | 3/1/10틱 | 통과 가능한 경로 | 1 | ROOT·경로 실패 |
| `UTILITY` | 0~10/1/8틱 | 자신·아군·시설 | 0~1 | 스킬별 채널 규칙 |

각 스킬은 표의 태그와 핵심 효과로 프로필을 결정한다. `HEAVY/BREAK` 우선 `MELEE_HEAVY`, `RANGED/THROW`는 `PROJECTILE`, `AREA`는 `AREA_CAST`, `MOBILITY`는 `MOBILITY`, 나머지 근접은 무기 속도에 따라 LIGHT/STANDARD다. 둘 이상이면 데이터의 `executionProfile` 명시값이 우선하며 런타임 추론은 하지 않는다.

## 3. 검 스킬

| ID | 이름 | AP·쿨다운 | 핵심 효과 | 태그 | 해금 |
|---|---|---|---|---|---|
| `ws.sword.guard_cut.v1` | 받아치기 베기 | 22, 5초 | ATK 1.40, 브레이크 80; 패링 후 피해 +20% | `SWORD`, `PARRY`, `COUNTER` | 기본 |
| `ws.sword.rally_lunge.v1` | 집결 돌진 | 34, 8초 | 5블록 돌진, ATK 1.80, MARK 5초 | `SWORD`, `MOBILITY`, `MARK` | 기본 |
| `ws.sword.turning_guard.v1` | 선회 방벽 | 28, 7초 | 반경 3블록 ATK 1.10, 브레이크 70; 1초간 투사체 피해 -40% | `SWORD`, `AREA`, `GUARD` | 레벨 3 |
| `ws.sword.focused_duel.v1` | 결투 표식 | 32, 9초 | 단일 대상 MARK 8초, 자신이 주는 피해 +10%; 다른 대상 공격 시 종료 | `SWORD`, `MARK`, `SINGLE_TARGET` | 레벨 6 |

결투 표식은 파티 피해를 증가시키지 않으며 보스에게 지속시간 6초를 적용한다.

## 4. 도끼 스킬

| ID | 이름 | AP·쿨다운 | 핵심 효과 | 태그 | 해금 |
|---|---|---|---|---|---|
| `ws.axe.cleaving_charge.v1` | 쪼개는 축력 | 36, 6초 | ATK 2.20, 브레이크 180, DEF -16 | `AXE`, `HEAVY`, `ARMOR_BREAK` | 기본 |
| `ws.axe.execution_arc.v1` | 처형 호 | 48, 10초 | ATK 2.80, 낮은 HP 추가 계수 | `AXE`, `EXECUTE`, `BURST` | 기본 |
| `ws.axe.hook_sweep.v1` | 갈고리 휩쓸기 | 30, 7초 | 120도 ATK 1.35, 일반 적 2블록 끌기, 브레이크 110 | `AXE`, `AREA`, `PULL` | 레벨 3 |
| `ws.axe.resolute_hew.v1` | 버티는 참격 | 42, 11초 | ATK 2.40, STARTUP 중 경직 면역; 받은 피해 최대 HP 12% 상한 | `AXE`, `TENACITY`, `HEAVY` | 레벨 6 |

## 5. 활 스킬

| ID | 이름 | AP·쿨다운 | 핵심 효과 | 태그 | 해금 |
|---|---|---|---|---|---|
| `ws.bow.pin_shot.v1` | 고정 사격 | 22, 4초 | ATK 1.25, ROOT 또는 보스 브레이크 변환 | `BOW`, `ROOT`, `CONTROL` | 기본 |
| `ws.bow.barbed_rain.v1` | 가시비 | 38, 9초 | 5발 영역, 대상당 최대 2발, BLEED | `BOW`, `AREA`, `BLEED` | 기본 |
| `ws.bow.piercing_lane.v1` | 관통선 | 30, 6초 | 28블록 직선 ATK 1.55, 최대 3대상, 대상마다 피해 15% 감소 | `BOW`, `PIERCE`, `RANGED` | 레벨 3 |
| `ws.bow.rescue_flare.v1` | 구조 신호 화살 | 35, 12초 | 착탄 4블록 파티 SPD +8, 구조속도 +15%, 6초 | `BOW`, `SUPPORT`, `RESCUE` | 레벨 6 |

구조 신호 화살은 적 피해와 상태를 발생시키지 않으며 일반 화살 1개를 소비한다.

## 6. 석궁 스킬

| ID | 이름 | AP·쿨다운 | 핵심 효과 | 태그 | 해금 |
|---|---|---|---|---|---|
| `ws.crossbow.breach_bolt.v1` | 파쇄 볼트 | 32, 6초 | ATK 1.80, 브레이크 160, DEF -20 | `CROSSBOW`, `BREACH`, `ARMOR_BREAK` | 기본 |
| `ws.crossbow.magazine_volley.v1` | 탄창 일제사 | 46, 10초 | 3발 연사, 같은 대상 피해 감쇠, SLOW | `CROSSBOW`, `MULTIHIT`, `MAGAZINE` | 기본 |
| `ws.crossbow.suppressive_bolt.v1` | 제압 볼트 | 28, 7초 | ATK 1.20, 5초 영역; 적 Damage Dealt -12% | `CROSSBOW`, `AREA`, `WEAKEN` | 레벨 3 |
| `ws.crossbow.snap_reload.v1` | 순간 장전 | 24, 14초 | 현재 선택 탄약 2발 장전, 탄약 실제 소비; 발사 피해 없음 | `CROSSBOW`, `RELOAD`, `UTILITY` | 레벨 6 |

순간 장전은 탄창 상한을 넘지 않으며 탄약이 없으면 AP와 쿨다운을 시작하지 않는다.

## 7. 단검 스킬

| ID | 이름 | AP·쿨다운 | 핵심 효과 | 태그 | 해금 |
|---|---|---|---|---|---|
| `ws.dagger.shadowstep.v1` | 그림자 걸음 베기 | 18, 4초 | 측후방 이동, ATK 1.35; 정확 회피 후 AP -4 | `DAGGER`, `DODGE`, `MOBILITY` | 기본 |
| `ws.dagger.venom_flurry.v1` | 맹독 난무 | 30, 8초 | 5타, POISON 최대 2회 | `DAGGER`, `MULTIHIT`, `POISON` | 기본 |
| `ws.dagger.hemorrhage_cut.v1` | 개방 상처 | 24, 6초 | ATK 1.20; BLEED 중첩당 +6%, 최대 +30%, 중첩 갱신 없음 | `DAGGER`, `BLEED`, `CONSUME` | 레벨 3 |
| `ws.dagger.fading_feint.v1` | 이탈 기만 | 26, 10초 | 4블록 후퇴, 2초간 적 우선순위 감소, 다음 공격 ATK 0.90 추가 | `DAGGER`, `DODGE`, `UTILITY` | 레벨 6 |

이탈 기만은 투명화가 아니며 보스 고정 패턴과 이미 확정된 공격 대상을 지우지 않는다.

## 8. 둔기 스킬

| ID | 이름 | AP·쿨다운 | 핵심 효과 | 태그 | 해금 |
|---|---|---|---|---|---|
| `ws.blunt.quake_break.v1` | 지진 파쇄 | 42, 8초 | ATK 2.00, 브레이크 350, AIRBORNE 변환 | `BLUNT`, `BREAK`, `CONTROL` | 기본 |
| `ws.blunt.shattering_guard.v1` | 분쇄 강타 | 52, 12초 | ATK 2.60, 브레이크 480, VULNERABLE | `BLUNT`, `BREAK`, `VULNERABLE` | 기본 |
| `ws.blunt.bulwark_strike.v1` | 버팀 타격 | 34, 8초 | ATK 1.50, 브레이크 180; 2초간 자신 Damage Taken -15% | `BLUNT`, `GUARD`, `IMPACT` | 레벨 3 |
| `ws.blunt.resonance_bell.v1` | 공명 종타 | 46, 13초 | 반경 5블록 브레이크 260, 피해 ATK 0.80; BREAK 80% 이상이면 +25% | `BLUNT`, `AREA`, `BREAK` | 레벨 6 |

## 9. 지팡이 스킬

| ID | 이름 | AP·쿨다운 | 핵심 효과 | 태그 | 해금 |
|---|---|---|---|---|---|
| `ws.staff.ember_orb.v1` | 잔불 구체 | 26, 5초 | ATK 1.40 폭발, BURN 2중첩 | `STAFF`, `MAGIC`, `BURN` | 기본 |
| `ws.staff.purifying_field.v1` | 정화장 | 50, 14초 | 5초 영역 회복·오염 획득 감소·약한 해제 | `STAFF`, `AREA`, `SUPPORT` | 기본 |
| `ws.staff.frost_ring.v1` | 냉각 고리 | 32, 8초 | 반경 4블록 ATK 0.90, SLOW 35% 4초; 일반 적 중심부 ROOT 0.8초 | `STAFF`, `AREA`, `SLOW` | 레벨 3 |
| `ws.staff.chain_spark.v1` | 연쇄 전류 | 40, 10초 | 최대 4대상 ATK 1.10/0.90/0.75/0.60, 대상당 브레이크 55 | `STAFF`, `CHAIN`, `MULTITARGET` | 레벨 6 |

## 10. 곡괭이 스킬

| ID | 이름 | AP·쿨다운 | 핵심 효과 | 태그 | 해금 |
|---|---|---|---|---|---|
| `ws.pickaxe.armor_drill.v1` | 장갑 천공 | 28, 6초 | 2타, 브레이크 220, DEF -24 | `PICKAXE`, `PENETRATE`, `ARMOR_BREAK` | 기본 |
| `ws.pickaxe.fault_line.v1` | 단층선 | 44, 11초 | 8블록 직선, ATK 2.20, 브레이크 320 | `PICKAXE`, `LINE`, `BREAK` | 기본 |
| `ws.pickaxe.expose_seam.v1` | 약층 노출 | 25, 7초 | ATK 1.00, MARK 6초; 표식 대상 파티 PEN +6 | `PICKAXE`, `MARK`, `COOP` | 레벨 3 |
| `ws.pickaxe.anchor_breaker.v1` | 고정점 파쇄 | 48, 12초 | 구조물·장갑 대상 ATK 2.50, 브레이크 420; 일반 대상 피해 70% | `PICKAXE`, `OBJECTIVE`, `BREAK` | 레벨 6 |

모든 곡괭이 스킬은 자원 채굴량과 블록 드롭을 증가시키지 않는다.

## 11. 삼지창 스킬

| ID | 이름 | AP·쿨다운 | 핵심 효과 | 태그 | 해금 |
|---|---|---|---|---|---|
| `ws.trident.cast_recall.v1` | 공명 투창/회수 전류 | 34·20, 6·4초 | HELD면 투척, THROWN이면 피해 경로 회수 | `TRIDENT`, `THROW`, `RECALL` | 기본 |
| `ws.trident.anchor_thrust.v1` | 정박 찌르기 | 30, 6초 | ATK 1.65, 브레이크 130, MARK 대상 ROOT 0.8초 | `TRIDENT`, `ROOT`, `MARK` | 기본 |
| `ws.trident.returning_crescent.v1` | 회귀 초승달 | 38, 9초 | 전방·귀환 2회 ATK 0.90, 대상별 최대 2회 | `TRIDENT`, `RECALL`, `MULTIHIT` | 레벨 3 |
| `ws.trident.current_cage.v1` | 전류 우리 | 46, 13초 | 반경 4블록 4초, SLOW 25%; 경계 통과 시 ATK 0.60 1회 | `TRIDENT`, `AREA`, `CONTROL` | 레벨 6 |

회귀 초승달과 전류 우리는 주무기 인스턴스를 던지지 않는 에너지 실행이며 HELD 상태를 유지한다.

## 12. 권투 스킬

| ID | 이름 | AP·쿨다운 | 핵심 효과 | 태그 | 해금 |
|---|---|---|---|---|---|
| `ws.unarmed.slip_counter.v1` | 흘리기 반격 | 14, 3초 | 정확 회피·패링 후 ATK 1.50, 적중 AP 8 환급 | `UNARMED`, `COUNTER`, `DODGE` | 기본 |
| `ws.unarmed.breaker_rush.v1` | 파쇄 연타 | 26, 7초 | 4타, 총 ATK 1.20, 총 브레이크 112 | `UNARMED`, `MULTIHIT`, `BREAK` | 기본 |
| `ws.unarmed.guard_intercept.v1` | 가로막기 | 20, 8초 | 3블록 내 파티를 향한 다음 직접 공격 피해 25% 분담, 2초 | `UNARMED`, `COOP`, `GUARD` | 레벨 3 |
| `ws.unarmed.centered_stance.v1` | 중심 자세 | 32, 12초 | 5초간 DEF +16, 권투 4타 완주 시 남은 시간 0.5초 증가, 최대 7초 | `UNARMED`, `STANCE`, `COMBO` | 레벨 6 |

가로막기는 위치를 강제 이동시키지 않으며 환경·전멸·POSITIONAL 피해를 분담하지 않는다.

## 13. 비무기 공용 액티브

모든 무기군이 사용할 수 있으며 공용 액티브 `C1~C4` 중 한 칸을 차지한다. 아이템을 소비하는 공용 액티브와 퀵 아이템 직접 사용은 같은 개인 아이템 원장과 공용 쿨다운 그룹을 사용한다.

| ID | 이름 | AP·쿨다운 | 소모품 | 효과 | 태그 |
|---|---|---|---|---|---|
| `ws.common.field_bandage.v1` | 전투 붕대 | 18, 12초 | 붕대 1 | 1.25초 채널, HP 8%+40 회복, BLEED 1중첩 제거 | `COMMON`, `HEALING`, `BLEED` |
| `ws.common.quick_purify.v1` | 신속 정화 | 22, 15초 | 정화 앰풀 1 | 1초 채널, 개인 오염 -15; STATUS 제거 없음 | `COMMON`, `CORRUPTION`, `CONSUMABLE` |
| `ws.common.ap_stim.v1` | 행동 자극 | 10, 30초 | AP 자극제 1 | 즉시 AP +30, 최대 AP 초과분 소멸; 탈진 해제 없음 | `COMMON`, `AP`, `CONSUMABLE` |
| `ws.common.threat_ping.v1` | 위협 지목 | 15, 12초 | 없음 | 24블록 대상 MARK 6초, 파티 브레이크 +4% | `COMMON`, `MARK`, `COOP` |
| `ws.common.guard_step.v1` | 방어 보법 | 20, 8초 | 없음 | 0.8초 Damage Taken -20%, 무적 아님, 이동 거리 1.5블록 | `COMMON`, `GUARD`, `MOBILITY` |
| `ws.common.break_call.v1` | 파쇄 지시 | 25, 15초 | 없음 | 대상 4초간 파티 BREAK_DAMAGE +6%, 보스 전체 상한 적용 | `COMMON`, `BREAK`, `COOP` |
| `ws.common.rescue_line.v1` | 구조 견인선 | 24, 16초 | 구조 고정대 1 | 8블록 내 빈사 파티원을 안전 방향으로 최대 3블록 이동 | `COMMON`, `RESCUE`, `UTILITY` |
| `ws.common.emergency_cover.v1` | 임시 엄폐 | 30, 20초 | 야전 수리 키트 1 | 12초, 폭 3블록 투사체 엄폐 오브젝트; HP 600 | `COMMON`, `FACILITY`, `DEFENSE` |
| `ws.common.shared_breath.v1` | 함께 숨쉬기 | 28, 18초 | 없음 | 5블록 파티 AP 8 회복, 사용자 제외; 전투당 대상 1회 | `COMMON`, `AP`, `SUPPORT` |
| `ws.common.control_break.v1` | 억제 해제 | 35, 25초 | 신경 안정제 1 | 자신 ROOT→SILENCE→DISARM 우선순위 중 1개 해제; HARD_CC 중 자기 사용 불가 | `COMMON`, `CONTROL_BREAK`, `CLEANSE` |

### 13.1 공용 쿨다운 그룹

| 그룹 | 포함 스킬 | 그룹 쿨다운 |
|---|---|---:|
| `COMMON_HEAL` | 전투 붕대 | 12초 |
| `COMMON_CLEANSE` | 신속 정화, 억제 해제 | 15초 |
| `COMMON_AP_ITEM` | 행동 자극 | 30초 |
| `COMMON_RESCUE` | 구조 견인선 | 12초 |
| `COMMON_DEPLOYABLE` | 임시 엄폐 | 20초 |

스킬 자체 쿨다운과 그룹 쿨다운 중 긴 값을 적용한다.

## 14. 상황형 공용 스킬

아래 행동은 액티브 슬롯을 차지하지 않고 유효 대상·아이템·시설 상호작용으로 나타난다.

| ID | 이름 | 입력 | 비용·시간 | 결과 |
|---|---|---|---|---|
| `ws.context.rescue.v1` | 빈사 구조 | 웅크리기+대상 우클릭 | 부상에 따라 AP 25~45, 채널 | DEATH 구조 규칙 실행 |
| `ws.context.field_repair.v1` | 야전 수리 | 수리 키트 우클릭 | 키트 1, 2초 | 선택 장비 내구 복구 |
| `ws.context.sample.v1` | 표본 채취 | 채취기+대상 우클릭 | 2~4초 | RES 표본 원장 등록 |
| `ws.context.ammo_share.v1` | 탄약 전달 | 파티 GUI | AP 0, 비전투 | 탄약 소유권을 원자적으로 이전 |

## 15. 증강·장비 태그 연결

| 빌드 축 | 주 스킬 태그 | 보조 태그 | 후보 가중 조건 |
|---|---|---|---|
| 회피 반격 | `DODGE`, `COUNTER` | `AGILE`, `MOBILITY` | 정확 회피 4회 이상 |
| 브레이크 | `BREAK`, `IMPACT` | `PARRY`, `ARMOR_BREAK` | 파티 브레이크 기여 20% 이상 |
| 상태 DOT | `BLEED`, `POISON`, `BURN` | `MULTIHIT`, `CONSUME` | 같은 상태 유효 적용 8회 |
| 원거리 탄약 | `RANGED`, `AMMO`, `RELOAD` | `PIERCE`, `MAGAZINE` | 활·석궁 장착 |
| 지원·구조 | `SUPPORT`, `RESCUE` | `CLEANSE`, `AP` | 구조·회복 기여 발생 |
| 삼지창 | `TRIDENT`, `RECALL` | `THROW`, `MARK` | 투척·회수 적중 4회 |
| 권투 | `UNARMED`, `COUNTER` | `COMBO`, `DODGE` | 빈 주무기 유지 |

- 후보 가중은 해당 증강 등급 안의 드로우 가중치만 바꾸며 등급 고정 규칙을 바꾸지 않는다.
- 무기 교체로 태그가 사라져도 이미 선택한 증강은 다른 증강으로 자동 변환하지 않는다.

## 16. 검증 항목

- 장착 무기 기본 공격의 `BASIC_ATTACK` 자동 연결과 바닐라 피해 차단
- 권투 가상 기본 공격과 장착 무기 기본 공격의 저장·슬롯 분리
- 무기 액티브 4개 중 최대 3개와 공용 액티브 최대 4개 장착 제한
- 입력 직전 slot 0에서만 `L/R/Shift+L/Shift+R/F`, `Shift+2~9` 실행
- 공용 액티브·퀵 아이템 실행 뒤 slot 0 복귀와 서버 복귀 이벤트 재귀 차단
- Shift 없는 2~9와 slot 1~8 L/R의 바닐라 행동 보존
- 퀵 아이템 4칸 사용·채널·자동 보충과 공용 스킬 동시 소비 경합
- 레벨 3·6 및 발견 C03·C05 이중 해금
- 삼지창 연결 스킬 한 슬롯 처리
- 액티브 교체 후 기존 쿨다운·투사체·장판 유지
- 공용 소모품 원자 소비와 대상 실패 시 미소비
- 다단·영역 스킬 상태·흡혈·AP 환급 감쇠
- 보스 약화 REDUCED와 강한 CC BREAK_CONVERT
- 아군 공격·훈련 대상에서 AP 환급·증강·EXP 발동 차단
- 태그 기반 증강 드로우 가중과 무기 교체 비변환

## 17. 완료 기준

- 각 무기군에 실제 선택 가능한 액티브 4개와 기본 추천 2개가 있으며 최대 3개를 장착할 수 있다.
- 10개 무기군 기본 공격이 고정 ID, AP, 간격, 계수, 브레이크, 내구·탄약, 실패 폴백을 가진다.
- 비무기 공용 액티브와 상황형 상호작용이 분리된다.
- 공용 액티브 최대 4개와 퀵 아이템 최대 4개가 서로 다른 핫바 영역과 소비 원장을 사용한다.
- 모든 스킬에 AP, 쿨다운, 대상, 핵심 효과와 태그가 있다.
- 레벨·발견 해금과 커스텀 GUI 로드아웃 규칙이 정의된다.
- 증강·장비·상태·브레이크와 연결되는 태그가 일관된다.
