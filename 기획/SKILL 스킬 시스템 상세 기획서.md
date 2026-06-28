# WildSurvival SKILL 스킬 시스템 상세 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 프로젝트 | WildSurvival |
| 카테고리 | SKILL 커스텀 스킬 시스템 |
| 콘텐츠 형태 | 2~4인 협동 하드코어 디펜스 RPG |
| 구현 환경 | Minecraft Java Edition, 바닐라 + 서버 플러그인 |
| 모드 사용 | 없음 |
| 문서 상태 | 단계별 상세 기획 진행 중 |
| 최종 수정일 | 2026-06-23 |

## 카테고리 공통 방향

- 일반 공격, 액티브, 채널링, 반응형, 몬스터와 보스 패턴은 공통 스킬 데이터와 실행 서비스를 사용한다.
- 스킬은 피해, 회복, 보호막, 상태이상, 브레이크, 이동, 소환과 자원 조작 효과를 함께 가질 수 있다.
- 하나의 효과는 여러 스탯을 동시에 참조할 수 있고, 한 스킬은 여러 독립 효과를 순서대로 실행할 수 있다.
- 서버 구동 중에도 스킬 데이터를 검증하고 실시간 교체할 수 있어야 한다.
- 보스는 허용된 플레이어 스킬을 복제하고 별도 오버라이드로 변경할 수 있다.
- `STANDARD`와 `CHAOS`는 같은 데이터 규격을 사용하되 수치 엔진과 최소 처리 간격을 다르게 적용한다.

---

# SKILL-001 스킬 데이터 규격과 계수·쿨타임·소모값

## 1. 시스템 목적

- 모든 스킬의 식별, 분류, 태그와 데이터 형식을 통일한다.
- 피해·회복·보호막 및 복합 계수를 동일한 구조로 계산한다.
- AP, 탄약, HP, 표식과 기타 자원을 원자적으로 소비한다.
- 쿨타임, 충전, ICD, 시전, 채널링과 취소 규칙을 공통 처리한다.
- 보스의 플레이어 스킬 복제와 안전한 실시간 핫 리로드를 지원한다.
- 카오스 모드의 BigNumber와 1틱 미만 논리 효과를 안정적으로 처리한다.

## 2. 스킬 ID와 표시 정보

```text
{소유군}.{무기·개체·시스템}.{스킬명}.v{호환 버전}
```

예시:

```text
player.sword.basic_1.v1
player.staff.arc_bolt.v1
monster.corrupted_spider.poison_spit.v1
boss.varkan.ground_slam.v1
boss.varkan.copy_player_skill.v1
```

- 영문 소문자, 숫자, `_`, `.`만 허용한다.
- 표시 이름과 설명은 별도 다국어 키로 관리한다.
- 같은 ID를 다른 의미의 스킬에 재사용하지 않는다.

필수 표시 데이터:

- `name-key`
- `description-key`
- `icon`
- `short-description`
- `detail-template`

## 3. 호환 버전과 리비전

| 값 | 역할 |
|---|---|
| 호환 버전 | 필드 의미와 실행 구조의 호환 단위 |
| 리비전 | 같은 구조 안의 계수·비용·시간 수정 단위 |
| 데이터 해시 | 실제 로드된 데이터 식별 |

- 계수, 비용, 쿨타임과 연출 수정은 같은 `.v1`에서 리비전만 변경한다.
- 필드 의미나 실행 구조가 달라지면 `.v2`를 생성한다.
- 실행 중인 시전, 투사체, 장판과 소환물은 시작 당시 리비전을 유지한다.
- 핫 리로드 이후 신규 실행부터 새 리비전을 사용한다.

## 4. 스킬 분류와 태그

### 최상위 분류

| 분류 | 내부 ID |
|---|---|
| 일반 공격 | `BASIC_ATTACK` |
| 액티브 | `ACTIVE` |
| 채널링 | `CHANNEL` |
| 토글 | `TOGGLE` |
| 수동 발동 | `PASSIVE_TRIGGER` |
| 반응형 | `REACTION` |
| 시스템·기믹 | `SYSTEM` |

### 복수 역할 태그

```text
DAMAGE, HEAL, SHIELD, BUFF, DEBUFF,
MOVEMENT, CONTROL, BREAK, SUMMON, RESOURCE,
MELEE, PROJECTILE, AREA, DOT, MULTI_HIT,
CHANNEL_TICK, HOMING, PIERCE_TERRAIN
```

규칙 태그:

```text
TRUE_PEN
ALLOW_NEGATIVE_ATK_HEAL
CAN_CRIT_HEAL
CAN_CRIT_SHIELD
DYNAMIC_SCALING
LETHAL_HP_COST
DISABLE_FRIENDLY_FIRE
```

- 한 스킬은 복수 태그를 가질 수 있다.
- 모순 태그는 로드 검증에서 거부한다.

## 5. 스킬 레벨과 효과 블록

- 데이터 구조는 레벨별 수치 테이블을 지원한다.
- 현재 기본 콘텐츠에서는 스킬 레벨 1만 사용한다.
- 장비 강화와 인챈트는 원본 레벨보다 별도 보정자를 사용한다.

한 스킬은 순서가 있는 여러 효과 블록을 가진다.

```text
DAMAGE
HEAL
SHIELD
STATUS
BREAK
MOVE
RESOURCE
SUMMON
SCRIPTED
```

- 피해, 회복, 보호막과 브레이크는 각각 독립된 계수 목록을 가진다.
- 효과 성공 여부에 따라 다음 효과 실행 여부를 지정할 수 있다.

## 6. 복수 스탯 계수

```text
원본 효과량 =
기본값
+ ATK × 1.20
+ 최대 HP × 0.05
+ 현재 AP × 0.30
```

지원 참조값:

```text
ATK, DEF, HIT, EVA, SPD,
CRIT, CRIT_DMG, PEN,
MAX_HP, CURRENT_HP, MISSING_HP,
MAX_AP, CURRENT_AP, MISSING_AP,
TARGET_MAX_HP, TARGET_CURRENT_HP, TARGET_MISSING_HP
```

- 참조 주체는 시전자, 대상, 소환자, 원본 소유자 중 하나로 지정한다.
- 현재값, 최대값과 손실값을 명시적으로 구분한다.
- 계수 `1.0`은 100%, `0.25`는 25%다.
- 음수 계수를 허용한다.
- `STANDARD`는 `double`, `CHAOS` 전투 효과량은 BigNumber를 사용한다.

## 7. 스탯 스냅샷

| 실행 유형 | 기본 정책 |
|---|---|
| 즉시 | 실행 시작 시 시전자 스탯 고정 |
| 투사체 | 발사 시 시전자 스탯 고정 |
| 채널링 | 시전 시작 시 고정 |
| 설치물·소환물 | 생성 시 원본 소유자 스탯 고정 |
| `DYNAMIC_SCALING` | 효과 틱 또는 적중 시 재조회 |

- 대상 DEF, 보호막, 받는 피해와 생명 상태는 적중 시 조회한다.
- 핫 리로드 이후에도 진행 중 실행은 기존 리비전과 스냅샷을 유지한다.

## 8. HP 비례 피해

```text
TARGET_CURRENT_HP_RATIO
TARGET_MAX_HP_RATIO
TARGET_MISSING_HP_RATIO
```

- 일반 적, 엘리트, 보스별 효율을 필수로 설정한다.
- 보스에게는 개별 1회 상한과 초당 상한을 필수로 둔다.
- DEF, 피해 보정, 보호막과 흡혈 적용 여부를 명시한다.
- 보스 상한이 누락되면 보스 대상 효율을 0으로 처리하고 경고한다.

## 9. 일반 피해 규격

`NORMAL` 피해의 필수 데이터:

- 기본 피해와 계수 목록
- 치명타 허용 여부
- DEF·PEN 적용 여부
- `DAMAGE_DEALT`·`DAMAGE_TAKEN` 적용 여부
- 보호막 적용 여부
- 생명력 흡수 허용 여부
- 빈사 전환 허용 여부
- 아군 공격 정책

기본적으로 일반 피해는 모든 일반 전투 보정을 허용한다.

## 10. 고정 피해 규격

`FIXED` 피해 기본 정책:

| 항목 | 기본값 |
|---|---|
| 공격자 `DAMAGE_DEALT` | 적용 |
| 대상 `DAMAGE_TAKEN` | 적용 |
| 치명타 | 미적용 |
| DEF·PEN | 미적용 |
| 보호막 | 적용 |
| 생명력 흡수 | 적용 |
| 빈사 전환 | 적용 |

- 흡혈은 실제 HP 피해량만 사용한다.
- 보호막 흡수량과 과잉 피해는 제외한다.
- 아군 공격은 흡혈을 발동하지 않는다.
- 공격 분류별 흡혈 효율과 회복 보정을 적용한다.

## 11. 회복과 보호막

### 회복

- 독립된 기본값과 계수 목록을 가진다.
- `HEALING_DONE`과 `HEALING_RECEIVED`를 적용한다.
- 기본적으로 치명타가 발생하지 않는다.
- `CAN_CRIT_HEAL` 태그가 있을 때만 치명타를 허용한다.
- 일반 HP와 빈사 체력 회복을 구분한다.

