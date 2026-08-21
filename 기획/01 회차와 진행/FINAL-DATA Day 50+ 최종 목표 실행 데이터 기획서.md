# WildSurvival FINAL-DATA Day 50+ 최종 목표 실행 데이터 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `FINAL-DATA-001` |
| 상태 | `DATA_LOCKED` |
| 목표 ID | `FINAL-D50-FIRST-RECONSTRUCTION-SIGNAL` |
| 보스 ID | `FINAL-BOSS-WORLD-COLLAPSE-CORE` |
| 상위 기준 | `FINAL-001`, `CONTENT-DATA-D50-001`, `FACILITY-DATA-D50-001` |
| 연계 | `ENEMY-DATA-D50-001`, `EVENT-DATA-D50-001`, `STORY-DATA-S1-001` |
| 데이터 리비전 | `final-data-d50-r1` |
| 최종 수정일 | 2026-08-21 |

## 1. 시작 검증 데이터

```yaml
activation-requirements:
  minimum-day: 50
  cleared-bosses: [BOSS-001, BOSS-002, BOSS-003, BOSS-004]
  bound-parts: [A, B, C, D]
  discoveries: [C27, C28-A, C28-B, C28-C, C28-D, C29]
  facilities:
    FAC-R01: READY
    FAC-R02: READY
    FAC-R03: READY
    FAC-R04: READY
    FAC-R05: {state: CALIBRATED, count: 3}
    FAC-R06: READY
  unique-input: WSR-FINAL_SIGNAL_KEY
  forbidden-active: [BOSS, SIEGE, BLOCKING_TRANSACTION]
```

- `C30`, 세계 오염 수치, 특정 무기·증강, 정착 정화기, 전원 Lv50은 시작 조건이 아니다.
- Day 49 이하는 아이템·GUI·명령·일반 관리자 권한 어디서 호출해도 `FINAL_DAY_LOCK`으로 거부한다.
- 전장 Manifest 저장 전 키를 소비하지 않고, Stage 1 오브젝트 생성 성공 뒤 소비를 확정한다.

## 2. 상태 전이·복구 목표

| 상태 | 진입 이벤트 | 저장 체크포인트 | 실패 복귀 |
|---|---|---|---|
| `LOCKED` | 조건 미충족 | 누락 범주 | 해당 없음 |
| `AVAILABLE` | 모든 조건 충족 | 준비 해시 | Day 계속 |
| `ACTIVATING` | 과반 투표+전장 후보 | 투표·Manifest·키 예약 | 60초/오류 시 AVAILABLE, 키 반환 |
| `ACTIVE_STAGE_1` | 오브젝트 생성·키 소비 | 말뚝·출력·웨이브 | 자발 중단 규칙 또는 재개 |
| `ACTIVE_STAGE_2` | 말뚝3+출력100 | 보스 전체 상태 | 재개, 자동 초기화 금지 |
| `ACTIVE_STAGE_3` | `CORE_SUBDUED` | 정화 초·체크포인트·웨이브 | 보스 부활 없이 재개 |
| `RESOLVING` | 180초+확정 충족 | 완료 TX 단계 | 다음 미완 단계 재시도 |
| `COMPLETED` | 완료 TX 커밋 | 읽기 전용 결과 | 재개 불가 |

## 3. 전장 Manifest

| 필드 | 조건 |
|---|---|
| 중심 | FAC-R06에서 18~36m, 본체와 스폰 판정 12m 이격 |
| 전투/복구 반경 | 50/70m |
| 말뚝 | 3개, 중심 14~24m, 서로 18m 이상 |
| 이동 경로 | 중심 순환 2, 시설 접근 2 |
| 엄폐 | 3~6, 부족 시 임시 3 |
| 고저차 | 핵심 4m 이하, 전체 16m 이하 |
| 액체 | 25% 이하, 용암 5% 이하 |

후보 24개를 틱당 2청크씩 검사한다. FAC-R06 인접 후보가 없으면 반경 64m 대체 전장과 임시 중계선을 만들며, 그것도 실패하면 자원 소비 없이 활성화를 거부한다.

