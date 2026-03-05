> 작성일: 2026-03-05

# tc-ui-backend-app UI 관리 페이지 확장 아키텍처 설계 (D02)

## 목적
- `01-system-architecture.md`의 기준 아키텍처를 유지하면서 UI 관리 페이지 확장 요구사항만 별도 설계합니다.
- 로그인 후 기본 진입 페이지, 신규 CRUD API 계약, RBAC 확장, 삭제/충돌 정책을 명확히 정의합니다.

## 범위
- 본 문서는 확장 설계 전용 문서입니다.
- 코드/테스트 구현 자체는 포함하지 않습니다.
- Front 저장소가 현재 워크스페이스에 없으므로 페이지 라우팅은 UI-backend 연동 계약 기준으로 정의합니다.

## 로그인 후 라우팅 및 페이지 범위
- 로그인 성공 후 기본 진입 경로는 `EqpInfo`로 고정합니다.

| 페이지 | 목적 | 연동 API |
|------|------|----------|
| `EqpInfo` | 설비 전체 조회 + 설비 제어/변경 | `GET /api/eqp`, `GET /api/eqp/{eqpId}` + 기존 `POST/PUT/DELETE/start/end /api/eqp/**` |
| `Model Info` | 모델 전체 조회/등록/수정/삭제 | `GET/POST/PUT/DELETE /api/model/**` |
| `User Info` | 사용자 + 사용자별 그룹 매핑 관리 | `GET/POST/PUT/DELETE /api/user/**`, `POST/DELETE /api/user/{userPk}/group/{groupId}`, `POST /api/user/{userPk}/password/reset` |
| `Group Info` | 그룹 + 그룹별 권한 매핑 관리 | `GET/POST/PUT/DELETE /api/group/**`, `GET/POST/DELETE /api/group/{groupId}/permission/{permId}` |
| `UI Permission` | UI/API 권한 정의 관리 | `GET/POST/PUT/DELETE /api/permission/**` |

## `/api/eqp/**` 현행 동작 (유지 대상)

| Method | Path | 기능 | 응답 패턴 |
|---|---|---|---|
| POST | `/api/eqp` | 설비 생성 (EQP_CREATE) | DualResponse 대기 후 200/500/504 |
| PUT | `/api/eqp/{eqpId}` | 설비 수정 (EQP_UPDATE) | DualResponse 대기 후 200/500/504 |
| DELETE | `/api/eqp/{eqpId}` | 설비 삭제 (EQP_DELETE) | DualResponse 대기 후 200/500/504 (`interfaceType` 쿼리 필수) |
| POST | `/api/eqp/{eqpId}/start` | 설비 시작 (EQP_START) | 202 + `traceId` 즉시 반환 |
| POST | `/api/eqp/{eqpId}/end` | 설비 종료 (EQP_END) | 202 + `traceId` 즉시 반환 |

연계 조회 API:

| Method | Path | 기능 |
|---|---|---|
| GET | `/api/async/{traceId}` | start/end 비동기 결과 polling |

## 확장 API 계약 (단수형 경로 규칙)

