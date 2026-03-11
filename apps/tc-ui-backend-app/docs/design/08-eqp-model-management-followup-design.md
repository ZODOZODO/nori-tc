> 작성일: 2026-03-12

# 08. EQP/Model 관리 후속 정비 설계

## 1. 개요

### 1.1 배경

07번 문서 기준의 EQP/Model 관리 기능은 기본 CRUD와 branch 관리 흐름을 제공하지만, 실제 사용 과정에서 다음 정합성 문제가 추가로 확인되었습니다.

- EQP page의 `Eqp Parameter Update`는 적용 버전을 바꾸는 UX를 제공하지만, 현재 적용 버전을 저장하는 전용 컬럼이 없어 실제 반영 상태를 안정적으로 표현하지 못합니다.
- EQP update 경로는 `comm_mode`를 읽기 전용으로 취급하고 있어 create와 update의 수정 가능 범위가 일치하지 않습니다.
- EQP 정보 수정 모달은 휠 스크롤 시 footer 아래에 공백이 생기며, `Cancel / Save` 버튼 영역이 밀리는 레이아웃 깨짐이 발생합니다.
- Model page는 root/branch 편집 경계가 약해 root model도 현재 사용자의 `EDIT` 상태에 따라 직접 수정 가능한 것처럼 동작합니다.
- workflow/model 상세 테이블의 주요 문자열 컬럼 길이가 실제 운영 naming/filter 데이터를 수용하기에 부족해 DB/검증 기준 확장이 필요합니다.

### 1.2 목표

본 문서는 07번 이후 남아 있는 EQP/Model 관리 화면의 UX 불일치와 저장 계약 부재를 정리하고, 후속 구현 시 따라야 할 최종 정책을 확정합니다.

- EQP 적용 Param Version의 저장 위치와 조회 기준을 확정합니다.
- EQP/Model 관련 주요 문자열 컬럼의 최대 길이를 후속 운영 요구에 맞게 확정합니다.
- EQP update/create modal의 수정 가능 항목을 일관된 기준으로 정리합니다.
- EQP modal 스크롤 레이아웃 버그에 대한 수정 방향을 확정합니다.
- Model root/branch 편집 정책과 checkout 흐름을 사용자 의도에 맞게 재정의합니다.

### 1.3 참조 문서

- 기준 아키텍처: `docs/design/01-system-architecture.md`
- 기존 UI 관리 페이지 설계: `docs/design/02-ui-management-pages-design.md`
- 기존 EQP UX 개선 설계: `docs/design/06-eqp-page-ux-improvement-design.md`
- 선행 CRUD/branch 설계: `docs/design/07-eqp-model-crud-and-branch-management-design.md`
- 선행 CRUD/branch 작업 계획: `docs/tasks/07-eqp-model-crud-and-branch-management-build-plan.md`

---

## 2. 범위와 전제

### 2.1 범위

- `nori-tc` UI-backend의 EQP/Model 후속 계약 정비
- `nori-tc-ui` EQP page / Model page 후속 UX 정비
- EQP checkout / Model checkout 충돌 처리 정책 보완

### 2.2 비범위

- 본 문서는 설계 문서이며 실제 코드 구현/테스트 결과는 포함하지 않습니다.
- deploy/runtime 구조 변경과 운영 이관 절차 상세는 본 문서 범위 밖입니다.
- model 상세 테이블 도메인 자체 리팩토링은 포함하지 않습니다.
- 새로운 model checkout 전용 API 도입은 포함하지 않습니다.

### 2.3 확정 전제

- `Gateway Jar`는 계속 `Eqp Info Update` modal에서 수정합니다.
- `Model Info Update` modal은 `Model Name`, `Model Version`, `Business Jar`만 담당합니다.
- EQP 적용 Param Version은 `tc_eqp.applied_param_version` 컬럼으로 저장합니다.
- `applied_param_version` 컬럼 위치는 `tc_eqp.model_version_key` 다음으로 고정합니다.
- root model은 직접 `Check Out / Check In` 하지 않습니다.
- branch model은 명시적 checkout 이후에만 edit mode로 진입합니다.
- EQP Param Version 선택 목록에서는 내부 편집용 버전인 `EDIT`를 제외합니다.

---

## 3. 데이터 및 공개 계약 변경

### 3.1 DB 스키마 변경

#### 3.1.1 `tc_eqp`

- `model_version_key` 다음에 `applied_param_version VARCHAR(100) NULL` 추가

