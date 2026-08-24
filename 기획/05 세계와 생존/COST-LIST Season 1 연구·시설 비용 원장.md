# WildSurvival Season 1 연구·시설 비용 원장

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `COST-LIST-S1-001` |
| 상태 | `DATA_LOCKED` |
| 소유 범위 | 연구 25개 결제 ID, 시설 46종의 제작·업그레이드 결제 ID, 회차 예산 적합성 |
| 상위 기준 | `RESEARCH-001`, `FACILITY-DATA-D50-001`, `RESOURCE-DATA-D50-001`, `BALANCE-D50-001` |
| 구조화 데이터 | `ws-content-r2/research/season1-research.json`, `ws-content-r2/facilities/season1-facilities.json` |
| 최종 수정일 | 2026-08-24 |

## 1. ID와 결제 원칙

- 연구 비용 ID는 `RCOST-{researchId}`다. 예: `RCOST-RS-D01-SAMPLE`.
- 시설 비용 ID는 `FCOST-{facilityId}-L{targetLevel}`이다. 예: `FCOST-FAC-S01-L3`.
- 두 접두사는 서로 다른 이름 공간이며 전체 146개 ID에서 중복을 허용하지 않는다.
- 시설 Lv1은 `CRAFT_RECIPE_REFERENCE`다. `FCOST-...-L1`은 해당 시설의 `WSRCP-*` 거래를 가리킬 뿐 별도 자원을 다시 차감하지 않는다.
- Lv2 이상은 `RESOURCE_VALUE` 거래다. 대상 레벨의 비용만 한 번 지불하며 이전 레벨 비용을 재청구하지 않는다.
- 연구·시설 모두 `validate→reserve→process→commit` 순서로 처리하고, 같은 비용 ID와 대상 인스턴스의 완료 키가 있으면 재결제하지 않는다.
- 3인 Easy STANDARD가 원형이다. 1/2/3/4인 배율은 `0.70/0.85/1.00/1.15`이고 0이 아닌 축별로 올림한다. 고유 부품·방문·관측·방향·말뚝 수는 배율을 받지 않는다.

## 2. 연구 비용 25개

표의 축 순서는 `일반/금속/신호/전문`이며 시간은 회차 논리 시계의 초다.

