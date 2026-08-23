# WildSurvival Season 1 등록 콘텐츠 통합 색인

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `CONTENT-CATALOG-INDEX-001` |
| 상태 | `DATA_LOCKED` |
| 역할 | 모든 생산 콘텐츠 ID의 합계, 소유 문서, 구현 파일과 참조 완결성을 관리하는 단일 색인 |
| 상위 기준 | `PRODUCTION-DESIGN-GATE-001`, `DOC-AUTHORITY-001`, `DATA-REVISION-002` |
| 목표 리비전 | `ws-content-r2` |
| 최종 수정일 | 2026-08-23 |

## 1. ID 소유권

| 접두·형식 | 도메인 | 단일 소유 목록 | 다른 문서의 허용 참조 |
|---|---|---|---|
| `WSR-*` | 원장 자원 | `MATERIAL-LIST-001` | recipe, loot, facility, final |
| `WSI-*` | 비장비 아이템 | `ITEM-LIST-001` | recipe, loot, codex, skill |
| `EQL-*`, `EQD20-*`, `EQD50-*` | 장비 템플릿 | `TOOL-LIST-001` | recipe, loot, skill, augment |
| `WSRCP-*` | 제작·가공 | `RECIPE-LIST-001` | facility, codex, research |
| `ws.*.v1` | 플레이어 스킬 | `SKILL-LIST-001` | equipment, augment, input |
| `AUG-S/G/P-*` | 개인 증강 | `AUG-LIST-001` | draw, equipment tags |
| `PAUG-*` | 파티 증강 | `AUG-LIST-002` | party draw, facility, final |
| `EN-*` | 적·정예·Final 적 | `ENTITY-LIST-001` | event, loot, boss |
| `BOSS-D*` | 보스 | `ENTITY-LIST-001`과 BOSS DATA | day, loot, story |
| `ENT-*` | 소환체·전투/표시/노드 개체 | `ENTITY-LIST-001` | skill, event, facility |
| `FAC-*` | 시설 정의 | `FACILITY-LIST-001` | recipe, event, final |
| `LOOT-*` | 획득 테이블 | `LOOT-LIST-001` | resource, enemy, event, boss |

ID를 문서 제목, 표시명 또는 와일드카드로 대신하지 않는다. 저장 데이터에 한 번 사용된 ID는 삭제·재사용하지 않고 `enabled:false`와 대체 ID를 기록한다.

## 2. 2026-08-23 생산 잠금 합계

| 목록 | 고유 ID | 상태 | 생산 잠금 전 조치 |
|---|---:|---|---|
| 재료·진행 증명 | 59 | locked | 원재료36+가공15+보스3+증명5 |
| 비장비 아이템 | 61 | locked | 소모품18+휴대7+시설32+호출4 |
| 레시피 | 315 | locked | 기존85+신규 비장비/시설/도구48+장비182 |
| 장비·상위 도구 | 214 | locked | 장비198+유틸리티 도구16 |
| 플레이어 스킬 | 64 | locked | 기본 공격10+기존 액티브·상황54 |
| 개인 증강 | 50 | locked | Silver18+Gold18+Prism14 |
| 파티 증강 | 16 | locked | Day10/20/30/40 총4회 |
| 일반·정예·Final 적 | 53 | locked | action bundle·loot 연결 |
| 보스 | 4 | locked | boss action/entity/loot set 연결 |
| 보조 엔티티 | 34 | locked | 투사체·UI7+노드6+보스/Final21 |
| 시설 | 46 | locked | item/virtual recipe/state 연결 |
| loot table | 62 | locked | 적45+보스4+노드6+기타6+NONE1 |

전체 도감 레코드는 334개이며 codexIndex 중복은 0이다. 레시피 output 고아, 시설 recipe 누락, 장비·비장비 recipe 누락도 0이다. 증거는 `CONTENT-GRAPH-AUDIT-001`이다.

## 3. 장비 ID 전개 결정

