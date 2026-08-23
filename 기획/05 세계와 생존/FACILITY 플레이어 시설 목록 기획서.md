# WildSurvival FACILITY 플레이어 시설 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | FACILITY-LIST-001 |
| 상태 | `DATA_LOCKED` |
| 최종 수정일 | 2026-08-23 |
| 상위 기준 | `BASE-001~003`, `DISC-LIST`, `RES-001`, `ECONOMY`, `TECH` |
| 목적 | 자유 야생에서 플레이어가 제작·배치할 실제 장치와 시설 목록 정의 |
| 구현 | 바닐라 블록 조합, 표시 엔티티, PDC 시설 ID, 인벤토리 GUI |
| 리소스 팩 | 불필요 |

## 1. 시설 설계 원칙

- 시작부터 완성된 기지를 제공하지 않는다.
- 시설은 휴대 장치, 야영 시설, 정착 시설, 재건 시설로 나뉜다.
- 시설을 짓지 않아도 휴대 경로로 핵심 진행을 유지할 수 있다.
- 시설은 처리량·효율·정보·공유 기능을 높이는 대신 자원, 위치 노출과 방어 부담을 만든다.
- 특정 시설이 파괴돼도 고유 부품은 복구 원장에 남고, 하위 등급 대안으로 재건할 수 있다.
- 바닐라 블록 외형은 표현일 뿐이며 실제 기능은 `facilityInstanceId`와 서버 원장이 결정한다.
- 시설 반경은 자동 안전 지대가 아니다.

## 2. 공통 시설 데이터

| 필드 | 설명 |
|---|---|
| `facilityType` | 시설 목록 ID |
| `facilityTier` | PORTABLE/CAMP/SETTLEMENT/RECONSTRUCTION |
| `representation` | 바닐라 블록·표시 엔티티 조합 |
| `networkPolicy` | 독립, 시설망 연결, 중계 필요 여부 |
| `workSlots` | 동시 작업 수 |
| `powerUse` | 동력 또는 연료 소비 |
| `threatValue` | 습격 목표 가치와 신호 발생량 |
| `corruptionResponse` | 오염 단계별 효율·손상 |
| `damageProfile` | HP, 방어 태그, 파괴 정책 |
| `portableFallback` | 파괴·미설치 시 대체 방식 |
| `unlockDiscovery` | 발견 노드 |

# 3. 휴대 장치

휴대 장치는 실제 설치 시설이 아니라 아이템 또는 짧은 수명의 임시 상호작용점이다. 전투 중에는 대부분 사용할 수 없다.

| ID | 이름 | 바닐라 표현 | 기능 | 제한·비용 |
|---|---|---|---|---|
| FAC-P01 | 휴대 제작 꾸러미 | 번들/상자 아이템 | 기초 제작·분해·복구 부품 | 처리시간 150%, 고급 장비 불가 |
| FAC-P02 | 야전 수리 키트 | 모루 아이템 | BROKEN 장비 응급 복구 | 최대 내구 40%까지만, 소모품 |
| FAC-P03 | 표본 채취기 | 유리병/솔 | 오염·생체·광물 표본 채취 | 피격·이동 시 중단 |
| FAC-P04 | 휴대 분석기 | 나침반/시계 | 증거 비교·신호 측정·소급 판정 | 한 번에 표본 2개, 느림 |
| FAC-P05 | 휴대 정화기 | 자수정 조각/양조기 아이콘 | 작은 반경 임시 정화장 | 촉매 소모, 60초, 출력 낮음 |
| FAC-P06 | 신호 말뚝 | 피뢰침/엔드 막대 아이템 | 공명 측정·동적 전장 후보 표시 | 회수 전 재사용 불가 |
| FAC-P07 | 구조 신호기 | 종/회복 토템 아이콘 | 빈사 위치 표시와 구조시간 소폭 감소 | 전투당 1회, 자동 구조 아님 |
| FAC-P08 | 휴대 보관 원장 | 책 | 공용 원장 조회·소량 입출고 예약 | 실제 입출고는 비전투 안전 상태 |

휴대 장치만으로도 C01~C30 핵심 발견을 진행할 수 있지만 반복 가공과 대량 저장 효율은 정착 시설보다 낮다.

