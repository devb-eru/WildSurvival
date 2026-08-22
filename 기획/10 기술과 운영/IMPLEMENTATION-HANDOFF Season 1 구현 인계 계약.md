# WildSurvival Season 1 구현 인계 계약

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `IMPLEMENTATION-HANDOFF-001` |
| 상태 | `IMPLEMENTATION_CONTRACT` |
| 인계 입력 | `DOC-AUTHORITY-001`, `DATA-REVISION-002`, G1~G3 권위 문서 |
| 구현 기준 | Minecraft Java, Paper 서버 플러그인, 바닐라 클라이언트, 모드 금지 |
| 외부 의존 | MagicSpells 비필수; S1 기본 백엔드는 네이티브 Paper |
| 실행 로드맵 | `DEV-ROADMAP-001` 프로토타입 우선 단계·승인 게이트 |
| 최종 수정일 | 2026-08-21 |

## 1. 인계 범위

이 계약은 모듈 경계, 저장·이벤트·테스트·배포 책임과 IMP 최종 범위를 고정한다. 실제 구현 순서·프로토타입 범위·단계 승인 게이트는 `DEV-ROADMAP-001`을 따른다. 구현 중 기획서에 없는 수치·콘텐츠를 임의 창작하지 않는다. 모호함은 `DOC-AUTHORITY-001`에 이슈로 등록하고 기존 불변식을 보존하는 안전 폴백을 사용한다.

## 2. 기술 불변식

- 클라이언트 모드는 사용하지 않는다.
- 모든 핵심 판정·수치·보상·장착은 서버 권위다.
- 커스텀 GUI 외 직접 장착 변경은 다음 틱에 권위 장비로 복원하고 감사 로그를 남긴다.
- 주무기 실제 슬롯은 `0`, 보조무기는 `-106`이다. 둘이 비면 권투를 자동 활성화한다.
- 곡괭이는 전투 표적 판정 우선, 삼지창은 바닐라 피해 취소 후 커스텀 투척·회수, 단검은 검·둔기는 철퇴 기반이다.
- 모든 파티는 한 팀이며 PvP·경쟁 보상·개인 승리 조건을 만들지 않는다.
- 신규 회차는 2~4인, 회차 중 신규 참가 금지, 유일 생존자 1인 지속은 허용한다.
- Day 50 전에 최종 목표를 완료할 수 없다.

## 3. 모듈 경계

| 모듈 | 소유 상태 | 발행 이벤트 | 금지 |
|---|---|---|---|
| `content` | 리비전·manifest·레지스트리 | `CONTENT_VALIDATED` | 부분 번들 활성 |
| `run` | 회차·Day·난이도·인원 스냅샷 | `DAY_STARTED`, `RUN_ENDED` | 콘텐츠 수치 직접 변경 |
| `player` | 생존·다운·사망·장착 의미 슬롯 | `PLAYER_STATE_CHANGED` | 보상 직접 지급 |
| `combat` | AP·회피·패링·상태·브레이크·패턴 | `COMBAT_RESULT` | Story 결과 승인 |
| `growth` | EXP·레벨·개인/파티 증강 드로우 | `MILESTONE_LOCKED` | 등급 재추첨 |
| `economy` | 자원·제작·장비·보상 원장 | `LEDGER_COMMITTED` | 클라이언트 수치 신뢰 |
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
| `IMP-001` | r2 schema·loader·manifest | DATA-REVISION-002 | 47파일 번들 골격·validator | L0 |
| `IMP-002` | 회차 잠금·DB migration | TECH-001, GAME-001~003 | 잠금 튜플·복구 | 재시작 fixture |
| `IMP-003` | 통합 GUI·HUD | UX-001 | 화면·PENDING·접근성 | GUI 상태 테스트 |
| `IMP-004` | 장착·10무기 실행 | EQUIP-001, WEAPON-002 | 슬롯 동기화·실행기 | 무기 판정 10종 |
| `IMP-005` | 상태·브레이크·패턴 | STATUS/BREAK/COMBAT | 전투 파이프라인 | 순서·상한 테스트 |
| `IMP-006` | 성장·드로우 | PROG/AUG/PARTY-SYNERGY | 마일스톤 잠금·후보 | 결정론 fixture |
| `IMP-007` | 자원·제작·장비 원장 | RESOURCE/EQUIP/ECONOMY | 거래·예약·NEED | 멱등·음수 차단 |
| `IMP-008` | 시설·연구·발견 | FACILITY/RESEARCH/DISC | 큐·네트워크·도감 | 소프트락 fixture |
| `IMP-009` | Day1~50 사건·적 | EVENT/ENEMY DATA | 스케줄러·웨이브 | Day 누락 0 |
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
| 삼지창 | 발사 이벤트 취소, 표시 엔티티/투사체 추적, 서버 충돌·회수 |
| 상태·브레이크 | 서버 틱 상태 컨테이너와 전용 BossBar |
| 오염 | 청크 메타데이터·블록 변경 큐, 월드 손상 제한 |
| 구조·시설 | 보호된 월드 오브젝트와 GUI 상호작용 |

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

## 12. 배포·롤백 인계

1. r2 번들을 별도 경로에 빌드한다.
2. `/ws admin content validate ws-content-r2`와 자동 L0을 실행한다.
3. L1~L3 보고서를 후보 레지스트리에 연결한다.
4. 신규 회차 포인터만 원자 교체한다.
5. canary 회차를 관측하고 치명 지표가 있으면 포인터를 r1 또는 직전 승인 r2 patch로 돌린다.
6. 기존 r2 회차의 잠금 튜플은 바꾸지 않는다.

## 13. 인계 완료 상태

현재 기획 인계는 `CONTRACT_READY`다. 소스 코드·r2 런타임 JSON·실제 부하 테스트는 아직 구현 산출물이며 완료로 표시하지 않는다. 구현자는 `DEV-ROADMAP-001`의 `DEV-000`을 먼저 수행하고, `G-400` 전에는 프로토타입 범위를 넘겨 콘텐츠를 확장하지 않는다.
