# WildSurvival Test Lab 솔로 테스트·튜닝 시스템 상세 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `TEST-LAB-001` |
| 상태 | `IMPLEMENTED_PROTOTYPE` |
| 상위 기준 | `DOC-AUTHORITY-001`, `DEV-ROADMAP-001`, `IMPLEMENTATION-HANDOFF-001` |
| 구현 경로 | `plugins/wsplugin/src/main/java/com/lsc/corp/wsplugin/testlab` |
| 적용 리비전 | `ws-prototype-r1` 전용 |
| 클라이언트 | 바닐라 Java Edition, 모드 없음 |
| 최종 수정일 | 2026-08-22 |

## 1. 목적

Test Lab은 한 명의 개발자 또는 밸런스 담당자가 협동 프로토타입의 각 시스템을 인게임에서 격리·조작·반복 검증하는 도구다. 일반 플레이 회차의 규칙을 느슨하게 바꾸는 관리자 치트가 아니라, `runType: TEST`와 별도 저장소를 사용하는 개발 전용 실행 환경이다.

핵심 목표는 다음과 같다.

1. 1인으로 채집·제작·장착·전투·상태·브레이크·증강·빈사·보스 시스템을 각각 준비한다.
2. 레벨·EXP·아이템·자원·스탯·피해 감소·AP·몬스터 수치를 정확한 값으로 바꾼다.
3. 가상 파티 규모와 기여 슬롯으로 2~4인 스케일링·협동 채널을 혼자 검증한다.
4. 변경 전 스냅샷, 되돌리기, 입장 전 플레이어 백업과 감사 로그로 테스트 오염을 차단한다.
5. 동일 시드·프리셋·시나리오·논리 시계로 결함을 재현한다.

## 2. 격리 불변식

- Test Lab 회차는 `runType: TEST`, `contentRevision: ws-prototype-r1`을 강제한다.
- 일반 프로토타입 저장은 `plugins/wsplugin/runs`, Test Lab 저장은 `plugins/wsplugin/test-lab/runs`를 사용한다.
- 일반 회차와 Test Lab 회차가 동시에 활성 상태면 서버 시작을 거부한다.
- Test Lab 입장 전 인벤토리·갑옷·오프핸드·선택 핫바·위치·게임 모드·체력·허기·총 EXP·포션·무적·비행·호흡·화상·핵심 속성을 별도 백업한다.
- 정상 종료 시 Test Lab 회차·테스트 아이템·테스트 개체를 폐기하고 입장 전 백업을 복원한다.
- 테스트 아이템은 `ws:test_item`, `ws:test_session` PDC를 가지며 일반 보상으로 승격하지 않는다.
- 일반 활성 회차가 있으면 Test Lab 진입을 허용하지 않는다.
- `test-lab.enabled` 기본값은 `false`다. 운영 서버에서 설정을 명시적으로 켜지 않으면 모든 진입을 거부한다.

## 3. 권한

| 권한 | 역할 | 기본값 |
|---|---|---|
| `wildsurvival.test.use` | 도움말·상태 확인 | OP |
| `wildsurvival.test.session` | Test Lab 입장·종료 | OP |
| `wildsurvival.test.mutate` | GUI와 모든 상태 변경 | OP |
| `wildsurvival.test.inspect` | 대상 검사·피해 미리보기·JSON 내보내기 | OP |

GUI를 연 뒤 권한이 회수되면 다음 클릭에서 GUI를 닫고 변경을 거부한다.

## 4. 회차 생명주기

```text
비활성
  → /ws test enter [seed] [virtualPartySize]
  → 입장 전 플레이어 백업 원자 저장
  → TEST 회차 생성·시작
  → SANDBOX 초기화·DEFAULT 프리셋
  → 변경 전 자동 스냅샷·감사
  → reset / undo / scenario 반복
  → /ws test exit <reason> --confirm
  → 태그 개체 정리·회차 archive
  → 입장 전 플레이어 백업 복원
  → 비활성
```

