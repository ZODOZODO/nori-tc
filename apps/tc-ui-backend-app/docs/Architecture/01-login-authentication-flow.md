# 01. 로그인 인증 흐름 (Login Authentication Flow)

## 개요

이 문서는 nori-tc-ui(프론트엔드)와 nori-tc(백엔드) 간의 로그인 및 인증 처리 전체 흐름을 상세하게 설명합니다.

- **인증 방식**: 세션 토큰 기반 쿠키 인증 (HttpOnly Cookie)
- **CSRF 방어**: Double-Submit Cookie 패턴 (`XSRF-TOKEN` + `X-XSRF-TOKEN` 헤더)
- **캐싱**: Redis (토큰 유효성 검증 캐시, TTL 300초)
- **비밀번호 해시**: BCrypt

---

## 관련 파일 목록

### 프론트엔드 (`nori-tc-ui`)

| 파일 | 역할 |
|------|------|
| `src/features/auth/components/LoginPage.tsx` | 로그인 UI 컴포넌트 |
| `src/features/auth/hooks/useLogin.ts` | 로그인 상태 관리 Hook |
| `src/features/auth/api/auth.api.ts` | 인증 API 호출 레이어 |
| `src/features/auth/types/auth.types.ts` | 타입 정의 |
| `src/shared/lib/api-client.ts` | axios 인스턴스 및 인터셉터 설정 |
| `src/app/Router.tsx` | 라우팅 설정 |

### 백엔드 (`nori-tc`)

| 파일 | 역할 |
|------|------|
| `libs/ui/adapter/tc-ui-web-adapter/.../AuthController.java` | 인증 REST 컨트롤러 |
| `libs/ui/tc-ui-core/.../usecase/LoginUseCase.java` | 로그인 비즈니스 로직 |
| `libs/ui/tc-ui-core/.../usecase/ValidateTokenUseCase.java` | 토큰 유효성 검증 |
| `libs/ui/tc-ui-core/.../usecase/LogoutUseCase.java` | 로그아웃 비즈니스 로직 |
| `libs/ui/adapter/tc-ui-web-adapter/.../security/UiSecurityConfig.java` | Spring Security 필터 체인 설정 (CSRF/CORS/URL 인가) |
| `libs/ui/adapter/tc-ui-web-adapter/.../security/UiTokenAuthenticationFilter.java` | 요청별 토큰 검증 필터 |
| `libs/ui/adapter/tc-ui-web-adapter/.../security/UiAuthenticationEntryPoint.java` | 인증 실패 응답 처리 |
| `libs/ui/adapter/tc-ui-web-adapter/.../security/UiApiPermissionCache.java` | DB 기반 API URL 인가 캐시 (기동 시 로드, 주기 갱신) |
| `libs/ui/tc-ui-domain/.../auth/AuthToken.java` | 세션 토큰 도메인 모델 |
| `libs/ui/tc-ui-domain/.../auth/UserPrincipal.java` | 인증된 사용자 도메인 모델 |
| `libs/db/tc-db-domain/.../user/TcUserInfo.java` | 사용자 DB 도메인 모델 |
| `libs/db/tc-db-domain/.../user/TcUiAuthSession.java` | 세션 DB 도메인 모델 |
| `libs/ui/tc-ui-core/.../properties/UiAuthProperties.java` | 인증 설정값 바인딩 |
| `apps/tc-ui-backend-app/config/tc-ui-backend.properties` | 인증 설정 파일 |

---

## 1. 로그인 흐름 (Login Flow)

### 1.1 전체 시퀀스

```
[사용자]            [LoginPage.tsx]         [useLogin.ts]         [auth.api.ts]
   |                     |                       |                      |
   | 아이디/비밀번호 입력  |                       |                      |
   |-------------------->|                       |                      |
   |                     | onSubmit()            |                      |
   |                     |---------------------->|                      |
   |                     |                       | mutateAsync()        |
   |                     |                       | → authApi.login()    |
   |                     |                       |--------------------->|
   |                     |                       |                      | issueCsrfToken()
   |                     |                       |                      | GET /api/auth/csrf
   |                     |                       |                      |--------------->
   |                     |                       |                      | <Set-Cookie: XSRF-TOKEN>
   |                     |                       |                      |<--------------
   |                     |                       |                      | POST /api/auth/login
   |                     |                       |                      | Header: X-XSRF-TOKEN
   |                     |                       |                      | Body: {userId, password}
   |                     |                       |                      |--------------->
   |                     |                       |                      | 200 OK
   |                     |                       |                      | <Set-Cookie: TC_UI_AUTH>
   |                     |                       |                      |<--------------
   |                     |                       | verifyEqpAccess()    |
   |                     |                       |--------------------->|
   |                     |                       |                      | GET /api/eqp (쿠키 자동 포함)
   |                     |                       |                      |--------------->
   |                     |                       |                      | 200 OK
   |                     |                       |                      |<--------------
   |                     | navigate('/eqp')      |                      |
   |                     |<----------------------|                      |
   | 메인 화면 이동        |                       |                      |
   |<--------------------|                       |                      |
```

