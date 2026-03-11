> 작성일: 2026-03-11

# 07. EQP/Model CRUD 및 Branch 관리 설계

## 1. 개요

### 1.1 배경

현재 UI 관리 페이지는 다음 한계를 갖고 있습니다.

- EQP page는 조회/파라미터 편집 중심이며 설비 생성/수정/삭제 관리 기능이 없습니다.
- Model page는 버전 단위 CRUD와 `EDIT` 중심 편집은 가능하지만 parent/branch 기반 관리 개념이 없습니다.
- EQP와 Model 사이 연결 규칙(`개발 장비는 DEVELOP 모델만`, `운영 장비는 OPERATE 모델만`)이 UI/API 계약에 반영되어 있지 않습니다.
- Sidebar 우클릭 컨텍스트 메뉴, create/update modal, branch commit diff UI가 없습니다.

### 1.2 목표

본 문서는 EQP page와 Model page에 다음 관리 기능을 추가하기 위한 설계 기준을 정의합니다.

- EQP 생성/수정/삭제
- EQP-Model 연결 변경
- EQP Parameter 버전 변경
- Root Model 생성/수정/삭제
- Branch Model 생성/정리/삭제
- Branch -> Parent commit 및 diff 확인

### 1.3 참조 문서

- 기준 아키텍처: `docs/design/01-system-architecture.md`
- 기존 UI 관리 페이지 설계: `docs/design/02-ui-management-pages-design.md`
- 기존 EQP UX 개선 설계: `docs/design/06-eqp-page-ux-improvement-design.md`
- 구현 계획: `docs/tasks/07-eqp-model-crud-and-branch-management-build-plan.md`

---

## 2. 범위와 전제

### 2.1 범위

- `nori-tc` UI-backend, DB schema/domain/API 설계
- `nori-tc-ui` EQP/Model page 프론트 설계
- EQP 저장 후 Gateway/Business runtime 동기화 설계
- Model parent/branch/version 관리 설계

### 2.2 비범위

- 본 문서는 설계 문서이며 실제 코드 구현/테스트 결과는 포함하지 않습니다.
- SQL migration 도구 도입, 배포 절차, 운영 데이터 이관 스크립트 상세는 본 문서 범위 밖입니다.

### 2.3 확정 전제

- 기존 Model 상세 편집의 `Check Out / Check In` 흐름은 유지합니다.
- `ProtocolType` 저장값과 코드 enum은 `HSMS/SOCKET`에서 `SECS/SOCKET`으로 전환합니다.
- `ModelStatus` 저장값과 코드 enum은 `DRAFT/ACTIVE/DEPRECATED`에서 `DEVELOP/OPERATE/DEPRECATED`로 전환합니다.
- `tc_eqp.is_dev`는 EQP 생성과 수정 양쪽에서 사용자가 변경할 수 있습니다.
- `Parent Model Commit`은 추가/변경뿐 아니라 삭제 diff도 부모 최신 버전에 반영합니다.

---

## 3. 데이터 및 공개 계약 변경

### 3.1 DB 스키마 변경

#### 3.1.1 `tc_model`

- `maker` 오른쪽에 `parent_model VARCHAR(128) NULL` 추가
- 의미:
  - root model: `parent_model IS NULL`
  - branch model: `parent_model = 부모 tc_model.model_name`
- 제약:
  - `FOREIGN KEY (parent_model) REFERENCES tc_model(model_name) ON DELETE CASCADE`

#### 3.1.2 `tc_eqp`

- `comm_mode` 오른쪽에 `is_dev BOOLEAN NOT NULL DEFAULT FALSE` 추가
- 의미:
  - `TRUE`: 개발 장비
  - `FALSE`: 운영 장비

### 3.2 enum / 타입 변경

| 항목 | 현재 | 변경 후 |
|---|---|---|
| `ProtocolType` | `HSMS`, `SOCKET` | `SECS`, `SOCKET` |
| `ModelStatus` | `DRAFT`, `ACTIVE`, `DEPRECATED` | `DEVELOP`, `OPERATE`, `DEPRECATED` |

연관 변경 대상:

- DB check constraint
- DB domain enum
- JPA entity / MyBatis mapper
- UI-backend request/response DTO
- 프론트 TS literal type

