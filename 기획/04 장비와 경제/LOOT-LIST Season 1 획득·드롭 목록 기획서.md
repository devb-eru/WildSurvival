# WildSurvival Season 1 획득·드롭 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `LOOT-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 적용 범위 | 채집, 전투, 정예, 보스, 사건, 분해, 복구의 아이템·자원·장비 획득 |
| 상위 기준 | `REWARD-001`, `MATERIAL-LIST-001`, `ITEM-LIST-001`, `TOOL-LIST-001`, `ENTITY-LIST-001` |
| 데이터 리비전 | `loot-s1-r1` |
| 최종 수정일 | 2026-08-23 |

## 1. 공통 트랜잭션

- 모든 획득은 `sourceId, lootTableId, lootTransactionId, eligibleContributors, rolledEntries, ownership, claimedBy, revision`을 저장한다.
- 적의 바닥 개별 드롭 대신 전투 묶음 종료에서 판정한다. 몹마다 직접 떨구는 시각 효과는 보상 아님 표시만 사용한다.
- 공용 물류고 전에는 수량 결과를 수령자의 개인 원장·인벤토리에 지급한다. FAC-S16 이후에도 자동 공용 입금하지 않는다.
- 일반 전투 결과는 유효 contributor UUID 사이에서 최근 수령 가치가 낮은 순으로 항목을 분배한다. 막타·소환체 소유만으로 독점하지 않는다.
- 보스 증명·재건 부품·파티 증강·Story 플래그는 수량 없는 회차 원장에 직접 기록한다.
- 미수령 결과는 바닥에 버리지 않고 reward inbox에 보존한다. 사망·접속 종료·인벤토리 가득 참은 소유권을 바꾸지 않는다.
- 동일 `lootTransactionId`는 roll과 claim을 각각 한 번만 commit한다.

## 2. 적 전투 테이블 45종

| lootTableId | source | profile | 분배 | 멱등키 |
|---|---|---|---|---|
| `LOOT-EN-D1-01` | `EN-D1-01` | COMBAT-D10 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D1-01` |
| `LOOT-EN-D1-02` | `EN-D1-02` | COMBAT-D10 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D1-02` |
| `LOOT-EN-D11-01` | `EN-D11-01` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D11-01` |
| `LOOT-EN-D11-02` | `EN-D11-02` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D11-02` |
| `LOOT-EN-D12-01` | `EN-D12-01` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D12-01` |
| `LOOT-EN-D12-02` | `EN-D12-02` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D12-02` |
| `LOOT-EN-D13-01` | `EN-D13-01` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D13-01` |
| `LOOT-EN-D13-02` | `EN-D13-02` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D13-02` |
| `LOOT-EN-D14-01` | `EN-D14-01` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D14-01` |
| `LOOT-EN-D14-02` | `EN-D14-02` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D14-02` |
| `LOOT-EN-D15-01` | `EN-D15-01` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D15-01` |
| `LOOT-EN-D15-02` | `EN-D15-02` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D15-02` |
| `LOOT-EN-D16-01` | `EN-D16-01` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D16-01` |
| `LOOT-EN-D16-02` | `EN-D16-02` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D16-02` |
| `LOOT-EN-D17-01` | `EN-D17-01` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D17-01` |
| `LOOT-EN-D17-02` | `EN-D17-02` | COMBAT-D20 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D17-02` |
| `LOOT-EN-D18-E01` | `EN-D18-E01` | ELITE | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D18-E01` |
| `LOOT-EN-D18-E02` | `EN-D18-E02` | ELITE | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D18-E02` |
| `LOOT-EN-D19-E01` | `EN-D19-E01` | ELITE | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D19-E01` |
| `LOOT-EN-D2-01` | `EN-D2-01` | COMBAT-D10 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D2-01` |
| `LOOT-EN-D21-01` | `EN-D21-01` | COMBAT-D30 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D21-01` |
| `LOOT-EN-D22-01` | `EN-D22-01` | COMBAT-D30 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D22-01` |
| `LOOT-EN-D23-01` | `EN-D23-01` | COMBAT-D30 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D23-01` |
| `LOOT-EN-D24-01` | `EN-D24-01` | COMBAT-D30 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D24-01` |
| `LOOT-EN-D27-01` | `EN-D27-01` | COMBAT-D30 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D27-01` |
| `LOOT-EN-D28-E01` | `EN-D28-E01` | ELITE | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D28-E01` |
| `LOOT-EN-D31-01` | `EN-D31-01` | COMBAT-D40 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D31-01` |
| `LOOT-EN-D33-01` | `EN-D33-01` | COMBAT-D40 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D33-01` |
| `LOOT-EN-D34-01` | `EN-D34-01` | COMBAT-D40 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D34-01` |
| `LOOT-EN-D37-01` | `EN-D37-01` | COMBAT-D40 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D37-01` |
| `LOOT-EN-D38-E01` | `EN-D38-E01` | ELITE | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D38-E01` |
| `LOOT-EN-D4-01` | `EN-D4-01` | COMBAT-D10 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D4-01` |
| `LOOT-EN-D4-02` | `EN-D4-02` | COMBAT-D10 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D4-02` |
| `LOOT-EN-D41-01` | `EN-D41-01` | COMBAT-D50 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D41-01` |
| `LOOT-EN-D44-01` | `EN-D44-01` | COMBAT-D50 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D44-01` |
| `LOOT-EN-D46-E01` | `EN-D46-E01` | ELITE | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D46-E01` |
| `LOOT-EN-D47-01` | `EN-D47-01` | COMBAT-D50 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D47-01` |
| `LOOT-EN-D49-E01` | `EN-D49-E01` | ELITE | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D49-E01` |
| `LOOT-EN-D5-01` | `EN-D5-01` | COMBAT-D10 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D5-01` |
| `LOOT-EN-D6-01` | `EN-D6-01` | COMBAT-D10 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D6-01` |
| `LOOT-EN-D6-02` | `EN-D6-02` | COMBAT-D10 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D6-02` |
| `LOOT-EN-D7-01` | `EN-D7-01` | COMBAT-D10 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D7-01` |
| `LOOT-EN-D7-02` | `EN-D7-02` | COMBAT-D10 | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D7-02` |
| `LOOT-EN-D8-E01` | `EN-D8-E01` | ELITE | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D8-E01` |
| `LOOT-EN-D9-E01` | `EN-D9-E01` | ELITE | CONTRIBUTOR_ROUND_ROBIN | `battleBundleId+EN-D9-E01` |

