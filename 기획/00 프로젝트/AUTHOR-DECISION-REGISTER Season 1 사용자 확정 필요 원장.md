# WildSurvival Season 1 사용자 확정 필요 원장

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `AUTHOR-DECISION-REGISTER-001` |
| 상태 | `USER_LOCKED_WITH_DEFERRED_STORY_CONTENT` |
| 역할 | 게임 정체성·서사 저작처럼 구현자가 임의 확정할 수 없는 선택, 완성된 대안, 응답 형식과 안전 기본값을 관리 |
| 상위 기준 | `VISION-001`, `PRODUCTION-DATA-CLOSURE-001`, `STORY-001`, `DEATH-001` |
| 최종 수정일 | 2026-08-24 |

## 1. 게이트 규칙

- 수치·비용·증거처럼 기존 시스템 원칙에서 유도할 수 있는 항목은 `PRODUCTION-DATA-CLOSURE-001`이 잠근다.
- 하드코어의 영구 사망 강도와 최종 한국어 대사처럼 플레이 경험의 정체성을 바꾸는 항목은 사용자 확정 없이 잠그지 않는다.
- 사용자 확정 전에는 신규 부활 ID를 생성하거나 임시 Story 문구를 출시 본문으로 승격하지 않는다. 현재 `REVIVAL_ITEM`은 정책만 잠겼고 실제 목록 반영은 다음 기획 목표로 위임됐으며, Story 본문은 명시적으로 최종 단계까지 연기됐다.
- 슬롯 0 파손 같은 긴급 호환 수정과 데이터 감사는 계속할 수 있다. 선택 결과에 영향을 받는 생산 기능 구현은 해당 결정 뒤 시작한다.

### 1.1 사용자 확정 기록 — 2026-08-24

| ADR | 사용자 확정 | 적용 상태 |
|---|---|---|
| `ADR-001` | `REVIVAL_ITEM` | `USER_LOCKED_AND_PLANNED`; ITEM/RECIPE/DEATH 권위에 반영 |
| `ADR-002` | `CHAPTER_REVIEW` | 작성 방식만 `USER_LOCKED`; Story 초안·장별 검토·payload 작성은 전체 플레이 가능 판정 이후 최종 작업으로 연기 |

연기된 Story 작업은 개발 폴백을 출시 원문으로 승인한다는 뜻이 아니다. 플레이 가능 판정 전에는 Story 본문을 작성·보정·검토하지 않으며, 출시 판정 전 최종 단계에서 별도 목표로 진행한다.

## 2. `ADR-001` 완전 사망 뒤 부활 정책 — `USER_LOCKED: REVIVAL_ITEM`

### 2.1 선택 A — `REVIVAL_ITEM` 권장

| 필드 | 고정안 |
|---|---|
| 신규 ID | `WSI-CONS-REVIVAL_CORE` |
| codexIndex | `0118` 예약 |
| 최초 Day | 33 (`Day 31+` 범위 안에서 `RS-D33-INTERRUPT`와 실제 사용 가능일 일치) |
| 소유 | `PARTY_BOUND`, 스택 1 |
| 제작 | `FAC-S11 Lv4+`, `RS-D33-INTERRUPT` 완료 |
| 레시피 입력 | `WSR-INTERRUPT_CORE×1`, `WSR-BIO_MEDIUM×2`, `WSR-PURIFY_CATALYST×2`, `WSR-NEURAL_CIRCUIT×1` |
| 사용 | 안전 상태의 FAC-S11에서 생존자 1명이 10초 채널, 대상 유품 `remainsId` 선택 |
| 제한 | 대상 플레이어당 회차 1회, 파티 전체 전멸 뒤 사용 불가 |
| 복귀 | `ALIVE`, 부상 3, HP 25%, AP 0, 개인 오염 보존, 5초 비공격 보호 |
| 소비 TX | 대상·유품·회차 제한을 잠근 뒤 부활 상태·아이템 소비·유품 귀속 해제를 한 TX로 커밋 |
| 목록 영향 | 비장비 아이템 62, 레시피 316, 도감 335로 각각 +1 |

장점은 협동 회복 목표를 만들면서도 죽음을 무효화하지 않는다는 점이다. 단점은 현재 잠긴 카탈로그 수를 한 번 올리고 Day31 이전 완전 사망은 기다려야 한다는 점이다.

### 2.2 선택 B — `NO_REVIVAL`

완전 사망은 해당 회차에서 영구 관전이다. 유품 회수는 생존 파티원의 자산 회복일 뿐 죽은 플레이어를 되살리지 않는다. 목록 수는 유지되고 전멸 즉시 회차 실패다. 가장 하드코어하지만 장기 협동 세션에서 탈락자의 대기 시간이 길 수 있다.

### 2.3 선택 C — `FACILITY_ONLY`

| 필드 | 고정안 |
|---|---|
| 해금 | Day 41, `RS-D44-REBUILD-D`, FAC-R04 READY |
| 시설 작업 | `FAC-S11 Lv5`와 R04가 같은 96블록 네트워크, 30초 채널 |
| 비용 | `WSR-BIO_MEDIUM×4`, `WSR-PURIFY_MATRIX×1`, `WSR-POWER_MATRIX×1` |
| 제한·복귀 | 대상당 회차 1회, 전멸 불가, 선택 A와 같은 복귀 상태 |
| 목록 영향 | 신규 아이템·도감·레시피 없음, 시설 opcode 1개 추가 |

