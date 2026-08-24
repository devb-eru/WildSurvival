# WildSurvival 문서 권위 및 상태 레지스트리

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `DOC-AUTHORITY-001` |
| 상태 | `ACTIVE_CONTROL` |
| 역할 | 문서 ID, 권위, 상태, 대체 관계와 구현 착수 가능 여부의 단일 원장 |
| 상위 기준 | `VISION-001`, `PLAN-AUDIT-001` |
| 갱신 책임 | 기획 리드. 문서 추가·폐기·게이트 변경과 같은 커밋에서 함께 갱신 |
| 최종 수정일 | 2026-08-24 |

## 1. 권위 판정 규칙

충돌은 아래 순서로 해결한다. 같은 단계끼리 충돌하면 더 구체적인 범위를 소유한 문서를 우선하고, 해결되지 않으면 구현을 중단한 뒤 이 레지스트리에서 권위자를 지정한다.

1. `VISION-001`, 사용자 확정 요구, `RULES-001`
2. 이 문서와 `PLAN-AUDIT-001`, `ROADMAP-001`, `DEV-ROADMAP-001`
3. 회차·난이도·Day·공통 시스템 계약
4. 도메인별 상세 시스템 계약
5. `*-DATA-*`, 목록, 밸런스 원장
6. 런타임 콘텐츠 리비전과 구현 인계 계약

런타임에서는 활성 회차가 잠근 `contentRevision`, `storyRevision`, `budgetPolicyRevision`이 최신 문서보다 우선한다. 문서 갱신은 진행 중 회차를 소급 변경하지 않는다.

## 2. 상태 값

| 상태 | 의미 | 구현 사용 |
|---|---|---|
| `ACTIVE_CONTROL` | 현재 권위·로드맵·감사 통제 문서 | 가능 |
| `BASELINE_LOCKED` | 상위 규칙과 상태 전이가 확정된 기준 문서 | 가능 |
| `DATA_LOCKED` | ID·수치·참조가 고정된 실행 데이터 원장 | 가능 |
| `VALIDATED_STATIC` | 정적 원장과 경계값 검증을 통과 | 구현 전 기준으로 가능 |
| `IMPLEMENTATION_CONTRACT` | 스키마·모듈·테스트·인계 조건 확정 | 가능 |
| `IMPLEMENTED_PROTOTYPE` | 프로토타입 코드·자동 검증·런타임 스모크가 존재하나 폐쇄 E2E 승격 전 | 프로토타입·개발 서버에서만 가능 |
| `PARTIAL_R1` | 한정 구간만 실행 데이터로 고정된 이전 리비전 | 해당 구간·기존 회차만 가능 |
| `PLANNED_G1`~`PLANNED_G4` | 지정 게이트에서 작성할 공식 위임 대상 | 작성 전 구현 판단 금지 |
| `S2_FOUNDATION` | Season 2 방향 기반이며 S1 구현 권위가 아님 | S1 구현에 사용 금지 |
| `SUPERSEDED` | 대체 문서가 존재하며 신규 구현 참조 금지 | 불가 |

`진행 중`, `초안`, `후속`, `별도 확정` 같은 자유 서술은 문서 상태로 사용하지 않는다. 본문에 남는 “진행 중”은 런타임 상태를 뜻한다.

## 3. 현재 권위 레지스트리

### 3.1 프로젝트·회차

| ID | 상태 | 권위 범위 | 구현 게이트 |
|---|---|---|---|
| `INDEX-001` | `ACTIVE_CONTROL` | 폴더·문서 색인 | G0 |
| `VISION-001` | `BASELINE_LOCKED` | 프로젝트 불변 원칙 | G0 |
| `RULES-001` | `BASELINE_LOCKED` | 바닐라 상호작용 허용·교체 | G0 |
| `ROADMAP-001` | `ACTIVE_CONTROL` | 작업 순서·게이트 | G0 |
| `PLAN-AUDIT-001` | `ACTIVE_CONTROL` | 기획 공백·완료 증거 | G0 |
| `DOC-AUTHORITY-001` | `ACTIVE_CONTROL` | ID·상태·권위·대체 관계 | G0 |
| `PRODUCTION-COMPLETION-PLAN-001` | `ACTIVE_CONTROL` | 전 고정 ID의 실제 플레이 전환 순서·개별 완료 판정 | DEV-300~800 |
| `CONTENT-CATALOG-INDEX-001` | `DATA_LOCKED` | 생산 콘텐츠 ID 합계·소유 문서·구현 파일 통합 색인 | DEV-300~800 |
| `PRODUCTION-DATA-CLOSURE-001` | `ACTIVE_CONTROL` | 목록의 미확정 실행 수치·비용·연구 증거 폐쇄와 사용자 결정 분리 | DEV-300~800 |
| `AUTHOR-DECISION-REGISTER-001` | `ACTIVE_CONTROL` | 부활 정책·Story 본문 작성 방식의 사용자 확정 게이트 | DEV-300·700~800 |
| `CONTENT-MASTER-001` | `BASELINE_LOCKED` | Day 1~50+ 상위 진행 | G0 |
| `GAME-001~003` | `BASELINE_LOCKED` | 타이머·저장·인원 스케일 | G0 |
| `DAY-001` | `BASELINE_LOCKED` | Day 전환·스킵·완료 | G0 |
| `DIFFICULTY-001` | `BASELINE_LOCKED` | 콘텐츠 분류·난이도 | G0 |
| `BUDGET-001` | `BASELINE_LOCKED` | 예산 허용 범위·계산 경계 | G0 |
| `FINAL-001` | `BASELINE_LOCKED` | Day 50+ 최종 목표 상태 전이 | G0 |