# 4. 야영 시설

야영 시설은 낮은 비용으로 빠르게 설치·철거할 수 있으며 시설망 연결 없이 독립 작동할 수 있다.

| ID | 이름 | 대표 블록 | 기능 | 위협값 | 휴대 대안 |
|---|---|---|---|---:|---|
| FAC-C01 | 간이 작업대 | 작업대+통 | 기초 장비·부품, 작업 슬롯 1 | 2 | P01 |
| FAC-C02 | 야전 화로 | 화로+모닥불 | 제련·식량 가공, 연료 효율 100% | 3 | 바닐라 화로 |
| FAC-C03 | 임시 보관함 | 통/상자 | 파티 등록 보관 9칸, 파괴 시 원장 보호 | 2 | 개인 인벤토리 |
| FAC-C04 | 침낭 표식 | 양탄자+랜턴 | 재접속 안전 후보, 비전투 회복 보조 | 1 | 마지막 유효 위치 |
| FAC-C05 | 간이 경보종 | 종+울타리 | 40블록 위험 방향 경고 | 3 | 액션바 짧은 경고 |
| FAC-C06 | 야전 약제대 | 모닥불+양조기 | 기본 해독·지혈·회복품 | 3 | 인벤토리 조합 |
| FAC-C07 | 소형 탄약대 | 화살 제조대/통 | 일반 탄약 묶음 제작 | 3 | 기본 제작대 |
| FAC-C08 | 임시 바리케이드 | 울타리·철창 | 공세 전용 등록 방어 블록 | 배치당 1 | 자연 지형·이동 |

### 야영 시설 철거

- 비전투 상태에서 3초 채널링한다.
- 핵심 부품 80%, 일반 재료 50%를 회수하는 기준안을 사용한다.
- 설치 후 2분 안에 철거하면 일반 재료 회수율을 20%로 낮춰 반복 설치 이득을 막는다.
- 임시 바리케이드는 압박 종료 후 자동 잔해화하며 영구 건축 대체물이 아니다.

# 5. 정착 시설

정착 시설은 `facilityNetworkId`에 연결한다. 네트워크가 여러 개여도 되며 주 거점을 지정할 의무는 없다.

## 5.1 제작·장비

| ID | 이름 | 대표 블록 | 기능 | 기본 작업 슬롯 | 위협값 |
|---|---|---|---|---:|---:|
| FAC-S01 | 정밀 제작대 | 대장장이 작업대+구리 | 희귀 이상 장비·시설 부품 제작 | 2 | 8 |
| FAC-S02 | 강화 단조대 | 모루+용광로 | 100% 확정 강화, BROKEN 완전 수리 | 1 | 10 |
| FAC-S03 | 재련 조율기 | 숫돌+자수정 | 옵션 잠금·재련, 천장 진행 저장 | 1 | 12 |
| FAC-S04 | 분해·회수기 | 석재 절단기+호퍼 | 장비 분해, 부산물·제작식 연구 | 2 | 7 |
| FAC-S05 | 탄약 압축기 | 화살 제조대+피스톤 표현 | 특수 탄약·탄창 일괄 제작 | 2 | 7 |

## 5.2 연구·발견

| ID | 이름 | 대표 블록 | 기능 | 기본 작업 슬롯 | 위협값 |
|---|---|---|---|---:|---:|
| FAC-S06 | 연구 단말 | 독서대+책장 | 표본 분석, 발견 증거 비교, 도감 | 2 | 9 |
| FAC-S07 | 관측 배열 | 피뢰침+망원경 아이콘 | 다음 압박 방향·전략·신호 오차 분석 | 1 | 12 |
| FAC-S08 | 패턴 기록기 | 주크박스+표시 엔티티 | 적 패턴 전조·대응 로그 분석 | 1 | 9 |
| FAC-S09 | 증강 공명기 | 마법부여대+자수정 | 증강 교체, 진화 계보·태그 검사 | 1 | 14 |
| FAC-S10 | 훈련 단말 | 갑옷 거치대+표적 블록 | 모의 구조·CC·브레이크, 스탯 투자 | 파티별 1 | 5 |

훈련 단말은 실제 EXP·자원·보상·증강 발동을 발생시키지 않는다.

