# WildSurvival Season 1 비소모 아이템 구현 상태 원장

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `ITEM-RUNTIME-STATUS-001` |
| 상태 | `ACTIVE_CONTROL` |
| 범위 | 탄약 5, 휴대 장치 7, 시설 키트 32, 보스 호출품 4 |
| 상위 기준 | `ITEM-LIST-001`, `FACILITY-LIST-001`, `RECIPE-LIST-001`, `PRODUCTION-COMPLETION-PLAN-001` |
| 데이터 리비전 | `item-s1-r2` / `ws-content-r2` |
| 최종 수정일 | 2026-08-24 |

소모품 13개는 `CONSUMABLE-RUNTIME-STATUS-001`이 따로 소유한다. 이 문서의 `VERIFIED`는 데이터가 존재한다는 뜻이 아니라 정상 사용, 거부, 소비, 저장·복구, 자동 테스트와 필요한 실제 클라이언트 E2E가 모두 통과했다는 뜻이다.

## 1. 상태 정의

| 상태 | 의미 |
|---|---|
| `DATA_VERIFIED` | 카테고리별 필드와 recipe·시설 참조가 L0 검증을 통과 |
| `PARTIAL_RUNTIME` | 정상 경로 일부가 실행되지만 고유 효과·비용·복구 중 하나 이상 미완성 |
| `RUNTIME_MISSING` | 등록·제작은 가능하나 사용 실행기가 없음 |
| `BLOCKED_DATA` | 정확 수치·비용·증거 계약이 없어 안전 정지 |
| `BLOCKED_E2E` | 서버 코드는 있으나 실제 클라이언트 입력·블록 상호작용 증거가 없음 |
| `VERIFIED` | ID별 DATA/RUNTIME/TEST/E2E 완료 |

## 2. 2026-08-24 데이터 보정

- 기존 생성기는 소모품 표의 열 위치를 61개 전체에 적용해 휴대 장치·시설 키트·호출품 43개의 `stackLimit/effectText/recipeId`를 잘못 만들었다.
- `ITEM-LIST-001 item-s1-r2`는 야영 키트 최초 Day와 호출품 스택을 명시하고, ID 범위별 `ownership/usePolicy/connectedFacilityId` 계약을 고정했다.
- 생성기는 카테고리별 행 모양을 강제하며 `textKey/customModelKey/recipeId`를 만든다. 행 수나 열 위치가 다르면 번들 생성을 중단한다.
- L0 validator는 범주 수 `13/5/7/32/4`, recipe 존재, 소유권, 사용 정책, 시설 연결과 호출품 스택 1을 검사한다.

## 3. 탄약 5개

| ID | DATA | 현재 RUNTIME | 남은 완료 조건 | 판정 |
|---|---|---|---|---|
| `WSI-AMMO-ARROW_BUNDLE` | 통과 | slot1~8 우클릭 전량 입금·저장, 활/석궁·순간 장전 우선 소비, HUD 잔량, 바닐라 화살 폴백 | 실제 우클릭·재접속·동시 소비 E2E | `PARTIAL_RUNTIME` |
| `WSI-AMMO-PIERCING_BOLT_BUNDLE` | 통과 | 실행기 없음 | 석궁 전용 선택, 첫 적중 PEN 수치, 소비·복구 | `BLOCKED_DATA` |
| `WSI-AMMO-PURIFY_ARROW_BUNDLE` | 통과 | 실행기 없음 | 호환 무기, 정화 취약 판정, 자원 생성 0 검증 | `PARTIAL_RUNTIME` |
| `WSI-AMMO-RESONANCE_BOLT_BUNDLE` | 통과 | 실행기 없음 | 석궁 전용, INTERRUPTIBLE 추가 브레이크 수치 | `BLOCKED_DATA` |
| `WSI-AMMO-STABILIZER_DART_BUNDLE` | 통과 | 실행기 없음 | 발사 무기, 아군·시설 표적, 완화 1단·대상별 20초 | `BLOCKED_DATA` |

탄약 아이템 1개는 1발이다. 제작식 출력 16/8/8/8/4가 원장 충전량이며 아이템 한 개를 다시 16발 또는 8발로 곱하지 않는다. 이 해석은 `RECIPE-LIST-001`의 실제 출력 수량과 소프트락 예산을 우선한다.

## 4. 휴대 장치 7개

