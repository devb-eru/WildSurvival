# WildSurvival 텔레메트리·튜닝·롤백 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `QA-BALANCE-001` |
| 상태 | `VALIDATED_STATIC` |
| 권위 | Season 1 밸런스 후보의 관측·승인·튜닝·롤백 운영 계약 |
| 상위 기준 | `BALANCE-CHAOS-001`, `BALANCE-WEAPON-001`, `BALANCE-D50-001`, `BALANCE-MATRIX-001`, `TECH-001` |
| 데이터 보존 | 원시 30일, 집계 180일, 익명 회차 통계 영구 |
| 최종 수정일 | 2026-08-21 |

## 1. 목표

- 정적 원장을 실제 플레이 결과와 분리해 검증한다.
- 실패 원인을 피해 부족 하나로 환원하지 않고 전조·상태·브레이크·시설·자원·성능 축으로 기록한다.
- 활성 회차를 중간에 재튜닝하지 않고 콘텐츠 리비전 단위로 배포·롤백한다.
- Story 실패율 10%, 보스·Final 목표 시간, 10개 무기 역할 편차, TPS 안전 목표를 같은 승인 절차로 관리한다.

## 2. 이벤트 스키마

모든 이벤트 공통 필드는 다음과 같다.

```yaml
event-id: UUID
occurred-at: epochMillis
run-id-hash: sha256
content-revision: ws-content-r2
story-revision: ws-story-s1-r1
budget-policy-revision: budget-live-r2
difficulty: EASY
game-mode: STANDARD
day: 30
registered-players: 3
survivable-players: 3
active-players: 3
tps-1m: 19.7
reason-code: null
```

플레이어 UUID·채팅·좌표 원문은 수집하지 않는다. 무기군·빌드 태그는 익명 회차 내 역할 분석용으로만 저장한다.

## 3. 필수 관측 이벤트

| 이벤트 | 핵심 필드 |
|---|---|
| `RUN_CREATED` | 난이도·모드·인원·리비전 |
| `DAY_BUDGET_LOCKED` | 프로필·도메인별 base/multiplier/locked |
| `DAY_COMPLETED` | 실제 EXP·자원·잔여 위협·시설 상태 |
| `ENCOUNTER_STARTED/ENDED` | 템플릿·비용·시간·성공·철수 |
| `BOSS_PHASE` | HP·브레이크·패턴·중단·다운 |
| `COMBAT_ACTION` | 무기군·액션·유효 피해·브레이크·AP·상태, 10초 집계 |
| `AUGMENT_TRIGGER` | 증강·source-chain·수신자 수·상한 차단 |
| `PLAYER_DOWN/DEATH/REVIVE` | 원인·패턴·상태·구조 시간 |
| `FACILITY_STATE_CHANGED` | 시설·이전/이후 상태·원인 |
| `SOFTLOCK_RECOVERY` | 부족 그룹·임계·대체 사건·회복량 |
| `PERFORMANCE_STATE` | TPS·활성 엔티티·영역·투사체·HOLD 시간 |
| `FINAL_TRANSACTION` | 단계 ID·멱등키·성공·복구 |
| `RUN_ENDED` | 완료·실패·운영 중단·데이터 손상·유효성 |

## 4. KPI와 승인 목표

| ID | 지표 | 목표 | 최소 표본 | 경고 |
|---|---|---:|---:|---|
| `KPI-01` | Story STANDARD 유효 실패율 | 10% | 완료·실패 100회 | <5% 또는 >15% |
| `KPI-02` | Day30 3인 Easy 중앙 TTK | 9~13분 | 30회 | 범위 밖 20%+ |
| `KPI-03` | Day40 3인 Easy 중앙 TTK | 10~14분 | 30회 | 범위 밖 20%+ |
| `KPI-04` | Final 3인 Easy 전체 시간 | 15~22분 | 30회 | <13 또는 >26분 |
| `KPI-05` | 첫 브레이크 | 110~165초 | 보스별 30회 | 실패 페이즈 40%+ |
| `KPI-06` | 무기 역할 지수 중앙 편차 | ±5% | 무기별 60분 전투 | ±10% |
| `KPI-07` | 빌드별 선택률 | 5~20% | 증강 선택 300회 | <2% 또는 >30% |
| `KPI-08` | 75% 자원 하한 미달 | <10% | 세션별 50회 | >20% |
| `KPI-09` | 소프트락 복구 발동 | 10~35% | 세션별 50회 | >50% |
| `KPI-10` | TPS p95 | ≥19 | 부하 60분 | <17 |
| `KPI-11` | CHAOS HOLD 비율 | <5% | 프로필별 20회 | >15% |
| `KPI-12` | 중복 보상·완료 | 0 | 전 회차 | 1건 즉시 차단 |

UNKNOWN의 목표 범위는 플레이어에게 공개하지 않지만 운영 원장에는 Easy와 같은 방식으로 집계한다.

## 5. 실패 원인 분류

