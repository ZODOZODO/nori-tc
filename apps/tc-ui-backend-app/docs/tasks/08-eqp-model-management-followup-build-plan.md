# 08. EQP/Model 관리 후속 정비 작업 계획

## 참조 문서

- 설계: `docs/design/08-eqp-model-management-followup-design.md`
- 선행 설계: `docs/design/07-eqp-model-crud-and-branch-management-design.md`
- 선행 작업 계획: `docs/tasks/07-eqp-model-crud-and-branch-management-build-plan.md`

---

## 진행 원칙

- 본 문서는 후속 정비 구현 순서를 고정하는 작업 계획입니다.
- 실제 구현 순서는 `DB/백엔드 계약 → EQP UI → Model UI → 검증`으로 유지합니다.
- 07번 문서와 충돌하는 정책은 08번 문서의 후속 정비 기준을 우선 적용합니다.
- 체크박스는 실제 구현 완료 시 즉시 갱신합니다.

---

## 작업 범위

| 작업 ID | 작업 항목 | 주요 대상 |
|---|---|---|
| T1 | DB 및 공통 타입 확장 | DDL/sample SQL, db-domain, JPA/MyBatis, request/command |
| T2 | EQP 백엔드 후속 정비 | UI core/db/web adapter, checkout command path |
| T3 | EQP 프론트 후속 정비 | `nori-tc-ui` EQP modal/page/api/type |
| T4 | Model 프론트/백엔드 후속 정비 | `nori-tc-ui` Model page + backend conflict handling |
| T5 | 테스트 및 acceptance 검증 | backend test, frontend build/lint, 브라우저 QA |

---

## T1. DB 및 공통 타입 확장

### 목적

EQP 적용 Param Version 저장 부재와 update 계약 누락을 보완하고, EQP/Model 관련 문자열 컬럼 길이 확장 기준을 반영합니다.

### 작업 내용

#### T1-1. `tc_eqp` 스키마 확장

- [x] `postgres_create_all_table.sql`의 `tc_eqp`에 `applied_param_version VARCHAR(100) NULL` 추가
- [x] `applied_param_version`을 `model_version_key` 다음 컬럼 위치에 배치
- [x] `postgres_insert_sample_data.sql` 반영 필요 여부를 검토하고, 필요 시 기본값 또는 예시 데이터를 정리

#### T1-2. EQP/Model 문자열 컬럼 길이 확장

- [x] `postgres_create_all_table.sql`의 `tc_model_workflow.workflow_name`, `message_name`을 `VARCHAR(1000)`으로, `transaction_id`를 `VARCHAR(2000)`으로, `workflow_filter`를 `VARCHAR(4000)`으로 확장
- [x] `postgres_create_all_table.sql`의 `tc_model_dcop_item.dcop_item_name`, `workflow_name`, `variable_id`를 `VARCHAR(1000)`으로, `calculation_rule`을 `VARCHAR(2000)`으로 확장
- [x] `postgres_create_all_table.sql`의 `tc_model_mdf.mdf_name`, `tc_model_param.param_name`, `tc_model_secs_message.secs_msg_name`, `tc_model_socket_message.socket_msg_name`, `tc_model_variableid.variable_id`를 `VARCHAR(1000)`으로 확장
- [x] `postgres_create_all_table.sql`의 `tc_model_version.model_version`을 `VARCHAR(100)`으로, `tc_model.model_name`, `parent_model`을 `VARCHAR(1000)`으로 확장
- [x] `tc_model.parent_model -> tc_model.model_name` self FK와 unique/index가 확장 길이에서도 그대로 유지되는지 확인

#### T1-3. DB domain / 저장 매핑 반영

- [x] `TcEqp`에 `appliedParamVersion` 필드 추가
- [x] `UpsertTcEqp`에 `commMode`, `appliedParamVersion` 필드 반영
- [x] JPA entity/store에 `applied_param_version` 매핑 추가
- [x] MyBatis mapper/resultMap/upsert SQL에 `applied_param_version` 반영
- [x] 문자열 길이 확장 대상 JPA entity `@Column(length=...)`, MyBatis parameter/resultMap, db-domain 주석/계약 길이를 DDL과 동일하게 조정

#### T1-4. 공개 계약 확장

