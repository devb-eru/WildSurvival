# WildSurvival STORY-DATA Season 1 런타임 데이터 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `STORY-DATA-S1-001` |
| 상태 | `DATA_LOCKED` |
| Story 리비전 | `ws-story-s1-r1` |
| 상위 기준 | `STORY-001`, `UX-001`, `FINAL-DATA-001` |
| 연계 | `DISC-LIST-001`, `BOSS-001~004`, `EVENT-DATA-D20-001`, `EVENT-DATA-D50-001` |
| 적용 범위 | 프롤로그, Day 1~50, 보스 4기, Final, 에필로그, 선택 기록, 이관 스냅샷 |
| 최종 수정일 | 2026-08-21 |

## 1. 런타임 원칙

- Story는 진행·전투·보상·완료의 소비자다. 장면 실패가 원천 트랜잭션을 롤백하거나 성공을 승인하지 않는다.
- 장면은 시스템 ID만 참조하고 새 전투·보상·발견 조건을 만들지 않는다.
- 전투 중 핵심 대사는 2줄 이하, 나머지는 큐와 기록 보관함에 둔다.
- `FULL/REDUCED`는 개인 표현 자산만 바꾸며 사실·대사·판정·난이도·보상은 같다.
- `EMPTY`는 계속 유효한 무내용 폴백이다. r2 신규 회차는 `ws-story-s1-r1`, 기존 r1 회차는 잠긴 `EMPTY`를 유지할 수 있다.

## 2. 장면 스키마

```yaml
scene:
  scene-id: "ST3-D30-CORRUPTION-HEART-AFTER"
  revision: "ws-story-s1-r1"
  trigger:
    event: "BOSS_REWARD_STEP_COMMITTED"
    ref: "B30-RW-PART-C"
  audience: PARTY
  priority: STORY_MAJOR
  blocking: false
  replayable-text: true
  world-effect-replayable: false
  payload-key: "story.s1.ch3.d30.after"
  fallback-text: "부품 C: 정화 판단 렌즈. 중앙 판단과 생존자 판단이 불일치합니다."
  presentation:
    full: "PRES-ST3-D30-FULL"
    reduced: "PRES-ST3-D30-REDUCED"
  idempotency-scope: RUN
```

필수 필드: `sceneId`, `trigger.event`, `trigger.ref`, `audience`, `priority`, `blocking`, `payloadKey`, `fallbackText`, `replayableText`, `presentation`, `idempotencyScope`.

## 3. 프롤로그

| Scene ID | 트리거 | 페이로드 | 종료·폴백 |
|---|---|---|---|
| `ST0-PROLOGUE-WAKE` | `RUN_STATE_CHANGED:ACTIVE` | 검은 화면→월드 페이드, “세계 기록을 찾을 수 없습니다.” | 연출 실패 시 채팅 4줄 |
| `ST0-PROLOGUE-SIGNALS` | `REGISTERED_MEMBERS_SPAWNED` | 파티 방향 5초, 생존 신호 수 등록 | 순간이동·자원 지급 없음 |
| `ST0-PROLOGUE-ARK0` | `ITEM_VIEWED:ARK0_COMPASS` | ARK-0 목적·첫 목표 | 나침반 없어도 `/ws story`에서 재생 |
| `ST0-PROLOGUE-END` | `LEDGER_FIRST_SHARED_SURVIVAL_MATERIAL` | C01 표시, “살아 있는 신호가 하나가 아닙니다.” | 장비·EXP 없음 |

## 4. Day 1~10 장면 원장

