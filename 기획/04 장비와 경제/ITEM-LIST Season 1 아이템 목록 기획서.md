# WildSurvival Season 1 아이템 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `ITEM-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 적용 범위 | Season 1에서 인벤토리·GUI·도감으로 표현되는 비장비 아이템 |
| 상위 기준 | `MATERIAL-LIST-001`, `CRAFT-001`, `FACILITY-LIST-001`, `CODEX-001` |
| 데이터 리비전 | `item-s1-r2` |
| 최종 수정일 | 2026-08-24 |

## 1. 경계와 공통 계약

- 이 문서는 `WSI-*` 비장비 아이템만 소유한다. `WSR/WSP-*`는 `MATERIAL-LIST-001`, `EQL/EQD*`는 `TOOL-LIST-001`이 소유한다.
- 모든 항목은 고정 `codexIndex`를 가진다. 미발견 칸은 `BLACK_DYE`, 이름 `???`, 로어 비공개로 표시하며 발견 순서로 이동하지 않는다.
- 최초로 개인 인벤토리에 들어오거나 개인이 결과를 수령한 시점에 해당 플레이어 도감을 해금한다. 파티 시설·보스 호출품은 최초 제작 또는 회차 지급 때 등록 플레이어 전원의 도감을 해금한다.
- 바닥 드롭, 호퍼, 상자, 사망 드롭만으로 소유권을 판단하지 않는다. `itemInstanceId` 또는 수량 트랜잭션과 PDC 서명을 검증한다.
- `CRAFT GUI` 밖의 바닐라 제작대·2×2 조합·레시피 북은 WS 아이템을 생성하지 않는다.
- 커스텀 소모품은 장비 GUI의 `Q1~Q4`에 ID를 바인딩하며 실제 핫바를 점유하지 않는다. 전투 자세(slot 0)의 `Shift+6~9`만 사용 요청을 발생시킨다.
- 음식·물약·횃불·블록 같은 바닐라 아이템은 slot 1~8에서 바닐라 규칙으로 사용한다.
- 시설 키트는 설치 성공 트랜잭션에서만 소비한다. 위치 검사·다중 블록 생성·청크 저장 중 하나라도 실패하면 같은 키트를 반환한다.

필수 필드는 `id`, `textKey`, `category`, `firstDay`, `displayMaterial`, `customModelKey`, `stackLimit`, `ownership`, `usePolicy`, `recipeId/sourceId`, `codexIndex`, `enabled`다.

### 1.1 카테고리별 고정 필드

표에 반복하지 않은 필드는 아래 규칙으로 생성한다. 생성기는 열 수나 첫 숫자를 추측하지 않고 각 카테고리의 표 계약을 따라야 한다.

| ID 범위 | category | ownership | usePolicy | 추가 참조 |
|---|---|---|---|---|
| `WSI-CONS-*` | CONS | PERSONAL | QUICK_BINDABLE | 없음 |
| `WSI-AMMO-*` | AMMO | PERSONAL | AMMO_LEDGER_DEPOSIT | 없음 |
| `WSI-PORTABLE-*` | PORTABLE | PARTY | PORTABLE_FACILITY_ACTION | `connectedFacilityId` 필수 |
| `WSI-FAC-*` | FAC | PARTY | FACILITY_PLACEMENT | `connectedFacilityId` 필수 |
| `WSI-CALL-*` | CALL | PARTY_BOUND | BOSS_CALL_TRANSACTION | `constraintText` 필수 |

- `textKey`는 `item.wildsurvival.<소문자 ID, 하이픈은 밑줄>`로 고정한다.
- `customModelKey`는 `wildsurvival:item/<소문자 ID, 하이픈은 밑줄>`로 고정한다. 리소스팩 모델이 아직 없더라도 키를 바꾸지 않고 대표 Material 폴백을 사용한다.
- 휴대 장치와 시설 키트는 파티 제작품이지만 설치·사용 트랜잭션을 커밋하기 전에는 실제 보유자의 인벤토리에 존재한다.
- `firstDay`는 사용 가능 최솟값이며 발견·연구·시설 조건을 우회하지 않는다.

## 2. 소모품·탄약