## 5.3 생존·오염

| ID | 이름 | 대표 블록 | 기능 | 기본 작업 슬롯 | 위협값 |
|---|---|---|---|---:|---:|
| FAC-S11 | 치료소 | 양조기+침대 | 부상 회복, 해독, 구조 보조 | 2 | 10 |
| FAC-S12 | 유품 회수대 | 영혼 모닥불+상자 | 접근 불가 유품 복구 요청·귀속 확인 | 1 | 8 |
| FAC-S13 | 정화기 | 비콘+자수정 표현 | 시설망·플레이어·지역 오염 정화 | 1 | 16 |
| FAC-S14 | 정화 중계기 | 피뢰침+엔드 막대 | 정화 출력 범위 전달 | 해당 없음 | 6 |
| FAC-S15 | 환경 차폐기 | 구리 블록+철창 | 낙뢰·오염 파동 등 특정 환경 위험 완화 | 1 | 9 |

정화기 블록 표현에 비콘을 사용해도 바닐라 비콘 효과는 차단한다. 실제 범위와 효과는 CORR 시스템이 계산한다.

## 5.4 물류·세션·이동

| ID | 이름 | 대표 블록 | 기능 | 기본 작업 슬롯 | 위협값 |
|---|---|---|---|---:|---:|
| FAC-S16 | 공용 물류고 | 상자+통+엔더 상자 표현 | 가상 공용 원장 입출고·작업 예약 | 3 | 11 |
| FAC-S17 | 동력 분배기 | 레드스톤 블록+구리 | 연결 시설 동력·연료 분배 | 해당 없음 | 13 |
| FAC-S18 | 이동 앵커 | 자석석+나침반 | 등록 앵커 사이 제한 빠른 이동 | 1 | 10 |
| FAC-S19 | 세션 중계기 | 종+독서대 | 정상 중단 투표·체크포인트 요청 | 1 | 4 |
| FAC-S20 | 공세 관측기 | 스컬크 센서+종 | 압박 모드·접근 방향·잔존 적 표시 | 1 | 12 |

- 이동 앵커는 전투·공세·보스·추적 중 사용할 수 없다.
- 세션 중계기가 없어도 안전 상태에서 명령으로 정상 중단 투표를 요청할 수 있다.
- 공용 물류고가 파괴돼도 가상 원장 자원은 소실되지 않고 입출고만 잠긴다.

## 5.5 방어 시설

| ID | 이름 | 대표 블록 | 기능 | 상한 | 위협값 |
|---|---|---|---|---:|---:|
| FAC-D01 | 보강벽 등록기 | 철 블록/철창 | 지정 임시 벽을 공세 파괴 대상으로 등록 | 네트워크당 48블록 | 1/블록 |
| FAC-D02 | 감속 함정 | 거미줄/트립와이어 | 적 이동 감속, 강한 CC 아님 | 8개 | 3 |
| FAC-D03 | 충격 함정 | 피스톤/압력판 | 낮은 브레이크·밀치기 | 6개 | 4 |
| FAC-D04 | 유도 신호기 | 피뢰침/레드스톤 횃불 | 제한 시간 적 목표 우선순위 변경 | 2개 | 8 |

- 자동 피해 시설만으로 공세를 완료할 수 없게 전체 공세 피해 기여 상한을 둔다.
- 함정은 보상 막타와 개인 증강 발동을 제공하지 않는다.
- 자연 지형과 일반 건축은 공세가 무차별 파괴하지 않는다.

# 6. 재건 시설

재건 시설은 Day 41 이후 발견과 재건 부품 A~D를 통해 단계적으로 조립한다. 하나의 고정 위치를 요구하지 않지만, 조립을 시작한 시설망에 귀속된다.

