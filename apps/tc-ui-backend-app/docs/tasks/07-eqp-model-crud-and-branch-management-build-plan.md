> 작성일: 2026-03-11

# 07. EQP/Model CRUD 및 Branch 관리 작업 계획

## 참조 문서

- 설계: `docs/design/07-eqp-model-crud-and-branch-management-design.md`
- 기존 관리 페이지 기준: `docs/design/02-ui-management-pages-design.md`
- 기존 EQP UX 기준: `docs/design/06-eqp-page-ux-improvement-design.md`

---

## 진행 원칙

- 본 문서는 구현 순서를 고정하는 작업 계획입니다.
- 실제 구현 순서는 `스키마/enum → 백엔드 port/service/controller → 프론트 타입/api → EQP UI → Model UI → 테스트`로 유지합니다.
- 기존 Model 상세 편집의 `Check Out / Check In` 흐름은 제거하지 않습니다.
- EQP 생성/수정/삭제는 DB 반영과 runtime 동기화를 모두 포함합니다.
- 체크박스는 실제 구현 완료 시 즉시 갱신합니다.

---

## 작업 범위

| 작업 ID | 작업 항목 | 주요 대상 |
|---|---|---|
| T1 | DB 스키마 및 enum/도메인 확장 | DDL/sample SQL, db-domain, JPA/MyBatis |
| T2 | EQP 백엔드 CRUD/조회/동기화 확장 | UI core/db/web adapter, orchestration service |
| T3 | Model 백엔드 branch/commit 확장 | UI core/db/web adapter, diff service |
| T4 | 프론트 공통 타입/API/컴포넌트 확장 | `nori-tc-ui` type/api/store/ui primitive |
| T5 | EQP page UI 구현 | EQP sidebar/page/modal/hook |
| T6 | Model page UI 구현 | Model sidebar/page/modal/hook |
| T7 | 테스트 및 acceptance 검증 | backend scenario/unit + frontend interaction |

---

## T1. DB 스키마 및 enum/도메인 확장

### 목적

EQP/Model 신규 관리 기능에 필요한 DB 구조와 공통 타입을 확장합니다.

### 작업 내용

#### T1-1. DDL 컬럼 및 제약 변경

- [x] `postgres_create_all_table.sql`의 `tc_model`에 `parent_model VARCHAR(128) NULL` 추가
- [x] `parent_model`을 `maker` 오른쪽 컬럼 위치에 배치
- [x] `parent_model -> tc_model.model_name` self FK 추가
- [x] self FK 삭제 정책을 `ON DELETE CASCADE`로 반영
- [x] `postgres_create_all_table.sql`의 `tc_eqp`에 `is_dev BOOLEAN NOT NULL DEFAULT FALSE` 추가
- [x] `is_dev`를 `comm_mode` 오른쪽 컬럼 위치에 배치
- [x] `tc_eqp` 관련 check/insert/update SQL에 `is_dev` 반영

#### T1-2. enum 저장값 전환

- [x] `ProtocolType` DB check constraint를 `SECS`, `SOCKET` 기준으로 수정
- [x] `ModelStatus` DB check constraint를 `DEVELOP`, `OPERATE`, `DEPRECATED` 기준으로 수정
- [x] `tc_eqp_state` 초기 상태 요구사항 반영이 가능하도록 제약 허용값 검토 및 수정
- [x] `tc_eqp_state`와 관련 enum/매핑 값이 신규 초기 상태값을 허용하는지 확인

#### T1-3. sample data 반영

- [x] `postgres_insert_sample_data.sql`에 `parent_model` 기본값 반영
- [x] `postgres_insert_sample_data.sql`에 `is_dev` 기본값 반영
- [x] sample data의 protocol 저장값을 `SECS/SOCKET`로 정리
- [x] sample data의 model status 저장값을 `DEVELOP/OPERATE/DEPRECATED`로 정리

#### T1-4. DB domain 확장

- [x] `db-domain`의 `ProtocolType` enum 값을 `SECS`, `SOCKET`으로 변경
- [x] `db-domain`의 `ModelStatus` enum 값을 `DEVELOP`, `OPERATE`, `DEPRECATED`로 변경
- [x] `TcModel`에 `parentModel` 필드 추가
- [x] `TcEqp`에 `isDev` 필드 추가