### 보호막

- 독립된 기본값과 계수 목록을 가진다.
- `SHIELD_POWER`를 적용한다.
- `CAN_CRIT_SHIELD` 태그가 있을 때만 치명타를 허용한다.
- 지속시간, 중첩 그룹과 최대 보유량을 명시한다.

## 12. 보스의 플레이어 스킬 복제

보스는 원본 플레이어 스킬을 참조한 뒤 오버라이드를 적용한다.

```yaml
skill:
  id: boss.varkan.copy_player_skill.v1
  copy:
    source-skill: player.sword.slash.v1
    selection-policy: LAST_USED_BY_TARGET
    overrides:
      resource-costs: []
      cooldown-seconds: 12
      coefficient-multiplier: 1.50
      range-multiplier: 1.25
      visual-id: varkan_corrupted_slash
```

복제 정책:

```text
ALLOW
ALLOW_WITH_OVERRIDE
DENY
```

- 기본값은 `DENY`다.
- 공격 스킬만 명시적으로 허용한다.
- 부활, 시설, 파티 자원과 운영 스킬은 기본적으로 복제 불가다.

지원 선택 정책:

- 지정 플레이어의 마지막 사용 스킬
- 전투 중 기록된 스킬 중 무작위
- 기여도가 가장 높은 플레이어의 스킬
- 특정 태그 스킬
- 패턴에 미리 지정된 플레이어 스킬

- 효과와 실행 구조는 원본을 참조한다.
- 보스 자원과 쿨타임은 패턴이 덮어쓴다.
- 플레이어 전용 장비와 탄약 조건은 무시할 수 있다.
- 피해 계수, 범위, 대상, 연출, 상태이상과 투사체를 오버라이드할 수 있다.
- 복제 스킬의 원본 소유자는 보스이며 플레이어에게 기여도를 귀속하지 않는다.

## 13. AP 비용

| 유형 | 기본 소비 시점 |
|---|---|
| 일반 공격 | AP 0 |
| 즉시 액티브 | 실행 확정 |
| 시전 | 시전 시작 |
| 채널링 | 시작 비용과 주기 비용 |
| 토글 | 활성화 비용과 유지 비용 |

필드:

- 기본 AP 비용
- 비용 태그
- 감소 허용 여부
- 최종 최소 비용
- 시작 비용
- 주기 비용

- 비용 계산은 `COMBAT-002`를 따른다.
- 일반 무기 스킬의 최종 최소 비용은 기본 비용의 50%다.

## 14. 기타 자원과 원자 소비

지원 자원:

- 화살·전용 탄약
- 아이템·소모품
- 현재 HP 고정값·비율
- 최대 HP 비율
- 보호막
- 장비 전용 게이지
- 중첩·표식
- 파티 공용 자원
- 시설 자원

- 모든 필수 비용을 충족할 때만 한 번에 소비한다.
- 하나라도 부족하면 아무 비용도 소비하지 않는다.
- 소비 직전 서버에서 전체 비용을 재검증한다.
- 일부 소비 실패는 트랜잭션으로 복구한다.

## 15. HP 비용

- 일반 HP 비용은 현재 HP를 1 미만으로 만들 수 없다.
- `LETHAL_HP_COST`만 비용으로 빈사 진입을 허용한다.
- HP 비용은 피해가 아니며 DEF, 보호막, 피격·반격을 발생시키지 않는다.
- 흡혈과 회복량 증가로 비용을 줄이거나 상쇄할 수 없다.

## 16. 취소와 환급

기본적으로 플레이어 취소, 적 공격과 CC 취소에는 환급하지 않는다.

지원 필드:

```text
refund-on-system-cancel
refund-on-no-valid-target
refund-before-active
refund-ratio
```

- 시스템 안전 취소의 기본 환급은 100%다.
- 환급은 실제 소비값 기준이다.

## 17. 쿨타임

| 유형 | 기본 시작 시점 |
|---|---|
| 즉시 | 실행 확정 |
| 시전 | 시전 시작 |
| 채널링 | 채널 시작 |
| 토글 | 활성화 시작 |
| 실행 실패 | 시작하지 않음 |

예외:

```text
ON_CAST_START
ON_CAST_END
ON_ACTIVE
ON_HIT
```

액티브 스킬과 충전 회복 시간은 `SKILL_HASTE`를 적용한다.

```text
최종 쿨타임 =
max(1틱, 기본 쿨타임 × 100 / (100 + SKILL_HASTE))
```

- 최종 감소율 최대 99%다.
- 일반 공격에는 적용하지 않는다.
- 진행 중 쿨타임은 핫 리로드로 소급 변경하지 않는다.

## 18. 공유 쿨타임과 충전

전역 쿨타임은 기본적으로 사용하지 않는다.

지원 공유 범위:

- 개인 스킬
- 무기군
- 스킬 그룹
- 아이템
- 보스 패턴

- 여러 쿨타임 중 가장 늦은 종료 시각까지 사용 불가다.

충전형 필드:

- 최대·시작 충전 수
- 사용당 소비 수
- 충전당 회복 시간
- 순차·동시 회복
- 최대 충전 HUD 정책

- 충전 회복에도 `SKILL_HASTE`, 최소 1틱과 99% 상한을 적용한다.

## 19. 내부 재사용 대기시간

```yaml
trigger:
  id: on_hit_ap_refund
  internal-cooldown: 3.0
  scope: TARGET
```

지원 범위:

```text
EXECUTION
TARGET
CASTER
PARTY
ENCOUNTER
```

- AP 환급, 처치, 상태이상, 흡혈과 보상 반복 악용 방지에 사용한다.
- 핫 리로드 이후에도 진행 중 ICD는 기존 종료 시각을 유지한다.

## 20. 쿨타임 시간과 저장

- 회차 전체 정지 시 쿨타임도 정지한다.
- 개인 접속 종료 중 회차가 진행되면 개인 쿨타임은 계속 흐른다.
- 서버 종료와 정상 중단 시 남은 쿨타임, 충전과 저장 대상 ICD를 저장한다.
- 짧은 일반 공격 간격은 저장하지 않는다.

## 21. 시전 단계와 이동

```text
PRECAST → CASTING → ACTIVE → RECOVERY
```

| 단계 | 역할 |
|---|---|
| `PRECAST` | 조건·대상·비용·쿨타임 검증 |
| `CASTING` | 시전 시간과 중단 판정 |
| `ACTIVE` | 실제 효과 생성 |
| `RECOVERY` | 후딜레이 |

모든 시전 스킬은 이동 정책을 필수로 가진다.

```text
FREE
SPEED_MULTIPLIER
ROOTED
CANCEL_ON_MOVE
```

- 점프, 질주와 회피 허용 여부를 별도 설정한다.

## 22. 시전 중단

- `interrupt-damage-threshold`를 필수로 정의한다.
- 고정 피해량 또는 최대 HP 비율을 사용할 수 있다.
- 보호막 흡수 피해 포함 여부를 명시한다.
- 강한 CC는 기본적으로 즉시 중단한다.
- 장비 강제 해제, 빈사와 사망은 시전을 중단한다.

## 23. 채널링

- 효과 틱과 자원 소비 틱을 별도로 설정할 수 있다.
- 최대 지속시간을 필수로 정의한다.
- 입력 해제, 자원 부족, CC와 장비 강제 해제로 종료할 수 있다.

| 규칙 모드 | 서버 실행 최소 간격 |
|---|---:|
| `STANDARD` | 5틱 |
| `CHAOS` | 1틱 |

논리 효과 간격은 서버 실행 최소 간격보다 짧을 수 있다. 이 경우 실행 시점까지 누적한다.

```text
논리 간격 0.5틱, 회당 5 피해
→ 1틱마다 10 피해
```

합산 대상:

- 피해·회복·보호막
- AP·HP 비용
- 브레이크 피해
- 오염도·게이지 변화

- 수치는 논리 실행 횟수만큼 합산한다.
- 치명타, 상태이상, 흡혈, 적중 보상과 ICD는 서버 틱당 한 번만 판정한다.
- 마지막 불완전 구간은 남은 논리 실행량에 비례 적용한다.

## 24. 토글 스킬

- 같은 입력으로 활성화·비활성화한다.
- 활성화 비용과 주기 유지 비용을 가진다.
- 자원 부족 시 자동 종료한다.
- 동시 활성 토글 그룹을 설정할 수 있다.
- 빈사, 사망, 장비 강제 해제와 회차 중단 시 종료한다.

## 25. 입력과 스킬 제공 출처

| 입력 | 기본 역할 |
|---|---|
| 좌클릭 | 주무기 일반 공격 |
| 우클릭 | 문맥 상호작용·방어·아이템 |
| 웅크리기 + 좌클릭 | 주무기 액티브 1 |
| 웅크리기 + 우클릭 | 보조무기 또는 액티브 2 |
| 핫바 스킬 아이템 | 추가 액티브 |
| 웅크리기 두 번 | 기본 회피 |
| 웅크리기 + 점프 | 대체 회피 |

- 기존 `COMBAT-003` 회피 입력을 유지한다.
- 한 플레이어에게 한 가지 회피 입력만 활성화한다.
- 최종 접근성 검증은 `UX-001`에서 수행한다.

스킬 제공 출처:

- 주무기·보조무기
- 방어구·장신구·부적
- 증강·인챈트
- 상태·기믹 임시 스킬

- 별도 자유 스킬 장착 슬롯은 기본적으로 제공하지 않는다.

## 26. 전투 중 장비 강제 해제

- 전투 상태에서 플레이어가 자의로 장비를 해제할 수 없다.
- 보스 기믹, 무장 해제, 장비 파괴만 강제 해제를 발생시킬 수 있다.
- 필수 장비가 해제되면 진행 중 시전을 취소한다.
- 이미 생성된 투사체, 장판과 소환물은 기존 스냅샷을 유지한다.
- 다시 착용해도 기존 쿨타임을 유지한다.

## 27. 대상 요구와 실패

지원 정책:

```text
NONE, TARGET, GROUND, DIRECTION,
ALLY, ENEMY, DOWNED_ALLY, CORPSE, STRUCTURE
```

- 유효 대상이 없으면 실행, 비용과 쿨타임을 시작하지 않는다.
- 시전 중 이탈 시 취소, 마지막 위치 실행, 방향 실행 중 하나를 지정한다.

실패 사유 우선순위:

```text
생명·행동 불가
→ 장비·대상 오류
→ 자원 부족
→ 쿨타임
→ 거리·시야
→ 기타 조건
```

## 28. 조건식과 논리 게이트

조건식은 생명·전투 상태, 장비, 자원, 대상 상태, 거리, Day, 오염, 증강, 인챈트, 파티 및 보스 페이즈를 읽을 수 있다.

입력 지원:

```text
AND, OR, NOT, XOR,
NAND, NOR, XNOR, IMPLIES,
기타 등록 논리 게이트
```

- 내부 엔진은 `AND`, `OR`, `NOT`, `XOR` 트리로 정규화한다.
- 임의 스크립트 실행은 허용하지 않는다.
- 조건은 읽기 전용이며 평가 중 실행이나 데이터 변경을 금지한다.
- 최대 깊이·노드 수·평가 시간과 순환 참조 제한을 둔다.

## 29. 규칙 모드

```text
game-mode: STANDARD | CHAOS
difficulty: EASY | NORMAL | HARD | CHALLENGE | UNKNOWN
```

- 규칙 모드와 난이도는 독립적으로 선택한다.
- 회차 생성 시 확정하고 진행 중 변경할 수 없다.

| 모드 | 최종 AP 상한 |
|---|---:|
| `STANDARD` | 200 |
| `CHAOS` | 999 |

- AP는 BigNumber를 사용하지 않으며 최소값은 0이다.

## 30. 카오스 BigNumber

```text
부호 × 가수 × 10^지수

sign: -1 | 0 | 1
mantissa: BigDecimal
exponent: BigInteger
```

- 가수 유효 숫자는 18자리다.
- 내부 반올림은 `HALF_UP`이다.
- NaN과 실제 무한대는 허용하지 않는다.

적용 대상:

- HP·보호막
- ATK·DEF와 피해 관련 스탯
- 피해·회복·브레이크 수치
- 몬스터·시설 HP
- 기여도 피해량
- 고정·환경 피해

AP, 확률, 시간, 이동속도, 대상 수는 일반 수치 체계를 유지한다.

- 음수 ATK와 `ALLOW_NEGATIVE_ATK_HEAL`을 BigNumber 부호·절댓값으로 처리한다.
- 흡혈도 BigNumber 실제 HP 감소량을 기준으로 계산한다.

## 31. 카오스 DEF와 PEN

```text
공격 기준값 = max(1, DEF 적용 전 피해량)
방어 비율 = 유효 DEF / 공격 기준값

카오스 피해 감소율 =
min(99%, 방어 비율 / (1 + 방어 비율))
```

- DEF가 공격 기준값과 같으면 50% 감소한다.
- DEF가 9배면 90% 감소한다.
- 피해 감소 상한은 99%다.
- 일반 PEN은 대상 DEF의 최대 98%까지 관통한다.
- `TRUE_PEN`만 DEF를 0까지 낮춘다.
- `STANDARD`의 일반 PEN 상한은 90%를 유지한다.

## 32. 카오스 하드캡과 표시

카오스에서도 효과율 하드캡은 유지한다.

- `DAMAGE_DEALT` +300%
- `DAMAGE_TAKEN` 감소 80%, 증가 +300%
- `CRIT_DMG` 300%
- 보호막 효율 +300%
- 브레이크 피해 증가 +300%
- 생명력 흡수 100%
- DEF 피해 감소 99%

서버 성능 상한도 유지한다.

- 최대 대상·투사체·소환물·장판 수
- 상태이상 적용 시도 수
- 조건식 평가량

표시:

```text
12,345
1.23M
4.56B
7.89e42
```

상세 화면에는 전체 가수와 지수를 표시한다.

## 33. STANDARD 기술 한계

| 항목 | 한계 |
|---|---:|
| 기본 효과량·계수 결과 절댓값 | 1,000,000,000 |
| 기본 쿨타임 | 24시간 |
| 시전·채널링 | 1시간 |
| AP 단일 소비 | 모드별 AP 상한 이내 |
| 타격·중첩 수 | 데이터별 상한 필수 |

- 카오스 BigNumber 효과량에는 절댓값 상한을 적용하지 않는다.
- 시간, 확률, AP와 성능 한계는 카오스에서도 유지한다.

## 34. 실시간 핫 리로드

실시간 변경 허용 항목:

- 기본값·계수
- AP·기타 비용
- 쿨타임·충전·ICD
- 시전·채널링 시간
- 조건식·태그·상태이상
- 참조·복제 스킬
- 연출·MagicSpells ID

원자 적용 절차:

1. 변경 파일을 임시 파싱한다.
2. 전체 필드와 참조를 검증한다.
3. 순환 참조와 기술 한계를 검사한다.
4. 모든 변경이 정상일 때만 새 레지스트리로 교체한다.
5. 하나라도 오류면 변경 묶음 전체를 거부한다.
6. 기존 정상 데이터와 실행을 유지한다.

- Minecraft `/reload`는 사용하지 않고 전용 명령을 사용한다.
- 신규 실행부터 새 리비전을 사용한다.
- 진행 중 실행, 쿨타임, 충전과 ICD는 기존 리비전과 종료 시각을 유지한다.

## 35. 삭제·별칭·롤백

- 참조 중인 스킬은 즉시 삭제하지 못한다.
- `deprecated`로 신규 사용만 차단한다.
- ID 변경은 이전 ID의 별칭을 등록한다.
- 적용 시각, 운영자, 이전·신규 해시, 파일 목록과 결과를 기록한다.
- 최근 정상 버전을 보관하고 직전 버전으로 롤백할 수 있다.
- 롤백도 신규 실행부터 적용한다.

## 36. 데이터 형식과 오류 처리

- 스킬 정의 원본은 YAML이다.
- 서버 시작 및 핫 리로드 시 구조화 파서로 읽는다.
- 검증 후 불변 스킬 정의 객체를 생성한다.

검출 오류:

- 필수 필드 누락·중복 ID
- 존재하지 않는 참조
- 순환 복제·조건·공유 쿨타임
- 음수 쿨타임·잘못된 시간
- 모순 태그
- 지원하지 않는 계수
- 기술·성능 상한 누락

- 서버 시작 시 오류 스킬과 필수 장비만 사용 불가로 만들고 서버는 계속 실행할 수 있다.
- 핫 리로드 오류는 변경 전체를 거부하고 기존 레지스트리를 유지한다.

## 37. 데이터 예시

```yaml
skills:
  player.staff.arc_bolt.v1:
    revision: 12
    type: ACTIVE
    name-key: skill.player.staff.arc_bolt.name
    tags: [DAMAGE, PROJECTILE]
    boss-copy-policy: ALLOW_WITH_OVERRIDE
    input:
      action: SNEAK_LEFT_CLICK
    requirements:
      target-policy: DIRECTION
      condition:
        gate: AND
        children:
          - { type: LIFE_STATE, value: ALIVE }
          - { type: EQUIPPED_WEAPON, value: STAFF }
    costs:
      atomic: true
      entries:
        - type: AP
          amount: 20
          cost-tag: WEAPON_SKILL
          minimum-ratio: 0.50
    cooldown:
      start-policy: ON_CAST_START
      base-seconds: 4.0
      haste-enabled: true
      shared-groups: [STAFF_ACTIVE]
    cast:
      base-cast-seconds: 0.5
      cast-speed-enabled: true
      movement-policy: SPEED_MULTIPLIER
      movement-multiplier: 0.70
      interrupt:
        strong-cc: true
        damage-threshold-max-hp-ratio: 0.05
    effects:
      - id: main_damage
        type: DAMAGE
        classification: NORMAL
        base:
          standard: 100
          chaos: "1.0e3"
        scaling:
          - { source: CASTER, stat: ATK, coefficient: 1.50 }
          - { source: CASTER, stat: MAX_AP, coefficient: 0.20 }
        critical: true
        def: true
        pen: true
        damage-dealt: true
        damage-taken: true
        shield: true
        lifesteal: true
        downed-transition: true
```

## 38. 플러그인 구현 방향