### 3.3 API/DTO/프론트 타입 확장

| 타입 계층 | 변경 항목 |
|---|---|
| `ModelInfo` 계열 | `parentModel` 추가 |
| `EqpInfo` 계열 | `isDev` 추가 |
| EQP 관리 상세 응답 | 공통 + SECS/Socket 분기 설정 + 로그 정책 + jar filename + 연결 model + 적용 param 버전 포함 |
| EQP 옵션 응답 | socket protocol list, gateway jar filename list, business jar filename list, 선택 가능한 model/version 목록 포함 |
| Model branch 응답 | parent/branch 구조, branch 상태, commit diff 결과 포함 |

### 3.4 상태값 확장

사용자 요구사항의 초기 상태를 반영하려면 현재 `tc_eqp_state` enum/제약만으로는 부족합니다.

- `control_state`는 최소 `DOWN`, `DISCONNECTED`를 허용해야 합니다.
- `eqp_state`는 최소 `DOWN`, `SERVICE_UNAVAILABLE`를 허용해야 합니다.

설계 원칙:

- DB 저장값은 enum 호환 대문자 문자열로 유지합니다.
- UI 표시 텍스트는 `down`, `Disconnected`, `Service Unavailable`처럼 별도 라벨 매핑으로 처리합니다.

---

## 4. EQP page 설계

### 4.1 Sidebar 컨텍스트 메뉴

#### 4.1.1 개별 EQP 노드 우클릭

선택된 `eqpId` 우클릭 시 다음 메뉴를 표시합니다.

- `Eqp Info Update`
- `Model Info Update`
- `Eqp Parameter Update`
- `Eqp Delete`

#### 4.1.2 Gateway 루트 우클릭

Gateway 섹션 우클릭 시 다음 메뉴를 표시합니다.

- `SECS Eqp Create`
- `Socket Eqp Create`

### 4.2 Eqp Info Update modal

공통 표시 필드:

| 필드 | 소스 | 수정 가능 여부 |
|---|---|---|
| EQP ID | `tc_eqp.eqp_id` | 불가 |
| Comm Interface | `tc_eqp.comm_interface` | 불가 |
| Route Partition | `tc_eqp.route_partition` | 가능 |
| EQP IP | `tc_eqp.eqp_ip` | 가능 |
| EQP Port | `tc_eqp.eqp_port` | 가능 |
| Is Dev | `tc_eqp.is_dev` | 가능 |
| Gateway Jar | `tc_jar_gateway.jar_file_name` | 가능 |
| Log Level | `tc_eqp_log.log_level` | 가능 |
| Log Retention Day | `tc_eqp_log.log_retention_days` | 가능 |
| Log Path | `tc_eqp_log.log_path` | 가능 |

SECS 전용 필드:

- `tc_eqp_hsms.device_id`
- `tc_eqp_hsms.t3_timeout`
- `tc_eqp_hsms.t5_timeout`
- `tc_eqp_hsms.t6_timeout`
- `tc_eqp_hsms.t7_timeout`
- `tc_eqp_hsms.t8_timeout`
- `tc_eqp_hsms.max_msg_bytes`
- `tc_eqp_hsms.link_test_enabled`
- `tc_eqp_hsms.link_test_interval`

Socket 전용 필드:

- `tc_eqp_socket.socket_protocol_type`
- `tc_eqp_socket.charset`
- `tc_eqp_socket.heartbeat_enabled`
- `tc_eqp_socket.heartbeat_interval`
- `tc_eqp_socket.read_timeout`
- `tc_eqp_socket.write_timeout`
- `tc_eqp_socket.max_frame_size_bytes`
- `tc_eqp_socket.keep_alive_enabled`

드롭다운 데이터 소스:

- Gateway Jar: `tc_jar_gateway.jar_file_name` distinct 목록
- Socket Protocol Type: `tc_eqp_socket_protocol_type.socket_protocol_type` 전체 목록

저장 정책:

- DB 반영 후 즉시 Gateway/Business runtime 동기화
- Gateway/Business jar filename이 바뀐 경우 `EQP_UPDATE_JARFILE` 후속 발행
- `is_dev` 변경으로 현재 연결 model이 허용 범위를 벗어나면 저장 거부

### 4.3 Model Info Update modal

구성:

