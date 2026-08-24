# WildSurvival Season 1 재료 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `MATERIAL-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 적용 범위 | Day 1~50 원장 자원, 가공 부품, 보스 재료, 소각하지 않는 진행 증명 |
| 상위 기준 | `RES-001`, `CRAFT-001`, `RESOURCE-DATA-D20-001`, `RESOURCE-DATA-D50-001`, `FINAL-DATA-001` |
| 데이터 리비전 | `material-s1-r2` |
| 최종 수정일 | 2026-08-24 |

## 1. 공통 계약

- `WSR-*`는 수량을 가지는 개인 또는 파티 원장 자원이다.
- `WSP-*`는 수량 소비가 없는 회차 귀속 증명이다. 인벤토리·호퍼·드롭으로 표현하지 않는다.
- 개인이 얻은 자원은 `FAC-S16 공용 물류고`가 설치되기 전 개인 원장에 들어간다.
- 공용 물류고 설치 전 제작은 요청자의 개인 원장과 인벤토리만 사용한다.
- 공용 물류고 설치 후에도 개인 자원을 자동 입금하지 않는다. GUI에서 명시적으로 입금해야 한다.
- 원장 1단위와 표시 아이템 스택은 다를 수 있다. 변환은 등록된 대표 Material과 고정 비율만 사용한다.
- 보스 진행 증명과 Final 키는 버리기·거래·분해·사망 드롭·난이도 배율 적용을 금지한다.

필수 필드는 `id`, `textKey`, `tier`, `category`, `firstDay`, `ledgerScope`, `unit`, `displayMaterial`, `stackLimit`, `sources`, `sinks`, `fallback`, `codexIndex`다.

## 2. Day 1~10 기초·산업·오염 자원

| ID | 표시명 | Tier·범주 | 최초 Day | 원장·단위 | 대표 Material | 주 획득 | 핵심 소비 |
|---|---|---|---:|---|---|---|---|
| `WSR-WOOD` | 목재 | T0 BASIC | 1 | 개인, 원목 4 | OAK_LOG | 원목 채집 | 기초 부품·시설 |
| `WSR-STONE` | 석재 | T0 BASIC | 1 | 개인, 석재류 8 | COBBLESTONE | 돌 채굴 | 골재·시설 |
| `WSR-FIBER` | 섬유 | T0 BASIC | 1 | 개인, 실·식물 4 | STRING | 거미·식생 | 천·치료품 |
| `WSR-LEATHER` | 가죽 | T0 BASIC | 1 | 개인, 가죽 1 | LEATHER | 동물·상자 | 천·장비 |
| `WSR-RATION` | 식량 배급 | T0 BASIC | 1 | 개인, 12 food point | BREAD | 음식 등록 | 배급팩·자극제 |
| `WSR-COAL` | 연료 | T0 BASIC | 1 | 개인, 석탄·숯 2 | COAL | 채굴·제련 | 모든 열 가공 |
| `WSR-IRON` | 철 | T1 INDUSTRIAL | 1 | 개인, 주괴 1 | IRON_INGOT | 철 채굴·제련 | 판재·장비·시설 |
| `WSR-COPPER` | 구리 | T1 INDUSTRIAL | 4 | 개인, 주괴 1 | COPPER_INGOT | 구리 채굴·제련 | 코일·회로·시설 |
| `WSR-GOLD` | 금 | T1 INDUSTRIAL | 5 | 개인, 주괴 1 | GOLD_INGOT | 위험 광맥·정예 | 정밀·약제·장비 |
| `WSR-REDSTONE` | 회로 가루 | T1 INDUSTRIAL | 4 | 개인, 가루 4 | REDSTONE | 광맥·폐허 | 회로·시설 |
| `WSR-AMETHYST` | 자수정 | T2 SPECIALIZED | 6 | 개인, 조각 4 | AMETHYST_SHARD | 자수정 노드 | 결정·렌즈·정화 |
| `WSR-MAGIC_CRYSTAL` | 마력 결정 | T2 SPECIALIZED | 6 | 개인, 결정 1 | AMETHYST_SHARD | 가공·엘리트 | 지팡이·신호·연구 |
| `WSR-TISSUE` | 오염 조직 | T2 CORRUPTED | 5 | 개인, 표본 1 | ROTTEN_FLESH | 오염 적·균열 | 촉매·분석 |
| `WSR-MUTATION_SHARD` | 변이 파편 | T2 CORRUPTED | 8 | 개인, 파편 1 | ECHO_SHARD | 변이·정예 | 재련·후반 정제 |

