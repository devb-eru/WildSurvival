# WildSurvival 명령·권한·관리자 복구 표

## 문서 정보

| 항목 | 내용 |
|---|---|
| 문서 ID | `OPS-001` |
| 상태 | `IMPLEMENTATION_CONTRACT` |
| 상위 기준 | `TECH-001`, `GAME`, `DAY-001`, `BUDGET-001`, `REWARD-001` |
| 구현 데이터 | `content/ws-content-r1/ops/admin-commands.json` |
| 기본 명령 | `/wildsurvival`, 별칭 `/ws` |
| 적용 범위 | 검사, 콘텐츠 검증, 스냅샷, 트랜잭션·아이템·사건·보상·회차 복구, 감사 |
| 금지 | 정상 손실 환불, 임의 아이템·레벨 지급, 최종 조건 우회, 활성 회차 리비전 교체 |
| 최종 수정일 | 2026-08-21 |

## 1. 운영 원칙

- 조회 명령과 변경 명령의 권한을 분리한다.
- 변경 명령은 `--reason`과 먼저 실행한 `--dry-run`의 1회용 확인 토큰을 요구한다.
- 스냅샷 생성은 상태를 바꾸지 않으므로 사유만 요구하고 확인 토큰은 요구하지 않는다.
- 모든 변경은 실행자, 시각, 대상 회차, 이전·이후 요약, 사유, 트랜잭션 ID, 콘텐츠 리비전을 `admin_audit`에 남긴다.
- 콘솔은 권한 노드 검사를 생략하지 않는다. 콘솔 실행자는 `CONSOLE`로 기록한다.
- 플레이어 실수, 정상 전투 패배, 정상 소모품 사용, 내구 손실과 완전 사망은 복구하지 않는다.
- 일반 운영자는 원장 복구 명령을 사용하며 직접 레벨·아이템·Day 값을 쓰는 범용 `set`·`give` 명령을 제공하지 않는다.

## 2. 역할과 권한

| 역할 | 권한 노드 | 기본 | 허용 |
|---|---|---|---|
| 감사 조회자 | `wildsurvival.admin.inspect` | OP | 회차·플레이어·아이템·시설·Day·사건·보상·증강 잠금·Story 조회 |
| 콘텐츠 검증자 | `wildsurvival.admin.validate` | OP | 스키마·참조·수치·해시 검증 |
| 감사 담당자 | `wildsurvival.admin.audit` | OP | 마스킹된 감사 로그 조회 |
| 스냅샷 담당자 | `wildsurvival.admin.snapshot` | OP | 수동 복구 스냅샷 생성 |
| 트랜잭션 복구자 | `wildsurvival.admin.recover.transaction` | false | PREPARED 작업 자동·확정·취소 복구 |
| 아이템 복구자 | `wildsurvival.admin.recover.item` | false | 중복 격리·원장 일치 사본 복원 |
| 사건 복구자 | `wildsurvival.admin.recover.encounter` | false | 사건·보스 재개, 안전 취소, 호출 롤백 |
| 보상 복구자 | `wildsurvival.admin.recover.reward` | false | 커밋된 결과의 미지급 단계만 재개 |
| 회차 복구자 | `wildsurvival.admin.recover.run` | false | 손상 회차를 승인된 스냅샷으로 복원 |
| 콘텐츠 리로드 | `wildsurvival.admin.content.reload` | false | 검증 완료 리비전을 새 회차 기본값으로 교체 |
| 마이그레이션 | `wildsurvival.admin.migrate` | false | 계획 생성·백업 후 데이터 리비전 이전 |
| Day 긴급 복구 | `wildsurvival.admin.root.day` | false | 복구 상태에서만 Day 전환 원장 복원 |

- `default: false` 권한은 OP 여부만으로 자동 허용하지 않고 권한 플러그인 또는 콘솔 정책으로 명시 부여한다.
- `wildsurvival.admin.*` 같은 포괄 권한은 운영 서버에서 권장하지 않는다.
- `root.day`는 최종 목표 최소 Day, 보스 보상, 발견·시설 요구를 승인하지 못한다.

## 3. 조회·검증 명령