| codex | ID | 표시명 | 최초 | 대표 Material | 스택 | 사용·제약 | 제작식 |
|---:|---|---|---:|---|---:|---|---|
| 0100 | `WSI-CONS-RATION_PACK` | 야전 배급팩 | 1 | BREAD | 16 | 8초 섭취, 허기·포화 회복, 전투 중 사용시간 +50% | `WSRCP-S09` |
| 0101 | `WSI-CONS-BANDAGE` | 붕대 | 1 | PAPER | 16 | BLEED 1중첩 제거, 직접 치료 없음 | `WSRCP-S01` |
| 0102 | `WSI-CONS-REPAIR_KIT` | 야전 수리 키트 | 2 | ANVIL | 8 | 일반 수리, BROKEN 장비 최대 내구 40%까지 복구 | `WSRCP-S02` |
| 0103 | `WSI-AMMO-ARROW_BUNDLE` | 일반 화살 묶음 | 3 | ARROW | 64 | 활·석궁 공용 16발 단위 탄약 원장 충전 | `WSRCP-S03` |
| 0104 | `WSI-AMMO-PIERCING_BOLT_BUNDLE` | 관통 볼트 묶음 | 5 | SPECTRAL_ARROW | 32 | 석궁 전용 8발, 첫 유효 적중 1체 PEN +12 | `WSRCP-S04` |
| 0105 | `WSI-CONS-PURIFY_AMPOULE` | 정화 앰풀 | 5 | HONEY_BOTTLE | 8 | 개인 오염 -15, 상태 제거 없음, 전투당 2회 | `WSRCP-S05` |
| 0106 | `WSI-CONS-AP_STIM` | 응급 AP 자극제 | 6 | SUGAR | 8 | AP +10 즉시 회복, 이후 5초간 초당 +3, 초과분 소멸·탈진 해제 아님, 전투당 1회 | `WSRCP-S06` |
| 0107 | `WSI-CONS-RESCUE_BRACE` | 구조 고정대 | 3 | TRIPWIRE_HOOK | 8 | 다음 구조 1회 중단 피해 임계 +10%, 강화형과 비합산 | `WSRCP-S07` |
| 0108 | `WSI-CONS-PORTABLE_PURIFIER_CHARGE` | 휴대 정화기 충전 | 5 | GLOWSTONE_DUST | 16 | FAC-P05 60초 가동 | `WSRCP-S08` |
| 0109 | `WSI-CONS-ANTIDOTE_INJECTION` | 해독 주사 | 11 | POTION | 8 | POISON 2중첩 또는 약한 독 1개 제거 | `WSRCP-D20-S01` |
| 0110 | `WSI-CONS-COOLING_SALVE` | 냉각 연고 | 12 | SNOWBALL | 8 | BURN 2중첩 제거, 5초 화상 지속시간 -25% | `WSRCP-D20-S02` |
| 0111 | `WSI-CONS-TOURNIQUET` | 지혈 압박대 | 13 | RED_CARPET | 8 | BLEED 2중첩 제거, 직접 치료 없음 | `WSRCP-D20-S03` |
| 0112 | `WSI-CONS-NEURAL_STABILIZER` | 신경 안정제 | 14 | FERMENTED_SPIDER_EYE | 4 | ROOT·SILENCE·DISARM 중 1개 제거, HARD_CC 중 자기 사용 불가 | `WSRCP-D20-S04` |
| 0113 | `WSI-CONS-REINFORCED_RESCUE_BRACE` | 강화 구조 고정대 | 14 | CHAIN | 8 | 다음 구조 1회 중단 피해 임계 +25% | `WSRCP-D20-S05` |
| 0114 | `WSI-CONS-BIO_SHIELD_AMPOULE` | 생체 보호막 앰풀 | 16 | TURTLE_SCUTE | 4 | 최대 HP 8% 보호막 6초, 전투당 1회 | `WSRCP-D20-S06` |
| 0115 | `WSI-AMMO-PURIFY_ARROW_BUNDLE` | 정화 화살 묶음 | 23 | TIPPED_ARROW | 32 | 활·석궁, 정화 취약 대상 PURIFY_EXPOSED 6초, 자원 생성 없음 | `WSRCP-D50-S01` |
| 0116 | `WSI-AMMO-RESONANCE_BOLT_BUNDLE` | 공진 볼트 묶음 | 33 | FIREWORK_STAR | 32 | 석궁 전용, INTERRUPTIBLE 적중 최종 브레이크 ×1.50 | `WSRCP-D50-S02` |
| 0117 | `WSI-AMMO-STABILIZER_DART_BUNDLE` | 안정화 다트 묶음 | 41 | WIND_CHARGE | 16 | 석궁 전용 32m 아군·시설 비피해, 약한 오염 압력 1단 완화, 대상별 20초 | `WSRCP-D50-S03` |