## 3. Day 11~20 의료·상태 표본

| ID | 표시명 | Tier·범주 | 최초 Day | 대표 Material | 주 획득 | 대체 경로 | 핵심 소비 |
|---|---|---|---:|---|---|---|---|
| `WSR-HERB` | 의약 재료 | T2 MEDICAL | 11 | HONEY_BOTTLE | 의약 식생 | 식량·약초 사건 | 젤·상태 소모품 |
| `WSR-ELASTIC_FIBER` | 탄성 섬유 | T2 MEDICAL | 11 | COBWEB | 탄성 둥지·적 | 섬유+가죽 압축 | 직조·구조품 |
| `WSR-TOXIN_SAMPLE` | 독성 표본 | T2 STATUS_SAMPLE | 11 | LIME_DYE | 독성 적·잔류물 | 조직 분석 | 해독·분류판 |
| `WSR-THERMAL_SAMPLE` | 열성 표본 | T2 STATUS_SAMPLE | 12 | BLAZE_POWDER | 열성 적·열핵 | 연료+조직 실험 | 냉각·분류판 |
| `WSR-HEMATIC_SAMPLE` | 혈성 표본 | T2 STATUS_SAMPLE | 13 | RED_DYE | 열상 적·생체 흔적 | 가죽+조직 분석 | 지혈·분류판 |
| `WSR-NEURAL_SAMPLE` | 신경 표본 | T3 STATUS_SAMPLE | 14 | FERMENTED_SPIDER_EYE | 제어 적·군락 | 저항·해제 기록 | 회로·안정제 |
| `WSR-VITAL_TISSUE` | 생체 조직 | T3 BIO | 16 | SCUTE | 정예·고위험 생체 | 표본 3종 합성 | 생체 매질·D20 |

## 4. Day 21~50 전문·재건 자원

| ID | 표시명 | Tier | 최초 Day | 대표 Material | 주 획득·가공 | 핵심 소비 |
|---|---|---:|---:|---|---|---|
| `WSR-PURIFY_CATALYST` | 안정 정화 촉매 | T4 | 21 | GHAST_TEAR | 자연 정화 노드·D50-P01 | 정화 매질·행렬 |
| `WSR-RIFT_POWDER` | 균열 가루 | T4 | 23 | GLOWSTONE_DUST | 균열 안정화·D50-P02 | 안정화 코어 |
| `WSR-REFINED_MUTATION` | 정제 변이 파편 | T4 | 25 | ECHO_SHARD | 변이 3범주·D50-P03 | 정화·심연 개조 |
| `WSR-STABLE_CORE` | 안정화 코어 | T5 | 27 | HEART_OF_THE_SEA | D50-P04·복구 사건 | D30 호출 |
| `WSR-PURIFY_MEDIUM` | 정화 매질 | T5 | 28 | PRISMARINE_CRYSTALS | D50-P05·운전 기록 | D30·행렬 |
| `WSR-HARD_AGGREGATE` | 고강도 구조 골재 | T4 | 31 | TUFF | 중장 잔해·D50-P06 | 합금·재건 구조 |
| `WSR-RESONANCE_COIL` | 공진 코일 | T5 | 32 | COPPER_BULB | D50-P07·공진 문맥 | 중단·동력 |
| `WSR-PATTERN_RESIDUE` | 패턴 잔재 | T5 | 34 | MUSIC_DISC_5 | 전조 기록·D50-P08 | 중단·렌즈 |
| `WSR-HIGH_DENSITY_ALLOY` | 고밀도 합금 | T5 | 37 | NETHERITE_SCRAP | D50-P09·균열 보상 | 심연·프레임 |
| `WSR-INTERRUPT_CORE` | 중단 코어 | T6 | 39 | RECOVERY_COMPASS | D50-P10·수단 기록 | D40 호출 |
| `WSR-POWER_MATRIX` | 동력 행렬 | T6 | 41 | REDSTONE_BLOCK | D50-P11·동력 기록 | FAC-R02·Final |
| `WSR-CALIBRATED_LENS` | 교정 렌즈 | T6 | 42 | SPYGLASS | D50-P12·관측 기록 | FAC-R03·Final |
| `WSR-PURIFY_MATRIX` | 정화 행렬 | T6 | 43 | SEA_LANTERN | D50-P13·정화 3범주 | FAC-R04·Final |
| `WSR-STABILIZED_FRAME` | 안정 프레임 | T6 | 44 | HEAVY_CORE | D50-P14·시설 회수 | FAC-R01·Final |
| `WSR-FINAL_SIGNAL_KEY` | 최종 신호 키 | UNIQUE | 49 | NETHER_STAR | D49+RS-D49+C29 | FAC-R06 활성 증명 |

