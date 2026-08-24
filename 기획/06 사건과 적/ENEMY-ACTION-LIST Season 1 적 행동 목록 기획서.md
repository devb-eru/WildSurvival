# WildSurvival Season 1 적 행동 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `ENEMY-ACTION-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 적용 범위 | 일반·정예·보스 소환체·Final 소환체 53종의 행동 bundle 53개와 행동 69개 |
| 상위 기준 | `ENEMY-001`, `ENEMY-DATA-D20-001`, `ENEMY-DATA-D50-001`, `ENTITY-LIST-001` |
| 데이터 리비전 | `enemy-action-s1-r1` |
| 최종 수정일 | 2026-08-24 |

## 1. 목적과 권위

이 문서는 53개 적 bundle을 대표 행동 하나로 축약하지 않기 위한 영구 action ID 원장이다. 피해·전조·상태·대응의 게임 디자인 수치는 각 행의 `수치 권위` 문서가 소유하고, 이 문서는 소유자→행동 ID 배열과 공통 실행 프로필을 소유한다.

- r2는 아래 배열의 순서와 수를 그대로 저장한다. `*-PRIMARY`, 표시명 해시, 문장 분할로 ID를 생성하지 않는다.
- 행동 레코드는 최소 `id, ownerId, profileId, telegraphTicks, startupTicks, activeTicks, recoveryTicks, cooldownTicks, range, targetPolicy, damage, penetration, breakDamage, statusId, statusStacks, effectOpcode, childEntityIds, tags, responseTags`를 가진다.
- 수치 권위 표에 값이 있으면 프로필 기본값보다 우선하며, 생성 시 명시 필드로 복사한다. 런타임은 Markdown이나 `raw` 문장을 읽지 않는다.
- 한 bundle의 후보 선택은 거리·쿨다운·페이즈·대상 조건을 만족한 행동만 대상으로 한다. 배열 첫 행동을 무조건 반복하지 않는다.
- 소환체 행동은 부모 `encounterId`를 요구하고 `NO_REWARD, NO_SAMPLE, NO_AUGMENT_TRIGGER, NO_CONTRIBUTION`을 유지한다.

## 2. 공통 실행 프로필

아래 값은 해당 수치가 원 행동 행에 없을 때만 쓰는 명시 기본값이다. 시간 단위는 tick, 거리는 block이다.

| profileId | 역할 | 전조 | startup/active/recovery | cooldown | range | 기본 대상 |
|---|---|---:|---|---:|---:|---|
| `EAP-CHASER` | CHASER | 11 | 4/4/10 | 40 | 3.2 | NEAREST_ALIVE_PLAYER |
| `EAP-FLANKER` | FLANKER | 15 | 4/4/10 | 70 | 3.2 | LOWEST_RECENT_AGGRO_PLAYER |
| `EAP-RANGED` | RANGED | 14 | 4/4/10 | 60 | 16 | VISIBLE_ALIVE_PLAYER |
| `EAP-BRUISER` | BRUISER | 22 | 4/4/10 | 80 | 3.2 | CURRENT_AGGRO_PLAYER |
| `EAP-CONTROLLER` | CONTROLLER | 20 | 4/4/10 | 90 | 16 | VALID_CONTROL_TARGET |
| `EAP-ARTILLERY` | ARTILLERY | 24 | 4/4/10 | 100 | 16 | GROUND_AT_VISIBLE_PLAYER |
| `EAP-DEFENDER` | DEFENDER | 12 | 4/4/10 | 80 | 5 | PROTECTED_ALLY_OR_CURRENT_TARGET |
| `EAP-SUPPORT` | SUPPORT | 24 | 4/4/10 | 120 | 16 | VALID_ALLY_OR_OBJECTIVE |
| `EAP-ELITE` | ELITE | 24 | 4/4/10 | 100 | 6 | ACTION_SPECIFIC_PLAYER_OR_ALLY |
| `EAP-SIEGE` | SIEGE | 28 | 4/4/10 | 120 | 5 | ACTIVE_FACILITY_OR_CURRENT_TARGET |
| `EAP-SUMMON` | SUMMON | 16 | 4/4/10 | 60 | 12 | PARENT_APPROVED_TARGET |