| ID | 이름 | 대표 블록 | 기능 | 필수 선행 | 위협값 |
|---|---|---|---|---|---:|
| FAC-R01 | 재건 조립 프레임 | 철·구리·석영 다중 블록 | 하위 계통 결합·시설 본체 | C27, 부품 A | 18 |
| FAC-R02 | 안정 동력 핵 | 레드스톤 램프+자수정 | 최종 장치 동력·과부하 시험 | C28-B, 부품 B | 22 |
| FAC-R03 | 신호 렌즈 배열 | 비콘 유리+피뢰침 | 세계 붕괴 핵 신호 교정 | C28-C, 부품 C | 20 |
| FAC-R04 | 정화 매질실 | 유리+물+자수정 | 최종 정화 매질 저장·순환 | C28-D, 부품 D | 22 |
| FAC-R05 | 교정 말뚝 | 자석석/엔드 막대 | 세 방향 오차 측정·안정화 | C29, 3개 | 8 |
| FAC-R06 | 첫 재건 장치 | R01~R05 결합 상태 | Day 50+ 최종 신호와 정화 유지 | C29, R01~R05 READY, 최종 신호 키 | 30 |

### 6.1 재건 시설 실제 투입

| 시설 | 실제 투입·시험 |
|---|---|
| R01 | 안정 프레임 12, 경화 골재 16, 부품 A 원장 결합 |
| R02 | 동력 행렬 6, 공진 코일 8, 부품 B 원장 결합, 60초 출력 시험 |
| R03 | 교정 렌즈 4, 패턴 잔재 6, 부품 C 원장 결합, 세 방향 오차 시험 |
| R04 | 정화 행렬 6, 정화 촉매 10, 부품 D 원장 결합, 환경 2종 반응 시험 |
| R05 | 말뚝마다 고밀도 합금 2, 교정 렌즈 1, 공진 코일 2, 총 3개 |
| R06 | R01~R05 결합, 최종 신호 키 존재 확인, Day 50 전 `READY_LOCKED` 유지 |

- 재건 부품 A~D는 시설 귀속을 증명하지만 소각하지 않는다.
- 실제 자원 ID와 제작식은 `CONTENT-DATA-D50-001 §6~7`, 활성화와 출력 계산은 `FINAL-001`을 따른다.
- C30은 R06 제작 선행이 아니라 최종 목표 완료 트랜잭션의 결과다.

### 6.2 최종 실행 기능

- Stage 1에는 R06과 말뚝 3개를 동적 전장에 전개한다.
- Stage 3 유효 출력은 R06 40, 말뚝 각 20, 플레이어 채널 합계 최대 20, 방해기당 -20으로 계산하며 70 이상을 180초 누적한다.
- 최종전 공격은 영구 파괴 대신 `OUTPUT_DISABLED`·임시 손상만 주고, 실패 복구 뒤 재사용할 수 있게 한다.
- 마지막 협동 확인은 살아 있는 등록 플레이어가 수행한다. 최종 생존자가 1명뿐이어도 완료할 수 있다.

### 재건 시설 보호

- Day 50 이전에는 `READY_LOCKED`까지만 전환하고 최종 활성화를 거부한다.
- 파괴 시 고유 부품 A~D를 회차 원장으로 되돌리고 일반 재료만 일부 손실한다.
- 지형 변화로 다중 블록 검사가 실패하면 기능을 잠그고 재배치 복구를 제공한다.
- 시설 위치를 옮기려면 모든 하위 작업을 중단하고 30초 철거 채널링과 일반 재료 손실을 감수한다.
- 최종 전투는 시설 반경의 안전 후보를 사용하되 전장이 시설 자체를 겹쳐 즉시 파괴하지 않게 한다.

## 7. 시설 등급과 업그레이드

| 시설 레벨 | 역할 | 공통 변화 |
|---:|---|---|
| 1 | 기본 기능 | 작업 슬롯·출력 기본값 |
| 2 | 처리 안정화 | 처리시간 -10%, HP +15% |
| 3 | 전문 개조 | 개조 슬롯 1, 출력 +15% |
| 4 | 네트워크 확장 | 중계·원격 작업 기능 |
| 5 | 최종 효율 | 개조 슬롯 추가, 출력 +15%, 효율 상한 |

- 모든 시설이 5레벨을 가질 필요는 없다. 목록 데이터의 `maxLevel`로 제한한다.
- 처리시간·출력·연료 효율을 동시에 최대치로 올리지 않고 개조 선택으로 분기한다.
- 업그레이드 완료 전 시설 파괴 시 투입 고유 부품은 원장으로, 일반 재료는 규정 환불률로 처리한다.

## 8. 네트워크 연결 규칙