의미:

- EQP가 현재 실제로 적용 중인 파라미터 버전을 저장합니다.
- `NULL`은 legacy 데이터 또는 아직 명시적으로 적용 버전을 저장하지 않은 EQP를 의미합니다.

#### 3.1.2 EQP/Model 문자열 컬럼 길이 확장

실제 스키마 기준 컬럼명으로 다음 길이 확장을 반영합니다.

| 테이블 | 컬럼 | 현재 | 변경 후 |
|---|---|---:|---:|
| `tc_model_workflow` | `workflow_name` | `VARCHAR(200)` | `VARCHAR(1000)` |
| `tc_model_workflow` | `message_name` | `VARCHAR(200)` | `VARCHAR(1000)` |
| `tc_model_workflow` | `transaction_id` | `VARCHAR(200)` | `VARCHAR(2000)` |
| `tc_model_workflow` | `workflow_filter` | `VARCHAR(200)` | `VARCHAR(4000)` |
| `tc_model_dcop_item` | `dcop_item_name` | `VARCHAR(200)` | `VARCHAR(1000)` |
| `tc_model_dcop_item` | `workflow_name` | `VARCHAR(200)` | `VARCHAR(1000)` |
| `tc_model_dcop_item` | `variable_id` | `VARCHAR(100)` | `VARCHAR(1000)` |
| `tc_model_dcop_item` | `calculation_rule` | `VARCHAR(20)` | `VARCHAR(2000)` |
| `tc_model_mdf` | `mdf_name` | `VARCHAR(100)` | `VARCHAR(1000)` |
| `tc_model_param` | `param_name` | `VARCHAR(128)` | `VARCHAR(1000)` |
| `tc_model_secs_message` | `secs_msg_name` | `VARCHAR(100)` | `VARCHAR(1000)` |
| `tc_model_socket_message` | `socket_msg_name` | `VARCHAR(100)` | `VARCHAR(1000)` |
| `tc_model_variableid` | `variable_id` | `VARCHAR(100)` | `VARCHAR(1000)` |
| `tc_model_version` | `model_version` | `VARCHAR(32)` | `VARCHAR(100)` |
| `tc_model` | `model_name` | `VARCHAR(128)` | `VARCHAR(1000)` |
| `tc_model` | `parent_model` | `VARCHAR(128)` | `VARCHAR(1000)` |

설계 원칙:

- `tc_model.parent_model`은 self FK 대상인 `tc_model.model_name`과 항상 동일한 길이를 유지합니다.
- 문자열 길이 확장은 DB DDL뿐 아니라 JPA/MyBatis 매핑 길이, 백엔드 validation, 프론트 입력 제한에도 동일하게 반영합니다.

### 3.2 백엔드/프론트 계약 반영 범위

| 계층 | 반영 항목 |
|---|---|
| `EqpUpdateRequest` | `commMode`, `appliedParamVersion` 추가 |
| `EqpManagementCommand.Update` | `commMode`, `appliedParamVersion` 추가 |
| `UpsertTcEqp` | `commMode`, `appliedParamVersion` 추가 |
| `TcEqp` | `commMode`, `appliedParamVersion` 추가 |
| EQP 관리 상세 응답 | `appliedParamVersion`을 실제 저장 컬럼 기준으로 노출 |

설계 원칙:

- update 요청과 DB 저장 모델이 동일한 필드를 공유하도록 맞춰 create/update 간 수정 가능 범위를 일치시킵니다.
- 프론트는 별도 계산값이 아니라 관리 상세 응답의 `appliedParamVersion`을 source of truth로 사용합니다.
- model/workflow 관련 요청/커맨드 검증 길이는 확장된 DB 길이와 동일하게 유지해 저장 직전 truncate 또는 불필요한 validation 실패를 방지합니다.

### 3.3 EQP 관리 상세 조회 정책

- EQP manage detail 응답의 `appliedParamVersion`은 우선 `tc_eqp.applied_param_version` 값을 그대로 사용합니다.
- legacy 데이터처럼 컬럼 값이 비어 있는 경우에만 fallback을 허용합니다.
- fallback은 기존 조회 경로와의 호환을 위한 임시 정책이며, 신규 저장 이후에는 실제 컬럼 값이 우선합니다.

### 3.4 Param Version 조회 계약

EQP Parameter Update 화면은 두 종류의 조회를 분리합니다.

1. version summary 조회
   - version 목록, 설명, 기본 메타 정보만 제공합니다.
   - `EDIT` 버전은 목록에서 제외합니다.
