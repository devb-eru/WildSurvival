# WildSurvival Season 1 구현 인계 계약

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `IMPLEMENTATION-HANDOFF-001` |
| 상태 | `IMPLEMENTATION_IN_PROGRESS` |
| 인계 입력 | `DOC-AUTHORITY-001`, `DATA-REVISION-002`, G1~G3 권위 문서 |
| 구현 기준 | Minecraft Java, Paper 서버 플러그인, 바닐라 클라이언트, 모드 금지 |
| 외부 의존 | MagicSpells 비필수; S1 기본 백엔드는 네이티브 Paper |
| 실행 로드맵 | `DEV-ROADMAP-001` 프로토타입 우선 단계·승인 게이트 |
| 전 항목 완료 계획 | `PRODUCTION-COMPLETION-PLAN-001` 개별 ID 구현·검증 행렬 |
| 최종 수정일 | 2026-08-24 |

## 1. 인계 범위

이 계약은 모듈 경계, 저장·이벤트·테스트·배포 책임과 IMP 최종 범위를 고정한다. 실제 구현 순서·프로토타입 범위·단계 승인 게이트는 `DEV-ROADMAP-001`을 따른다. 구현 중 기획서에 없는 수치·콘텐츠를 임의 창작하지 않는다. 모호함은 `DOC-AUTHORITY-001`에 이슈로 등록하고 기존 불변식을 보존하는 안전 폴백을 사용한다.

## 2. 기술 불변식

- 클라이언트 모드는 사용하지 않는다.
- 모든 핵심 판정·수치·보상·장착은 서버 권위다.
- 커스텀 GUI 외 직접 장착 변경은 다음 틱에 권위 장비로 복원하고 감사 로그를 남긴다.
- 주무기 실제 슬롯은 `0`, 보조무기는 `-106`이다. 둘이 비면 권투를 자동 활성화한다.
- 핫바 `1~8`은 바닐라 자유 슬롯이다. 전투 입력과 Shift+숫자 C/Q 입력은 현재 선택 슬롯이 `0`일 때만 해석한다.
- 커스텀 자원은 `FAC-S16` 완공 전 개인 자원 원장에 지급·소비하고, 완공 뒤 명시적 입출고를 거쳐야만 공용 원장을 사용한다. 획득 즉시 공용 원장으로 보내지 않는다.
- 커스텀 제작은 원목 4개로 파티 해금한 Craft GUI의 3×3 고정 조합으로만 커밋한다.
- 무기 기본 공격과 W1~W3은 슬롯 `0`에서만 서버 실행한다. `Shift+2~9`의 C/Q 실행은 완료·실패와 무관하게 입력 시퀀스 가드 후 슬롯 `0`으로 복원하고, L/R/Shift+L/Shift+R/F는 이미 슬롯 `0`이므로 별도 슬롯 변경을 만들지 않는다.
- 방패 `F`는 지속·홀드·토글 방어가 아니다. 슬롯 `0`의 일반 `F` 1회로 `1틱 시작→4틱 패링→12틱 단기 방어→자동 종료`하며, 시간 비례 AP 없이 가드 충격 AP만 사용한다. 공격·스킬·회피·슬롯 이탈·방패 해제·강한 행동 불가 상태가 즉시 취소한다.
- 아이템 도감의 ID·위치는 리비전에 고정하며 최초 획득 전에는 검은 염료 `???`만 노출한다.
- 도감 해금은 최초 획득 커밋으로 한 번만 발생한다. 해금하면 등록된 조합 정보는 보이되 실제 제작 권한·시설·Day 조건은 별도로 검사한다.
- 초반 목표는 바닐라 Advancement의 별도 `WildSurvival` 탭으로 제공하고 회차 상태에서 완료를 복구한다.
- 곡괭이는 전투 표적 판정 우선, 삼지창은 바닐라 피해 취소 후 커스텀 투척·회수, 단검은 검·둔기는 철퇴 기반이다.
- 모든 파티는 한 팀이며 PvP·경쟁 보상·개인 승리 조건을 만들지 않는다.
- 신규 회차는 2~4인, 회차 중 신규 참가 금지, 유일 생존자 1인 지속은 허용한다.
- Day 50 전에 최종 목표를 완료할 수 없다.

## 3. 모듈 경계

| 모듈 | 소유 상태 | 발행 이벤트 | 금지 |
|---|---|---|---|
| `content` | 리비전·manifest·레지스트리 | `CONTENT_VALIDATED` | 부분 번들 활성 |
| `input` | 현재 슬롯·클릭·웅크림·F·숫자 입력 중재 | `INPUT_INTENT` | 전투 결과 직접 확정 |
| `run` | 회차·Day·난이도·인원 스냅샷 | `DAY_STARTED`, `RUN_ENDED` | 콘텐츠 수치 직접 변경 |
| `player` | 생존·다운·사망·장착 의미 슬롯 | `PLAYER_STATE_CHANGED` | 보상 직접 지급 |
| `combat` | AP·회피·패링·상태·브레이크·패턴 | `COMBAT_RESULT` | Story 결과 승인 |
| `growth` | EXP·레벨·개인/파티 증강 드로우 | `MILESTONE_LOCKED` | 등급 재추첨 |
| `economy` | 개인/공용 자원·제작·장비·내구도·보상 원장 | `LEDGER_COMMITTED`, `EQUIPMENT_BROKEN` | 클라이언트 수치 신뢰 |
| `catalog` | 아이템·도감·조합·스킬·엔티티·드롭 레지스트리 | `DISCOVERY_COMMITTED` | 회차 중 ID 재배치 |
| `ui` | 메뉴·Craft·도감·스탯·장비·설정·피해 표시 | `UI_INTENT` | 도메인 원장 직접 변경 |
| `world` | 오염·노드·시설·연구·발견 | `WORLD_OBJECTIVE_COMMITTED` | 영구 월드 직접 손상 |
| `encounter` | 사건·적·공세·잔여 예산 | `ENCOUNTER_COMMITTED` | 활성 상한 우회 |
| `boss` | Day10·20·30·40 상태기계 | `BOSS_DEFEATED` | 개별 소환체 보상 |
| `finale` | Final 활성·3단계·완료 TX | `FINAL_COMPLETED` | Story 성공 의존 |
| `story` | 장면·기록·표현 큐 | `PRESENTATION_ACK` | 도메인 상태 변경 |
| `ops` | 명령·감사·텔레메트리·롤백 | `OPS_ACTION` | 활성 회차 리비전 교체 |