소모품 사용은 `request→validate→reserve→apply→commit` 순서다. 대상·거리·상태·전투당 상한 검사가 실패하면 수량을 차감하지 않는다.

## 3. 휴대 장치

`FAC-P02`는 0102의 수리 키트를 그대로 사용하므로 중복 아이템을 만들지 않는다.

| codex | ID | 연결 시설 | 표시명 | 최초 | 대표 Material | 스택 | 핵심 제약 | 제작식 |
|---:|---|---|---|---:|---|---:|---|---|
| 0120 | `WSI-PORTABLE-CRAFT_KIT` | FAC-P01 | 휴대 제작 꾸러미 | 1 | BUNDLE | 1 | CRAFT GUI 저효율 작업, 희귀 이상 불가 | `WSRCP-F01` |
| 0121 | `WSI-PORTABLE-SAMPLE_EXTRACTOR` | FAC-P03 | 표본 채취기 | 5 | BRUSH | 1 | 피격·이동 시 채취 중단 | `WSRCP-F08` |
| 0122 | `WSI-PORTABLE-ANALYZER` | FAC-P04 | 휴대 분석기 | 5 | COMPASS | 1 | 작업 1개, 시설 대비 150% 시간 | `WSRCP-F07` |
| 0123 | `WSI-PORTABLE-PURIFIER` | FAC-P05 | 휴대 정화기 | 5 | BREWING_STAND | 1 | 0108 소비, 작은 반경 60초 | `WSRCP-F09` |
| 0124 | `WSI-PORTABLE-SIGNAL_STAKE` | FAC-P06 | 신호 말뚝 | 6 | LIGHTNING_ROD | 8 | 설치한 인스턴스 회수 전 재사용 불가 | `WSRCP-G01` |
| 0125 | `WSI-PORTABLE-RESCUE_BEACON` | FAC-P07 | 구조 신호기 | 8 | BELL | 2 | 전투당 1회, 자동 구조 아님 | `WSRCP-F10` |
| 0126 | `WSI-PORTABLE-LEDGER` | FAC-P08 | 휴대 보관 원장 | 12 | WRITABLE_BOOK | 1 | FAC-S16 설치 뒤에만 조회·안전 상태 예약 가능 | `WSRCP-F11` |

## 4. 야영 시설 키트

| codex | ID | 시설 | 표시명 | 대표 Material | 최초 | 스택 | 제작식 |
|---:|---|---|---|---|---:|---:|---|
| 0130 | `WSI-FAC-C01-KIT` | FAC-C01 | 간이 작업대 키트 | CRAFTING_TABLE | 1 | 4 | `WSRCP-F02` |
| 0131 | `WSI-FAC-C02-KIT` | FAC-C02 | 야전 화로 키트 | FURNACE | 1 | 4 | `WSRCP-F03` |
| 0132 | `WSI-FAC-C03-KIT` | FAC-C03 | 임시 보관함 키트 | BARREL | 1 | 4 | `WSRCP-F04` |
| 0133 | `WSI-FAC-C04-KIT` | FAC-C04 | 침낭 표식 키트 | WHITE_CARPET | 2 | 4 | `WSRCP-F12` |
| 0134 | `WSI-FAC-C05-KIT` | FAC-C05 | 간이 경보종 키트 | BELL | 2 | 4 | `WSRCP-F13` |
| 0135 | `WSI-FAC-C06-KIT` | FAC-C06 | 야전 약제대 키트 | BREWING_STAND | 3 | 4 | `WSRCP-F05` |
| 0136 | `WSI-FAC-C07-KIT` | FAC-C07 | 소형 탄약대 키트 | FLETCHING_TABLE | 3 | 4 | `WSRCP-F06` |
| 0137 | `WSI-FAC-C08-KIT` | FAC-C08 | 임시 바리케이드 키트 | IRON_BARS | 3 | 16 | `WSRCP-F14` |

## 5. 정착 시설 키트