- Test Lab 입장 실패 시 생성 중인 TEST 회차를 중단·archive하고 백업을 즉시 복원한다.
- 서버가 비정상 종료되면 활성 TEST 회차와 백업을 유지한다. 재시작 시 소유자가 접속하면 속성·장비를 다시 동기화한다.
- 종료 중 중단되면 `ABORTED + restorePending: true` 회차도 복구 대상으로 다시 읽는다. 백업 복원 전에는 회차를 archive하지 않으며, 설정이 다시 비활성화돼도 소유자의 `exit --confirm` 복구는 허용한다.
- 고아 가상 파티 더미는 서버 시작 시 활성 TEST `runId`와 비교해 제거한다.

## 5. 스냅샷·프리셋·감사

### 5.1 스냅샷

- 모든 수치 변경 전 회차 JSON과 플레이어 런타임 상태·인벤토리를 저장한다.
- 기본 최대 보관 수는 20개이며 오래된 순서로 제거한다.
- `/ws test undo`는 가장 최근 스냅샷을 한 번 소비하는 LIFO 방식이다. 최신 파일을 먼저 비파괴로 읽고 회차 저장·플레이어 복원·감사 기록이 모두 성공한 뒤에만 소비하며, 중간 실패 시 같은 스냅샷을 다시 사용할 수 있어야 한다.
- 논리 회차 상태와 보스·시설 상태를 복원하고, 비영속 테스트 몬스터는 정리한다.

### 5.2 프리셋

| 프리셋 | 목적 |
|---|---|
| `DEFAULT` | 모든 배율 1, 1인, 시간 정지 |
| `GLASS-CANNON` | 주는 피해 x4, 받는 피해 x3, 최대 체력 10 |
| `TANK` | 최대 체력 100, 피해 감소 75%, 주는 피해 x0.75 |
| `NO-COOLDOWN` | 쿨다운 x0.05, AP 비용 0, AP 재생 x20 |
| `SURVIVABILITY` | 최대 체력 40, 피해 감소 50%, AP 재생 x3 |
| `PARTY-4` | 가상 4인 스케일링, 시간 정지 |

현재 값을 이름 있는 사용자 프리셋으로 저장·불러오기·삭제할 수 있다. 파일 ID는 경로 문자를 허용하지 않는다.

### 5.3 감사

모든 Test Lab 변경은 다음 정보를 `test-lab/audit.jsonl`과 기존 텔레메트리 감사 로그에 남긴다.

- 시각, `runId`, 실행자 UUID
- 명령/GUI 행동 ID
- 변경 전 스냅샷 또는 이전 값
- 변경 결과

## 6. 커스텀 GUI

`/ws test gui`는 54칸 바닐라 인벤토리 GUI로 다음 빠른 동작을 제공한다.

- 프리셋·시나리오 순환
- 가상 파티 1~4인과 논리 시간 정지
- 레벨·EXP·체력·AP·무적·피해 감소·주는 피해
- 자원·대표 무기·응급 배급·플레이어 상태 정화
- 일반 적·장갑 적·보스 소환, 보스 페이즈·패턴
- 조준 대상 검사·테스트 몬스터 정리
- 빈사 더미·가상 보스 기여
- Day·시간 배율·단계 실행
- 수동 스냅샷·되돌리기·JSON 내보내기
- Shift+클릭 초기화

정밀한 값과 반복 자동화는 동일 서비스 계층을 사용하는 `/ws test` 명령을 사용한다.

## 7. 플레이어 조절 명령

| 명령 | 효과 |
|---|---|
| `/ws test player level set|add <value>` | 레벨과 누적 EXP 정합성 유지 |
| `/ws test player exp set|add <value>` | EXP로 레벨 역산 |
| `/ws test player health set <value>` | 현재 체력 설정 |
| `/ws test player health heal` | 체력·허기·AP·생명 상태 회복 |
| `/ws test player ap set|full <value?>` | 현재 AP 설정 |
| `/ws test player life set <ACTIVE\|DOWNED\|DEAD>` | 생명 상태 전환 |
| `/ws test player stat set|reset <id> <value?>` | 테스트 스탯 오버라이드 |
| `/ws test player invulnerable set <true\|false>` | 테스트 무적 |
| `/ws test player effect add <type> <ticks> [amplifier]` | 포션 상태 부여 |
| `/ws test player effect clear` | 플레이어 상태 전체 정화 |