## 4. 구현 작업 패키지

구현 시작 전 `DEV-ROADMAP-001`의 `DEV-000` 계약 보정 게이트를 통과해야 한다. 아래 IMP는 최종 기능 묶음이며 프로토타입 조각이 통과해도 전체 완료로 표시하지 않는다.

| ID | 작업 | 입력 문서 | 산출물 | 핵심 승인 |
|---|---|---|---|---|
| `IMP-001` | r2 schema·loader·manifest | DATA-REVISION-002 | 70파일 번들 골격·validator | L0 |
| `IMP-002` | 회차 잠금·DB migration | TECH-001, GAME-001~003 | 잠금 튜플·복구 | 재시작 fixture |
| `IMP-003` | 통합 메뉴·Craft·도감·HUD·피해 표시 | UX-001, ITEM-LIST-001 | Shift+F 메뉴·고정 도감·TextDisplay | GUI·표시 상한 테스트 |
| `IMP-004` | 입력 중재·장착·10무기·스킬64·내구도 | EQUIP-001, WEAPON-002, TOOL-LIST-001, SKILL-LIST-001, SKILL-EFFECT-LIST-001 | slot0 판정·명시 operation 실행기·BROKEN 원장 | 스킬64 필수 필드·자식 참조·실패 환불, 무기 판정 10종·이중 소비 0 |
| `IMP-005` | 상태·브레이크·패턴 | STATUS/BREAK/COMBAT | 전투 파이프라인 | 순서·상한 테스트 |
| `IMP-006` | 성장·개인 50·파티 16 드로우·효과66 | PROG/AUG/PARTY-SYNERGY, AUG-LIST-001/002, AUGMENT-EFFECT-LIST-001 | 마일스톤 잠금·후보·고유 opcode/trigger/stateScope 실행 | 결정론·ICD·상한·파생 재트리거 0 fixture |
| `IMP-007` | 재료 59·일반 아이템 62·제작 316·장비 214 원장 | MATERIAL/ITEM/RECIPE/TOOL-LIST | 거래·3×3 예약·NEED·부활 코어 | 카디널리티·멱등·음수 차단 |
| `IMP-008` | 시설 46·연구·발견·공용 원장 게이트 | FACILITY-LIST/FACILITY/RESEARCH/DISC | 큐·네트워크·도감·FAC-S16 | 소프트락 fixture |
| `IMP-009` | Day1~50 사건·엔티티 92·적 행동 69·드롭 62 | EVENT/ENEMY DATA, ENEMY-ACTION/ENTITY/LOOT-LIST | 스케줄러·웨이브·명시 행동·분배 | Day 누락·미해석 owner/child 참조 0, `*-PRIMARY` 0 |
| `IMP-010` | Day10~40 보스 | BOSS DATA | 상태기계 4종 | 재접속·보상 1회 |
| `IMP-011` | Final | FINAL-DATA-001 | 활성·3단계·6단계 TX | Day50·멱등 |
| `IMP-012` | Story | STORY-DATA-S1-001 | FULL/REDUCED·큐·로그 | 도메인 비침범 |
| `IMP-013` | 예산·CHAOS | BUDGET-PROFILE/BALANCE-CHAOS | 선택·잔여·HOLD | 극단 벡터 |
| `IMP-014` | 난이도·인원·사망 | BALANCE-MATRIX | 스냅샷·1인 분기 | 40조합 |
| `IMP-015` | 텔레메트리·관리 명령 | QA-BALANCE/OPS | 이벤트·감사·롤백 | 권한·dry-run |
| `IMP-016` | 리소스 팩 폴백 | UX/DATA-REVISION-002 | model registry·fallback | 팩 없음 완주 |
| `IMP-017` | E2E·부하·릴리스 | 전체 | L1~L5 보고서 | RC 승인 |

작업 패키지는 순서 의존이 있다. `DEV-000→프로토타입 조각→G-400 승인→나머지 001~017` 순으로 확장한다. 최종 병합 의존은 `001→002→003~008→009~014→015~017`을 유지하며, 병렬 작업도 상위 인터페이스가 `S1_dev`에 합쳐진 뒤 시작한다.

## 5. 핵심 이벤트 계약

```text
domain command
→ validate authority and current state
→ reserve resources/budget
→ calculate result
→ commit domain ledger with idempotency key
→ publish committed event through outbox
→ Story/telemetry consume asynchronously
```

