# WildSurvival Season 1 방어구·장신구 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `ARMOR-ACCESSORY-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 역할 | `TOOL-LIST-001` 생산 장비 중 방어구·장신구·부적의 별도 고정 투영 목록 |
| 상위 권위 | `TOOL-LIST-001`, `EQUIP-LIST-001`, `EQUIP-DATA-D20-001`, `EQUIP-DATA-D50-001` |
| 데이터 리비전 | `tool-equipment-s1-r1` |
| 최종 수정일 | 2026-08-24 |

## 1. 슬롯 경계

| 분류 | 슬롯 | 수량 |
|---|---|---:|
| 투구 | `ARMOR_HEAD` | 10 |
| 흉갑·단일 외피 | `ARMOR_CHEST` | 15 |
| 각반 | `ARMOR_LEGS` | 10 |
| 장화 | `ARMOR_FEET` | 10 |
| 장신구 | `ACCESSORY` | 24 |
| 부적 | `CHARM` | 10 |
| 합계 |  | 79 |

모든 항목은 커스텀 장비 GUI에서만 장착한다. 갑옷 바·인벤토리 표현은 서버 원장의 `instanceUuid`, 슬롯, 내구, `BROKEN` 상태를 미러링할 뿐 권위가 아니다.

## 2. 방어구 45개

| 세트·단일 계열 | 정확한 ID | 수량 |
|---|---|---:|
| 개척자 | `EQL-AR-C01`, `EQL-AR-C02`, `EQL-AR-C03`, `EQL-AR-C04` | 4 |
| 수색자 | `EQL-AR-U-SCOUT-HEAD`, `EQL-AR-U-SCOUT-CHEST`, `EQL-AR-U-SCOUT-LEGS`, `EQL-AR-U-SCOUT-FEET` | 4 |
| 선봉대 | `EQL-AR-U-VANGUARD-HEAD`, `EQL-AR-U-VANGUARD-CHEST`, `EQL-AR-U-VANGUARD-LEGS`, `EQL-AR-U-VANGUARD-FEET` | 4 |
| 오염 관측자 | `EQL-AR-R-OBSERVER-HEAD`, `EQL-AR-R-OBSERVER-CHEST`, `EQL-AR-R-OBSERVER-LEGS`, `EQL-AR-R-OBSERVER-FEET` | 4 |
| D10 단일 흉갑 | `EQL-D10-AR` | 1 |
| 멸균 구조복 | `EQD20-AR-MED-HEAD`, `EQD20-AR-MED-CHEST`, `EQD20-AR-MED-LEGS`, `EQD20-AR-MED-FEET` | 4 |
| 신경 차폐복 | `EQD20-AR-CTRL-HEAD`, `EQD20-AR-CTRL-CHEST`, `EQD20-AR-CTRL-LEGS`, `EQD20-AR-CTRL-FEET` | 4 |
| 격리 방호복 | `EQD20-AR-GUARD-HEAD`, `EQD20-AR-GUARD-CHEST`, `EQD20-AR-GUARD-LEGS`, `EQD20-AR-GUARD-FEET` | 4 |
| D18·D20 단일 흉갑 | `EQD20-EP-AR01`, `EQD20-LG-AR01` | 2 |
| 경계 정화복 | `EQD50-AR-PURIFIER-HEAD`, `EQD50-AR-PURIFIER-CHEST`, `EQD50-AR-PURIFIER-LEGS`, `EQD50-AR-PURIFIER-FEET` | 4 |
| 중단 작업복 | `EQD50-AR-INTERRUPT-HEAD`, `EQD50-AR-INTERRUPT-CHEST`, `EQD50-AR-INTERRUPT-LEGS`, `EQD50-AR-INTERRUPT-FEET` | 4 |
| 첫불씨 방호복 | `EQD50-AR-REBUILD-HEAD`, `EQD50-AR-REBUILD-CHEST`, `EQD50-AR-REBUILD-LEGS`, `EQD50-AR-REBUILD-FEET` | 4 |
| D30·D40 단일 흉갑 | `EQD50-B30-AR01`, `EQD50-B40-AR01` | 2 |

세트 정의 ID는 효과 묶음이며 ItemStack 수에 포함하지 않는다. 방어구 합계는 `16+1+12+2+12+2=45`다.

## 3. 장신구 24개

| 최초 구간 | 정확한 ID |
|---|---|
| D1~5 | `EQL-AC-C01`, `EQL-AC-U01`, `EQL-AC-U02`, `EQL-UA-U01`, `EQL-AC-R01`, `EQL-AC-R02`, `EQL-UA-R01` |
| D10 | `EQL-D10-AC`, `EQL-D10-UA` |
| D11~20 | `EQD20-AC-R01`, `EQD20-AC-R02`, `EQD20-UA-R01`, `EQD20-EP-AC01`, `EQD20-EP-UA01`, `EQD20-LG-AC01`, `EQD20-LG-UA01` |
| D21~31 | `EQD50-UA-E21`, `EQD50-B30-AC01`, `EQD50-B30-UA01`, `EQD50-UA-L31` |
| D40~41 | `EQD50-B40-AC01`, `EQD50-B40-UA01`, `EQD50-AC-CALIBRATE`, `EQD50-UA-A41` |

`UA` 항목은 권투 빌드용 장신구이며 주무기나 권투 ItemStack으로 바꾸지 않는다.

## 4. 부적 10개

`EQL-CH-C01`, `EQL-CH-U01`, `EQL-CH-U02`, `EQL-CH-R01`, `EQL-D10-CH`, `EQD20-CH-R01`, `EQD20-CH-R02`, `EQD20-EP-CH01`, `EQD20-LG-CH01`, `EQD50-CH-RELAY`.

## 5. 합계·검증

- 방어구 45 + 장신구 24 + 부적 10 = `79`
- 실제 방어구 부위 ID와 세트 정의 ID 혼입 0
- 장신구·부적의 slot 0/-106 미러링 0
- `BROKEN` 상태의 스탯·세트·고유 효과 기여 0
- 이 목록 79개와 `WEAPON-EQUIPMENT-LIST-001` 119개, `UTILITY-TOOL-LIST-001` 16개의 합집합이 정확히 생산 장비 214개다.
