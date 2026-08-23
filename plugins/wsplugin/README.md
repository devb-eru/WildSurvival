# WildSurvival 프로토타입 플러그인

`DEV-ROADMAP-001`의 `ws-prototype-r1` 수직 구간을 구현한 Paper 플러그인이다. 바닐라 클라이언트만 사용하며 정식 `ws-content-r2` 회차와 저장을 공유하지 않는다.

## 요구 환경

- Java 25
- Paper 26.1.2
- 클라이언트 모드 없음
- 리소스 팩 선택 사항

## 빌드와 검증

Windows:

```powershell
.\gradlew.bat check
.\gradlew.bat build
```

`check`는 `ws-prototype-r1` lock·manifest·SHA-256, 최소 콘텐츠 불변식, 레벨 공식, 자원 원장과 재시작 스냅샷을 검사한다. 결과 JAR은 `build/libs/wsplugin-0.0.1-ALPHA.jar`다.

개발 서버는 다음 명령으로 실행한다.

```powershell
.\gradlew.bat runServer
```

첫 실행에서는 Mojang EULA를 운영자가 직접 검토하고 `run/eula.txt`에 동의해야 한다. 저장소는 EULA 동의를 자동 작성하지 않는다.

## 회차 시작

운영자 권한으로 다음 순서를 사용한다.

```text
/ws content validate
/ws prototype create [player1 player2 player3 player4]
/ws prototype start
```

플레이어 명단을 생략하면 온라인 플레이어 전체를 사용한다. 신규 회차는 2~4인만 허용하며 시작 후 멤버 추가는 지원하지 않는다.

개발 검증용 명령:

```text
/ws prototype inspect
/ws prototype advance
/ws prototype boss
/ws prototype stop TEST --dry-run
/ws prototype stop TEST --confirm
```

일반 플레이 명령:

```text
/ws menu
/ws guide
/ws equipment
/ws skills
/ws craft
/ws codex
/ws status
/ws augment
```

회차 시작 직후 L키의 `WildSurvival 생존 기록` 탭에서 Day 10까지의 선형 길잡이를 확인할 수 있다. 첫 습격은 기본 90초 뒤 시작하며, 시작 보급 없이 원목→Craft 해금→급조 곡괭이→석재 도구→첫 무기 순으로 진행한다.

## WildSurvival Test Lab

Test Lab은 한 명이 콘텐츠 시스템을 격리된 `runType: TEST` 회차에서 조절·반복 검증하는 개발 도구다. 일반 프로토타입 회차 및 저장과 동시에 실행되지 않으며 기본 설정은 비활성이다.

개발 서버의 `plugins/wsplugin/config.yml`에서 다음 값을 명시적으로 켠 뒤 재시작한다.

```yaml
test-lab:
  enabled: true
```

입장과 종료:

```text
/ws test enter [seed] [virtualPartySize]
/ws test gui
/ws test status
/ws test exit TEST_COMPLETE --confirm
```

입장 시 인벤토리·선택 핫바·위치·게임 모드·체력·허기·총 EXP·포션·무적·비행·핵심 속성을 백업한다. 정상 종료 시 입장 전 상태를 먼저 복원한 뒤 TEST 회차와 태그 개체를 폐기한다. 종료 도중 중단된 회차는 다음 기동에서 `restorePending` 복구 대상으로 유지된다.

주요 정밀 명령:

```text
/ws test player level set 10
/ws test player exp add 500
/ws test player stat set damage-reduction 0.75
/ws test player stat set damage-dealt 3
/ws test player effect clear
/ws test item resource fill 50
/ws test item equipment equip PICKAXE
/ws test augment personal give AUG-S-006
/ws test mob spawn EN-D4-01 1
/ws test mob set defence 100
/ws test mob status clear
/ws test world day set 10
/ws test world time freeze
/ws test party size 4
/ws test party boss-channel 2
/ws test scenario run BOSS-PHASE-2
/ws test snapshot MANUAL
/ws test undo
/ws test export
```

조준 몬스터 변경 명령은 64블록 안에서 바라보는 WildSurvival 태그 개체만 대상으로 한다. 전체 명령·범위·시나리오 계약은 `TEST-LAB-001`을 따른다.

## 입력

| 동작 | 입력 |
|---|---|
| 장착 무기 기본 공격 | `L` |
| 무기 스킬 W1 | `R` |
| 무기 스킬 W2 | `Shift+L` |
| 무기 스킬 W3 | `Shift+R` 또는 `F` |
| 공용 액티브 C1~C4 | 어느 선택 슬롯에서든 `Shift+2~5` |
| 퀵 아이템 Q1~Q4 | 어느 선택 슬롯에서든 `Shift+6~9` |
| 통합 플레이어 메뉴 | 어느 선택 슬롯에서든 `Shift+F` |
| 회피 보조 입력 | 빠른 `Shift` 두 번 |

무기 공격·W1~W3는 현재 선택 슬롯이 `0`일 때만 해석하고 실행 뒤 `0`을 유지한다. C1~C4·Q1~Q4는 현재 슬롯과 무관하게 실행한 뒤 원래 선택 슬롯으로 복원하며, `Shift+F` 메뉴도 같은 규칙을 따른다. 주무기는 실제 핫바 슬롯 `0`, 보조무기는 오프핸드 `-106`에 서버가 동기화한다. 슬롯 `1~8`은 채굴·설치·섭취를 포함한 바닐라 자유 슬롯이고, 일반 F도 슬롯 `1~8`에서는 바닐라 교환으로 남는다. 권투를 제외한 장착 무기는 바닐라 피해를 사용하지 않으며 두 장착 슬롯이 비면 권투 가상 프로필을 사용한다.