#### T1-5. JPA/MyBatis 매핑 확장

- [x] `tc_model`, `tc_model_version` 관련 JPA entity/mapper/store에 `parentModel` 반영
- [x] `tc_eqp` 관련 JPA entity/mapper/store에 `isDev` 반영
- [x] enum 저장값 변경에 맞게 JPA enum mapping 확인
- [x] enum 저장값 변경에 맞게 MyBatis XML/resultMap/upsert SQL 확인
- [x] `tc_eqp_state` 관련 enum/string 매핑 경로 검토

### T1 검증

- [x] 신규 컬럼이 DDL, sample SQL, domain, JPA, MyBatis에 누락 없이 반영되었는지 확인
- [x] `parent_model` self FK가 root/branch 구조에서 동작하는지 확인
- [x] `ProtocolType`, `ModelStatus` 저장값 변경 후 기존 조회/저장 경로가 깨지지 않는지 확인
- [x] `is_dev`가 EQP 조회 응답까지 전달되는지 확인

---

## T2. EQP 백엔드 CRUD/조회/동기화 확장

### 목적

EQP create/update/delete와 관리 modal 조회를 지원하는 백엔드 계약을 추가합니다.

### 작업 내용

#### T2-1. EQP 관리 Port 정의

- [x] `tc-ui-core`에 EQP CRUD Port 추가
- [x] `tc-ui-core`에 EQP 관리 상세 Query Port 추가
- [x] `tc-ui-core`에 EQP 옵션 Query Port 추가
- [x] EQP create/update/delete 요청 모델에 `isDev` 반영
- [x] EQP 관리 상세 응답 모델에 공통/SECS/Socket/log/jar/model/param 정보를 정의

#### T2-2. EQP DB adapter 구현

- [x] UI DB adapter에 EQP CRUD Port 구현체 추가
- [x] UI DB adapter에 EQP 관리 상세 Query 구현체 추가
- [x] UI DB adapter에 EQP 옵션 Query 구현체 추가
- [x] `tc_eqp`, `tc_eqp_hsms`, `tc_eqp_socket`, `tc_eqp_log`, `tc_eqp_state`, `tc_jar_gateway`, `tc_jar_business`를 한 흐름으로 조회하는 구조 정의
- [x] socket protocol type 전체 목록 조회 경로 연결
- [x] gateway/business jar filename distinct 조회 경로 연결

#### T2-3. EQP orchestration service 구현

- [x] create/update/delete 공통 orchestration service 추가
- [x] DB 저장 전 입력 검증 분기 정의
- [x] DB 저장 후 Gateway/Business runtime 동기화 호출 연결
- [x] create 실패 시 DB 롤백 정책 구현
- [x] update 실패 시 이전 스냅샷 복구 정책 구현
- [x] delete 실패 시 삭제 전 스냅샷 복구 정책 구현

#### T2-4. EQP 생성 규칙 구현

- [x] `SECS Eqp Create` 기본값 반영
- [x] `Socket Eqp Create` 기본값 반영
- [x] create 시 `tc_eqp_state` 초기 row 생성 구현
- [x] create 시 `tc_eqp_log` 초기 row 생성 구현
- [x] create 시 `modelVersionKey` 필수 연결 규칙 반영
- [x] create 시 `enabled=true` 기본값 반영

#### T2-5. EQP 수정 규칙 구현

- [x] `Eqp Info Update` 요청 모델에 수정 가능 필드만 반영
- [x] `eqp_id`, `comm_interface` 읽기 전용 정책 유지
- [x] `is_dev` 변경 허용 정책 반영
- [x] `is_dev`와 현재 model status 불일치 시 저장 차단
- [x] jar filename 변경 시 후속 runtime reload 필요 여부 판단 로직 반영

#### T2-6. EQP 삭제 규칙 구현

