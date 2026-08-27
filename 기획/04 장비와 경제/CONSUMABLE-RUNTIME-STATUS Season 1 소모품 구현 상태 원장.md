---
document_id: CONSUMABLE-RUNTIME-STATUS-001
title: Season 1 소모품 구현 상태 원장
version: 0.2.0
status: ACTIVE_CONTROL
authority_scope: Season 1 소모품 13개 DATA·RUNTIME·TEST 상태
content_revision: ws-content-r2
last_updated: 2026-08-27
---

# Season 1 소모품 구현 상태 원장

## 1. 목적과 판정

`ITEM-LIST-001`의 `CONS` 13개를 다른 아이템 범주와 분리해 추적한다. 이 문서는 구현 완료를 선언하는 문서가 아니다. 각 행의 데이터, 서버 효과, 자동 시험, 실제 클라이언트 시험이 모두 `VERIFIED`일 때만 해당 ID를 완료로 승격한다.

권위 순서는 `ITEM-LIST-001 → CRAFT-001/RESOURCE-DATA-D20-001 → STATUS-001/SKILL-001 → PRODUCTION-DATA-CLOSURE-001 → 이 원장`이다. 상위 문서에 정확한 수치가 없으면 기존 코드값을 권위로 승격하지 않고 `BLOCKED`로 둔다.

허용 상태는 `NOT_STARTED`, `IN_PROGRESS`, `VERIFIED`, `BLOCKED`다.

## 2. 공통 실행 계약

- Q1~Q4 입력은 slot 0 전투 자세의 `Shift+6~9`에서만 해석한다.
- 즉시형도 `request → validate → reserve → apply → commit` 순서를 사용한다. 실패 시 수량을 차감하지 않는다.
- 전투당 한도 키는 활성 보스, 활성 Encounter, 마지막 전투 행동부터 5초인 개인 교전 순서로 결정한다. Day 변경 자체는 한도 키가 아니다.
- 야전 배급팩 채널은 시작 시 수량을 예약·차감하지 않는다. 완료 틱에 대상 조건과 실제 수량을 다시 검증하고 원장·물리 아이템을 커밋하며, 좌클릭·접속 종료·서버 종료·행동 불가 취소는 아이템을 소비하지 않는다.
- 공용 액티브와 Q 입력은 같은 실제 아이템 원장을 소비하므로 마지막 1개를 동시에 성공시킬 수 없다.
- 자동 시험만으로 실제 숫자키, 좌클릭 취소, 재접속, 액션바 판독을 승인하지 않는다.

## 3. ID별 상태