### 1.2 단계별 상세 설명

#### Step 1: CSRF 토큰 발급

**프론트엔드** (`auth.api.ts` - `issueCsrfToken()`, `authApi.login()` 내부에서 자동 호출)

- `authApi.login()` 최초 단계에서 `issueCsrfToken()`을 직접 호출
- `GET /api/auth/csrf` 호출 → `Set-Cookie: XSRF-TOKEN` 수신
- `document.cookie`에서 `XSRF-TOKEN` 파싱 → 이후 `X-XSRF-TOKEN` 헤더에 주입
- CSRF 쿠키가 없으면 `AuthApiError(CSRF_TOKEN_MISSING)` throw (로그인 요청 중단)
- `prepareCsrfToken`은 `issueCsrfToken`의 public 별칭으로 노출되어 있으나,
  실제 로그인 흐름에서는 `authApi.login()` 내부에서 자동으로 처리됨

**백엔드** (`AuthController.java` - `csrf()`)

- `CsrfTokenRepository.loadDeferredToken(request, response).get()` 명시적 호출로
  토큰 생성 + 저장을 즉시 트리거
- `CookieCsrfTokenRepository`가 `Set-Cookie: XSRF-TOKEN` 응답 헤더를 생성
- CSRF 쿠키 정책(Secure/SameSite/Domain)은 인증 쿠키와 동일하게 맞춤

#### Step 2: 로그인 요청

**프론트엔드** (`auth.api.ts` - `login()`)

```typescript
POST /api/auth/login
Headers:
  Content-Type: application/json
  X-XSRF-TOKEN: {csrfToken}  // CSRF 검증용
Cookie: XSRF-TOKEN={csrfToken}  // withCredentials: true로 자동 포함
Body:
  {
    "userId": "string",
    "password": "string"
  }
```

> **참고**: `validateStatus: (status) => status >= 200 && status < 500` 설정으로
> 401 응답도 예외 없이 수신하여 폼 에러 메시지로 표시

**백엔드** (`AuthController.java` - `login()`) → `LoginUseCase.java`

```
1. userId 정규화
   userId.trim().toLowerCase() → userIdNorm

2. 사용자 조회 (UserPort.findByUserIdNorm)
   SELECT * FROM tc_user_info WHERE user_id_norm = '{userIdNorm}'
   ↳ NOT FOUND → UiAuthenticationException (401)

3. 계정 상태 확인
   if (status != UserStatus.ACTIVE)
   ↳ → UiAuthenticationException (401)

4. BCrypt 비밀번호 검증 (PasswordVerifierPort.matches)
   BCrypt.checkpw(rawPassword, passwordHash)
   ↳ 불일치 → UiAuthenticationException (401)

5. SecureRandom 토큰 생성 (64자, A-Za-z0-9)
   LENGTH = 64, CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

6. 세션 저장 (SessionPort.save)
   INSERT INTO tc_ui_auth_session
   (token, user_pk, issued_at, expires_at, last_seen_at, revoked)
   VALUES (?, ?, now(), now()+8h, null, false)

7. AuthToken 반환 → ResponseCookie 생성 → 응답
```

#### Step 3: 로그인 성공 응답

**백엔드 응답**

```http
HTTP/1.1 200 OK
Set-Cookie: TC_UI_AUTH={token}; Path=/; HttpOnly; Secure; SameSite=None
Content-Type: application/json

{
  "success": true,
  "data": {
    "userPk": 1,
    "issuedAt": "2026-03-09T09:00:00+09:00",
    "expiresAt": "2026-03-09T17:00:00+09:00"
  },
  "errorCode": null,
  "errorMsg": null
}
```