- [x] delete 전에 `EQP_END` 선행 호출 구현
- [x] 이미 종료 상태면 성공으로 간주하는 분기 구현
- [x] 종료 실패/타임아웃이면 삭제 중단
- [x] DB 삭제 후 runtime sync 호출 구현
- [x] delete rollback 시 관련 하위 row까지 복구되도록 구현

#### T2-7. jar / 옵션 정책 구현

- [x] jar dropdown은 filename만 응답하도록 API 설계
- [x] 동일 filename 다중 row일 때 최신 `updated_at` row 선택 정책 반영
- [x] jar 미선택 시 기존값 유지/미생성 정책 반영
- [x] socket protocol dropdown이 전체 목록을 반환하는지 구현

#### T2-8. Web controller/API 확장

- [x] `GET /api/eqp/{eqpId}/manage` 추가
- [x] `GET /api/eqp/options` 추가
- [x] 기존 `POST /api/eqp`를 DB insert + runtime sync 의미로 확장
- [x] 기존 `PUT /api/eqp/{eqpId}`를 DB update + runtime sync 의미로 확장
- [x] 기존 `DELETE /api/eqp/{eqpId}`를 END 선행 + DB delete + runtime sync 의미로 확장
- [x] EQP 응답 DTO에 `isDev` 추가

### T2 검증

- [x] EQP create 시 공통/SECS/Socket 저장이 모두 반영되는지 확인
- [x] EQP create 시 `tc_eqp_state`, `tc_eqp_log` 초기 row가 생성되는지 확인
- [x] EQP update 시 `is_dev`와 model status 검증이 동작하는지 확인
- [x] EQP delete 시 END 선행 실패이면 삭제가 중단되는지 확인
- [x] runtime sync 실패 시 create/update/delete 보상 로직이 동작하는지 확인
- [x] jar dropdown 선택 결과가 filename 기준으로 올바른 row를 복사하는지 확인

---

## T3. Model 백엔드 branch/commit 확장

### 목적

root model, branch model, parent commit을 지원하는 백엔드 API와 서비스를 추가합니다.

### 작업 내용

#### T3-1. Model 관리 Port 정의

- [x] `tc-ui-core`에 root model 관리 Port 추가
- [x] `tc-ui-core`에 branch model 생성/삭제 Port 추가
- [x] `tc-ui-core`에 parent commit/diff Port 추가
- [x] `tc-ui-core`의 Model 응답 모델에 `parentModel` 추가

#### T3-2. root model 생성/수정 API 구현

- [x] `POST /api/model/roots` 추가
- [x] `PUT /api/model/{modelKey}/info` 추가
- [x] root model 생성 시 `parent_model = NULL` 고정
- [x] root model 생성 시 `model_version = EDIT`, `status = OPERATE` 고정
- [x] Model Info Update에서 `modelName` 불변 정책 반영

#### T3-3. branch model 생성 구현

- [x] `POST /api/model/{modelKey}/branches` 추가
- [x] branch model 이름 규칙 `{parent}_{suffix}_{userId}` 반영
- [x] branch model 생성 시 `parent_model = parent model name` 반영
- [x] branch 초기 version을 `EDIT/DEVELOP`로 생성
- [x] parent 최신 version 전체 clone 서비스 구현

#### T3-4. parent commit diff 구현

- [x] `POST /api/model/{modelKey}/commit-parent` 추가
- [x] branch 최신 version vs parent 최신 version diff 계산 구조 정의
- [x] `model-parameter` diff 계산 구현
- [x] `secs-message` diff 계산 구현
- [x] `socket-message` diff 계산 구현
- [x] `variableides/reportides/eventides` diff 계산 구현
- [x] `workflow` diff 계산 구현
- [x] `mdf` diff 계산 구현
- [x] `dcop-itemes` diff 계산 구현
- [x] 추가/변경/삭제 모두 diff 결과에 포함

#### T3-5. parent commit 반영 구현

- [x] 사용자 입력 새 parent version을 받는 요청 모델 정의
- [x] parent 새 version 생성 로직 구현
- [x] diff 결과를 parent 새 version에 반영
- [x] commit 완료 후 branch의 모든 version status를 `DEPRECATED`로 변경

#### T3-6. 삭제/정리 정책 구현