- `SkillRegistry`가 ID, 호환 버전, 리비전과 해시를 관리한다.
- `SkillExecutionService`는 검증된 불변 정의만 실행한다.
- `NumericEngine`은 STANDARD와 CHAOS 연산을 분리한다.
- `ResourceTransactionService`가 비용을 원자 처리한다.
- `ConditionEngine`은 정규화된 읽기 전용 논리 트리를 평가한다.
- `SkillReloadService`가 임시 레지스트리 검증 후 원자 교체한다.
- 보스 복제는 원본을 변경하지 않고 오버라이드 뷰를 생성한다.

## 39. 저장 데이터

```yaml
run:
  game-mode: CHAOS
  skill-registry:
    compatibility-version: 1
    revision: 42
    hash: "sha256:..."
players:
  "player-uuid":
    cooldowns:
      player.staff.arc_bolt.v1:
        revision: 41
        remaining-millis: 2300
```

- BigNumber는 부호, 18자리 가수와 임의 정밀도 지수로 저장한다.
- 저장된 별칭은 로드 시 현재 ID로 변환한다.

## 40. 테스트 항목

| 테스트 | 확인 목적 |
|---|---|
| 복수 스탯·독립 효과 계수 | 효과량 계산 검증 |
| 일반·고정 피해 | 보정·치명타·DEF·흡혈 검증 |
| HP 비례 보스 피해 | 효율과 상한 검증 |
| 복수 자원·HP 비용 | 원자 소비와 빈사 정책 검증 |
| 쿨타임·충전·ICD | 시작 정책과 저장 검증 |
| 회차 정지·접속 종료 | 시간 흐름 검증 |
| 시전 이동·피격·CC | 중단·환급 검증 |
| STANDARD 5틱 채널 | 최소 처리 검증 |
| CHAOS 1틱·0.5틱 효과 | 합산 및 틱당 판정 검증 |
| 논리 게이트 | 정규화·순환·제한 검증 |
| 보스 스킬 복제 | 선택·오버라이드·기여도 검증 |
| 핫 리로드·롤백 | 원자 교체와 실행 보존 검증 |
| BigNumber | HP·피해·DEF·브레이크·흡혈 검증 |
| 모드별 AP | 200·999 상한 검증 |
| 카오스 DEF·PEN | 99% 감소·98% 관통 검증 |

## 41. SKILL-001 완료 기준

- 모든 스킬이 ID, 호환 버전, 리비전, 분류와 복수 태그를 가진다.
- 복수 스탯 및 효과별 독립 계수를 사용할 수 있다.
- 일반·고정·회복·보호막이 확정된 보정 정책을 따른다.
- AP와 추가 자원이 원자적으로 소비된다.
- 쿨타임, 공유 그룹, 충전과 ICD가 일관되게 처리된다.
- 시전, 채널링, 토글, 중단과 환급을 데이터로 표현한다.
- 모든 대상 요구와 논리 게이트 조건식을 안전하게 평가한다.
- 보스가 허용된 플레이어 스킬을 복제·오버라이드할 수 있다.
- 스킬 데이터가 서버 구동 중 원자적으로 핫 리로드·롤백된다.
- 진행 중 실행과 쿨타임이 핫 리로드로 소급 변경되지 않는다.
- STANDARD와 CHAOS가 모드별 AP, 수치와 처리 간격 규칙을 따른다.
- 카오스 BigNumber가 초대형 전투 수치를 처리한다.
- 1틱 미만 효과가 수치는 합산하고 부가 판정은 틱당 한 번 수행한다.
- 잘못된 데이터가 기존 정상 레지스트리를 손상시키지 않는다.

---

# SKILL-002 스킬 범위·대상 선정·최대 개체·다단 판정

## 1. 문서 목적

이 작업은 스킬이 어느 공간을 검사하고, 어떤 대상을 어떤 순서로 선택하며, 한 실행에서 몇 번 적중할 수 있는지를 정의한다.

- 모든 범위와 충돌 판정은 서버 권위로 처리한다.
- 범위 판정은 바닐라 공격 범위와 분리한다.
- 효과 블록마다 독립적인 범위, 필터, 최대 대상 수와 다단 규칙을 가질 수 있다.
- MagicSpells는 사용할 수 있으나 커스텀 HP, 피해, 상태이상, 브레이크의 최종 결과를 직접 확정하지 않는다.
- 일반 모드와 카오스 모드는 동일한 의미 규칙을 사용하되 처리량 상한과 최소 처리 간격이 다르다.

## 2. 핵심 실행 구조

스킬 한 번의 실행은 다음 계층을 사용한다.

| 계층 | 의미 |
|---|---|
| `execution` | 스킬 한 번의 시전 또는 일반 공격 실행 |
| `effect-block` | 피해, 회복, 상태이상, 이동 등 독립 효과 단위 |
| `query` | 특정 시점의 공간 및 대상 검색 |
| `target-selection` | 필터와 정렬을 통과한 최종 대상 목록 |
| `hit-request` | 특정 대상에게 특정 효과를 적용하려는 한 번의 요청 |
| `hit-result` | 적중, 회피, 패링, 면역, 사망 등 최종 결과 |

- 실행마다 전역적으로 유일한 `execution-id`를 생성한다.
- 각 효과 블록은 실행 안에서 유일한 `effect-id`를 가진다.
- 각 타격 요청은 `execution-id + effect-id + hit-sequence + target-uuid`로 고유 `hit-id`를 만든다.
- 같은 `hit-id`는 서버 재호출, MS 중복 콜백 또는 이벤트 재귀가 발생해도 한 번만 처리한다.

## 3. 좌표계와 판정 기준

### 기본 좌표계

- 기본 범위는 월드의 `X/Y/Z`를 모두 사용하는 3차원 판정이다.
- 스킬별로 `horizontal-only: true`를 지정하면 높이 차이를 별도 허용값으로 제한하고 수평 평면을 기준으로 판정한다.
- 거리 비교는 별도 명시가 없으면 유클리드 거리의 제곱값을 사용한다.
- 회전은 시전자 시선의 `yaw`와 `pitch`를 모두 사용할 수 있다.

### 경계 포함

- 도형의 경계에 정확히 닿은 히트박스는 범위 안으로 인정한다.
- 부동소수점 오차용 기본 허용값은 `epsilon: 0.000001`이다.
- 경계 판정은 `distance <= radius + epsilon` 형태로 처리한다.

### 실제 히트박스

- 엔티티 중심점만 검사하지 않고 Paper/Bukkit에서 얻은 실제 바운딩 박스를 사용한다.
- 1차로 도형을 감싸는 AABB를 사용해 후보를 찾고, 2차로 도형과 대상 히트박스의 실제 교차를 검사한다.
- 웅크리기, 수영, 비행, 빈사 등 자세에 따라 바뀐 현재 히트박스를 사용한다.
- 장식용 엔티티, 무적 연출 엔티티와 투사체 표시용 엔티티는 기본 대상에서 제외한다.

## 4. 범위 원점

효과 블록은 다음 원점을 사용할 수 있다.

| 원점 | 설명 |
|---|---|
| `CASTER_FEET` | 시전자 발 위치 |
| `CASTER_CENTER` | 시전자 히트박스 중심 |
| `CASTER_EYE` | 시전자 눈 위치 |
| `WEAPON_MUZZLE` | 무기별 발사 오프셋이 적용된 위치 |
| `TARGET_FEET` | 지정 대상 발 위치 |
| `TARGET_CENTER` | 지정 대상 히트박스 중심 |
| `TARGET_EYE` | 지정 대상 눈 위치 |
| `TARGET_LOCATION` | 조준 또는 사전 선택된 위치 |
| `PROJECTILE` | 현재 투사체 위치 |
| `AREA_CENTER` | 지속 영역의 생성 위치 |
| `PREVIOUS_HIT` | 연쇄 스킬의 직전 적중 위치 |
| `FIXED_LOCATION` | 기믹이 제공한 고정 월드 좌표 |

- 원점에는 로컬 또는 월드 좌표 오프셋을 추가할 수 있다.
- `WEAPON_MUZZLE`은 무기 모델 연출과 실제 판정 시작점이 과도하게 어긋나지 않도록 최대 오프셋을 검증한다.
- 원점 엔티티가 사라졌을 때의 정책은 `TERMINATE`, `KEEP_LAST_LOCATION`, `FOLLOW_OWNER` 중 스킬별로 지정한다.

## 5. 회전 추적

| 정책 | 처리 |
|---|---|
| `SNAPSHOT` | 실행 시점의 방향을 고정 |
| `TRACK_CASTER` | 처리 시점마다 시전자 방향을 사용 |
| `TRACK_TARGET` | 처리 시점마다 원점에서 목표를 향함 |
| `FIXED` | 데이터에 기록된 고정 방향 사용 |
| `FOLLOW_PROJECTILE` | 투사체의 현재 진행 방향 사용 |

- 회전 추적은 효과 블록 또는 투사체별로 지정한다.
- 즉발 일반 공격은 기본 `SNAPSHOT`이다.
- 채널 빔은 기본 `TRACK_CASTER`, 유도 투사체는 `TRACK_TARGET` 또는 자체 유도 규칙을 사용한다.
- 시전 도중 기절, 빈사, 사망 또는 월드 이탈이 발생하면 기존 중단 정책을 우선한다.

