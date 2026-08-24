# WildSurvival Season 1 실행 데이터 폐쇄 원장

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `PRODUCTION-DATA-CLOSURE-001` |
| 상태 | `ACTIVE_CONTROL` |
| 역할 | 고정 콘텐츠 목록의 자유 서술을 기계 실행 수치·증거·비용 계약으로 닫고, 사용자 결정이 필요한 항목을 분리하는 통제 원장 |
| 상위 기준 | `VISION-001`, `DOC-AUTHORITY-001`, `PRODUCTION-DESIGN-GATE-001`, `CONTENT-CATALOG-INDEX-001` |
| 실행 대상 | `ws-content-r2`, `S1_dev` |
| 최종 수정일 | 2026-08-24 |

## 1. 판정 원칙

이 문서는 기존 고정 ID를 새로 세지 않는다. 각 목록의 ID·도감 위치·표시명 소유권은 원 소유 문서에 남기고, 그 문서에 실행 수치가 없는 필드만 보충한다.

- `DATA_LOCKED`: 구현자가 추가 게임 디자인 판단 없이 데이터·런타임·테스트를 작성할 수 있다.
- `AUTHOR_DECISION_REQUIRED`: 하드코어 정체성이나 최종 서사처럼 코드 담당자가 임의로 결정할 수 없다.
- `CLIENT_E2E_REQUIRED`: 설계나 자동 테스트 문제가 아니라 실제 바닐라 클라이언트 입력·표현 증명이 필요하다.
- 생성기는 이 문서의 표를 자연어로 재해석하지 않는다. 다음 r2 스키마 리비전에서 각 값을 명시 필드로 옮긴 뒤에만 런타임이 사용한다.
- Story 한국어 대사·로그 원문은 이 문서가 창작하지 않는다. ID·트리거·폴백만 기존 `STORY-DATA-001` 권위를 따른다.

## 2. 카테고리별 고정 목록과 완료 단위

| 범주 | 소유 문서 | 고정 수 | 개별 완료 단위 | 현재 판정 |
|---|---|---:|---|---|
| 재료·진행 증명 | `MATERIAL-LIST-001`; T3~T6 투영 `HIGH-TIER-MATERIAL-LIST-001` | 59 | 전체59·상위23의 획득·개인/공용 귀속·소비·복구 | `PARTIAL_RUNTIME` |
| 비장비 아이템 | `ITEM-LIST-001` | 61 | 보유·사용·거부·환불·도감 | `PARTIAL_RUNTIME` |
| 장비·상위 도구 | `TOOL-LIST-001`; 분리 투영 `WEAPON/ARMOR/UTILITY-*-LIST-001` | 214 | 주/보조119·방어구/장신구79·유틸리티16의 제작·인스턴스·장착·고유 효과·파손·수리 | `PARTIAL_RUNTIME` |
| 레시피 | `RECIPE-LIST-001` | 315 | 3×3 일치·수량·태그·증명·원자 출력 | `RUNTIME_REVIEW` |
| 플레이어 스킬 | `SKILL-LIST-001` | 64 | 입력·비용·쿨다운·표적·효과·복구 | `PARTIAL_RUNTIME` |
| 개인 증강 | `AUG-LIST-001` | 50 | 드로우·선택·효과·상충·저장 | `PARTIAL_RUNTIME` |
| 파티 증강 | `AUG-LIST-002` | 16 | Day 드로우·투표·파티 효과·인원 보정 | `PARTIAL_RUNTIME` |
| 적·보스·지원 개체 | `ENTITY-LIST-001` | 91 | 생성·행동·수명·보상 권위·cleanup | `PARTIAL_RUNTIME/STRUCTURED_ONLY` |
| 시설 | `FACILITY-LIST-001` | 46 | 설치·작업 opcode·비용·파괴·복구 | `PARTIAL_RUNTIME` |
| 획득·드롭 | `LOOT-LIST-001` | 62 | 유효 참여·개인/파티 수령·중복 방지 | `PARTIAL_RUNTIME` |

고정 수 일치는 목록 완성 판정이고 실제 플레이 완성 판정이 아니다. 위 각 ID의 정상·거부·재접속·재시작 경로가 닫힐 때까지 카테고리를 `VERIFIED`로 올리지 않는다.