| 명령 | 권한 | 주요 출력 | 상태 변경 |
|---|---|---|---|
| `/ws admin content validate [revision]` | `.validate` | JSON 문법, 스키마, 해시, ID·참조, Material·EntityType, 수치 상한 | 없음 |
| `/ws admin inspect run [runId]` | `.inspect` | 회차 상태, Day, 멤버, 난이도, 고정 콘텐츠·예산 정책·프로필 리비전 | 없음 |
| `/ws admin inspect player <player> [runId]` | `.inspect` | 생명, EXP, 장비, 증강, 발견, 미완료 TX | 없음 |
| `/ws admin inspect item <itemInstanceId>` | `.inspect` | PDC, 서명, 소유권, 원장·인벤토리 위치 | 없음 |
| `/ws admin inspect facility <facilityInstanceId>` | `.inspect` | 시설망, 작업, 블록 스냅샷, 오염 | 없음 |
| `/ws admin inspect day [runId]` | `.inspect` | Day 전환, 스킵 표, 시대, 최종 게이트 | 없음 |
| `/ws admin inspect encounter <encounterId>` | `.inspect` | 패턴·소환·스냅샷·보상·TX 상태 | 없음 |
| `/ws admin inspect reward <rewardId>` | `.inspect` | 공용·개인·고유 지급 단계와 나머지 | 없음 |
| `/ws admin inspect augment-lock <runId> <level>` | `.inspect` | 등급, 최초 달성자, 시드, 저장 순번 | 없음 |
| `/ws admin inspect story [runId]` | `.inspect` | `EMPTY` 또는 활성 Story 리비전과 상태 | 없음 |
| `/ws admin snapshot list <runId>` | `.inspect` | 헤더, 저장 순번, 생성 이유, 해시 | 없음 |
| `/ws admin audit query [filters]` | `.audit` | 실행자·대상·사유·결과, 비밀·서명 원문 제외 | 없음 |

## 4. 변경 명령 공통 흐름

```text
조회·원인 확인
→ 변경 명령 --dry-run --reason "티켓/원인"
→ 변경 전·후 예상값과 위험 표시
→ 60초 유효 확인 토큰 발급
→ 같은 실행자·대상·모드로 --confirm <token>
→ 트랜잭션 커밋
→ 전체 불변식 재검사
→ 감사 로그와 결과 ID 출력
```

- 확인 토큰은 실행자 UUID/CONSOLE, 명령 ID, 대상 ID, 모드, 예상 저장 순번을 서명한다.
- 저장 순번이 바뀌거나 60초가 지나면 토큰은 무효다.
- 변경 중 새 전투·GUI·Day 전환을 잠그고 성공·실패 뒤 잠금을 해제한다.
- 사유는 10~200자이며 공백·`test`·`fix` 같은 무의미한 값만 있으면 거부한다.

## 5. 변경 명령

| 명령 | 권한 | 선행 조건 | 결과 |
|---|---|---|---|
| `/ws admin content reload <revision>` | `.content.reload` | 전체 validate 성공 | 새 회차 기본 레지스트리만 교체 |
| `/ws admin snapshot create <runId>` | `.snapshot` | 회차 존재 | DB·핵심 월드 상태 해시 스냅샷 |
| `/ws admin recover transaction <txId> <AUTO\|COMMIT\|CANCEL>` | `.recover.transaction` | PREPARED 또는 불일치 | 실제 상태 비교 후 한 번만 확정·취소 |
| `/ws admin recover item <itemId> <QUARANTINE\|RESTORE_LEDGER_COPY>` | `.recover.item` | 서명·원장 감사 | 중복 전부 잠금 후 권위 사본 하나 복원 |
| `/ws admin recover encounter <id> <RESUME\|SAFE_CANCEL\|ROLLBACK_CALL>` | `.recover.encounter` | 보상 커밋 여부 검사 | 저장 패턴 재개, 무보상 취소 또는 호출 전 롤백 |
| `/ws admin recover reward <rewardId> RESUME_MISSING_STAGES` | `.recover.reward` | 원천 결과 COMMITTED | 이미 지급한 단계 제외, 미지급만 재개 |
| `/ws admin recover run <runId> <snapshotId>` | `.recover.run` | `CORRUPTED`/`ADMIN_RECOVERY_REQUIRED` | 승인 스냅샷 복원과 전체 감사 |
| `/ws admin override day <runId> <targetDay>` | `.root.day` | 복구 상태, Day 전환 원장 증거 | 누락된 전환 상태만 복원 |
| `/ws admin migrate plan <from> <to>` | `.migrate` | 두 리비전 존재 | 호환성·변환·백업 계획, 변경 없음 |
| `/ws admin migrate apply <planId>` | `.migrate` | 전투 없음, 백업 존재 | 계획 해시와 일치하는 변환만 적용 |

## 6. 관리자 복구 결정표

