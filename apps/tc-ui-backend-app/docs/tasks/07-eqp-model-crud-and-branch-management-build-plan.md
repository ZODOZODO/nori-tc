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

#### 1. 스키마 변경

- `tc_model.parent_model` 추가
- `tc_eqp.is_dev` 추가
- `ProtocolType` check constraint를 `SECS`, `SOCKET` 기준으로 전환
- `ModelStatus` check constraint를 `DEVELOP`, `OPERATE`, `DEPRECATED` 기준으로 전환
- EQP 초기 상태값 반영을 위해 `tc_eqp_state` 관련 제약/enum 확장

#### 2. 샘플 데이터 반영

- sample SQL에 `parent_model`, `is_dev` 기본값 추가
- enum 저장값 전환 반영

#### 3. 도메인/매퍼 반영

- `TcModel`에 `parentModel` 추가
- `TcEqp`에 `isDev` 추가
- JPA entity / repository / mapper / store 반영
- MyBatis XML / mapper / store 반영

### 검증 포인트

- 신규 컬럼이 모든 조회/저장 경로에서 누락 없이 매핑되는지 확인
- enum 저장값 변경 후 기존 CRUD 경로가 같은 값을 참조하는지 확인
- `parent_model` self FK cascade 정책이 의도대로 동작하는지 확인

---

## T2. EQP 백엔드 CRUD/조회/동기화 확장

### 목적

EQP create/update/delete와 관리 modal 조회를 지원하는 백엔드 계약을 추가합니다.

### 작업 내용

#### 1. Port/서비스 추가

- EQP CRUD Port 추가
- EQP 관리 상세 Query Port 추가
- EQP 옵션 Query Port 추가
- EQP CRUD orchestration service 추가

#### 2. EQP 관리 조회 API

- `GET /api/eqp/{eqpId}/manage`
- `GET /api/eqp/options`

관리 상세 응답 포함 항목:

- 공통 EQP 정보
- SECS/Socket 분기 설정
- log 정책
- gateway/business jar filename
- 현재 연결 model/version
- 현재 적용 parameter version/description

#### 3. EQP 저장/삭제 처리

- 기존 `POST /api/eqp`를 DB insert + runtime sync 의미로 확장
- 기존 `PUT /api/eqp/{eqpId}`를 DB update + runtime sync 의미로 확장
- 기존 `DELETE /api/eqp/{eqpId}`를 END 선행 + DB delete + runtime sync 의미로 확장

#### 4. 보상/롤백 처리

- create 실패 시 DB insert 롤백
- update 실패 시 이전 스냅샷 복구
- delete 실패 시 삭제 전 스냅샷 복구
- jar 변경 시 `EQP_UPDATE_JARFILE` 후속 발행

#### 5. 검증 규칙

- `is_dev`와 선택 model status 정합성 검증
- EQP delete 전 runtime 종료 실패 시 삭제 차단
- 중복 `eqp_id`/잘못된 route partition/잘못된 socket protocol 검증

### 검증 포인트

- DB 저장과 DualResponse 동기화 결과가 분리되지 않고 하나의 성공/실패로 귀결되는지 확인
- `tc_eqp_state`, `tc_eqp_log` 초기 row가 생성되는지 확인
- jar dropdown 선택 결과가 filename 기준으로 올바른 row를 복사하는지 확인

---

## T3. Model 백엔드 branch/commit 확장

### 목적

root model, branch model, parent commit을 지원하는 백엔드 API와 서비스를 추가합니다.

### 작업 내용

#### 1. 신규 API 추가

- `POST /api/model/roots`
- `PUT /api/model/{modelKey}/info`
- `POST /api/model/{modelKey}/branches`
- `POST /api/model/{modelKey}/commit-parent`
- `DELETE /api/model/{modelKey}/branches/deprecated`
- `DELETE /api/model/{modelKey}`

#### 2. branch 생성 서비스

- root model 기준 branch model 생성
- `{parent}_{suffix}_{userId}` 규칙으로 model name 생성
- parent 최신 version 전체 clone
- 초기 branch version을 `EDIT/DEVELOP`로 생성

#### 3. parent commit 서비스

- parent 최신 version vs branch 최신 version diff 생성
- 추가/변경/삭제 diff 모두 산출
- 사용자가 입력한 새 parent version으로 새 row 생성
- branch 전체 version status를 `DEPRECATED`로 전환

#### 4. 삭제 정책 반영

- deprecated branch bulk delete
- parent delete 시 branch cascade delete
- EQP 참조 중 model version이 있으면 `409 CONFLICT`

### 검증 포인트

- branch 생성 시 상세 테이블 clone이 누락 없이 수행되는지 확인
- commit diff가 삭제 항목까지 포함하는지 확인
- parent delete가 branch cascade와 EQP 참조 충돌 정책을 모두 만족하는지 확인

---

## T4. 프론트 공통 타입/API/컴포넌트 확장

### 목적

