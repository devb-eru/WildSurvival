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
/ws equipment
/ws craft
/ws status
/ws augment
```

## 입력

| 동작 | 입력 |
|---|---|
| 장착 무기 기본 공격 | `L` |
| 무기 액티브 W1 | `Shift+L` |
| 무기 액티브 W2 | `Shift+R` |
| 무기 액티브 W3 | `F` |
| 공용 액티브 C1~C4 | `Shift+2~5` |
| 퀵 아이템 Q1~Q4 | `6~9` |
| 회피 보조 입력 | 빠른 `Shift` 두 번 |

주무기는 실제 핫바 슬롯 `0`, 보조무기는 오프핸드 `-106`에 서버가 동기화한다. 공용 액티브나 퀵 아이템 실행 뒤 선택 슬롯은 `0`으로 돌아간다. 검·활·곡괭이·삼지창은 바닐라 피해를 사용하지 않으며, 두 장착 슬롯이 비면 권투 가상 프로필을 사용한다.

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

기본 시간은 10분, 25분, 45분 체크포인트이며 `config.yml`에서 조정할 수 있다. `/ws prototype advance`는 폐쇄 테스트 가속용이고 일반 권한에서는 사용할 수 없다.

## 런타임 저장

- 회차 원자 스냅샷: `plugins/wsplugin/runs/current.json`
- 이벤트 로그: `plugins/wsplugin/telemetry/events.jsonl`
- 감사 로그: `plugins/wsplugin/telemetry/audit.jsonl`
- 세션 보고서: `plugins/wsplugin/telemetry/session-<runId>.json`

회차 저장은 `runType: PROTOTYPE`, `contentRevision: ws-prototype-r1`을 강제한다. 정식 또는 r1/r2 회차를 이 저장소로 읽으면 로드를 거부한다.

## 현재 승인 경계

- 자동 빌드·L0 콘텐츠 검증·순수 도메인 단위 테스트: 통과
- Paper 26.1.2 기동·플러그인 활성화·런타임 L0 검증·명령 등록·정상 종료: 2026-08-22 스모크 통과
- 2인·4인 `45~90분` 완주, 재접속·보스 장애·1,000회 곡괭이 입력·부하 측정: 폐쇄 플레이테스트 필요
- `G-400` 승인 전 Day 11+와 정식 r2 확장 금지