다음 패턴은 생산 데이터에 허용하지 않는다.

| 기존 패턴 | 생산 전개 |
|---|---|
| `EQL-AR-U-SCOUT-*` | `HEAD`, `CHEST`, `LEGS`, `FEET` 4개 |
| `EQL-AR-U-VANGUARD-*` | `HEAD`, `CHEST`, `LEGS`, `FEET` 4개 |
| `EQL-AR-R-OBSERVER-*` | `HEAD`, `CHEST`, `LEGS`, `FEET` 4개 |
| `EQL-D10-W-{class}` | 권투 제외 9개 무기군별 템플릿 |
| D20 세트 3종 | 세트 정의와 별도로 12개 부위 템플릿 |
| D50 세트 3종 | 세트 정의와 별도로 12개 부위 템플릿 |

기존 `169개` 잠정치는 다음 이유로 폐기한다.

- `EQL-W10` 권투 가상 프로필과 `EQD20-AUG-R01/R02` 개조 규칙은 ItemStack이 아니다.
- D20 EP/LG, D30, D40의 `현재 주무기군` 4개를 9개 무기군별 ID로 추가 전개해야 한다.
- T3~T6 실제 채집을 위한 상위 유틸리티 도구 16개가 필요하다.

최종 생산 인벤토리 템플릿은 장비 198개와 상위 도구 16개, 합계 `214개`다.

## 4. 플레이어 스킬 기준선

| 범주 | 수량 | ID 범위 |
|---|---:|---|
| 10개 무기군 선택 액티브 | 40 | 무기군별 4개 |
| 공용 액티브 | 10 | `ws.common.*.v1` |
| 상황 스킬 | 4 | `ws.context.*.v1` |
| 기본 공격 | 10 | 신규 `ws.basic.<weapon>.v1`, 권투 가상 프로필 포함 |
| 합계 목표 | 64 | 플레이어 실행 목록 |

적·보스·시설 스킬은 플레이어 목록과 다른 `ws.entity.*.v1`, `ws.facility.*.v1` 이름공간을 사용한다.

## 5. 참조 완결성 규칙

| 생산 레코드 | 반드시 참조할 대상 |
|---|---|
| item | Material, textKey, codexIndex, stackPolicy, source 또는 recipe |
| material | item 표현, tier, firstDay, sources, sinks, fallback |
| equipment | item 표현, slot, rarity, iLv, durability, stats/effects, source/recipe |
| recipe | input item/resource ID, output ID, grid/process, unlock, facility, ownership |
| skill | input, weapon/context, AP, cooldown, timing, target, effects, durability/ammo, fallback |
| augment | tier, triggers, effects, caps, conflicts, eligibility, fallback |
| entity | Bukkit type, display/model, stats, skills, spawn, cleanup, reward owner |
| facility | placement form, item/virtual build, costs, states, queue, network, damage/repair |
| loot | source, entries, weight/guarantee, ownership, pity, idempotency key |

## 6. 정적 검증 항목

- 전역 ID 중복 0, 와일드카드 0, 표시명 참조 0
- recipe input/output 고아 0
- item source 없는 non-admin item 0
- craftable item의 recipe 0개 또는 복수 권위 충돌 0
- equipment slot·Material·weapon class 미해석 0
- skill effect·status·break·projectile 참조 미해석 0
- augment trigger·tag·cap·conflict 참조 미해석 0
- entity skill·loot·summon·cleanup 참조 미해석 0
- facility kit·recipe·network·repair 참조 미해석 0
- codexIndex 중복·가변 배치 0
- Day 50 이전 Final 활성 경로 0

## 7. 상태 승격

별도 목록과 기계 추출 합계가 일치해 본 문서는 `DATA_LOCKED`다. 생산 코드 착수는 여전히 `PRODUCTION-DESIGN-GATE-001`의 PDG-5 번들·인계 계약까지 완료해야 승인한다.