## 3. 소모품·개인 오염 실행 잠금

### 3.1 개인 오염

| 필드 | 고정값 |
|---|---|
| 저장 범위 | 플레이어별 `0.00~100.00`, 소수 둘째 자리까지 저장 |
| 갱신 주기 | `CORR-001`에 따라 5초, 효과 단계 판정은 값이 실제 임계선을 넘은 경우만 발행 |
| 사망·재접속 | 완전 사망에도 보존, 다른 회차 반입 금지, 재접속·재시작 복원 |
| 하한·상한 | 모든 증감 뒤 `clamp(0,100)` |
| 정화 앰풀 | `WSI-CONS-PURIFY_AMPOULE` 1개당 개인 오염 `-15.00`, 전투당 2회 |
| 상태 관계 | 정화 앰풀은 STATUS 중첩을 제거하지 않는다. 독·출혈·화상·CC는 전용 소모품만 처리 |

### 3.2 미확정이었던 소모품

| ID | 실행값 | 소비 시점 | 중첩·거부 |
|---|---|---|---|
| `WSI-CONS-AP_STIM` | 현재 AP `+30`, 최대 AP 초과분 소멸 | 검증 성공 뒤 즉시 | 전투당 1회, `EXHAUSTED`·탈진을 제거하지 않음 |
| `WSI-CONS-RESCUE_BRACE` | 다음 구조의 피격 중단 피해 임계 `+15%` | 첫 유효 구조 진행 틱 | 강화형과 합산하지 않고 높은 값 하나만 예약 |
| `WSI-CONS-REINFORCED_RESCUE_BRACE` | 다음 구조의 피격 중단 피해 임계 `+25%` | 첫 유효 구조 진행 틱 | 기본형 예약이 있으면 강화형으로 교체하고 기본형 예약은 유지 수량으로 반환 |

구조 대상·거리·AP 검사가 실패하거나 첫 유효 진행 틱 전에 구조를 취소하면 고정대를 소비하지 않는다. 소비 후 같은 구조가 중단되면 효과와 수량은 반환하지 않는다.

## 4. 탄약 선택·효과 잠금

### 4.1 입력과 원장

- 탄약 아이템 1개는 원장 1발이다. 제작 출력 수를 다시 묶음 배율로 곱하지 않는다.
- slot 1~8에서 탄약 아이템 우클릭은 해당 스택 전량을 개인 탄약 원장에 입금한다.
- 활·석궁을 slot 0에 장착한 전투 자세에서 `Q`를 누르면 호환 탄약을 순방향 순환한다. slot 1~8의 `Q`는 바닐라 드롭이며 WS가 가로채지 않는다.
- 장비 GUI에서도 활·석궁별 선택 탄약을 직접 지정할 수 있다. `Q` 순환과 GUI는 같은 `selectedAmmoByWeaponClass`를 변경한다.
- 선택 탄약이 0발이면 일반 화살로 한 번 폴백하고 액션바로 알린다. 일반 화살도 없으면 공격을 거부하며 AP·쿨다운·장전 상태를 소비하지 않는다.
- 발사 요청은 `validate→reserve ammo/AP→spawn/resolve→commit` 순서다. 서버가 발사체 생성 전 실패하면 전량 롤백한다.

### 4.2 탄약 5종

| ID | 호환 | 정확한 특수 효과 | 실패·중복 방지 |
|---|---|---|---|
| `WSI-AMMO-ARROW_BUNDLE` | 활·석궁 | 일반 공격 실행 데이터만 적용 | 발사 1회당 1발 |
| `WSI-AMMO-PIERCING_BOLT_BUNDLE` | 석궁 | 첫 유효 적중 1체의 피해 계산에 `PEN +12`; 일반 PEN 상한 유지 | 관통 후속 대상에 보너스 없음, 벽 충돌도 1발 소비 |
| `WSI-AMMO-PURIFY_ARROW_BUNDLE` | 활·석궁 | `CORRUPTED` 또는 `PURIFY_VULNERABLE` 대상에 `PURIFY_EXPOSED` 6초. 다음 파티 `PURIFY/CLEANSE` 효과가 오염 압력 1단을 낮추고 표식을 소비 | 일반 피해는 유지, WSR·표본·드롭 생성 0, 같은 sourceChain 재발동 0 |
| `WSI-AMMO-RESONANCE_BOLT_BUNDLE` | 석궁 | 적중 순간 패턴이 `INTERRUPTIBLE`이면 해당 발사의 최종 브레이크 `×1.50` | 그 밖에는 일반 볼트와 동일, 패턴 종료 뒤 지연 적중은 보너스 없음 |
| `WSI-AMMO-STABILIZER_DART_BUNDLE` | 석궁 | 32m 시야선의 아군 또는 아군 시설에 피해 없이 약한 개인/시설 오염 압력 1단 완화 | 대상별 20초, 적·빈 공간이면 발사 전 거부하고 탄약·AP·쿨다운 미소비 |

