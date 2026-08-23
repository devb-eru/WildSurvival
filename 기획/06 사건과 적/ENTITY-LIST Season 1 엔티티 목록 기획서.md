# WildSurvival Season 1 엔티티 목록 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `ENTITY-LIST-001` |
| 상태 | `DATA_LOCKED` |
| 적용 범위 | 일반·정예·보스·Final 적, 소환체, 전투 오브젝트, 투사체, 자원 노드, 피해 표시 |
| 상위 기준 | `ENEMY-001`, `ENEMY-DATA-D20-001`, `ENEMY-DATA-D50-001`, `BOSS-001~004`, `FINAL-DATA-001` |
| 데이터 리비전 | `entity-s1-r1` |
| 최종 수정일 | 2026-08-23 |

## 1. 공통 필드와 생명주기

모든 생산 레코드는 `id, kind, bukkitType, displayFallback, statsProfile, actionIds, spawnPolicy, parentId, cleanupPolicy, rewardOwner, lootTableId, flags, revision`을 가진다.

- Bukkit Entity는 충돌·AI·추적 앵커이고 Display/Particle은 표현이다. 리소스 팩 거부 시 Bukkit 기본 외형과 이름·보스바·입자 전조를 유지한다.
- 모든 동적 개체는 `entityInstanceId`와 `parentEncounterId`를 저장한다. 부모가 끝나면 자식·투사체·장판·Display를 같은 cleanup transaction에서 제거한다.
- 청크 언로드는 제거가 아니다. 장기 개체는 저장하고 5초 이하 투사체·입력 버퍼·피해 숫자는 재시작 시 안전 제거한다.
- 소환체·분열체·핵·훈련·Final 보조는 `NO_REWARD, NO_SAMPLE, NO_AUGMENT_TRIGGER, NO_CONTRIBUTION`을 가진다.
- 아군, 관전, 사망, 운영자 표시 개체와 `Interaction`은 전투 대상 ray에서 제외한다.
- 자연 스폰 바닐라 몹은 명시적 변환 transaction을 통과해야 WS 적이 된다. PDC ID 없는 몹에 WS 보상·증강을 적용하지 않는다.

## 2. 등록 적 53종

정확한 Bukkit type, HP/공격/브레이크, Day, 비용, 역할은 정의 원장의 같은 ID 행이 권위다. action bundle은 각 행의 공격·상태·지원 행동을 서버 상태 기계로 전개한다.

