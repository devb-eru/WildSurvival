# WildSurvival EVENT-DATA Day 21~50 사건 실행 데이터 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `EVENT-DATA-D50-001` |
| 상태 | `DATA_LOCKED` |
| 상위 기준 | `EVENT-LIST-001`, `CONTENT-DATA-D50-001`, `ENEMY-DATA-D50-001`, `RESOURCE-DATA-D50-001` |
| 적용 범위 | Day 21~49 주 사건·야간 압박, Day 30·40 보스 전환, Day 50 Final/대체 압박 |
| 수치 기준 | 위협은 2인 Easy STANDARD, 3·4인은 ×1.30/×1.60 |
| 데이터 리비전 | `event-data-d21-d50-r1` |
| 최종 수정일 | 2026-08-21 |

## 1. 편성 불변식

- 일반 Day마다 주 사건 1개와 압박 1개가 유효 후보로 존재한다. 보스 Day 30·40은 보스가 압박을 대체한다.
- Day 50은 Final을 시작하면 일반 사건·Day 타이머를 정지하고, 시작하지 않으면 `EV50-D50-READINESS`와 위협 100 압박을 실행할 수 있다.
- 주 사건은 필수 재료를 직접 지급하는 장치가 아니라 해금된 주·대체 경로를 선택하게 하는 실행 문맥이다.
- 적 비용이 활성 상한을 넘으면 3~5웨이브로 나누며 HP·피해 배율로 압축하지 않는다.
- 사건·압박·보스·Final은 각각 별도 RNG 스트림과 보상 멱등키를 가진다.

## 2. Day 21~30 편성 — 오염·변이

| Day | 주 사건 ID | 압박 프로필 | 2/3/4인 위협 | 필수 연결 | 주 보상 범주 |
|---:|---|---|---:|---|---|
| 21 | `EV50-D21-MOVING-BORDER` | `PR50-CORRUPTION-TRAIL` | 32/42/51 | C14, RS-D21 | 정화 촉매 |
| 22 | `EV50-D22-MUTATION-MIRROR` | `PR50-MUTATION-SPLIT` | 34/44/54 | 변이 비교 | 변이 표본 |
| 23 | `EV50-D23-PURIFIER-PARASITE` | `PR50-PURIFY-JAM` | 36/47/58 | C15~C17 | 균열 가루·촉매 |
| 24 | `EV50-D24-LEDGER-TRACK` | `PR50-CARGO-HUNT` | 38/49/61 | 운반 방어 | 금속·신호 |
| 25 | `EV50-D25-MUTATION-MAP` | `PR50-AUGMENTED-MUTATION` | 40/52/64 | RS-D25 | 정제 변이 |
| 26 | `EV50-D26-CONFLICTED-ORDER` | `PR50-DUAL-OBJECTIVE` | 42/55/67 | C18 | 신호·연구 기록 |
| 27 | `EV50-D27-RIFT-STABILITY` | `PR50-PURIFY-JAM` | 44/57/70 | C19, RS-D27 | 안정화 코어 |
| 28 | `EV50-D28-ABYSSAL-COST` | `PR50-ELITE-MUTATION` | 47/61/75 | 심연 대가 공개 | 정화 매질 |
| 29 | `EV50-D29-CORE-TRIANGULATION` | `PR50-BOSS-PRELUDE-30` | 50/65/80 | C20·호출 | 렌즈·호출 보충 |
| 30 | `BOSS-003` | `BOSS_REPLACES_PRESSURE` | 보스 | 부품 C | 보스 정산 |

## 3. Day 31~40 편성 — 브레이크·중단