2. 선택 version 상세 조회
   - 기존 `GET /api/eqp/{eqpId}/params?version=` API를 재사용합니다.
   - 상세 결과는 `param name / param value` table 구성에 사용합니다.

---

## 4. EQP page 설계

### 4.1 `Eqp Info Update` modal

공통 원칙:

- `Eqp Info Update`는 설비 자체 설정, 통신 설정, gateway/log 정책을 관리합니다.
- create와 update의 수정 가능 범위는 가능한 한 동일하게 유지합니다.

수정 가능 항목:

| 항목 | 정책 |
|---|---|
| `Comm Mode` | update에서도 수정 가능 |
| `Log Level` | 기존과 동일하게 수정 가능 유지 |
| `Socket Protocol Type` | socket EQP일 때 수정 가능 유지 |
| `Gateway Jar` | 현재 위치 유지, 이 modal에서 수정 |

읽기 전용 유지 항목:

- `EQP ID`
- `Comm Interface`

### 4.2 `Model Info Update` modal

수정 가능 항목:

- `Model Name`
- `Model Version`
- `Business Jar`

연동 규칙:

- `tc_eqp.is_dev = TRUE`이면 `DEVELOP` 상태 model/version만 노출합니다.
- `tc_eqp.is_dev = FALSE`이면 `OPERATE` 상태 model/version만 노출합니다.
- `Model Name`이 바뀌면 해당 model에 속한 `Model Version` 목록을 즉시 다시 계산합니다.

제외 항목:

- `Gateway Jar`는 이 modal로 이동하지 않습니다.

### 4.3 `Eqp Parameter Update` modal

구성:

- 현재 적용 버전: `tc_eqp.applied_param_version`
- 변경 대상 버전: 선택 가능한 `param_version` 목록
- 선택 버전 설명: textarea 대신 단일 text field
- 선택 버전 상세: `param name / param value` data table

조회 규칙:

- 변경 대상 버전 선택 시 기존 `GET /api/eqp/{eqpId}/params?version=` API로 상세 데이터를 다시 조회합니다.
- 선택 버전 설명은 summary 응답 또는 상세 응답의 description을 단문 형태로 표시합니다.
- version 선택 목록에서는 `EDIT`를 제외합니다.

저장 규칙:

- 저장 시 `tc_eqp.applied_param_version`만 변경합니다.
- 파라미터 row 자체를 수정하는 동작과 적용 버전 변경 동작은 분리합니다.

### 4.4 `SECS Eqp Create` / `Socket Eqp Create` modal

- update modal에서 허용하는 선택 정책은 create modal에도 동일하게 반영합니다.
- `Comm Mode`, `Log Level`, `Socket Protocol Type`, `Model Name`, `Model Version`, `Business Jar`의 선택 기준이 create/update에서 다르게 보이지 않도록 맞춥니다.
- create에서도 `is_dev` 선택 결과에 맞는 model/version 필터를 적용합니다.

### 4.5 EQP modal 레이아웃 버그 수정 기준

문제:

- `Eqp Info Update` modal을 마우스 휠로 계속 내리면 footer 아래에 빈 공간이 생기며 버튼 영역이 밀립니다.

수정 기준:

- scroll 영역과 footer를 구조적으로 분리합니다.
- footer는 고정된 액션 영역으로 유지하고 shrink되지 않게 합니다.
- body 영역만 스크롤되도록 하여 `Cancel / Save` 아래에 추가 공백이 생기지 않게 합니다.

### 4.6 EQP checkout 오류 분석 및 후속 정책

현재 문제:

- `JpaEqpParamCommandPort.checkout()`는 `exists` 선조회 후 insert를 수행하므로, 동시에 checkout이 들어오면 race condition이 발생할 수 있습니다.
- 중복 키 발생 시 실제 동시 checkout 충돌인지, source version 데이터 중복 문제인지 로그만으로 즉시 구분하기 어렵습니다.
- 재조회 시 owner를 찾지 못하면 사용자 메시지에 `알 수 없음`이 노출됩니다.

후속 정책:

- 설비 단위 직렬화 또는 parent row lock으로 checkout 경쟁을 줄입니다.
- duplicate key 발생 후 재조회 실패 시에도 사용자 메시지는 결정적으로 정규화합니다.
- 로그에는 `eqpId`, source version, 충돌 시점 식별 정보를 남겨 원인 추적 가능성을 높입니다.