- 이벤트 버스 직접 발행과 DB 커밋을 따로 수행하지 않는다. outbox를 같은 트랜잭션에 기록한다.
- 소비자는 `eventId + consumerId`로 중복을 제거한다.
- Story·HUD 전송 실패는 도메인 커밋을 롤백하지 않는다.
- 보상·Final은 단계별 멱등키와 재개 지점을 저장한다.

## 6. 저장 요구

| 테이블·저장 | 필수 키 |
|---|---|
| `runs` | runId, 잠금 튜플, day, difficulty, mode, state |
| `run_players` | registered/survivable/active, deathState, equipment snapshot |
| `player_loadouts` | W1~W3, C1~C4, Q1~Q4, commonInputMode, version |
| `equipment_instances` | instanceId, templateId, owner, slot, current/max durability, condition, version |
| `player_discoveries` | playerId, codexId, discoveredAt, sourceEventId |
| `recipe_permissions` | runId, recipeId, unlockState, sourceEventId |
| `personal_resources` | runId, playerId, resourceId, balance, version |
| `public_resources` | runId, resourceId, balance, facilityStateVersion |
| `budget_snapshots` | day, profile, domain base/multiplier/locked/residual |
| `milestone_locks` | milestone, tier, firstPlayer, draw seed/revision |
| `ledgers` | domain, delta, balance, idempotencyKey |
| `world_objects` | objectId, chunk, state, version |
| `encounters` | state, wave, residual budget, participant snapshot |
| `boss_runs` | phase, HP ratio, break, pattern deck, reward step |
| `final_runs` | activation snapshot, stage, completion TX step |
| `story_runs` | story revision, queue cursor, scene state |
| `outbox` | eventId, type, payload revision, delivery state |
| `audit_log` | actor, permission, before/after, reason, timestamp |

낙관적 잠금 `version`과 단일 회차 직렬 실행 큐를 함께 사용한다. 서버 종료 시 신규 명령을 막고 큐·outbox·원장을 플러시한 뒤 종료한다.

## 7. Paper·바닐라 구현 경계

| 요구 | 플러그인 구현 |
|---|---|
| 커스텀 GUI | Bukkit InventoryHolder+PDC, 서버 상태 재렌더 |
| HUD | ActionBar·BossBar·Title·Scoreboard 채널 우선순위 |
| 커스텀 몹 | 바닐라 EntityType+속성+AI 목표+PDC, 서버 패턴 실행기 |
| 커스텀 외형 | 선택 리소스 팩 CustomModelData, 로직 폴백 필수 |
| 장비 내구도 | `PlayerItemDamageEvent`는 관찰 가능하게 유지하고 적용 damage만 0으로 중화한 뒤 서버 원장 1회 차감, 미러 Damageable 수동 갱신. 0이면 아이템 보존 `BROKEN`, 권위 `EquipmentBrokenEvent`, 감사 `EQUIPMENT_BROKEN`, 전이 직전 복사본을 담은 호환 `PlayerItemBreakEvent`를 각 1회 발행 |
| 삼지창 | 발사 이벤트 취소, 표시 엔티티/투사체 추적, 서버 충돌·회수 |
| 상태·브레이크 | 서버 틱 상태 컨테이너와 전용 BossBar |
| 오염 | 청크 메타데이터·블록 변경 큐, 월드 손상 제한 |
| 구조·시설 | 보호된 월드 오브젝트와 GUI 상호작용 |
| 피해 숫자 | 피해 확정 뒤 victim 근처 TextDisplay, 4틱 다단 집계·14틱 수명·개인/청크 상한 |

NMS 직접 접근은 금지하지 않지만 Paper API로 불가능한 경우에만 어댑터 뒤에 둔다. 서버 버전 변경 시 어댑터 실패가 콘텐츠 원장을 손상시키지 않아야 한다.

## 8. MagicSpells 결정

- Season 1 필수 의존성으로 채택하지 않는다.
- `softdepend`가 없어도 전체 기능·테스트·완주가 가능해야 한다.
- 향후 사용 시 `integration` 어댑터는 시각·시전 표현만 위임하며 피해·AP·상태·브레이크·보상 결과는 네이티브 파이프라인이 확정한다.
- 호환 버전·장애 정책은 실제 도입 PR에서 별도 계약과 폐쇄 테스트를 요구한다. 현재 기획에 특정 버전을 임의 고정하지 않는다.

## 9. Definition of Ready

작업 패키지는 다음을 충족해야 시작할 수 있다.

- 권위 문서 ID와 구현할 레코드·상태 전이가 명시됨
- 입력·출력·오류·재시작·멱등성 fixture가 정의됨
- 다른 모듈 소유 상태를 직접 변경하지 않음
- 바닐라+플러그인으로 표현 가능한 폴백이 있음
- 1~4인·사망·접속·TPS 분기가 확인됨

## 10. Definition of Done

- 코드·schema·DATA·테스트가 같은 변경에 포함됨
- L0 schema·참조·합계 100% 통과
- 단위·통합·재시작·중복 이벤트 테스트 통과
- GUI FULL/REDUCED와 리소스 팩 없음 테스트 통과
- 1인 생존과 2·3·4인 E2E fixture 통과
- TPS·활성 개체·전조 상한 계측 포함
- 텔레메트리·감사 로그가 민감정보 없이 생성됨
- 문서 상태와 실제 후보 상태가 일치함

## 11. 필수 E2E 시나리오

