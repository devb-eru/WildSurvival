# WildSurvival Season 1 전체 콘텐츠 번들 계약

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `DATA-REVISION-002` |
| 상태 | `IMPLEMENTATION_CONTRACT` |
| 목표 번들 | `ws-content-r2` |
| Story 리비전 | `ws-story-s1-r1` |
| 예산 정책 | `budget-live-r2` |
| 활성 정책 | `NEW_RUN_ONLY` |
| 이전 번들 | `ws-content-r1`, 불변·동시 로드 가능 |
| 후보 레지스트리 | `기획/10 기술과 운영/contracts/ws-content-r2-registry.json` |
| 최종 수정일 | 2026-08-23 |

## 1. 계약 목적

`ws-content-r2`는 Season 1 Day 1~50, Day 51+ 반복, Final과 실제 Story를 한 번들로 구현하기 위한 전체 데이터 계약이다. 이 문서는 JSON 파일이 이미 구현됐다는 선언이 아니다. 구현자는 본 계약과 권위 문서를 입력으로 실제 번들을 생성하고 L0~L3 검증을 통과해야 한다.

- `ws-content-r1` 파일·해시·의미를 수정하지 않는다.
- r1 활성 회차는 `storyRevision: EMPTY`와 기존 데이터 잠금을 유지한다.
- r2는 새 회차에서만 선택하며 활성 회차에 소급하지 않는다.
- 도메인 결과는 Story가 승인하지 않는다. Story는 결과 이벤트를 소비해 표현만 담당한다.

## 2. 회차 잠금 튜플

회차 생성 트랜잭션은 다음 값을 함께 저장한다.

```yaml
content-revision: ws-content-r2
schema-version: 2
story-revision: ws-story-s1-r1
budget-policy-revision: budget-live-r2
draw-revision: draw-s1-r2
rules-revision: rules-s1-r2
activation-policy: NEW_RUN_ONLY
resource-pack-contract: ws-rp-s1-r1
```

튜플 중 하나라도 로드할 수 없으면 회차 생성을 거부한다. 생성 후에는 기본 포인터가 바뀌어도 저장된 튜플을 사용한다.

## 3. 목표 디렉터리

```text
content/ws-content-r2/
├─ content-lock.yaml
├─ manifest.json
├─ schemas/
│  ├─ manifest.schema.json
│  ├─ common.schema.json
│  ├─ day.schema.json
│  ├─ event.schema.json
│  ├─ enemy.schema.json
│  ├─ resource.schema.json
│  ├─ item.schema.json
│  ├─ recipe.schema.json
│  ├─ equipment.schema.json
│  ├─ facility.schema.json
│  ├─ research.schema.json
│  ├─ augment.schema.json
│  ├─ skill.schema.json
│  ├─ action.schema.json
│  ├─ entity.schema.json
│  ├─ loot.schema.json
│  ├─ codex.schema.json
│  ├─ migration.schema.json
│  ├─ boss.schema.json
│  ├─ final.schema.json
│  ├─ story.schema.json
│  ├─ budget.schema.json
│  └─ ops.schema.json
├─ days/season1-days-01-50.json
├─ days/endless-days-51-plus.json
├─ events/day01-10.json
├─ events/day11-20.json
├─ events/day21-50.json
├─ enemies/day01-10.json
├─ enemies/day11-20.json
├─ enemies/day21-50.json
├─ resources/day01-10.json
├─ resources/day11-20.json
├─ resources/day21-50.json
├─ items/materials.json
├─ items/non-equipment-items.json
├─ items/codex-index.json
├─ recipes/season1-recipes.json
├─ equipment/day01-10.json
├─ equipment/day11-20.json
├─ equipment/day21-50.json
├─ facilities/season1-facilities.json
├─ research/season1-research.json
├─ augments/personal-augments.json
├─ augments/party-augments.json
├─ skills/player-skills.json
├─ skills/entity-actions.json
├─ entities/support-entities.json
├─ loot/season1-loot.json
├─ bosses/day10.json
├─ bosses/day20.json
├─ bosses/day30.json
├─ bosses/day40.json
├─ final/day50-reconstruction-signal.json
├─ story/season1-scenes.json
├─ story/season1-logs.json
├─ budget/live-profiles.json
├─ migrations/id-aliases.json
├─ fixtures/cardinality.json
├─ fixtures/reference-graph.json
├─ fixtures/draw-locks.json
├─ fixtures/softlock-scenarios.json
├─ ops/admin-commands.json
└─ ops/telemetry-contract.json
```

