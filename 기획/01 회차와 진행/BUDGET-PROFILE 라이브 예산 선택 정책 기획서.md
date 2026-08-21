# WildSurvival 라이브 예산 선택 정책 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `BUDGET-PROFILE-001` |
| 상태 | `VALIDATED_STATIC` |
| 권위 | `BUDGET-001`의 허용 범위 안에서 실제 라이브 배율을 고르는 단일 원장 |
| 상위 기준 | `BUDGET-001`, `DIFFICULTY-001`, `CONTENT-DATA-D50-001` |
| 정책 리비전 | `budget-live-r2` |
| 기준 | Easy STANDARD DATA 값 = `1.00` |
| 최종 수정일 | 2026-08-21 |

## 1. 결정 사항

- Story·Easy STANDARD는 모든 도메인 `AUTHORED 1.00`으로 고정한다.
- Normal·Hard·UNKNOWN STANDARD는 Day 시작 시 결정론적 시드로 프로필 하나를 잠근다.
- CHAOS는 미리 승인된 프로필만 사용한다. 도메인별 독립 난수로 극단값을 우연히 겹치게 하지 않는다.
- 필수 진행 자원은 일반 자원 배율과 분리한 `CRITICAL_PATH_SUPPLY`로 관리하며 STANDARD 최저 `0.75`, CHAOS 최저 `0.50`을 보장한다.
- 확정된 프로필·배율·정수 예산은 Day 스냅샷에 먼저 커밋하고 재접속·재시작·리로드로 다시 뽑지 않는다.

## 2. 프로필 필드

| 필드 | BUDGET 도메인 | 단위 | 반올림 |
|---|---|---:|---|
| `progressExp` | `PROGRESS_EXP` | 배율 | 총합에 곱한 뒤 내림 |
| `activityExp` | `WILD_ACTIVITY_EXP` | 배율 | 총합에 곱한 뒤 최대 나머지법 |
| `commonResource` | `COMMON_RESOURCE` | 배율 | 자원 그룹별 곱한 뒤 최대 나머지법 |
| `criticalPathSupply` | 필수 시설·호출·Final 재료 | 배율 | 그룹별 올림, 고유품 무배율 |
| `personalSupply` | `PERSONAL_SUPPLY_VALUE` | 배율 | 가치 예산만 배율, 등급표 무배율 |
| `encounterThreat` | `ENCOUNTER_THREAT` | 배율 | 비용 예산 내림, 잔여 이월 |
| `eliteMutation` | `ELITE_MUTATION` | 배율 | 슬롯이 아니라 발생 비용 배율 |
| `environmentPressure` | `ENVIRONMENT_PRESSURE` | 배율 | 강도 하드캡 뒤 횟수·구역으로 분할 |
| `facilityPressure` | `FACILITY_PRESSURE` | 배율 | 동시 공성 상한 뒤 단계로 분할 |
| `bossPattern` | `BOSS_PATTERN` | 배율 | 패턴 토큰으로 분할, HP·피해 무관 |

`criticalPathSupply`에는 재건 부품 A~D 같은 고유 불리언을 넣지 않는다. 그것들은 1회 결과다. 대신 해당 부품을 얻는 사건의 재도전 재료, 시설 수리재, 호출 전 소모재의 가치만 포함한다.

## 3. STANDARD 승인 프로필

표의 값 순서는 `activity/common/critical/personal/threat/mutation/environment/facility/boss`다. `progressExp`는 모든 STANDARD 프로필에서 `1.00`이다.