- [x] `DELETE /api/model/{modelKey}/branches/deprecated` 추가
- [x] deprecated branch bulk delete 로직 구현
- [x] `DELETE /api/model/{modelKey}`에 parent cascade delete 정책 반영
- [x] EQP 참조 중 model version 존재 시 `409 CONFLICT` 반환
- [x] branch model delete API/서비스 구현
  - 기존 `DELETE /api/model/{modelVersionKey}`와 경로 충돌을 피하기 위해 `scope=model` 쿼리 파라미터로 model 단위 삭제를 구분

### T3 검증

- [x] root model 생성 시 `parent_model = NULL`과 `EDIT/OPERATE`가 적용되는지 확인
- [x] branch model 생성 시 parent 최신 version 전체 clone이 누락 없이 수행되는지 확인
- [x] parent commit diff가 추가/변경/삭제를 모두 산출하는지 확인
- [x] parent commit 후 branch 전체 status가 `DEPRECATED`로 바뀌는지 확인
- [x] parent delete 시 branch cascade와 EQP 참조 충돌 정책이 함께 동작하는지 확인

---

## T4. 프론트 공통 타입/API/컴포넌트 확장

### 목적

EQP/Model 신규 관리 UI가 사용할 공통 타입, API, UI primitive를 준비합니다.

### 작업 내용

#### T4-1. 타입 전환

- [x] 프론트 `ProtocolType` literal을 `SECS`, `SOCKET`으로 전환
- [x] 프론트 `ModelStatus` literal을 `DEVELOP`, `OPERATE`, `DEPRECATED`로 전환
- [x] 프론트 `ModelInfo`에 `parentModel` 추가
- [x] 프론트 `EqpInfo`에 `isDev` 추가

#### T4-2. API 계층 확장

- [x] EQP manage API 추가
- [x] EQP options API 추가
- [x] EQP create/update 요청 DTO에 `isDev` 반영
- [x] Model root/branch/commit API 추가
- [x] deprecated branch bulk delete API 추가

#### T4-3. 공통 UI primitive 추가

- [x] `Select` primitive 추가
- [x] `DropdownMenu` primitive 추가
- [x] `ContextMenu` primitive 추가
- [x] `ConfirmDialog` primitive 추가

#### T4-4. query/cache 정책 정리

- [x] EQP create/update/delete 후 invalidate 범위 정의
- [x] Model create/update/branch/commit/delete 후 invalidate 범위 정의
- [x] branch commit 후 sidebar/detail/tab 상태 정리 규칙 정의

### T4 검증

- [x] 기존 page와 충돌 없이 신규 타입이 연결되는지 확인
- [ ] EQP/Model 양쪽에서 context menu와 select가 공통으로 재사용되는지 확인
- [ ] create/update/delete/commit 후 query invalidate가 누락 없이 동작하는지 확인

---

## T5. EQP page UI 구현

### 목적

EQP sidebar/context menu/modal 기반 관리 기능을 화면에 반영합니다.

### 작업 내용

#### T5-1. Sidebar 우클릭 메뉴

- [ ] EQP 노드에 context menu 추가
- [ ] EQP 노드 메뉴 `Eqp Info Update` 추가
- [ ] EQP 노드 메뉴 `Model Info Update` 추가
- [ ] EQP 노드 메뉴 `Eqp Parameter Update` 추가
- [ ] EQP 노드 메뉴 `Eqp Delete` 추가
- [ ] Gateway 루트에 context menu 추가
- [ ] Gateway 메뉴 `SECS Eqp Create` 추가
- [ ] Gateway 메뉴 `Socket Eqp Create` 추가

#### T5-2. Eqp create/update modal

- [ ] `EqpManageFormModal` 생성
- [ ] create 모드에서 읽기 전용 없는 공통 폼 구성
- [ ] update 모드에서 `eqp_id`, `comm_interface` 읽기 전용 처리
- [ ] create 모드에서 `comm_mode` 선택 UI 추가
- [ ] create/update 모드에서 `is_dev` 토글 UI 추가
- [ ] SECS 전용 필드 섹션 추가
- [ ] Socket 전용 필드 섹션 추가
- [ ] Gateway Jar dropdown 연결
- [ ] Socket Protocol Type dropdown 연결