| Scene ID | 주 트리거 | payloadKey | 안전 폴백 트리거 |
|---|---|---|---|
| `ST1-D01-TRACE` | `DISCOVERY:C01:DISCOVERED` | `story.s1.ch1.d01` | Day1 종료 시 단서 버전 |
| `ST1-D02-BLACK-DEW` | `DISCOVERY:C04:CLUE_FOUND` | `story.s1.ch1.d02` | Day2 종료, 사실 미확정 표현 |
| `ST1-D03-SELECTIVE-RESPONSE` | `PERSONAL_AUGMENT_SELECTED:LEVEL_3` | `story.s1.ch1.d03` | 선택 PENDING이면 완료 뒤 |
| `ST1-D04-COST-OF-LIGHT` | `FACILITY_THREAT_FIRST_INCREASED` | `story.s1.ch1.d04` | Day4 첫 압박 전조 |
| `ST1-D05-HUNTING-SHELL` | `ENEMY_GRADE_FIRST_SEEN:ENHANCED` | `story.s1.ch1.d05` | Day5 압박 정산 |
| `ST1-D06-BROKEN-GRAMMAR` | `DISCOVERY:C05:DISCOVERED` | `story.s1.ch1.d06` | Day6 종료 기록 보관함 |
| `ST1-D07-THREE-DIRECTIONS` | `DISCOVERY:C06:DISCOVERED` | `story.s1.ch1.d07` | Day7 종료, ARBOR 명칭 공개 유지 |
| `ST1-D08-WEAKNESS-REMEMBERS` | `ELITE_PATTERN_ANALYZED` | `story.s1.ch1.d08` | Day8 정예 정산 |
| `ST1-D09-NOT-A-RESPONSE` | `BOSS_SIGNAL:DAY_10:TELEGRAPHED` | `story.s1.ch1.d09` | Day9 종료 보스 전조 |
| `ST1-D10-PURSUER-BEFORE` | `BOSS_STATE:BOSS-001:ACTIVE` | `story.s1.ch1.d10.before` | 전투 중 2줄 |
| `ST1-D10-PURSUER-AFTER` | `BOSS_REWARD:PART_A:COMMITTED` | `story.s1.ch1.d10.after` | 정산 뒤 안전 큐 |

## 5. Day 11~20 장면 원장

| Scene ID | 주 트리거 | payloadKey | 안전 폴백 |
|---|---|---|---|
| `ST2-D11-REMAINING-RESPONSE` | `DISCOVERY:C08:CLUE_FOUND` | `story.s1.ch2.d11` | EV20-11 완료/실패 기록 |
| `ST2-D12-STITCHED-TERMINATION` | `STATUS_REACTION_FIRST_RECORDED` | `story.s1.ch2.d12` | Day12 정산 |
| `ST2-D13-DISTINCTION` | `DISCOVERY:C09:DISCOVERED` | `story.s1.ch2.d13` | 상태 분류판 제작 |
| `ST2-D14-RESCUER-MARK` | `DISCOVERY:C10:CLUE_FOUND` | `story.s1.ch2.d14` | EV20-14 기록 |
| `ST2-D15-LEARNED-TIER` | `AUGMENT_TIER_LOCKED:LEVEL_15` | `story.s1.ch2.d15` | 잠금 커밋 뒤 1회 |
| `ST2-D16-BREATHING-CLOUD` | `EVENT:EV20-16-BREATHING-FOG:ACTIVE` | `story.s1.ch2.d16` | 사건 기록 보관함 |
| `ST2-D17-EMPTY-HANDS` | `EVENT:EV20-17-SILENT-HANDS:OBJECTIVE` | `story.s1.ch2.d17` | Day17 압박 후 |
| `ST2-D18-THREE-SECONDS` | `DISCOVERY:C12:DISCOVERED` | `story.s1.ch2.d18` | 정예 분석 완료 |
| `ST2-D19-LETTING-GO` | `DISCOVERY:C13:DISCOVERED` | `story.s1.ch2.d19` | 보스 신호 전조 |
| `ST2-D20-AMALGAM-BEFORE` | `BOSS_STATE:BOSS-002:ACTIVE` | `story.s1.ch2.d20.before` | 전투 핵심 2줄 |
| `ST2-D20-AMALGAM-AFTER` | `BOSS_REWARD:PART_B:COMMITTED` | `story.s1.ch2.d20.after` | 정산 큐 |

## 6. Day 21~30 장면 원장