- [x] `EqpUpdateRequest`에 `commMode`, `appliedParamVersion` 추가
- [x] `EqpManagementCommand.Update`에 `commMode`, `appliedParamVersion` 추가
- [x] EQP 관리 상세 응답 모델에 `appliedParamVersion` source of truth를 저장 컬럼 기준으로 정리
- [x] model/workflow 관련 create/update request, command, validation max length를 확장된 DB 기준으로 정리

### T1 검증

- [x] 신규 컬럼이 DDL, domain, JPA, MyBatis, store에 누락 없이 반영되었는지 확인
- [x] update 요청에서 `commMode`, `appliedParamVersion`이 command/store까지 전달되는지 확인
- [x] 문자열 길이 확장 대상 컬럼이 DDL/JPA/MyBatis/validation에 동일하게 반영되었는지 확인
- [x] `model_name`/`parent_model` 확장 후 branch/self FK 관련 저장이 회귀되지 않는지 확인

---

## T2. EQP 백엔드 후속 정비

### 목적

EQP update 저장 범위, applied param version 조회 기준, checkout 충돌 처리를 정리합니다.

### 작업 내용

#### T2-1. EQP update 저장 경로 정비

- [x] EQP update 경로에서 `commMode` 저장이 가능하도록 service/db adapter를 수정
- [x] EQP update 경로에서 `appliedParamVersion` 저장이 가능하도록 service/db adapter를 수정
- [x] `Comm Interface`, `EQP ID` 읽기 전용 정책이 유지되는지 확인

#### T2-2. EQP manage detail 조회 기준 정비

- [x] manage detail 응답에서 `tc_eqp.applied_param_version`을 우선 반환
- [x] 컬럼 값이 비어 있을 때만 legacy fallback을 허용
- [x] fallback 적용 여부가 응답/서비스 코드에서 혼동되지 않도록 계산 책임을 한 곳으로 모음

#### T2-3. Param version 목록/상세 정책 정비

- [x] param version summary 목록에서 `EDIT` 버전을 제외
- [x] 선택 version 상세는 기존 `GET /api/eqp/{eqpId}/params?version=` API 재사용 기준으로 정리
- [x] version summary와 상세 param 조회 책임을 분리해 프론트가 table을 안정적으로 구성할 수 있게 함

#### T2-4. EQP checkout race 보강

- [x] `JpaEqpParamCommandPort.checkout()`의 경쟁 구간을 분석해 설비 단위 직렬화 또는 row lock 적용
- [x] duplicate key 발생 시 재조회 실패하더라도 사용자 메시지를 정규화
- [x] 로그에 원인 추적용 key 정보가 충분히 남는지 확인

### T2 검증

- [ ] EQP update 후 `comm_mode`, `applied_param_version`이 실제 DB에 반영되는지 확인
- [x] manage detail이 컬럼 우선 / fallback 보조 정책을 지키는지 확인
- [x] checkout 동시 요청 시 duplicate key가 raw 오류로 노출되지 않는지 확인

---

## T3. EQP 프론트 후속 정비

### 목적

EQP modal의 수정 가능 항목과 applied param version UX를 후속 정책에 맞게 정렬합니다.

### 작업 내용

#### T3-1. `EqpManageFormModal` 정비

- [x] update 모드에서도 `Comm Mode`를 수정 가능하게 변경
- [x] `Log Level` 수정 가능 상태가 회귀되지 않았는지 확인
- [x] socket EQP에서 `Socket Protocol Type` 수정 가능 상태가 회귀되지 않았는지 확인
- [x] `Gateway Jar`는 계속 `Eqp Info Update`에 유지

#### T3-2. `EqpParamVersionModal` 재구성

- [x] `변경 대상 Param Version`을 선택 가능한 dropdown으로 유지/보완
- [x] `선택 Version Description`을 textarea 대신 단일 text field로 변경
- [x] description 아래에 `param name / param value` data table 추가
- [x] version 변경 시 상세 param 조회를 다시 수행하도록 상태 흐름 수정

#### T3-3. EQP create modal 정책 동기화

- [x] `SECS Eqp Create`와 `Socket Eqp Create`가 update modal과 동일한 선택 정책을 사용하도록 정리
- [x] `is_dev`에 따른 model/version 필터가 create에서도 일관되게 동작하는지 확인
- [x] model/version/business jar 선택 흐름이 create/update에서 다르게 보이지 않도록 정리

#### T3-4. modal 레이아웃 버그 수정

- [x] scroll 영역과 footer를 구조적으로 분리
- [x] footer 액션 영역이 shrink되지 않도록 수정
- [ ] 마우스 휠 스크롤 시 footer 아래 공백이 생기지 않는지 확인