#### T5-3. Model Info Update modal

- [ ] `EqpModelBindingModal` 생성
- [ ] model name dropdown 연결
- [ ] model version dropdown 연결
- [ ] business jar dropdown 연결
- [ ] `is_dev` 기준 model/version 필터 적용

#### T5-4. Eqp Parameter Update modal

- [ ] `EqpParamVersionModal` 생성
- [ ] 현재 적용 version/description 표시
- [ ] 변경 대상 param version dropdown 연결
- [ ] 선택 버전 description 연동 표시
- [ ] Save/Cancel 동작 구현

#### T5-5. Delete confirm UI

- [ ] `EqpDeleteConfirmDialog` 생성
- [ ] 삭제 전 확인 문구 표시
- [ ] delete pending 상태 UI 처리
- [ ] 성공 후 선택 상태 초기화 처리

#### T5-6. 저장 후 상태 갱신

- [ ] EQP 목록 invalidate
- [ ] EQP 상세 invalidate
- [ ] EQP runtime state invalidate
- [ ] EQP param version invalidate
- [ ] EQP manage/options invalidate

### T5 검증

- [ ] EQP 노드 우클릭 시 4개 메뉴가 노출되는지 확인
- [ ] Gateway 우클릭 시 2개 create 메뉴가 노출되는지 확인
- [ ] SECS create에서 기본값이 자동 반영되는지 확인
- [ ] Socket create에서 기본값이 자동 반영되는지 확인
- [ ] update modal에서 `eqp_id`, `comm_interface`가 읽기 전용인지 확인
- [ ] `is_dev` 기준으로 model/version dropdown 목록이 달라지는지 확인
- [ ] delete 성공 후 목록과 선택 상태가 즉시 갱신되는지 확인

---

## T6. Model page UI 구현

### 목적

parent/branch 트리와 branch commit 관리 UI를 구현합니다.

### 작업 내용

#### T6-1. Sidebar 트리 재구성

- [ ] 기존 대표 1행 sidebar 구조 제거
- [ ] `SECS -> parent -> branch` 트리 구조 구현
- [ ] `Socket -> parent -> branch` 트리 구조 구현
- [ ] root/branch/deprecated 상태 표시 추가

#### T6-2. Sidebar 우클릭 메뉴

- [ ] SECS 루트에 `SECS Model Create` 메뉴 추가
- [ ] Socket 루트에 `Socket Model Create` 메뉴 추가
- [ ] root model에 `Model Info Update` 메뉴 추가
- [ ] root model에 `Branch Model Create` 메뉴 추가
- [ ] root model에 `Branch Deprecated Model Delete` 메뉴 추가
- [ ] root model에 `Model Delete` 메뉴 추가
- [ ] branch model에 `Parent Model Commit` 메뉴 추가
- [ ] branch model에 `Branch Model Delete` 메뉴 추가

#### T6-3. Model create/update modal

- [ ] `ModelCreateOrUpdateModal` 생성
- [ ] root create에서 `Model Name`, `Maker` 입력 UI 구현
- [ ] update 모드에서 `Model Name` 읽기 전용 처리
- [ ] SECS/Socket root create 분기 처리

#### T6-4. Branch create modal

- [ ] `BranchModelCreateModal` 생성
- [ ] 부모 model 고정값 표시
- [ ] suffix 입력 UI 추가
- [ ] 현재 로그인 `userId` 표시 또는 내부 반영
- [ ] 최종 model name preview 표시

#### T6-5. Parent commit modal

- [ ] `ParentModelCommitModal` 생성
- [ ] 새 parent version 입력 UI 추가
- [ ] diff 섹션별 렌더링 구성
- [ ] 추가 항목 렌더링
- [ ] 변경 항목 렌더링
- [ ] 삭제 항목 렌더링
- [ ] Commit/Cancel 동작 구현

#### T6-6. Delete/정리 dialog