## 5. 시설 작업 비용·상태 잠금

### 5.1 명시 비용

| 시설·opcode | 입력 ID | 수량·단위 | 실행 계약 |
|---|---|---|---|
| `FAC-S03 / REFORGE` | `WSR-REDSTONE` | 재련 확정 1회당 1 | 후보 미리보기·취소는 무료, 새 옵션 커밋과 같은 TX에서 소비 |
| `FAC-S11 / MEDICAL` | `WSR-RATION` | 환자 1명의 부상 1단계 회복당 1 | 보스·잔존 공세 중 거부, 해독·구조 정보 열람은 무료 |
| `FAC-S12 / GRAVE_RECOVERY` | `WSR-REDSTONE` | 유품 이전 요청당 2 | 원본 유품 `remainsId` 잠금 뒤 소비, 이전 실패 시 전량 반환 |
| `FAC-D04 / TAUNT_BEACON` | `WSR-REDSTONE` | 활성화당 2 | 반경 32m 적 목표 우선 20초, 시설별 60초 재사용, 보스 강제 도발 금지 |
| `FAC-S17 / POWER_DISTRIBUTE` | `WSR-COAL` | 입금 1개당 원시 전력 10 | 배치 TX 뒤 연결 시설 전력 원장으로 분배 |

### 5.2 동력 분배

| 시설 레벨 | 손실률 | `WSR-COAL×5`의 전달 전력 |
|---:|---:|---:|
| 1 | 10% | 45 |
| 2 | 8% | 46 |
| 3 | 6% | 47 |
| 4 | 4% | 48 |
| 5 | 2% | 49 |

동력 입금은 5개 단위만 허용한다. `delivered=floor(coalCount×10×(1-lossRate))`이며 플레이어가 GUI에서 대상 시설과 수량을 확인한 뒤 커밋한다. 시설망이 끊기거나 대상 용량이 부족하면 소비하지 않는다. 자동으로 개인 자원을 가져오지 않으며 `FAC-S16` 전에는 요청자의 개인 원장만, 이후에는 GUI에서 명시 선택한 개인 또는 공용 원장만 사용한다.

## 6. 재건 시설 시험 증거 잠금

### 6.1 공통

- 시험은 자연 날씨·특정 생물군계·고정 좌표를 기다리지 않는다. 시설 GUI가 결정론적 시험 펄스를 제공한다.
- 각 성공 증거는 `proof:{facilityInstanceId}:{testId}:{revision}`으로 한 번만 커밋한다.
- 청크 언로드·서버 중단은 진행 시간을 정지한다. 실패는 투입 고유 부품을 소각하지 않는다.

| 시설 | 시작 조건 | 성공 증거 | 실패 조건·재시도 |
|---|---|---|---|
| `FAC-R03` | `ASSEMBLED`, R02 READY, 동일 96블록 네트워크 | `R03-BEARING-A/B/C` 세 펄스. 서로 다른 수평 방위 구간, 각 5초 채널, 오차 절댓값 `≤5°`; 세 증거 뒤 `READY` | 같은 방위 재사용, 이동·피격, 네트워크 단절. 성공한 방위는 보존 |
| `FAC-R04` | `ASSEMBLED`, R02 READY | GUI의 `THERMAL`과 `CORRUPTION` 시험 펄스를 각 10초 유지하고 출력 `80~120%`; `R04-THERMAL`, `R04-CORRUPTION` 뒤 `READY` | 범위 이탈·동력 부족·출력 범위 이탈. 성공 범주는 보존 |
| `FAC-R05` | R03 READY, 말뚝 3기 `PLACED` | R03 중심 16~48m, 말뚝 간 16~64m, 수평 삼각형 넓이 `≥96m²`. 북쪽에 가장 가까운 말뚝부터 시계 방향으로 각 5초 채널해 3기 `CALIBRATED` | 기하 조건 실패 시 위치만 재배치, 이미 소비한 제작 재료 복제·환불 없음 |