같은 bundle의 행동은 각각 독립 쿨다운을 가진다. 단, 활성 행동이 끝나기 전 다른 행동을 시작하지 않으며 기본 전역 recovery는 실행된 행동의 `recoveryTicks`다.

## 3. Day 1~10 행동 배열

| ownerId | profileId | actionIds | 수치 권위 |
|---|---|---|---|
| `EN-D1-01` | `EAP-CHASER` | `ED10-HUNGRY-CLAW` | `ENEMY-001 §5.1 굶주린 배회자` |
| `EN-D1-02` | `EAP-FLANKER` | `ED10-FLANK-LEAP` | `ENEMY-001 §5.1 측면 거미` |
| `EN-D2-01` | `EAP-RANGED` | `ED10-BONE-ARROW` | `ENEMY-001 §5.1 뼈 사수` |
| `EN-D4-01` | `EAP-BRUISER` | `ED10-GROUND-SMASH` | `ENEMY-001 §5.1 부패한 강타자` |
| `EN-D4-02` | `EAP-ARTILLERY` | `ED10-RESONANCE-BLAST` | `ENEMY-001 §5.1 불안정 폭발체` |
| `EN-D5-01` | `EAP-CONTROLLER` | `ED10-TOXIC-BITE` | `ENEMY-001 §5.1 오염 송곳니` |
| `EN-D6-01` | `EAP-CONTROLLER` | `ED10-PULL-LINE` | `ENEMY-001 §5.1 침수 견인자` |
| `EN-D6-02` | `EAP-FLANKER` | `ED10-DIVE` | `ENEMY-001 §5.1 공중 포착자` |
| `EN-D7-01` | `EAP-DEFENDER` | `ED10-FRONT-GUARD` | `ENEMY-001 §5.1 철편 방어자` |
| `EN-D7-02` | `EAP-SUPPORT` | `ED10-WEAKEN-ORB` | `ENEMY-001 §5.1 포자 조율자` |
| `EN-D8-E01` | `EAP-ELITE` | `ED10-ECHO-COMBO`, `ED10-TRACK-CHARGE`, `ED10-RESONANCE-MARK` | `ENEMY-001 §6.1` |
| `EN-D9-E01` | `EAP-ELITE` | `ED10-CHILL-SHOT`, `ED10-LINE-BARRAGE`, `ED10-ESCORT-SIGNAL` | `ENEMY-001 §6.2` |

합계는 owner 12, action 16이다.

## 4. Day 11~20 행동 배열