## 5. Day 1~20 가공 부품

기존 문서에서 표시명으로만 사용하던 출력에 다음 ID를 부여한다.

| ID | 표시명 | Tier | 최초 | 출력 레시피 | 대표 Material | 주요 소비 |
|---|---|---:|---:|---|---|---|
| `WSR-HARDWOOD_PART` | 경목 부품 | T1 | 1 | `WSRCP-P01` | STICK | 무기·시설 |
| `WSR-SINTERED_AGGREGATE` | 소결 골재 | T1 | 2 | `WSRCP-P02` | ANDESITE | 둔기·시설 |
| `WSR-METAL_PLATE` | 금속 판재 | T1 | 2 | `WSRCP-P03` | IRON_NUGGET | 무기·방어구 |
| `WSR-COPPER_COIL` | 구리 코일 | T2 | 4 | `WSRCP-P04` | LIGHTNING_ROD | 정밀·장비 |
| `WSR-REFINED_ALLOY` | 정련 합금 | T2 | 5 | `WSRCP-P05` | IRON_INGOT | 희귀 장비 |
| `WSR-PRECISION_PART` | 정밀 부품 | T2 | 5 | `WSRCP-P06` | COMPARATOR | 희귀·고유 장비 |
| `WSR-CRUDE_PURIFY_CATALYST` | 조잡한 정화 촉매 | T2 | 5 | `WSRCP-P07` | GLOWSTONE_DUST | 초반 앰풀·휴대 정화 |
| `WSR-SIGNAL_LENS` | 신호 렌즈 | T3 | 6 | `WSRCP-P09` | TINTED_GLASS | D10~30 호출 |
| `WSR-REINFORCED_CLOTH` | 보강 천 | T1 | 2 | `WSRCP-P10` | WHITE_CARPET | 방어구·구조품 |
| `WSR-STERILE_GEL` | 멸균 젤 | T3 | 11 | `WSRCP-D20-P01` | SLIME_BALL | 의료·방어구 |
| `WSR-ELASTIC_WEAVE` | 탄성 직조 | T3 | 12 | `WSRCP-D20-P02` | STRING | D20 장비·구조 |
| `WSR-REINFORCED_ALLOY` | 강화 합금 | T3 | 14 | `WSRCP-D20-P03` | IRON_BLOCK | D20 장비·시설 |
| `WSR-NEURAL_CIRCUIT` | 신경 회로 | T3 | 15 | `WSRCP-D20-P04` | REPEATER | D20 장비·연구 |
| `WSR-BIO_MEDIUM` | 생체 매질 | T4 | 16 | `WSRCP-D20-P05` | TURTLE_SCUTE | D20 보스·장비 |
| `WSR-STATUS_PLATE` | 상태 분류판 | T3 | 13 | `WSRCP-D20-P06` | MAP | 상태 연구·장비 |

### 동명 충돌 해소

- 초반 `소결 골재(WSR-SINTERED_AGGREGATE)`와 후반 `고강도 구조 골재(WSR-HARD_AGGREGATE)`는 별도 재료다.
- 초반 `조잡한 정화 촉매(WSR-CRUDE_PURIFY_CATALYST)`와 후반 `안정 정화 촉매(WSR-PURIFY_CATALYST)`는 별도 재료다.
- `구리 코일`과 `공진 코일`, `신호 렌즈`와 `교정 렌즈`, `정련 합금`·`강화 합금`·`고밀도 합금`은 자동 대체하지 않는다.
- 상위 재료가 하위 재료를 요구하면 레시피 입력에 명시하며 이름 유사성으로 암묵 변환하지 않는다.

## 6. 보스 재료와 진행 증명