- `tc_model.model_name` 드롭다운
- `tc_model_version.model_version` 드롭다운
- `tc_jar_business.jar_file_name` 드롭다운

초기 선택:

- 현재 EQP에 연결된 model name / model version / business jar filename

필터 규칙:

- `tc_eqp.is_dev = TRUE` -> `tc_model_version.status = DEVELOP`만 노출
- `tc_eqp.is_dev = FALSE` -> `tc_model_version.status = OPERATE`만 노출
- model name 드롭다운도 위 status 조건을 만족하는 모델만 노출

저장 정책:

- `tc_eqp.model_version_key` 갱신
- business jar 선택이 바뀌면 jar row 갱신 후 runtime reload

### 4.4 Eqp Parameter Update modal

구성:

- 현재 적용 버전: `tc_eqp_param.param_version`
- 현재 설명: `tc_eqp_param.description`
- 변경 대상 버전 드롭다운: 해당 EQP의 전체 `param_version`
- 설명 패널: 선택 버전에 맞는 `description`

저장 정책:

- EQP가 참조하는 적용 parameter version을 변경
- Save 시 DB update
- Cancel 시 변경 폐기

### 4.5 Eqp Delete

흐름:

1. 삭제 확인 modal 표시
2. 사용자가 `OK` 선택 시 runtime 연결 종료 시도
3. 종료 성공 또는 이미 종료 상태면 DB 삭제
4. DB 삭제 후 Gateway/Business runtime 동기화

정책:

- 종료 요청은 `EQP_END`를 먼저 보냄
- 종료 실패/타임아웃이면 삭제를 중단
- DB 삭제 후 runtime sync 실패 시 사전 스냅샷으로 DB 복구

### 4.6 Eqp Create modal

`SECS Eqp Create`, `Socket Eqp Create`는 같은 modal을 공유하고 `comm_interface`만 고정합니다.

공통 입력:

- `eqp_id`
- `comm_mode` 드롭다운 (`ACTIVE`, `PASSIVE`)
- `route_partition`
- `eqp_ip`
- `eqp_port`
- `is_dev`
- 연결 model name / model version
- Gateway Jar
- Business Jar
- 로그 정책

`SECS Eqp Create` 고정값/기본값:

- `tc_eqp.comm_interface = SECS`
- `t3_timeout = 45`
- `t5_timeout = 10`
- `t6_timeout = 5`
- `t7_timeout = 10`
- `t8_timeout = 5`
- `max_msg_bytes = 10485760`
- `link_test_enabled = true`
- `link_test_interval = 60`

`Socket Eqp Create` 고정값/기본값:

- `tc_eqp.comm_interface = SOCKET`
- `charset = UTF-8`
- `heartbeat_enabled = true`
- `heartbeat_interval = 30`
- `read_timeout = 0`
- `write_timeout = 0`
- `max_frame_size_bytes = 8192`
- `keep_alive_enabled = true`

최초 부가 row 생성:

- `tc_eqp_state`
  - SECS: `control_state=DOWN`, `eqp_state=DOWN`
  - SOCKET: `control_state=DISCONNECTED`, `eqp_state=SERVICE_UNAVAILABLE`
- `tc_eqp_log`
  - `log_level=INFO`
  - `log_retention_days=7`
  - `log_path=\\`

---

## 5. Model page 설계

### 5.1 관리 개념

모델 관리 방식은 `git main + branch`와 유사하게 정의합니다.

- root model: 운영 기준 모델
- branch model: root model에서 파생된 개발 모델
- version: 각 model 내부 변경 이력

정책:

- root model의 운영 반영 버전은 `OPERATE`
- branch model의 작업 버전은 `DEVELOP`
- branch commit 완료 후 branch의 모든 version status는 `DEPRECATED`

### 5.2 Sidebar 구조

기존 `SECS/Socket -> 대표 model 1행` 구조를 다음으로 변경합니다.

```text
SECS
  Parent Model A
    Parent Model A_branch_user1
    Parent Model A_test_user2
Socket
  Parent Model B
    Parent Model B_hotfix_user3
```

우클릭 메뉴 규칙:

- root model:
  - `Model Info Update`
  - `Branch Model Create`
  - `Branch Deprecated Model Delete`
  - `Model Delete`