| ID | 이름 | 확정 실행 계약 | DATA | RUNTIME | AUTO_TEST | CLIENT_E2E | 잔여 작업·차단 |
|---|---|---|---|---|---|---|---|
| `WSI-CONS-RATION_PACK` | 야전 배급팩 | 비전투 160틱, 전투 240틱 완료 시 소비 채널; 완료 시 허기 8·포화 6 | `VERIFIED` | `IN_PROGRESS` | `IN_PROGRESS` | `BLOCKED` | 완료 시 원자 소비, 좌클릭·접속/서버 종료 미소비 구현. 실제 키 입력·TPS 열화·중복 입력 검증 필요 |
| `WSI-CONS-BANDAGE` | 붕대 | BLEED 1중첩 제거, 직접 회복 없음 | `VERIFIED` | `IN_PROGRESS` | `IN_PROGRESS` | `BLOCKED` | 무효 대상 무소비 구현. 실제 상태 아이콘·잔여 중첩 검증 필요 |
| `WSI-CONS-REPAIR_KIT` | 야전 수리 키트 | 가장 손상된 장착 장비를 최대 내구 40%까지 복구 | `VERIFIED` | `IN_PROGRESS` | `IN_PROGRESS` | `BLOCKED` | 아이템·사용 횟수·내구 복구 원자 커밋 구현. 장비 선택 동률·BROKEN·동시 GUI E2E 필요 |
| `WSI-CONS-PURIFY_AMPOULE` | 정화 앰풀 | 개인 오염 -15, STATUS 제거 없음, 전투당 2회 | `VERIFIED` | `NOT_STARTED` | `NOT_STARTED` | `BLOCKED` | 개인 오염 0~100 영속 필드와 단계 이벤트 구현 필요 |
| `WSI-CONS-AP_STIM` | 응급 AP 자극제 | 즉시 AP +10, 이후 5초간 초당 +3, 초과분 소멸, 탈진 해제 없음, 전투당 1회 | `VERIFIED` | `VERIFIED` | `IN_PROGRESS` | `BLOCKED` | 단일 지속 상태·저장·누락 틱 보정·지속 중 재사용 거부 구현. 재접속·재시작·마지막 1개 경합 E2E 필요 |
| `WSI-CONS-RESCUE_BRACE` | 구조 고정대 | 다음 구조 첫 유효 틱부터 중단 피해 임계 +10% | `VERIFIED` | `IN_PROGRESS` | `IN_PROGRESS` | `BLOCKED` | 아이템·사용 횟수·최고값 예약과 첫 유효 구조 틱 활성화 구현. 실피격·동시 구조 E2E 필요 |
| `WSI-CONS-PORTABLE_PURIFIER_CHARGE` | 휴대 정화기 충전 | 활성 FAC-P05 가동시간 60초 연장 | `VERIFIED` | `IN_PROGRESS` | `IN_PROGRESS` | `BLOCKED` | 아이템·사용 횟수·선택 시설 만료 연장 원자 커밋 구현. 동일 시설 다중 사용·철거/파괴 경합 E2E 필요 |
| `WSI-CONS-ANTIDOTE_INJECTION` | 해독 주사 | POISON 최대 2중첩, 관리 밖 약한 독이면 효과 1개 제거 | `VERIFIED` | `IN_PROGRESS` | `IN_PROGRESS` | `BLOCKED` | 과다 정화를 제거함. 잔여 스택 포션 강도·지속시간 실제 판독 필요 |
| `WSI-CONS-COOLING_SALVE` | 냉각 연고 | BURN 최대 2중첩 제거, 이후 5초간 새 BURN 지속시간 ×0.75 | `VERIFIED` | `IN_PROGRESS` | `IN_PROGRESS` | `BLOCKED` | 상태 PDC 저장·만료 구현. 재시작 경계와 연속 사용 E2E 필요 |
| `WSI-CONS-TOURNIQUET` | 지혈 압박대 | BLEED 최대 2중첩 제거, 직접 회복 없음 | `VERIFIED` | `IN_PROGRESS` | `IN_PROGRESS` | `BLOCKED` | 무효 대상 무소비·정확 중첩 구현. 실제 잔여 중첩 검증 필요 |
| `WSI-CONS-NEURAL_STABILIZER` | 신경 안정제 | ROOT→SILENCE→DISARM 우선순위 중 1개 제거; HARD_CC 자기 사용 불가 | `VERIFIED` | `IN_PROGRESS` | `IN_PROGRESS` | `BLOCKED` | 자기 Q와 `CONTROL_CLEANSE` 공용 스킬 우선순위 실행 구현. 아군 대상 선택·거리 계약 미구현 |
| `WSI-CONS-REINFORCED_RESCUE_BRACE` | 강화 구조 고정대 | 다음 구조 시작 1회 중단 피해 임계 +25% | `VERIFIED` | `IN_PROGRESS` | `IN_PROGRESS` | `BLOCKED` | +10%와 비합산 최고값 예약·첫 유효 진행 틱 활성화 구현. 실피격·동시 구조 E2E 필요 |
| `WSI-CONS-BIO_SHIELD_AMPOULE` | 생체 보호막 앰풀 | 최대 HP 8% 보호막 120틱, 전투당 1회 | `VERIFIED` | `IN_PROGRESS` | `IN_PROGRESS` | `BLOCKED` | 다른 흡수 보호막과의 원천별 만료·피해 소비 경합 검증 필요 |

## 4. 다음 구현 순서

1. 개인 오염 0~100 영속 필드와 정화 앰풀 -15를 구현한다.
2. 신경 안정제의 아군 대상 선택과 생체 보호막의 원천별 흡수 원장을 구현한다.
3. 수리 키트·휴대 정화기 충전의 실제 GUI/철거 동시 경합을 검증한다.
4. Test Lab에 13개 ID별 성공·무효 대상·마지막 1개 경합·저장 복구 fixture를 추가한다.
5. `E2E-52` 실제 클라이언트 시험을 통과한 행만 `VERIFIED`로 승격한다.

## 5. 현재 결론

소모품 13개는 목록·제작·기본 진입점이 존재하지만 전체 완료 상태가 아니다. 생산 소모품의 AP·수량·전투당 사용 횟수와 지속 가능한 직접 효과는 후보 회차 한 번으로 커밋하며, 배급팩은 완료 시 소비해 취소·접속 종료 손실을 제거했다. 구조 고정대 예약·첫 유효 틱 활성화도 구현됐지만 개인 오염 원장, 일부 원천별 효과와 실제 클라이언트 시험이 남아 있으므로 묶음 상태는 `IN_PROGRESS`다.