이 선택은 카탈로그를 늘리지 않지만 Day41 이전에는 `NO_REVIVAL`과 동일하다.

## 3. `ADR-002` Story 한국어 본문 작성 방식 — `METHOD_USER_LOCKED: CHAPTER_REVIEW`, `CONTENT_DEFERRED`

### 3.1 현재 확정·누락

| 항목 | 확정 | 아직 없는 출시 데이터 |
|---|---|---|
| 장면 | Scene ID·트리거·비트 73개 | payloadKey별 최종 한국어 화자/본문 73개 |
| 선택 기록 | Log ID·트리거 9개 | payloadKey별 최종 한국어 본문 9개 |
| 인물 | 윤서하·강도윤·이해원·ARK-0의 정체·관점·말투·대표 문장 | 각 장면에서 누가 몇 줄 말하는지 |
| 표현 | FULL/REDUCED 자산 차이·전투 중 2줄 상한·큐/재생 계약 | 자막 분절·표시시간·사운드 키 |
| 결말 | C30·첫 신호·응답 헤더 3개·S2 이관 사실 | 최종 확인/완료/에필로그 문장 |

현재 r2 장면은 73개 중 54개가 `payloadKey`만 있고 `payloadText`가 비어 있다. 나머지 19개도 연출 요약 또는 한 문장 폴백이며 전체 출시 대본이 아니다. 선택 기록 9개도 key만 있고 본문 파일이 없다.

### 3.2 작성 방식 선택

| 선택 | 진행 방식 | 검토 부담 |
|---|---|---|
| `ASSISTANT_DRAFT` | 기존 Story 비트·대표 문장만 근거로 82개 payload 초안을 작성하고 사용자가 최종 승인 | 한 번의 전체 톤·사실 검토 |
| `CHAPTER_REVIEW` 권장 | 프롤로그→1~5장→Final 순서로 장별 초안·승인을 반복 | 가장 안전하지만 승인 횟수 7회 이상 |
| `USER_SUPPLY` | 사용자가 82개 payload 또는 외부 대본을 제공, 구현자는 키 매핑·검증만 수행 | 창작 통제 최고, 입력 준비 필요 |

### 3.3 payload 완료 계약

각 payload는 `payloadKey`, `speakerId`, `lines[]`, `fallbackLines[]`, `displayTicks`, `soundKey`, `combatSafe`, `replayable`을 가진다.

- 한 줄은 기본 42자 이하, 전투 중 장면은 최대 2줄·총 70자 이하다.
- 진행 필수 정보는 색상만으로 구분하지 않고 명시 명사·숫자·방향을 포함한다.
- `FULL/REDUCED`는 시각 자산만 다르고 대사·사실·판정은 동일하다.
- Story가 보상·발견·보스·Final 성공을 승인하거나 롤백하지 않는다.
- 없는 음성·모델·NMS 연출을 전제로 쓰지 않는다. Adventure 컴포넌트, Title/ActionBar, Display, 바닐라 사운드 폴백으로 표현 가능해야 한다.
- payloadKey 82개 누락·중복 0, 장면 73·로그 9 매핑 1:1, 금지 스포일러·진행 우회 0을 L0에서 검증한다.

## 4. 사용자 확정과 변경 형식

현재 확정값은 다음과 같다.

```text
부활=ITEM, Story=CHAPTER_REVIEW, Story 작업시점=전체 플레이 가능 판정 이후 최종 단계
```

결정을 변경할 때만 `부활=ITEM|NONE|FACILITY, Story=ASSISTANT_DRAFT|CHAPTER_REVIEW|USER_SUPPLY` 형식으로 다시 확정한다. Story 작성은 P8 전까지 실행하지 않는다. `REVIVAL_ITEM`의 기획 데이터는 P0에서 잠갔으며 런타임 투영은 P0.5 이후에 수행한다.

## 5. `ADR-003` F 보조무기 입력 — `CLOSED`

2026-08-24 사용자 확정에 따라 지속 방어는 사용하지 않고 `F=짧은 패링/단기 방어`로 잠근다.

| 필드 | 확정값 |
|---|---|
| 입력 문맥 | 현재 선택 슬롯 `0`, 웅크리지 않은 일반 `F`, 사용 가능한 방패 장착 |
| 상태 순서 | `PARRY_STARTUP` 1틱 → `PARRY_ACTIVE` 4틱 → `SHORT_GUARD` 12틱 → 자동 종료 |
| 입력 방식 | 탭 1회, 키 해제 감지·홀드·토글 없음 |
| AP | 패링 시도 15, 단기 방어 지속 비용 0, 피격 시 가드 충격 AP 적용 |
| 취소 | 기본 공격·스킬·회피·슬롯 이탈·방패 해제/파손·GUI/시설 상호작용·빈사·강한 행동 불가 |
| 충돌 분리 | `Shift+F`는 메뉴, 슬롯 `1~8` 일반 `F`는 바닐라 손 교환 |

이 결정은 `ADR-001~002`의 확정값과 독립적이다. 구현 권위는 `COMBAT-004`, 입력 권위는 `SKILL-001 §25`와 `IMPLEMENTATION-HANDOFF-001`을 따른다.
