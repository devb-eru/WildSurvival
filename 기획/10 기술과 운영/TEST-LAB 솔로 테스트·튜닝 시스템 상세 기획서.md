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
- `/ws test exit`는 입장 백업 복원과 감사 기록 뒤 회차 보관·제거가 성공할 때까지 `restorePending=true`를 유지한다. 회차 제거가 실패하면 현재 파일과 메모리 권한을 함께 보존해 같은 소유자가 다시 실행할 수 있으며, 회차 제거 성공 뒤에만 해당 run의 스냅샷과 입장 백업을 소비한다. 이 후처리의 일부 파일이 잠겨도 복구 완료를 실패로 되돌리지 않고 잔류 파일과 예외를 운영 로그에 남긴다.

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
| `CRAFT-UNLOCK` | Craft 잠금·참나무 원목 4개·메뉴 경유 해금 준비 |
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
/ws test inspect run|player|target|dummies|transaction
/ws test fault transaction RESERVED|PROCESSING|COMMITTED
/ws test fault status|clear
/ws test status
/ws test export
```

`inspect transaction`은 소유자의 최신 `CRAFT` 거래 ID·상태·recipe ID·예약 자원·입력/출력 서명·출력 instanceId를 표시한다. `fault transaction`은 다음 자원 거래의 지정 내구 체크포인트에서 한 번만 멈춘다. `COMMITTED`는 출력 원장 저장 뒤 실제 Bukkit 인벤토리 지급 직전에 멈추므로, 이 지점의 증거는 채팅 표시와 거래 검사를 확인한 뒤 종료 훅 없는 실제 JVM 중단으로만 취득한다.

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
- heartbeat 자동 저장은 마지막 성공 스냅샷과 실제 상태가 동일하면 파일 쓰기와 version 증가를 생략한다. 명시적 거래·전투 비용·종료 저장은 이 최적화 대상이 아니며 계속 즉시 원자 저장한다.
- Test Lab 종료 history도 고유 임시 파일과 원자 교체를 사용하며, history 교체 또는 current 임시 파일 정리 실패 전에는 권위 `current.json`을 삭제하지 않는다.
- RCOST/FCOST의 예약·처리·커밋·취소와 연결된 연구·시설 변경은 분리된 후보 회차에서 실행하고 저장 성공 뒤에만 활성 Test Lab 회차로 교체한다. 콜백 또는 저장 예외가 나면 기존 잔액·거래·도메인 상태와 version을 유지한다.
- 1인 비용 기준 `RS-D01-SAMPLE`은 목재 3, `FAC-S16-L2`는 목재 7·철 9·레드스톤 9·마력 결정 1의 정확 동가치 조합을 사용한다. 개인/공용 클릭 오선택은 반대 원장을 사용하거나 차감하지 않으며, 성공 거래의 scope·소유자·예약 수량·시도 번호가 재접속 뒤 동일해야 한다.
- `RESERVED/PROCESSING` 거래의 연구 target 또는 시설 작업 근거가 복구 시 없으면 자원을 원래 개인/공용 원장에 정확히 환불하고 `CANCELLED` 이력을 남긴다. 같은 시설 transaction ID의 낡은 작업 행은 환불과 함께 제거하며, 취소 연구 노드는 가용성 판정을 다시 받는다.
- AP 자극제는 테스트 배율이 반영된 AP 비용, 퀵 아이템 `-1`, 즉시 AP `+10`, 5초간 초당 `+3` 예약, 전투당 사용 횟수를 하나의 후보 회차에 적용한다. 최초 저장이나 개별 펄스 저장이 실패하면 활성 회차에는 해당 단계의 일부 변경도 남기지 않는다.
- 실제 프로세스 복구 증거는 AP 자극제 K0~K5의 여섯 저장 경계 `30/5→33/4→36/3→39/2→42/1→45/0` 각각에서 종료 훅 없는 JVM 강제 종료를 수행한다. 재시작 뒤 AP 45·잔여 0·퀵 수량 0·범위별 사용 1회와 재접속 실제 인벤토리 0개가 모두 일치해야 한다.
- 비용 복구 증거는 `PROCESSING`의 `RS-D01-SAMPLE` 개인 목재 3과 `RESERVED`의 `RS-D03-SEPARATION` 개인 목재 5·철 2를 실제 JVM 강제 종료한다. `RESERVED` 복구는 `QUEUED/RESERVED/PERSONAL/attempt 1`에서 `PROCESSING/PROCESSING`으로 한 번만 전이하고 예약 수량·잔액 0·시도 번호를 유지해야 한다. 2026-09-15 격리 포트 25567에서 두 경로와 AP K0~K5가 모두 통과했다.
- AP 자극제 외 생산 소모품도 공용 스킬 AP 비용·퀵 아이템 `-1`·전투당 사용 횟수를 한 후보 회차로 커밋한다. 직접 사용형 구조 고정대 예약, 수리 키트 40% 내구 복구, FAC-P05 만료 `+60초`는 아이템 차감과 같은 저장에 포함한다.
- `research state <id> READY`로 주입한 증거 소유 상태는 연구 GUI 진입·주기 가용성 갱신에서 강등하지 않는다. 자동 가용성 재계산 대상은 `HIDDEN/OBSERVABLE/HYPOTHESIZED`뿐이며 `READY` 이후는 증거·거래·완료 런타임이 소유한다.
- 야전 배급팩은 160/240틱 채널 완료 시에만 수량을 소비하고, 좌클릭·접속 종료·서버 종료·행동 불가 취소에서는 원장과 물리 아이템이 모두 유지된다.
- 정상 드롭·획득·상자 이동·인벤토리 이동은 이벤트 반영 뒤 실제 퀵 아이템 수량을 개인 원장의 새 기준선으로 채택한다. 접속·회차 재로딩과 명시적인 물리 소비 실패 복구에서만 저장된 원장을 물리 인벤토리보다 우선한다.
- 경로 문자가 포함된 프리셋 ID와 범위를 벗어난 수치를 거부한다.
- 방어·피해 감소·테스트 배율·쿨다운 수식 단위 테스트가 통과한다.
- Paper에서 플러그인 활성화, `/ws test status`, 도움말·권한 등록, 정상 종료가 통과한다.
- `ABORTED + restorePending` 복구 정책을 포함한 자동 테스트 19건이 실패·오류·건너뜀 없이 통과한다.
- `ABORTED/ENDED + restorePending`에서는 동일 세션 소유자만 `undo` 또는 `exit` 복구를 수행할 수 있다. 일반 mutate는 `RUNNING` 소유자에게만 허용하고, 복구 성공 시 snapshot ID를 표시한다.
- 플레이 테스트가 불가능한 기간의 콘솔 스모크는 보존 중인 복구 회차를 변경 명령 없이 기동·정상 종료하고, 동일 run ID·`restorePending=true`·스냅샷·입장 백업·임시 파일 0·닫힌 포트를 대조한다. 이는 복구 로딩 증거일 뿐 아래 실클라이언트 완료 항목을 대체하지 않는다.
- 실제 플레이어가 입장→조절→시나리오→undo→exit 후 인벤토리·위치·속성이 복원된다.
- P2 Craft 해금은 `craft-unlock:<runId>`와 `VANILLA:ANY_LOG` 목표 수량을 대조한다. 저장 성공 전 원목을 제거하지 않고, 저장 뒤 중단은 재접속 때 목표 수량으로 수렴해야 한다.
- P2 3×3 제작은 `CRAFT` 거래의 입력 fingerprint·출력 서명·output instanceId를 검사한다. `RESERVED`, `PROCESSING`, `COMMITTED` 각 체크포인트에서 실제 JVM을 종료한 뒤 재접속해 개인 자원·GUI 입력 소비·등록 아이템 수량 또는 장비 instanceId가 각각 정확히 한 번만 반영되는지 확인한다.
- `COMMITTED` 실물 지급 전 장애 체크포인트는 강제 종료 직전까지 등록 아이템 대기·`REGISTERED:<id>` 또는 `RESOURCE:<id>` 목표 수량, 장비 pending instanceId가 유지되고 실제 출력 수량이 늘지 않아야 유효하다. 인벤토리 클릭 관찰, 일반 pending flush, 자원·장비 물리 복구가 이를 먼저 지급했다면 실제 JVM을 종료하지 말고 `PAUSE_LEAK` 결함으로 기록한 뒤 수정 빌드에서 다시 예약한다.
- `CRAFT-UNLOCK`은 리셋된 잠금 회차와 원목 4개만 준비하며 해금 자체는 플레이어 메뉴의 Craft 버튼과 해금 GUI를 통해 수행한다. `CRAFT`는 거래 장애 주입 전용으로 해금 상태와 개인 자원을 준비하고, 두 시나리오의 관리자 준비 행위는 관리자 지급 없는 Day 1~3 완주 증거를 대체하지 않는다.
- Test Lab `enter/reset/scenario`가 회차 스냅샷을 교체할 때 Day 1의 시작·공세 시각을 0으로 되돌리면 동결 시계에서도 무예산 공세가 즉시 완료돼 Day 2로 진행한다. 교체 후보는 현재 논리 시각, 30초 준비, Day 1의 잠금 자원·사건·인원별 위협 예산을 함께 저장해야 한다. 수동 `world day set`도 3인 기본 예산을 재사용하지 않고 가상 파티 크기의 예산을 저장한다. 실클라이언트에서 `/ws test world day set 1`은 구버전 임시 복구일 뿐 새 런타임의 무보정 Day 1 초기화 통과를 대체하지 않는다.
- P2 공용 물류고는 설치 전 개인 원장 획득·소비, 설치 커밋 뒤 명시적 개인↔공용 입출금, 시설 비활성 거부를 검증한다. 각 입출금은 개인/공용 잔액 합계를 보존하고 `RESOURCE:<id>` 체크포인트가 실물 PDC 수량과 일치한 뒤 제거되어야 한다.

마지막 항목은 폐쇄 인게임 플레이테스트 증거가 있어야 `VERIFIED`로 승격한다. 자동·Paper 콘솔 스모크만 통과한 상태에서는 `IMPLEMENTED_PROTOTYPE`을 유지한다.
