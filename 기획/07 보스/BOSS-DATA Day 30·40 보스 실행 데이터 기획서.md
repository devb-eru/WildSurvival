# WildSurvival BOSS-DATA Day 30·40 보스 실행 데이터 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `BOSS-DATA-D50-001` |
| 상태 | `DATA_LOCKED` |
| 상위 기준 | `BOSS-003`, `BOSS-004`, `BREAK-001~002`, `CONTENT-DATA-D50-001` |
| 연계 | `RESOURCE-DATA-D50-001`, `EQUIP-DATA-D50-001`, `STORY-DATA-S1-001` |
| 적용 범위 | Day 30 오염 섭식핵, Day 40 공진 파괴자 런타임 스냅샷·패턴·보상 |
| 수치 기준 | 2인 Easy STANDARD, 인원 배율은 `GAME-003` |
| 데이터 리비전 | `boss-data-d30-d40-r1` |
| 최종 수정일 | 2026-08-21 |

## 1. 공통 상태 머신

```text
LOCKED → CALL_READY → ARENA_VALIDATING → SPAWNING → ACTIVE
→ SUBDUED → REWARDING → CLEARED
```

오류는 호출 소비 전 `CALL_READY`, 소비 후 복구 가능하면 `SPAWNING`, 자동 복구 불가면 `ADMIN_RECOVERY_REQUIRED`에 둔다. `CLEARED` 뒤 동일 보스 호출·보상·파티 증강을 다시 실행할 수 없다.

### 1.1 공통 원자 순서

1. Day·발견·부품·호출 세트·다른 활성 콘텐츠를 검사한다.
2. 전장 후보와 원래 블록 `ArenaManifest`를 저장한다.
3. 호출 입력을 예약하고 `ARENA_VALIDATING`을 커밋한다.
4. 보스·오브젝트 생성 성공 뒤 호출을 소비 확정하고 `ACTIVE`를 저장한다.
5. 사망/제압 시 공격·소환·투사체를 정지하고 결과를 커밋한다.
6. 공용 부품·재료→EXP→개인 증강→장비 후보→파티 증강 순으로 각 멱등 단계를 처리한다.
7. 모든 정산 단계가 완료돼야 `CLEARED`와 다음 Day를 허용한다.

## 2. 인원 배율

| 인원 | HP | 직접 피해 | 브레이크 | 주요 표적 | 소환체 |
|---:|---:|---:|---:|---:|---:|
| 1인 예외 | 0.72 | 0.90 | 0.75 | 1 | 보스별 1단계 감소 |
| 2인 | 1.00 | 1.00 | 1.00 | 1 | 기본 |
| 3인 | 1.32 | 1.06 | 1.25 | 2 | +1, 보스 상한 |
| 4인 | 1.60 | 1.12 | 1.50 | 2 | +1~2, 보스 상한 |

전투 시작 스냅샷을 끝까지 유지한다. 난이도와 CHAOS 프로필은 G3 원장 순서로 적용하되 즉사 금지·전조·개체·영역·CC·고유 보상 상한을 바꾸지 않는다.

## 3. Day 30 — 오염 섭식핵

### 3.1 시작·전장·프로필

| 필드 | 값 |
|---|---|
| 템플릿 | `BOSS-D30-CORRUPTION-HEART` |
| 시작 | Day 30, C20, D10·D20 완료, 부품 A·B, `WSRCP-D30-CALL` |
| 전장 | 반경 40m, 복구 58m, 균열 3, 웅덩이 2~3, 엄폐 2~4 |
| 표현 | Ravager 이동 코어 + Slime 비충돌 표현 + Display 외피 |
| 2인 HP/DEF/PEN | 360,000 / 150 / 28 |
| RES/TENACITY/STAGGER_RES | 80 / 42 / 45 |
| 브레이크 | 20,000, 그로기 5초, 성공 뒤 +25%, 최대 +100% |
| 목표 | 10~14분, 3~5 브레이크 |

| 인원 | HP | 브레이크 | 변이 동시 | 소환체 상한 |
|---:|---:|---:|---:|---:|
| 1 | 259,200 | 15,000 | 1 | 2 |
| 2 | 360,000 | 20,000 | 2 | 3 |
| 3 | 475,200 | 25,000 | 2 | 4 |
| 4 | 576,000 | 30,000 | 2 | 4 |