| 축 | 코드 예 |
|---|---|
| 판독 | `TELEGRAPH_MISREAD`, `HUD_OCCLUDED`, `COLOR_ONLY` |
| 전투 | `DAMAGE_CHECK`, `BREAK_MISSED`, `STATUS_CHAIN`, `AP_STARVED` |
| 협동 | `REVIVE_FAILED`, `POSITION_SPLIT`, `INTERRUPT_ROLE_MISSING` |
| 성장 | `ILVL_LOW`, `AUGMENT_TAG_GAP`, `RESEARCH_MISSING` |
| 경제 | `CRITICAL_RESOURCE_LOW`, `REPAIR_DEBT`, `OPTIONAL_SPEND_OVER` |
| 시설 | `POWER_LOSS`, `FACILITY_DAMAGED`, `QUEUE_BLOCKED` |
| 성능 | `TPS_HOLD`, `ENTITY_CAP`, `IO_STALL` |
| 운영 | `ADMIN_STOP`, `DATA_CORRUPT`, `PLUGIN_ERROR` |

운영·데이터 손상 회차는 난이도 실패율 분모에서 제외하지만 별도 안정성 지표에는 포함한다.

## 6. 테스트 단계

| 단계 | 범위 | 통과 조건 |
|---|---|---|
| L0 정적 | 스키마·참조·산술 | 오류 0, 모든 ID 해석 |
| L1 단위 | 공식·상태 전이·멱등성 | 결정론적 벡터 100% |
| L2 헤드리스 | 가상 플레이어 1~4, 50 Day 가속 | 소프트락·중복 0 |
| L3 부하 | 4인+CHAOS 최고 프로필 60분 | TPS p95 19+, HOLD <5% |
| L4 폐쇄 플레이 | 내부 20회/난이도 핵심 조합 | 치명 버그 0, 목표 범위 추세 |
| L5 라이브 후보 | 최소 표본 표 충족 | KPI 경고 2개 이하, 치명 0 |

현재 G3 문서는 L0 정적 완료 상태다. L1~L5는 구현 뒤 수행하며 완료 전 `LIVE_LOCKED`로 표시하지 않는다.

## 7. 튜닝 규칙

한 리비전에서 한 KPI의 주요 축은 하나만 바꾼다.

1. 버그·중복·가독성
2. 패턴 동시성·안전창·이동거리
3. 자원 보장·복구 임계
4. AP·상태·브레이크 저항
5. HP·직접 피해

| 변경 폭 | 승인 | 배포 |
|---|---|---|
| 표시·입자만 | 콘텐츠 담당 1인 | 다음 패치 |
| 수치 ±5% 이내 | 기획+개발 | 새 DATA patch 리비전 |
| 수치 >5%, 드로우·게이트 | 기획+개발+QA | 폐쇄 플레이 재수행 |
| 스키마·저장·멱등성 | 전 담당 | major 리비전, 활성 회차 불변 |

## 8. 리비전·롤백

- 모든 튜닝은 `contentRevision`, `budgetPolicyRevision`, `changeSetId`를 가진다.
- 새 리비전은 `NEW_RUN_ONLY`가 기본이다. 활성 회차는 생성 당시 잠금 튜플을 유지한다.
- 중복 보상·저장 손상 위험이면 새 회차 생성을 차단하고 이전 승인 리비전으로 원자 롤백한다.
- 활성 회차를 구 리비전으로 강제 변환하지 않는다. 읽을 수 없는 경우 안전 중단하고 스냅샷 복구 절차를 따른다.
- 롤백 뒤에도 원시 텔레메트리와 실패 원인은 삭제하지 않는다.

```text
deploy candidate
→ validate manifest and schemas
→ atomically swap new-run revision pointer
→ observe canary runs
→ keep or swap pointer back
```

## 9. 대시보드 최소 화면

| 화면 | 필터 | 핵심 표시 |
|---|---|---|
| 회차 퍼널 | 리비전·난이도·인원 | Day 도달률·실패 원인 |
| 전투 | Day·보스·무기 | TTK·브레이크·다운·역할 지수 |
| 경제 | 세션·자원 그룹 | 유입·지출·하한·복구 발동 |
| 빌드 | 증강 티어·태그·무기 | 제시·선택·승률·재귀 차단 |
| 성능 | 프로필·TPS 단계 | 엔티티·투사체·HOLD·틱 시간 |
| 무결성 | 리비전 | 중복 키·참조 오류·복구 단계 |

## 10. 완료 기준

- 밸런스 판단에 필요한 사건·전투·경제·성능·무결성 필드가 정의된다.
- KPI마다 목표·표본·경고선이 있으며 정적 검증과 라이브 승인이 구분된다.
- 튜닝 순서와 승인 폭이 고정되고 활성 회차를 임의 변경하지 않는다.
- 치명 오류 시 새 회차 포인터를 원자 롤백하고 기존 회차·감사 로그를 보존한다.