## 4. Stage 1 실행 데이터

### 4.1 목표

| 오브젝트 | 수량 | 최대 진행 | 행동 | 방해 |
|---|---:|---:|---|---|
| `F50-STAKE` | 3 | 100/개 | 3초 상호작용 +25, 주변 주요 적 제거 +15, 브레이크 성공 +10 | 피격 시 채널 취소, 진행 최소10 보존 |
| `FAC-R06-OUTPUT` | 1 | 100 | 유효 채널 +2/초 | 침입체 채널 -20 |

- 한 명이 말뚝을 순차 처리할 수 있다. 말뚝 100은 잠기고 감소하지 않는다.
- 세 말뚝과 출력 100 달성 후 5초 안전 전환으로 Stage 2를 시작한다.

### 4.2 웨이브

| 인원 | 잠긴 총예산 | 웨이브 수 | 웨이브 예산 | 활성 상한 |
|---:|---:|---:|---|---:|
| 1 | 60 | 3 | 20/20/20 | 8 |
| 2 | 90 | 3 | 30/30/30 | 10 |
| 3 | 117 | 4 | 29/29/29/30 | 12 |
| 4 | 144 | 4 | 36/36/36/36 | 12 |

| 웨이브 | 프로필 | 필수 역할 |
|---:|---|---|
| 1 | `FINAL-ST1-PURSUIT` | 파티 추적 1, CHASER |
| 2 | `FINAL-ST1-STAKES` | `EN-F50-A01`, SUPPORT 최대1 |
| 3 | `FINAL-ST1-OUTPUT` | `EN-F50-A02`, SIEGE 최대1 |
| 4 | `FINAL-ST1-MIXED` | 3·4인만, 정예 최대1 |

적은 EXP·자원·장비·표본을 만들지 않고 목표 기여만 기록한다.

## 5. Stage 2 보스 데이터

### 5.1 프로필

| 필드 | 2인 Easy |
|---|---:|
| 표현 | Ravager 이동 코어 + 다중 Display 외피, 바닐라 보스 AI 없음 |
| HP/DEF/PEN | 1,250,000 / 240 / 50 |
| RES/TENACITY/STAGGER_RES | 135 / 70 / 75 |
| 브레이크 | 45,000 |
| 이동속도 | 플레이어 질주의 86% |

| 인원 | HP | 브레이크 | 소환체 | 동시 표적 |
|---:|---:|---:|---:|---:|
| 1 | 900,000 | 33,750 | 2 | 1 |
| 2 | 1,250,000 | 45,000 | 3 | 1 |
| 3 | 1,650,000 | 56,250 | 4 | 2 |
| 4 | 2,000,000 | 67,500 | 4 | 2 |

### 5.2 페이즈

| 페이즈 ID | HP | 패턴 풀 | 최소 반복 간격 |
|---|---|---|---:|
| `F50-P1-COLLAPSE-PURSUIT` | 100~72% | CUT, STATUS_QUADRANT, TRACK_LINE, ECHO_SUMMON | 같은 ID 2회 연속 금지 |
| `F50-P2-CORRUPTION-GRAFT` | 72~38% | MUTATION_ROTATE, PURIFY_BACKFLOW, FACILITY_JAM, SYNAPSE_CORE | 같은 축 12초 |
| `F50-P3-RESONANCE-FRACTURE` | 38~0% | FRACTURE_CHANNEL, LOCKED_RING, THREE_CORES, FINAL_COLLAPSE | 최종 패턴 1회 |

페이즈 전환은 4초 무공격, 현재 브레이크 0, 누적 내성 유지다.

### 5.3 패턴