| 증상 | 권위 검사 | 허용 복구 | 금지 |
|---|---|---|---|
| `PREPARED` 제작·장착 TX 잔존 | DB 기대 리비전, 자원 원장, PDC·슬롯 | 실제 결과가 있으면 COMMIT, 없으면 CANCEL | 재료와 완제품 동시 지급 |
| 같은 장비 ID 2개 | 아이템 서명, 원장 위치, 생성 TX | 둘 다 격리 후 원장 일치 1개만 복원 | 관리자가 외형만 보고 하나 선택 |
| 호출 소비 후 보스 미생성 | 호출 TX, ArenaManifest, 보스 인스턴스 | 보상 미커밋이면 `ROLLBACK_CALL` | 호출품+보스 동시 유지 |
| 보스 스냅샷은 있으나 개체 없음 | 저장 순번, 전장, 플레이어 원자 스냅샷 | `RESUME`, 3초 동기화 | HP 초기화 재소환 |
| 중복 보스 | encounter ID와 저장 순번 | 가장 높은 순번 유지, 나머지 무보상 제거 | 둘 중 하나의 보상 지급 |
| 보스 처치 후 정산 일부 누락 | 보스 사망 COMMIT, reward_grants 단계 | `RESUME_MISSING_STAGES` | 전체 정산 재실행 |
| 증강 선택지는 있으나 등급 잠금 없음 | 후보 저장 순번, 시드, 최초 달성 이벤트 | 후보가 참조한 등급을 잠금으로 복원 | 새 확률 추첨 |
| 등급 잠금은 있으나 후보 없음 | 잠금 등급·개인 드로우 시드 | 같은 등급에서 후보 재생성 | 등급 변경 |
| 시설 원장과 블록 불일치 | 시설 TX, 소유 PDC, 블록 스냅샷 | 원장 기준 재구성 또는 안전 철거 | 플레이어 건축 덮어쓰기 |
| Day 전환 원장 일부 누락 | 이전·다음 Day 스냅샷, 보상·스킵 TX | 증거가 일치하는 전환 단계만 복원 | 임의 Day 점프 |
| 최종 목표 상태 손상 | Day·보스·발견·시설·부품 독립 원장 | 조건 재평가 후 상태 재생성 | 완료 플래그 직접 true |
| 콘텐츠 리비전 파일 누락 | 활성 회차 고정 리비전·백업 해시 | 동일 해시 리비전 복원 | 최신 리비전으로 강제 치환 |
| Story `EMPTY` | manifest와 story_runs | 정상 상태로 유지 | 오류로 간주하거나 임의 Story 생성 |
| SQLite 스키마 불일치 | schema_version, migration plan | 백업 후 승인 계획 적용 | 즉석 SQL 수정 |

## 7. 안전 취소와 롤백 경계

- `SAFE_CANCEL`은 보상 미커밋 사건만 종료하고 이미 소비된 정상 전투 소모품·내구·사망 결과를 환불하지 않는다.
- `ROLLBACK_CALL`은 호출 소비와 보스 생성 사이의 서버 오류만 호출 전 상태로 되돌린다.
- 보스 처치, 완전 사망, 고유 장비 수령과 파티 증강 확정은 일반 체크포인트 롤백으로 되돌리지 않는다.
- 회차 스냅샷 복원은 손상된 도메인과 의존 도메인을 함께 복원한다. 플레이어만 또는 보스만 과거로 돌리지 않는다.
- 자동 복구가 불변식을 만족하지 못하면 상태를 유지한 채 `ADMIN_RECOVERY_REQUIRED`로 동결한다.

## 8. 콘텐츠 검증 규칙

| 단계 | 오류 예시 | 결과 |
|---:|---|---|
| 1 | JSON/YAML 문법 오류 | 리비전 거부 |
| 2 | schemaVersion·contentRevision 불일치 | 리비전 거부 |
| 3 | manifest SHA-256 불일치 | 리비전 거부 |
| 4 | ID 중복·참조 누락·순환 | 리비전 거부 |
| 5 | Day·희귀도·강화·적 예산 상한 위반 | 리비전 거부 |
| 6 | Material·EntityType·상태·태그 미등록 | 리비전 거부 |
| 7 | EXP·자원·웨이브 합계 불일치 | 리비전 거부 |
| 8 | 예산 배율 범위·잠금 합계·등록 도메인·CHAOS 10.00 상한 검사 | 리비전 거부 |
| 9 | 전체 성공 | 새 불변 레지스트리 후보 생성 |

- 활성 회차는 시작 시 고정한 `contentRevision`을 계속 사용한다.
- 리로드는 새 회차 기본값만 바꾸며 활성 회차 수치를 핫스왑하지 않는다.
- 로더가 하나라도 실패하면 현재 정상 레지스트리를 유지한다.

## 9. 완료 기준

- 모든 조회·변경 명령에 정확한 권한·인자·선행 조건·출력이 있다.
- 변경 명령은 사유, dry-run, 확인 토큰, 감사 로그와 트랜잭션을 사용한다.
- 정상 플레이 손실과 서버·데이터 오류 복구의 경계가 구분된다.
- 보스·보상·증강 등급·장비 중복·Day·최종 목표 손상의 복구 경로가 있다.
- 관리자 명령으로 Day 50 최소 잠금과 최종 완료 요구를 우회할 수 없다.