## 6. 지원 범위 도형

모든 도형은 직접 조합할 수 있으며, 하나의 효과 블록에 여러 도형을 `UNION`, `INTERSECTION`, `SUBTRACT` 방식으로 결합할 수 있다.

| 도형 | 주요 데이터 | 판정 의미 |
|---|---|---|
| `RAY` | `length` | 두께가 없는 시선 광선과 히트박스 교차 |
| `LINE` | `length`, `width`, `height` | 폭과 높이를 가진 직선 구간 |
| `CAPSULE` | `length`, `radius` | 선분과 양 끝 반구를 합친 근접·돌진 범위 |
| `BOX` | `width`, `height`, `depth` | 원점과 회전을 따르는 직육면체 |
| `SPHERE` | `radius` 또는 축별 반지름 | 구 또는 타원체 |
| `CYLINDER` | `radius`, `height`, `axis` | 원기둥 또는 타원기둥 |
| `CONE` | `length`, `angle`, `vertical-angle` | 원점에서 퍼지는 3차원 원뿔 |
| `ARC` | `inner-radius`, `outer-radius`, `angle` | 부채꼴 근접 공격 |
| `RING` | `inner-radius`, `outer-radius`, `height` | 중심이 비어 있는 고리 |

### 도형별 기본 규칙

- `RAY`는 최초 충돌 지점만 필요할 때 사용한다.
- `LINE`은 검기, 빔, 관통 사격처럼 폭이 있는 직선 공격에 사용한다.
- `CAPSULE`은 공격 시작점과 끝점 사이를 연속 검사하므로 빠른 돌진 공격에 우선 사용한다.
- `BOX`는 벽, 직사각 장판과 전방 사각 베기에 사용한다.
- `SPHERE`는 폭발과 구형 오라에 사용한다.
- `CYLINDER`는 지면 장판과 수직 기둥에 사용한다.
- `CONE`과 `ARC`는 방향 기반 공격이며, `horizontal-only`를 별도로 지정할 수 있다.
- `RING`은 중심 안전지대가 있는 보스 기믹에 사용한다.

## 7. 복합 도형

```yaml
shape:
  combine: SUBTRACT
  base:
    type: CYLINDER
    radius: 8
    height: 3
  subtract:
    type: CYLINDER
    radius: 3
    height: 3
```

- 복합 도형은 최대 깊이 `4`, 구성 노드 `16`을 기본 상한으로 둔다.
- `UNION`은 하나라도 교차하면 포함한다.
- `INTERSECTION`은 모든 도형과 교차해야 포함한다.
- `SUBTRACT`는 기본 도형에는 들어오고 제외 도형에는 들어오지 않아야 한다.
- 성능 상한을 넘는 도형은 로드 시 오류로 처리한다.

## 8. 시야선과 장애물

### 기본 LOS 표본

대상 히트박스의 다음 세 지점을 검사한다.

1. 히트박스 중심
2. 상단에서 10% 아래 지점
3. 하단에서 10% 위 지점

- 셋 중 하나라도 원점과 연결되면 기본 LOS를 통과한다.
- `los-policy: ALL_SAMPLES`를 사용하면 세 지점이 모두 보여야 한다.
- `los-policy: NONE`은 시야선을 검사하지 않는다.
- 투명 블록, 통과 가능한 블록과 액체 충돌 정책은 스킬별 설정을 사용할 수 있다.
- 부분 엄폐에 따른 피해 비율 감소는 기본적으로 사용하지 않는다.

### 장애물 정책

| 정책 | 처리 |
|---|---|
| `BLOCK` | 최초 장애물에서 공격 종료 |
| `IGNORE_PASSABLE` | 충돌부가 없는 통과 가능 블록 무시 |
| `PIERCE_TERRAIN` | 허용 두께만큼 지형 관통 |
| `IGNORE_TERRAIN` | 지형 무시 |
| `EXPLODE_ON_TERRAIN` | 충돌 위치에서 후속 효과 실행 |

- 벽 관통 증강 또는 효과는 `terrain-policy`를 실행 시점에 오버라이드한다.
- 청크 경계 밖을 검사하기 위해 청크를 강제 로드하지 않는다.
- 투사체 또는 범위 진행 경로가 미로드 청크에 닿으면 해당 실행을 종료한다.

## 9. 대상 필터

모든 대상 조건은 조합 가능하며 `include`, `exclude`, `required-tags`, `forbidden-tags`를 함께 사용할 수 있다.

### 관계 필터

- `SELF`
- `ALLY`
- `ENEMY`
- `NEUTRAL`
- `OWNER`
- `SUMMON_OF_CASTER`
- `SUMMON_OF_ALLY`
- `SUMMON_OF_ENEMY`

### 개체 필터

- `PLAYER`
- `MONSTER`
- `BOSS`
- `ELITE`
- `SUMMON`
- `NPC`
- `PROJECTILE`
- `INTERACTABLE`

### 생존 상태 필터

- `ALIVE`
- `DOWNED`
- `DEAD`
- `SPECTATOR`
- `TARGETABLE`
- `INVULNERABLE`

### 태그 및 조건 필터

- 몬스터 등급, 종족, 속성, 보스 페이즈, 상태이상 보유 여부, 기지 안팎, 오염 구간을 조건으로 사용할 수 있다.
- 시전자 자신은 기본 제외하지만 `include-self: true`로 스킬별 허용한다.
- 공격 스킬도 아군을 정상적으로 선택할 수 있다. 최종 아군 피해, 브레이크와 강제 이동 배율은 `CORE-001` 규칙을 따른다.
- MS의 `can-target`, `target-modifiers`는 후보 수를 줄이는 선행 필터로 사용할 수 있으나 WildSurvival 필터를 대체하지 않는다.

## 10. 필터 처리 순서

1. 월드와 청크 유효성 검사
2. 대상 엔티티 종류와 내부 제외 태그 검사
3. 생존 및 관전 상태 검사
4. 관계와 팀 검사
5. 스킬별 include/exclude 검사
6. 도형과 실제 히트박스 교차 검사
7. LOS와 장애물 검사
8. 상태, 보스 페이즈와 사용자 정의 조건 검사
9. 정렬
10. 최대 대상 수만큼 확정

- 최대 대상 확정 후의 회피, 패링, 면역과 무효 결과는 선택 슬롯을 되돌려 주지 않는다.
- 즉, `max-targets: 3`에서 세 번째 대상이 면역이어도 네 번째 후보를 대신 선택하지 않는다.
- 이는 적중 결과에 따라 대상 수가 흔들리거나 서버 틱 순서가 개입하는 것을 방지한다.

## 11. 최대 대상 수

- `max-targets`는 스킬 전체가 아니라 효과 블록별 필수 데이터다.
- `0` 또는 생략을 무제한으로 해석하지 않는다. 명시적 `UNLIMITED`는 기술 상한 안에서만 허용한다.
- 단일 대상은 `max-targets: 1`이다.
- 광역 효과는 데이터상 값과 모드별 기술 상한 중 작은 값을 사용한다.
- 연쇄 공격의 `max-chain-targets`와 투사체의 `entity-pierce-count`는 별도 제한이다.

## 12. 대상 정렬 정책

다음 정책을 스킬별로 선택할 수 있다.

| 정책 | 기준 |
|---|---|
| `NEAREST` | 원점과 가장 가까운 대상 |
| `FARTHEST` | 원점과 가장 먼 대상 |
| `FRONTMOST` | 시선 중심과 각도 차가 가장 작은 대상 |
| `FIRST_COLLISION` | 진행 경로에서 먼저 충돌한 대상 |
| `LOWEST_HP` | 현재 HP가 가장 낮은 대상 |
| `LOWEST_HP_RATIO` | 현재 HP 비율이 가장 낮은 대상 |
| `HIGHEST_HP` | 현재 HP가 가장 높은 대상 |
| `HIGHEST_HP_RATIO` | 현재 HP 비율이 가장 높은 대상 |
| `HIGHEST_THREAT` | 시전자에 대한 위협도가 높은 대상 |
| `LOWEST_DEF` | 최종 DEF가 낮은 대상 |
| `HIGHEST_BREAK_VALUE` | 현재 브레이크 피해 효율이 높은 대상 |
| `RANDOM` | 실행 시드 기반 무작위 |
| `CUSTOM_SCORE` | 데이터 수식으로 계산한 점수 |

### 결정론적 동률 처리

일반 정렬의 동률은 다음 순서를 사용한다.

1. 거리
2. 시선 중심과의 각도
3. 대상 UUID의 오름차순

- `RANDOM`은 `execution-id + effect-id`를 시드로 사용한다.
- 같은 입력과 같은 실행 ID에서는 서버 재시작 후에도 같은 순서를 만든다.
- `CUSTOM_SCORE`의 점수가 같아도 위 동률 규칙을 사용한다.

## 13. 거리 감쇠

| 방식 | 처리 |
|---|---|
| `NONE` | 거리와 무관하게 100% |
| `LINEAR` | 시작 거리부터 종료 거리까지 선형 감소 |
| `STEP` | 거리 구간별 고정 배율 |
| `CURVE` | 사전 정의한 곡선 또는 점 목록 보간 |

