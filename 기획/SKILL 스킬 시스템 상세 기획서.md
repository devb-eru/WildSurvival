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

# 다음 기획 작업

다음 단계는 `SKILL-002 스킬 범위·대상 선정·최대 개체·다단 판정`이다.