| ID | 시나리오 | 기대 |
|---|---|---|
| `E2E-01` | 3인 Easy Day1→50 | 누락 없이 Final 활성 |
| `E2E-02` | Day15 최초 티어 잠금 뒤 후발 3명 | 같은 등급·후보만 독립 랜덤 |
| `E2E-03` | Day30 보스 중 재시작 | 페이즈·보상 단계 1회 |
| `E2E-04` | 4→1인 생존 | 다음 안전 페이즈 보정·대체 기믹 |
| `E2E-05` | 빈 손↔무기 GUI 전환 | 권투 자동·slot 0/-106 일치 |
| `E2E-06` | 곡괭이 전투 표적 앞 블록 | 전투 우선, 블록 미파괴 |
| `E2E-07` | 삼지창 투척·회수 실패 | 바닐라 이중 피해 없음·AP 손실 1회 |
| `E2E-08` | CHAOS 위협 10배·TPS 저하 | 잔여 보존·HOLD·재개 |
| `E2E-09` | Story 소비자 실패 | 도메인 완료 정상·장면 재시도 |
| `E2E-10` | Day49 모든 준비 완료 | Final 비활성, Day50에만 활성 |
| `E2E-11` | 완료 TX 단계 4 뒤 장애 | 재개 후 고유 보상·완료 1회 |
| `E2E-12` | 리소스 팩 거부 | 바닐라 외형으로 전 기능 완주 |
| `E2E-13` | 슬롯 1~8에서 블록·횃불·음식 사용 | C/Q 오발 0, 선택 슬롯 강제 복귀 0 |
| `E2E-14` | Craft 해금 동시 클릭·3×3 제작·GUI 강제 종료 | 원목·재료 복제/유실 0 |
| `E2E-15` | 저장소 전후 자원 획득·입출금 | 설치 전 공용 원장 접근 불가, 설치 후 잔액 보존 |
| `E2E-16` | 도감 첫 획득·재접속·콘텐츠 순서 | 고정 ID/위치 유지, 해금 알림 1회 |
| `E2E-17` | 등록 원목·광물 채집과 도구 등급 | 블록당 WS 드롭 1종, 바닐라 중복 드롭 0, 부족 등급은 블록 보존 |
| `E2E-18` | 무기별 W1~W3 장착·해제·재접속·시전 | 무기별 저장 유지, 빈 슬롯 거부, AP/화살 1회 소비, 식별 가능한 효과 |
| `E2E-19` | L키 초반 길잡이 진행·재접속 | 18단계 고정 순서, 완료 복구, 미완료 다음 목표 표시 |
| `E2E-20` | 신규 월드 Day1 시작 보급 없음 | 90초 준비 안에 원목→Craft→급조 곡괭이→첫 무기 경로가 소프트락 없이 성립 |
| `E2E-21` | slot0 곡괭이·적대 표적 없음·허용 블록 | 바닐라 채굴 성립, AP 0소비, 서버 내구도 정확히 1회 차감 |
| `E2E-22` | slot0 곡괭이·적대 표적과 블록 겹침 | 커스텀 기본 공격만 실행, 블록 보존, AP·내구도 각 1회 소비 |
| `E2E-23` | 장비 내구도 1에서 실행·수리 | 아이템 미삭제, BROKEN 전환·효과/공격 차단, 같은 instanceId로 수리 |
| `E2E-24` | slot0/slot1~8에서 Shift+2~9 | slot0에서만 C/Q 실행 후 0 복원, 나머지 슬롯은 바닐라 선택·행동 유지 |
| `E2E-25` | 어느 핫바 슬롯에서든 Shift+F | 플레이어 메뉴 1회 열림, 전투 F 오발 0 |
| `E2E-26` | 단타·4틱 내 다단·흡수 피해 | victim 근처 실제 HP 피해량 표시, 다단 집계·14틱 제거·상한 준수 |
| `E2E-27` | FAC-S16 전후 획득·제작·입출고 | 전에는 개인 원장만 사용, 이후 명시 입출고만 공용 반영, 자동 이체 0 |
| `E2E-28` | 도감 전체 레지스트리·첫 획득·재접속 | 335 고정 ID/위치, 미발견 BLACK_DYE `???`, 조합 표시·권한 분리 |
| `E2E-29` | 전체 생산 카탈로그 로드 | 재료59·일반62·장비214·조합316·스킬64·증강66·적행동69·엔티티92·시설46·드롭62 일치 |
| `E2E-30` | slot0 WS 장비 내구 1에서 공격·스킬·곡괭이 채굴 | ItemStack 보존 `BROKEN`, 원장 1회 차감, 두 파손 이벤트·주손 효과·사운드 각 1회, 다음 틱 동일 instanceId 미러 복원, 재실행 중복 0 |
| `E2E-31` | slot1~8 바닐라 도구 파손 | 합성 이벤트 0, 바닐라 `PlayerItemBreakEvent`와 실제 ItemStack 소멸 유지 |
| `E2E-32` | FAC-C03 두 플레이어 동시 접근·재접속·재시작·철거 | 한 쓰기 세션만 허용, 9칸 ItemStack/PDC 원형 일치, 내용물이 있으면 철거 거부, 복제·유실 0 |
| `E2E-33` | FAC-R01/R02 제작 뒤 즉시 Final 준비 검사 | 각각 `ASSEMBLED`, 준비 증명 없음; 서버 가동 120/60초 뒤에만 `READY`와 증명 1회 |
| `E2E-34` | FAC-R03/R04/R05 증거 없는 기능 실행 | `READY/CALIBRATED` 승격과 증명 생성 0, `BLOCKED_EVIDENCE` 원인 표시 |
| `E2E-35` | FAC-R06 Day49/Day50 및 하위 시설 누락 | Day49 항상 거부, Day50에도 R01~R04 READY·R05 CALIBRATED 3개 전에는 거부 |
| `E2E-36` | TAUNT 아군·플레이어·일반 적·보스 | 같은 파티 적용 거부, 플레이어의 비고정 대상 피해 0, 일반 적은 유효 범위에서 시전자를 우선, 보스 패턴 대상은 변경 없음 |
| `E2E-37` | 부상 0·1·2·3에서 치명 피해 | 0~2는 부상 증가 후 1.5초 보호와 70%·52.5%·35% 빈사 체력, 3은 빈사 없이 완전 사망 |
| `E2E-38` | 빈사 중 일반·엘리트/보스·환경 피해와 다단 공격 | 방어 후 피해의 50%·75%·50%, 타격당 빈사 최대 체력 50% 상한, 자연 시간초과 사망 0 |
| `E2E-39` | 부상 1·2·3 한 명 구조 | 5·7·9초 채널, 완료 HP 30%·25%·20%, 최소 최종 시간 2.5초 |
| `E2E-40` | 빈사 중 재접속·서버 재시작 | 생명 상태·부상·빈사 현재/최대 체력을 보존하고 일반 HP 1·제한 효과 복원 |
| `E2E-41` | 일반 Day 종료·보스전·잔존 공세·치료 시설 | 일반 종료에만 생존자의 부상 1 감소, 나머지는 유지하거나 정확한 비용으로 감소 |
| `E2E-42` | 두 명 동시 구조·중단·AP 부족 | 100%+60% 공유 진행, 각자 초당 5 AP, 0.5초 유예 뒤 초당 20% 감소, 최대 2명 |
| `E2E-43` | 완전 사망·재시작·유품 회수·희귀 부활 | 불변 사망 스냅샷과 유품 원본 인스턴스 1회 생성, 25% 소모품 손실, 중복 회수·부활 0 |
| `E2E-44` | 빈사 상태에서 웅크리기 도움 요청 연타 | 최초 1회만 파티에 이름·거리·방향과 3초 표시, 이후 10초 동안 채팅·사운드 중복 0 |
| `E2E-45` | 최근 안전 지점 기록 후 공허 치명 피해 2회 | 첫 회는 안전 지점 이동·빈사·기회 소비, 두 번째는 빈사 우회 완전 사망 |
| `E2E-46` | 빈사 중 일반 로그아웃·재접속 및 서버 종료·재시작 | 일반 로그아웃은 다음 서버 틱 완전 사망, 서버 종료는 예약 작업 미실행·빈사 상태 원형 복원 |
| `E2E-47` | 빈사 보호·이동·수영·사다리·넉백·탈것·순간이동 | 보호 1.5초 이동 0, 이후 활성 속도의 20%, 수면 방향만 이동, 상승·점프·질주·탑승·외부 순간이동 차단, 적 넉백 50% |
| `E2E-48` | 완전 사망 관전자 평시·보스·Final 경계 이탈 | 평시 생존자 48m/활성 시설 32m, 보스 48/52/40/44m, Final 50m 밖에서 생존자 시점 또는 허용 중심으로 즉시 복귀 |
| `E2E-49` | 붉은 처형자·일반 적·보스의 빈사 대상 선택 | 처형자는 보호 종료 빈사 우선, 일반 적은 공격 가능한 생존자가 없을 때만 빈사 선택, 보호 중·완전 사망자는 단일 대상 제외 |
| `E2E-50` | 316개 제작식 정규 입력·동일 배열 순환·GUI 강제 종료 | 모든 ID의 수량·태그·증명·소비 슬롯 일치, 선택 ID 유지, 입력 복제·유실 0 |
| `E2E-51` | 신규 일반·기존 프로토타입·r2 회차·Test Lab 생성과 재시작 | 승격 뒤 신규는 `SEASON_1/ws-content-r2.1`, 기존 r1/r2는 각 잠금 저장소 복구, 교차 저장·동시 활성 임의 선택 0 |
| `E2E-52` | 소모품 전투 한도·정확 정화·배급팩 예약 채널 | Encounter/보스/5초 개인 교전별 한도, 중첩 과다 제거 0, 8/12초 완료, 좌클릭·접속 종료·서버 종료 취소 시 예약 수량 원형 반환 |
| `E2E-53` | 탄약·휴대 장치·시설 키트·호출품 데이터와 사용 | 48개 모두 카테고리별 스택·소유·정책·recipe·시설 참조 일치, 탄약 원장·휴대 인스턴스·호출 원자 롤백과 실제 우클릭 통과 |
| `E2E-54` | 일반 화살 묶음 입금·활/석궁·순간 장전·재접속 | slot1~8 우클릭 수량 그대로 입금, 공격당 1·장전당 2 우선 소비, HUD·저장 일치, 부족 시 바닐라 화살 폴백 |
| `E2E-55` | 동일 플레이어 P05 2기·P06 3기 배치와 재시작 | 장치·배치별 `portableInstanceId`가 겹치지 않고 P05 동시 가동·최근접 충전, P06 3기 설치·회수·블록 PDC 복원이 중복·유실 0 |
| `E2E-56` | Day10/20/30/40 말뚝 조합과 전장 후보 | 모든 3개 조합을 결정 순서로 검사하고 Day10 20~42m·Day20 24~46m·Day40 중심 12~20m를 준수, 유효 조합 없으면 호출 소비 0 |
| `E2E-57` | 스킬 64개 schema·catalog·시전 전수 | 각 ID의 profile/target/cost/parameter/failure가 기획 입력과 일치, child 고아·자연어 추론·실패 소비 0 |
| `E2E-58` | 증강 66개 trigger·ICD·상한·재접속 전수 | 각 고유 opcode와 stateScope 보존, 파생 효과의 재트리거·보상·기여 생성 0 |
| `E2E-59` | 적 행동 69개 선택·실행·정리 전수 | `*-PRIMARY` 0, 숫자/조건 cooldown mode 해석, owner 53과 child 참조 일치, D18 추종체 보상·표본·기여 0 |
| `E2E-60` | 재생 신호 코어 제작·10초 의식·중단·재시작·전멸 | Day40/Final 진행 예비를 해치면 제작 거부, Day33/연구/시설/대상별 1회·안전 조건을 지키고 성공 때만 코어 소비·복귀 1회, 중단 반환·전멸 거부 |