- branch model:
  - `Parent Model Commit`
  - `Branch Model Delete`
- SECS 루트:
  - `SECS Model Create`
- Socket 루트:
  - `Socket Model Create`

### 5.3 Root Model Create / Update

#### 5.3.1 SECS Model Create

입력:

- `Model Name`
- `Maker`

고정값:

- `tc_model.comm_interface = SECS`
- `tc_model.parent_model = NULL`
- `tc_model_version.model_version = EDIT`
- `tc_model_version.status = OPERATE`

#### 5.3.2 Socket Model Create

입력:

- `Model Name`
- `Maker`

고정값:

- `tc_model.comm_interface = SOCKET`
- `tc_model.parent_model = NULL`
- `tc_model_version.model_version = EDIT`
- `tc_model_version.status = OPERATE`

#### 5.3.3 Model Info Update

- 신규 create modal을 재사용
- `Model Name`은 읽기 전용
- `Maker`만 수정 가능

### 5.4 Branch Model Create

입력:

- 부모 model 고정값 표시
- 사용자 입력 suffix
- 현재 로그인 `userId`

최종 model name 규칙:

```text
{선택된 parent model}_{사용자 입력}_{userId}
```

저장값:

- `tc_model.parent_model = 선택된 parent model`
- `tc_model_version.model_version = EDIT`
- `tc_model_version.status = DEVELOP`

초기화 규칙:

- parent model의 최신 version 데이터를 branch `EDIT` version으로 복제
- 복제 대상:
  - model parameter
  - secs/socket message
  - variable/report/event
  - workflow
  - mdf
  - dcop item

### 5.5 Parent Model Commit

대상:

- branch model 우클릭 메뉴

비교 기준:

- branch model의 최신 version
- parent model의 최신 version

diff 대상:

- `model-parameter`
- `secs-message`
- `socket-message`
- `variableides`
- `reportides`
- `eventides`
- `workflow`
- `mdf`
- `dcop-itemes`

동작:

1. diff modal 오픈
2. 추가/변경/삭제 항목을 섹션별로 표시
3. 사용자가 새 parent version 문자열 직접 입력
4. Commit 실행 시 parent의 새 version 생성
5. diff 결과를 parent 새 version에 반영
6. branch model의 모든 version status를 `DEPRECATED`로 변경

### 5.6 Branch Deprecated Model Delete

대상:

- parent model 우클릭 메뉴

동작:

- 선택된 parent에 연결된 branch model 중 최신 version status가 `DEPRECATED`인 모델을 일괄 삭제

### 5.7 Model Delete / Branch Model Delete

#### 5.7.1 Parent Model Delete

정책:

- branch까지 연쇄 물리 삭제
- 단, 어떤 version이든 `tc_eqp.model_version_key`에서 참조 중이면 `409 CONFLICT`

#### 5.7.2 Branch Model Delete

정책:

- 삭제 확인 modal 후 물리 삭제
- EQP 참조 중이면 `409 CONFLICT`

---

## 6. 백엔드 설계

### 6.1 EQP CRUD 처리 방식

현재 `POST/PUT/DELETE /api/eqp`는 runtime 명령 발행 중심입니다.
이번 확장에서는 같은 경로를 유지하되 내부 처리 의미를 다음으로 변경합니다.

```text
요청 수신
  → DB 검증
  → DB create/update/delete
  → Gateway/Business 동기화 발행
  → DualResponse 확인
  → 최종 응답 반환
```

필요 계층:

- EQP CRUD Port
- EQP 관리 상세 Query Port
- EQP 옵션 Query Port
- EQP CRUD Orchestration Service

### 6.2 EQP 보상 전략

#### Create

- DB insert 후 runtime sync 실패 시 DB delete로 롤백

#### Update

- update 전 스냅샷 보관
- runtime sync 실패 시 이전 스냅샷으로 DB 복구

#### Delete

- delete 전 스냅샷 보관
- runtime sync 실패 시 삭제했던 row를 복구
- jar/log/state/hsms/socket row도 함께 복구

### 6.3 Model branch/commit 서비스

필요 기능:

- root model 생성
- branch model 생성
- branch clone
- parent/branch diff 계산
- parent 새 version 생성
- branch deprecated 일괄 처리
- parent cascade delete 검증

### 6.4 신규 API 방향