### 3.2 성장·전투·경제

| ID | 상태 | 권위 범위 | 구현 게이트 |
|---|---|---|---|
| `ACT-001` | `BASELINE_LOCKED` | 활동·전략·연구 포커스 접점 | G0 |
| `PROG-001~002` | `BASELINE_LOCKED` | 레벨·EXP·투자 | G0 |
| `STAT-001~002` | `BASELINE_LOCKED` | 전투 스탯·계산 | G0 |
| `AUG-001` | `BASELINE_LOCKED` | 개인·파티 증강 드로우 | G0 |
| `AUG-LIST-001~002` | `DATA_LOCKED` | 증강 후보 풀 | G0 |
| `SKILL-001~002` | `BASELINE_LOCKED` | 스킬 실행·범위·백엔드 | G0 |
| `SKILL-LIST-001` | `DATA_LOCKED` | 스킬 ID 목록 | G0 |
| `DEATH-001~003` | `BASELINE_LOCKED` | 빈사·구조·사망·부활 | G0 |
| `CORE-001~002` | `BASELINE_LOCKED` | 서버 권위 피해·일반 공격 | G0 |
| `COMBAT-001~004` | `BASELINE_LOCKED` | AP·회피·방어·패링 | G0 |
| `STATUS-001~002` | `BASELINE_LOCKED` | 상태·CC 보호 | G0 |
| `BREAK-001~002` | `BASELINE_LOCKED` | 브레이크·그로기·중단 | G0 |
| `WEAPON-001` | `BASELINE_LOCKED` | 10개 무기군 역할 | G0 |
| `WEAPON-002` | `DATA_LOCKED` | 무기 실행 기초값 | G0 |
| `EQUIP-001` | `BASELINE_LOCKED` | 장착·슬롯·인스턴스 | G0 |
| `EQUIP-LIST-001` | `DATA_LOCKED` | Day 1~20 장비·후반 방향 | G0 |
| `ECONOMY-001` | `BASELINE_LOCKED` | 공급·소비·강화·재련 | G0 |
| `CRAFT-001` | `DATA_LOCKED` | 제작·가공 레시피 | G0 |
| `ITEM-LIST-001` | `DATA_LOCKED` | 비장비 아이템 61개 ID·도감 위치·기본 효과 | DEV-300~400 |
| `MATERIAL-LIST-001` | `DATA_LOCKED` | 재료 59개 ID·등급·획득·용도 | DEV-300~400 |
| `RECIPE-LIST-001` | `DATA_LOCKED` | 제작식 315개 ID·출력·소유 권위 | DEV-300~400 |
| `TOOL-LIST-001` | `DATA_LOCKED` | 장비·도구 214개 ID·장착 분류 | DEV-300~500 |
| `WEAPON-EQUIPMENT-LIST-001` | `DATA_LOCKED` | TOOL 214개 중 주무기108·보조무기11 분리 투영 | DEV-300~500 |
| `ARMOR-ACCESSORY-LIST-001` | `DATA_LOCKED` | TOOL 214개 중 방어구45·장신구24·부적10 분리 투영 | DEV-300~500 |
| `UTILITY-TOOL-LIST-001` | `DATA_LOCKED` | TOOL 214개 중 상위 채집 도구16 분리 투영 | DEV-300~500 |
| `HIGH-TIER-MATERIAL-LIST-001` | `DATA_LOCKED` | MATERIAL 59개 중 T3~T6 상위 재료23 분리 투영 | DEV-300~500 |
| `LOOT-LIST-001` | `DATA_LOCKED` | 획득·드롭 62개 ID·보상 경계 | DEV-400~700 |
| `CONSUMABLE-RUNTIME-STATUS-001` | `ACTIVE_CONTROL` | 소모품 13개 DATA·RUNTIME·TEST 개별 상태 | DEV-300~400 |
| `ITEM-RUNTIME-STATUS-001` | `ACTIVE_CONTROL` | 탄약·휴대 장치·시설 키트·호출품 48개 개별 상태 | DEV-300~700 |
| `REWARD-001` | `BASELINE_LOCKED` | 기여·분배·고유 보상 | G0 |