| 비용 ID | 연구 ID | 최소 Day | 비용 | 시간 |
|---|---|---:|---:|---:|
| `RCOST-RS-D01-SAMPLE` | `RS-D01-SAMPLE` | 1 | 4/0/0/0 | 30 |
| `RCOST-RS-D03-SEPARATION` | `RS-D03-SEPARATION` | 3 | 6/2/0/0 | 45 |
| `RCOST-RS-D05-DOCTRINE-I` | `RS-D05-DOCTRINE-I` | 5 | 4/2/2/0 | 60 |
| `RCOST-RS-D07-FIELD-REPAIR` | `RS-D07-FIELD-REPAIR` | 7 | 8/4/0/0 | 50 |
| `RCOST-RS-D09-RESONANCE` | `RS-D09-RESONANCE` | 9 | 4/2/6/0 | 75 |
| `RCOST-RS-D11-NEURAL` | `RS-D11-NEURAL` | 11 | 6/4/4/2 | 80 |
| `RCOST-RS-D13-TOXIN` | `RS-D13-TOXIN` | 13 | 8/2/2/4 | 90 |
| `RCOST-RS-D15-DOCTRINE-II` | `RS-D15-DOCTRINE-II` | 15 | 6/4/8/4 | 100 |
| `RCOST-RS-D18-JAMMING` | `RS-D18-JAMMING` | 18 | 8/6/8/6 | 110 |
| `RCOST-RS-D20-AMALGAM` | `RS-D20-AMALGAM` | 20 | 6/8/10/6 | 120 |
| `RCOST-RS-D21-MOBILE-PURIFY` | `RS-D21-MOBILE-PURIFY` | 21 | 8/6/8/10 | 100 |
| `RCOST-RS-D23-MUTATION-MAP` | `RS-D23-MUTATION-MAP` | 23 | 6/8/10/12 | 120 |
| `RCOST-RS-D25-AUGMENT-RESONANCE` | `RS-D25-AUGMENT-RESONANCE` | 25 | 8/8/12/12 | 120 |
| `RCOST-RS-D27-RIFT-STABILITY` | `RS-D27-RIFT-STABILITY` | 27 | 10/10/14/16 | 140 |
| `RCOST-RS-D29-CORE-SIGNAL` | `RS-D29-CORE-SIGNAL` | 29 | 8/10/18/18 | 150 |
| `RCOST-RS-D31-BREAK-TELEMETRY` | `RS-D31-BREAK-TELEMETRY` | 31 | 10/14/14/10 | 120 |
| `RCOST-RS-D33-INTERRUPT` | `RS-D33-INTERRUPT` | 33 | 10/12/18/14 | 140 |
| `RCOST-RS-D36-PHASE-ARMOR` | `RS-D36-PHASE-ARMOR` | 36 | 10/16/18/16 | 150 |
| `RCOST-RS-D39-RESONANT-BREAKER` | `RS-D39-RESONANT-BREAKER` | 39 | 12/18/22/20 | 180 |
| `RCOST-RS-D41-REBUILD-A` | `RS-D41-REBUILD-A` | 41 | 8/12/12/10 | 160 |
| `RCOST-RS-D42-REBUILD-B` | `RS-D42-REBUILD-B` | 42 | 8/12/14/12 | 170 |
| `RCOST-RS-D43-REBUILD-C` | `RS-D43-REBUILD-C` | 43 | 8/12/16/12 | 180 |
| `RCOST-RS-D44-REBUILD-D` | `RS-D44-REBUILD-D` | 44 | 8/12/14/14 | 180 |
| `RCOST-RS-D47-SYNTHESIS` | `RS-D47-SYNTHESIS` | 47 | 9/18/20/16 | 210 |
| `RCOST-RS-D49-CALIBRATION` | `RS-D49-CALIBRATION` | 49 | 9/20/24/18 | 240 |

### 2.1 세션 합계

| Day 구간 | 노드 수 | 일반 | 금속 | 신호 | 전문 | 판정 |
|---|---:|---:|---:|---:|---:|---|
| 1~10 | 5 | 26 | 10 | 8 | 0 | 초반 개인 자원과 휴대 분석 경로로 지불 가능 |
| 11~20 | 5 | 34 | 24 | 32 | 22 | 첫 정착 시설과 장비 성장 예산을 침범하지 않음 |
| 21~30 | 5 | 40 | 42 | 62 | 68 | 세션 3의 75% 하한 145/333/181/270 안 |
| 31~40 | 4 | 42 | 60 | 72 | 60 | 세션 4의 75% 하한 115/436/241/286 안 |
| 41~50 | 6 | 50 | 86 | 100 | 82 | 세션 5의 75% 하한 85/525/301/364 안 |

연구 비용만 하한 전체를 소모하지 않으며 다음 보스·Final, 생존 소모품, 장비 1세트의 상위 예약을 먼저 뺀다. 예약 후 해당 축 잔액이 10 미만이면 선택 연구의 시작을 막지만, Final 필수 연구는 보장 사건과 복구 후보를 먼저 활성화한다.

## 3. 시설 비용 121개

### 3.1 Lv1 제작 결제 46개

아래 행의 `L1 비용 ID`는 같은 행의 제작식 한 번만 지불한다. 범위 표기는 문서 표시 축약이며 구조화 데이터에는 각 ID가 개별 행으로 저장된다.