R03 방위 구간은 시설 yaw 기준 `[−60°,60°]`, `[60°,180°]`, `[−180°,−60°]`로 고정한다. 경계값은 낮은 인덱스 구간에 포함해 한 펄스가 두 증거를 채우지 못하게 한다.

## 7. 연구 25노드 기계 판독 계약

### 7.1 비용 동가치

| 연구 비용 | 자원 태그 | 허용 ID·가치 |
|---|---|---|
| `general` | `CONSTRUCTION` | `WSR-WOOD:1`, `WSR-STONE:1`, `WSR-HARD_AGGREGATE:4`, `WSR-STABILIZED_FRAME:8` |
| `metal` | `METAL` | `WSR-IRON:1`, `WSR-METAL_PLATE:1`, `WSR-REFINED_ALLOY:2`, `WSR-REINFORCED_ALLOY:3`, `WSR-HIGH_DENSITY_ALLOY:6` |
| `signal` | `SIGNAL` | `WSR-REDSTONE:1`, `WSR-COPPER_COIL:2`, `WSR-NEURAL_CIRCUIT:4`, `WSR-RESONANCE_COIL:6` |
| `specialist` | `SPECIAL` | `WSR-MAGIC_CRYSTAL:2`, `WSR-PURIFY_CATALYST:3`, `WSR-PATTERN_RESIDUE:5`, `WSR-INTERRUPT_CORE:8` |

인원 배율은 1/2/3/4인에서 `0.70/0.85/1.00/1.15`이며 0이 아닌 각 축에 `ceil(base×multiplier)`를 적용한다. 서버는 정확한 가치 합 조합 중 아이템 수가 가장 적은 조합을 선택하고, 동률이면 위 표의 오른쪽 고가 ID부터 선택한다. 정확 합이 불가능하면 GUI가 부족한 축을 표시하고 예약하지 않는다. `FAC-S16` 전에는 시작 플레이어 개인 원장, 이후에는 GUI에서 명시 선택한 개인/공용 한 원장만 사용하며 두 원장을 자동 혼합하지 않는다.

### 7.2 증거 원자

- 표본 원자는 `SAMPLE(type,tags,sourceId,sourceInstance,context,quality)`이며 제출 시 `consumedByJob`으로 예약한다.
- 관측 원자는 `OBS(code,subjectId,contextId,result,sourceChainId)`이며 동일 `sourceChainId+code+result`는 한 번만 센다.
- 진행 증명은 `PROOF(id)`이며 존재만 검사하고 연구에 소각하지 않는다.
- `DISTINCT_SOURCE n`, `DISTINCT_ROLE n`, `DISTINCT_CONTEXT n`, `BEFORE_AFTER n`, `SUCCESS_FAILURE n`은 `RESEARCH-001` 비교 규칙을 그대로 기계 연산한다.

### 7.3 노드별 술어