### 3.3 세계·사건·보스·Story

| ID | 상태 | 권위 범위 | 구현 게이트 |
|---|---|---|---|
| `BASE-001~003` | `BASELINE_LOCKED` | 이동형 시설망·공세 접점 | G0 |
| `RES-001` | `BASELINE_LOCKED` | 자원·채집·재생 | G0 |
| `DISC-001` | `BASELINE_LOCKED` | 발견 상태·소급 판정 | G0 |
| `DISC-LIST-001` | `DATA_LOCKED` | C01~C30 발견 노드 | G0 |
| `FACILITY-LIST-001` | `DATA_LOCKED` | 시설 ID·역할 | G0 |
| `CORR-001~003` | `BASELINE_LOCKED` | 오염 수치·확산·정화 | G0 |
| `CORR-LIST-001` | `DATA_LOCKED` | 오염 변이 목록 | G0 |
| `EVENT-LIST-001` | `DATA_LOCKED` | 사건 유형·후보 정책 | G0 |
| `ENTITY-LIST-001` | `DATA_LOCKED` | 적·보스·지원 개체 91개 ID·수명·보상 경계 | DEV-600~700 |
| `ENEMY-ACTION-LIST-001` | `DATA_LOCKED` | 적 53종 행동 bundle 53개·행동 69개 영구 ID와 실행 프로필 | DEV-600~700 |
| `ENEMY-001` | `BASELINE_LOCKED` | 적 역할·템플릿 | G0 |
| `BOSS-001~004` | `BASELINE_LOCKED` | Day 10·20·30·40 보스 설계 | G0 |
| `SYSTEM-AUDIT-001` | `ACTIVE_CONTROL` | Story 선행 시스템 경계 | G0 |
| `STORY-001` | `BASELINE_LOCKED` | Season 1 장면·대사·결말 | G0 |
| `STORY-S2-001` | `S2_FOUNDATION` | Season 2 방향 기반 | S2 승인 후 |

### 3.4 기존 부분 데이터·기술

| ID | 상태 | 권위 범위 | 구현 게이트 |
|---|---|---|---|
| `EVENT-DATA-001` | `PARTIAL_R1` | Day 1~10 사건 실행 데이터 | 기존 r1·G2 입력 |
| `RESOURCE-DATA-D20-001` | `PARTIAL_R1` | Day 11~20 자원 데이터 | 기존 r1·G2 입력 |
| `EQUIP-DATA-D20-001` | `PARTIAL_R1` | Day 11~20 장비 데이터 | 기존 r1·G2 입력 |
| `ENEMY-DATA-D20-001` | `PARTIAL_R1` | Day 11~20 적 데이터 | 기존 r1·G2 입력 |
| `BALANCE-D10-001` | `VALIDATED_STATIC` | Day 1~10 정적 원장 | G0 |
| `BALANCE-D20-001` | `VALIDATED_STATIC` | Day 11~20 정적 원장 | G0 |
| `CONTENT-DATA-D50-001` | `BASELINE_LOCKED` | Day 21~50 통합 기준 수치 | G0 |
| `TECH-001` | `IMPLEMENTATION_CONTRACT` | Paper 모듈·저장·성능 | G0 |
| `OPS-001` | `IMPLEMENTATION_CONTRACT` | 명령·권한·복구 | G0 |
| `DATA-REVISION-001` | `PARTIAL_R1` | Day 1~20 `ws-content-r1` | 기존 r1만 |
| `DEV-ROADMAP-001` | `ACTIVE_CONTROL` | 프로토타입 우선 구현 단계·승인 게이트 | DEV-000~800 |
| `TEST-LAB-001` | `IMPLEMENTED_PROTOTYPE` | 격리 솔로 테스트·튜닝·가상 파티 계약 | `ws-prototype-r1` |

## 4. 공식 위임·완료 레지스트리

아래 ID는 자유 서술형 “후속 문서”가 아니라 게이트에 귀속된 공식 위임이다. 같은 책임을 다른 문서가 중복 소유하지 않는다.

