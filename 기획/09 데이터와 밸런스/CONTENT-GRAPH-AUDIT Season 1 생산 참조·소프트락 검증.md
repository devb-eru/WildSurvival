# WildSurvival Season 1 생산 참조·소프트락 검증

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `CONTENT-GRAPH-AUDIT-001` |
| 상태 | `DESIGN_GRAPH_PASSED` |
| 기준 리비전 | `ws-content-r2` 설계 원장 |
| 대상 | 획득→도감→제작→장비→전투→Day→보스→재건→Final |
| 최종 수정일 | 2026-08-23 |

## 1. 기계 추출 합계

| 도메인 | 생산 레코드 | 검증 결과 |
|---|---:|---|
| 재료·진행 증명 | 59 | codex 0001~0059, 중복 0 |
| 비장비 아이템 | 61 | 고정 codex 0100~0173, 의도된 빈칸 유지 |
| 장비·상위 도구 | 214 | codex 1000~1213, 중복 0 |
| 전체 도감 레코드 | 334 | codexIndex 중복 0 |
| 레시피 | 315 | recipeId 고유 315, output 314, 고아 output 0 |
| 플레이어 스킬 | 64 | 기본 공격 10 + 액티브·상황 54 |
| 개인/파티 증강 | 50/16 | 개인 10회·파티 4회 |
| 적/보스/보조 엔티티 | 53/4/34 | 전체 91 |
| 시설 | 46 | item/virtual recipe 누락 0 |
| loot table | 62 | 적45+보스4+노드6+기타6+NONE1 |

추출 시 장비 214개 모두 정확히 하나 이상의 recipe output을 가지며, 비장비 61개도 recipe output 누락이 0이었다. 시설 매핑의 recipeId 46개도 전체 레시피 레지스트리에서 모두 해석됐다.

## 2. 핵심 진행 그래프

```text
시작 후보 검증
→ 원목 4 Craft 해금
→ 개인 자원 3×3 제작
→ 기초 무기·방어구·휴대 장치
→ C01~C07 발견·D10 호출
→ BOSS-D10: 부품 A·핵 증명
→ Day11 정착 시설·FAC-S16 선택 건설
→ 상태 표본·D20 호출
→ BOSS-D20: 부품 B
→ 변이·정화·D30 호출
→ BOSS-D30: 부품 C
→ 중단·공진·D40 호출
→ BOSS-D40: 부품 D
→ R01~R05 제작·시험
→ Final 키·R06 READY_LOCKED
→ Day50 잠금 해제·Final 3 Stage
→ FIRST_SIGNAL_SENT·Day51+
```

- Craft 해금과 Day 1 기초 제작에는 FAC-S16·파티 자원·보스 재료가 없다.
- FAC-S16은 효율·공유 시설이며 그 전에도 개인 원장과 명시적 CONTRIBUTION으로 모든 핵심 제작을 수행할 수 있다.
- 보스 증명 A→B→C→D는 존재 검사이며 시설 파괴·이전·재시작으로 소각되지 않는다.
- R01~R05는 서로 다른 부품을 검사하지만 한 시설의 결과를 자기 선행 재료로 요구하지 않는다.
- R06은 R01~R05 상태를 결합하며 Final 키는 Stage 1 Manifest 성공 때만 소비한다.
- Day 49에는 준비만 가능하고 R06은 `READY_LOCKED`, Day 50 전 활성 경로는 없다.

## 3. 발견된 순환과 보정

| ID | 발견 | 보정 |
|---|---|---|
| `GRAPH-FIX-001` | RI 도구가 Day14 강화 합금을 요구해 Day11 T3 채집이 잠길 수 있음 | Day5 정련 합금 기반으로 변경 |
| `GRAPH-FIX-002` | RS 도구가 Day25 정제 변이를 요구해 Day21 T4 채집이 잠길 수 있음 | Day21 촉매+기존 강화 합금 기반으로 변경 |
| `GRAPH-FIX-003` | HD 도구가 Day37 고밀도 합금을 요구해 Day31 T5 진입이 잠김 | Day31 골재+이전 세션 재료 기반으로 변경 |
| `GRAPH-FIX-004` | RC 도구가 Day44 안정 프레임을 요구해 Day41 T6 진입이 잠김 | 시설 가공 가능한 Day41 동력 행렬 기반으로 변경 |
| `GRAPH-FIX-005` | R06 조합 ID가 없어 46번째 시설 output이 고아 | `WSRCP-R06→FAC-R06@READY_LOCKED` 추가 |
| `GRAPH-FIX-006` | 자원 노드 entity가 LOOT-NONE을 참조 | 노드 6종을 LOOT-NODE 6종에 연결 |
| `GRAPH-FIX-007` | Craft 해금 원목이 없는 spawn seed 가능성 | 시작 후보 12회 검증+회차당 원목4 폴백 Manifest |