**로그인 실패 응답 (401)**

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "success": false,
  "data": null,
  "errorCode": "UNAUTHORIZED",
  "errorMsg": "아이디 또는 비밀번호가 올바르지 않습니다."
}
```

> **보안**: 사용자 미존재와 비밀번호 불일치를 동일한 메시지로 처리하여
> 사용자 열거(User Enumeration) 공격 방지

#### Step 4: 인증 확인 (verifyEqpAccess)

로그인 성공 후 `apiClient.get('/eqp', { withCredentials: true })`로 `GET /api/eqp`를 호출하여
쿠키 기반 인증이 실제로 작동하는지 검증한 뒤,
`navigate('/eqp', { replace: true })`로 메인 화면으로 이동.

> `apiClient`의 `baseURL`이 `/api`이므로 실제 요청 경로는 `/api/eqp`입니다.

---

## 2. 인증 요청 흐름 (Authenticated Request Flow)

로그인 이후 모든 보호된 API 요청은 아래 흐름을 따릅니다.

```
[브라우저]                  [UiTokenAuthenticationFilter]           [ValidateTokenUseCase]
    |                                    |                                   |
    | GET /eqp                           |                                   |
    | Cookie: TC_UI_AUTH={token}         |                                   |
    |---------------------------------->|                                   |
    |                                    | extractTokenFromCookie()          |
    |                                    | TC_UI_AUTH 쿠키 추출              |
    |                                    |                                   |
    |                                    | ValidateTokenUseCase.execute()   |
    |                                    |---------------------------------->|
    |                                    |                                   |
    |                                    |          [Redis 캐시 조회]        |
    |                                    |          캐시 히트 →              |
    |                                    |          UserPrincipal 즉시 반환  |
    |                                    |          (DB 조회 없음)           |
    |                                    |                                   |
    |                                    |          캐시 미스 → DB 조회:     |
    |                                    |          1) Session 조회          |
    |                                    |             WHERE token=?         |
    |                                    |             AND revoked=false     |
    |                                    |             AND expires_at>now()  |
    |                                    |          2) User 상태 확인        |
    |                                    |          3) 권한 3-JOIN 쿼리      |
    |                                    |          4) Redis 캐시 저장       |
    |                                    |          5) lastSeenAt 비동기 갱신|
    |                                    |<----------------------------------|
    |                                    |                                   |
    |                                    | SecurityContext에 인증 등록       |
    |                                    | UsernamePasswordAuthenticationToken
    |                                    |  principal: UserPrincipal         |
    |                                    |  credentials: 원본 토큰           |
    |                                    |                                   |
    |                                    | filterChain.doFilter()            |
    |                                    |-----------> [Controller]          |
    |                                    |             비즈니스 로직 실행     |
    | 200 OK + 응답 데이터                |                                   |
    |<----------------------------------|                                   |
```

### 2.1 토큰 검증 세부 단계 (`ValidateTokenUseCase.java`)

```
1. Redis 캐시 조회 (TokenCachePort.get)
   키: tc:ui:backend:session:{token}
   ↳ 캐시 히트:
     - 30초마다 주기적으로 DB revoke 재검증
     - UserPrincipal 즉시 반환 (DB 조회 생략)
   ↳ 캐시 미스: DB 조회 진행

2. DB 세션 조회 (SessionPort.findValidByToken)
   SELECT * FROM tc_ui_auth_session
   WHERE token = ?
     AND revoked = false
     AND expires_at > now()
   ↳ 없음 → UiAuthenticationException

3. 사용자 계정 상태 확인 (UserPort.findByUserPk)
   ↳ ACTIVE 아닌 경우 → UiAuthenticationException

4. 권한 로드 (PermissionPort.findPermissionCodesByUserPk)
   SELECT p.permission_code
   FROM tc_user_group_member m
   JOIN tc_user_group_permission gp ON m.group_pk = gp.group_pk
   JOIN tc_ui_permission p ON gp.permission_pk = p.permission_pk
   WHERE m.user_pk = ?

5. UserPrincipal 생성
   {userPk, userId, permissionCodes: Set<String>}

6. Redis 캐시 저장 (TTL: 300초)

7. lastSeenAt 비동기 업데이트
   CompletableFuture.runAsync()
   (실패해도 인증 실패로 전파 안 함)