| 게이트 | ID | 책임 | 현재 상태 |
|---|---|---|---|
| G1 | `RESEARCH-001` | 연구·분석·대응책·도감·대기열 | `BASELINE_LOCKED` |
| G1 | `UX-001` | 통합 GUI·HUD·접근성 | `BASELINE_LOCKED` |
| G1 | `PARTY-SYNERGY-001` | 파티 증강·생존 인원·사망 전후 보정 | `BASELINE_LOCKED` |
| G2 | `EVENT-DATA-D20-001` | Day 11~20 사건 | `DATA_LOCKED` |
| G2 | `EVENT-DATA-D50-001` | Day 21~50 사건 | `DATA_LOCKED` |
| G2 | `ENEMY-DATA-D50-001` | Day 21~50 적·정예·공성·소환체 | `DATA_LOCKED` |
| G2 | `RESOURCE-DATA-D50-001` | Day 21~50 전문 자원·처리 | `DATA_LOCKED` |
| G2 | `EQUIP-DATA-D50-001` | Day 21~50 장비·옵션·심연 대가 | `DATA_LOCKED` |
| G2 | `FACILITY-DATA-D50-001` | Day 11~50 시설 단계·처리량·비용 | `DATA_LOCKED` |
| G2 | `BOSS-DATA-D50-001` | Day 30·40 보스 실행 데이터 | `DATA_LOCKED` |
| G2 | `FINAL-DATA-001` | Day 50+ 최종 목표 실행 데이터 | `DATA_LOCKED` |
| G2 | `STORY-DATA-S1-001` | Season 1 Story 런타임 데이터 | `DATA_LOCKED` |
| G3 | `BUDGET-PROFILE-001` | 라이브 예산 선택·상관 프로필 | `VALIDATED_STATIC` |
| G3 | `BALANCE-CHAOS-001` | CHAOS 극단값·분할·정수 경계 | `VALIDATED_STATIC` |
| G3 | `BALANCE-WEAPON-001` | 10개 무기·스킬·증강 통합 원장 | `VALIDATED_STATIC` |
| G3 | `BALANCE-D50-001` | Day 21~50 세션 통합 원장 | `VALIDATED_STATIC` |
| G3 | `BALANCE-MATRIX-001` | 난이도·1~4인·사망·TPS 검증 행렬 | `VALIDATED_STATIC` |
| G3 | `QA-BALANCE-001` | 텔레메트리·조정·승인·롤백 | `VALIDATED_STATIC` |
| G4 | `DATA-REVISION-002` | Season 1 전체 콘텐츠 번들 계약 | `IMPLEMENTATION_CONTRACT` |
| G4 | `IMPLEMENTATION-HANDOFF-001` | 모듈·티켓·테스트 픽스처 인계 | `IMPLEMENTATION_CONTRACT` |

G1~G4 문서 작성은 완료됐다. G3의 `VALIDATED_STATIC`은 공식·참조·경계값 검증 상태이며 구현 후 L1~L5 플레이테스트 승인을 뜻하지 않는다. G4의 현재 후보 상태는 `CONTRACT_READY`이고 실제 `ws-content-r2` 번들 빌드는 구현 작업 `IMP-001`이다.

## 5. 대체·보존 규칙

- `DATA-REVISION-002`는 `DATA-REVISION-001`을 삭제하거나 수정하지 않는다. 신규 회차 기본값만 r2로 바꾸고 활성 r1 회차는 r1을 계속 사용한다.
- G2 도메인 문서는 `CONTENT-DATA-D50-001`의 상위 합계를 대체하지 않는다. 개별 ID·수치의 세부 원장 역할을 맡는다.
- `STORY-DATA-S1-001`이 만들어져도 `EMPTY`는 유효 폴백 리비전으로 남는다.
- `BALANCE-*`는 플레이테스트 조정 순서를 정하지만 진행 중 회차의 잠긴 값을 소급 변경하지 않는다.
- Season 2 문서는 Season 1 상태·수치·결말을 덮어쓰지 않고 `seasonBridgeSnapshot`만 입력으로 사용한다.

## 6. G0 종료 증거

- 모든 직접 위임 ID가 이 문서의 공식 위임 레지스트리에 있다.
- 오래된 “다음 작업은 RES” 문구는 현재 로드맵과 이 문서를 가리키도록 교체한다.
- 기존 핵심 문서의 자유 상태값 `진행 중`은 `BASELINE_LOCKED`로 정규화한다.
- 문서 상태와 구현 가능 여부는 파일 위치나 마지막 본문 문장 대신 이 문서에서 판정한다.
- 새 문서는 문서 ID·상태·상위 기준·적용 범위·최종 수정일을 반드시 가진다.

## 7. G1~G4 종료 증거

| 게이트 | 종료 증거 |
|---|---|
| G1 | 연구·통합 UX·파티 증강이 각각 단일 권위 문서와 저장·오류·1인 분기를 가짐 |
| G2 | Day 11~50 사건과 Day 21~50 적·자원·장비·시설·보스·Final·Story가 실행 ID로 닫힘 |
| G3 | 라이브 예산·CHAOS 극단·10무기·후반 세션·5난이도×1~4인×모드·QA 원장이 정적 검증됨 |
| G4 | r2 47파일 목표·잠금 튜플·검증 게이트와 17개 구현 패키지가 기계 판독 레지스트리에 연결됨 |