| ID | 표시명 | 유형 | 최초 Day | 등록 단위 | 획득 권위 ID | 획득·수량 | 소비·검사 |
|---|---|---|---:|---:|---|---|---|
| `WSR-RESONANT_RESIDUE` | 공명 잔류 패턴 | PARTY_RESOURCE | 10 | 1 | `LOOT-BOSS-D10` | 보스 완료 시 1/2/3~4인 6/7/8 | D10 영웅 추가 제작·강화 |
| `WSR-NEURAL_RESIDUE` | 신경 보스 잔류물 | PARTY_RESOURCE | 20 | 1 | `LOOT-BOSS-D20` | 보스 완료 시 1/2/3~4인 8/10/12 | D20 전설 추가 제작 |
| `WSR-BOSS_SIGNAL_CORE` | 보스 신호 코어 | PARTY_RESOURCE | 1 | 1 | `WSRCP-G02` | 제작 출력 1 | `WSRCP-G03` Day 10 호출 |
| `WSP-BOSS-D10-CORE` | 공명 추적체 핵 증명 | BOUND_PROOF | 10 | 1 | `LOOT-BOSS-D10` | 최초 완료 TX에서 1회 설정 | D20 호출 존재 검사, 소각 금지 |
| `WSP-REBUILD-PART-A` | 재건 안정화 부품 A | BOUND_PROOF | 10 | 1 | `LOOT-BOSS-D10` | 최초 완료 TX에서 1회 설정 | D20+, FAC-R01 증명 |
| `WSP-REBUILD-PART-B` | 재건 생체 부품 B | BOUND_PROOF | 20 | 1 | `LOOT-BOSS-D20` | 최초 완료 TX에서 1회 설정 | D30+, FAC-R02 증명 |
| `WSP-REBUILD-PART-C` | 재건 정화 부품 C | BOUND_PROOF | 30 | 1 | `LOOT-BOSS-D30` | 최초 완료 TX에서 1회 설정 | D40+, FAC-R03 증명 |
| `WSP-REBUILD-PART-D` | 재건 공명 부품 D | BOUND_PROOF | 40 | 1 | `LOOT-BOSS-D40` | 최초 완료 TX에서 1회 설정 | FAC-R04·Final 증명 |

- 증명은 시설에 귀속할 수 있으나 파괴·철거 시 회차 원장으로 복귀한다.
- 보스 재료의 난이도별 수량은 보스 보상 원장이 권위이며 재지급은 같은 reward transaction ID를 사용한다.
- 위 표의 `최초 Day`, `등록 단위`, `획득 권위 ID`는 생성기가 자유 서술에서 추론하지 않는 실행 열이다. `BOUND_PROOF`의 등록 단위 1은 수량 소비가 아니라 Boolean 최초 설정을 뜻한다.

## 7. 등급과 도구 요구

| Tier | 대표 Day | 채집 도구 최소 등급 | 일반 용도 |
|---:|---|---|---|
| T0 | 1 | 손·WOOD | 생존·Craft 해금 |
| T1 | 1~5 | STONE | 기초 산업·COMMON |
| T2 | 5~10 | IRON | 상태 입문·UNCOMMON/RARE |
| T3 | 11~20 | REINFORCED_IRON | 의료·상태·EPIC |
| T4 | 21~30 | RESONANT | 변이·정화·LEGENDARY |
| T5 | 31~40 | HARDENED | 브레이크·ABYSSAL |
| T6 | 41~50 | RECONSTRUCTION | Final 하위 계통 |
| UNIQUE | 보스·Final | 해당 없음 | 회차 증명·키 |

도구 등급 부족은 블록을 보존하고 WS 드롭과 바닐라 드롭을 모두 지급하지 않는다.

## 8. 도감·표현

- 재료 도감 범위는 `codexIndex 0001~0099`로 고정한다.
- 미발견 재료는 검은색 염료, 이름 `???`, 고정 위치를 사용한다.
- 최초 개인 획득 또는 파티 증명 공유 시 해금한다. 파티 자원 입금만으로 아직 본 적 없는 개인 도감을 자동 해금하지 않는다.
- `WSP-*`는 별도 진행 탭에서 파티 전체에 동시 공개한다.
- 리소스 팩이 없으면 표의 대표 Material과 PDC ID로 구분한다.

### 8.1 고정 codexIndex

아래 위치는 발견 순서와 무관하며 삭제된 항목도 재사용하지 않는다.