| ID | 태그 | 전조/쿨다운 | 실행 | 대응·중단 요구 |
|---|---|---|---|---|
| `F50-COLLAPSE_CUT` | DODGE/GUARD | 0.9초/7초 | 두 부채 320, 교차 60% | 회피·방어·패링·후방 |
| `F50-STATUS_QUADRANT` | POSITIONAL/STATUS | 1.5초/12초 | 4구역 중2, 300+공개 상태 | 안전구역·상태 대응·브레이크3,200 |
| `F50-TRACK_LINE` | PARRYABLE | 1.3초/10초 | 대상/시설 반대 선 340 | 외곽 유도·반대편·패링 |
| `F50-ECHO_SUMMON` | SUMMON | 1.2초/14초 | `EN-F50-A03` 2~4 | 지점 선점·광역·본체 브레이크 |
| `F50-MUTATION_ROTATE` | MUTATION | 1.6초/16초 | 서로 다른 축 변이2, 14초 | HUD·축 전환·정화·브레이크 |
| `F50-PURIFY_BACKFLOW` | PARTIAL | 1.5초/14초 | 링 360, 출력-10 | 위치·말뚝 차폐·브레이크3,800 |
| `F50-FACILITY_JAM` | INTERRUPTIBLE | 1.8초/18초 | 7초 뒤 출력 정지8초 | 선 절단·브레이크·소환체 제거 |
| `F50-SYNAPSE_CORE` | SCRIPTED | 1.4초/16초 | 두 대상/1인 핵, 실패320+상태2 | 6~14m 유지, HP3,500/브레이크1,800 |
| `F50-FRACTURE_CHANNEL` | INTERRUPTIBLE | 8초/24초 | 실패420, 출력-20 | 브레이크6,000·말뚝3·상태 변환 |
| `F50-LOCKED_RING` | UNBREAKABLE | 1.4초/12초 | 3회×330, 후속65% | 안전 링·보호·게이지 누적 |
| `F50-THREE_CORES` | SCRIPTED | 8초/22초 | 핵3, 실패400 | 합계 HP12,000/브레이크6,000 |
| `F50-FINAL_COLLAPSE` | PARTIAL | HP8% 10초 | 중단 시 마지막2파 취소, 1파 유지 | 브레이크7,500·이동·방어 |

- 세 핵은 1인이 순차 처리할 수 있다. 특정 무기·상태를 요구하지 않는다.
- DOT 합산은 최대 HP 초당 0.5%, SLOW 15%, ROOT/STUN/AIRBORNE은 브레이크 변환, SILENCE/DISARM 면역이다.
- 브레이크 성공은 5초 그로기·피해 +15%·최대 AP20%·R06 출력+10, 성공 뒤 게이지 +20%, 최대 +100%다.
- HP 0은 사망이 아니라 `CORE_SUBDUED`; 모든 공격·소환·투사체를 정리하고 5초 후 Stage 3로 간다.

## 6. Stage 3 실행 데이터

```text
effectiveOutput = 40 + activeStakes×20 + min(playerChannels,2)×10 - activeJammers×20
```

| 출력 | 정화 초 처리 |
|---:|---|
| 70+ | 실제 경과 틱 누적 |
| 40~69 | 정지 |
| 0~39 | 초당 0.5초 후퇴, 0/60/120/180 체크포인트 아래로 불가 |

| 구간 | 프로필 | 2/3/4인 총예산 | 핵심 역할 |
|---|---|---:|---|
| 0~60초 | `FINAL-ST3-STATUS` | 30/39/48 | 상태·이동, 방해자0~1 |
| 60~120초 | `FINAL-ST3-CORRUPTION` | 36/47/58 | 출력 방해자1, 지원1 |
| 120~180초 | `FINAL-ST3-SIEGE` | 42/55/67 | 공성1, 정예1 |

- 동시 일반10, 정예1, 지원1, 공성1, 강한 전조3, 지속영역4를 넘지 않는다.
- 1인은 말뚝 3개 기본 출력100으로 채널 없이 진행 가능하다.
- 적·말뚝·정화 행동은 보상·EXP·증강 발동을 만들지 않는다.

## 7. 마지막 협동 확정