- 감쇠는 피해뿐 아니라 회복, 보호막, 브레이크, 넉백과 상태이상 지속시간에 독립 적용할 수 있다.
- 각 효과는 `falloff-enabled`를 별도로 가진다.
- 히트박스가 큰 대상은 원점에서 대상 히트박스의 가장 가까운 점까지의 거리를 사용한다.
- 감쇠 적용 후에도 해당 효과의 최종 하드캡과 최소값을 다시 적용한다.

## 14. 연쇄 대상 선정

연쇄 효과는 다음 필드를 필수로 가진다.

```yaml
chain:
  max-chain-targets: 5
  jump-range: 6
  allow-repeat-target: false
  selection: NEAREST
  line-of-sight: true
  falloff:
    type: STEP
    per-jump-multiplier: 0.85
```

- 첫 대상은 일반 대상 선정 규칙을 사용한다.
- 다음 원점은 `PREVIOUS_HIT`이다.
- 이미 선택한 대상은 `allow-repeat-target: true`가 아니면 제외한다.
- 연쇄 중 대상이 사망하면 해당 타격 이후 다음 후보를 다시 찾을 수 있다.
- 연쇄 처리 중에도 효과 블록 최대 대상, 실행당 타격 요청과 모드별 상한을 모두 적용한다.

## 15. 투사체 기본 규격

### 단위

- 속도는 초당 블록 수 `blocks-per-second`를 사용한다.
- 수명은 초 단위 `lifetime-seconds`를 사용한다.
- 서버 틱에서는 실제 경과 시간으로 이동 거리를 계산하되, 한 틱에 과도한 보정 이동이 발생하지 않도록 최대 시뮬레이션 스텝을 둔다.

### 연속 충돌 검사

- 현재 위치만 점검하지 않고 이전 위치에서 새 위치까지의 이동 구간을 스윕 검사한다.
- 빠른 투사체는 한 틱에 여러 블록을 이동해도 중간 대상과 벽을 건너뛰지 않는다.
- 충돌 후보는 경로상의 정규화 거리 `t`가 작은 순서대로 처리한다.
- 같은 `t`로 간주되는 충돌은 지형, 엔티티 UUID 순서로 고정한다.
- 최초 충돌 정책에서는 가장 먼저 충돌한 하나만 적용한다.

## 16. 투사체 이동 방식

스킬별로 다음 이동 방식을 단독 또는 조합해 사용할 수 있다.

- `STRAIGHT`
- `GRAVITY`
- `BALLISTIC`
- `ACCELERATE`
- `DECELERATE`
- `HOMING`
- `SPIRAL`
- `WAVE`
- `ORBIT`
- `BOOMERANG`
- `BOUNCE`
- `RETURN_TO_OWNER`

- 조합 순서는 기본 속도, 가속, 중력, 유도 회전, 특수 궤도, 충돌 순서다.
- 조합이 비결정적이거나 수치 폭주를 일으키면 데이터 검증에서 거부한다.
- 반사된 투사체는 반사 시점의 위치와 속도를 기준으로 새 소유자와 관계 필터를 사용한다.

## 17. 유도 투사체

### HIT 유도 보정

```text
HITBonus = min(100, max(0, HIT - 100))
FinalTurnAngle = BaseTurnAngle × (1 + HITBonus / 100)
```

- `HIT <= 100`에서는 기본 회전각을 사용한다.
- `HIT >= 200`부터 유도 회전각 보너스는 최대 100%로 고정한다.
- HIT는 탐지 범위나 은신 적 탐지에는 사용하지 않는다.
- 유도 대상의 위치는 대상 히트박스 중심 또는 스킬별 조준 지점을 사용한다.

### 대상 상실 정책

| 정책 | 처리 |
|---|---|
| `TERMINATE` | 즉시 종료 |
| `CONTINUE_STRAIGHT` | 마지막 방향으로 비행 |
| `RETARGET_NEAREST` | 가장 가까운 유효 대상 재탐색 |
| `RETARGET_POLICY` | 스킬의 대상 정렬 정책으로 재탐색 |
| `RETURN_TO_OWNER` | 소유자에게 복귀 |

- 대상 상실 정책은 투사체별로 지정한다.
- 재탐색은 매 틱이 아니라 지정한 `retarget-interval`에 수행한다.
- 재탐색 역시 LOS, 관계, 최대 거리와 기술 상한을 적용한다.

## 18. 관통, 반사와 지형 통과

### 개체 관통

- `entity-pierce-count`는 최초 대상 이후 추가로 관통할 수 있는 개체 수다.
- 관통마다 피해, 브레이크와 상태이상 배율을 독립적으로 감쇠할 수 있다.
- 동일 대상 재적중은 `allow-repeat-hit`와 다단 간격을 모두 만족해야 한다.

### 지형 관통

- `terrain-pierce-thickness`는 경로상 충돌 블록의 누적 두께로 차감한다.
- 공기층을 사이에 둔 여러 벽은 각각 누적한다.
- 남은 두께가 0 이하가 되는 최초 블록 내부 또는 표면에서 종료한다.
- `IGNORE_TERRAIN`과 달리 실제 벽 두께에 따라 결과가 달라진다.

### 반사

- 기본 최대 반사 횟수는 `1`이다.
- 반사 시 투사체 소유자는 반사자에게 변경된다.
- 원래 시전자는 이후 관계 판정에서 일반 대상이 될 수 있다.
- 실행 계보 추적을 위해 원본 `execution-id`와 `parent-execution-id`는 유지하고 새 투사체 실행 ID를 만든다.
- 무한 반사 방지를 위해 반사 횟수와 실행당 타격 상한을 함께 검사한다.

## 19. 지속 영역

지속 영역은 `ENTER`, `STAY`, `EXIT` 세 이벤트를 지원한다.

| 이벤트 | 발생 시점 |
|---|---|
| `ENTER` | 대상이 직전 검사에는 없고 현재 범위에 들어옴 |
| `STAY` | 대상이 연속해서 범위 안에 있음 |
| `EXIT` | 대상이 직전 검사에는 있었으나 현재 범위를 벗어남 |

- 장판, 오라와 지속 빔은 검사 시점마다 현재 범위 안의 대상을 다시 찾는다.
- 영역이 이동하거나 회전하면 갱신된 도형을 사용한다.
- 장판이 적용한 별도 DOT 상태이상은 대상을 기억하며, 대상이 장판을 벗어나도 상태이상 자체의 지속시간 동안 유지된다.
- DOT 재적용과 중첩은 `STATUS`의 해당 상태 중첩 정책을 따른다.

## 20. 지속 영역 중복

- 같은 스킬 ID와 같은 소유자가 만든 영역이 겹치면 기본적으로 가장 강한 하나만 적용한다.
- `STACKABLE_AREA` 태그가 있는 스킬만 같은 소유자의 복수 영역 효과를 중첩한다.
- 강도 비교에는 효과 블록의 `overlap-priority`를 우선 사용한다.
- 우선순위가 같으면 주 효과의 계산 전 기준값, 남은 지속시간, 실행 ID 순서로 결정한다.
- 서로 독립된 개체 속성을 가진 다른 영역 또는 다른 스킬 ID는 각자 적용한다.

## 21. 다단 적중 공통 규칙

모든 반복 적중 효과는 다음 필드를 필수로 가진다.

```yaml
multi-hit:
  max-hits-per-target: 4
  hit-interval-ticks: 3
  allow-repeat-hit: true
  cancel-after-target-death: true
  retarget-after-target-death: false
```

- `max-hits-per-target`는 한 실행에서 한 대상에게 허용하는 최대 적중 횟수다.
- `hit-interval-ticks`는 같은 대상의 유효 적중 사이 최소 간격이다.
- 회피, 패링, 면역으로 결과가 0이 되어도 타격 시도 횟수에는 포함한다.
- 대상이 사망하면 예약된 후속 타격은 기본 취소한다.
- `retarget-after-target-death: true`인 스킬만 남은 타격을 새 대상에게 이전한다.

## 22. 치명타와 상태이상 판정 횟수

- 일반 다단 공격은 각 타격마다 치명타를 독립 판정한다.
- 지속 피해형 스킬은 시전 시 한 번 치명타 여부를 판정하고 해당 실행의 모든 지속 틱이 그 결과를 사용한다.
- 별도 DOT 상태이상은 상태이상 정의의 치명타 정책을 따른다.
- `max-status-attempts-per-target`는 필수이며 기본값은 실행당 대상별 `1`회다.
- 다단 공격이 매 타격 상태이상을 시도하려면 명시적으로 횟수를 늘려야 한다.
- 상태이상 시도 실패, 저항과 면역도 시도 횟수에 포함한다.

## 23. 1틱 미만 효과와 집계

### 일반 모드

- 지속 피해와 반복 효과의 최소 실제 처리 간격은 `5틱`이다.
- 더 짧은 논리 간격이 입력되면 데이터 로드 시 거부하거나 일반 모드 전용 값으로 대체해야 한다.

### 카오스 모드