### EqpInfo

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/eqp` | 설비 목록 조회 (DB 기반) |
| GET | `/api/eqp/{eqpId}` | 설비 상세 조회 (DB 기반) |
| POST/PUT/DELETE/start/end | `/api/eqp/**` | 기존 명령 API 유지 |

### Model Info

| Method | Path |
|---|---|
| GET | `/api/model` |
| GET | `/api/model/{modelVersionKey}` |
| POST | `/api/model` |
| PUT | `/api/model/{modelVersionKey}` |
| DELETE | `/api/model/{modelVersionKey}` |

### User Info

| Method | Path |
|---|---|
| GET | `/api/user` |
| GET | `/api/user/{userPk}` |
| POST | `/api/user` |
| PUT | `/api/user/{userPk}` |
| DELETE | `/api/user/{userPk}` |
| POST | `/api/user/{userPk}/group/{groupId}` |
| DELETE | `/api/user/{userPk}/group/{groupId}` |
| POST | `/api/user/{userPk}/password/reset` |

### Group Info

| Method | Path |
|---|---|
| GET | `/api/group` |
| GET | `/api/group/{groupId}` |
| POST | `/api/group` |
| PUT | `/api/group/{groupId}` |
| DELETE | `/api/group/{groupId}` |
| GET | `/api/group/{groupId}/permission` |
| POST | `/api/group/{groupId}/permission/{permId}` |
| DELETE | `/api/group/{groupId}/permission/{permId}` |

### UI Permission

| Method | Path |
|---|---|
| GET | `/api/permission` |
| GET | `/api/permission/{permId}` |
| POST | `/api/permission` |
| PUT | `/api/permission/{permId}` |
| DELETE | `/api/permission/{permId}` |

## 공개 인터페이스/타입 변경사항 (구현 단계 기준)

| 모듈 | 변경 항목 | 확정 내용 |
|---|---|---|
| `libs/ui/tc-ui-core` | Port 추가 | Eqp 조회, Model CRUD, User CRUD, Group CRUD, Permission CRUD, User-Group 매핑, Group-Permission 매핑 Port 추가 |
| `libs/db/tc-db-core` | Store 계약 보강 | `TcUserInfoStore`에 `findAll(PageRequest)` 추가 |
| `libs/ui/adapter/tc-ui-db-adapter` | Port 구현체 추가 | 상기 Port를 `tc-db-core` Store 기반으로 구현 |
| `libs/ui/adapter/tc-ui-web-adapter` | Controller/DTO 추가 | `ModelController`, `UserController`, `GroupController`, `PermissionController` 및 DTO 추가 |
| 공통 응답 | 타입 추가 | 목록 응답용 `PagedResponse<T>{items, offset, limit, count}` 추가 |

## 삭제/충돌 정책
- 기본 삭제 정책은 전체 물리 삭제입니다.
- `tc_user_info` 삭제 전 `tc_ui_auth_session` 정리(폐기/삭제) 절차를 강제합니다.
- `tc_model_version` 삭제 시 `tc_eqp.model_version_key` 참조가 있으면 `409 CONFLICT`를 반환합니다.
- 매핑 테이블(`tc_user_group_member`, `tc_user_group_permission`)은 물리 삭제합니다.
- FK/유니크 충돌은 `409` 또는 `400` 정책으로 표준화합니다.

## RBAC 확장 규칙
- 경로 규칙은 기존 단수형 유지: `/api/model`, `/api/user`, `/api/group`.
- 신규 권한:
  - `MODEL_WRITE` → `/api/model` (`POST/PUT/DELETE`)
  - `PERMISSION_WRITE` → `/api/permission` (`null`, 모든 메서드)
- 유지 권한:
  - `MODEL_READ(GET)` 유지
  - `USER_INFO_WRITE`, `GROUP_WRITE` 유지

권한 매트릭스:

| permCode | resource | httpMethod | 설명 |
|---|---|---|---|
| `EQP_MANAGE` | `/api/eqp` | `null` | 설비 변경/제어 |
| `ASYNC_READ` | `/api/async` | `GET` | 비동기 결과 조회 |
| `MODEL_READ` | `/api/model` | `GET` | 모델 조회 |
| `MODEL_WRITE` | `/api/model` | `POST/PUT/DELETE` | 모델 변경 |
| `USER_INFO_WRITE` | `/api/user` | `null` | 사용자/멤버십/비밀번호 초기화 |
| `GROUP_WRITE` | `/api/group` | `null` | 그룹/그룹-권한 매핑 |
| `PERMISSION_WRITE` | `/api/permission` | `null` | UI 권한 CRUD |
| `DLQ_READ` | `/api/dlq` | `GET` | DLQ 조회 |
| `DLQ_DELETE` | `/api/dlq` | `DELETE` | DLQ 삭제 |

역할 기본 매핑:
- `ADMIN`: 전체 권한
- `DEVELOPER`: USER/GROUP/PERMISSION 제외
- `OPERATOR`: 조회 중심

## 응답 포맷 계약
- 기존 `ApiResponse<T>`는 유지합니다.
- 목록 API는 `ApiResponse<PagedResponse<T>>` 형태를 사용합니다.

```java
public record PagedResponse<T>(
        java.util.List<T> items,
        int offset,
        int limit,
        long count
) { }
```

## 검증 시나리오
1. 로그인 성공 후 기본 진입 페이지가 `EqpInfo`인지 확인
2. `GET /api/eqp` + 기존 `POST/PUT/DELETE/start/end` 공존 확인
3. `Model Info` CRUD 성공/실패/중복/참조충돌(409) 확인
4. `User Info` CRUD + 비밀번호 초기화 + 사용자-그룹 매핑 CRUD 확인
5. `Group Info` CRUD + 그룹-권한 매핑 CRUD 확인
6. `UI Permission` CRUD + 권한 캐시 재로딩 확인
7. 물리 삭제 시 FK 제약 충돌 에러코드(409/400) 확인
8. 권한 없는 사용자 403, 미인증 401 확인
9. 목록 API `offset/limit` 기본값/상한 확인

## 구현 순서
1. 아키텍처/작업 문서 확정
2. Core Port 및 DB Store 계약 보강
3. DB Adapter 구현
4. Web Adapter Controller/DTO 구현
5. 권한 seed 및 매핑 정책 반영
6. 시나리오 테스트 확장

## 가정
- 구현 범위는 별도이며 본 문서는 설계 계약입니다.
- Front 라우팅 구현은 외부 저장소에서 수행합니다.
- Eqp 변경은 기존 명령 API를 유지하고 조회 API만 신규 추가합니다.