| ownerId | profileId | actionIds | 수치 권위 |
|---|---|---|---|
| `EN-D11-01` | `EAP-CHASER` | `ED20-WEAKEN-CLAW` | `ENEMY-DATA-D20-001 §4 쇠약 배회자` |
| `EN-D11-02` | `EAP-RANGED` | `ED20-TOXIN-DART` | `ENEMY-DATA-D20-001 §4 독침 사수` |
| `EN-D12-01` | `EAP-ARTILLERY` | `ED20-THERMAL-CORE` | `ENEMY-DATA-D20-001 §4 잔불 포자체` |
| `EN-D12-02` | `EAP-DEFENDER` | `ED20-REVERSE-CARAPACE` | `ENEMY-DATA-D20-001 §4 충격 갑피병` |
| `EN-D13-01` | `EAP-FLANKER` | `ED20-HEMATIC-LEAP` | `ENEMY-DATA-D20-001 §4 혈흔 추적자` |
| `EN-D13-02` | `EAP-SUPPORT` | `ED20-STATUS-AMPLIFY` | `ENEMY-DATA-D20-001 §4 반응 조율자` |
| `EN-D14-01` | `EAP-BRUISER` | `ED20-EXECUTION-SLAM` | `ENEMY-DATA-D20-001 §4 붉은 처형자` |
| `EN-D14-02` | `EAP-CONTROLLER` | `ED20-BIND-LINE` | `ENEMY-DATA-D20-001 §4 구조 결박자` |
| `EN-D15-01` | `EAP-DEFENDER` | `ED20-ALTERNATING-BARRIER` | `ENEMY-DATA-D20-001 §4 공명 방벽체` |
| `EN-D15-02` | `EAP-SUPPORT` | `ED20-AUGMENT-SIGNAL` | `ENEMY-DATA-D20-001 §4 과부하 전령` |
| `EN-D16-01` | `EAP-CONTROLLER` | `ED20-BURROW-BURST` | `ENEMY-DATA-D20-001 §4 잠복 안개체` |
| `EN-D16-02` | `EAP-CHASER` | `ED20-THERMAL-SHARD` | `ENEMY-DATA-D20-001 §4 열성 운반체` |
| `EN-D17-01` | `EAP-CONTROLLER` | `ED20-SILENCE-WAVE` | `ENEMY-DATA-D20-001 §4 침묵 울음체` |
| `EN-D17-02` | `EAP-BRUISER` | `ED20-DISARM-CUT` | `ENEMY-DATA-D20-001 §4 무장 절단자` |
| `EN-D18-E01` | `EAP-ELITE` | `ED20-SUTURE-LINK`, `ED20-STATUS-TRANSFER`, `ED20-CLING-TRAIL` | `ENEMY-DATA-D20-001 §5.1` |
| `EN-D18-E02` | `EAP-ELITE` | `ED20-BLOCKING-CHORUS`, `ED20-SILENT-FOLLOWER`, `ED20-HIVE-RUPTURE` | `ENEMY-DATA-D20-001 §5.2` |
| `EN-D19-E01` | `EAP-ELITE` | `ED20-ESCORT-LINK`, `ED20-FRONT-CARAPACE`, `ED20-RESCUER-CHARGE` | `ENEMY-DATA-D20-001 §5.3` |
| `EN-D20-A01` | `EAP-SUMMON` | `ED20-SUMMON-TOXIN-SPRAY` | `ENEMY-DATA-D20-001 §6` |
| `EN-D20-A02` | `EAP-SUMMON` | `ED20-SUMMON-THERMAL-WAVE` | `ENEMY-DATA-D20-001 §6` |
| `EN-D20-A03` | `EAP-SUMMON` | `ED20-SUMMON-NEURAL-BLOCK` | `ENEMY-DATA-D20-001 §6` |
| `EN-D20-A04` | `EAP-SUMMON` | `ED20-SUMMON-HEMATIC-LEAP` | `ENEMY-DATA-D20-001 §6` |

합계는 owner 21, action 27이다.

## 5. Day 21~50 행동 배열

