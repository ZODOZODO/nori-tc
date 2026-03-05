> 작성일: 2026-03-05

# tc-ui-backend-app UI 관리 페이지 확장 구현 계획 (T02)

## 진행 방법
- `01` 문서는 원본 상태를 유지하고, 본 문서에서 확장 작업만 관리합니다.
- 체크박스는 실제 구현 완료 시 즉시 갱신합니다.
- 의존 순서는 `문서 확정 → Core/Store 계약 → Adapter 구현 → 권한/seed 반영 → 테스트`로 고정합니다.
- 상세 아키텍처는 `docs/design/02-ui-management-pages-design.md`를 기준으로 합니다.

---

## 문서 반영 완료 항목
- [x] `01-system-architecture.md` 원복
- [x] `01-initial-build-plan.md` 원복
- [x] 확장 설계 문서 신규 생성: `02-ui-management-pages-design.md`
- [x] 확장 작업 계획 문서 신규 생성: `02-ui-management-pages-build-plan.md`

---

## Phase 1: API 계약 및 코어 인터페이스 확장

### 목표
- UI 관리 페이지 CRUD를 지원하는 기술 중립 Port/계약을 정의합니다.

### 작업
- [x] `libs/ui/tc-ui-core`에 Eqp 조회 Port 추가 (`GET /api/eqp`, `GET /api/eqp/{eqpId}`)
- [x] `libs/ui/tc-ui-core`에 Model CRUD Port 추가
- [x] `libs/ui/tc-ui-core`에 User CRUD Port 추가
- [x] `libs/ui/tc-ui-core`에 Group CRUD Port 추가
- [x] `libs/ui/tc-ui-core`에 Permission CRUD Port 추가
- [x] `libs/ui/tc-ui-core`에 User-Group 매핑 Port 추가
- [x] `libs/ui/tc-ui-core`에 Group-Permission 매핑 Port 추가
- [x] 공통 목록 응답 계약 `PagedResponse<T>` 정의

### DB Store 계약 보강
- [x] `libs/db/tc-db-core/.../TcUserInfoStore`에 `findAll(PageRequest)` 추가

---

## Phase 2: DB Adapter 구현

### 목표
- `tc-db-core` Store 기반으로 확장 Port 구현체를 추가합니다.

### 작업
- [x] Eqp 조회 Port 구현체 추가
- [x] Model CRUD Port 구현체 추가
- [x] User CRUD Port 구현체 추가
- [x] Group CRUD Port 구현체 추가
- [x] Permission CRUD Port 구현체 추가
- [x] User-Group 매핑 Port 구현체 추가
- [x] Group-Permission 매핑 Port 구현체 추가
- [x] 삭제/충돌 정책 반영 (`409`/`400`)

### 정책 반영 체크
- [x] `tc_user_info` 삭제 전 `tc_ui_auth_session` 정리 절차 강제
- [x] `tc_model_version` 참조 충돌 시 `409 CONFLICT` 반환
- [x] 매핑 테이블(`tc_user_group_member`, `tc_user_group_permission`) 물리 삭제

---

## Phase 3: Web Adapter (Controller/DTO) 확장

### 목표
- 단수형 경로 규칙으로 CRUD API를 제공합니다.

### 컨트롤러 추가
- [x] `ModelController` (`/api/model/**`)
- [x] `UserController` (`/api/user/**`)
- [x] `GroupController` (`/api/group/**`)
- [x] `PermissionController` (`/api/permission/**`)

### Eqp 조회 API 추가
- [x] `GET /api/eqp`
- [x] `GET /api/eqp/{eqpId}`
- [x] 기존 `POST/PUT/DELETE/start/end /api/eqp/**`와 공존 확인

### Model Info API
- [x] `GET /api/model`
- [x] `GET /api/model/{modelVersionKey}`
- [x] `POST /api/model`
- [x] `PUT /api/model/{modelVersionKey}`
- [x] `DELETE /api/model/{modelVersionKey}`

### User Info API
- [x] `GET /api/user`
- [x] `GET /api/user/{userPk}`
- [x] `POST /api/user`
- [x] `PUT /api/user/{userPk}`
- [x] `DELETE /api/user/{userPk}`
- [x] `POST /api/user/{userPk}/group/{groupId}`
- [x] `DELETE /api/user/{userPk}/group/{groupId}`
- [x] `POST /api/user/{userPk}/password/reset`

### Group Info API
- [x] `GET /api/group`
- [x] `GET /api/group/{groupId}`
- [x] `POST /api/group`
- [x] `PUT /api/group/{groupId}`
- [x] `DELETE /api/group/{groupId}`
- [x] `GET /api/group/{groupId}/permission`
- [x] `POST /api/group/{groupId}/permission/{permId}`
- [x] `DELETE /api/group/{groupId}/permission/{permId}`

### UI Permission API
- [x] `GET /api/permission`
- [x] `GET /api/permission/{permId}`
- [x] `POST /api/permission`
- [x] `PUT /api/permission/{permId}`
- [x] `DELETE /api/permission/{permId}`

---

## Phase 4: RBAC 및 Seed 반영

### 목표
- 신규 API 경로에 대한 권한 정책을 데이터 기준으로 고정합니다.

### 작업
- [ ] `docs/db_table/sample_data/postgres_insert_sample_data.sql`에 `MODEL_WRITE` 추가
- [ ] `docs/db_table/sample_data/postgres_insert_sample_data.sql`에 `PERMISSION_WRITE` 추가
- [ ] `/api/permission` 리소스 권한 매핑 반영
- [ ] ADMIN/DEVELOPER/OPERATOR 기본 권한 매핑 재점검

---

## Phase 5: 시나리오 테스트 확장

### 인증/인가 기본 검증
- [ ] 미인증 요청 401
- [ ] 권한 부족 403

### 기능 검증
- [ ] 로그인 성공 후 front 기본 진입 페이지 `EqpInfo` 검증
- [ ] `GET /api/eqp` + 기존 명령 API 공존 검증
- [ ] Model CRUD 성공/실패/중복/참조충돌(409) 검증
- [ ] User CRUD + 비밀번호 초기화 + 사용자-그룹 매핑 CRUD 검증
- [ ] Group CRUD + 그룹-권한 매핑 CRUD 검증
- [ ] UI Permission CRUD + 권한 캐시 재로딩 검증
- [ ] 물리 삭제 시 FK 제약 충돌 에러코드(409/400) 검증
- [ ] 목록 API `offset/limit` 기본값/상한 검증

---

## 완료 기준 (Definition of Done)
- [ ] 설계 문서(D02)와 구현 결과가 일치함
- [ ] 단수형 경로 규칙(`/api/model`, `/api/user`, `/api/group`, `/api/permission`)이 일관됨
- [ ] Eqp 변경 API는 기존 명령 방식을 유지하고 조회 API만 추가됨
- [ ] 물리 삭제/충돌 정책이 API 응답 코드에 반영됨
- [ ] 신규 테스트가 CI에서 통과함
