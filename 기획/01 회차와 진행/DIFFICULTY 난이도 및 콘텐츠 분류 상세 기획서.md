# WildSurvival DIFFICULTY 난이도 및 콘텐츠 분류 상세 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `DIFFICULTY-001` |
| 역할 | 콘텐츠 분류, 표시 난이도, 내부 ID, 마이그레이션과 통계 기준의 단일 권위 문서 |
| 상위 기준 | `VISION` |
| 연계 문서 | `GAME`, `STAT`, `CORE`, `ACT`, `SKILL`, `DAY`, `BUDGET-001`, `TECH` |
| 구현 환경 | 바닐라 Minecraft Java Edition + Paper 서버 플러그인 |
| 상태 | `BASELINE_LOCKED` |
| 최종 수정일 | 2026-08-21 |

## 1. 도입 판단

본 변경은 도입한다.

- 기존 Easy를 Story로 분리하면 처음 경험하는 파티를 위한 완주 지향 밸런스와 숙련 파티의 도전 밸런스를 같은 이름 아래 섞지 않을 수 있다.
- 기존 수치 단계를 한 칸씩 당기면 이미 작성된 수치와 패턴 자산을 폐기하지 않고 새 역할에 그대로 재배치할 수 있다.
- `Challenge`를 난이도명이 아닌 상위 콘텐츠 분류로 사용하면 Easy부터 `???`까지를 하나의 도전형 규칙군으로 운영할 수 있다.
- Story의 실패율 목표를 수치로 두면 이후 서사를 추가해도 난이도 튜닝 목표가 흔들리지 않는다.

주의점은 기존 내부 ID와 새 내부 ID의 의미가 달라진다는 것이다. 활성·과거 회차는 반드시 난이도 스키마 버전으로 해석하며 문자열만 보고 변환하지 않는다.

## 2. 선택 구조와 용어

회차 생성 화면은 서로 다른 세 축을 분리한다.

```text
콘텐츠 분류
├─ Story 콘텐츠
│  └─ Story 난이도
└─ Challenge 콘텐츠
   ├─ Easy 난이도
   ├─ Normal 난이도
   ├─ Hard 난이도
   └─ ??? 난이도

규칙 모드
├─ STANDARD
└─ CHAOS
```

| 축 | 저장 필드 | 값 | 책임 |
|---|---|---|---|
| 콘텐츠 분류 | `contentCategory`, 직렬화 `content-category` | `STORY_CONTENT`, `CHALLENGE_CONTENT` | UI, 통계, 향후 서사·도전 콘텐츠 라우팅 |
| 난이도 | `difficultyId`, 직렬화 `difficulty` | `STORY`, `EASY`, `NORMAL`, `HARD`, `UNKNOWN` | HP, 환경 피해, 적 전략과 밸런스 |
| 규칙 모드 | `gameMode`, 직렬화 `game-mode` | `STANDARD`, `CHAOS` | 수치 엔진, 하드캡, 처리 주기와 Day 스킵 제한 |

- 플레이어 화면의 `Story 모드`는 `STORY_CONTENT + STORY` 조합을 뜻한다.
- 기술 문서에서 `모드`가 STANDARD·CHAOS와 혼동되지 않도록 내부 필드에서는 Story를 콘텐츠 분류와 난이도로 분리한다.
- 플레이어 화면의 `도전(Challenge) 콘텐츠`는 `CHALLENGE_CONTENT`를 뜻한다.
- `Challenge`는 콘텐츠 분류의 표시명이며 난이도 ID가 아니다.
- 신규 데이터에 `difficultyId: CHALLENGE`를 저장하지 않는다.
- `???`는 표시명을 그대로 유지하고 내부 ID `UNKNOWN`을 사용한다.
- 규칙 모드는 콘텐츠 분류·난이도와 독립적으로 저장한다.

## 3. 난이도 재분류

### 3.1 기존 단계의 이동