```text
직접 연결 = 시설 중심 간 거리 24블록 이하
중계 연결 = 동력 분배기 또는 기능별 중계기 경유
```

- 서로 다른 시설망이 거리 조건으로 합쳐질 때 양쪽 소유 파티와 병합 확인을 검사한다.
- 단일 파티 콘텐츠이므로 소유권 분쟁 대신 실수 병합 방지 확인을 제공한다.
- 연결 그래프는 시설 설치·철거·중계 손상 때만 재계산한다.
- 한 네트워크의 기본 시설 상한은 32개, 방어 블록은 별도 64개로 시작한다.
- 상한 초과 시설은 설치를 거부하고 가장 가까운 네트워크 분리 방법을 안내한다.

## 9. 시설 위협도

```text
네트워크 위협도 =
활성 시설 위협값 합
× 최근 5분 사용량 보정
× 재건 신호 보정
× 오염 단계 보정
```

- 위협도는 `SETTLEMENT_ASSAULT` 목표 선택에 사용한다.
- 시설을 전혀 짓지 않으면 `PURSUIT` 또는 `RIFT_PRESSURE`가 발생하므로 야간 압박을 회피할 수 없다.
- 장식용 일반 건축 블록은 시설 위협도에 포함하지 않는다.
- 사용하지 않은 오래된 야영 시설은 위협도가 점진 감소한다.

## 10. 작업 대기열

| 상태 | 처리 |
|---|---|
| `QUEUED` | 입력 재료 예약, 아직 소비 확정 전 |
| `PROCESSING` | 재료 소비 확정, 시간 진행 |
| `PAUSED` | 동력·연료·시설 손상으로 정지 |
| `COMPLETED` | 결과가 가상 출력함에 확정 |
| `CLAIMED` | 플레이어·공용 원장으로 이동 |
| `CANCELLED` | 정책 환불 후 종료 |

- 작업 등록자 접속 종료로 취소하지 않는다.
- 시설 파괴 시 `PROCESSING` 작업은 네트워크 복구 원장으로 이동한다.
- 결과를 바닥에 직접 드롭하지 않고 출력함 또는 공용 원장에 저장한다.
- 같은 작업 ID를 두 번 수령할 수 없다.
- Day 스킵 가결 시 실제 경과 시간까지만 처리하고 건너뛴 남은 시간을 작업 진행에 더하지 않는다.

## 11. 시설 GUI

모든 시설 GUI는 다음 공통 영역을 가진다.

- 시설명·레벨·HP·손상 단계
- 네트워크 ID와 연결 상태
- 현재 오염·위협·동력·연료
- 입력·작업 대기열·출력
- 업그레이드·개조·수리
- 권한·철거·검사

리소스 팩 없이 바닐라 아이템, 이름, 로어, 숫자, 색과 소리로 표시한다. 기능 정지 원인을 `전력 부족`, `연료 없음`, `손상`, `오염`, `연결 끊김`으로 구분한다.

## 12. 데이터 예시

```yaml
id: ws.facility.research_terminal
tier: SETTLEMENT
representation:
  core-block: LECTERN
  required-nearby: [BOOKSHELF]
network-policy: CONNECTED
max-level: 4
base-hp: 4200
work-slots: 2
threat-value: 9
unlock-discovery: ws.discovery.c05_resonance_reaction
portable-fallback: ws.item.portable_analyzer
```

## 13. 테스트 항목

### 12.1 생산 item·recipe·상태 매핑