| ID | 배율 묶음 | 성격 |
|---|---|---|
| `STD-BALANCED` | `1.00/1.00/1.00/1.00/1.00/1.00/1.00/1.00/1.00` | 기준 |
| `STD-LEAN` | `0.75/0.85/0.75/0.85/0.90/0.90/1.00/0.90/1.00` | 낮은 활동 보상, 낮은 공세 |
| `STD-FRONTIER` | `1.25/1.15/1.00/1.10/1.25/1.15/1.20/1.10/1.10` | 고수익·고압 |
| `STD-TACTICAL` | `1.00/0.90/0.90/1.00/1.15/1.35/1.10/1.15/1.25` | 변이·보스 패턴 중심 |
| `STD-RECOVERY` | `0.75/1.25/1.25/1.10/0.80/0.80/0.85/0.80/0.90` | 최근 소프트락 위험 복구 |

난이도별 후보와 가중치는 다음과 같다.

| 난이도 | BALANCED | LEAN | FRONTIER | TACTICAL | RECOVERY |
|---|---:|---:|---:|---:|---:|
| Story·Easy | 100% | 0% | 0% | 0% | 0% |
| Normal | 35% | 15% | 25% | 15% | 10% |
| Hard | 20% | 20% | 30% | 20% | 10% |
| UNKNOWN | 10% | 20% | 30% | 30% | 10% |

- Hard·UNKNOWN가 수학적으로 허용하는 활동 EXP `0.00`은 QA용 `ADMIN_QA` 값으로만 유지한다. 라이브 승인 프로필 최저는 `0.75`다.
- `STD-RECOVERY`는 Day 시작 소프트락 검사에서 필수 자원 충족률이 `115%` 미만이거나 시설 2개 이상이 `DAMAGED`일 때만 후보가 된다. 후보가 아니면 그 10%를 BALANCED에 합산한다.
- 같은 프로필은 3일 연속 선택하지 않는다. 세 번째 Day에는 그 가중치를 동일 난이도의 다른 후보에 비례 배분한다.

## 4. CHAOS 승인 프로필

| ID | 진행/활동 | 일반/필수/개인 | 위협/변이/환경/시설/보스 | 전달 의도 |
|---|---|---|---|---|
| `CH-SURGE` | `1.50/3.00` | `2.00/1.00/2.00` | `3.00/2.00/2.00/2.00/2.00` | 고보상 다단 공세 |
| `CH-FAMINE` | `1.00/1.00` | `0.50/0.75/0.75` | `2.00/2.00/3.00/2.00/1.50` | 선택 소비 압박, 필수 경로 유지 |
| `CH-HUNT` | `1.00/2.00` | `1.25/1.00/1.50` | `5.00/5.00/2.00/3.00/3.00` | 적·변이 극대화 |
| `CH-STORM` | `1.00/1.50` | `1.50/1.00/1.25` | `2.00/3.00/8.00/5.00/2.00` | 환경·시설 방어 |
| `CH-BOSS-LAB` | `1.00/1.00` | `1.00/1.00/1.00` | `1.50/2.00/1.50/1.50/10.00` | 순차 패턴 극단 시험 |
| `CH-OVERFLOW` | `3.00/10.00` | `10.00/1.50/5.00` | `10.00/8.00/5.00/5.00/6.00` | 최고 부하, 5% 희귀 후보 |

- 일반 CHAOS 가중치는 SURGE 25%, FAMINE 20%, HUNT 20%, STORM 20%, BOSS-LAB 10%, OVERFLOW 5%다.
- `CH-BOSS-LAB`은 보스 Day에만, `CH-OVERFLOW`는 파티 전원 사전 동의와 지난 3일간 TPS p95 `19.0+`일 때만 후보가 된다.
- 조건을 만족하지 않은 후보의 가중치는 SURGE에 합산한다.
- CHAOS도 활성 개체·투사체·영역·상태·고유 보상 상한을 바꾸지 않는다.

## 5. 상관 제약과 소프트락 검사

Day 스냅샷을 승인하려면 아래를 모두 통과해야 한다.

```text
criticalExpected = baseCriticalSupply × criticalPathSupply
criticalNeed = remainingMandatoryCost × dayWindowShare
criticalExpected + guaranteedRecovery >= criticalNeed
```