| 구 난이도 스키마 v1 | 신 난이도 스키마 v2 | 콘텐츠 분류 | 수치·패턴 계승 |
|---|---|---|---|
| Easy / `EASY` | Story / `STORY` | Story | 구 Easy 전체 규칙 |
| Normal / `NORMAL` | Easy / `EASY` | Challenge | 구 Normal 전체 규칙 |
| Hard / `HARD` | Normal / `NORMAL` | Challenge | 구 Hard 전체 규칙 |
| Challenge / `CHALLENGE` | Hard / `HARD` | Challenge | 구 Challenge 전체 규칙 |
| ??? / `UNKNOWN` | ??? / `UNKNOWN` | Challenge | 변경 없음 |

`전체 규칙`은 플레이어 HP뿐 아니라 환경 피해, 오염 DEF 효율, 적 전략 수, 몬스터 템플릿, 보상 계수, 추천 스탯과 향후 난이도별 패턴을 포함한다.

### 3.2 신 난이도 목록

| 표시명 | 내부 ID | 콘텐츠 분류 | 투자 전 HP | HP 배율 | 기본 성격 |
|---|---|---|---:|---:|---|
| Story | `STORY` | Story | 1,800 | 1.80 | 완주 지향, 시스템 학습, 향후 서사 제공 |
| Easy | `EASY` | Challenge | 1,000 | 1.00 | 도전 콘텐츠 기준선 |
| Normal | `NORMAL` | Challenge | 800 | 0.80 | 적 전략과 복합 대응 강화 |
| Hard | `HARD` | Challenge | 500 | 0.50 | 구 Challenge 규칙 계승 |
| ??? | `UNKNOWN` | Challenge | 100 | 0.10 | 최고 위험, 정보 제한, 별도 제한 |

- 새 수치 기준선은 Easy 1,000 HP다.
- Story는 구 Easy의 수치·패턴을 계승하되 실패율 목표를 기준으로 추가 완화할 수 있다.
- Hard는 구 Challenge의 모든 난이도별 수치와 출현 일정을 계승한다.
- `???`의 현재 수치와 규칙은 이동하지 않는다.

## 4. Story 콘텐츠

### 4.1 현재 범위

실제 서사, 대사, 5개 챕터, 기록 오브젝트와 등장인물은 `STORY-001`에 작성됐다. `STORY_CONTENT + STORY` 회차는 해당 내용을 사용하고, Challenge 회차는 기본적으로 Story 장면을 재생하지 않는다.

기획 콘텐츠와 런타임 상태는 다음처럼 구분한다.

- 구 Easy 밸런스를 계승한 `STORY` 난이도
- Story 전용 통계 분리
- `STORY-001`의 프롤로그·Day 1~50+·인물·대사·최종 연출 기획
- 챕터·오브젝트·인물·대사·연출을 연결할 이벤트 접점
- 런타임 Story JSON이 없는 `EMPTY` 리비전에서도 회차를 정상 완주하는 무내용 폴백

`ws-content-r1`은 아직 `storyRevision: EMPTY`이므로 기획 완료와 런타임 탑재 완료를 혼동하지 않는다. 실제 이벤트 JSON을 추가하는 차기 콘텐츠 리비전에서 활성한다.

### 4.2 Story 실패율 목표

Story + STANDARD의 유효 회차 클리어 실패율 목표는 `10%`다.

```text
Story 클리어 실패율 =
terminalFailedRuns
÷ (terminalCompletedRuns + terminalFailedRuns)
× 100
```

| 항목 | 집계 규칙 |
|---|---|
| 완료 | 최종 목표 트랜잭션이 확정되어 `COMPLETED`가 된 회차 |
| 실패 | 전원 완전 사망 등 정상 게임 규칙으로 `FAILED`가 된 회차 |
| 제외 | 진행 중, 정상 일시중단, 운영자 중단, 데이터 손상, 개발·QA 태그 회차 |
| 별도 표기 | 장기 미복귀·포기 회차는 `ABANDONED` 지표로 분리하고 실패율 분모에 숨기지 않음 |
| 주 기준 집단 | Story + STANDARD, 2~4인, 첫 완주 전 파티 |
| 보조 분해 | 파티 인원, Day 스킵 사용 여부, 도달 Day, 전멸 원인 |