EQP/Model 신규 관리 UI가 사용할 공통 타입, API, UI primitive를 준비합니다.

### 작업 내용

#### 1. 타입/API 확장

- `ProtocolType` TS literal 전환 (`SECS`, `SOCKET`)
- `ModelStatus` TS literal 전환 (`DEVELOP`, `OPERATE`, `DEPRECATED`)
- `ModelInfo.parentModel` 추가
- `EqpInfo.isDev` 추가
- EQP manage/options API 추가
- Model branch/commit API 추가

#### 2. 공통 UI primitive 추가

- `Select`
- `DropdownMenu`
- `ContextMenu`
- `ConfirmDialog`

#### 3. 캐시 정책 정리

- create/update/delete/commit 성공 시 관련 query invalidate 범위 명확화

### 검증 포인트

- 기존 page와 충돌 없이 신규 타입이 연결되는지 확인
- 우클릭 메뉴와 드롭다운 primitive가 EQP/Model 양쪽에서 재사용 가능한지 확인

---

## T5. EQP page UI 구현

### 목적

EQP sidebar/context menu/modal 기반 관리 기능을 화면에 반영합니다.

### 작업 내용

#### 1. Sidebar 우클릭 메뉴

- EQP 노드: `Eqp Info Update`, `Model Info Update`, `Eqp Parameter Update`, `Eqp Delete`
- Gateway 루트: `SECS Eqp Create`, `Socket Eqp Create`

#### 2. modal 구현

- `EqpManageFormModal`
- `EqpModelBindingModal`
- `EqpParamVersionModal`
- `EqpDeleteConfirmDialog`

#### 3. 폼 규칙 적용

- SECS/Socket 필드 분기
- create vs update 읽기 전용 차이 반영
- `is_dev` 기반 model/version dropdown 필터 적용
- jar filename dropdown 표시

#### 4. 저장 후 상태 반영

- 목록/상세/runtime/param/version 캐시 무효화
- 삭제 시 선택 상태 정리

### 검증 포인트

- 우클릭 진입과 modal 오픈이 정확한 노드에서만 노출되는지 확인
- create/update/delete 후 화면 상태가 즉시 최신 데이터로 갱신되는지 확인
- model/version 드롭다운이 `is_dev` 규칙을 정확히 반영하는지 확인

---

## T6. Model page UI 구현

### 목적

parent/branch 트리와 branch commit 관리 UI를 구현합니다.

### 작업 내용

#### 1. Sidebar 재구성

- `SECS/Socket -> parent -> branch` 트리 구조로 변경
- root/branch/deprecated 상태 표시 추가

#### 2. 우클릭 메뉴 구현

- root model:
  - `Model Info Update`
  - `Branch Model Create`
  - `Branch Deprecated Model Delete`
  - `Model Delete`
- branch model:
  - `Parent Model Commit`
  - `Branch Model Delete`
- SECS/Socket 루트:
  - `SECS Model Create`
  - `Socket Model Create`

#### 3. modal 구현

- `ModelCreateOrUpdateModal`
- `BranchModelCreateModal`
- `ParentModelCommitModal`
- `ModelDeleteConfirmDialog`

#### 4. 기존 편집 흐름 공존

- 기존 `Check Out / Check In` 탭/상세 흐름 유지
- branch 관리 기능과 충돌하지 않도록 선택 상태/액션 상태 분리

### 검증 포인트

- parent/branch 트리가 현재 목록 데이터로 안정적으로 구성되는지 확인
- branch 생성 이름 규칙과 commit version 입력이 UI에 정확히 반영되는지 확인
- commit modal diff가 추가/변경/삭제를 모두 보여주는지 확인

---

## T7. 테스트 및 acceptance 검증

### 목적

문서 기준 요구사항이 실제 구현 결과로 충족되는지 검증합니다.

### 작업 내용

#### 1. 백엔드 테스트

- schema/domain 매핑 테스트
- EQP CRUD + rollback 테스트
- Model branch/commit/delete 테스트
- jar/socket protocol/options 조회 테스트
- 상태/enum 전환 회귀 테스트

#### 2. 시나리오 테스트

- EQP create/update/delete 전체 흐름
- EQP model/parameter 연결 변경 흐름
- root model create/update/delete 흐름
- branch create/commit/deprecated bulk delete 흐름
- EQP 참조 중 model delete `409` 흐름

#### 3. 프론트 검증

- sidebar context menu 노출 조건
- modal 입력/검증/초기값
- 성공 후 query invalidate
- parent/branch 트리 렌더링

### acceptance criteria

- EQP page에서 생성/수정/삭제/model 연결/parameter 버전 변경이 가능해야 함
- Model page에서 root create, branch create, parent commit, deprecated branch 정리, 삭제가 가능해야 함
- `is_dev`와 model status 규칙 위반은 저장 전에 차단되어야 함
- parent commit은 삭제 diff까지 반영해야 함
- 기존 Model `Check Out / Check In` 흐름은 계속 동작해야 함

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