## 12. 배포·롤백 인계

1. r2 번들을 별도 경로에 빌드한다.
2. `/ws admin content validate ws-content-r2.1`와 자동 L0을 실행한다.
3. L1~L3 보고서를 후보 레지스트리에 연결한다.
4. 신규 회차 포인터만 원자 교체한다.
5. canary 회차를 관측하고 치명 지표가 있으면 포인터를 r1 또는 직전 승인 r2 patch로 돌린다.
6. 기존 r2 회차의 잠금 튜플은 바꾸지 않는다.

## 13. 인계 완료 상태

소모품은 Day 단위 한도를 Encounter·보스·5초 개인 교전 범위로 교체했다. 해독·냉각·지혈의 제한 중첩 제거, 신경 안정제 단일 제거와 HARD_CC 자기 사용 거부, 냉각 연고의 5초 BURN 지속시간 감소를 실행한다. 야전 배급팩은 비전투 8초·전투 12초 동안 실제 아이템 1개를 예약하고 좌클릭·접속 종료·서버 종료 취소 시 반환한다. AP 자극제는 즉시 AP +10 뒤 20틱 간격 +3을 5회 적용하며 남은 횟수와 다음 펄스까지 남은 서버 틱을 회차 상태에 저장하고 지속 중 재사용을 거부한다. 기본 구조 고정대 중단 임계는 +10%, 강화형은 +25%, 정화 앰풀 개인 오염은 -15로 잠겼다. 개인 오염과 구조 고정대의 첫 유효 구조 틱 예약·소비 런타임은 아직 미구현이다.