### T3 검증

- [ ] update 모드에서 `Comm Mode` 수정 후 저장되는지 확인
- [ ] `Eqp Parameter Update`에서 version 선택 후 table 내용이 바뀌는지 확인
- [ ] create/update modal의 공통 정책이 동일하게 노출되는지 확인
- [ ] scroll bug가 재현되지 않는지 확인

---

## T4. Model 프론트/백엔드 후속 정비

### 목적

Model page를 root read-only, branch explicit checkout 중심 흐름으로 재정렬합니다.

### 작업 내용

#### T4-1. Sidebar 단순화

- [x] sidebar에서 `최신 버전:` 문구 제거
- [x] `ROOT` badge 제거
- [x] `DEPRECATED` badge만 유지
- [x] model name 중심 표시/검색으로 정리

#### T4-2. `ModelCreateOrUpdateModal` 정리

- [x] `Comm Interface` 표시 제거
- [x] `Model Version` 표시 제거
- [x] `Status` 표시 제거

#### T4-3. root / branch 편집 상태 정비

- [x] root model detail을 항상 읽기 전용으로 처리
- [x] root model에서 `Check Out / Check In` 버튼을 숨기거나 비활성화
- [x] branch model도 명시적 checkout 전에는 읽기 전용으로 유지
- [x] 활성 tab만으로 edit mode에 진입하지 않도록 UI 상태 조건을 수정

#### T4-4. branch explicit checkout 흐름 정비

- [x] 새 전용 백엔드 API 없이 기존 `EDIT` 생성 흐름을 유지
- [x] checkout 직전 최신 목록을 재조회하는 흐름 추가
- [x] 409 conflict 발생 시 owner 정보를 정규화해 사용자 메시지로 노출

#### T4-5. Branch Model Create 사전 검증

- [x] `${parent}_${suffix}_${userId}` 기준 최종 이름 길이를 프론트에서 계산
- [x] 1000자 초과 여부를 저장 전에 안내
- [x] 남은 길이 또는 초과 상태를 사용자가 즉시 확인할 수 있게 표시

### T4 검증

- [ ] root model에서 checkout 버튼이 노출되지 않거나 비활성화되는지 확인
- [ ] branch model에서 explicit checkout 후에만 편집 가능한지 확인
- [ ] branch create 길이 초과 시 1000자 기준 사전 검증 메시지가 보이는지 확인
- [ ] model checkout conflict가 raw 오류 대신 정규화된 메시지로 보이는지 확인

---

## T5. 테스트 및 acceptance 검증

### 목적

후속 정비가 저장 계약, UI 상태 전환, 충돌 처리에 회귀 없이 반영되었는지 확인합니다.

### 작업 내용

#### T5-1. 백엔드 테스트

- [x] `applied_param_version` 매핑 테스트 추가/수정
- [x] 문자열 길이 확장 대상 컬럼의 entity/mapper/validation max length 테스트 추가/수정
- [x] EQP update의 `commMode`, `appliedParamVersion` 저장 테스트 추가/수정
- [x] EQP manage detail의 컬럼 우선 / fallback 보조 정책 테스트 추가/수정
- [x] EQP checkout race 또는 duplicate key conflict 정규화 테스트 추가/수정

#### T5-2. 프론트 검증

- [x] `nori-tc-ui` build 확인
- [x] `nori-tc-ui` lint 확인
- [ ] EQP/Model 주요 modal interaction 회귀 여부 확인

#### T5-3. 수동 acceptance QA

- [ ] EQP `Comm Mode` 저장 확인
- [ ] EQP Param Version 선택 후 table 변경 확인
- [ ] create/update modal 공통 정책 동작 확인
- [ ] scroll bug 재현 불가 확인
- [ ] root model에서 checkout 버튼 미노출 또는 비활성 확인
- [ ] branch model에서 explicit checkout 후에만 편집 가능 확인
- [ ] branch create 길이 초과 시 1000자 기준 사전 검증 메시지 표시 확인

---

## 추가 확인 필요 사항

- 운영 데이터에 `applied_param_version` 초기값 보정이 필요한지 확인 필요
- EQP checkout race 재현 시나리오를 브라우저/멀티세션 기준으로 별도 검증할 필요가 있음
- branch explicit checkout 관련 UX 문구는 구현 후 최종 확인이 필요