| 시설군 | 시설 ID | L1 비용 ID | 제작식 | 최대 레벨 |
|---|---|---|---|---:|
| 휴대 | `FAC-P01`~`FAC-P08` | `FCOST-FAC-P01-L1`~`FCOST-FAC-P08-L1` | 각 시설 `recipeId` | 1 |
| 야영 | `FAC-C01`~`FAC-C08` | `FCOST-FAC-C01-L1`~`FCOST-FAC-C08-L1` | 각 시설 `recipeId` | 1 |
| 정착 | `FAC-S01` | `FCOST-FAC-S01-L1` | 시설 `recipeId` | 5 |
| 정착 | `FAC-S02` | `FCOST-FAC-S02-L1` | 시설 `recipeId` | 5 |
| 정착 | `FAC-S03` | `FCOST-FAC-S03-L1` | 시설 `recipeId` | 5 |
| 정착 | `FAC-S04`~`FAC-S05` | `FCOST-FAC-S04-L1`~`FCOST-FAC-S05-L1` | 각 시설 `recipeId` | 4 |
| 정착 | `FAC-S06`~`FAC-S08` | `FCOST-FAC-S06-L1`~`FCOST-FAC-S08-L1` | 각 시설 `recipeId` | 5 |
| 정착 | `FAC-S09` | `FCOST-FAC-S09-L1` | 시설 `recipeId` | 4 |
| 정착 | `FAC-S10` | `FCOST-FAC-S10-L1` | 시설 `recipeId` | 3 |
| 정착 | `FAC-S11` | `FCOST-FAC-S11-L1` | 시설 `recipeId` | 5 |
| 정착 | `FAC-S12` | `FCOST-FAC-S12-L1` | 시설 `recipeId` | 3 |
| 정착 | `FAC-S13` | `FCOST-FAC-S13-L1` | 시설 `recipeId` | 5 |
| 정착 | `FAC-S14`~`FAC-S15` | `FCOST-FAC-S14-L1`~`FCOST-FAC-S15-L1` | 각 시설 `recipeId` | 4 |
| 정착 | `FAC-S16`~`FAC-S17` | `FCOST-FAC-S16-L1`~`FCOST-FAC-S17-L1` | 각 시설 `recipeId` | 5 |
| 정착 | `FAC-S18` | `FCOST-FAC-S18-L1` | 시설 `recipeId` | 4 |
| 정착 | `FAC-S19` | `FCOST-FAC-S19-L1` | 시설 `recipeId` | 3 |
| 정착 | `FAC-S20` | `FCOST-FAC-S20-L1` | 시설 `recipeId` | 4 |
| 방어 | `FAC-D01`~`FAC-D04` | `FCOST-FAC-D01-L1`~`FCOST-FAC-D04-L1` | 각 시설 `recipeId` | 3 |
| 재건 | `FAC-R01`~`FAC-R06` | `FCOST-FAC-R01-L1`~`FCOST-FAC-R06-L1` | `WSRCP-R01`~`WSRCP-R06` | 1 |

### 3.2 업그레이드 결제 75개

표의 축 순서는 `건설/생존/금속/신호/전문`이다. 각 정착 시설은 자신의 프로필과 최대 레벨까지 `FCOST-{facilityId}-L2...`를 개별로 생성한다.

| 프로필 | Lv2 | Lv3 | Lv4 | Lv5 |
|---|---:|---:|---:|---:|
| `FP-PRODUCTION` | 8/0/12/6/2 | 10/0/18/10/6 | 12/0/24/16/10 | 14/0/30/20/16 |
| `FP-RESEARCH` | 6/0/10/14/4 | 8/0/14/20/8 | 10/0/18/28/12 | 12/0/22/34/18 |
| `FP-SURVIVAL` | 8/8/10/5/4 | 10/10/14/7/8 | 12/12/18/10/12 | 14/14/22/12/18 |
| `FP-LOGISTICS` | 10/0/12/12/2 | 12/0/16/18/6 | 16/0/20/24/10 | 18/0/26/30/14 |
| `FP-DEFENSE` | 6/0/14/5/2 | 8/0/18/8/4 | 해당 없음 | 해당 없음 |