- 최소 실제 실행 간격은 `1틱`이다.
- 논리 간격이 1틱보다 짧으면 1틱 동안 발생할 수치 결과를 합산해 한 번에 실행한다.
- 예: `0.5틱마다 5 피해`는 `1틱마다 10 피해`로 실행한다.
- 공간 검색은 서버 틱당 한 번만 수행한다.
- 치명타, 상태이상, 흡혈, 온힛과 ICD 판정도 틱당 한 번만 수행한다.
- 수치 합산 과정은 BigNumber를 사용한다.

## 24. 모드별 기술 상한

| 항목 | 일반 모드 | 카오스 모드 최대 |
|---|---:|---:|
| 효과 블록당 최종 대상 | 64 | 128 |
| 실행당 전체 타격 요청 | 256 | 512 |
| 투사체 개체 관통 | 32 | 64 |
| 연쇄 대상 수 | 32 | 64 |
| 플레이어당 활성 투사체 | 128 | 256 |
| 플레이어당 활성 지속 영역 | 16 | 32 |

- 카오스는 일반 모드 초기 상한의 최대 2배까지 설정할 수 있다.
- 서버 설정이 표의 최대치를 넘으면 시작 또는 리로드를 거부한다.
- 스킬 데이터가 상한을 넘으면 조용히 자르지 않고 해당 스킬을 로드 실패 처리한다.
- 하나의 잘못된 스킬 때문에 기존 정상 레지스트리를 교체하지 않는다.

## 25. MagicSpells 사용 결론

MagicSpells는 WildSurvival에서 사용한다. 단, 역할은 스킬별로 선택하는 실행 보조 백엔드이며 전투 결과의 최종 권위자는 WildSurvival이다.

### 사용하는 이유

- MagicSpells는 공식 문서 기준 PaperMC 또는 그 포크가 필요하므로 서버 런타임도 해당 계열로 고정한다.
- YAML 기반 스킬 구성과 서버 중 리로드를 지원한다.
- `MultiSpell`, 하위 스펠과 지연 실행을 통해 복합 연출을 빠르게 구성할 수 있다.
- `AreaEffectSpell`은 수평·수직 반경, 원형, 원뿔, 최대 대상과 근접 정렬을 제공한다.
- `BeamSpell`은 즉시 선형 이동, 충돌 반경, 거리와 중력형 궤적을 제공한다.
- `ProjectileSpell`과 `ParticleProjectileSpell`은 투사체 이동, 수명, 중간 히트박스, 중력, 가속과 적중 하위 스펠을 제공한다.
- `HomingMissileSpell`과 `HomingProjectileSpell`은 유도 투사체 기반을 제공한다.
- `LoopSpell`과 `PulserSpell`은 반복 실행과 지속 오브젝트 연출에 활용할 수 있다.
- `can-target`, `obey-los`, 대상 수정자와 하위 스펠 cast argument를 전처리와 문맥 전달에 사용할 수 있다.

### 그대로 맡기지 않는 이유

- 모든 커스텀 도형과 실제 히트박스 교차를 동일한 의미로 지원하지 않는다.
- WildSurvival의 BigNumber 피해, DEF, PEN, 아군 피해 30%, 빈사, 브레이크와 기여도를 알지 못한다.
- 대상 선택 슬롯 소모, 결정론적 동률, 실행 ID 기반 무작위와 중복 방지 규칙이 별도다.
- 카오스 모드의 1틱 미만 수치 집계와 모드별 성능 상한을 직접 보장하지 않는다.
- MS의 바닐라 피해, 포션 효과 또는 자원·쿨타임을 그대로 사용하면 독자 전투 시스템과 이중 적용된다.

## 26. 실행 백엔드

```yaml
execution-backend: AUTO
magicspells:
  spell-id: ws.staff.arc_bolt.projectile
  role: PROJECTILE_DRIVER
  callback-policy: WILDSURVIVAL_ONLY
```

| 백엔드 | 사용 조건 | 역할 |
|---|---|---|
| `AUTO` | 기본값 | 로드 시 기능과 비용을 비교해 아래 방식 선택 |
| `MAGICSPELLS_DRIVER` | MS 기능과 기획 의미가 정확히 일치 | MS가 이동·연출·1차 충돌 후보 생성 |
| `HYBRID` | MS 연출은 유리하지만 판정 차이가 있음 | MS가 연출 또는 이동, WS가 공간·대상 판정 |
| `WILDSURVIVAL_NATIVE` | 복합 도형, 특수 유도, 카오스 집계 등 | WS가 전체 실행, MS는 선택적 연출만 담당 |

### AUTO 선택 기준

1. MS가 필요한 도형, 이동, 수명과 콜백 시점을 정확히 표현할 수 있는지 확인한다.
2. WS 재검증 비용을 포함해도 MS 사용이 더 적은 연산과 개발 복잡도를 가지는지 확인한다.
3. 동일 실행에서 두 엔진이 중복 공간 검색을 해야 하면 `HYBRID`보다 `WILDSURVIVAL_NATIVE`를 우선한다.
4. 단순 파티클 투사체, 단순 유도체와 직선 빔은 `MAGICSPELLS_DRIVER`를 우선 검토한다.
5. 복합 도형, 정밀 근접 히트박스, 특수 연쇄, 카오스 초고속 다단은 `WILDSURVIVAL_NATIVE`를 우선한다.
6. 선택 결과는 로드 로그에 이유와 함께 남기며 스킬별 수동 오버라이드를 허용한다.

## 27. MS 기능별 채택 범위

| MS 기능 | 채택 방식 |
|---|---|
| `AreaEffectSpell` | 단순 원형·원뿔 후보 검색 또는 연출. 최종 필터·정렬·피해는 WS |
| `AreaScanSpell` | 블록 검색용 환경·건설 스킬에 사용. 엔티티 전투 범위 대체 불가 |
| `BeamSpell` | 단순 즉발 선형 빔의 이동·연출·최초 충돌 후보에 사용 |
| `ProjectileSpell` | 실제 엔티티형 투사체가 필요한 경우 사용하되 바닐라 피해 차단 |
| `ParticleProjectileSpell` | 비엔티티 마법 투사체의 우선 구현 후보 |
| `HomingMissileSpell` | 단순 파티클 유도체. HIT 보정과 재탐색이 복잡하면 WS 유도 사용 |
| `HomingProjectileSpell` | 실제 투사체형 유도체. 소유자와 반사 문맥은 WS가 관리 |
| `MultiSpell` | 연출, 소리, 파티클과 비전투 보조 하위 스펠 조합 |
| `LoopSpell` | 반복 연출과 콜백 예약. 실제 다단 상한은 WS가 재검증 |
| `ParticleCloudSpell` | 장판 시각화에만 사용. 바닐라 포션 효과는 전투 효과로 사용하지 않음 |
| `PulserSpell` | 설치물 연출 후보. 실제 영역 수명·대상 재탐색·상한은 WS가 관리 |

## 28. MS 연동 권한 경계

### MagicSpells가 담당할 수 있는 것

- 파티클, 사운드, 표시 엔티티와 시각 효과
- 호환되는 투사체의 이동과 수명
- 호환되는 단순 충돌 후보와 충돌 위치
- 복합 연출의 하위 스펠 순서와 지연
- 연동 콜백 발생

### WildSurvival만 담당하는 것

- AP, 탄약, 자원, 쿨타임, 충전과 ICD
- 실행 ID, 타격 ID와 중복 방지
- 최종 대상 필터와 최대 대상 슬롯
- 커스텀 HP, 피해, 회복, 보호막, DEF, PEN과 치명타
- 회피, 패링, 아군 피해, 강제 이동과 브레이크
- 상태이상 저항, 보스 변환과 빈사·사망
- 기여도, 활동 EXP와 전투 로그
- BigNumber, 카오스 집계와 기술 상한

- MS의 `DamageSpell`, `PainSpell`, `HealSpell`, `DotSpell`, 포션 효과를 핵심 전투 수치에 직접 사용하지 않는다.
- 필요할 경우 해당 스펠은 연출 또는 콜백 전용 helper spell로만 사용한다.

## 29. MS 콜백 문맥

WildSurvival이 MS 실행을 시작하기 전에 `MsExecutionHandle`을 생성한다.

```yaml
ms-execution-handle:
  token: "opaque-execution-token"
  execution-id: "skill-execution-uuid"
  skill-id: "player.staff.arc_bolt.v1"
  effect-id: "projectile_hit"
  owner-uuid: "player-uuid"
  revision: 42
  expires-at-tick: 240300
```

- 토큰은 MS 하위 스펠의 `args`와 `pass-args`를 통해 전달하는 방식을 우선한다.
- 실제 엔티티 투사체에는 토큰 또는 내부 추적 키를 메타데이터/PDC에 함께 기록한다.
- 파티클 투사체는 MS 캐스트 또는 트래커와 토큰을 어댑터 맵에서 연결한다.
- 콜백은 토큰, 대상 UUID 또는 위치, 충돌 유형과 MS 스펠 ID를 전달한다.
- 토큰이 없거나 만료되었거나 소유자가 일치하지 않으면 콜백을 거부한다.
- 같은 토큰과 같은 충돌 시퀀스의 중복 콜백은 한 번만 처리한다.

## 30. MS 스펠 작성 규칙