### 3.2 전투 자원

| ID | 범위 | 증가 | 감소 | 임계 결과 |
|---|---:|---|---|---|
| `ARENA_CONTAMINATION` | 0~100 | 웅덩이 +2/초, 섭식 +15 | 균열 -12, 정화 -2/초 | 100: 웅덩이 +1, 85로 복귀 |
| `PLAYER_CORRUPTION_PRESSURE` | 0~5 | 지정 피격 +1 | 정화 3초·브레이크 성공 -1 | 5: 다음 상태 +20%, 뒤 2 |
| `PURIFY_CHARGE` | 0~100 | 정화 출력·유효 행동 | 방해 완료 -20 | 100: 외피 취약 8초, 0 |

### 3.3 페이즈·패턴 데이터

| 페이즈 | HP | 고정 규칙 | 사용 패턴 |
|---|---|---|---|
| `P1_FEEDING` | 100~70% | `MUT_MENDING_FOG` | CUT, FEED_LINE, RIFT_SPORE, HIDE_SWEEP |
| `P2_MUTATION` | 70~35% | 안전한 변이 후보 2개 | MUTATION_SHIFT, SPLIT_VOLLEY, PURIFY_JAM, CLING_CHASE |
| `P3_BACKFLOW` | 35~0% | 축소 `MUT_PURIFIER_JAMMER` | PURIFY_BACKFLOW, TRIPLE_RIFT, CORE_SEPARATION, FINAL_FEED |

| ID | 전조/쿨다운 | 실행 | 중단·대응 |
|---|---|---|---|
| `B30-CORRUPT_CUT` | 0.85초/7초 | 부채꼴 180, 압력 +1 | 회피·방어·패링·거리 |
| `B30-FEED_LINE` | 1.20초/12초 | 5초 뒤 오염 +15, HP 2% 회복 | 선 절단·브레이크 1,800·정화; 회복 총 8% |
| `B30-RIFT_SPORE` | 1.10초/10초 | 무보상 소환체 2 | 지점 선점·광역·본체 브레이크 |
| `B30-HIDE_SWEEP` | 0.75초/6초 | 160도 210, 가드 충격 24 | 회피·HEAVY_GUARD·패링·후방 |
| `B30-MUTATION_SHIFT` | 1.40초/12초 | 공개 변이 1개 12초 | 축 전환·브레이크 |
| `B30-SPLIT_VOLLEY` | 1.0초/8초 | 4갈래 105, 최대 2회 | 엄폐·회피·방어·제거 |
| `B30-PURIFY_JAM` | 1.5초/14초 | 6초 뒤 충전 -20 | 공격·브레이크 2,200·재배치 |
| `B30-CLING_CHASE` | 1.1초/10초 | 190, 흔적 4초 | 표적 교대·외곽·회피 |
| `B30-PURIFY_BACKFLOW` | 1.5초/12초 | 안/밖 교대 230 | 위치·방어·브레이크 2,600 |
| `B30-TRIPLE_RIFT` | 1.2초/16초 | 균열 3, 각 오염 +12 | 순차 안정화·집중·분산 정화 |
| `B30-CORE_SEPARATION` | 7초/20초 | 핵 3, 실패 280+압력 | 합계 HP 8,000 또는 브레이크 4,000 |
| `B30-FINAL_FEED` | HP10% 1회 | 공개 4구역, 260 | 안전 구역; 충전80+ 전조 +0.5초 |

- P2 변이는 피해·이동·상태·브레이크 중 서로 다른 축 2개다. 1인은 CLING+BREAK_SHELL을 함께 쓰지 않는다.
- 정화 취약은 피해 +12%, 브레이크 +15%이고 전역 취약 상한을 따른다.

### 3.4 보상 단계