## 4. 인원·사망 소프트락

| 상태 | 진행 가능성 |
|---|---|
| 정상 2~4인 | 원형 비용·보스 패턴·시설 동시 작업 |
| 1인 잔존 | 보스 연결·핵·말뚝을 순차 처리, 파티 증강 ID별 폴백 |
| 빈사 포함 | 빈사자는 동시 대상 수에서 제외, 구조 대상에는 포함 |
| 완전 사망 | 고유 증명·공용 진행 보존, 남은 1인이 Final까지 수행 가능 |
| 접속 종료 | 개인 예약 해제 또는 저장, 공용/보스 snapshot 보존 |

- 서로 다른 플레이어를 요구하는 보스 기믹은 1인에서 핵·순차 상호작용으로 바뀐다.
- 파티 증강은 activeSize 1에서 수치 폴백 또는 명시적 비활성을 가지며 진행 플래그를 만들지 않는다.
- 등록 인원 감소는 보스 최초 보상·증강 횟수·재건 부품 수를 늘리지 않는다.
- 다른 플레이어 개인 자원이 필요한 제작은 CONTRIBUTION 확인을 제공하고, 해당 플레이어가 사망·이탈하면 예약을 해제한다.

## 5. 입력·내구·바닐라 교차 검증

| 경로 | 단일 결과 |
|---|---|
| slot0 L + 유효 적 | BASIC_ATTACK, 블록 손상 취소 |
| slot0 전투 곡괭이 L + 적 없음 + 허용 블록 | 바닐라 채굴, AP 미소비, 원장 내구 1회 |
| slot0 빈손 + 적 없음 + 웅크리기 + 허용 블록 | 손 파괴, 권투 공격 미실행 |
| slot1~8 L/R | 바닐라 채굴·설치·섭취·사용 |
| slot0 Shift+2~5 | C1~C4 실행 뒤 slot0 |
| slot0 Shift+6~9 | Q1~Q4 실행 뒤 slot0 |
| slot1~8 Shift+숫자 | WS 입력 아님 |
| slot0 F | OFF 행동, 손 교환 취소 |
| Shift+F | 어느 슬롯에서든 플레이어 메뉴 |

- 장비 미러는 `Unbreakable=false`; PlayerItemDamageEvent를 취소하고 원장 내구만 한 번 차감한다.
- 원장 0은 BROKEN이며 ItemStack을 삭제하지 않는다. 공격·옵션·세트 효과를 비활성화하고 수리 경로를 유지한다.
- 한 root event에서 COMBAT_HIT와 BLOCK_BREAK, 바닐라 damage와 원장 damage가 동시에 commit되면 실패다.

## 6. 도감·획득·공용 원장

- 미발견 334개 항목은 고정 위치 BLACK_DYE/???이며 최초 개인 수령에서만 개인 상세가 열린다.
- 최초 획득 뒤 해당 output의 등록 recipe 배열은 보이고, 연구·Day·시설·보스 조건은 실행 잠금으로 별도 표시한다.
- 일반 전투·채집 보상은 FAC-S16 전후 모두 개인 지급이 기본이다.
- FAC-S16 최초 ACTIVE 뒤 명시 입금한 수량만 공용 원장에 존재한다.
- 보스 증명·재건 부품은 수량 없는 회차 원장 예외이며 개인 재료 제작을 우회하지 않는다.

## 7. 남은 검증 경계

설계 그래프는 통과했지만 실제 구현 착수 전 다음을 r2 schema·data validator로 다시 기계 검증한다.

1. 기존 원장의 모든 텍스트 ingredient가 MATERIAL-LIST ID로 치환됐는지 확인.
2. 장비 효과·스킬·증강 trigger/effect/status/action 참조 고아 0.
3. entity action bundle과 loot table·소환 set 구성원의 명시 배열.
4. Day 1~50 unlock 시뮬레이터의 1/2/3/4인 최소·중앙·불운 seed.
5. manifest hash, schema version, migration alias와 롤백 fixture.

이 5개는 코드 기능 구현이 아니라 생산 데이터 번들을 만드는 PDG-5 작업에 포함한다.
