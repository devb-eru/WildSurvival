# WildSurvival Season 1 상위 채집 도구 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `UTILITY-TOOL-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 역할 | slot 1~8에서 바닐라 채집 행동을 유지하는 상위 유틸리티 도구 16개의 별도 목록 |
| 상위 권위 | `TOOL-LIST-001`, `RESOURCE-DATA-D20-001`, `CONTENT-DATA-D50-001` |
| 데이터 리비전 | `tool-equipment-s1-r1` |
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

## 3. 합계·검증

- T3/T4/T5/T6 각 도구 4종, 합계 `16`
- codex 1000~1015 연속, 중복 0
- `MAIN_WEAPON/OFF_WEAPON` 혼입 0
- 가위·솔·낚싯대 커스텀 상위 ID 0; 바닐라 도구 또는 FAC-P03 권위 유지
