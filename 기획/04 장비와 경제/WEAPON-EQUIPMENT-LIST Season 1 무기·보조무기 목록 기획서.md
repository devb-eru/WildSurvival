# WildSurvival Season 1 무기·보조무기 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `WEAPON-EQUIPMENT-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 역할 | `TOOL-LIST-001` 생산 장비 214개 중 주무기·보조무기만 분리한 고정 투영 목록 |
| 상위 권위 | ID·도감·Material은 `TOOL-LIST-001`, 효과는 `EQUIP-LIST-001`·`EQUIP-DATA-D20-001`·`EQUIP-DATA-D50-001` |
| 데이터 리비전 | `tool-equipment-s1-r1` |
| 최종 수정일 | 2026-08-24 |

## 1. 경계

- 주무기 108개는 모두 `equipmentSlot=MAIN_WEAPON`이며 장착 시 실제 slot 0에 미러링한다.
- 보조무기 11개는 모두 `equipmentSlot=OFF_WEAPON`이며 장착 시 slot -106에 미러링한다.
- 권투는 빈 주무기 상태의 가상 프로필이므로 이 목록의 ItemStack 수에 포함하지 않는다.
- 단검은 검 계열 Material, 둔기는 철퇴 Material을 사용해도 PDC `weaponClass`가 판정 권위다.
- 아래 ID는 표시용 패턴이 아니라 이미 전개된 실제 ID다. 런타임은 접두사나 현재 무기군을 추론하지 않는다.

## 2. 주무기 108개

| 최초 Day·등급 | 정확한 ID 9개 |
|---|---|
| D1 COMMON | `EQL-W01`, `EQL-W02`, `EQL-W03`, `EQL-W04`, `EQL-W05`, `EQL-W06`, `EQL-W07`, `EQL-W08`, `EQL-W09` |
| D3 UNCOMMON | `EQL-SW-U01`, `EQL-AX-U01`, `EQL-BO-U01`, `EQL-CB-U01`, `EQL-DG-U01`, `EQL-BL-U01`, `EQL-ST-U01`, `EQL-PK-U01`, `EQL-TR-U01` |
| D5 RARE | `EQL-SW-R01`, `EQL-AX-R01`, `EQL-BO-R01`, `EQL-CB-R01`, `EQL-DG-R01`, `EQL-BL-R01`, `EQL-ST-R01`, `EQL-PK-R01`, `EQL-TR-R01` |
| D10 EPIC | `EQL-D10-W-SW`, `EQL-D10-W-AX`, `EQL-D10-W-BO`, `EQL-D10-W-CB`, `EQL-D10-W-DG`, `EQL-D10-W-BL`, `EQL-D10-W-ST`, `EQL-D10-W-PK`, `EQL-D10-W-TR` |
| D11 RARE | `EQD20-SW-R01`, `EQD20-AX-R01`, `EQD20-BO-R01`, `EQD20-CB-R01`, `EQD20-DG-R01`, `EQD20-BL-R01`, `EQD20-ST-R01`, `EQD20-PK-R01`, `EQD20-TR-R01` |
| D18 EPIC | `EQD20-EP-W01-SW`, `EQD20-EP-W01-AX`, `EQD20-EP-W01-BO`, `EQD20-EP-W01-CB`, `EQD20-EP-W01-DG`, `EQD20-EP-W01-BL`, `EQD20-EP-W01-ST`, `EQD20-EP-W01-PK`, `EQD20-EP-W01-TR` |
| D20 LEGENDARY | `EQD20-LG-W01-SW`, `EQD20-LG-W01-AX`, `EQD20-LG-W01-BO`, `EQD20-LG-W01-CB`, `EQD20-LG-W01-DG`, `EQD20-LG-W01-BL`, `EQD20-LG-W01-ST`, `EQD20-LG-W01-PK`, `EQD20-LG-W01-TR` |
| D21 EPIC | `EQD50-SW-E21`, `EQD50-AX-E21`, `EQD50-BO-E21`, `EQD50-CB-E21`, `EQD50-DG-E21`, `EQD50-BL-E21`, `EQD50-ST-E21`, `EQD50-PK-E21`, `EQD50-TR-E21` |
| D30 ABYSSAL | `EQD50-B30-W01-SW`, `EQD50-B30-W01-AX`, `EQD50-B30-W01-BO`, `EQD50-B30-W01-CB`, `EQD50-B30-W01-DG`, `EQD50-B30-W01-BL`, `EQD50-B30-W01-ST`, `EQD50-B30-W01-PK`, `EQD50-B30-W01-TR` |
| D31 LEGENDARY | `EQD50-SW-L31`, `EQD50-AX-L31`, `EQD50-BO-L31`, `EQD50-CB-L31`, `EQD50-DG-L31`, `EQD50-BL-L31`, `EQD50-ST-L31`, `EQD50-PK-L31`, `EQD50-TR-L31` |
| D40 ABYSSAL | `EQD50-B40-W01-SW`, `EQD50-B40-W01-AX`, `EQD50-B40-W01-BO`, `EQD50-B40-W01-CB`, `EQD50-B40-W01-DG`, `EQD50-B40-W01-BL`, `EQD50-B40-W01-ST`, `EQD50-B40-W01-PK`, `EQD50-B40-W01-TR` |
| D41 ABYSSAL | `EQD50-SW-A41`, `EQD50-AX-A41`, `EQD50-BO-A41`, `EQD50-CB-A41`, `EQD50-DG-A41`, `EQD50-BL-A41`, `EQD50-ST-A41`, `EQD50-PK-A41`, `EQD50-TR-A41` |

각 행의 클래스 순서는 `SWORD, AXE, BOW, CROSSBOW, DAGGER, MACE, STAFF, PICKAXE, TRIDENT`다. 클래스별 정확히 12개이며 9×12=`108`이다.

## 3. 보조무기 11개

| codex | ID | 표시명 | 최초 Day | 등급 |
|---:|---|---|---:|---|
| 1044 | `EQL-OH-C01` | 야전 방패 | 1 | COMMON |
| 1046 | `EQL-OH-U01` | 반응 방패 | 3 | UNCOMMON |
| 1045 | `EQL-OH-R01` | 구조 방패 | 5 | RARE |
| 1040 | `EQL-D10-OH` | 맥동 방패 | 10 | EPIC |
| 1106 | `EQD20-OH-R01` | 정화 투사 방패 | 11 | RARE |
| 1099 | `EQD20-EP-OH01` | 구조 중계 방패 | 18 | EPIC |
| 1104 | `EQD20-LG-OH01` | 접합 차단막 | 20 | LEGENDARY |
| 1168 | `EQD50-OH-PURIFY` | 정화 차단막 | 25 | LEGENDARY |
| 1148 | `EQD50-B30-OH01` | 변이 차단막 | 30 | ABYSSAL |
| 1167 | `EQD50-OH-INTERRUPT` | 공진 방패 | 35 | ABYSSAL |
| 1152 | `EQD50-B40-OH01` | 잔류 차폐막 | 40 | ABYSSAL |

## 4. 합계·검증

| 구분 | 수량 |
|---|---:|
| 주무기 | 108 |
| 보조무기 | 11 |
| 합계 | 119 |

- 주무기 클래스 누락·중복 0, 클래스별 12개
- `MAIN_WEAPON`이 아닌 주무기 0, `OFF_WEAPON`이 아닌 보조무기 0
- 권투 ItemStack·codexIndex 0
- 전체 119개는 `TOOL-LIST-001` 214개 집합의 진부분집합이며 다른 투영 목록과 교집합 0