| 단계 ID | 내용 | 수량 |
|---|---|---|
| `B30-RW-PART-C` | 재건 정화 부품 C | 1 고정 |
| `B30-RW-MATERIAL` | 정제 변이/정화 매질 | 2인 8/6, 3인 10/8, 4인 12/10 |
| `B30-RW-EXP` | 진행 EXP | 2,200/인 + Day·C20 2,350 |
| `B30-RW-PERSONAL-AUG` | Lv30 잠금·후보 | 등록 생존 자격자별 |
| `B30-RW-ABYSSAL` | `EQD50-B30-*` 후보 3→1 | 파티 최초 1개 |
| `B30-RW-PARTY-AUG` | 파티 증강 3회차 | 1회 |

## 4. Day 40 — 공진 파괴자

### 4.1 시작·전장·프로필

| 필드 | 값 |
|---|---|
| 템플릿 | `BOSS-D40-RESONANT-DEMOLISHER` |
| 시작 | Day 40, C26, D10~30 완료, 부품 A~C, `WSRCP-D40-CALL` |
| 전장 | 반경 44m, 복구 62m, 중단 말뚝 3, 엄폐 2~5 |
| 표현 | Iron Golem 이동 코어 + Ravager 충돌 외곽 + Display 장갑 |
| 2인 HP/DEF/PEN | 650,000 / 190 / 38 |
| RES/TENACITY/STAGGER_RES | 105 / 55 / 60 |
| 브레이크 | 30,000, 그로기 5초, 성공 뒤 +20%, 최대 +100% |
| 목표 | 11~15분, 4~6 브레이크 |

| 인원 | HP | 브레이크 | 동시 표적 | 보조핵 상한 |
|---:|---:|---:|---:|---:|
| 1 | 468,000 | 22,500 | 1 | 1 |
| 2 | 650,000 | 30,000 | 1 | 2 |
| 3 | 858,000 | 37,500 | 2 | 3 |
| 4 | 1,040,000 | 45,000 | 2 | 3 |

### 4.2 태그·감쇠

| 태그 | 결과 |
|---|---|
| `INTERRUPTIBLE` | 요구 브레이크 즉시 시전 취소 |
| `PARTIAL_INTERRUPT` | 본체 시전 취소, 이미 확정된 잔류 영역 유지 |
| `UNBREAKABLE_POSITIONAL` | 본체 게이지 누적, 현재 패턴은 위치 대응 |
| `SCRIPTED_CORE` | 핵 HP·브레이크 또는 명시 본체 기여로 처리 |

같은 패턴에 같은 수단 연속 기여는 100/70/40/20%다. `PARRY`, `WEAPON_BREAK`, `STATUS_CONVERT`, `ANCHOR_INTERACT` 중 다른 수단이 유효 기여하면 연속 카운트를 초기화한다.

### 4.3 페이즈·패턴 데이터

| 페이즈 | HP | 사용 패턴 |
|---|---|---|
| `P1_FRACTURE` | 100~75% | FRACTURE_SLAM, RESONANCE_CHANNEL, LOCK_RING, SHARD_FIRE |
| `P2_COUNTER` | 75~40% | PARRY_LEARN, HALF_COLLAPSE, GUARD_CORE, ALTERNATE_CHANNEL |
| `P3_COLLAPSE` | 40~0% | TRIPLE_DEMOLISH, RESONANCE_PRISON, CORE_OVERDRIVE, FINAL_BREAK |