| 시설 | 프로필 | 생성되는 업그레이드 비용 ID |
|---|---|---|
| `FAC-S01`, `S02` | `FP-PRODUCTION` | 각 `L2`~`L5` |
| `FAC-S03` | `FP-RESEARCH` | `L2`~`L5` |
| `FAC-S04`, `S05` | `FP-PRODUCTION` | 각 `L2`~`L4` |
| `FAC-S06`, `S07`, `S08` | `FP-RESEARCH` | 각 `L2`~`L5` |
| `FAC-S09` | `FP-RESEARCH` | `L2`~`L4` |
| `FAC-S10` | `FP-RESEARCH` | `L2`~`L3` |
| `FAC-S11` | `FP-SURVIVAL` | `L2`~`L5` |
| `FAC-S12` | `FP-SURVIVAL` | `L2`~`L3` |
| `FAC-S13` | `FP-SURVIVAL` | `L2`~`L5` |
| `FAC-S14` | `FP-LOGISTICS` | `L2`~`L4` |
| `FAC-S15` | `FP-SURVIVAL` | `L2`~`L4` |
| `FAC-S16`, `S17` | `FP-LOGISTICS` | 각 `L2`~`L5` |
| `FAC-S18` | `FP-LOGISTICS` | `L2`~`L4` |
| `FAC-S19` | `FP-LOGISTICS` | `L2`~`L3` |
| `FAC-S20` | `FP-RESEARCH` | `L2`~`L4` |
| `FAC-D01`~`D04` | `FP-DEFENSE` | 각 `L2`~`L3` |

정착 업그레이드 67개와 방어 업그레이드 8개를 합쳐 75개다. Lv1 46개와 합치면 시설 비용 ID는 121개다.

## 4. 회차 기준 구매 봉투

모든 시설의 최대 레벨을 한 회차에 사는 구조가 아니다. STANDARD 기대 회차는 아래 필수·선택 봉투를 사용한다.

| Day 구간 | 필수 봉투 | 선택 봉투 | 차단 기준 |
|---|---|---|---|
| 1~10 | 휴대 분석/채취 2종, 야영 제작·화로·보관 중 2종 | 나머지 휴대·야영 | 다음 보스 호출 예약 부족 |
| 11~20 | `FAC-S01`, `S02`, `S06`, `S11`, `S16` Lv1, 이 중 2기만 Lv2 | 탄약·관측·훈련 | 생존품 2회분 또는 활동 장비 1세트 부족 |
| 21~30 | `FAC-S13`, `S17` Lv1, 핵심 제작/연구 시설 총 3회 업그레이드 | 증강·이동·방어 특화 | Day 30 호출·복구 예비 10 미만 |
| 31~40 | `FAC-S08`, `S15`, `S20` 중 진행에 필요한 2기, 시설망 총 4회 업그레이드 | 함정·재련·다중 네트워크 | Day 40 호출·시설 복구 예비 10 미만 |
| 41~50 | `FAC-R01`~`R06`, 진행에 필요한 정착 시설만 2회 업그레이드 | 기존 빌드 마감 | Final 수리·재건 예비 42 미만 |

R01~R05 직접 재건비는 자원 가치로 `74+40+36+39+51=240`이다. Day 41~49 필수 연구의 전문 축 82와 합쳐 322이며, 세션 5 재건 자원 75% 하한 364에서 42를 키 경로·실패 복구 예비로 남긴다. R06은 R01~R05와 최종 신호 키의 존재를 확인하는 결합 거래이므로 동일 재료를 다시 차감하지 않는다.

## 5. 검증 계약

- 구조화 데이터는 연구 25개와 시설 비용 121개, 총 146개의 고유 ID를 가져야 한다.
- 각 시설은 `levelCosts.size == maxLevel`이어야 하고 목표 레벨 1부터 최대 레벨까지 빠짐없이 한 번씩 존재해야 한다.
- Lv1 비용은 그 시설의 제작식만 참조하고 축 비용은 모두 0이어야 한다.
- Lv2 이상은 제작식 참조가 비어 있고 다섯 축 중 하나 이상이 양수여야 한다.
- 비용 ID 중복, 음수, 누락 레벨, Lv1 이중 결제, 배율 적용 뒤 0이 되는 양수 축은 콘텐츠 번들 로드를 실패시킨다.
- 자동 검산은 1~4인 비용 반올림, 세션 합계, 75% 하한, R01~R05 가치 240, 후반 필수 합계 322를 검사한다.