---

## 5. Model page 설계

### 5.1 Sidebar 표시 정책

변경 사항:

- 모델 목록의 `최신 버전:` 문구를 제거합니다.
- `ROOT` badge를 제거합니다.
- `DEPRECATED` badge만 유지합니다.
- sidebar는 model name 중심으로 간략하게 표시합니다.

설계 의도:

- root/branch 구조를 설명하는 메타 정보보다 모델 식별 자체를 우선 노출해 탐색 복잡도를 낮춥니다.
- 상태 강조는 실제 작업 판단에 필요한 `DEPRECATED`만 남깁니다.

### 5.2 `Model Info Update` modal

삭제 항목:

- `Comm Interface`
- `Model Version`
- `Status`

유지 목적:

- model 정보 수정 modal은 실제 수정 가능한 핵심 필드만 보여주고, 읽기 전용 상태 요약은 제거합니다.

### 5.3 root / branch 편집 정책

- root model은 항상 읽기 전용입니다.
- root model에서는 `Check Out`, `Check In` 기능을 제공하지 않습니다.
- branch model만 편집 대상입니다.
- branch model도 명시적 checkout 전에는 읽기 전용으로 유지합니다.

정책 배경:

- root는 배포 기준이 되는 기준선으로 유지하고, 변경은 branch에서 작업 후 version 생성 및 반영 절차를 통해서만 진행합니다.

### 5.4 branch checkout 방식

- 새 전용 백엔드 API는 추가하지 않습니다.
- 기존 `EDIT` 생성 흐름을 checkout 의미로 계속 사용합니다.
- 프론트는 checkout 직전 최신 model 목록을 다시 조회합니다.
- 409 conflict가 발생하면 재조회 결과를 바탕으로 owner 정보를 정규화해 안내합니다.

UI 상태 전환 기준:

- 활성 tab 자체만으로 edit mode를 결정하지 않습니다.
- `branch 여부`와 `명시적 checkout 성공 여부`를 함께 만족해야 edit mode로 진입합니다.

### 5.5 Branch Model Create 길이 오류 원인

원인:

- branch model 생성 시 최종 model name은 `${parent}_${suffix}_${userId}` 형식으로 조합됩니다.
- 조합된 최종 이름이 1000자를 초과하면 백엔드 validation에서 길이 초과 오류가 발생합니다.

후속 정책:

- 프론트는 suffix 입력 단계에서 최종 조합 길이를 기준으로 사전 검증합니다.
- 사용자는 저장 전에 남은 길이 또는 초과 여부를 확인할 수 있어야 합니다.

---

## 6. 테스트 및 검증 관점

### 6.1 백엔드 계약 검증

- `applied_param_version` 컬럼이 DDL/domain/JPA/MyBatis/store에 누락 없이 반영되는지 확인합니다.
- 문자열 길이 확장 대상 컬럼이 DDL/entity/mapper/validation 전 구간에 동일하게 반영되는지 확인합니다.
- EQP update에서 `commMode`와 `appliedParamVersion`이 실제 저장되는지 확인합니다.
- EQP manage detail이 저장 컬럼 우선, legacy fallback 보조 정책을 지키는지 확인합니다.
- EQP checkout race 완화와 conflict 메시지 정규화가 동작하는지 확인합니다.

### 6.2 프론트 상태 전환 검증

- `Eqp Info Update`에서 `Comm Mode`가 update에서도 수정 가능한지 확인합니다.
- `Eqp Parameter Update`에서 version 변경 시 설명과 param table이 함께 갱신되는지 확인합니다.
- create/update modal이 동일한 선택 정책을 쓰는지 확인합니다.
- root model이 읽기 전용으로 유지되고 branch explicit checkout 이후에만 편집 가능한지 확인합니다.

### 6.3 브라우저 수동 확인 포인트

- EQP modal 스크롤 시 footer 아래 공백이 재현되지 않는지 확인합니다.
- `Model Name` 변경 시 `Model Version` 옵션이 올바르게 연동되는지 확인합니다.
- branch model create 길이 초과 시 1000자 기준 사전 안내가 서버 오류 전에 화면에 보이는지 확인합니다.
- checkout 충돌 시 owner 정보가 일관된 한국어 메시지로 표시되는지 확인합니다.

---

## 7. 비범위

- deploy/runtime 구조 자체를 다시 설계하는 작업
- model 상세 도메인 테이블 구조 리팩토링
- 별도 model checkout 전용 REST API 신설