`EN-D20-A01~A04`와 `EN-F50-A01~A04`는 `LOOT-NONE`을 사용한다.

## 3. 전투 profile

| profile | 보장 자원 | 전문·표본 | 손상 장비 | 천장 |
|---|---|---|---|---|
| `COMBAT-D10` | WSR-WOOD/STONE/FIBER/COAL 중 1~3 | TISSUE 0~1 | COMMON 2%, UNCOMMON 0.5% | 20묶음 내 UNCOMMON 1 |
| `COMBAT-D20` | IRON/COPPER/HERB/ELASTIC_FIBER 중 2~4 | 상태 표본 1~2 | UNCOMMON 4%, RARE 1.5% | 16묶음 내 RARE 설계 1 |
| `COMBAT-D30` | REINFORCED_ALLOY/NEURAL_CIRCUIT 중 1~3 | PURIFY_CATALYST/RIFT_POWDER 0~2 | RARE 4%, EPIC 1.2% | 18묶음 내 EPIC 설계 1 |
| `COMBAT-D40` | HARD_AGGREGATE/RESONANCE_COIL 1~3 | PATTERN_RESIDUE 0~1 | EPIC 3%, LEGENDARY 0.8% | 20묶음 내 LEGENDARY 설계 1 |
| `COMBAT-D50` | HIGH_DENSITY_ALLOY 재료 가치 1~2 | T5 전문 1 | LEGENDARY 2%, ABYSSAL 완제품 0% | ABYSSAL은 제작·보스만 |
| `ELITE` | 현재 Day 보장 자원 ×2 | 해당 세션 전문 2~4 | 현재 상한 손상 장비 8%, 설계 12% | 6회 내 설계 1 |

- 표의 약어는 모두 `WSR-*` 고정 ID로 JSON에 기록한다. profile 문자열을 표시명으로 런타임 해석하지 않는다.
- 손상 장비 후보는 현재 Day·발견·무기군·파티 역할에 유효한 `TOOL-LIST-001` ID만 사용한다.
- 장비 roll은 templateId, iLv, rarity, durability 20~55%, loot transaction 서명을 함께 생성한다.
- 천장은 회차 profile별 카운터이며 보장 발동 뒤 0으로 초기화한다.

## 4. 보스 테이블 4종

| ID | source | 고정 결과 | 선택 결과 | 수량 결과 |
|---|---|---|---|---|
| `LOOT-BOSS-D10` | BOSS-D10 | WSP-BOSS-D10-CORE, WSP-REBUILD-PART-A | EQL-D10 후보 3→1, PAUG 1회차 | WSR-RESONANT_RESIDUE 6/7/8 |
| `LOOT-BOSS-D20` | BOSS-D20 | WSP-REBUILD-PART-B | EQD20-LG 후보 3→1, PAUG 2회차 | WSR-NEURAL_RESIDUE 8/10/12 |
| `LOOT-BOSS-D30` | BOSS-D30 | WSP-REBUILD-PART-C | EQD50-B30 후보 3→1, PAUG 3회차 | WSR-REFINED_MUTATION 8/10/12, WSR-PURIFY_MEDIUM 6/8/10 |
| `LOOT-BOSS-D40` | BOSS-D40 | WSP-REBUILD-PART-D | EQD50-B40 후보 3→1, PAUG 4회차 | WSR-PATTERN_RESIDUE 10/13/16, WSR-HIGH_DENSITY_ALLOY 7/9/11 |