현재 신규 일반 회차는 `SEASON_1/ws-content-r2`를 사용한다. P0.5~승격 전까지 이 동작을 유지하고, `ws-content-r2.1`이 승인되면 새 회차 기본 포인터만 r2.1로 교체한다. 기존 `PROTOTYPE/ws-prototype-r1`과 `SEASON_1/ws-content-r2` 저장소는 자동 변환 없이 각 잠금 리비전으로 복구하며, Test Lab은 기존 r1/r2 복구와 신규 r2.1 생성을 허용한다. 복구 가능한 저장소가 둘 이상이면 임의 선택하지 않고 안전 정지한다.

비장비 아이템 생성기는 `item-s1-r2`의 카테고리별 행 계약을 사용한다. 종전처럼 소모품 열 위치를 휴대 장치·시설 키트·호출품에 재사용하지 않으며, 61개 모두 `textKey/customModelKey/ownership/usePolicy/recipeId`를 가진다. 데이터·스키마·L0 회귀 검증은 통과했고 일반 화살 원장과 휴대 장치 개별 인스턴스는 부분 실행되지만, 특수 탄약·휴대 장치 고유 효과·ArenaManifest·호출 트랜잭션은 `ITEM-RUNTIME-STATUS-001`에 따라 아직 미완료다.

일반 화살 묶음은 slot1~8 우클릭으로 현재 스택의 발 수를 개인 탄약 원장에 입금한다. 활·석궁 기본 공격과 액티브는 원장을 바닐라 화살보다 먼저 1발 소비하고, 석궁 순간 장전은 2발을 소비한다. HUD는 원장 잔량과 석궁 탄창을 분리 표시한다. 특수 탄약 4종의 호환 무기·PEN·브레이크·정화·안정화 수치는 `PRODUCTION-DATA-CLOSURE-001`로 잠겼지만 명시 데이터 필드와 실행기는 아직 없다.

2026-09-15 P1 비용 감사에서 일반 공격·무기 액티브·삼지창 전환의 AP, 일반 화살 원장/석궁 탄창, 주무기 내구, 개인 교전 범위를 한 분리 후보 회차로 통합했다. 일반 화살 묶음 입금과 PDC 없는 바닐라 화살 폴백은 변경 후 절대 인벤토리 수량을 먼저 저장하고 물리 변경과 Bukkit playerdata 저장 뒤 체크포인트를 제거한다. 재접속은 남은 체크포인트를 멱등 복구한다. 탄창 5~6발에서 순간 장전은 AP·2발을 소비하지 않는다. F 방어도 시작 AP, 패링 환급/가드 충격 AP와 방패 내구를 각각 원자 후보로 커밋한다. 자동 정책·마이그레이션 회귀는 통과했으나 실제 우클릭·발사·재접속·JVM 종료 증거 전에는 탄약과 P1을 `VERIFIED/PASSED`로 승격하지 않는다.