EQP:

- `GET /api/eqp/{eqpId}/manage`
- `GET /api/eqp/options`
- 기존 `POST /api/eqp`
- 기존 `PUT /api/eqp/{eqpId}`
- 기존 `DELETE /api/eqp/{eqpId}`

Model:

- `POST /api/model/roots`
- `PUT /api/model/{modelKey}/info`
- `POST /api/model/{modelKey}/branches`
- `POST /api/model/{modelKey}/commit-parent`
- `DELETE /api/model/{modelKey}/branches/deprecated`
- `DELETE /api/model/{modelKey}`

기존 API 유지:

- `GET /api/model`
- `GET /api/model/{modelVersionKey}`
- `GET /api/model/{modelVersionKey}/details/{detailNode}`
- 기존 version 단위 `POST/PUT/DELETE /api/model`는 상세 편집 호환을 위해 유지

---

## 7. 프론트 설계

### 7.1 공통 UI 컴포넌트

현재 프론트에는 `select`, `dropdown-menu`, `context-menu`가 없습니다.
다음 primitive를 추가합니다.

- `Select`
- `DropdownMenu`
- `ContextMenu`
- 공통 `ConfirmDialog`

### 7.2 EQP page UI

- Sidebar 우클릭 메뉴 추가
- EQP 관리용 modal 4종 추가
  - `EqpManageFormModal`
  - `EqpModelBindingModal`
  - `EqpParamVersionModal`
  - `EqpDeleteConfirmDialog`
- gateway 우클릭 create 흐름 추가
- 저장 성공 후 관련 query invalidate

### 7.3 Model page UI

- Sidebar를 parent/branch 트리 구조로 재작성
- root/branch별 우클릭 메뉴 분기
- Model create/update/branch create/commit/delete modal 추가
- parent commit diff 결과를 탭/섹션별 테이블로 표시
- 기존 checkout/checkin 상세 편집 흐름은 유지

### 7.4 Dropdown 데이터 소스

| 드롭다운 | 데이터 소스 |
|---|---|
| Gateway Jar | `tc_jar_gateway.jar_file_name` distinct |
| Business Jar | `tc_jar_business.jar_file_name` distinct |
| Socket Protocol Type | `tc_eqp_socket_protocol_type.socket_protocol_type` 전체 |
| EQP Model Name/Version | `is_dev` 규칙으로 필터링된 model/version |
| Eqp Param Version | 해당 EQP의 `param_version` 목록 |

---

## 8. 검증 및 예외 정책

### 8.1 검증 규칙

- `is_dev=true`인데 `OPERATE` model 연결 시 저장 불가
- `is_dev=false`인데 `DEVELOP` model 연결 시 저장 불가
- parent model name과 branch model name은 중복 불가
- branch commit 시 parent version 문자열은 필수
- model delete 시 EQP 참조가 남아 있으면 `409`
- EQP delete 시 runtime 종료 실패면 삭제 불가

### 8.2 jar filename 정책

- dropdown은 filename만 보여줌
- 실제 저장 시 같은 filename의 최신 `updated_at` row를 원본으로 사용
- 선택하지 않으면 기존 jar 유지 또는 미생성 상태 유지

### 8.3 acceptance 기준

- EQP page에서 create/update/delete와 model/parameter 연결 변경이 모두 가능해야 함
- Model page에서 root/branch 생성과 parent commit이 가능해야 함
- parent commit은 diff에서 삭제 항목까지 보여주고 반영해야 함
- 기존 Model checkout/checkin 기능은 계속 동작해야 함

---

## 9. 구현 순서

1. DB schema, enum, domain 타입 변경
2. UI-backend port/service/controller 설계 및 구현
3. 프론트 타입/api 훅 확장
4. EQP page UI 구현
5. Model page UI 구현
6. 테스트 및 시나리오 검증

---

## 10. 가정

- `tc_eqp.enabled`는 create 시 기본 `true`, update 시 기존값 유지
- `comm_mode`는 EQP 생성 시만 선택하고 수정 modal에는 노출하지 않음
- branch model 생성 시 parent 최신 version 전체를 clone하는 것이 기본 정책
- EQP create/update/delete는 DB 저장 후 runtime 동기화까지 성공해야 최종 성공으로 판단