| 검사 ID | 거부·대체 조건 |
|---|---|
| `BP-C01` | `criticalPathSupply < 0.50` 거부 |
| `BP-C02` | `commonResource < 0.75 && encounterThreat > 3.00`이면 `personalSupply >= 1.50` 필요 |
| `BP-C03` | `environmentPressure > 5.00`이면 정화 대체 사건 1개 예약 |
| `BP-C04` | `facilityPressure > 3.00`이면 같은 Day 영구 파괴·철거 잠금 |
| `BP-C05` | `bossPattern > 6.00`이면 보스 HP·피해 난이도 배율 외 추가 증가 금지 |
| `BP-C06` | 남은 Day로 A~D·C01~C30·최종 시설 확보 불가능하면 `STD-RECOVERY` 또는 CHAOS 구조 사건 강제 |
| `BP-C07` | 활성 개체 예상 p95가 GAME-003 상한을 넘으면 단계 수를 늘리고 동시량을 줄임 |

## 6. 선택·잠금 알고리즘

```text
candidateSet = profiles allowed by difficulty, mode, day, TPS and softlock state
seed = SHA-256(runId + day + policyRevision)
profileId = weightedDeterministicDraw(candidateSet, seed)
validate correlations
for each domain:
  lockedBudget = floor(baseBudget × profile.multiplier)
commit BudgetSnapshot and DayStarted in one transaction
```

- 검증 실패 시 같은 난수를 다시 굴리지 않고 후보 ID 정렬순 다음 프로필을 검사한다.
- 모든 후보가 실패하면 `STD-RECOVERY`; CHAOS에서는 `CH-SURGE`의 위협을 `2.00`으로 낮춘 `CH-SAFE-FALLBACK`을 사용한다.
- UNKNOWN은 서버 원장에 전체 값을 저장하되 일반 UI에는 프로필명·배율을 `UNKNOWN`으로 직렬화한다.

## 7. 저장·감사 계약

```yaml
budget-snapshot:
  policy-revision: budget-live-r2
  day: 37
  profile-id: CH-HUNT
  source: SEEDED
  seed-hash: "sha256:..."
  selection-attempt: 1
  domain-multipliers: { activityExp: 2.0, commonResource: 1.25, criticalPathSupply: 1.0, encounterThreat: 5.0 }
  locked-budgets: { activityExp: 5240, commonResource: 84, encounterThreat: 275 }
  residual-ledger-id: "BL:run:37"
```

저장값은 프로필 ID만으로 재계산하지 않는다. 정책 리비전이 바뀌어도 활성 회차의 실제 배율과 확정 예산은 스냅샷 값이 권위다.

## 8. 정적 검증 결과

| 검증 | 결과 |
|---|---|
| 모든 STANDARD 라이브 배율이 난이도 허용 범위 안인가 | 통과 |
| 모든 CHAOS 배율이 `0.00~10.00` 안인가 | 통과 |
| 필수 진행 공급 최저가 0보다 큰가 | 통과, `0.50` |
| 10배 위협과 보스 패턴이 동시 안전 상한을 바꾸는가 | 아님, 잔여 원장·단계 분할 |
| Story·Easy STANDARD가 1.00 외 값을 고르는가 | 아님 |
| Day 50 이전 최종 목표를 푸는 프로필이 있는가 | 없음 |

실제 실패율·세션 시간·TPS 목표는 `QA-BALANCE-001`의 플레이테스트 게이트를 추가 통과해야 한다.

## 9. 완료 기준

- 모든 라이브 조합이 허용된 프로필과 결정론적 선택 규칙을 가진다.
- 낮은 공급·높은 압박 조합도 필수 진행·복구 경로를 0으로 만들지 않는다.
- CHAOS 10배는 동시량이 아니라 잔여 예산·웨이브·단계로 전달된다.
- 스냅샷·UI 비공개·재시작·감사 계약이 구현 필드로 고정된다.