| facilityId | Tier | 휴대/배치 입력 | recipeId | 상태 기계 |
|---|---|---|---|---|
| `FAC-P01` | PORTABLE | `WSI-PORTABLE-CRAFT_KIT` | `WSRCP-F01` | READY↔ACTIVE/COOLDOWN |
| `FAC-P02` | PORTABLE | `WSI-CONS-REPAIR_KIT` | `WSRCP-S02` | READY↔ACTIVE/COOLDOWN |
| `FAC-P03` | PORTABLE | `WSI-PORTABLE-SAMPLE_EXTRACTOR` | `WSRCP-F08` | READY↔ACTIVE/COOLDOWN |
| `FAC-P04` | PORTABLE | `WSI-PORTABLE-ANALYZER` | `WSRCP-F07` | READY↔ACTIVE/COOLDOWN |
| `FAC-P05` | PORTABLE | `WSI-PORTABLE-PURIFIER` | `WSRCP-F09` | READY↔ACTIVE/COOLDOWN |
| `FAC-P06` | PORTABLE | `WSI-PORTABLE-SIGNAL_STAKE` | `WSRCP-G01` | READY↔ACTIVE/COOLDOWN |
| `FAC-P07` | PORTABLE | `WSI-PORTABLE-RESCUE_BEACON` | `WSRCP-F10` | READY↔ACTIVE/COOLDOWN |
| `FAC-P08` | PORTABLE | `WSI-PORTABLE-LEDGER` | `WSRCP-F11` | READY↔ACTIVE/COOLDOWN |
| `FAC-C01` | CAMP | `WSI-FAC-C01-KIT` | `WSRCP-F02` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-C02` | CAMP | `WSI-FAC-C02-KIT` | `WSRCP-F03` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-C03` | CAMP | `WSI-FAC-C03-KIT` | `WSRCP-F04` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-C04` | CAMP | `WSI-FAC-C04-KIT` | `WSRCP-F12` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-C05` | CAMP | `WSI-FAC-C05-KIT` | `WSRCP-F13` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-C06` | CAMP | `WSI-FAC-C06-KIT` | `WSRCP-F05` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-C07` | CAMP | `WSI-FAC-C07-KIT` | `WSRCP-F06` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-C08` | CAMP | `WSI-FAC-C08-KIT` | `WSRCP-F14` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S01` | SETTLEMENT | `WSI-FAC-S01-KIT` | `WSRCP-FAC-S01` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S02` | SETTLEMENT | `WSI-FAC-S02-KIT` | `WSRCP-FAC-S02` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S03` | SETTLEMENT | `WSI-FAC-S03-KIT` | `WSRCP-FAC-S03` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S04` | SETTLEMENT | `WSI-FAC-S04-KIT` | `WSRCP-FAC-S04` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S05` | SETTLEMENT | `WSI-FAC-S05-KIT` | `WSRCP-FAC-S05` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S06` | SETTLEMENT | `WSI-FAC-S06-KIT` | `WSRCP-D20-F02` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S07` | SETTLEMENT | `WSI-FAC-S07-KIT` | `WSRCP-FAC-S07` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S08` | SETTLEMENT | `WSI-FAC-S08-KIT` | `WSRCP-FAC-S08` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S09` | SETTLEMENT | `WSI-FAC-S09-KIT` | `WSRCP-D20-F05` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S10` | SETTLEMENT | `WSI-FAC-S10-KIT` | `WSRCP-D20-F04` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S11` | SETTLEMENT | `WSI-FAC-S11-KIT` | `WSRCP-D20-F03` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S12` | SETTLEMENT | `WSI-FAC-S12-KIT` | `WSRCP-FAC-S12` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S13` | SETTLEMENT | `WSI-FAC-S13-KIT` | `WSRCP-FAC-S13` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S14` | SETTLEMENT | `WSI-FAC-S14-KIT` | `WSRCP-FAC-S14` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S15` | SETTLEMENT | `WSI-FAC-S15-KIT` | `WSRCP-FAC-S15` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S16` | SETTLEMENT | `WSI-FAC-S16-KIT` | `WSRCP-FAC-S16` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S17` | SETTLEMENT | `WSI-FAC-S17-KIT` | `WSRCP-FAC-S17` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S18` | SETTLEMENT | `WSI-FAC-S18-KIT` | `WSRCP-FAC-S18` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S19` | SETTLEMENT | `WSI-FAC-S19-KIT` | `WSRCP-FAC-S19` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-S20` | SETTLEMENT | `WSI-FAC-S20-KIT` | `WSRCP-FAC-S20` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-D01` | DEFENSE | `WSI-FAC-D01-KIT` | `WSRCP-FAC-D01` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-D02` | DEFENSE | `WSI-FAC-D02-KIT` | `WSRCP-FAC-D02` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-D03` | DEFENSE | `WSI-FAC-D03-KIT` | `WSRCP-FAC-D03` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-D04` | DEFENSE | `WSI-FAC-D04-KIT` | `WSRCP-FAC-D04` | PLANNED→PLACING→ACTIVE↔DEGRADED↔DISABLED |
| `FAC-R01` | RECONSTRUCTION | `VIRTUAL_BUILD` | `WSRCP-R01` | PLANNED→ASSEMBLED→TESTING/CALIBRATING→READY |
| `FAC-R02` | RECONSTRUCTION | `VIRTUAL_BUILD` | `WSRCP-R02` | PLANNED→ASSEMBLED→TESTING/CALIBRATING→READY |
| `FAC-R03` | RECONSTRUCTION | `VIRTUAL_BUILD` | `WSRCP-R03` | PLANNED→ASSEMBLED→TESTING/CALIBRATING→READY |
| `FAC-R04` | RECONSTRUCTION | `VIRTUAL_BUILD` | `WSRCP-R04` | PLANNED→ASSEMBLED→TESTING/CALIBRATING→READY |
| `FAC-R05` | RECONSTRUCTION | `VIRTUAL_BUILD` | `WSRCP-R05` | PLANNED→ASSEMBLED→TESTING/CALIBRATING→READY |
| `FAC-R06` | RECONSTRUCTION | `VIRTUAL_BUILD` | `WSRCP-R06` | PLANNED→ASSEMBLED→TESTING/CALIBRATING→READY |

- 키트는 배치 Manifest와 facilityInstance 저장이 성공한 commit에서만 소비한다. 위치·권한·다중 블록·청크 저장 실패는 같은 instance item을 반환한다.
- R01~R06은 휴대 아이템을 만들지 않는다. 가상 조합 결과가 회차 시설 원장과 다중 블록 Manifest를 직접 만든다.
- 안전 철거·파괴·이전은 고유 부품과 작업을 복구 원장에 먼저 보존한 뒤 표현 블록을 제거한다.
- 피스톤·실크터치·폭발·복제 블록·호퍼는 facility instance를 이동·복제·회수하지 못한다.

### 12.2 FAC-S16 공용 물류고 게이트

- `FAC-S16`이 회차에서 한 번도 ACTIVE가 아니면 수량 자원은 획득자의 개인 원장/인벤토리로만 들어간다.
- 설치 전 제작은 요청자의 개인 자원만 자동 예약한다. 다른 플레이어 기여는 각자 `CONTRIBUTE` 확인이 필요하다.
- 최초 ACTIVE commit 뒤에만 공용 원장을 생성하고, 플레이어가 GUI에서 명시 입금한 수량만 공용으로 이동한다.
- 설치 뒤 획득도 개인 지급이 기본이다. 자동 입금 옵션은 제공하지 않는다.
- DISABLED·파괴·네트워크 단절 중 공용 수량은 보존하지만 입출고·신규 예약을 정지한다.
- `FAC-P08`은 FAC-S16을 우회해 공용 원장을 생성하지 않으며, 최초 설치 전에는 제작 안내와 개인 원장만 보여준다.

- 임의 지형에서 시설 배치·다중 블록 검사·철거
- 여러 소규모 시설망과 단일 대형 시설망의 연결 계산
- 시설 없이 휴대 장치만으로 핵심 발견 진행
- 공용 작업 중 서버 종료·시설 파괴·네트워크 분리
- 정화기·관측기·재건 시설의 위협도 목표 선택
- 시설을 짓지 않은 파티의 추적 압박 발생
- 방어 시설 자동 피해만으로 보상·증강 발동 불가
- 고유 재건 부품 파괴·철거·재배치 복구
- 이동 앵커 전투·공세 중 차단
- 훈련 단말 보상·EXP·증강 발동 차단
- 리소스 팩 없는 GUI 가독성

## 14. 완료 기준

- 휴대 8개, 야영 8개, 정착·방어 24개, 재건 6개의 역할이 정의된다.
- 시설마다 휴대 대안 또는 파괴 복구 경로가 있다.
- 유목·다중 야영·대형 정착지 플레이가 모두 가능하다.
- 시설 건설이 이득과 위협을 함께 만들지만 핵심 진행의 유일 경로가 아니다.
- 모든 표현과 기능이 바닐라 클라이언트와 Paper 플러그인으로 구현 가능하다.