| codex | ID |
|---:|---|
| 0001 | `WSR-WOOD` |
| 0002 | `WSR-STONE` |
| 0003 | `WSR-FIBER` |
| 0004 | `WSR-LEATHER` |
| 0005 | `WSR-RATION` |
| 0006 | `WSR-COAL` |
| 0007 | `WSR-IRON` |
| 0008 | `WSR-COPPER` |
| 0009 | `WSR-GOLD` |
| 0010 | `WSR-REDSTONE` |
| 0011 | `WSR-AMETHYST` |
| 0012 | `WSR-MAGIC_CRYSTAL` |
| 0013 | `WSR-TISSUE` |
| 0014 | `WSR-MUTATION_SHARD` |
| 0015 | `WSR-HERB` |
| 0016 | `WSR-ELASTIC_FIBER` |
| 0017 | `WSR-TOXIN_SAMPLE` |
| 0018 | `WSR-THERMAL_SAMPLE` |
| 0019 | `WSR-HEMATIC_SAMPLE` |
| 0020 | `WSR-NEURAL_SAMPLE` |
| 0021 | `WSR-VITAL_TISSUE` |
| 0022 | `WSR-PURIFY_CATALYST` |
| 0023 | `WSR-RIFT_POWDER` |
| 0024 | `WSR-REFINED_MUTATION` |
| 0025 | `WSR-STABLE_CORE` |
| 0026 | `WSR-PURIFY_MEDIUM` |
| 0027 | `WSR-HARD_AGGREGATE` |
| 0028 | `WSR-RESONANCE_COIL` |
| 0029 | `WSR-PATTERN_RESIDUE` |
| 0030 | `WSR-HIGH_DENSITY_ALLOY` |
| 0031 | `WSR-INTERRUPT_CORE` |
| 0032 | `WSR-POWER_MATRIX` |
| 0033 | `WSR-CALIBRATED_LENS` |
| 0034 | `WSR-PURIFY_MATRIX` |
| 0035 | `WSR-STABILIZED_FRAME` |
| 0036 | `WSR-FINAL_SIGNAL_KEY` |
| 0037 | `WSR-HARDWOOD_PART` |
| 0038 | `WSR-SINTERED_AGGREGATE` |
| 0039 | `WSR-METAL_PLATE` |
| 0040 | `WSR-COPPER_COIL` |
| 0041 | `WSR-REFINED_ALLOY` |
| 0042 | `WSR-PRECISION_PART` |
| 0043 | `WSR-CRUDE_PURIFY_CATALYST` |
| 0044 | `WSR-SIGNAL_LENS` |
| 0045 | `WSR-REINFORCED_CLOTH` |
| 0046 | `WSR-STERILE_GEL` |
| 0047 | `WSR-ELASTIC_WEAVE` |
| 0048 | `WSR-REINFORCED_ALLOY` |
| 0049 | `WSR-NEURAL_CIRCUIT` |
| 0050 | `WSR-BIO_MEDIUM` |
| 0051 | `WSR-STATUS_PLATE` |
| 0052 | `WSR-RESONANT_RESIDUE` |
| 0053 | `WSR-NEURAL_RESIDUE` |
| 0054 | `WSP-BOSS-D10-CORE` |
| 0055 | `WSP-REBUILD-PART-A` |
| 0056 | `WSP-REBUILD-PART-B` |
| 0057 | `WSP-REBUILD-PART-C` |
| 0058 | `WSP-REBUILD-PART-D` |
| 0059 | `WSR-BOSS_SIGNAL_CORE` |

## 9. 합계와 검증

| 범주 | 수량 |
|---|---:|
| 기존 원장 자원 | 36 |
| 새 가공 부품 | 15 |
| 새 보스·호출 소비 재료 | 3 |
| 비소각 진행 증명 | 5 |
| 전체 | 59 |

필수 검증:

- ID·codexIndex 중복 0
- 모든 수량 자원에 획득 경로와 소비처 각각 1개 이상
- 모든 증명에 최초 지급 transaction과 복구 경로 존재
- 초반·후반 동명 자동 대체 0
- 개인→공용 자동 입금 0
- 시설 미설치 상태의 개인 제작 경로 존재
- 도구 등급 부족 시 블록 보존·중복 드롭 0
- Day 49 이전 Final 키 생성 0, Day 50 이전 사용 0