| 연구 ID | 정확한 입력 술어 |
|---|---|
| `RS-D01-SAMPLE` | `SAMPLE type ENVIRONMENT 1 + MINERAL 1`, 서로 다른 sourceId |
| `RS-D03-SEPARATION` | `BEFORE_AFTER(CORRUPTION_PURIFY) 1 + SAMPLE type MINERAL 1` |
| `RS-D05-DOCTRINE-I` | `OBS ENEMY_ROLE_SEEN DISTINCT_ROLE 2 + OBS PATTERN_RESOLVED DISTINCT_SOURCE 2` |
| `RS-D07-FIELD-REPAIR` | `SUCCESS_FAILURE(EQUIPMENT_REPAIR) 1`, 같은 장비 등급 축 |
| `RS-D09-RESONANCE` | `SAMPLE tag RESONANCE DISTINCT_SOURCE 2` |
| `RS-D11-NEURAL` | `SAMPLE type BIOLOGICAL DISTINCT_ROLE 2 + OBS STATUS_APPLIED:SILENCE 1` |
| `RS-D13-TOXIN` | `BEFORE_AFTER(POISON_CLEANSE) 1 + BEFORE_AFTER(BLEED_CLEANSE) 1` |
| `RS-D15-DOCTRINE-II` | `OBS STRATEGY_SEEN DISTINCT_SOURCE 4 + OBS ELITE_DEFEATED 1` |
| `RS-D18-JAMMING` | `OBS REVIVE_INTERRUPTED_OR_RESISTED 1 + SAMPLE tag RESONANCE 1` |
| `RS-D20-AMALGAM` | `SAMPLE source BOSS-D20 1 + SUCCESS_FAILURE(STRUCTURE_BREAK) 1` |
| `RS-D21-MOBILE-PURIFY` | `BEFORE_AFTER(CORRUPTION_PURIFY) 2`의 context가 서로 다름 + `SAMPLE type ENVIRONMENT DISTINCT_CONTEXT 2` |
| `RS-D23-MUTATION-MAP` | `SAMPLE tag MUTATION DISTINCT_CONTEXT 3`, 전체 sourceId 최소 2 |
| `RS-D25-AUGMENT-RESONANCE` | `OBS PERSONAL_AUGMENT_TRIGGER DISTINCT_CONTEXT 3`, augment tag 서로 다름 |
| `RS-D27-RIFT-STABILITY` | `OBS RIFT_STABILIZED DISTINCT_CONTEXT 2 + BEFORE_AFTER(RIFT_PURIFY) 1` |
| `RS-D29-CORE-SIGNAL` | `OBS RESONANCE_BEARING DISTINCT_CONTEXT 3 + SAMPLE tag MUTATION 1` |
| `RS-D31-BREAK-TELEMETRY` | `OBS BREAK_DAMAGE_COMMITTED DISTINCT_SOURCE 3`, weaponClass 서로 다름 |
| `RS-D33-INTERRUPT` | `SUCCESS_FAILURE(PATTERN_INTERRUPT) 1 + OBS INTERRUPTIBLE_CHANNEL_SEEN DISTINCT_SOURCE 2` |
| `RS-D36-PHASE-ARMOR` | `BEFORE_AFTER(PHASE_ARMOR) 1 + OBS STATUS_TRANSFORMED DISTINCT_SOURCE 2` |
| `RS-D39-RESONANT-BREAKER` | `OBS BOSS_PATTERN_SEEN DISTINCT_SOURCE 4 + SAMPLE tag RESONANCE DISTINCT_SOURCE 3` |
| `RS-D41-REBUILD-A` | `PROOF WSP-REBUILD-PART-A + OBS STRUCTURAL_TEST DISTINCT_SOURCE 3` |
| `RS-D42-REBUILD-B` | `PROOF WSP-REBUILD-PART-B + OBS POWER_OUTPUT_TEST DISTINCT_SOURCE 3` |
| `RS-D43-REBUILD-C` | `PROOF WSP-REBUILD-PART-C + OBS RESONANCE_BEARING DISTINCT_CONTEXT 5` |
| `RS-D44-REBUILD-D` | `PROOF WSP-REBUILD-PART-D + OBS PURIFY_REACTION DISTINCT_CONTEXT 3` |
| `RS-D47-SYNTHESIS` | 앞선 A~D 연구 `UNLOCKED` + `OBS PORTABLE_FALLBACK_SUCCESS 1` |
| `RS-D49-CALIBRATION` | `OBS R03_BEARING_PASS DISTINCT_CONTEXT 3 + OBS R03_BEARING_FAIL 1` |

선행 발견·연구는 문자열에서 추론하지 않고 다음 데이터 리비전의 `prerequisiteDiscoveryIds[]`, `prerequisiteResearchIds[]`, `minimumDay` 필드로 분리한다. 위 술어는 `evidenceExpression` 구조체로 이동하며 `comparisonInput`은 표시 전용으로 남긴다.

## 8. 사용자·서사 결정 게이트

### 8.1 완전 사망 뒤 희귀 부활