| Scene ID | 주 트리거 | payloadKey | 안전 폴백 |
|---|---|---|---|
| `ST3-D21-MOVING-BORDER` | `DISCOVERY:C14:CLUE_FOUND` | `story.s1.ch3.d21` | EV50-D21 기록 |
| `ST3-D22-SAME-BODY-DIFFERENT-ANSWER` | `SKILL:ED50-MIMIC-MUTATION:OBSERVED` | `story.s1.ch3.d22` | Day22 정산 |
| `ST3-D23-EATING-SAFETY` | `EVENT:EV50-D23-PURIFIER-PARASITE:ACTIVE` | `story.s1.ch3.d23` | C15~17 첫 상태 |
| `ST3-D24-STORED-HISTORY` | `EVENT:EV50-D24-LEDGER-TRACK:MARKED` | `story.s1.ch3.d24` | 사건 종료 |
| `ST3-D25-ARBOR` | `RESEARCH:RS-D23-MUTATION-MAP:UNLOCKED` | `story.s1.ch3.d25` | Day25 종료, ARBOR 진실 공개 |
| `ST3-D26-MISSING-CONSENSUS` | `DISCOVERY:C18:DISCOVERED` | `story.s1.ch3.d26` | 사건 비교 완료 |
| `ST3-D27-PURIFY-JAM` | `DISCOVERY:C19:DISCOVERED` | `story.s1.ch3.d27` | 균열 안정화 |
| `ST3-D28-COST` | `ABYSSAL_COST_PREVIEWED` | `story.s1.ch3.d28` | 선택 강제 없음 |
| `ST3-D29-WHAT-NOT-TO-FIX` | `DISCOVERY:C20:DISCOVERED` | `story.s1.ch3.d29` | 호출 준비 완료 |
| `ST3-D30-HEART-BEFORE` | `BOSS_STATE:BOSS-003:ACTIVE` | `story.s1.ch3.d30.before` | 전투 핵심 2줄 |
| `ST3-D30-HEART-AFTER` | `BOSS_REWARD_STEP:B30-RW-PART-C` | `story.s1.ch3.d30.after` | 정산 큐 |

## 7. Day 31~40 장면 원장

| Scene ID | 주 트리거 | payloadKey | 안전 폴백 |
|---|---|---|---|
| `ST4-D31-BLACKOUT` | `DISCOVERY:C21:DISCOVERED` | `story.s1.ch4.d31` | Day31 기록 보관함 |
| `ST4-D32-INTERRUPTIBLE-ORDER` | `PATTERN_TAGS_ALL_OBSERVED` | `story.s1.ch4.d32` | EV50-D32 완료 |
| `ST4-D33-WHAT-COULD-NOT-BREAK` | `SKILL:ED50-FACILITY-BORE:OBSERVED` | `story.s1.ch4.d33` | 공성 사건 종료 |
| `ST4-D34-REFUSE-SAME-ANSWER` | `METHOD_DECAY_TRIGGERED` | `story.s1.ch4.d34` | EV50-D34 종료 |
| `ST4-D35-FOUR-PIECES` | `LEVEL_REACHED:35` | `story.s1.ch4.d35` | 안전 큐 |
| `ST4-D36-WHAT-REMAINS` | `PARTIAL_INTERRUPT_FIRST_SUCCESS` | `story.s1.ch4.d36` | 잔류 장판 기록 |
| `ST4-D37-DIFFERENT-HANDS` | `INTERRUPT_METHODS_DISTINCT:3` | `story.s1.ch4.d37` | 사건 종료 |
| `ST4-D38-BLACKOUT-EXECUTOR` | `ENEMY:EN-D38-E01:ANALYZED` | `story.s1.ch4.d38` | 정예 정산 |
| `ST4-D39-LAST-BLOCK` | `DISCOVERY:C26:DISCOVERED` | `story.s1.ch4.d39` | 호출 준비 |
| `ST4-D40-DEMOLISHER-BEFORE` | `BOSS_STATE:BOSS-004:ACTIVE` | `story.s1.ch4.d40.before` | 전투 핵심 2줄 |
| `ST4-D40-DEMOLISHER-AFTER` | `BOSS_REWARD_STEP:B40-RW-PART-D` | `story.s1.ch4.d40.after` | 정산 큐 |

## 8. Day 41~50 장면 원장

