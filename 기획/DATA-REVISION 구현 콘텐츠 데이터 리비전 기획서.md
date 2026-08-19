# WildSurvival 구현 콘텐츠 데이터 리비전 기획서

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `DATA-REVISION-001` |
| 상위 기준 | `TECH-001`, `CONTENT-MASTER-001`, `EVENT-DATA-001`, `BALANCE-D20-001` |
| 리비전 | `ws-content-r1` |
| 위치 | `plugins/wsplugin/src/main/resources/content/ws-content-r1/` |
| 활성 정책 | 새 회차만 `NEW_RUN_ONLY`, 활성 회차 핫스왑 금지 |
| Story | `EMPTY`, 시스템·보상·완료 조건과 분리 |
| 최종 수정일 | 2026-08-20 |

## 1. 목적

- 기획 문서의 Day 1~20 수치를 서버가 읽을 수 있는 정적 데이터로 고정한다.
- JSON 문법, 스키마, ID·참조, 합계와 정규화된 UTF-8 LF 바이트의 SHA-256이 모두 통과한 리비전만 후보 레지스트리로 만든다.
- 활성 회차가 시작 때 고정한 콘텐츠 의미를 서버 리로드로 바꾸지 않는다.
- Story가 없어도 Day, 전투, 보상, 보스와 회차 저장이 독립적으로 동작하게 한다.

이 리비전은 구현 코드 자체가 아니라 로더 입력과 검증 계약이다. 현재 플러그인 골격에는 실제 로더·명령 실행기가 없으므로 `plugin.yml`에 동작하지 않는 관리자 명령을 미리 등록하지 않는다.

## 2. 파일 구조

```text
content/ws-content-r1/
├─ content-lock.yaml
├─ manifest.json
├─ schemas/
│  ├─ domain-bundle.schema.json
│  └─ manifest.schema.json
├─ days/day01-20.json
├─ encounters/day01-10.json
├─ resources/day11-20.json
├─ equipment/day11-20.json
├─ enemies/
│  ├─ day01-10.json
│  └─ day11-20.json
├─ bosses/
│  ├─ day10-resonant-pursuer.json
│  └─ day20-neural-amalgam.json
└─ ops/admin-commands.json
```

## 3. 파일별 책임

| 파일 | 주요 책임 | 핵심 불변식 |
|---|---|---|
| `content-lock.yaml` | 진입점, 리비전·활성 정책 | `ws-content-r1`, `NEW_RUN_ONLY`, Story `EMPTY` |
| `manifest.json` | 로드 대상·도메인·SHA-256 | 파일 누락·변조 즉시 거부 |
| `days/day01-20.json` | EXP, 종료 레벨, 증강 마일스톤, 원천 자원 예산 | 2,820·13,900, 원천 합계 일치 |
| `encounters/day01-10.json` | 적 약어, 27개 실제 웨이브, 인원 예산 | 각 모드 소비 비용 = Day 예산 |
| `resources/day11-20.json` | 자원 7종, 가공·소모품·호출 레시피, 노드 | 100% 제작, 호출 트랜잭션 |
| `equipment/day11-20.json` | 10개 무기군, EPIC·LEGENDARY 후보와 슬롯 | 슬롯 0·-106, 권투 무아이템, +5 상한 |
| `enemies/*.json` | Day 1~20 적 수치·패턴·상태·소환체 | 역할 비용, 전조, 소환체 무보상 |
| `bosses/*.json` | Day 10·20 전장·페이즈·패턴·보상·복구 | HP·브레이크·보상 멱등성 |
| `ops/admin-commands.json` | 명령 경로·권한·인자·변경 안전장치 | 변경 사유·dry-run·토큰·감사 |

## 4. 공통 번들 계약

모든 도메인 JSON은 다음 필드를 가진다.

```json
{
  "schemaVersion": 1,
  "contentRevision": "ws-content-r1",
  "domain": "enemies",
  "dataRevision": "enemy-d11-d20-r1",
  "metadata": {},
  "entries": []
}
```

| 필드 | 규칙 |
|---|---|
| `schemaVersion` | 현재 정확히 1, 모르는 상위 버전 거부 |
| `contentRevision` | manifest와 정확히 일치 |
| `domain` | 허용 도메인 열거값만 사용 |
| `dataRevision` | 도메인 독립 리비전, 런타임 저장에 함께 기록 |
| `metadata` | 배율·상한처럼 번들 전체에 적용하는 값 |
| `entries` | ID가 있는 불변 템플릿 목록 |

- 알 수 없는 최상위 필드는 거부한다.
- 숫자는 의미 단위를 필드명 또는 상위 metadata로 고정한다. 시간은 원칙적으로 서버 틱을 사용한다.
- 확률은 `0.0~1.0`, 비율 배율은 양수, 개수·Day·틱은 0 이상의 정수다.
- 표시명은 로직 키로 사용하지 않는다.

## 5. 로드·검증 순서