| codex | ID | 시설 | 표시명 | 대표 Material | 최초 | 스택 | 제작식 |
|---:|---|---|---|---|---:|---:|---|
| 0140 | `WSI-FAC-S01-KIT` | FAC-S01 | 정밀 제작대 키트 | SMITHING_TABLE | 11 | 2 | `WSRCP-FAC-S01` |
| 0141 | `WSI-FAC-S02-KIT` | FAC-S02 | 강화 단조대 키트 | ANVIL | 11 | 2 | `WSRCP-FAC-S02` |
| 0142 | `WSI-FAC-S03-KIT` | FAC-S03 | 재련 조율기 키트 | GRINDSTONE | 18 | 2 | `WSRCP-FAC-S03` |
| 0143 | `WSI-FAC-S04-KIT` | FAC-S04 | 분해·회수기 키트 | STONECUTTER | 11 | 2 | `WSRCP-FAC-S04` |
| 0144 | `WSI-FAC-S05-KIT` | FAC-S05 | 탄약 압축기 키트 | FLETCHING_TABLE | 11 | 2 | `WSRCP-FAC-S05` |
| 0145 | `WSI-FAC-S06-KIT` | FAC-S06 | 연구 단말 키트 | LECTERN | 11 | 2 | `WSRCP-D20-F02` |
| 0146 | `WSI-FAC-S07-KIT` | FAC-S07 | 관측 배열 키트 | SPYGLASS | 14 | 2 | `WSRCP-FAC-S07` |
| 0147 | `WSI-FAC-S08-KIT` | FAC-S08 | 패턴 기록기 키트 | JUKEBOX | 21 | 2 | `WSRCP-FAC-S08` |
| 0148 | `WSI-FAC-S09-KIT` | FAC-S09 | 증강 공명기 키트 | ENCHANTING_TABLE | 15 | 1 | `WSRCP-D20-F05` |
| 0149 | `WSI-FAC-S10-KIT` | FAC-S10 | 훈련 단말 키트 | TARGET | 10 | 1 | `WSRCP-D20-F04` |
| 0150 | `WSI-FAC-S11-KIT` | FAC-S11 | 치료소 키트 | RED_BED | 11 | 2 | `WSRCP-D20-F03` |
| 0151 | `WSI-FAC-S12-KIT` | FAC-S12 | 유품 회수대 키트 | SOUL_CAMPFIRE | 12 | 1 | `WSRCP-FAC-S12` |
| 0152 | `WSI-FAC-S13-KIT` | FAC-S13 | 정화기 키트 | BEACON | 15 | 2 | `WSRCP-FAC-S13` |
| 0153 | `WSI-FAC-S14-KIT` | FAC-S14 | 정화 중계기 키트 | END_ROD | 15 | 8 | `WSRCP-FAC-S14` |
| 0154 | `WSI-FAC-S15-KIT` | FAC-S15 | 환경 차폐기 키트 | COPPER_BLOCK | 19 | 2 | `WSRCP-FAC-S15` |
| 0155 | `WSI-FAC-S16-KIT` | FAC-S16 | 공용 물류고 키트 | ENDER_CHEST | 11 | 1 | `WSRCP-FAC-S16` |
| 0156 | `WSI-FAC-S17-KIT` | FAC-S17 | 동력 분배기 키트 | REDSTONE_BLOCK | 18 | 4 | `WSRCP-FAC-S17` |
| 0157 | `WSI-FAC-S18-KIT` | FAC-S18 | 이동 앵커 키트 | LODESTONE | 19 | 4 | `WSRCP-FAC-S18` |
| 0158 | `WSI-FAC-S19-KIT` | FAC-S19 | 세션 중계기 키트 | BELL | 11 | 1 | `WSRCP-FAC-S19` |
| 0159 | `WSI-FAC-S20-KIT` | FAC-S20 | 공세 관측기 키트 | SCULK_SENSOR | 14 | 2 | `WSRCP-FAC-S20` |

## 6. 방어 시설 키트

| codex | ID | 시설 | 표시명 | 대표 Material | 최초 | 스택 | 제작식 |
|---:|---|---|---|---|---:|---:|---|
| 0160 | `WSI-FAC-D01-KIT` | FAC-D01 | 보강벽 등록기 | IRON_BARS | 11 | 16 | `WSRCP-FAC-D01` |
| 0161 | `WSI-FAC-D02-KIT` | FAC-D02 | 감속 함정 | TRIPWIRE_HOOK | 14 | 16 | `WSRCP-FAC-D02` |
| 0162 | `WSI-FAC-D03-KIT` | FAC-D03 | 충격 함정 | PISTON | 21 | 16 | `WSRCP-FAC-D03` |
| 0163 | `WSI-FAC-D04-KIT` | FAC-D04 | 유도 신호기 | REDSTONE_TORCH | 19 | 4 | `WSRCP-FAC-D04` |

재건 시설 `FAC-R01~R06`은 가방에 들어가는 키트를 만들지 않는다. `WSRCP-R01~R06`과 `WSRCP-FINAL-KEY`의 원자적 작업 결과가 시설 인스턴스·진행 증명으로 직접 전환된다.