```

### 2.2 비동기 재디스패치 처리

`UiTokenAuthenticationFilter`는 `shouldNotFilterAsyncDispatch() = false`로 설정되어
DeferredResult 비동기 재디스패치 시에도 필터가 재실행됩니다.

재디스패치(ASYNC dispatch) 처리 단계:

1. **SecurityContext 재사용 (우선)**: SecurityContext에 이미 인증 정보가 있으면
   토큰 재검증 없이 다음 필터로 즉시 통과
2. **ASYNC_AUTH_ATTRIBUTE 복원 (폴백)**: SecurityContext가 비어 있는 경우
   최초 REQUEST 디스패치에서 `request.setAttribute(ASYNC_AUTH_ATTRIBUTE, authentication)`으로
   저장한 인증 객체를 복원하여 SecurityContext를 재구성

> Redis 캐시 히트로 처리되는 경우에도 재조회가 필요하지만,
> SecurityContext 재사용 경로에서는 Redis/DB 조회 자체를 건너뛰므로 성능 오버헤드가 최소화됩니다.

### 2.3 요청 단위 traceId MDC 주입

`UiTokenAuthenticationFilter`는 매 요청마다 traceId를 MDC에 주입합니다.

우선순위:
1. `X-Request-Id` 요청 헤더 값
2. 동일 request의 이전 디스패치에서 저장한 `request attribute` 값 (재디스패치 시 동일 traceId 유지)
3. `UUID.randomUUID()` 신규 생성

필터 종료 시 이전 MDC 상태로 복구하여 traceId 누수를 방지합니다.

---

## 3. 로그아웃 흐름 (Logout Flow)

```
[프론트엔드]                        [백엔드]
    |                                  |
    | POST /api/auth/logout            |
    | Cookie: TC_UI_AUTH={token}       |
    |--------------------------------->|
    |                                  | AuthController.logout()
    |                                  | SecurityContext에서 원본 토큰 추출
    |                                  |   credentials = authentication.getCredentials()
    |                                  |
    |                                  | LogoutUseCase.execute(token)
    |                                  |   1) DB 세션 폐기
    |                                  |      UPDATE tc_ui_auth_session
    |                                  |      SET revoked = true
    |                                  |      WHERE token = ?
    |                                  |   2) Redis 캐시 즉시 삭제
    |                                  |      TokenCachePort.evict(token)
    |                                  |      (Redis 장애 시 무시, DB revoke는 보존)
    |                                  |
    |                                  | SecurityContext.clearContext()
    |                                  |
    | 200 OK                           |
    | Set-Cookie: TC_UI_AUTH=;         |
    |            MaxAge=0  ← 쿠키 삭제  |
    |<---------------------------------|
    |                                  |
    | (401 인터셉터 or 명시적 리다이렉트) |
    | window.location.href = '/login'  |