- 목표점은 10%이며 초기 운영 허용 구간은 8~12%로 본다.
- 표본이 100개 미만이면 확정 튜닝 근거로 사용하지 않고 원인 지표만 관찰한다.
- 실패율이 낮다는 이유만으로 전투 전조·협동·생존 핵심 규칙을 삭제하지 않는다.
- 실패율 조정은 우선 경고 시간, 복구 자원 보장, 상태이상 가독성, 소프트락 방지와 Story 전용 적 예산으로 수행한다.
- CHAOS 규칙 모드의 Story 회차는 같은 실패율 목표 집계에서 분리한다.

### 4.3 Story 이벤트 접점

다음 도메인 이벤트에 `STORY-001` 장면을 연결한다. 런타임 리비전이 `EMPTY`이면 현재 폴백 동작을 유지한다.

| 이벤트 | `STORY-001` 연결 | `EMPTY` 폴백 |
|---|---|---|
| `RUN_CREATED` | `나이테 없는 아침` 프롤로그 | 빈 상태 생성 |
| `DAY_STARTED` | Day별 Story 비트·기록 큐 | 아무 내용도 재생하지 않음 |
| `DISCOVERY_COMPLETED` | C01~C30 기록·대사 해금 | 발견 시스템만 정상 처리 |
| `FACILITY_BUILT` | 시설·재건 장치 반응 | 시설 시스템만 정상 처리 |
| `BOSS_SIGNAL_ACQUIRED` | 각 장 보스 전후 장면 | 보스 신호만 공개 |
| `PLAYER_DOWNED` | 상태별 짧은 변형 대사 | 사망 시스템만 정상 처리 |
| `POST_50_ENTERED` | 미완료 준비 안내·최종 장 유지 | Day 51+ 규칙만 적용 |
| `FINAL_OBJECTIVE_READY` | 첫 신호 활성화 투표 전 장면 | 최종 목표 UI만 활성화 |
| `RUN_COMPLETED` | `첫불씨` 엔딩·결과 책 | 기본 결과 화면만 표시 |

Story 소비자는 도메인 결과를 바꿀 수 없다. 장비 지급, 발견 완료, 보스 처치와 최종 목표 승인 권한은 각 원본 시스템에 남는다.

## 5. Challenge 콘텐츠

Challenge 콘텐츠에는 `Easy`, `Normal`, `Hard`, `???`가 포함된다.

- Easy는 구 Normal의 기준 수치와 패턴을 사용한다.
- Normal은 구 Hard의 적 전략·패턴 빈도와 수치를 사용한다.
- Hard는 구 Challenge의 높은 압박 규칙을 사용한다.
- `???`는 기존 최고 위험 규칙과 정보 제한을 유지한다.
- Challenge 콘텐츠라는 분류 자체는 추가 배율을 적용하지 않는다. 실제 난이도 ID만 수치 원천이 된다.
- 콘텐츠 분류는 통계, UI, 향후 도전 과제와 시즌 규칙의 라우팅에 사용한다.

### 5.1 야생 활동 EXP 예산 범위

Challenge STANDARD는 Easy 기준 야생 활동 EXP 공급 예산을 다음 범위에서 확정할 수 있다.

| 난이도 | 변동 허용 | 예산 배율 범위 |
|---|---:|---:|
| Easy | 고정 | `1.00` |
| Normal | `±50%` | `0.50~1.50` |
| Hard | `±100%` | `0.00~2.00` |
| ??? | `±200%` | `0.00~3.00` |