현재 목표는 lock 1, manifest 1, schema 25, 데이터 43의 `70개 파일`이다. `STATUS-002`의 개별 상태 권위를 누락 없이 분리하기 위해 `status.schema.json`과 `season1-statuses.json`을 추가했다. 파일 분할이 바뀌면 manifest·레지스트리·검증 fixture를 같은 변경 세트에서 갱신한다. fixture도 manifest에 포함되는 실행 데이터이며 테스트 전용이라는 이유로 해시 검증에서 제외하지 않는다.

## 4. 도메인 권위 매핑

| 번들 도메인 | 권위 문서 ID | 구현 입력 |
|---|---|---|
| Day 1~50·51+ | `CONTENT-MASTER-001`, `CONTENT-DATA-D50-001` | Day 일정·EXP·위협·해금 |
| 사건 | `EVENT-DATA-001`, `EVENT-DATA-D20-001`, `EVENT-DATA-D50-001` | 단계·웨이브·보상·실패 |
| 적 | `ENEMY-DATA-001`, `ENEMY-DATA-D20-001`, `ENEMY-DATA-D50-001` | 템플릿·기술·변이·그룹 |
| 자원·가공 | `RESOURCE-DATA-001`, `RESOURCE-DATA-D20-001`, `RESOURCE-DATA-D50-001` | 노드·가치·가공·복구 |
| 재료·일반 아이템 | `MATERIAL-LIST-001`, `ITEM-LIST-001` | 고정 ID·획득·소모·도감 위치 |
| 도구·장비 | `TOOL-LIST-001`, `EQUIP-LIST-001`, `EQUIP-DATA-D20-001`, `EQUIP-DATA-D50-001` | 214 템플릿·옵션·내구도·BROKEN |
| 제작 | `RECIPE-LIST-001` | 315 조합·해금·시설 조건·3×3 배치 |
| 시설 | `FACILITY-LIST-001`, `FACILITY-001`, `FACILITY-DATA-D50-001` | 46 상태·비용·큐·네트워크·공용 원장 게이트 |
| 연구 | `RESEARCH-001` | 표본·노드·대응책·도감 |
| 플레이어 스킬 | `SKILL-LIST-001`, `SKILL-001` | 64 실행 레코드·입력·AP·탄약·내구도 |
| 증강 | `AUG-001`, `AUG-LIST-001`, `AUG-LIST-002`, `PARTY-SYNERGY-001` | 개인 50·파티 16·등급 잠금·1~4인 분기 |
| 엔티티·행동 | `ENTITY-LIST-001`, `ENEMY-DATA-001`, `ENEMY-DATA-D20-001`, `ENEMY-DATA-D50-001` | 적 53·보스 4·지원 34·행동 번들 |
| 획득·드롭 | `LOOT-LIST-001` | 62 테이블·기여자 분배·중복 차단 |
| 보스 | `BOSS-DATA-001`, `BOSS-DATA-D20-001`, `BOSS-DATA-D50-001` | Day10·20·30·40 상태기계 |
| Final | `FINAL-001`, `FINAL-DATA-001` | 활성·3단계·완료 TX |
| Story | `STORY-DATA-S1-001` | 장면·로그·큐·이관 스냅샷 |
| 예산 | `BUDGET-001`, `BUDGET-PROFILE-001` | 라이브 프로필·잠금·잔여 |
| 운영·관측 | `OPS-001`, `QA-BALANCE-001` | 명령·감사·텔레메트리 |
| 생산 그래프 검증 | `CONTENT-GRAPH-AUDIT-001` | 카디널리티·참조·Day 접근성·소프트락 fixture |

충돌 시 `DOC-AUTHORITY-001`의 우선순위를 사용한다. 이 문서는 원본 수치를 임의 복제해 새 권위를 만들지 않는다.

## 5. 공통 레코드

모든 실행 레코드는 최소 다음 필드를 가진다.

```json
{
  "id": "DOMAIN-STABLE-ID",
  "schemaVersion": 2,
  "contentRevision": "ws-content-r2",
  "enabled": true,
  "availableFromDay": 1,
  "tags": [],
  "references": [],
  "sourceDocumentId": "AUTHORITY-ID"
}
```