| ownerId | profileId | actionIds | 수치 권위 |
|---|---|---|---|
| `EN-D21-01` | `EAP-CHASER` | `ED50-CORRUPTION-TRAIL`, `ED50-CARRIER-BURST` | `ENEMY-DATA-D50-001 §3 D21` |
| `EN-D22-01` | `EAP-FLANKER` | `ED50-MIMIC-MUTATION`, `ED50-BACKSTEP-CUT` | `ENEMY-DATA-D50-001 §3 D22` |
| `EN-D23-01` | `EAP-SUPPORT` | `ED50-PURIFY-DRAIN`, `ED50-PARASITE-LEAP` | `ENEMY-DATA-D50-001 §3 D23` |
| `EN-D24-01` | `EAP-SIEGE` | `ED50-LEDGER-MARK`, `ED50-SIEGE-RAM` | `ENEMY-DATA-D50-001 §3 D24` |
| `EN-D27-01` | `EAP-SUPPORT` | `ED50-PURIFY-JAM` | `ENEMY-DATA-D50-001 §3 D27` |
| `EN-D28-E01` | `EAP-ELITE` | `ED50-MUTATION-ATLAS` | `ENEMY-DATA-D50-001 §3 D28` |
| `EN-D31-01` | `EAP-DEFENDER` | `ED50-HEAVY-CHANNEL`, `ED50-REAR-VENT` | `ENEMY-DATA-D50-001 §3 D31` |
| `EN-D33-01` | `EAP-SIEGE` | `ED50-FACILITY-BORE` | `ENEMY-DATA-D50-001 §3 D33` |
| `EN-D34-01` | `EAP-BRUISER` | `ED50-PARRY-ADAPT` | `ENEMY-DATA-D50-001 §3 D34` |
| `EN-D37-01` | `EAP-SUPPORT` | `ED50-SUTURE-BREAK` | `ENEMY-DATA-D50-001 §3 D37` |
| `EN-D38-E01` | `EAP-ELITE` | `ED50-PHASE-RESET`, `ED50-PHASE-SHELL` | `ENEMY-DATA-D50-001 §3 D38` |
| `EN-D41-01` | `EAP-SUPPORT` | `ED50-AUGMENT-HERALD` | `ENEMY-DATA-D50-001 §3 D41` |
| `EN-D44-01` | `EAP-SIEGE` | `ED50-SPLIT-TARGET` | `ENEMY-DATA-D50-001 §3 D44` |
| `EN-D46-E01` | `EAP-ELITE` | `ED50-BOSS-ECHO` | `ENEMY-DATA-D50-001 §3 D46` |
| `EN-D47-01` | `EAP-CONTROLLER` | `ED50-RELAY-SEVER` | `ENEMY-DATA-D50-001 §3 D47` |
| `EN-D49-E01` | `EAP-ELITE` | `ED50-COLLAPSE-GATE` | `ENEMY-DATA-D50-001 §3 D49` |
| `EN-F50-A01` | `EAP-SUMMON` | `ED50-CALIBRATION-INTRUDE` | `ENEMY-DATA-D50-001 §6` |
| `EN-F50-A02` | `EAP-SUMMON` | `ED50-OUTPUT-SABOTAGE` | `ENEMY-DATA-D50-001 §6` |
| `EN-F50-A03` | `EAP-SUMMON` | `ED50-ECHO-PROJECTILE` | `ENEMY-DATA-D50-001 §6` |
| `EN-F50-A04` | `EAP-SUMMON` | `ED50-SIEGE-NODE` | `ENEMY-DATA-D50-001 §6` |

합계는 owner 20, action 26이다.

## 6. 전체 합계와 보스 경계

| 범위 | bundle | action |
|---|---:|---:|
| Day 1~10 일반·정예 | 12 | 16 |
| Day 11~20 일반·정예·소환체 | 21 | 27 |
| Day 21~50 일반·정예·Final 소환체 | 20 | 26 |
| 일반 적 계 | 53 | 69 |
| 보스 4기, 각 12패턴 | 4 | 48 |
| 전체 r2 action bundle | 57 | 117 |

보스 action ID와 수치는 `BOSS-001~004`가 계속 소유한다. 일반 적 69개를 전개하면서 보스 48개를 이름·효과 기준으로 합치거나 재사용하지 않는다.

## 7. 구현·검증 계약

- `ACT-<enemyId>` 53개 owner 중복·누락 0, owner별 action 수 1~3
- action ID 69개 중복 0, `*-PRIMARY` 0, 표시명·접두사 런타임 추론 0
- 정예 5종(`D8`, `D9`, `D18×2`, `D19`)은 각각 3행, 원장의 세 행동 모두 실행 후보
- D21/D22/D23/D24/D31/D38은 각각 2행, 나머지 후반 적은 명시 수만 유지
- 상태 행동은 `STATUS-001`의 적용·저항·면역 경로만 사용하고 바닐라 PotionEffect를 별도 권위로 만들지 않음
- 자식 개체 행동은 `ENTITY-LIST-001`의 고정 `childEntityIds`만 생성하고 부모 종료 TX에서 정리
- 53개 bundle 각각 정상 실행, 부적합 대상 거부, 쿨다운, 재시작 복구 fixture 보유
- 실제 클라이언트에서 전조와 안전 위치를 리소스 팩 수락·거부 양쪽으로 판독 가능

위 조건과 action 69·보스 48의 합계 117을 통과하기 전에는 적·엔티티 도메인을 `VERIFIED`로 승격하지 않는다.