| Day | 주 사건 ID | 압박 프로필 | 2/3/4인 위협 | 필수 연결 | 주 보상 범주 |
|---:|---|---|---:|---|---|
| 31 | `EV50-D31-BREAK-TELEMETRY` | `PR50-HEAVY-CHANNEL` | 50/65/80 | C21, RS-D31 | 경화 골재 |
| 32 | `EV50-D32-TAG-TRIAL` | `PR50-INTERRUPT-TAGS` | 53/69/85 | 중단 태그 | 공진 코일 |
| 33 | `EV50-D33-SIEGE-BORE` | `PR50-FACILITY-SIEGE` | 56/73/90 | C22 | 수리·골재 |
| 34 | `EV50-D34-METHOD-DECAY` | `PR50-PARRY-LEARN` | 59/77/94 | C23 | 패턴 잔재 |
| 35 | `EV50-D35-FOUR-AUTHORITIES` | `PR50-AUGMENTED-BREAK` | 62/81/99 | Lv35·기록 | 공진·연구 |
| 36 | `EV50-D36-RESIDUAL-FIELD` | `PR50-RESIDUAL-HAZARD` | 65/85/104 | C24 | 패턴 잔재 |
| 37 | `EV50-D37-BREAK-SUTURE` | `PR50-BREAK-HEALER` | 68/88/109 | 수단 교대 | 고밀도 합금 |
| 38 | `EV50-D38-PHASE-ELITE` | `PR50-PHASE-ELITE` | 72/94/115 | RS-D36 | 위상 기록·합금 |
| 39 | `EV50-D39-INTERRUPT-CORE` | `PR50-BOSS-PRELUDE-40` | 76/99/122 | C26·호출 | 중단 코어 |
| 40 | `BOSS-004` | `BOSS_REPLACES_PRESSURE` | 보스 | 부품 D | 보스 정산 |

## 4. Day 41~50 편성 — 복합 붕괴·재건

| Day | 주 사건 ID | 압박 프로필 | 2/3/4인 위협 | 필수 연결 | 주 보상 범주 |
|---:|---|---|---:|---|---|
| 41 | `EV50-D41-FOUR-PARTS` | `PR50-AUGMENT-HERALD` | 64/83/102 | C27, 연구 A | 안정 프레임 |
| 42 | `EV50-D42-POWER-TEST` | `PR50-RIFT-POWER` | 68/88/109 | C28-B | 동력 행렬 |
| 43 | `EV50-D43-CONTRADICTION` | `PR50-DUAL-AUGMENT` | 72/94/115 | C28-C/D | 렌즈·정화 행렬 |
| 44 | `EV50-D44-SPLIT-RETURN` | `PR50-SPLIT-FRONT` | 76/99/122 | 시설/유목 분기 | 안정 프레임 |
| 45 | `EV50-D45-BUILD-SUMMARY` | `PR50-FINAL-BUILD` | 80/104/128 | Lv45 접근 | 재련 전환 자원 |
| 46 | `EV50-D46-BOSS-ECHO` | `PR50-ECHO-ELITE` | 84/109/134 | 마지막 개인 증강 | 패턴 잔재 |
| 47 | `EV50-D47-RELAY-LINE` | `PR50-MOVING-RELAY` | 88/114/141 | RS-D47 | 동력·렌즈 |
| 48 | `EV50-D48-FOUR-SYSTEMS` | `PR50-RECONSTRUCTION-SIEGE` | 92/120/147 | C28 완료 | R01~R04 재료 |
| 49 | `EV50-D49-THREE-CALIBRATIONS` | `PR50-COLLAPSE-GATE` | 96/125/154 | C29·키 | 최종 신호 키 경로 |
| 50 | `EV50-D50-READINESS` 또는 `FINAL-001` | `PR50-FINAL-WAIT` 또는 정지 | 100/130/160 | Day 잠금·Final | 준비 보충, 새 고유품 없음 |

## 5. 주 사건 템플릿

### 5.1 오염 세션