- ID는 리비전 안에서뿐 아니라 저장 데이터에 사용된 뒤 영구 재사용하지 않는다.
- 삭제 대신 `enabled:false`와 후속 ID를 manifest 변경 기록에 남긴다.
- 수치는 JSON number로 표현 가능한 범위만 사용한다. CHAOS BigNumber 전투 계수는 문자열 십진수로 직렬화한다.
- 확률은 `0.0000~1.0000`, 배율은 `0.0000~10.0000`, 시간은 틱 정수다.
- 표시 문자열은 `textKey`로 참조하고 로직 레코드에 한국어 문장을 직접 넣지 않는다.

## 6. 참조 방향

```text
days
├─ events ─ enemyGroups ─ enemies ─ skills/status/break
├─ resources ─ recipes ─ equipment
├─ facilities ─ research
├─ bosses/final
└─ story trigger keys

augments ─ tags ─ equipment/skills/status/break
budget profiles ─ day domain budgets
story ─ domain result event keys only
```

- 순환 참조는 `augments ↔ trigger result`, `story ↔ domain mutation` 모두 금지한다.
- Story 장면은 `BOSS_DEFEATED`, `FINAL_COMPLETED` 같은 커밋된 결과만 구독한다.
- Final은 Story 재생 성공 여부와 무관하게 완료 트랜잭션을 커밋한다.

## 7. manifest 계약

```json
{
  "contentRevision": "ws-content-r2",
  "schemaVersion": 2,
  "activationPolicy": "NEW_RUN_ONLY",
  "storyRevision": "ws-story-s1-r1",
  "budgetPolicyRevision": "budget-live-r2",
  "drawRevision": "draw-s1-r2",
  "rulesRevision": "rules-s1-r2",
  "files": [
    {"path":"days/season1-days-01-50.json","domain":"days","schema":"day.schema.json","sha256":"<build-generated>"}
  ]
}
```

`<build-generated>`는 실제 manifest에 허용되지 않는다. 위 코드는 형태 예시이며 빌드 작업이 UTF-8 LF 정규화 후 실제 소문자 SHA-256 64자를 넣는다. manifest 자체 해시는 `content-lock.yaml`에 기록한다.

## 8. 필수 합계·카디널리티 검증

| ID | 검증 |
|---|---|
| `RV2-C01` | Day 레코드가 1~50 정확히 50개, 중복·누락 없음 |
| `RV2-C02` | 개인 증강 마일스톤 3·6·10·15·20·25·30·35·40·45 정확히 10회 |
| `RV2-C03` | 개인 3/6/10 등급 SILVER/GOLD/PRISM 고정 |
| `RV2-C04` | 이후 등급 가중치 0.50+0.30+0.20=1.00, 최초 도달 잠금 |
| `RV2-C05` | 파티 증강 Day10·20·30·40 정확히 4회 |
| `RV2-C06` | 10개 무기군과 세 후반 세션 장비 템플릿 존재 |
| `RV2-C07` | Day10·20·30·40 보스 및 Final 레코드 존재 |
| `RV2-C08` | Story Day1~50 트리거와 Final 결과 장면 해석 가능 |
| `RV2-C09` | Final 활성 최소 Day가 50이며 다른 레코드가 낮추지 않음 |
| `RV2-C10` | 세션 전문 자원 하한 합 `361/382/486`과 DATA 원장 일치 |
| `RV2-C11` | 예산 프로필 배율 `0~10`, 필수 공급 `>=0.50` |
| `RV2-C12` | 모든 참조·textKey·Material·EntityType·태그 해석 가능 |
| `RV2-C13` | 파일 경로·크기·SHA-256이 manifest와 완전 일치 |
| `RV2-C14` | admin 명령이 권한·dry-run·확인·감사 정책을 가짐 |
| `RV2-C15` | 모든 완료·보상 단계에 멱등키 생성 규칙 존재 |
| `RV2-C16` | 재료 59, 일반 아이템 62, 도구·장비 214가 정확히 존재 |
| `RV2-C17` | 제작법 316개가 유일하며 산출물 315개와 의도된 대체 조합 1개가 일치 |
| `RV2-C18` | 도감 레코드 335개가 고정 ID·고정 위치로 유일하며 모든 대상 아이템을 해석 |
| `RV2-C24` | `WSI-CONS-REVIVAL_CORE`와 `WSRCP-D31-S01`이 1:1이며 Day33·RS-D33·FAC-S11 Lv4·대상별 회차 1회·전멸 거부를 명시 |
| `RV2-C19` | 플레이어 스킬 64, 개인 증강 50, 파티 증강 16이 정확히 존재 |
| `RV2-C20` | 엔티티 91개가 적 53·보스 4·지원 34로 정확히 분해되고 행동 참조가 해석됨 |
| `RV2-C21` | 시설 46, 획득·드롭 테이블 62가 정확히 존재하고 미해석 참조가 없음 |
| `RV2-C22` | Craft는 원목 4개 파티 해금, FAC-S16 전 개인 원장·이후 공용 원장이라는 접근 그래프를 위반하지 않음 |
| `RV2-C23` | slot 0 전투/채굴 중재와 서버 내구도·BROKEN 상태가 이중 소비·바닐라 파괴 없이 결정론적임 |