### 7.1 스탯 ID와 경계

| ID | 범위 |
|---|---:|
| `damage-dealt` | `0~100` |
| `break` | `0~100` |
| `damage-taken` | `0~100` |
| `damage-reduction` | `0~0.95` |
| `cooldown` | `0.05~10` |
| `ap-cost` | `0~10` |
| `ap-regen` | `0~20` |
| `move-speed` | `0.1~5` |
| `max-health` | `1~2048` |
| `max-ap` | `1~10000` |

피해 감소율은 95%를 상한으로 하며 무적은 별도 플래그로 처리한다.

## 8. 아이템·자원·증강 조절

```text
/ws test item resource set|add <resourceId> <amount>
/ws test item resource fill <amount>
/ws test item resource clear
/ws test item equipment give|equip|remove <weaponId>
/ws test item equipment clear
/ws test item quick set <itemId> <amount>
/ws test item vanilla give <material> <amount>
/ws test item test-clear
/ws test augment personal give|remove <augmentId>
/ws test augment personal clear
/ws test augment party set <augmentId>
/ws test augment party clear
/ws test augment redraw <3|6|10>
```

장비는 기존 서버 권위 장착 원장과 슬롯 `0/-106` 동기화를 그대로 통과한다. 권투는 `UNARMED`를 장착하는 것이 아니라 주무기 원장을 비우는 방식이다.

## 9. 몬스터·상태·전투 조절

```text
/ws test mob spawn <enemyId> [count]
/ws test mob boss
/ws test mob inspect
/ws test mob set <health|max-health|defence|break|break-max|attack> <value>
/ws test mob flag <ai|invulnerable|glowing> <true|false>
/ws test mob status add <id> <durationTicks> [amplifier]
/ws test mob status clear
/ws test mob attack <parry|guard|hit>
/ws test mob phase 2
/ws test mob pattern
/ws test mob remove
/ws test mob clear
/ws test damage preview <rawDamage> <rawBreak>
```

- 몬스터 조절은 플레이어가 64블록 안에서 바라보는 WildSurvival 태그 개체만 대상으로 한다.
- 피해 미리보기는 원 피해→방어 계수→증강→Test Lab 배율→최종 피해와 브레이크를 분리해 출력한다.
- 테스트 상태 정화는 상태 PDC, 포션, 발광, 그로기 AI 정지를 해제한다.
- `attack` 오버라이드는 해당 테스트 개체의 플레이어 대상 공격 피해에만 적용한다.
- `mob attack parry|guard`는 바라보는 운영 적을 일시 정지하고 다음 실제 slot 0 F 입력 뒤 각각 2틱·8틱에 그 적의 첫 운영 패턴을 실행한다. `hit`은 F를 합성하지 않고 20틱 뒤 같은 경로를 실행한다. 이 검증 지연은 Test Lab 논리 시간 정지와 독립된 Bukkit 서버 틱을 사용하고 실행 ID도 해당 서버 틱으로 고유화한다. 세 모드 모두 직접 피해를 주입하지 않으며 `ENEMY_HIT_RESOLVED`·`ENEMY_ACTION_COMMITTED`를 남긴 뒤 기존 AI 상태를 복원한다.

## 10. Day·시간·환경 조절

```text
/ws test world day set <1..50>
/ws test world day advance
/ws test world time freeze|resume
/ws test world time scale <0.05..100>
/ws test world time step <ticks>
/ws test world weather <clear|rain|thunder>
```

Test Lab은 실제 벽시계 대신 저장 가능한 논리 시계를 사용한다. 시간 정지 중에도 `step`으로 이벤트·보스 타이머를 정확한 틱만큼 진행할 수 있다. Day 직접 지정과 Test Lab GUI의 Day 좌·우클릭은 Season 1 전체 `1~50`을 순환하며, 변경 시 `run.day`와 `seasonDay`를 같은 운영 데이터로 다시 잠가 서로 다른 Day의 예산·사건이 섞이지 않게 한다. 일반 프로토타입은 기존 벽시계와 서버 틱을 유지한다.

## 11. 솔로 협동 시뮬레이션