| ID | 단계·목표 | 공간·전조 | 실패·대체 |
|---|---|---|---|
| `EV50-D21-MOVING-BORDER` | 이동 경계 3회 측정→전후 표본 2쌍 | 최근 이동 경로 3점, 20초 방향음 | 범용 오염 표식으로 전환, 표본 직접 지급 없음 |
| `EV50-D22-MUTATION-MIRROR` | 같은 계열 변이 2체 관측→다른 대응 축 2개 기록 | 변이 아이콘 2개 공개 | 처치 대신 90초 관측 가능 |
| `EV50-D23-PURIFIER-PARASITE` | 출력 흡수선 2개 절단→정화 30초 유지 | 시설 또는 휴대 정화기 중심 | 시설 없으면 FAC-P05 가상 출력점 2개 |
| `EV50-D24-LEDGER-TRACK` | 운반자 표식 이전 2회→추적자 제압 | 실제 아이템 강탈 없음 | 원장·상자 파괴 없음, 표식 시간 초과 후 재시도 |
| `EV50-D25-MUTATION-MAP` | 변이 3범주·2출처 등록→연구 큐 제출 | 지형 무관 Display 표본판 | 부족 범주는 EVT-C02 가중 +50% |
| `EV50-D26-CONFLICTED-ORDER` | 서로 상충한 두 신호 중 순차 안정화 | 동시 입력 요구 없음 | 한 축 완료 보존, 다음 Day 재후보 |
| `EV50-D27-RIFT-STABILITY` | 이동 균열 3회 추적→6초 봉합 | 방향 8분면·강도 3단계 | 놓치면 C04로 재후보, 자원 직접 생성 금지 |
| `EV50-D28-ABYSSAL-COST` | 심연 대가 3종 미리보기→고위험 표본 1개 회수 | 전투 전 비가역 경고 | 선택·제작을 강제하지 않음 |
| `EV50-D29-CORE-TRIANGULATION` | 3방향 신호 측정→호출 공간 검사 | 35초, 3말뚝 순차 가능 | 위치 실패 시 말뚝 무료 재배치 1회 |

### 5.2 브레이크 세션

| ID | 단계·목표 | 공간·전조 | 실패·대체 |
|---|---|---|---|
| `EV50-D31-BREAK-TELEMETRY` | 서로 다른 브레이크 수단 3개 기록 | 훈련 보상 없음 태그 대상 포함 가능 | 1인도 actionTag 3개로 완료 |
| `EV50-D32-TAG-TRIAL` | INTERRUPTIBLE/PARTIAL/UNBREAKABLE 각각 1회 대응 | 문자+아이콘+소리 | 실패 기록도 연구 비교에 인정, 보상은 성공분만 |
| `EV50-D33-SIEGE-BORE` | 시설 또는 이동 신호 2목표 중 하나 보호 | 건축 무차별 파괴 없음 | 시설 없으면 파티 표식 추적 모드 |
| `EV50-D34-METHOD-DECAY` | 같은 수단 2회→다른 수단 교대 | 감쇠 수치 HUD | 특정 무기 불필요, 회피 후 공격도 다른 축 |
| `EV50-D35-FOUR-AUTHORITIES` | 부품 A~D 기록 비교, 전투 중 2개 신호 보호 | 부품 소각·월드 드롭 없음 | 원장 불일치 시 사건 동결·감사 |
| `EV50-D36-RESIDUAL-FIELD` | 채널 중단→남은 장판 3개 안전 해제 | PARTIAL 아이콘 | 중단 성공이 장판 삭제로 오인되지 않게 표시 |
| `EV50-D37-BREAK-SUTURE` | 봉합자 채널 2회 중단→주요 적 브레이크 | 지원 1체 상한 | 봉합 회복은 게이지 15%/회, HP 회복 없음 |
| `EV50-D38-PHASE-ELITE` | 3페이즈 브레이크·내성 관측 | 페이즈 전환 3초 안전 | 게이지 0, 누적 내성 유지 |
| `EV50-D39-INTERRUPT-CORE` | 패턴 4종 기록→호출 세트 공간 검사 | 40초 보스 전조 | 호출 소비는 Day 40 전 금지 |

### 5.3 재건 세션

