# 01. REST API 구조 (REST API Structure)

## 개요

`tc-ui-backend-app`은 프론트엔드 및 운영 도구와 통신하는 **REST API 서버**다.
`/api/**` 경로로 제공되며, 인증/인가, EQP 관리, 모델 관리, 사용자/권한, DLQ 조회, 비동기 결과 폴링 등을 담당한다.

---

## API 엔드포인트 일람

### AuthController (`/api/auth`)

| 메서드 | 경로 | 인증 필요 | 설명 |
|--------|------|-----------|------|
| `POST` | `/api/auth/login` | 공개 | 로그인 + HttpOnly 쿠키 발급 |
| `POST` | `/api/auth/logout` | 필요 | 세션 토큰 폐기 + 쿠키 삭제 |
| `GET`  | `/api/auth/me`    | 필요 | 현재 인증 사용자 정보 조회 |
| `GET`  | `/api/auth/csrf`  | 공개 | CSRF 쿠키 발급 트리거 |

### EqpController (`/api/eqp`)

| 메서드   | 경로                          | 설명 |
|----------|-------------------------------|------|
| `GET`    | `/api/eqp`                    | 설비 목록 조회 (페이지) |
| `GET`    | `/api/eqp/{eqpId}`            | 설비 단건 조회 |
| `GET`    | `/api/eqp/{eqpId}/manage`     | 설비 관리 상세 조회 |
| `GET`    | `/api/eqp/options`            | 설비 관리 옵션(모델/JAR) 목록 |
| `GET`    | `/api/eqp/{eqpId}/param-versions` | 파라미터 버전 목록 |
| `GET`    | `/api/eqp/{eqpId}/runtime-state` | 런타임 상태 조회 |
| `POST`   | `/api/eqp`                    | 설비 생성 (Dual Response) |
| `PUT`    | `/api/eqp/{eqpId}`            | 설비 수정 (Dual Response) |
| `DELETE` | `/api/eqp/{eqpId}`            | 설비 삭제 (Dual Response) |
| `POST`   | `/api/eqp/{eqpId}/start`      | 설비 시작 → Gateway (202 + traceId) |
| `POST`   | `/api/eqp/{eqpId}/end`        | 설비 종료 → Gateway (202 + traceId) |

### EqpParamController (`/api/eqp-param`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET`  | `/api/eqp-param` | 설비 파라미터 목록 |
| `POST` | `/api/eqp-param` | 파라미터 추가 |
| `PUT`  | `/api/eqp-param/{id}` | 파라미터 수정 |
| `DELETE` | `/api/eqp-param/{id}` | 파라미터 삭제 |

### ModelController (`/api/model`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET`  | `/api/model` | 모델 목록 조회 |
| `GET`  | `/api/model/{id}` | 모델 단건 조회 |
| `POST` | `/api/model` | 모델 생성 |
| `PUT`  | `/api/model/{id}` | 모델 수정 |
| `DELETE` | `/api/model/{id}` | 모델 삭제 |

### UserController (`/api/user`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET`  | `/api/user` | 사용자 목록 |
| `GET`  | `/api/user/{userPk}` | 단건 조회 |
| `POST` | `/api/user` | 사용자 생성 |
| `PUT`  | `/api/user/{userPk}` | 정보 수정 |
| `POST` | `/api/user/{userPk}/password-reset` | 비밀번호 재설정 |
| `DELETE` | `/api/user/{userPk}` | 삭제 |

### GroupController (`/api/group`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET`  | `/api/group` | 그룹 목록 |
| `POST` | `/api/group` | 그룹 생성 |
| `PUT`  | `/api/group/{groupPk}` | 그룹 수정 |
| `DELETE` | `/api/group/{groupPk}` | 그룹 삭제 |

### PermissionController (`/api/permission`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET`  | `/api/permission` | 권한 목록 |
| `POST` | `/api/permission` | 권한 생성 |
| `PUT`  | `/api/permission/{permPk}` | 권한 수정 |
| `DELETE` | `/api/permission/{permPk}` | 권한 삭제 |

### DlqController (`/api/dlq`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET`  | `/api/dlq/gateway` | Gateway DLQ 목록 (Redis) |
| `GET`  | `/api/dlq/business` | Business DLQ 목록 (Redis) |
| `DELETE` | `/api/dlq/gateway/{key}` | Gateway DLQ 항목 삭제 |
| `DELETE` | `/api/dlq/business/{key}` | Business DLQ 항목 삭제 |

### AsyncResultController (`/api/async`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET`  | `/api/async/{traceId}` | 비동기 작업 결과 폴링 |

---

## 공통 응답 포맷 (ApiResponse)

모든 API는 아래 통일된 래퍼 형태로 응답한다.

```json
{
  "success": true,
  "data": { ... },
  "errorCode": null,
  "errorMessage": null
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `success` | boolean | 성공 여부 |
| `data` | T | 응답 데이터 (성공 시) |
| `errorCode` | String | 오류 코드 (실패 시) |
| `errorMessage` | String | 오류 메시지 (실패 시) |

---

## 페이지 조회 공통 파라미터

목록 조회 API는 공통 페이지 파라미터를 사용한다.

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `offset` | 0 | 조회 시작 위치 |
| `limit` | 20 | 조회 건수 |

페이지 응답은 `PagedResponse<T>` 형식이다.

```json
{
  "items": [...],
  "offset": 0,
  "limit": 20,
  "count": 150
}
```

---

## 공개/보호 경로 구분

```
공개 경로 (인증 불필요):
  POST /api/auth/login
  GET  /api/auth/csrf
  GET  /api/actuator/health
  OPTIONS /**   (CORS Preflight)
  /error

보호 경로 (인증 필수 + DB 권한 확인):
  그 외 모든 /api/** 경로
```

> 인증 실패 → 401 Unauthorized
> 권한 없음 → 403 Forbidden
> 미존재 리소스 → 404 Not Found

---

## 비동기 명령 API 패턴

EQP START/END는 처리 완료를 기다리지 않고 **202 Accepted** 를 먼저 반환한다.
CRUD(생성/수정/삭제)는 Gateway+Business 양쪽 응답을 수집하는 **Dual Response** 패턴을 사용한다.

```
[START/END]
    요청 → 202 Accepted + {"traceId": "..."}
    클라이언트 → GET /api/async/{traceId} 폴링
    완료 시 → 200 OK + 결과

[CREATE/UPDATE/DELETE]
    요청 → DeferredResult (CompletableFuture)
    Gateway + Business 양쪽 응답 수신 대기
    완료 시 → 200/201/204 + 결과
    타임아웃(기본 10s) → 504
```

---

## 관련 문서

- [UI: 쿠키 기반 인증](02-cookie-based-authentication.md)
- [UI: CSRF/CORS 보안](03-csrf-cors-security.md)
- [UI: Dual Response 패턴](04-dual-response-pattern.md)
- [UI: 비동기 결과 폴링](05-async-result-polling.md)
- [UI: Kafka 이벤트 발행](06-kafka-event-publishing.md)