- 음수 예산은 허용하지 않으므로 Hard·???의 하한은 0이다.
- `???`의 선택 배율은 서버 원장에 저장하지만 일반 UI에는 공개하지 않는다.
- 범위는 개인 EXP 보너스가 아니라 회차·Day에 잠기는 야생 활동 EXP 공급 예산이다.
- CHAOS에서는 모든 수치형 예산 도메인의 상한을 기준 1000%, 즉 `×10.00`까지 확장한다.
- 계산 순서, CHAOS 적용 도메인, 안전 상한과 고유 보상 제외 규칙은 `BUDGET-001`이 단일 기준이다.

## 6. 회차 생성 UI

1. 콘텐츠 분류를 선택한다.
2. Story를 선택하면 난이도를 `STORY`로 고정하고 실패율 목표 안내를 표시한다.
3. Challenge를 선택하면 Easy, Normal, Hard, ??? 중 하나를 선택한다.
4. 규칙 모드 STANDARD 또는 CHAOS를 별도로 선택한다.
5. 난이도·규칙 모드에 따른 예산 허용 범위와 공개 가능한 선택 프로필을 표시한다.
6. Day 스킵 허용 여부를 계산해 요약 화면에 표시한다.
7. 파티 전원이 요약 계약에 동의하면 회차를 생성한다.

표시 예시:

```text
콘텐츠: Challenge
난이도: Normal
규칙 모드: STANDARD
Day 스킵: 사용 가능
```

`???`의 세부 수치를 숨기더라도 Day 스킵 불가, 회차 변경 불가, 최고 위험 콘텐츠라는 사실은 시작 전에 명시한다.

## 7. 데이터 계약

```yaml
run-classification:
  difficulty-schema-version: 2
  content-category: CHALLENGE_CONTENT
  difficulty-id: NORMAL
  game-mode: STANDARD
  budget-policy-revision: budget-r1
  budget-profile-id: challenge-normal-seeded
  story:
    schema-version: 1
    content-revision: EMPTY
    active-chapter-id: null
    chapter-states: {}
    object-states: {}
    character-states: {}
    flags: {}
```

### Story 데이터 필드

| 필드 | 목적 | 현재 기본값 |
|---|---|---|
| `storySchemaVersion` | 향후 데이터 마이그레이션 | `1` |
| `storyContentRevision` | 사용한 Story 콘텐츠 묶음 | `EMPTY` |
| `activeChapterId` | 현재 챕터 | `null` |
| `chapterStates` | 챕터별 상태·단계 | 빈 맵 |
| `storyObjectStates` | 상호작용 오브젝트 상태 | 빈 맵 |
| `characterStates` | 인물 관계·생존·위치 등 | 빈 맵 |
| `storyFlags` | 명시적 서사 플래그 | 빈 맵 |

- 빈 Story 데이터는 오류가 아니다.
- Story 데이터가 없는 서버에서는 관련 GUI 탭과 메시지를 숨긴다.
- Story 상태가 손상되어도 전투·장비·Day·최종 목표 원장을 덮어쓰지 않는다.
- `STORY-001` 실제 콘텐츠는 별도 런타임 리비전으로 추가하고 활성 회차의 콘텐츠 리비전을 고정한다.

## 8. 난이도 스키마 마이그레이션

### v1 → v2

```text
if difficultySchemaVersion == 1:
  EASY      -> STORY
  NORMAL    -> EASY
  HARD      -> NORMAL
  CHALLENGE -> HARD
  UNKNOWN   -> UNKNOWN
```

1. 원본 난이도 ID와 스키마 버전을 감사 로그에 남긴다.
2. 새 난이도 ID와 파생 콘텐츠 분류를 한 트랜잭션으로 저장한다.
3. v1 `EASY`는 `STORY_CONTENT`, 나머지는 `CHALLENGE_CONTENT`로 분류한다.
4. 회차가 고정한 실제 수치 리비전은 바꾸지 않고 표시·라우팅 ID만 신 체계로 정규화한다.
5. 마이그레이션 완료 후 `difficultySchemaVersion: 2`를 저장한다.
6. 같은 회차의 재마이그레이션 요청은 기존 결과를 반환한다.