```

---

## 4. 인증 실패 처리 (401 Unauthorized)

### 프론트엔드 인터셉터 (`api-client.ts`)

```typescript
// 응답 인터셉터: 401 응답 시 자동 로그인 페이지 이동
if (error.response?.status === 401) {
  window.location.href = '/login'
}
```

> **예외**: 로그인 API 호출 자체는 `validateStatus`로 401을 정상 응답으로 처리하여
> 위 인터셉터를 우회하고 폼 에러 메시지로 표시

### 백엔드 엔트리포인트 (`UiAuthenticationEntryPoint.java`)

Spring Security의 인증 실패 진입점. 쿠키 없음 / 만료 토큰 / 폐기된 토큰 모두 동일 형태로 응답:

```json
HTTP/1.1 401 Unauthorized
{
  "success": false,
  "data": null,
  "errorCode": "UNAUTHORIZED",
  "errorMsg": "인증이 필요합니다."
}
```

---

## 5. API 엔드포인트 목록

| Method | Path | 인증 필요 | 설명 |
|--------|------|-----------|------|
| `GET` | `/api/auth/csrf` | 불필요 | CSRF 토큰 발급 |
| `POST` | `/api/auth/login` | 불필요 | 로그인 (세션 생성) |
| `POST` | `/api/auth/logout` | 필요 | 로그아웃 (세션 폐기) |
| `GET` | `/api/auth/me` | 필요 | 현재 사용자 정보 조회 |
| `GET` | `/api/actuator/health` | 불필요 | 헬스 체크 |
| `OPTIONS` | `/**` | 불필요 | CORS preflight (브라우저 자동 호출) |

---

## 6. 데이터 모델

### 6.1 DB 테이블

#### `tc_user_info` - 사용자 정보

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `user_pk` | bigint PK | 사용자 고유 번호 (IDENTITY) |
| `company` | varchar | 회사명 |
| `department` | varchar | 부서명 |
| `user_name` | varchar | 사용자 이름 |
| `user_id` | varchar | 로그인 ID (원본) |
| `user_id_norm` | varchar UNIQUE | 정규화된 로그인 ID (소문자, trim) |
| `password_hash` | varchar | BCrypt 해시 |
| `email` | varchar UNIQUE | 이메일 |
| `status` | varchar | 계정 상태 (ACTIVE/LOCKED/DISABLED/DELETED) |
| `created_at` | timestamptz | 생성 시각 |
| `updated_at` | timestamptz | 수정 시각 |

#### `tc_ui_auth_session` - 세션 정보

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `token` | varchar(255) PK | 세션 토큰 (64자 alphanumeric) |
| `user_pk` | bigint FK | 사용자 참조 |
| `issued_at` | timestamptz | 발급 시각 |
| `expires_at` | timestamptz | 만료 시각 (issued_at + 8시간) |
| `last_seen_at` | timestamptz | 마지막 사용 시각 (nullable) |
| `revoked` | boolean | 폐기 여부 (기본값: false) |

### 6.2 도메인 모델

#### `AuthToken` (Java Record)

```java
record AuthToken(
  String token,            // 64자 세션 토큰
  long userPk,             // 사용자 PK
  OffsetDateTime issuedAt,
  OffsetDateTime expiresAt
) {
  boolean isExpired();     // 만료 여부
  boolean isValid();       // 유효 여부 (미만료 + 미폐기)
}
```

#### `UserPrincipal` (Java Record)

```java
record UserPrincipal(
  long userPk,
  String userId,
  Set<String> permissionCodes  // 불변 Set
) {
  boolean hasPermission(String permCode);
  boolean hasAnyPermission(String... permCodes);
  boolean hasNoPermission();
}
```

---

## 7. 설정값 (UiAuthProperties)

**설정 파일**: `apps/tc-ui-backend-app/config/tc-ui-backend.properties`

| 설정 키 | 기본값 | 설명 |
|---------|--------|------|
| `tc.ui.backend.auth.session-ttl-hours` | `8` | 세션 유효 시간 (시간) |
| `tc.ui.backend.auth.token-cache-ttl-seconds` | `300` | Redis 캐시 TTL (초) |
| `tc.ui.backend.auth.cookie-name` | `TC_UI_AUTH` | 인증 쿠키 이름 |
| `tc.ui.backend.auth.cookie-path` | `/` | 쿠키 경로 |
| `tc.ui.backend.auth.cookie-domain` | (없음, 미지정) | 쿠키 Domain (옵션, 미지정 시 현재 도메인) |
| `tc.ui.backend.auth.cookie-secure` | `true` | HTTPS 전용 여부 |
| `tc.ui.backend.auth.cookie-same-site` | `None` | SameSite 정책 |
| `tc.ui.backend.auth.csrf-cookie-name` | `XSRF-TOKEN` | CSRF 쿠키 이름 |
| `tc.ui.backend.auth.csrf-header-name` | `X-XSRF-TOKEN` | CSRF 헤더 이름 |
| `tc.ui.backend.auth.cors-allowed-origins` | (없음) | CORS 허용 Origin 목록 (쉼표 구분) |

> **로컬 프로파일 오버라이드** (`config/tc-ui-backend-local.properties`):
> `spring.profiles.active=local` 지정 시 아래 값으로 덮어씁니다.
>
> | 설정 키 | 로컬 값 | 이유 |
> |---------|---------|------|
> | `cookie-secure` | `false` | HTTP(비TLS) 환경 테스트 |
> | `cookie-same-site` | `Lax` | 로컬 same-site 요청 허용 |
> | `cors-allowed-origins` | `http://localhost:3000,http://127.0.0.1:3000` | Vite dev server 허용 |

---

## 8. 보안 설계 원칙

### 8.1 인증 쿠키 보안

| 속성 | 값 | 목적 |
|------|-----|------|
| `HttpOnly` | true | JavaScript 접근 불가 → XSS 토큰 탈취 방지 |
| `Secure` | true | HTTPS에서만 전송 |
| `SameSite` | None | Cross-origin 쿠키 전송 허용 (CSRF는 토큰으로 방어) |
| `Path` | `/` | 전체 경로 유효 |

### 8.2 CSRF 방어 (Double-Submit Cookie)

```
1. GET /api/auth/csrf → Set-Cookie: XSRF-TOKEN={token}
2. POST /api/auth/login
   Header: X-XSRF-TOKEN={token}   ← 헤더 (공격자 설정 불가)
   Cookie: XSRF-TOKEN={token}     ← 쿠키 (브라우저 자동 포함)
3. 서버: 헤더값 == 쿠키값 검증
```

### 8.3 비밀번호 보안

- **저장**: BCrypt 해시만 저장 (평문 없음)
- **검증**: `PasswordVerifierPort.matches(rawPassword, passwordHash)`
- **전송**: HTTPS로만 전송

### 8.4 토큰 보안

- **생성**: `SecureRandom` (암호학적으로 안전한 난수)
- **길이**: 64자 (A-Za-z0-9 62진법 → 약 10^114 조합)
- **로깅**: 앞 8자리만 마스킹 로깅

### 8.5 사용자 열거 공격 방지

사용자 미존재와 비밀번호 불일치를 동일한 메시지로 처리:
> "아이디 또는 비밀번호가 올바르지 않습니다."

### 8.6 URL 인가 체계 (UiApiPermissionCache)

Spring Security의 `authorizeHttpRequests`에 커스텀 `AuthorizationManager`를 등록하여
**DB 기반 URL 인가(Closed by Default)**를 구현합니다.

**`UiApiPermissionCache` 동작:**
- 애플리케이션 기동 시(`@PostConstruct`) `tc_ui_permission` 테이블의 활성 권한 목록을 메모리에 로드
- `@Scheduled`로 주기적 갱신 (권한 변경 반영)
- 초기 로드 실패 시 `initializationFailed=true` → 모든 보호 API 차단 (failsafe)

**매 HTTP 요청 인가 판단 순서:**
1. 인증 미완료(Anonymous) → 거부 (401)
2. principal이 `UserPrincipal`이 아님 → 거부 (비정상 상태)
3. `UiApiPermissionCache.isAuthorized(userPrincipal, httpMethod, requestUri)`:
   - 캐시에 해당 URI 권한 없음 → **기본 차단**
   - 권한 있음 + 사용자가 `permissionCode` 보유 → 허용
   - matchType: `PREFIX`(startsWith) 또는 `EXACT`(equals)
   - httpMethod: `null`이면 모든 메서드 허용, 지정 시 대소문자 무관 비교

---

## 9. 캐싱 전략

### Redis 캐시 구조

```
키:   tc:ui:backend:session:{token}
값:   UserPrincipal { userPk, userId, permissionCodes }
TTL: 300초 (5분)
```

### 캐시 일관성 보장

| 시나리오 | 처리 방식 |
|---------|----------|
| 로그아웃 | DB revoke + Redis 즉시 삭제 |
| 토큰 만료 | TTL 만료 → 자동 캐시 삭제 |
| 주기적 revoke 재검증 | 30초마다 DB 재조회 |
| 권한 변경 | Redis TTL 만료 후 반영 (최대 300초 지연) |
| Redis 장애 | DB 직접 조회로 폴백, 로그아웃은 DB revoke 우선 보장 |

---

## 10. 에러 코드

### 프론트엔드

| 에러코드 | 발생 상황 |
|---------|----------|
| `CSRF_TOKEN_MISSING` | CSRF 토큰 발급 실패 (쿠키 없음) |
| `UNKNOWN_ERROR` | 예상치 못한 서버 오류 |

### 백엔드

| 에러코드 | HTTP Status | 발생 상황 | 처리 주체 |
|---------|-------------|----------|----------|
| `UNAUTHORIZED` | 401 | 사용자 없음, 비밀번호 불일치, 비활성 계정 | `AuthController` (로그인 실패) |
| `UNAUTHORIZED` | 401 | 토큰 만료/폐기/미존재, 계정 비활성 (보호 API 접근) | `UiTokenAuthenticationFilter` (직접 응답) |
| `UNAUTHORIZED` | 401 | 인증 정보 없이 보호 API 접근 (쿠키 미포함) | `UiAuthenticationEntryPoint` (Spring Security 위임) |
| `UNAUTHORIZED` | 401 | Redis/DB 장애로 토큰 검증 불가 | `UiTokenAuthenticationFilter` (직접 응답) |