| Scene ID | 주 트리거 | payloadKey | 안전 폴백 |
|---|---|---|---|
| `ST5-D41-FOUR-AUTHORITIES` | `DISCOVERY:C27:DISCOVERED` | `story.s1.ch5.d41` | A~D 연구 첫 완료 |
| `ST5-D42-ONE-HUNDRED-PERCENT` | `WORLD_CORRUPTION_REACHED:100` | `story.s1.ch5.d42` | Day42 종료, 사실 기록 |
| `ST5-D43-KEEP-CONTRADICTION` | `ENEMY_AUGMENT_CONFLICT_REJECTED` | `story.s1.ch5.d43` | EV50-D43 기록 |
| `ST5-D44-PLACE-TO-RETURN` | `EVENT:EV50-D44-SPLIT-RETURN:COMPLETED` | `story.s1.ch5.d44` | 시설/유목 변형 선택 |
| `ST5-D45-BEFORE-LAST-CHOICE` | `LEVEL_REACHED:45` | `story.s1.ch5.d45` | 빌드 태그만 요약 |
| `ST5-D46-ECHO-OF-MEMORY` | `ENEMY:EN-D46-E01:DEFEATED` | `story.s1.ch5.d46` | 마지막 증강 뒤 |
| `ST5-D47-PEOPLE-WHO-CONNECT` | `EVENT:EV50-D47-RELAY-LINE:COMPLETED` | `story.s1.ch5.d47` | 이동/정착 변형 |
| `ST5-D48-BLACKOUT-COMPILATION` | `DISCOVERY:C28:COMPLETE` | `story.s1.ch5.d48` | 합본 기록 전체 큐 |
| `ST5-D49-FIFTIETH-MORNING` | `DISCOVERY:C29:COMPLETE` | `story.s1.ch5.d49` | 키 제작 뒤 준비 대사 |
| `ST5-D50-AVAILABLE` | `FINAL_STATE:AVAILABLE` | `story.s1.ch5.d50.available` | Day50 도달만으로 미재생 |

## 9. Final 장면 원장

| Scene ID | 트리거 | 우선순위·내용 | 다시 보기 |
|---|---|---|---|
| `ST5-FINAL-ACTIVATE` | `FINAL_STATE:ACTIVATING` | SYSTEM, 승인 투표 문구 | 텍스트 true |
| `ST5-FINAL-STAGE1-START` | `FINAL_STATE:ACTIVE_STAGE_1` | STORY_MAJOR, BLACKOUT 경계·세 교정점 | true |
| `ST5-FINAL-STAGE1-COMPLETE` | `FINAL_STAKES_ALL_LOCKED` | SYSTEM, 지면선 연결 | 텍스트 true/월드 false |
| `ST5-FINAL-CORE-ARRIVAL` | `FINAL_PHASE:F50-P1` | STORY_MAJOR, 핵 등장 문장 | true |
| `ST5-FINAL-FIRST-BREAK` | `FINAL_BOSS_BREAK_COUNT:1` | SYSTEM, 단일 승인 반박 | true |
| `ST5-FINAL-CORE-60` | `FINAL_BOSS_HP_BELOW:60` | STORY_MAJOR, 구조 기록 대조 | true |
| `ST5-FINAL-CORE-30` | `FINAL_BOSS_HP_BELOW:30` | STORY_MAJOR, 오류를 남길 권한 | true |
| `ST5-FINAL-CORE-SUBDUED` | `FINAL_BOSS:CORE_SUBDUED` | SYSTEM, 180초 유지 지시 | true |
| `ST5-FINAL-PURIFY-000` | `PURIFICATION_CHECKPOINT:0` | 서하 기록, 정화 판단 | true |
| `ST5-FINAL-PURIFY-060` | `PURIFICATION_CHECKPOINT:60` | 해원 기록, 생체 인증 | true |
| `ST5-FINAL-PURIFY-120` | `PURIFICATION_CHECKPOINT:120` | 도윤 기록, 협력 원장 | true |
| `ST5-FINAL-PURIFY-180` | `PURIFICATION_CHECKPOINT:180` | 2초 환경음, 승인 자료 완료 | true |
| `ST5-FINAL-CONFIRM` | `FINAL_CONFIRMATION_OPENED` | SYSTEM, “함께 다시 시작한다” | 선택 결과만 false |
| `ST5-FINAL-COMPLETE` | `FINAL_COMPLETION_STEP:F50-TX-05` | STORY_MAJOR, C30·첫 송신 | 텍스트 true/월드 false |
| `ST5-EPILOGUE-FIRST-EMBER` | `RUN_STATE:COMPLETED` | 결과 책·타이틀·응답 헤더 3개 | true |