## 7. 보스 호출품

| codex | ID | 표시명 | 최초 사용 | 대표 Material | 스택 | 소유 | 제작식·검사 |
|---:|---|---|---:|---|---:|---|---|
| 0170 | `WSI-CALL-D10` | 공명 추적체 호출 세트 | 10 | RECOVERY_COMPASS | 1 | PARTY_BOUND | `WSRCP-G03`, Day10·C07 |
| 0171 | `WSI-CALL-D20` | 신경 접합체 호출 세트 | 20 | SCULK_CATALYST | 1 | PARTY_BOUND | `WSRCP-D20-CALL`, C13·D10 증명 |
| 0172 | `WSI-CALL-D30` | 오염 섭식핵 호출 세트 | 30 | HEART_OF_THE_SEA | 1 | PARTY_BOUND | `WSRCP-D30-CALL`, C20 |
| 0173 | `WSI-CALL-D40` | 공진 파괴자 호출 세트 | 40 | HEAVY_CORE | 1 | PARTY_BOUND | `WSRCP-D40-CALL`, C26 |

- 호출품은 전장 후보 생성에 성공하고 보스 encounter transaction이 커밋될 때만 소비한다.
- 서버 종료·청크 실패·부적합 지형·중복 encounter 검출이면 100% 반환한다.
- 호출품 복제 방지를 위해 `callInstanceId`, `runId`, `recipeTransactionId`를 저장한다.

## 8. 공용 물류고 전환

```text
FAC-S16 미설치
  획득 → 개인 인벤토리/개인 원장
  제작 → 요청자 개인 보유량만 예약·소비
  파티 제작 → 각 플레이어가 기여 GUI로 명시 예약

FAC-S16 ACTIVE
  획득 → 기존대로 개인에게 지급
  입금 → 플레이어 확인 뒤 공용 원장 이동
  제작 → 개인/공용 중 사용 원천을 GUI에서 선택
```

- 설치 전 공용 원장에 바로 적립하는 모든 채집·드롭 코드는 결함이다.
- 설치 전 생성된 보스 증명·고유 재건 부품만 수량 없는 회차 원장에 직접 기록할 수 있다.
- FAC-S16 파괴·비활성화 중에도 이미 입금한 자원은 보존하되 입출고·신규 예약을 정지한다.
- 휴대 보관 원장 FAC-P08은 FAC-S16이 한 번도 건설되지 않은 회차에서 공용 원장을 만들지 않는다.

## 9. 도감 표시 계약

| 범위 | 탭 | 소유 문서 |
|---|---|---|
| 0001~0099 | 재료 | MATERIAL-LIST-001 |
| 0100~0199 | 소모품·시설·호출품 | ITEM-LIST-001 |
| 1000~1999 | 장비·도구 | TOOL-LIST-001 |

- 상세 페이지는 이름, ID, 최초 Day, 분류, 대표 Material, 획득처, 사용처, 등록된 조합법, 관련 시설을 표시한다.
- 아이템을 최초 획득하면 해당 outputId를 가진 현재 리비전의 조합법 3×3 배열과 수량을 모두 열람할 수 있다.
- 조합법 열람과 제작 실행 권한은 별도다. 연구·Day·시설·보스 선택 조건이 남아 있으면 배열은 보이되 `제작 잠금: <이유>`를 표시하고 결과를 만들 수 없다.
- 관리자 지급은 기본적으로 도감을 해금하지 않는다. `/ws admin give --discover`일 때만 해금한다.

## 10. 합계와 완료 기준

| 범주 | 수량 |
|---|---:|
| 소모품·탄약 | 18 |
| 휴대 장치(수리 키트 공유 제외) | 7 |
| 야영 시설 키트 | 8 |
| 정착 시설 키트 | 20 |
| 방어 시설 키트 | 4 |
| 보스 호출품 | 4 |
| 합계 | 61 |

완료 검증:

- 61개 ID·codexIndex·대표 Material 중복 및 누락 0
- 제작품 61개 모두 유효 recipe 또는 명시적 reward source 보유
- FAC-R01~R06을 휴대 아이템으로 복제하는 경로 0
- FAC-S16 전 개인 자원만으로 시작·Craft 해금·기초 장비 제작 가능
- 미발견 칸 위치 변화 0, `BLACK_DYE/???` 외 정보 누출 0
- Q1~Q4 사용이 slot 0에서만 실행되고 바닐라 slot 1~8을 변경하지 않음