```text
/ws test party size <1..4>
/ws test party contribute <category> <amount>
/ws test party boss-channel <contributors>
/ws test party dummy spawn <id> <ACTIVE|DOWNED|DEAD>
/ws test party dummy list|clear
```

- `virtualPartySize`는 보스 HP·브레이크, 사건 적 수, 협동 요구 인원 계산에 사용한다.
- 실제 `Player` 객체를 위조하지 않는다. 보상 수신자·온라인 멤버·인벤토리 소유자는 실제 한 명만 유지한다.
- 보스 협동 채널은 `virtual-test-N` 기여 슬롯으로 요구 인원을 모의한다.
- 가상 파티 더미는 ArmorStand 기반 개발 개체다. `DOWNED` 더미는 웅크린 채 우클릭을 유지하면 설정된 채널 뒤 `ACTIVE`로 전환된다.

## 12. 재현 시나리오

| ID | 준비 상태 |
|---|---|
| `SANDBOX` | 빈 테스트 회차 |
| `GATHER` | 전투 곡괭이·Day 1 채집 판정 |
| `CRAFT` | 개인 PDC 자원 각 50·Craft 해금·3×3 제작 GUI 준비 |
| `COMBAT` | 검·AP 재생·근접 2·원거리 1 |
| `STATUS-BREAK` | 곡괭이·장갑 적·브레이크 75%·둔화 |
| `AUGMENT` | 레벨 3 실버 개인 증강 재추첨 |
| `BOSS-PHASE-1` | 가상 4인 Day 10 보스 1페이즈 |
| `BOSS-PHASE-2` | 가상 4인 보스 2페이즈·협동 기여 1/2 |
| `DOWNED-REVIVE` | 가상 2인·빈사 더미 구조 채널 |

실행은 `/ws test scenario run <id>`이며 실행 전 스냅샷을 생성한다.

## 13. 검사·내보내기

```text
/ws test inspect run|player|target|dummies
/ws test status
/ws test export
```

JSON 내보내기는 다음을 포함한다.

- 전체 TEST 회차 스냅샷
- 플레이어 레벨·EXP·생명·장비·증강·스탯 오버라이드·상태
- 조준 중인 몬스터의 HP·방어·브레이크·공격·AI·상태
- 플러그인 틱 p95

## 14. 구현·검증 완료 기준

- 일반 저장소가 TEST runType 저장을 거부하고 Test Lab 저장소가 PROTOTYPE을 거부한다.
- Test Lab 백업·스냅샷·프리셋이 원자 파일 교체를 사용한다.
- Test Lab 백업·스냅샷·프리셋·내보내기는 저장마다 고유 임시 파일을 사용하고, Windows의 일시적 `AccessDenied`에 회차 저장과 동일한 제한 재시도 정책을 적용한다.
- Test Lab 회차 `current.json`은 저장마다 고유 임시 파일을 사용한다. Windows의 일시적 `AccessDenied`만 5회까지 제한 재시도하고, 원자 이동 미지원 시 교체 이동으로 폴백한다. 최종 실패 시 메모리 version을 저장 전 값으로 되돌리고 실패 임시 파일을 정리한다.
- 경로 문자가 포함된 프리셋 ID와 범위를 벗어난 수치를 거부한다.
- 방어·피해 감소·테스트 배율·쿨다운 수식 단위 테스트가 통과한다.
- Paper에서 플러그인 활성화, `/ws test status`, 도움말·권한 등록, 정상 종료가 통과한다.
- `ABORTED + restorePending` 복구 정책을 포함한 자동 테스트 19건이 실패·오류·건너뜀 없이 통과한다.
- `ABORTED/ENDED + restorePending`에서는 동일 세션 소유자만 `undo` 또는 `exit` 복구를 수행할 수 있다. 일반 mutate는 `RUNNING` 소유자에게만 허용하고, 복구 성공 시 snapshot ID를 표시한다.
- 실제 플레이어가 입장→조절→시나리오→undo→exit 후 인벤토리·위치·속성이 복원된다.

마지막 항목은 폐쇄 인게임 플레이테스트 증거가 있어야 `VERIFIED`로 승격한다. 자동·Paper 콘솔 스모크만 통과한 상태에서는 `IMPLEMENTED_PROTOTYPE`을 유지한다.