- `ST5-FINAL-COMPLETE`는 `F50-TX-05` 성공 전 절대 실행하지 않는다.
- 마지막 선택 버튼은 Story가 아니라 Final 상태 머신에 명령을 제출한다. Story 스킵은 확정을 대신하지 않는다.
- 응답은 `CIVITAS-0`, `TIDE-12`, `MIRROR-NULL` 헤더만 저장하고 정체·선악·진위를 확정하지 않는다.

## 10. 선택 기록 데이터

| Log ID | 트리거 | payloadKey | 진행 영향 |
|---|---|---|---|
| `LOG-O01` | 첫 씨앗·농작물 보관 | `story.s1.log.o01` | 없음, 소량 기존 활동 EXP |
| `LOG-O02` | FAC-S11 첫 열람 | `story.s1.log.o02` | 없음 |
| `LOG-O03` | FAC-S17/중계 기록 | `story.s1.log.o03` | S2 권역 복선만 |
| `LOG-O04` | 제작 롤백 1회 | `story.s1.log.o04` | 도움말 |
| `LOG-O05` | 자연 관측 5종 | `story.s1.log.o05` | 도감 |
| `LOG-O06` | 접합 잔류물 분석 | `story.s1.log.o06` | 없음 |
| `LOG-O07` | FAC-R01 설계 열람 | `story.s1.log.o07` | S2 기관 복선만 |
| `LOG-O08` | 고오염 비적대 식생 | `story.s1.log.o08` | 정화 힌트 |
| `LOG-O09` | C29 교정 기록 | `story.s1.log.o09` | 엔딩 책 여백 |

선택 기록은 C01~C30·보스·Final을 완료하거나 대체하지 않는다.

## 11. FULL/REDUCED 표현 매핑

| Presentation ID | FULL | REDUCED | 유지 정보 |
|---|---|---|---|
| `PRES-ST2-D11` | 노출 조직·잘못 연결된 손가락 Display | 스컬크 케이블·붕대 묶음 | 생체 신호 중첩·표본 위치 |
| `PRES-ST2-D12` | 절단면·낭종 입자 | 갈라진 외피·연결선 | 독/열 두 범주 |
| `PRES-ST2-D14` | 잘못 붙은 팔의 구조 자세 | 갑옷 거치대 실루엣·결박선 | 구조자 추적 전조 |
| `PRES-ST2-D16` | 갈비뼈형 포자막·젖은 조직 | 스컬크 막·회색 포자 | 구름 열림·닫힘 타이밍 |
| `PRES-ST2-D20` | 여러 몸통·사지·턱 접합 외피 | 케이블·붕대·갑피 접합 | 소환·재접합·핵 약점 |
| `PRES-ST3-D22` | 얼굴·손·갑피가 자라 찢어짐 | 블록성 외피가 갈라져 교체 | 변이 축·교대 타이밍 |
| `PRES-ST3-D30` | 살·뿌리·광물 액화 | 오염 유체·뿌리·결정 | 섭식선·정화 렌즈 |
| `PRES-DEATH-TRACE` | 짧은 혈흔·장비 잔해 | 회색 입자·장비 표식 | 유품·구조 가능 여부 |

- 피격 범위, 약점, 브레이크, 상태, 구조 빔은 위 자산과 별도 `UX-001` 전조를 사용한다.
- 실제 플레이어 시신의 장시간 훼손 표현은 두 모드 모두 금지한다.

## 12. 큐·재접속·스킵·재생