| 자격 | 요구 |
|---|---|
| 온라인 ALIVE | 45초 안 R06 2초 상호작용 1회 |
| DOWNED | 구조 뒤 상호작용 또는 다른 생존자의 구조 대리 기록 |
| DEAD | 요구 제외, 협력 기록은 결과에 보존 |
| 접속 종료 | 90초 유예 후 요구 인원 재계산 |
| 최종 1인 | 그 한 명의 상호작용으로 충족 |

45초 실패 시 정화 120초 체크포인트로 돌아가며 보스는 부활하지 않는다.

## 8. 완료 트랜잭션

| 단계 ID | 쓰기 | 멱등키 |
|---|---|---|
| `F50-TX-01` | 사전조건: CORE_SUBDUED·180초·확정 수 | `completionId:validate` |
| `F50-TX-02` | C30 `COMPLETE` | `completionId:discovery` |
| `F50-TX-03` | `finalObjectiveState=RESOLVING` | `completionId:objective` |
| `F50-TX-04` | `worldStabilization=FIRST_SIGNAL_SENT` | `completionId:world` |
| `F50-TX-05` | 회차 `COMPLETED`, 통계 스냅샷 | `completionId:run` |
| `F50-TX-06` | Story·결과 책 소비 이벤트 | `completionId:presentation` |

- 01~05 중 실패하면 `RESOLVING`에서 다음 미완 단계부터 재개하고 성공 연출을 하지 않는다.
- 06 실패는 완료를 롤백하지 않고 Story 조정기가 재생성한다.
- 완료 뒤 회차는 읽기 전용이며 Day·전투·아이템 변경 명령을 거부한다.

## 9. 중단·재시작

| 상황 | 결과 |
|---|---|
| Stage1 첫 자발 중단 | 말뚝 폐기, 일반 손실 유지, 키 가치90% 복구, 10분 대기 |
| 두 번째 이후 자발 중단 | 키 미반환, 부품 A~D 유지 |
| 전원 접속 종료 | 상태·시간 정지, 스냅샷 재개 |
| 전장 이탈 | 안전 위치 복구, HP·브레이크 유지 |
| 전원 완전 사망 | 회차 FAILED, 무환불 |
| 스냅샷 불일치 | ADMIN_RECOVERY_REQUIRED, 자동 완료 금지 |

## 10. 저장 예시

```yaml
final-snapshot:
  revision: final-data-d50-r1
  objective-id: FINAL-D50-FIRST-RECONSTRUCTION-SIGNAL
  state: ACTIVE_STAGE_3
  activation-day: 53
  arena-manifest-id: "final-arena:run-01"
  combat-scale-size: 3
  boss: {state: CORE_SUBDUED, hp: 0, break-stacks: 4}
  stakes: [100, 100, 100]
  facility-output: 80
  purification-seconds: 126.4
  purification-checkpoint: 120
  confirmations: []
  rng-index: 181
  completion-steps: []
```

## 11. 테스트·완료 기준

- `T-FINAL-001`: Day49 이하 모든 진입 경로 차단, C30 비선행
- `T-FINAL-002`: 전장 후보·Manifest·키 예약·소비 원자 순서
- `T-FINAL-003`: 1~4인 Stage1 예산·말뚝 순차 수행
- `T-FINAL-004`: Stage2 HP·브레이크·패턴·전조·금지 조합
- `T-FINAL-005`: HP0이 CORE_SUBDUED이며 완료가 아님
- `T-FINAL-006`: Stage3 출력·체크포인트·적 상한·1인 유지
- `T-FINAL-007`: 생존·빈사·사망·접속 종료 마지막 확정
- `T-FINAL-008`: 완료 6단계 멱등성·Story 실패 독립
- `T-FINAL-009`: 재시작·이탈·중단·전원 사망 복구 경계
- `T-FINAL-010`: 전체 15~22분, Stage2 9~13분, 첫 브레이크125~165초 목표

Day 50+ 시작부터 읽기 전용 성공 회차까지 모든 ID·상태·수치·실패·복구·완료 TX가 직렬화 가능하면 완료다.