- 수량은 2/3/4인 순서이며 1인 잔존은 회차 시작 등록 인원의 고정 수량을 사용한다.
- 고정 결과는 최초 완료 한 번만 기록한다. 재전투·복구·재시작으로 재건 부품·파티 증강·최초 장비 수량을 늘리지 않는다.
- 후보 선택 transaction과 보스 완료 transaction을 분리하되 완료 ID를 부모로 묶는다. 결과 inbox가 남아 있어도 보스를 다시 생성하지 않는다.

## 5. 자원 노드 6종

| ID | source entity | 후보 | 도구·소유 |
|---|---|---|---|
| `LOOT-NODE-BASIC` | ENT-NODE-BASIC | WSR-WOOD/STONE/FIBER/LEATHER/RATION/COAL | T0~T1, 채집자 개인 |
| `LOOT-NODE-INDUSTRIAL` | ENT-NODE-INDUSTRIAL | WSR-IRON/COPPER/GOLD/REDSTONE/AMETHYST | T1~T2, 채집자 개인 |
| `LOOT-NODE-MEDICAL` | ENT-NODE-MEDICAL | WSR-HERB/ELASTIC_FIBER/상태 표본 | T3, 채집자 개인 |
| `LOOT-NODE-CORRUPTED` | ENT-NODE-CORRUPTED | WSR-TISSUE/MUTATION_SHARD/VITAL_TISSUE | T2~T4, 채집자 개인 |
| `LOOT-NODE-RIFT` | ENT-NODE-RIFT | WSR-PURIFY_CATALYST/RIFT_POWDER/REFINED_MUTATION | T4~T5, 사건 contributor 분배 |
| `LOOT-NODE-RECONSTRUCTION` | ENT-NODE-RECONSTRUCTION | WSR-HARD_AGGREGATE/RESONANCE_COIL/PATTERN_RESIDUE/HIGH_DENSITY_ALLOY | T5~T6, contributor 분배 |

- 도구 등급 부족은 node와 블록을 보존하며 바닐라·WS 드롭을 모두 지급하지 않는다.
- 일반 바닐라 블록을 resource node와 같은 tick에 처리해 이중 지급하지 않는다.
- 최초 개인 수령은 해당 재료 도감을 해금한다.

## 6. 사건·분해·복구 6종

| ID | source | 결과·제약 |
|---|---|---|
| `LOOT-ENCOUNTER-COMMON` | 일반 사건 | Day profile 일반 자원, contributor 분배 |
| `LOOT-ENCOUNTER-ELITE` | 정예 사건 | ELITE profile, 전투 묶음당 1회 |
| `LOOT-ENCOUNTER-ASSAULT` | 시설/추적 공세 | 방어 성공 가치, 자동 시설 피해만으로 claim 불가 |
| `LOOT-DISCOVERY` | 발견·연구 | recipe/도감/Story 해금, 자원 복제 없음 |
| `LOOT-SALVAGE-EQUIPMENT` | FAC-S04 분해 | 일반 50%, 가공 70%, 강화 35%, 고유 분해 금지 |
| `LOOT-RECOVERY` | 사망·시설·관리자 복구 | 새 roll 없음, 원 transaction 결과만 반환 |

`LOOT-NONE`은 결과 배열이 빈 명시적 table이다. null이나 누락을 LOOT-NONE으로 추정하지 않는다.

## 7. 합계와 검증

| 범주 | 수량 |
|---|---:|
| LOOT-NONE | 1 |
| 보상 있는 적 | 45 |
| 보스 | 4 |
| 자원 노드 | 6 |
| 사건·분해·복구 | 6 |
| 전체 | 62 |

필수 검증:

- loot table 62개 고유, entity source·entry item/resource 참조 고아 0
- 전투 묶음 중복 roll·claim 0, 막타 독점 0
- FAC-S16 전 공용 수량 자동 입금 0
- 전문·고유·보스·재건·Final 보너스 증식 0
- 소환체·핵·Final 보조·훈련체 보상 0
- 장비 후보 Day/희귀도/iLv/발견 상한 초과 0
- 인벤토리 가득 참·사망·재시작 뒤 reward inbox 손실 0