2026-09-15 실제 클라이언트 F 행렬은 정면 `PARRIED`, 180도 후방 `HIT`, 단기 방어 `GUARDED`, 저 AP `broken=true`를 통과했고 각 성공 방어에서 방패 내구와 AP 충격이 같은 권위 결과로 저장됐다. 연구 상태 재계산은 `READY` 이상을 강등하지 않으며 Test Lab 증거 강제 상태도 GUI 진입 뒤 유지한다. 개인/공용 비용 재접속 및 실제 JVM 종료 증거가 남아 있으므로 P1 전체 상태는 아직 `IMPLEMENTED_AWAITING_CLIENT_EVIDENCE`다.

정지 Test Lab heartbeat는 마지막 성공 스냅샷과 상태가 같으면 디스크 쓰기와 version 증가를 생략한다. 이 보정은 무변경 5초 주기 쓰기가 Windows 파일 I/O에서 20초 이상 서버 스레드를 막은 워치독 사건을 폐쇄한다. 명시적 RCOST/FCOST·전투 비용 커밋과 종료 저장은 계속 동기 원자 저장을 사용하며, 변경된 heartbeat만 다음 자동 저장에서 기록한다. 회귀와 재배포 후 정지 회차의 3개 이상 저장 주기에서 파일·version 불변 및 워치독 0건을 확인했다.

`P1-CLIENT-COST-002`는 실제 클라이언트에서 통과했다. 개인 연구는 목재 3의 단일 예약과 `PROCESSING/0%`, 공용 FAC-S16 Lv2는 목재 7·철 9·레드스톤 9·마력 결정 1의 단일 커밋과 `COMPLETED` 작업을 각각 일반 재접속 전후 동일하게 복구했다. 개인/공용 클릭 오선택 실패에는 차감·거래가 없었고, 공용 GUI는 재접속 뒤 `Lv 2/5 · ACTIVE`와 HP `5060/5060`을 표시했다. P1 전체 승격은 실제 JVM 종료 경계인 `P1-PROCESS-KILL-003` 통과 전까지 금지한다.

`P1-PROCESS-KILL-003`도 2026-09-15 격리 Paper 서버와 `non2error` 클라이언트에서 통과했다. AP 자극제 K0~K5를 각각 `AP/잔여 펄스=30/5, 33/4, 36/3, 39/2, 42/1, 45/0`의 내구 저장 직후 실제 JVM 강제 종료했으며, 매 재시작은 AP 45·잔여 0·퀵 수량 0·범위별 사용 1회로 수렴하고 재접속 실제 인벤토리에 복제나 잔존을 만들지 않았다. 비용 `PROCESSING`은 `RS-D01-SAMPLE`의 목재 3 예약을, 비용 `RESERVED`는 `RS-D03-SEPARATION`의 개인 목재 5·철 2 예약을 종료 훅 없이 복구했다. 후자는 `QUEUED/RESERVED/PERSONAL/attempt 1`에서 재시작 뒤 `PROCESSING/PROCESSING`, 같은 예약 수량·잔액 0·시도 1회로 전이해 이중 차감이 없었고, 재접속 GUI도 `진행 0% · PROCESSING`을 표시했다. 따라서 P1 인계 상태는 `P1_PASSED`, 다음 실행 게이트는 `P2_READY`다. Season 1 전체 인계 상태 `IMPLEMENTATION_IN_PROGRESS`와 Story `DEFERRED_BY_USER`는 유지한다.

P2 구현 후보는 개인 PDC 자원과 회차 개인 원장을 단일 권위로 통합하고, 연구·시설 비용 예약/취소와 FAC-S16 입출금을 물리 수량 체크포인트까지 연결했다. Craft 해금의 원목 4개, 3×3 제작 입력 예약, 고정 출력 서명, 일반 아이템 목표 수량, 자원 원장 출력, 장비 고정 instanceId 지급, 저장소 아이템 소비·시설 상태를 각각 내구 우선·물리 후반영 순서로 전환했다. 미완료 `CRAFT` 거래는 소유자 접속 시 같은 출력 서명으로 재개한다. 자동 검증은 51 suite·184 test와 세 콘텐츠 번들 검증을 통과했지만 `E2E-14~20` 및 실제 JVM 중단 증거가 없으므로 P2는 `IMPLEMENTED_AWAITING_CLIENT_EVIDENCE`, 전체 인계는 계속 `IMPLEMENTATION_IN_PROGRESS`다.

휴대 장치는 최초 사용 또는 배치 시 `portableInstanceId`를 얻는다. P05는 아이템별 런타임 인스턴스로 동시에 가동할 수 있고 충전은 보유자가 가동한 최근접 인스턴스에 적용한다. P06은 스택을 유지하되 배치할 때마다 새 ID를 발급하므로 한 플레이어도 말뚝 3개 이상을 설치할 수 있다. 실제 블록 입력·재시작 복원은 `E2E-55` 전까지 완료로 간주하지 않는다.

`ArenaManifestPolicy`는 활성 P06의 모든 3개 조합을 ID 순서로 검사하고 같은 월드·유한 좌표·Day별 거리 계약을 통과하는 첫 조합과 무게중심을 고른다. 이 결과는 아직 지형 안전 통과 전 `ArenaCandidate`이므로 호출품을 소비하거나 정식 Manifest를 저장하지 않는다.