| ID | 단계·목표 | 공간·전조 | 실패·대체 |
|---|---|---|---|
| `EV50-D41-FOUR-PARTS` | A~D 공명선 4개 순차 확인 | 시설 또는 휴대 원장 | 부품은 존재 증명만, 손상 없음 |
| `EV50-D42-POWER-TEST` | 출력 60초 중 45초 유효 유지 | FAC-R02 또는 시험 프레임 | 실패 축 공개, 재료 추가 소각 없음 |
| `EV50-D43-CONTRADICTION` | 상충 증강 2쌍 판독→안전 조합 선택 | 선택은 전투 프로필에만 | 영구 증강 변경 없음 |
| `EV50-D44-SPLIT-RETURN` | 시설/파티 2목표를 순차 또는 분담 방어 | 1인 순차 시간 +30초 | 두 목표 동시 입력 금지 |
| `EV50-D45-BUILD-SUMMARY` | 최종 태그 요약 확인→재련 전환 1회 미리보기 | 성능 평가·순위 없음 | 선택하지 않아도 완료 |
| `EV50-D46-BOSS-ECHO` | 이전 보스 축 1개 축약 정예 대응 | 원본보다 피해·복잡도 낮음 | 보스 고유 보상·부품 없음 |
| `EV50-D47-RELAY-LINE` | 이동 중계 3점 연결→60초 유지 | 경로 40~80m, 강제 고정 기지 없음 | 휴대 중계기 3개 대체 |
| `EV50-D48-FOUR-SYSTEMS` | R01~R04 시험 중 3개 성공→나머지 복구 | 동시 작업 2개 상한 | 한 축 실패가 완료 축을 초기화하지 않음 |
| `EV50-D49-THREE-CALIBRATIONS` | 방향·출력·정화 오차 3축 교정 | 세 말뚝 순차 | 3회 실패 시 오차 범위·축 전부 공개 |
| `EV50-D50-READINESS` | 누락 범주 검사→해금 경로 1개 강화 | Final 시작 전만 | Final 키·부품·C30 직접 지급 없음 |

## 6. 압박 프로필

| 프로필 | 핵심 적 풀 | 단계 | 상한·금지 |
|---|---|---:|---|
| `PR50-CORRUPTION-TRAIL` | D21·D22 + CHASER | 3 | 오염 흔적 2개 |
| `PR50-MUTATION-SPLIT` | D22 + 일반 두 역할 | 3 | 변이 2축, 강한 CC 1 |
| `PR50-PURIFY-JAM` | D23·D27 + 지원 | 3 | 지원 1, 방해선 1 |
| `PR50-CARGO-HUNT` | D24 + FLANKER | 3 | 공성 1, 아이템 강탈 금지 |
| `PR50-AUGMENTED-MUTATION` | D28-E01 + 일반 | 3 | 정예 1, 상충 조합 금지 |
| `PR50-DUAL-OBJECTIVE` | D24·D27 | 3 | 시설/파티 순차 가능 |
| `PR50-ELITE-MUTATION` | D28-E01 + D21~27 | 4 | 정예 1, 활성 12 |
| `PR50-BOSS-PRELUDE-30` | D27·D28-E01 | 4 | 보스 패턴 복제 금지 |
| `PR50-HEAVY-CHANNEL` | D31 + DEFENDER | 3 | 채널 1개 |
| `PR50-INTERRUPT-TAGS` | D31·D34 | 3 | 중단 불가+강한 CC 동시 금지 |
| `PR50-FACILITY-SIEGE` | D33 + 일반 | 4 | 공성 1, 건축 파괴 금지 |
| `PR50-PARRY-LEARN` | D34 + D31 | 4 | 수단 감쇠 공개 |
| `PR50-AUGMENTED-BREAK` | D38-E01 + D31~37 | 4 | 정예 1, 지원 1 |
| `PR50-RESIDUAL-HAZARD` | D31·D37 | 4 | 지속 영역 4 |
| `PR50-BREAK-HEALER` | D37 + D33 | 4 | 봉합자 1 |
| `PR50-PHASE-ELITE` | D38-E01 + 일반 | 4 | 페이즈 전환 중 새 웨이브 금지 |
| `PR50-BOSS-PRELUDE-40` | D37·D38-E01 | 4 | 정예 1, 전조 3 |
| `PR50-AUGMENT-HERALD` | D41 + 일반 | 3 | 적 증강 2, 같은 축 금지 |
| `PR50-RIFT-POWER` | D41 + D47 | 4 | 중계 차단 1 |
| `PR50-DUAL-AUGMENT` | D41 + D44 | 4 | 강한 CC 증강 2개 금지 |
| `PR50-SPLIT-FRONT` | D44 + D41 | 4 | 공성 1, 순차 가능 |
| `PR50-FINAL-BUILD` | D41·D47 | 4 | 새 수직 성장 없음 |
| `PR50-ECHO-ELITE` | D46-E01 + 일반 | 5 | 정예 1, 원본 보스보다 약함 |
| `PR50-MOVING-RELAY` | D47 + CHASER | 5 | 이동 봉쇄+정화 봉쇄 금지 |
| `PR50-RECONSTRUCTION-SIEGE` | D44·D47 + D46-E01 | 5 | 정예 1·공성 1·지원 1 |
| `PR50-COLLAPSE-GATE` | D49-E01 + D41~47 | 5 | 정예 1, 3축 중 2개만 |
| `PR50-FINAL-WAIT` | D49-E01 + 후반 일반 | 5 | Final 활성 즉시 취소·정리 |