| ID / 시설 | DATA | 현재 RUNTIME | 남은 완료 조건 | 판정 |
|---|---|---|---|---|
| `WSI-PORTABLE-CRAFT_KIT` / P01 | 통과 | Craft GUI 연결·최초 사용 시 장치 ID 부여 | 저효율 시간·희귀 이상 차단 | `PARTIAL_RUNTIME` |
| `WSI-PORTABLE-SAMPLE_EXTRACTOR` / P03 | 통과 | 안내 문구만 출력 | 채널·피격/이동 취소·표본 결과·증거 저장 | `PARTIAL_RUNTIME` |
| `WSI-PORTABLE-ANALYZER` / P04 | 통과 | 시설 수·위협 요약 | 단일 작업 큐·150% 시간·증거 비교·소급 판정 | `PARTIAL_RUNTIME` |
| `WSI-PORTABLE-PURIFIER` / P05 | 통과 | 아이템별 `portableInstanceId`, 다중 60초 인스턴스·반경 5·최근접 장치 충전 연장 | 지속 오염 압력 완화·재시작 E2E | `PARTIAL_RUNTIME` |
| `WSI-PORTABLE-SIGNAL_STAKE` / P06 | 통과 | 배치별 `portableInstanceId`, 동일 플레이어 다중 블록 설치·회수 | 3개 거리·무게중심·측정·전장 Manifest 연결 | `PARTIAL_RUNTIME` |
| `WSI-PORTABLE-RESCUE_BEACON` / P07 | 통과 | 300초 반경 12·구조속도 1.15배 | 전투당 1회·도움 표식·재시작·실클라 입력 | `PARTIAL_RUNTIME` |
| `WSI-PORTABLE-LEDGER` / P08 | 통과 | FAC-S16 ACTIVE일 때 원장 GUI 연결 | 안전 상태 소량 예약 상한·원격 권한·실클라 E2E | `PARTIAL_RUNTIME` |

`FAC-P02`는 별도 휴대 아이템이 아니라 `WSI-CONS-REPAIR_KIT`를 사용하므로 소모품 상태 원장에서 추적한다.

## 5. 시설 키트 32개

모든 키트는 현재 Day·공간·유형 상한을 검사하고, 설치 성공 시 1개를 소비하며 예외 시 블록과 키트를 롤백한다. 다만 실제 클라이언트 블록 설치를 통과하지 않았고 시설 기능의 완료 여부는 별도 시설 상태 원장에 따른다.

| ID 범위 | 개별 ID | DATA | 아이템 설치 런타임 | 판정 |
|---|---|---|---|---|
| 야영 | `WSI-FAC-C01-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 야영 | `WSI-FAC-C02-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 야영 | `WSI-FAC-C03-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 야영 | `WSI-FAC-C04-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 야영 | `WSI-FAC-C05-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 야영 | `WSI-FAC-C06-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 야영 | `WSI-FAC-C07-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 야영 | `WSI-FAC-C08-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S01-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S02-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S03-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S04-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S05-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S06-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S07-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S08-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S09-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S10-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S11-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S12-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S13-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S14-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S15-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S16-KIT` | 통과 | 최초 ACTIVE 뒤 공용 원장 해금 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S17-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S18-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S19-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 정착 | `WSI-FAC-S20-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 방어 | `WSI-FAC-D01-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 방어 | `WSI-FAC-D02-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 방어 | `WSI-FAC-D03-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |
| 방어 | `WSI-FAC-D04-KIT` | 통과 | 설치·소비·롤백 구현 | `BLOCKED_E2E` |

## 6. 보스 호출품 4개

| ID | DATA | 현재 RUNTIME | 남은 완료 조건 | 판정 |
|---|---|---|---|---|
| `WSI-CALL-D10` | 통과 | 제작·도감만 가능 | C05~C07·말뚝 3·전장 검사·Manifest·원자 소비/생성 | `RUNTIME_MISSING` |
| `WSI-CALL-D20` | 통과 | 제작·도감만 가능 | C11~C13·부품 A/D10 증명·전장·원자 롤백 | `RUNTIME_MISSING` |
| `WSI-CALL-D30` | 통과 | 제작·도감만 가능 | C16/C18~C20·정화 출력·전장·원자 롤백 | `RUNTIME_MISSING` |
| `WSI-CALL-D40` | 통과 | 제작·도감만 가능 | C22/C24~C26·부품 A~C·전장·원자 롤백 | `RUNTIME_MISSING` |

관리자 `/ws season boss`와 Test Lab 보스 생성은 호출품 정상 경로의 통과 증거로 인정하지 않는다.

## 7. 다음 구현 순서

1. 일반 탄약 원장 입금·활/석궁 소비·저장은 구현됐으며 실제 클라이언트 우클릭·재접속 E2E로 닫는다.
2. 휴대 장치 `portableInstanceId`, P05 다중 장치, P06 동일 플레이어 다중 말뚝은 구현됐으며 재시작·실제 설치 E2E로 닫는다.
3. P06 말뚝 3개를 공통 입력으로 하는 `ArenaCandidate/ArenaManifest` 검사기를 만든다.
4. 호출품의 `callInstanceId/runId/recipeTransactionId`와 `validate→manifest→reserve→spawn→commit`, 실패 시 반환을 구현한다.
5. 특수 탄약 4종은 정확한 PEN·추가 브레이크·발사 무기 계약을 확정한 뒤 효과를 연결한다.
6. 실제 클라이언트로 설치·회수·우클릭·인벤토리 부족·재시작 E2E를 통과시킨다.