현재 인계는 `IMPLEMENTATION_IN_PROGRESS`다. r2 70파일과 L0 validator, 21개 상태 권위와 17개 실행 기준선의 공통 인스턴스 런타임, TAUNT의 같은 파티 거부·플레이어 공격 대상 제한·일반 적 AI 우선 대상, slot0 중재·서버 내구도·BROKEN·파손 호환 이벤트, 10개 무기군, 214개 장비의 슬롯 정규화와 GUI 장착·동기화, 334칸 생산 도감 로더까지 구현됐다. 사망 런타임은 부상 1~3, 1.5초 빈사 보호, 별도 빈사 체력과 피해 배율·타격 상한, 최대 2명의 대상별 공유 구조 진행, 100%+60% 기여, 각자 초당 5 AP, 0.5초 유예와 진행 감소, 5/7/9초 기준, 30/25/20% 복귀, 2초 80%+1초 30% 회복 보호와 CC/DOT 면역, 부상 3 상태의 다음 치명 피해 완전 사망까지 구현됐다. 빈사 웅크리기 도움 요청은 10초 재사용·파티 거리/방향·3초 표시로 실행되며, 활성 플레이어의 최근 안전 위치를 1초마다 저장해 첫 공허 치명 피해만 안전 이동 후 빈사로 바꾼다. 빈사 일반 로그아웃은 다음 서버 틱 완전 사망시키되 서버 종료 시 예약 작업은 실행하지 않아 저장된 빈사를 복원한다. 빈사 이동은 활성 속도의 20%, 보호 중 이동 0, 사다리·수영·넉백·탈것·순간이동 예외까지 구현했고, 완전 사망 관전은 평시 48/32m와 보스·Final 전장 반경으로 제한한다. `DOWNED_EXECUTOR`가 붙은 붉은 처형자는 보호 종료 빈사자를 우선하고, 일반 적은 공격 가능한 생존자가 없을 때만 빈사자를 고른다. 비보스 Day는 모든 잔존 전투 엔티티가 해소된 원자 `DAY_RESOLVED`에서 생존자의 부상을 1 감소시키며, 보스 Day에는 자동 회복하지 않는다. 완전 사망은 `DEAD_PENDING` 뒤 인벤토리·가상 장비 불변 스냅샷을 먼저 저장하고, 일반 재료·장비 instanceId/내구/BROKEN을 보존하며 소모품 스택별 25% 내림 손실을 적용한다. 플러그인 관리 유품 표식과 페이지 GUI, 생존 파티원 권한, 파괴·조작 차단, 전달 토큰 기반 장애 복구 회수까지 구현됐다. FAC-S11 치료 비용은 `환자당 생존 1`의 구체 WSR ID가 없어 잠겨 있다. 희귀 부활은 등록된 실제 아이템·장비 효과가 없어 아직 완료가 아니다. 시설은 FAC-C03 9칸 원형 보관·동시 편집 잠금, FAC-S09 증강 GUI 연결, 함정 잔여 횟수 표시, R01/R02 서버 가동 시간 작업과 R06 하위 준비 검사를 갖췄고 제작 즉시 재건 완료 우회를 제거했다. 제작은 315개 레시피 전부의 정규 9칸 입력, 수량·태그 가치·비소비 증명·소비 슬롯을 헤드리스 전수 검증했고, 동일 배열 후보 순환과 잔여 수량 변경 뒤 선택 ID 유지, GUI 종료 반환 공간 부족 시 본인 전용 드롭 보존까지 구현했다. 콘텐츠 생성기는 70파일·68 manifest 항목을 재생성하며 10상태 연구·21상태 권위 파일을 보존하도록 동기화됐다. 2026-08-24 최신 `candidate` JAR을 Paper 26.1.2 빌드 74·25566 격리 서버에서 활성화해 런타임 `70/334/315/64/66/21` 검증, 정상 플러그인 비활성화와 3개 차원 저장을 통과했다. 제작의 실제 클라이언트 중복 클릭·장애 중간점·outbox, 생산 장비 고유 효과·64개 스킬·66개 증강·상태별 특수 동작·91개 엔티티·46개 시설의 의미 런타임, Paper 멀티플레이·부하 테스트는 아직 완료로 표시하지 않는다. `REFORGE`, `GRAVE_RECOVERY`, `POWER_DISTRIBUTE`, `TAUNT_BEACON`, `FAC-S11 MEDICAL`의 구체 비용 ID와 R03/R04/R05 시험 성공 증거 계약은 `BLOCKED_DATA`다. 실제 클라이언트가 필요한 블록 설치·채굴·제작 강제 종료·유품 클릭·회수·빈사 입력 판정은 `BLOCKED_E2E`이며 콘솔 명령으로 대체 승인하지 않는다.

위 현황 문단의 데이터 차단 판정은 2026-08-24 `PRODUCTION-DATA-CLOSURE-001` 이후 다음처럼 정정한다. `FAC-S11`은 `WSR-RATION×1`, `REFORGE/GRAVE_RECOVERY/TAUNT_BEACON`은 각각 `WSR-REDSTONE×1/2/2`, `POWER_DISTRIBUTE`는 `WSR-COAL×5` 배치로 잠겼다. R03/R04/R05 시험 증거도 동 문서 6장으로 잠겼으므로 이 항목들은 `BLOCKED_DATA`가 아니라 `RUNTIME_MISSING`이다. 부활 정책은 `REVIVAL_ITEM`, Story 작성 방식은 `CHAPTER_REVIEW`로 사용자 잠금됐다. 부활 아이템·레시피·진행 예비 정책과 64/66/69 실행 데이터는 `ws-content-r2.1` 후보에 투영되어 `L0_VALIDATED`이며 실제 의식·효과·행동 실행은 `RUNTIME_MISSING`이다. Story 원문은 전체 플레이 가능 판정 뒤까지 `DEFERRED_BY_USER`, 실제 클라이언트 입력·표현은 계속 `BLOCKED_E2E`다.
