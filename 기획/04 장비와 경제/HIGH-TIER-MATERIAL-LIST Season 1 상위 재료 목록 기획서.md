# WildSurvival Season 1 상위 재료 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `HIGH-TIER-MATERIAL-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 역할 | `MATERIAL-LIST-001` 59개 중 T3~T6 연구·장비·보스·재건 재료의 별도 고정 투영 목록 |
| 상위 권위 | `MATERIAL-LIST-001`, `RECIPE-LIST-001`, `CONTENT-DATA-D50-001` |
| 데이터 리비전 | `material-s1-r1` |
| 최종 수정일 | 2026-08-24 |

## 1. 고정 목록 23개

| codex | ID | 표시명 | Tier | 최초 Day | 대표 Material |
|---:|---|---|---|---:|---|
| 0020 | `WSR-NEURAL_SAMPLE` | 신경 표본 | T3 | 14 | FERMENTED_SPIDER_EYE |
| 0021 | `WSR-VITAL_TISSUE` | 생체 조직 | T3 | 16 | SCUTE |
| 0022 | `WSR-PURIFY_CATALYST` | 안정 정화 촉매 | T4 | 21 | AMETHYST_SHARD |
| 0023 | `WSR-RIFT_POWDER` | 균열 가루 | T4 | 23 | GUNPOWDER |
| 0024 | `WSR-REFINED_MUTATION` | 정제 변이 파편 | T4 | 25 | PAPER |
| 0025 | `WSR-STABLE_CORE` | 안정화 코어 | T5 | 27 | HEART_OF_THE_SEA |
| 0026 | `WSR-PURIFY_MEDIUM` | 정화 매질 | T5 | 28 | SLIME_BALL |
| 0027 | `WSR-HARD_AGGREGATE` | 고강도 구조 골재 | T4 | 31 | DEEPSLATE |
| 0028 | `WSR-RESONANCE_COIL` | 공진 코일 | T5 | 32 | REDSTONE_TORCH |
| 0029 | `WSR-PATTERN_RESIDUE` | 패턴 잔재 | T5 | 34 | GUNPOWDER |
| 0030 | `WSR-HIGH_DENSITY_ALLOY` | 고밀도 합금 | T5 | 37 | NETHERITE_SCRAP |
| 0031 | `WSR-INTERRUPT_CORE` | 중단 코어 | T6 | 39 | HEART_OF_THE_SEA |
| 0032 | `WSR-POWER_MATRIX` | 동력 행렬 | T6 | 41 | REDSTONE_TORCH |
| 0033 | `WSR-CALIBRATED_LENS` | 교정 렌즈 | T6 | 42 | AMETHYST_SHARD |
| 0034 | `WSR-PURIFY_MATRIX` | 정화 행렬 | T6 | 43 | REDSTONE_TORCH |
| 0035 | `WSR-STABILIZED_FRAME` | 안정 프레임 | T6 | 44 | NETHERITE_SCRAP |
| 0044 | `WSR-SIGNAL_LENS` | 신호 렌즈 | T3 | 6 | AMETHYST_SHARD |
| 0046 | `WSR-STERILE_GEL` | 멸균 젤 | T3 | 11 | SLIME_BALL |
| 0047 | `WSR-ELASTIC_WEAVE` | 탄성 직조 | T3 | 12 | PAPER |
| 0048 | `WSR-REINFORCED_ALLOY` | 강화 합금 | T3 | 14 | NETHERITE_SCRAP |
| 0049 | `WSR-NEURAL_CIRCUIT` | 신경 회로 | T3 | 15 | REDSTONE_TORCH |
| 0050 | `WSR-BIO_MEDIUM` | 생체 매질 | T4 | 16 | SLIME_BALL |
| 0051 | `WSR-STATUS_PLATE` | 상태 분류판 | T3 | 13 | NETHERITE_SCRAP |

## 2. Tier 합계와 경계

| Tier | 수량 | 주 용도 |
|---|---:|---|
| T3 | 8 | D10~20 의료·상태·호출·강화 |
| T4 | 5 | D21~31 정화·균열·변이·구조 |
| T5 | 5 | D27~40 보스 호출·공진·심연 장비 |
| T6 | 5 | D39~50 중단·재건·Final |
| 합계 | 23 |  |

- T0~T2 기초·중간 재료, `PARTY_RESOURCE`, `BOUND_PROOF`, `UNIQUE`는 `MATERIAL-LIST-001`에 남고 이 상위 재료 투영에는 포함하지 않는다.
- Tier는 희귀도나 아이템 레벨이 아니라 획득·가공·시설 접근 단계다.
- `firstDay`는 하한이며 연구·발견·시설·recipe 조건을 우회하지 않는다.
- 이 목록 23개는 `MATERIAL-LIST-001` 59개 집합의 진부분집합이고 새 자원 ID를 추가하지 않는다.