- WildSurvival 하위 스펠은 `helper-spell: true`를 기본으로 한다.
- 플레이어가 명령어로 직접 실행하지 못하도록 `can-cast-by-command: false`를 사용한다.
- MS 자체 mana, reagent, experience와 cooldown은 사용하지 않는다.
- 대상과 LOS 설정은 후보 수를 줄이는 수준에서 WildSurvival 설정과 동일하게 맞춘다.
- 투사체 `tick-interval`은 가능한 `1`로 두고, 필요한 경우 중간 히트박스를 활성화한다.
- 실제 피해를 일으키는 바닐라 폭발, 화살 피해와 포션 효과는 취소하거나 생성하지 않는다.

```yaml
ws_arc_bolt_projectile:
  spell-class: ".instant.ParticleProjectileSpell"
  helper-spell: true
  can-cast-by-command: false
  projectile-velocity: 18
  projectile-gravity: 0
  tick-interval: 1
  max-duration: 3
  hit-radius: 0.35
  vertical-hit-radius: 0.35
  spell: ws_arc_bolt_callback(mode=direct; pass-args=true)
```

- 위 예시는 MS 설정 방향이며 최종 피해값은 넣지 않는다.
- 실제 옵션명과 공개 API 호출 방식은 채택한 MS 버전을 고정한 뒤 `TECH-001`에서 컴파일 검증한다.

## 31. MS 리로드와 장애 대응

- 공식 `/ms reload`와 WildSurvival 스킬 핫 리로드를 연동한다.
- `ws skill reload`는 먼저 WildSurvival 데이터를 검증하고 참조하는 MS 스펠 ID 목록을 만든다.
- MS 리로드 후 모든 스펠 ID와 콜백 스펠을 다시 조회해 존재 여부와 클래스 호환성을 검사한다.
- 검증이 끝난 뒤에만 새 WildSurvival 스킬 레지스트리를 원자적으로 교체한다.
- MS 리로드 또는 참조 검증이 실패하면 기존 WildSurvival 레지스트리를 유지한다.
- 진행 중인 실행은 시작 당시의 스킬 리비전과 백엔드 핸들을 유지한다.
- MS가 비활성화되면 `WILDSURVIVAL_NATIVE` 대체 구현이 명시된 스킬만 계속 사용할 수 있다.
- 대체 구현이 없는 스킬은 비활성화하고 관리자 로그에 명확한 원인을 남긴다.

## 32. 데이터 예시

```yaml
id: player.staff.arc_bolt.v1
execution-backend: AUTO
targeting:
  origin: WEAPON_MUZZLE
  rotation: SNAPSHOT
  include: [ENEMY, MONSTER, BOSS]
  exclude: [DEAD, SPECTATOR]
  max-targets: 1
  selection: FIRST_COLLISION
  line-of-sight:
    policy: ANY_SAMPLE
projectile:
  movement: [STRAIGHT, HOMING]
  speed-blocks-per-second: 18
  lifetime-seconds: 3
  radius: 0.35
  target-loss-policy: CONTINUE_STRAIGHT
  entity-pierce-count: 0
  terrain-policy: BLOCK
multi-hit:
  max-hits-per-target: 1
  hit-interval-ticks: 1
  max-status-attempts-per-target: 1
magicspells:
  spell-id: ws_arc_bolt_projectile
  role: PROJECTILE_DRIVER
  callback-policy: WILDSURVIVAL_ONLY
```

## 33. 예외 처리

| 상황 | 처리 |
|---|---|
| 대상이 선택 후 텔레포트 | 적용 시점에 월드·거리·필수 조건 재검증 |
| 대상이 선택 후 사망 | 후속 타격 취소, 허용 시 재대상 |
| 시전자가 접속 종료 | 스킬별 소유자 이탈 정책 적용 |
| 투사체가 미로드 청크 진입 | 청크를 로드하지 않고 종료 |
| MS 콜백 중복 | `hit-id`로 두 번째 요청 거부 |
| MS가 바닐라 피해 발생 | 피해 이벤트 취소, 커스텀 콜백만 인정 |
| MS 스펠 ID 누락 | 해당 스킬 로드 실패, 기존 레지스트리 유지 |
| 최대 대상 또는 타격 상한 초과 | 런타임 절삭 대신 데이터 로드 실패 |
| 영역이 겹침 | 같은 스킬·소유자는 가장 강한 하나, 태그 시 중첩 |
| 1틱 미만 효과 | 카오스에서 수치 합산, 일반 모드에서는 거부 |
| 반사 후 원래 소유자 적중 | 새 소유자 기준 관계 필터로 정상 처리 |
| 같은 틱에 벽과 대상 충돌 | 경로 거리 우선, 완전 동률은 지형 우선 |

## 34. 테스트 체크리스트

| 테스트 | 확인 목적 |
|---|---|
| 9종 도형 경계와 실제 히트박스 | 중심점 오판정과 경계 포함 검증 |
| 3D·수평 전용 원뿔 | 높이 차와 각도 검증 |
| 복합 도형 UNION·INTERSECTION·SUBTRACT | 조합 결과 검증 |
| LOS 중심·상단·하단 | 부분 엄폐 통과 규칙 검증 |
| 복합 대상 필터 | 관계·생존·태그 조합 검증 |
| 최대 대상과 면역 대상 | 선택 슬롯 미복구 검증 |
| 동률 정렬 | 거리·각도·UUID 결정론 검증 |
| RANDOM 재현 | 실행 ID 시드 재현성 검증 |
| 고속 투사체 | 스윕 충돌과 터널링 방지 검증 |
| 유도 HIT 100·150·200 | 회전각 공식 검증 |
| 관통·반사·지형 두께 | 소유권과 누적 두께 검증 |
| 장판 ENTER·STAY·EXIT | 현재 대상 재탐색 검증 |
| 장판 이탈 후 DOT | 기존 대상 상태 유지 검증 |
| 같은 소유자 영역 중복 | 최강 하나와 태그 중첩 검증 |
| 다단 대상 사망 | 후속 취소와 재대상 검증 |
| 다단 치명타·상태이상 | 타격별 치명타와 시도 상한 검증 |
| 카오스 0.5틱 효과 | 수치 합산·검색 1회·부가 판정 1회 검증 |
| 모드별 상한 초과 | 스킬 로드 거부 검증 |
| MS Projectile·ParticleProjectile | 이동·콜백·바닐라 피해 차단 검증 |
| MS AreaEffect·Beam | 후보 검색 후 WS 재검증 |
| MS 중복·만료 토큰 | 콜백 위조와 중복 방지 검증 |
| MS 리로드 실패 | 기존 레지스트리 유지 검증 |

## 35. SKILL-002 완료 기준

- 9종 기본 도형과 복합 도형 규칙이 확정되었다.
- 원점, 회전, 실제 히트박스, LOS와 장애물 정책이 확정되었다.
- 관계·개체·생존·태그 필터를 조합할 수 있다.
- 최대 대상은 효과 블록별로 처리하고 무효 결과가 슬롯을 돌려주지 않는다.
- 대상 정렬과 무작위 선정이 결정론적으로 재현된다.
- 연쇄, 거리 감쇠, 관통, 반사와 유도 규칙이 확정되었다.
- 지속 영역은 현재 대상을 재탐색하고 별도 DOT는 기존 대상을 유지한다.
- 다단 적중, 치명타, 상태이상 시도와 사망 후 처리 규칙이 확정되었다.
- 일반 모드와 카오스의 처리 간격 및 기술 상한이 확정되었다.
- MagicSpells를 사용하되 WildSurvival이 최종 전투 권위를 유지한다.
- 스킬별로 MS, 하이브리드, 네이티브 실행 방식을 효율에 따라 선택할 수 있다.
- MS 리로드 실패와 중복 콜백이 기존 전투 상태를 손상시키지 않는다.

## 36. MagicSpells 공식 문서 참조

- Wiki 홈 및 설정 시작: <https://github.com/TheComputerGeek2/MagicSpells/wiki>
- 전체 스펠 목록: <https://github.com/TheComputerGeek2/MagicSpells/wiki/Spell-List>
- 공통 스펠 설정과 타게팅: <https://github.com/TheComputerGeek2/MagicSpells/wiki/Spell-Configuration>
- 하위 스펠 체인: <https://github.com/TheComputerGeek2/MagicSpells/wiki/Spell-chaining>
- 하위 스펠 인자와 `args`, `pass-args`: <https://github.com/TheComputerGeek2/MagicSpells/wiki/Cast-arguments>
- 범위 스펠: <https://github.com/TheComputerGeek2/MagicSpells/wiki/AreaEffectSpell>
- 빔: <https://github.com/TheComputerGeek2/MagicSpells/wiki/BeamSpell>
- 일반 투사체: <https://github.com/TheComputerGeek2/MagicSpells/wiki/ProjectileSpell>
- 파티클 투사체: <https://github.com/TheComputerGeek2/MagicSpells/wiki/ParticleProjectileSpell>
- 유도 투사체: <https://github.com/TheComputerGeek2/MagicSpells/wiki/HomingMissileSpell>
- 반복 실행: <https://github.com/TheComputerGeek2/MagicSpells/wiki/LoopSpell>
- 복합 실행: <https://github.com/TheComputerGeek2/MagicSpells/wiki/MultiSpell>

---

# 다음 기획 작업

다음 단계는 `CORR-003 정화탑 및 정화 자원`이다.