## Day 1~10 제작 콘텐츠

- 자원 6종: 목재·석재·섬유·철·연료탄·오염 조직
- 채집 도구 3종: 급조·석재·철제 곡괭이
- 전투 장비 6종과 권투: 도끼·검·활·전투 곡괭이·둔기·삼지창·권투
- 퀵 아이템 3종: 응급 배급·압박 붕대·오염 중화제
- 시설·유틸리티: 야전 화살·수지 횃불·공용 보급 저장소·생존 시계
- 3×3 조합법 15종(고정 배치 11종·위치 무관 4종), 무기 스킬 13종, 공용 액티브 4종

등록 자원 블록은 바닐라 드롭과 WS 드롭을 동시에 주지 않으며, 등록 결과는 플레이어 인벤토리로 지급된다. 인벤토리가 가득 차면 월드 드롭 대신 회차 보관함에 대기했다가 공간이 생길 때 자동 지급한다. 미등록 아이템은 바닐라 드롭 규칙을 유지한다. 석재·연료탄은 급조 곡괭이 이상, 철은 석재 곡괭이 이상이 필요하다.

Craft 입력 3×3은 실제 빈 슬롯이고 비입력 영역만 회색 유리로 막는다. 위치 무관 조합도 필요한 수량만큼 서로 다른 칸을 점유해야 하므로, 한 칸에 재료 3개를 쌓아 붕대의 섬유 3칸 요구를 대신할 수 없다. 공용 보급 저장소는 제작 즉시 활성화되지 않고 실물 아이템으로 지급되며, 월드에 설치한 뒤에만 공용 자원 원장을 사용할 수 있다.

모든 제작 장비는 실물 인벤토리 아이템이다. 장비 GUI는 실제 인벤토리의 장착 가능 장비만 후보로 보여 주고 나머지 위치를 장벽으로 표시한다. 장착 중인 주·보조무기는 상단에 표시되며 클릭 해제와 후보 직접 교체를 지원한다. 스킬 GUI는 W 무기 스킬과 C 공용 액티브를 분리하고 장착된 항목에 광택을 표시한다. 증강 메뉴는 보유 개인·파티 증강을 표시하며 미선택 드로우가 있으면 선택 화면을 우선 연다. 설명 기본값은 간단 모드이고 설정에서 수치 상세 모드로 바꿀 수 있다.

회차 시작 시 지급되는 생존 시계를 우클릭하면 현재 Day·경과 시간·다음 체크포인트까지 남은 시간을 확인할 수 있다. 액션바는 빈사·구조·레벨 상승·EXP·자원 획득·전투 알림 순으로 보존되며 상시 HUD는 활성 알림이 끝난 뒤에만 다시 그려진다.

## 프로토타입 루프

```text
Day 1 잔해 수색
→ Day 3 원거리 압박·Lv3 SILVER
→ Day 6 장갑/오염 사건·Lv6 GOLD
→ Day 10 보스 준비·Lv10 PRISM
→ 공명 추적체 2페이즈·협동 중단
→ 파티 증강 3택 투표
→ 결과·텔레메트리
```

기본 시간은 첫 습격 90초, Day 3·6·10 체크포인트 10분·25분·45분이며 `config.yml`에서 조정할 수 있다. `/ws prototype advance`는 폐쇄 테스트 가속용이고 일반 권한에서는 사용할 수 없다.

## 런타임 저장

- 회차 원자 스냅샷: `plugins/wsplugin/runs/current.json`
- Test Lab 회차: `plugins/wsplugin/test-lab/runs/current.json`
- Test Lab 입장 백업·스냅샷·프리셋·내보내기: `plugins/wsplugin/test-lab/`
- Test Lab 감사 로그: `plugins/wsplugin/test-lab/audit.jsonl`
- 이벤트 로그: `plugins/wsplugin/telemetry/events.jsonl`
- 감사 로그: `plugins/wsplugin/telemetry/audit.jsonl`
- 세션 보고서: `plugins/wsplugin/telemetry/session-<runId>.json`

회차 저장은 `runType: PROTOTYPE`, `contentRevision: ws-prototype-r1`을 강제한다. 정식 또는 r1/r2 회차를 이 저장소로 읽으면 로드를 거부한다.

## 현재 승인 경계

- 자동 빌드·L0 콘텐츠 검증·순수 도메인 단위 테스트: 2026-08-23 통과
- Paper 26.1.2 격리 기동·플러그인 활성화·18단계 Advancement 등록·런타임 L0 검증·명령 등록·정상 종료: 2026-08-23 최신 보정 JAR 스모크 통과
- Test Lab 저장소 격리·수치 경계·전투 계산 자동 테스트와 `/ws test status`·도움말 노출·콘솔 진입 거부·정상 종료: 2026-08-22 통과
- Test Lab 실제 플레이어 입장→조절→시나리오→undo→종료 후 인벤토리·위치·속성 복원: 폐쇄 인게임 E2E 필요
- 2인·4인 `45~90분` 완주, 재접속·보스 장애·1,000회 곡괭이 입력·부하 측정: 폐쇄 플레이테스트 필요
- `G-400` 승인 전 Day 11+와 정식 r2 확장 금지