1. `content-lock.yaml`을 읽고 스키마·리비전·manifest 경로를 확인한다.
2. `manifest.json`을 파싱하고 manifest 스키마를 검사한다.
3. 파일을 UTF-8·LF로 정규화하고 manifest의 각 SHA-256을 비교한다. 저장소의 `.gitattributes`도 콘텐츠 JSON·YAML을 LF로 고정한다.
4. 도메인 JSON을 파싱하고 공통 번들 스키마를 검사한다.
5. 모든 최상위 ID를 수집해 도메인 내 중복을 거부한다.
6. Day→사건·보스, 사건→적, 보스→적·장비·자원 참조를 확인한다.
7. Material·EntityType·상태·변이·태그를 런타임 등록표와 대조한다.
8. EXP, 원천별 자원, 웨이브 역할 비용과 인원 예산 합계를 계산한다.
9. 희귀도·iLv·강화, 활성 적·전조·소환체와 패턴 수치 상한을 검사한다.
10. 모두 통과하면 새 불변 `ContentRegistry` 후보를 만든다.
11. 후보는 새 회차 기본값으로만 교체하고 활성 회차는 기존 리비전을 유지한다.

하나라도 실패하면 현재 정상 레지스트리를 유지하고 실패 파일·JSON 경로·기대값·실제값을 운영 로그와 validate 결과에 남긴다.

## 6. 리비전 교차검증 값

| 검사 | 기대값 |
|---|---:|
| Day 항목 | 20 |
| Day 1~10 진행/활동 EXP | 1,974 / 846 |
| Day 1~10 EXP 합계 | 2,820 |
| Day 11~20 진행/활동 EXP | 9,730 / 4,170 |
| Day 11~20 EXP 합계 | 13,900 |
| Day 1~9 압박 모드 | 27 |
| 웨이브 예산 불일치 허용 | 0 |
| Day 11~20 신규 자원 | 7 |
| Day 11~20 자원 기대 합계 | 272/257/306/142/301 |
| Day 11~20 장비 템플릿 | 24 |
| Day 11~20 적·소환체 템플릿 | 21 |
| 보스 템플릿 | Day 10·20 각 1 |
| 관리자 명령 계약 | 22 |

## 7. 참조 경계

### 리비전 안에서 반드시 해결

- `DAY-01~09.encounterRef` → `ENC-D01~09`
- `DAY-10/20.bossRef` → 해당 보스 템플릿
- 사건 약어 `templateId` → Day 1~10 적 템플릿
- Day 20 보스 소환 풀 → Day 20 접합편 4종
- Day 20 전설 후보 → 장비 전설 후보 6종
- Day 20 호출 `transactionalSpawnRef` → Day 20 보스

### 시스템 등록표에서 해결

- C01~C13 발견 ID
- 시설 템플릿 ID
- Day 1~10 기본 자원·가공품 ID
- 상태·변이·증강·태그 ID
- Bukkit/Paper Material과 EntityType

시스템 등록표 참조도 실제 로더에서 누락을 허용하지 않는다. 이 문서의 “외부”는 검증 생략이 아니라 다른 권위 레지스트리에서 해결한다는 뜻이다.

## 8. 활성 회차·마이그레이션

- `runs.content_revision`은 회차 생성 때 `ws-content-r1`로 고정한다.
- `/ws admin content reload`는 새 회차 기본 포인터만 바꾼다.
- 저장된 활성 회차 리비전 파일을 삭제하지 않는다.
- 기존 회차를 새 리비전으로 옮길 때는 `migrate plan`이 ID 삭제, 수치 의미 변경, 상태·장비·보상 변환을 모두 열거해야 한다.
- `migrate apply` 전 자동 백업, 활성 전투 없음, 계획 해시 일치가 필요하다.
- 수치만 바뀌어도 의미가 달라지면 새 `contentRevision`을 발급한다.
- 표시명·설명 오탈자만 고칠 때도 manifest 해시는 갱신하지만 활성 회차 로직 필드는 바꾸지 않는다.

## 9. 구현 순서

1. lock·manifest·JSON 파서와 SHA-256 검사
2. 공통 번들 스키마 검사와 오류 경로 출력
3. 도메인별 DTO·ID 레지스트리
4. 참조·범위·합계 검증기
5. 불변 ContentRegistry와 새 회차 고정
6. `content validate`와 `content reload` 조회·교체 서비스
7. 감사·복구 명령 실행기
8. 서버 시작·종료·실패·활성 회차 리비전 통합 테스트

## 10. 검증 항목

- JSON 12개(manifest 포함) 문법과 YAML lock 필수 키
- manifest 파일 수·경로·SHA-256 완전 일치
- 도메인 번들의 공통 필드와 contentRevision 일치
- Day 1~20 EXP와 원천 자원 합계
- Day 1~9 세 모드 웨이브 비용
- 적·보스·장비·호출의 내부 참조
- 중복 ID와 미등록 Material·EntityType·상태·변이
- 활성 회차 리비전 불변과 새 회차 리로드
- Story `EMPTY` 상태에서 Day 1~20 로드·저장·정산

## 11. 완료 기준

- 기획 수치가 파싱 가능한 YAML/JSON 리비전으로 존재한다.
- manifest 해시와 스키마·참조·합계 검증 순서가 정의된다.
- 활성 회차를 핫 리로드로 변형하지 않는다.
- 구현자가 추가 설계 판단 없이 DTO, 검증기와 레지스트리 로더를 작성할 수 있다.
- Story 데이터가 없어도 시스템 리비전이 완전한 상태로 로드된다.