스키마 버전이 없는 레거시 데이터는 자동 변환하지 않는다. 콘텐츠 리비전, 생성 시각과 원본 백업을 검사한 뒤 운영자 승인 마이그레이션을 사용한다.

### 통계 호환

- 과거 통계에는 `sourceDifficultyId`와 `normalizedDifficultyId`를 함께 보존한다.
- v1 Challenge 기록은 v2 Hard 집계로 조회할 수 있지만 원본 라벨도 남긴다.
- 명칭 변경 전후의 실패율을 비교할 때 스키마 버전을 필터로 제공한다.

## 9. 플러그인 구현 경계

- 난이도 ID는 열거형과 스키마 검증을 사용하고 임의 문자열을 허용하지 않는다.
- 콘텐츠 분류는 난이도 ID로부터 서버가 파생하며 클라이언트 입력을 신뢰하지 않는다.
- `CHALLENGE`는 v1 마이그레이션 입력 외 신규 회차·설정·명령에서 거부한다.
- Story 이벤트 소비자는 읽기 전용 도메인 스냅샷을 받고 자체 상태만 갱신한다.
- Story 연출이 실패해도 핵심 도메인 트랜잭션을 롤백하지 않는다.
- 모든 핵심 선택과 Story 폴백은 인벤토리 GUI, 채팅 컴포넌트, 타이틀과 사운드로 구현한다.

## 10. 검증 항목

### 분류·수치

- 신규 회차에 난이도 5종만 노출되는가
- `CHALLENGE` 난이도 ID가 신규 데이터에서 거부되는가
- Story/Easy/Normal/Hard가 각각 구 Easy/Normal/Hard/Challenge 규칙을 계승하는가
- `UNKNOWN` 수치와 제한이 변경되지 않는가
- Easy/Normal/Hard/???가 모두 `CHALLENGE_CONTENT`로 분류되는가
- 규칙 모드가 콘텐츠 분류·난이도와 독립적으로 저장되는가
- Challenge STANDARD 야생 활동 EXP가 Easy 1.00, Normal 0.50~1.50, Hard 0.00~2.00, UNKNOWN 0.00~3.00 범위를 지키는가
- CHAOS의 등록 예산 도메인이 최대 10.00이고 안전 상한·고유 결과가 배율 대상에서 제외되는가

### 마이그레이션

- v1의 5개 ID가 정확히 v2 ID로 한 번만 변환되는가
- v1 `EASY`와 v2 `EASY`가 스키마 버전 없이 혼동되지 않는가
- 활성 회차의 실제 고정 수치 리비전이 명칭 변경으로 달라지지 않는가
- 과거 기록에 원본·정규화 ID가 모두 남는가

### Story 기반

- Story 콘텐츠 파일이 없어도 Story 회차를 시작·저장·완료할 수 있는가
- 빈 챕터·오브젝트·인물 상태가 정상값으로 처리되는가
- Story 소비자 오류가 전투·Day·보상 결과를 되돌리지 않는가
- 실패율 보고서가 유효 완료·실패 회차만 분모에 포함하는가
- Day 스킵 사용 여부와 파티 인원별 실패율을 분리할 수 있는가

## 11. 완료 기준

- Story와 Challenge 콘텐츠 분류의 책임이 구분되어 있다.
- Story, Easy, Normal, Hard, ???의 새 의미와 구 규칙 계승 관계가 정의되어 있다.
- Challenge 난이도가 삭제되고 `CHALLENGE` 신규 저장이 금지된다.
- Story + STANDARD 클리어 실패율 10%의 집계식과 표본 기준이 있다.
- Challenge 야생 활동 EXP 변동 범위와 CHAOS 전 예산 1000% 상한이 `BUDGET-001`로 고정된다.
- `STORY-001` 실제 Story와 `EMPTY` 무내용 폴백이 같은 시스템 계약에서 독립적으로 동작한다.
- 난이도 스키마 v1 데이터를 손실 없이 v2로 변환할 수 있다.