| ID | 종류 | 정의 | action bundle | loot | cleanup |
|---|---|---|---|---|---|
| `EN-D1-01` | HOSTILE | ENEMY-001 | `ACT-EN-D1-01` | `LOOT-EN-D1-01` | parent encounter/60m+300틱 |
| `EN-D1-02` | HOSTILE | ENEMY-001 | `ACT-EN-D1-02` | `LOOT-EN-D1-02` | parent encounter/60m+300틱 |
| `EN-D11-01` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D11-01` | `LOOT-EN-D11-01` | parent encounter/60m+300틱 |
| `EN-D11-02` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D11-02` | `LOOT-EN-D11-02` | parent encounter/60m+300틱 |
| `EN-D12-01` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D12-01` | `LOOT-EN-D12-01` | parent encounter/60m+300틱 |
| `EN-D12-02` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D12-02` | `LOOT-EN-D12-02` | parent encounter/60m+300틱 |
| `EN-D13-01` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D13-01` | `LOOT-EN-D13-01` | parent encounter/60m+300틱 |
| `EN-D13-02` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D13-02` | `LOOT-EN-D13-02` | parent encounter/60m+300틱 |
| `EN-D14-01` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D14-01` | `LOOT-EN-D14-01` | parent encounter/60m+300틱 |
| `EN-D14-02` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D14-02` | `LOOT-EN-D14-02` | parent encounter/60m+300틱 |
| `EN-D15-01` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D15-01` | `LOOT-EN-D15-01` | parent encounter/60m+300틱 |
| `EN-D15-02` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D15-02` | `LOOT-EN-D15-02` | parent encounter/60m+300틱 |
| `EN-D16-01` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D16-01` | `LOOT-EN-D16-01` | parent encounter/60m+300틱 |
| `EN-D16-02` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D16-02` | `LOOT-EN-D16-02` | parent encounter/60m+300틱 |
| `EN-D17-01` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D17-01` | `LOOT-EN-D17-01` | parent encounter/60m+300틱 |
| `EN-D17-02` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D17-02` | `LOOT-EN-D17-02` | parent encounter/60m+300틱 |
| `EN-D18-E01` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D18-E01` | `LOOT-EN-D18-E01` | parent encounter/60m+300틱 |
| `EN-D18-E02` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D18-E02` | `LOOT-EN-D18-E02` | parent encounter/60m+300틱 |
| `EN-D19-E01` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D19-E01` | `LOOT-EN-D19-E01` | parent encounter/60m+300틱 |
| `EN-D2-01` | HOSTILE | ENEMY-001 | `ACT-EN-D2-01` | `LOOT-EN-D2-01` | parent encounter/60m+300틱 |
| `EN-D20-A01` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D20-A01` | `LOOT-NONE` | parent encounter/60m+300틱 |
| `EN-D20-A02` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D20-A02` | `LOOT-NONE` | parent encounter/60m+300틱 |
| `EN-D20-A03` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D20-A03` | `LOOT-NONE` | parent encounter/60m+300틱 |
| `EN-D20-A04` | HOSTILE | ENEMY-DATA-D20-001 | `ACT-EN-D20-A04` | `LOOT-NONE` | parent encounter/60m+300틱 |
| `EN-D21-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D21-01` | `LOOT-EN-D21-01` | parent encounter/60m+300틱 |
| `EN-D22-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D22-01` | `LOOT-EN-D22-01` | parent encounter/60m+300틱 |
| `EN-D23-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D23-01` | `LOOT-EN-D23-01` | parent encounter/60m+300틱 |
| `EN-D24-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D24-01` | `LOOT-EN-D24-01` | parent encounter/60m+300틱 |
| `EN-D27-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D27-01` | `LOOT-EN-D27-01` | parent encounter/60m+300틱 |
| `EN-D28-E01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D28-E01` | `LOOT-EN-D28-E01` | parent encounter/60m+300틱 |
| `EN-D31-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D31-01` | `LOOT-EN-D31-01` | parent encounter/60m+300틱 |
| `EN-D33-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D33-01` | `LOOT-EN-D33-01` | parent encounter/60m+300틱 |
| `EN-D34-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D34-01` | `LOOT-EN-D34-01` | parent encounter/60m+300틱 |
| `EN-D37-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D37-01` | `LOOT-EN-D37-01` | parent encounter/60m+300틱 |
| `EN-D38-E01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D38-E01` | `LOOT-EN-D38-E01` | parent encounter/60m+300틱 |
| `EN-D4-01` | HOSTILE | ENEMY-001 | `ACT-EN-D4-01` | `LOOT-EN-D4-01` | parent encounter/60m+300틱 |
| `EN-D4-02` | HOSTILE | ENEMY-001 | `ACT-EN-D4-02` | `LOOT-EN-D4-02` | parent encounter/60m+300틱 |
| `EN-D41-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D41-01` | `LOOT-EN-D41-01` | parent encounter/60m+300틱 |
| `EN-D44-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D44-01` | `LOOT-EN-D44-01` | parent encounter/60m+300틱 |
| `EN-D46-E01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D46-E01` | `LOOT-EN-D46-E01` | parent encounter/60m+300틱 |
| `EN-D47-01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D47-01` | `LOOT-EN-D47-01` | parent encounter/60m+300틱 |
| `EN-D49-E01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-D49-E01` | `LOOT-EN-D49-E01` | parent encounter/60m+300틱 |
| `EN-D5-01` | HOSTILE | ENEMY-001 | `ACT-EN-D5-01` | `LOOT-EN-D5-01` | parent encounter/60m+300틱 |
| `EN-D6-01` | HOSTILE | ENEMY-001 | `ACT-EN-D6-01` | `LOOT-EN-D6-01` | parent encounter/60m+300틱 |
| `EN-D6-02` | HOSTILE | ENEMY-001 | `ACT-EN-D6-02` | `LOOT-EN-D6-02` | parent encounter/60m+300틱 |
| `EN-D7-01` | HOSTILE | ENEMY-001 | `ACT-EN-D7-01` | `LOOT-EN-D7-01` | parent encounter/60m+300틱 |
| `EN-D7-02` | HOSTILE | ENEMY-001 | `ACT-EN-D7-02` | `LOOT-EN-D7-02` | parent encounter/60m+300틱 |
| `EN-D8-E01` | HOSTILE | ENEMY-001 | `ACT-EN-D8-E01` | `LOOT-EN-D8-E01` | parent encounter/60m+300틱 |
| `EN-D9-E01` | HOSTILE | ENEMY-001 | `ACT-EN-D9-E01` | `LOOT-EN-D9-E01` | parent encounter/60m+300틱 |
| `EN-F50-A01` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-F50-A01` | `LOOT-NONE` | parent encounter/60m+300틱 |
| `EN-F50-A02` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-F50-A02` | `LOOT-NONE` | parent encounter/60m+300틱 |
| `EN-F50-A03` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-F50-A03` | `LOOT-NONE` | parent encounter/60m+300틱 |
| `EN-F50-A04` | HOSTILE | ENEMY-DATA-D50-001 | `ACT-EN-F50-A04` | `LOOT-NONE` | parent encounter/60m+300틱 |

## 3. 보스 4종

| ID | 표시명 | Bukkit 폴백 | 패턴 원장 | 소환·오브젝트 | loot |
|---|---|---|---|---|---|
| `BOSS-D10` | 공명 추적체 | RAVAGER+Display | ACTSET-BOSS-D10 | ENTSET-BOSS-D10 | LOOT-BOSS-D10 |
| `BOSS-D20` | 신경 접합체 | IRON_GOLEM+Display | ACTSET-BOSS-D20 | ENTSET-BOSS-D20 | LOOT-BOSS-D20 |
| `BOSS-D30` | 오염 섭식핵 | MAGMA_CUBE+Display | ACTSET-BOSS-D30 | ENTSET-BOSS-D30 | LOOT-BOSS-D30 |
| `BOSS-D40` | 공진 파괴자 | RAVAGER+Display | ACTSET-BOSS-D40 | ENTSET-BOSS-D40 | LOOT-BOSS-D40 |

- 완료 ID와 entity template ID를 동일하게 사용한다. 긴 기존 template 이름은 migration alias로만 받는다.
- 보스 소환 성공 전 호출품을 소비하지 않는다. Manifest·본체·필수 오브젝트 생성과 저장이 끝난 뒤 ACTIVE와 소비를 한 transaction으로 commit한다.
- HP 0/제압 시 신규 공격·소환을 먼저 정지하고 투사체·장판·자식을 정리한 뒤 보상을 commit한다.
- 동적 적 증강은 boss data의 `allowDynamicAugment=true`일 때만 허용하며 기본값은 false다.

`ACTSET-BOSS-D10/D20/D30/D40`의 구성원은 각 BOSS 문서 표의 명시적 패턴 ID다. `ENTSET-BOSS-D10/D20/D30/D40`은 §4의 같은 Day 접두 엔티티 ID를 배열로 저장하며 접두 검색으로 런타임 구성하지 않는다. D20 set에는 `EN-D20-A01~A04` 네 ID도 명시 배열로 포함한다.

## 4. 보조·표현 엔티티 34종

| ID | 종류 | Bukkit·표현 폴백 | 제거 조건 | loot |
|---|---|---|---|---|
| `ENT-PROJ-PLAYER-BOW` | PROJECTILE | ARROW | owner skill 종료/적중/60틱 | `LOOT-NONE` |
| `ENT-PROJ-PLAYER-CROSSBOW` | PROJECTILE | ARROW | owner skill 종료/적중/60틱 | `LOOT-NONE` |
| `ENT-PROJ-PLAYER-STAFF` | PROJECTILE | SNOWBALL+Display | 적중/40틱 | `LOOT-NONE` |
| `ENT-PROJ-PLAYER-TRIDENT` | PROJECTILE | TRIDENT Display | 회수/80틱 | `LOOT-NONE` |
| `ENT-PROJ-PLAYER-RESCUE-FLARE` | PROJECTILE | FIREWORK | 착탄/40틱 | `LOOT-NONE` |
| `ENT-DEPLOY-EMERGENCY-COVER` | DEPLOYABLE | BLOCK_DISPLAY | HP0/12초/전투 종료 | `LOOT-NONE` |
| `ENT-UI-DAMAGE-NUMBER` | UI_TRANSIENT | TEXT_DISPLAY | 12~18틱 | `LOOT-NONE` |
| `ENT-NODE-BASIC` | RESOURCE_NODE | INTERACTION+BLOCK_DISPLAY | 채집/Day despawn | `LOOT-NODE-BASIC` |
| `ENT-NODE-INDUSTRIAL` | RESOURCE_NODE | INTERACTION+BLOCK_DISPLAY | 채집/Day despawn | `LOOT-NODE-INDUSTRIAL` |
| `ENT-NODE-MEDICAL` | RESOURCE_NODE | INTERACTION+ITEM_DISPLAY | 채집/Day despawn | `LOOT-NODE-MEDICAL` |
| `ENT-NODE-CORRUPTED` | RESOURCE_NODE | INTERACTION+BLOCK_DISPLAY | 채집/정화/Day despawn | `LOOT-NODE-CORRUPTED` |
| `ENT-NODE-RIFT` | RESOURCE_NODE | INTERACTION+BLOCK_DISPLAY | 안정화/사건 종료 | `LOOT-NODE-RIFT` |
| `ENT-NODE-RECONSTRUCTION` | RESOURCE_NODE | INTERACTION+ITEM_DISPLAY | 채집/회차 보존 | `LOOT-NODE-RECONSTRUCTION` |
| `ENT-B10-ECHO` | SUMMON | ZOMBIE/SKELETON+Display | BOSS-D10/페이즈 종료 | `LOOT-NONE` |
| `ENT-B10-CORE` | OBJECTIVE | INTERACTION+BLOCK_DISPLAY | 패턴 종료 | `LOOT-NONE` |
| `ENT-B10-SHARD` | PROJECTILE | SNOWBALL+Display | 적중/40틱 | `LOOT-NONE` |
| `ENT-B10-SIPHON-LINK` | TELEGRAPH | TEXT_DISPLAY+PARTICLE | 패턴 종료 | `LOOT-NONE` |
| `ENT-B20-LINK-CORE` | OBJECTIVE | INTERACTION+BLOCK_DISPLAY | 연결 종료 | `LOOT-NONE` |
| `ENT-B20-UNSPLICE-CORE` | OBJECTIVE | INTERACTION+BLOCK_DISPLAY | 패턴 종료 | `LOOT-NONE` |
| `ENT-B20-GRAFT-SPAWN` | TELEGRAPH | INTERACTION+PARTICLE | 1.2초/취소 | `LOOT-NONE` |
| `ENT-B20-STATUS-AREA` | AREA | AREA_EFFECT_CLOUD+Display | 패턴 종료 | `LOOT-NONE` |
| `ENT-B30-RIFT-SPORE` | SUMMON | SLIME+Display | BOSS-D30/페이즈 종료 | `LOOT-NONE` |
| `ENT-B30-CORRUPTION-POOL` | AREA | AREA_EFFECT_CLOUD+Display | 정화/전투 종료 | `LOOT-NONE` |
| `ENT-B30-SEPARATION-CORE` | OBJECTIVE | INTERACTION+BLOCK_DISPLAY | 패턴 종료 | `LOOT-NONE` |
| `ENT-B30-RIFT-NODE` | OBJECTIVE | INTERACTION+BLOCK_DISPLAY | 안정화/패턴 종료 | `LOOT-NONE` |
| `ENT-B40-GUARD-CORE` | OBJECTIVE | INTERACTION+BLOCK_DISPLAY | 파괴/페이즈 종료 | `LOOT-NONE` |
| `ENT-B40-PRISON-CORE` | OBJECTIVE | INTERACTION+BLOCK_DISPLAY | 해제/6초 | `LOOT-NONE` |
| `ENT-B40-RESONANCE-SHARD` | PROJECTILE | SHULKER_BULLET 대체 Display | 적중/50틱 | `LOOT-NONE` |
| `ENT-B40-FRACTURE-FIELD` | AREA | AREA_EFFECT_CLOUD+BLOCK_DISPLAY | 4초 | `LOOT-NONE` |
| `ENT-F50-COLLAPSE-CORE` | FINAL_BOSS | IRON_GOLEM+Display | CORE_SUBDUED/Final 종료 | `LOOT-NONE` |
| `ENT-F50-SYNAPSE-CORE` | OBJECTIVE | INTERACTION+BLOCK_DISPLAY | 패턴 종료 | `LOOT-NONE` |
| `ENT-F50-THREE-CORE` | OBJECTIVE | INTERACTION+BLOCK_DISPLAY | 패턴 종료 | `LOOT-NONE` |
| `ENT-F50-JAMMER-LINK` | OBJECTIVE | INTERACTION+TEXT_DISPLAY | 절단/8초 | `LOOT-NONE` |
| `ENT-F50-OUTPUT-RING` | AREA | BLOCK_DISPLAY+PARTICLE | 패턴 종료 | `LOOT-NONE` |

## 5. 적 action bundle

- 등록 적 53종은 각각 `ACT-<enemyId>` 하나를 가진다. bundle 안의 1~3개 행동은 정의 원장에 적힌 공격명·피해·상태·전조를 고정 순서가 아닌 쿨다운/거리 조건으로 실행한다.
- 행동 공통 상태는 `READY→TELEGRAPH→STARTUP→ACTIVE→RECOVERY→READY`다.
- 모든 피해는 `actionInstanceId`로 멱등 처리하고 최종 피해 commit 뒤 상태·브레이크·기여·피해 숫자 이벤트를 발행한다.
- 저 TPS에서 장식 입자·Display 보간·비핵심 투사체 수만 낮춘다. 전조 시간, 판정, 피해, 안전 구역은 바꾸지 않는다.
- 적 action의 텍스트 이름은 r2 data에서 허용하지 않으며 bundle의 `actions[].id`로 전개한다.

## 6. 적 증강

```text
일반 적 보유 확률 =
min(45%, 8% + 잠긴 개인 마일스톤 수×2%p + 지역 오염 단계×4%p)
```

- 아직 개인 증강 등급 잠금이 하나도 없으면 적 증강 없음이다.
- 일반 적은 최대 1개, 엘리트는 최신 잠긴 마일스톤 등급에서 1개 보장, 보스는 §3의 opt-in만 최대 1개다.
- 등급은 해당 마일스톤 최초 플레이어가 잠근 값이고 후보는 `AUG-LIST-001`의 같은 등급 monsterEligible pool에서 entity seed로 뽑는다.
- 스폰 이후 살아 있는 개체의 등급·증강을 재추첨하지 않는다.
- adapter가 없는 후보는 제외하며 유효 후보가 없으면 증강 없이 생성한다. 다른 등급으로 대체하지 않는다.

## 7. 피해 숫자 표시

- 최종 `DAMAGE_COMMITTED` 뒤 `ENT-UI-DAMAGE-NUMBER` TextDisplay를 victim 중심에서 높이 `boundingBox.maxY+0.25`, 좌우 seed offset 0.2~0.6블록에 생성한다.
- 표시 값은 방어·저항·보호막·상한을 모두 적용한 실제 HP 피해다. 보호막 흡수는 청록색, 치명타는 `✦`, 브레이크만 준 경우에는 별도 노란 게이지 숫자를 사용한다.
- 같은 source+victim+skill의 다단 피해는 4틱 창에서 합산해 `합계 ×타수`로 표시한다. 서로 다른 플레이어의 피해는 합치지 않는다.
- 기본 수명 14틱, 상승 0.35블록, 8틱부터 투명화한다. victim 사망·월드 이동·부모 encounter 종료 때 즉시 제거한다.
- 반경 32블록의 등록 파티원에게만 표시하고 관전자 설정의 `damageNumbers=false`를 존중한다. 구현은 Paper tracking API 또는 패킷 가시성으로 처리한다.
- 전역 활성 상한 160, victim당 8, viewer당 초당 40이다. 초과 시 같은 묶음을 합산하며 피해 이벤트 자체는 버리지 않는다.
- 0 피해·면역·아군 공격은 기본 숨김이다. 패링·완전 차단은 HUD 전투 로그 설정에서만 문자로 표시한다.

## 8. 자원 노드

- 6개 node template은 위치·히트박스·전조 표현만 소유하고 실제 WSR 지급은 `LOOT-LIST-001`과 도구 등급 검사가 결정한다.
- node instance는 `nodeId, world, position, materialPool, requiredToolTier, remainingUses, ownerEvent, despawnAt`을 저장한다.
- 등급 부족 시 블록·노드를 보존하고 WS·바닐라 드롭을 모두 지급하지 않는다.
- 한 채집 root event에서 바닐라 드롭과 WS 원장 지급을 동시에 commit하지 않는다.

## 9. 합계와 검증

| 범주 | 수량 |
|---|---:|
| 일반·강화·정예·Final 적 | 53 |
| 보스 | 4 |
| 투사체·UI·배치물 | 7 |
| 자원 노드 | 6 |
| 보스·Final 오브젝트/소환체 | 21 |
| 전체 entity template | 91 |

필수 검증:

- entity ID 91개 고유, Bukkit type·표현 폴백·부모·cleanup·loot 참조 누락 0
- 적 53개 action bundle·loot table 참조 고아 0
- 부모 종료·서버 재시작·청크 언로드 뒤 고아 Display/Interaction/투사체 0
- 소환체·핵·훈련·Final 보조의 EXP·드롭·표본·증강·기여 0
- 적 증강 등급 잠금 일치와 adapter 없는 효과 선택 0
- 피해 숫자가 victim 근처 실제 최종 피해와 일치하고 게임 피해 계산에 영향 0
- 리소스 팩 거부·REDUCED UI에서도 전조·안전 구역·대상 판별 가능