| ID | 태그 | 전조/쿨다운 | 실행 | 요구·대응 |
|---|---|---|---|---|
| `B40-FRACTURE_SLAM` | UNBREAKABLE | 1.15초/8초 | 십자 270+균열 2초 | 안전 사분면·회피·방어·엄폐 |
| `B40-RESONANCE_CHANNEL` | INTERRUPTIBLE | 6초/14초 | 실패 320+보호막 4% | 브레이크 3,200 또는 말뚝 3회 |
| `B40-LOCK_RING` | UNBREAKABLE | 1.30초/10초 | 안/밖 250 | 공개 안전 링·이동·보호 |
| `B40-SHARD_FIRE` | INTERRUPTIBLE | 1.0초/8초 | 직선 5×120, 최대 2회 | 브레이크 1,800·엄폐·회피 |
| `B40-PARRY_LEARN` | INTERRUPTIBLE | 0.85초/8초 | 돌진 245, 수단 감쇠 | 패링·회피공격·상태·엄폐 |
| `B40-HALF_COLLAPSE` | PARTIAL | 5초/14초 | 중단 뒤 장판 2개 4초 | 브레이크 2,800 뒤 이동 |
| `B40-GUARD_CORE` | SCRIPTED | 1.4초/18초 | 핵 2~3, 각 피해감소 8% | 핵 HP3,000/브레이크1,400; 무시 가능 |
| `B40-ALTERNATE_CHANNEL` | INTERRUPTIBLE | 5초/12초 | 표시 축 브레이크 -25% | 다른 축·말뚝·상태 전환 |
| `B40-TRIPLE_DEMOLISH` | PARTIAL | 3×0.9초/14초 | 회당 220, 중단 뒤 균열1 | 첫/둘째 중단·연속 회피·방어 |
| `B40-RESONANCE_PRISON` | SCRIPTED | 1.4초/18초 | 핵 1인2/그외3, 6초 뒤300 | 핵 처리·본체 브레이크3,500 |
| `B40-CORE_OVERDRIVE` | INTERRUPTIBLE | 7초/35초 | 실패340+현재 게이지 -20% | 브레이크4,500·말뚝3·상태 |
| `B40-FINAL_BREAK` | UNBREAKABLE | HP10% 1회 | 4선 280, 후속65% | 안전선·방어·게이지 누적 |

### 4.4 보상 단계

| 단계 ID | 내용 | 수량 |
|---|---|---|
| `B40-RW-PART-D` | 재건 공명 부품 D | 1 고정 |
| `B40-RW-MATERIAL` | 패턴 잔재/고밀도 합금 | 2인10/7, 3인13/9, 4인16/11 |
| `B40-RW-EXP` | 진행 EXP | 4,000/인 + Day·C26 4,330 |
| `B40-RW-PERSONAL-AUG` | Lv40 잠금·후보 | 자격자별 |
| `B40-RW-ABYSSAL` | `EQD50-B40-*` 후보 3→1 | 파티 최초 1개 |
| `B40-RW-PARTY-AUG` | 네 번째·마지막 파티 증강 | 1회 |

## 5. 저장 계약

```yaml
boss-instance:
  template-id: BOSS-D40-RESONANT-DEMOLISHER
  revision: boss-data-d30-d40-r1
  state: ACTIVE
  combat-scale-size: 3
  difficulty: EASY
  hp-current: 602000
  break-current: 12100
  break-resistance-stacks: 2
  phase: P2_COUNTER
  pattern-id: B40-HALF_COLLAPSE
  pattern-ticks: 46
  method-decay: {PARRY: 2, WEAPON_BREAK: 0}
  anchors: [1, 0, 2]
  rng-index: 84
  reward-steps: []
  arena-manifest-id: "arena:run-01:d40"
```

- 5초 이하 투사체·입력 버퍼는 저장하지 않고 재시작 시 안전 제거한다.
- 현재 패턴은 재시작 후 취소하되 HP·브레이크·페이즈·내성·자원·RNG는 유지한다.
- 보상 단계는 완료 배열로 저장해 다음 미완 단계부터 재개한다.

## 6. 테스트·완료 기준

- `T-BD50-001`: Day·발견·부품·호출·활성 콘텐츠 잠금
- `T-BD50-002`: 1~4인 HP·브레이크·소환체·표적 값
- `T-BD50-003`: 전장 후보 실패 시 호출 미소비·무료 재배치
- `T-BD50-004`: D30 변이 금지 조합·세 전투 자원·3~5 브레이크
- `T-BD50-005`: D40 네 태그·수단 감쇠·4~6 브레이크
- `T-BD50-006`: 모든 패턴 전조·대응 2개·1인 순차 수행
- `T-BD50-007`: 보상 단계 순서와 부품·심연·증강 중복 방지
- `T-BD50-008`: 재시작·이탈·언로드 후 HP·게이지·RNG·보상 불변
- `T-BD50-009`: 소환체·핵 EXP·드롭·증강 발동 없음
- `T-BD50-010`: 리소스 팩 없이 태그·변이·정화·중단 판독

두 보스가 별도 디자인 판단 없이 스키마·패턴·전장·보상 데이터로 직렬화될 수 있으면 완료다.