| 상황 | 처리 |
|---|---|
| COMBAT/SYSTEM 우선 정보 활성 | Story 장면을 `queuedSceneIds`에 넣음 |
| 플레이어 접속 종료 | 파티 해금은 저장, 개인 재생은 안전 재접속 때 |
| Day 스킵 | 분위기 장면 텍스트만 해금, 미완 발견·보스 장면은 미해금 |
| 같은 장면 재트리거 | 최초 월드 연출·보상 없음, 텍스트 다시 보기만 |
| 장면 데이터 누락 | `fallbackText`, 없으면 일반 오류+진행 계속 |
| Story 저장 실패 | 원천 시스템 유지, `story-reconcile`이 사건 원장을 읽어 복구 |
| 완료 뒤 결과 책 공간 부족 | 가상 보관함·다시 보기, 바닥 드롭 없음 |

## 13. 저장 계약

```yaml
story-run:
  story-revision: ws-story-s1-r1
  unlocked-scenes: [ST0-PROLOGUE-WAKE, ST1-D01-TRACE]
  played-by-player:
    uuid-a: [ST0-PROLOGUE-WAKE]
  queued-by-player:
    uuid-a: [ST1-D01-TRACE]
  optional-logs: [LOG-O01]
  processed-trigger-ids: ["domain-event:run-01:184"]
  final-epilogue-snapshot-id: null
  season-bridge-snapshot-id: null
```

장면 해금은 회차, 재생은 플레이어 단위다. `processedTriggerIds`는 보상 멱등키와 분리한다.

## 14. `seasonBridgeSnapshot` 스키마

```yaml
season-bridge-snapshot:
  schema-version: 1
  snapshot-id: "s1-bridge:run-01:completion-id"
  source-run-id: "run-01"
  source-content-revision: "ws-content-r2"
  source-story-revision: "ws-story-s1-r1"
  completion-id: "completion:run-01"
  completion-day: 53
  registered-members:
    - {uuid: "uuid-a", final-life-state: ALIVE}
  party-style:
    settlement-ratio: 0.42
    relay-ratio: 0.58
    label: MIXED
  cooperation-facts:
    rescues: 18
    facility-recoveries: 4
    purification-valid-seconds: 180
    distinct-interrupt-methods: 4
  party-augments: [PAUG-003, PAUG-004, PAUG-008, PAUG-014]
  final-build-tag-summary: [BREAK, RESCUE, NOMAD]
  optional-logs: [LOG-O01, LOG-O03, LOG-O09]
  response-headers: [CIVITAS-0, TIDE-12, MIRROR-NULL]
  checksum: "sha256"
```

- 장비·레벨·스탯·자원·전투력 수치는 이관하지 않는다.
- 스냅샷 생성 실패는 S1 완료를 롤백하지 않는다. 완료 원장으로 동일 `snapshotId`를 재생성한다.
- 완료 뒤 플레이로 스냅샷 값을 바꾸지 않는다.

## 15. 테스트·완료 기준

- `T-STORY-001`: 프롤로그+Day1~50+Final+에필로그 Scene ID 유일성
- `T-STORY-002`: 모든 triggerRef가 실제 시스템·데이터 ID를 참조
- `T-STORY-003`: Day50 전 Final 질문·핵·C30·엔딩 장면 차단
- `T-STORY-004`: 전투 중 2줄 상한·큐 우선순위·안전 재생
- `T-STORY-005`: 재접속·Day 스킵·다시 보기에서 진행·보상 중복 없음
- `T-STORY-006`: FULL/REDUCED의 사실·판정·대사·난이도 동일
- `T-STORY-007`: `EMPTY`와 Story 오류에서 회차 정상 완주
- `T-STORY-008`: `F50-TX-05` 전 성공 연출 없음, TX-06 실패 독립
- `T-STORY-009`: seasonBridgeSnapshot 재생성·체크섬·전투력 미이관
- `T-STORY-010`: 응답 헤더 3개가 미확정 상태로 보존

모든 S1 장면이 실제 트리거·페이로드 키·폴백·표현·저장·재생 정책을 가지며 Story 실패가 시스템 결과를 변경하지 않으면 완료다.