### 6.1 웨이브 분할 공식

```text
waveCap = min(24, ceil(lockedThreat / minimumWaveCount))
minimumWaveCount =
  threat <= 48 ? 3 :
  threat <= 88 ? 4 : 5
```

- 한 웨이브 비용이 `waveCap`을 넘지 않게 적 비용을 내림차순으로 채운다.
- 남은 비용이 최소 적 비용보다 작으면 다음 웨이브의 동일 프로필 `patternBudget`으로 이월한다. HP·피해를 올리지 않는다.
- 4인 Day 49의 154도 활성 일반 12·정예 2·공성 1·지원 1 상한 안에서 5웨이브로 처리한다.

## 7. 보상·진행 원장

- Day별 EXP·자원 합은 `CONTENT-DATA-D50-001` §3~5를 그대로 사용한다.
- 주 사건은 활동 EXP의 최대 35%, 압박은 진행 EXP의 최대 38%를 소유한다. 나머지는 발견·Day·보스·필수 활동 원천이다.
- 전문 자원 보상은 해당 Day `특` 예산의 최대 45%이며 `RESOURCE-DATA-D50-001`의 노드·가공 원천과 합쳐 Day 합을 넘지 않는다.
- 전문·고유품·부품 A~D·심연 장비·최종 신호 키는 일반 개인 기여품으로 전환하지 않는다.
- Day 51+ 재사용은 새 진행·고유 보상을 만들지 않고 누락 준비 범주의 일반·전문 경로 가중치만 높인다.

## 8. 실패·복구

| 상태 | 처리 |
|---|---|
| 주 사건 실패 | 완료 단계·연구 관측 보존, 미완 단계 보상 없음, 2 Day 내 대체 후보 |
| 압박 잔존 | 주요 적·오염 원점만 다음 Day 이월, 새 보상 ID 생성 안 함 |
| 시설 파괴 | 고유 부품·완료 연구 보존, 일반 복구 비용만 유지 |
| 공간 후보 3회 실패 | 범용 Display 실행으로 전환 |
| 필수 범주 80% 미만 | 같은 총예산에서 해당 후보를 강제, 아이템 직접 지급 금지 |
| Day 49 교정 3회 실패 | 정답 자동 완료 없이 모든 오차 축·허용 범위 공개 |
| Final 활성 | 일반 사건·압박 타이머 정지, 소환체·투사체 안전 정리 |

## 9. 저장·검증

저장에는 `templateId`, `profileId`, `lockedThreat`, `wavePlan`, `objectiveState`, `arenaManifestId`, `spawnedEntities`, `rngIndex`, `rewardState`, `contentRevision`을 둔다.

필수 테스트:

- `T-EV50-001`: Day 21~29·31~39·41~50 각각 주 사건과 압박 후보 존재
- `T-EV50-002`: 표의 2/3/4인 예산과 3~5웨이브 비용 합 일치
- `T-EV50-003`: 활성 일반 12·정예 2·공성 1·지원 1·영역 4·전조 3 상한
- `T-EV50-004`: 시설 없음·1인 잔존·범용 지형에서 모든 핵심 사건 완료
- `T-EV50-005`: Day 30·40 일반 압박 미생성, Day 50 Final과 일반 사건 상호 배제
- `T-EV50-006`: 실패·재시작·청크 언로드 후 증거·보상·적 중복 없음
- `T-EV50-007`: 필수 자원 경로 0 보정이 총예산·Day 50 잠금을 늘리지 않음
- `T-EV50-008`: Day 51+ 재사용이 새 고유 진행을 만들지 않음

Day 21부터 Final 시작 전까지 매 Day 실제 사건 ID·목표·공간·압박·보상·실패·복구 참조가 닫히면 완료다.