현재 61개 아이템과 214개 장비에는 완전 사망 플레이어를 되살리는 권위 효과가 없다. 완성된 세 대안과 Story 작성 방식은 `AUTHOR-DECISION-REGISTER-001`이 소유하며, 사용자 권위로 하나를 고정하기 전에는 신규 ID·레시피·도감 수를 임의로 늘리지 않는다.

| 선택 | 결과 | 영향 |
|---|---|---|
| `REVIVAL-ITEM` 권장 | Day 31+ 희귀 파티 귀속 부활 코어 1종, 플레이어당 회차 1회, 안전 시설 10초 채널 | 아이템 +1, 레시피 +1, 도감 +1; 전멸은 복구 불가 |
| `NO-REVIVAL` | 완전 사망은 해당 회차에서 영구 관전 | 목록 수 불변, 하드코어 강도 최고 |
| `FACILITY-ONLY` | Day 41+ 재건 시설의 고비용 부활 작업 | 목록 수 불변, 시설/자원 계약 추가 |

선택 전 런타임은 기존 `DEAD`·유품·관전만 유지하며 가짜 부활 버튼이나 관리자 명령을 정상 플레이 경로로 노출하지 않는다.

### 8.2 Story 원문

`STORY-DATA-001`의 장면 73개·선택 기록 9개 ID/트리거는 유지한다. 한국어 대사·로그 본문, 화자 말투, 선택지 최종 문구는 콘텐츠 작성 입력이므로 `AUTHOR_DECISION_REQUIRED`다. 원문이 없을 때 런타임은 ID와 요약 키만 표시하는 개발 폴백을 허용하되 출시 완료로 인정하지 않는다.

## 9. 실제 플레이 전환 순서

| 순서 | 작업 묶음 | 진입 조건 | 종료 증거 |
|---:|---|---|---|
| 1 | r2 스키마·생성기 데이터 폐쇄 | 이 문서 수치 승인 | 자연어 추론 0, L0 참조·수량·경계 전수 통과 |
| 2 | 개인/공용 자원·제작·도감·메뉴 | 1 | FAC-S16 전후 원장, 315 제작식, 334 고정 도감, Shift+F 실클라 E2E |
| 3 | 아이템·탄약·장비·도구 | 2 | 61+214 ID의 사용/장착/파손/복구 상태 원장 전부 `VERIFIED` |
| 4 | 스킬·개인/파티 증강·상태 | 3 | 64+66+21 ID의 효과·상충·저장·인원 행렬 통과 |
| 5 | 적·지원 개체·드롭·Day 사건 | 4 | 91 엔티티·62 loot·Day1~50 생성/cleanup/보상 통과 |
| 6 | 시설·연구 | 2~5의 의존 기능 | 46 opcode와 연구25 노드 정상/거부/중단/재시작 통과 |
| 7 | 보스·Final·Story | 4~6, 부활·Story 결정 | Day10/20/30/40, Day50 TX, 원문 표현 E2E |
| 8 | 멀티플레이·성능·장애 복구 | 모든 기능 카테고리 `VERIFIED` | 1~4인, p95 tick≤50ms, 강제 종료·재시작·중복/유실 0 |

실제 바닐라 클라이언트가 필요한 slot 0 입력, Shift 숫자, Shift+F, 3×3 GUI 중복 클릭, 블록 설치·채굴, 시설 표시, 피해 숫자는 콘솔·`/setblock`·MockBukkit으로 대체 통과시키지 않는다.

## 10. 즉시 후속 작업

1. `research.schema.json`에 자원 가치표 참조, 선행 ID 배열, `evidenceExpression`을 추가한다.
2. `item.schema.json`에 `compatibleWeaponClasses`, `selectionOrder`, `effectOpcode`, 명시 수치 필드를 추가한다.
3. `facility.schema.json`에 `operationCost`, `cooldownSeconds`, `durationSeconds`, `evidenceContract`를 추가한다.
4. 생성기가 이 문서의 잠금값을 출력하도록 원 소유 목록 표에도 같은 필드를 반영한다.
5. L0 validator가 자유 서술만 있고 명시 실행 필드가 없는 레코드를 거부하게 한다.
6. 데이터 폐쇄 커밋 뒤에만 연구·특수 탄약·차단 시설 opcode 구현을 시작한다.