## 9. 검증 순서

1. lock 문법·리비전·manifest 해시
2. manifest schema·경로 중복·파일 수
3. 개별 파일 SHA-256·크기·UTF-8 LF
4. 도메인 schema
5. 전역 ID 유일성·참조 그래프
6. Material·EntityType·CustomModelData 범위
7. 확률·배율·합계·Day 카디널리티
8. 소프트락·1인 생존·Day50 불변식
9. 결정론적 드로우 fixture
10. 전체 결과 해시와 `CandidateReport` 생성

한 단계라도 실패하면 부분 도메인을 등록하지 않고 번들 전체를 거부한다.

## 10. 리소스 팩 계약

리소스 팩은 선택 사항이며 없을 때도 로직·판정·표시명이 유지돼야 한다.

| 범위 | 용도 |
|---:|---|
| `21000~21099` | 단검·둔기·지팡이·특수 무기 외형 |
| `21100~21199` | 보조무기·장신구 GUI 아이콘 |
| `21200~21299` | 시설·연구·Final GUI 아이콘 |
| `21300~21399` | Story 기록·신호 오브젝트 |

- 단검 Material은 검, 둔기 Material은 철퇴다.
- 모델 누락은 경고 후 바닐라 외형 폴백이며 회차를 막지 않는다.
- PDC `weapon_type`, 장비 ID, 슬롯 0/-106 동기화가 외형보다 권위다.

## 11. 마이그레이션·공존

| 회차 | 로드 |
|---|---|
| r1 활성 | `ws-content-r1 + EMPTY`, 변경 없음 |
| r2 신규 | 전체 잠금 튜플 r2 |
| r1 완료 기록 | r1 리더보드·감사 원장으로 읽기 가능 |
| 지원 종료 리비전 | 신규 생성만 차단, 기존 회차 로더 유지 |

r1→r2 활성 회차 마이그레이션은 제공하지 않는다. 운영자가 r1 회차를 r2로 바꾸는 명령도 만들지 않는다.

## 12. 후보 승인

| 상태 | 의미 |
|---|---|
| `CONTRACT_READY` | 본 문서·후보 레지스트리 완료 |
| `BUNDLE_BUILT` | 70개 목표 파일과 실제 해시 생성 |
| `L0_VALIDATED` | schema·참조·합계 통과 |
| `L1_TESTED` | 단위·결정론·멱등성 통과 |
| `L2_SIMULATED` | 1~4인 50 Day 헤드리스 통과 |
| `L3_LOAD_TESTED` | CHAOS 부하 통과 |
| `RELEASE_CANDIDATE` | 폐쇄 플레이 가능 |
| `LIVE_LOCKED` | 운영 승인, 신규 회차 포인터 교체 가능 |

현재 상태는 `L0_VALIDATED`다. `ws-content-r2` 70개 파일을 생성했고 manifest SHA-256, 카디널리티, 레시피 산출물, 엔티티→loot 및 엔티티·행동→status 참조 검증을 자동 통과했다. 전투·Day·시설 전체 실행 의미가 아직 프로덕션 런타임으로 전환 중이므로 `L1_TESTED` 이상으로 표시하지 않는다.

## 13. 완료 기준

- Season 1 전체 도메인의 파일·schema·참조·권위·합계 계약이 정의된다.
- r1 불변과 r2 신규 회차 전용 활성 정책이 함께 성립한다.
- Story·예산·드로우·규칙·리소스 팩 리비전이 원자 잠금 튜플에 포함된다.
- 구현자가 추측 없이 번들을 만들고 기계 검증 상태를 단계적으로 올릴 수 있다.
