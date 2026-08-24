# WildSurvival Season 1 상위 채집 도구 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `UTILITY-TOOL-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 역할 | slot 1~8에서 바닐라 채집 행동을 유지하는 상위 유틸리티 도구 16개의 별도 목록 |
| 상위 권위 | `TOOL-LIST-001`, `RESOURCE-DATA-D20-001`, `CONTENT-DATA-D50-001` |
| 데이터 리비전 | `tool-equipment-s1-r2` |
| 최종 수정일 | 2026-08-24 |

## 1. 고정 목록

| codex | ID | 표시명 | Tier·최초 Day | Material | 최대 내구 |
|---:|---|---|---|---|---:|
| 1000 | `EQL-UT-RI-PICKAXE` | 강화 철 곡괭이 | T3·11 | IRON_PICKAXE | 720 |
| 1001 | `EQL-UT-RI-AXE` | 강화 철 도끼 | T3·11 | IRON_AXE | 720 |
| 1002 | `EQL-UT-RI-SHOVEL` | 강화 철 삽 | T3·11 | IRON_SHOVEL | 720 |
| 1003 | `EQL-UT-RI-HOE` | 강화 철 괭이 | T3·11 | IRON_HOE | 720 |
| 1004 | `EQL-UT-RS-PICKAXE` | 공명 곡괭이 | T4·21 | DIAMOND_PICKAXE | 1100 |
| 1005 | `EQL-UT-RS-AXE` | 공명 도끼 | T4·21 | DIAMOND_AXE | 1100 |
| 1006 | `EQL-UT-RS-SHOVEL` | 공명 삽 | T4·21 | DIAMOND_SHOVEL | 1100 |
| 1007 | `EQL-UT-RS-HOE` | 공명 괭이 | T4·21 | DIAMOND_HOE | 1100 |
| 1008 | `EQL-UT-HD-PICKAXE` | 경화 곡괭이 | T5·31 | NETHERITE_PICKAXE | 1550 |
| 1009 | `EQL-UT-HD-AXE` | 경화 도끼 | T5·31 | NETHERITE_AXE | 1550 |
| 1010 | `EQL-UT-HD-SHOVEL` | 경화 삽 | T5·31 | NETHERITE_SHOVEL | 1550 |
| 1011 | `EQL-UT-HD-HOE` | 경화 괭이 | T5·31 | NETHERITE_HOE | 1550 |
| 1012 | `EQL-UT-RC-PICKAXE` | 재건 곡괭이 | T6·41 | NETHERITE_PICKAXE | 2100 |
| 1013 | `EQL-UT-RC-AXE` | 재건 도끼 | T6·41 | NETHERITE_AXE | 2100 |
| 1014 | `EQL-UT-RC-SHOVEL` | 재건 삽 | T6·41 | NETHERITE_SHOVEL | 2100 |
| 1015 | `EQL-UT-RC-HOE` | 재건 괭이 | T6·41 | NETHERITE_HOE | 2100 |

## 2. 실행 경계

- `equipmentType=UTILITY`, `equipmentSlot=INVENTORY`, `weaponClass` 빈 값으로 고정한다.
- 커스텀 장비 GUI 주무기·보조무기 칸에 장착할 수 없다.
- slot 1~8 좌클릭은 바닐라 채굴·벌목·굴착·경작이며 스킬로 바꾸지 않는다.
- WS 노드 보상, 허용 자원 Tier, 서버 내구 원장만 후처리한다.
- 전투 곡괭이는 이 목록이 아니라 `WEAPON-EQUIPMENT-LIST-001`의 `PICKAXE` 12개다.

### 2.1 실행 필드

16개 도구는 설명문을 해석하지 않고 아래 필드로 채집을 판정한다.

| 필드 | 고정값·규칙 |
|---|---|
| `executionOpcode` | `VANILLA_HARVEST_WITH_WS_TIER_GATE` |
| `harvestProfileId` | 도구 종류에 따라 §2.2의 4개 값 중 하나 |
| `resourceYieldMultiplier` | `1.00`; 도구 자체는 WS 노드 수율을 늘리지 않음 |
| `durabilityCostPerSuccess` | 유효 블록 또는 WS 노드 채집 성공 1회당 `1` |
| `vanillaActionPassthrough` | `true`; slot 1~8의 바닐라 채굴·상호작용을 취소하지 않음 |
| `toolTier` | RI/RS/HD/RC 순서대로 `3/4/5/6`; 해당 Tier 이하 WS 노드만 허용 |

- WS 노드의 실제 지급량은 `LOOT-NODE-*`, Day·난이도·인원 예산과 노드 감쇠가 소유한다. 도구가 그 결과를 다시 곱하지 않는다.
- 바닐라 블록 드롭량·채굴 속도·적합 도구 판정은 대표 Material과 바닐라 인챈트 규칙을 따른다.
- 서버 내구 원장이 성공을 확정한 뒤 바닐라 내구 손상을 상쇄하고 서버 값만 1회 차감한다. 실패·취소·보호 구역·이미 소비된 동일 실행은 차감하지 않는다.
- `Unbreaking` 확률이나 `Mending` 경험치 수선으로 서버 내구를 우회하지 않는다. 수리는 WildSurvival 수리 거래만 사용한다.

### 2.2 도구별 채집 프로필

| 도구 접미사 | `harvestProfileId` | 바닐라 행동 | WS 노드 허용 범주 |
|---|---|---|---|
| `PICKAXE` | `HARVEST_PICKAXE` | 광석·석재 채굴 | 광물·합금·공진 광맥 |
| `AXE` | `HARVEST_AXE` | 원목·목재 벌목 | 목질·수지·생체 외피 |
| `SHOVEL` | `HARVEST_SHOVEL` | 흙·모래·자갈 굴착 | 토양·골재·매몰 표본 |
| `HOE` | `HARVEST_HOE` | 경작·식물 채집 | 식물·섬유·농업 표본 |

분류가 맞지 않는 도구는 바닐라 블록 행동은 그대로 수행할 수 있지만 WS 노드 보상은 지급하지 않는다. 같은 블록 실행에서 바닐라 드롭과 WS 노드 보상을 함께 지급하도록 표시된 노드가 아니라면 둘 중 노드가 소유한 한 경로만 실행한다.

## 3. 합계·검증

- T3/T4/T5/T6 각 도구 4종, 합계 `16`
- codex 1000~1015 연속, 중복 0
- `MAIN_WEAPON/OFF_WEAPON` 혼입 0
- 가위·솔·낚싯대 커스텀 상위 ID 0; 바닐라 도구 또는 FAC-P03 권위 유지
- 실행 opcode 1종, 채집 프로필 4종, `resourceYieldMultiplier=1.00` 16개
- `effectText`에 `본 문서`, `§` 같은 런타임 해석 불가능 문구 0