- [ ] `ModelDeleteConfirmDialog` 생성
- [ ] branch delete confirm 흐름 구현
- [ ] parent delete confirm 흐름 구현
- [ ] deprecated branch bulk delete confirm 흐름 구현

#### T6-7. 기존 편집 흐름 공존

- [ ] 기존 `Check Out / Check In` 상세 흐름 유지
- [ ] branch 관리 액션과 기존 편집 상태 충돌 방지
- [ ] branch commit 후 상세 탭/선택 상태 정리 규칙 반영

### T6 검증

- [ ] parent/branch 트리가 현재 목록 기준으로 안정적으로 구성되는지 확인
- [ ] root model 우클릭 메뉴와 branch model 우클릭 메뉴가 다르게 노출되는지 확인
- [ ] branch model 이름 preview가 `{parent}_{suffix}_{userId}` 형식인지 확인
- [ ] parent commit modal이 추가/변경/삭제 diff를 모두 표시하는지 확인
- [ ] 기존 Model `Check Out / Check In` 흐름이 그대로 동작하는지 확인

---

## T7. 테스트 및 acceptance 검증

### 목적

문서 기준 요구사항이 실제 구현 결과로 충족되는지 검증합니다.

### 작업 내용

#### T7-1. 백엔드 단위/통합 테스트

- [ ] schema/domain 매핑 테스트 추가
- [ ] enum 저장값 전환 회귀 테스트 추가
- [ ] EQP create 테스트 추가
- [ ] EQP update 테스트 추가
- [ ] EQP delete 테스트 추가
- [ ] EQP rollback/보상 테스트 추가
- [ ] EQP options/manage 조회 테스트 추가
- [ ] Model root create/update 테스트 추가
- [ ] Model branch create/clone 테스트 추가
- [ ] parent commit diff/commit 테스트 추가
- [ ] deprecated branch bulk delete 테스트 추가
- [ ] model delete `409` 테스트 추가

#### T7-2. 시나리오 테스트

- [ ] EQP create -> DB 저장 -> runtime sync 성공 흐름 검증
- [ ] EQP update -> jar 변경 -> reload 흐름 검증
- [ ] EQP delete -> END 선행 -> delete 성공 흐름 검증
- [ ] root model create/update/delete 흐름 검증
- [ ] branch create -> commit -> deprecated 전환 흐름 검증
- [ ] EQP 참조 중 model delete `409` 흐름 검증

#### T7-3. 프론트 검증

- [ ] sidebar context menu 노출 조건 검증
- [ ] create/update modal 기본값/읽기 전용/검증 규칙 검증
- [ ] `is_dev` 기반 dropdown 필터 검증
- [ ] parent/branch 트리 렌더링 검증
- [ ] commit diff UI 렌더링 검증
- [ ] 성공 후 query invalidate 검증

### T7 acceptance criteria

- [ ] EQP page에서 생성/수정/삭제/model 연결/parameter 버전 변경이 가능해야 함
- [ ] Model page에서 root create, branch create, parent commit, deprecated branch 정리, 삭제가 가능해야 함
- [ ] `is_dev`와 model status 규칙 위반은 저장 전에 차단되어야 함
- [ ] parent commit은 삭제 diff까지 반영해야 함
- [ ] 기존 Model `Check Out / Check In` 흐름은 계속 동작해야 함

---

## 완료 기준

- [ ] 스키마/enum/type 변경이 DB부터 프론트까지 일관되게 반영됨
- [ ] EQP CRUD가 DB 저장과 runtime 동기화를 함께 처리함
- [ ] Model branch/commit 관리가 parent/branch 구조로 동작함
- [ ] 신규 API/DTO/프론트 타입에 `parentModel`, `isDev`가 반영됨
- [ ] 문서 기준 acceptance criteria를 테스트로 확인함

---

## 가정

- `comm_mode`는 EQP 생성 시만 선택하고 update modal에서는 변경하지 않습니다.
- branch 생성 시 parent 최신 version의 상세 전체를 clone합니다.
- jar dropdown은 filename만 표시하고 실제 저장은 최신 row를 원본으로 사용합니다.
- EQP create 시 `enabled=true`를 기본값으로 사용합니다.
