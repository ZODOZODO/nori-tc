# 02. 쿠키 기반 인증 (Cookie-Based Authentication)

## 개요

`tc-ui-backend-app`은 세션 토큰을 **HttpOnly 쿠키**로 관리한다.
브라우저는 쿠키를 자동으로 전송하므로, 클라이언트 코드에서 토큰을 직접 다룰 필요가 없다.
서버 측에서는 `UiTokenAuthenticationFilter`가 모든 요청에서 쿠키를 읽어 토큰을 검증한다.

---

## 왜 HttpOnly 쿠키인가?

| 방식 | 문제점 |
|------|--------|
| Authorization 헤더 (Bearer) | 클라이언트(JS)가 토큰을 localStorage에 저장 → XSS에 취약 |
| 일반 쿠키 | JS에서 `document.cookie`로 읽기 가능 → XSS에 취약 |
| **HttpOnly 쿠키** | JS에서 접근 불가 → XSS로 토큰 탈취 불가 |

---

## 전체 흐름 다이어그램

```
[로그인 요청] POST /api/auth/login
    │  { userId, password }
    ▼
AuthController.login()
    │
    └── LoginUseCase.execute(userId, password)
            │  DB 조회 + 비밀번호 검증
            ▼
        토큰 생성 (UUID 또는 opaque token)
            │
            └── DB에 token 저장 (tc_user_session 테이블)
                    │
                    ▼
        ResponseCookie (HttpOnly, SameSite=Lax, Path=/)
            └── Set-Cookie: authToken=<token>; HttpOnly; Path=/; SameSite=Lax


[이후 모든 요청]
    │  Cookie: authToken=<token>  ← 브라우저 자동 전송
    ▼
UiTokenAuthenticationFilter (OncePerRequestFilter)
    │
    ├── [1] 쿠키에서 authToken 추출
    │
    ├── [2] 쿠키 없음 → 필터 통과 (Spring Security가 인가 결정)
    │
    ├── [3] ValidateTokenUseCase.execute(token)
    │           │
    │           ├── Redis 캐시 조회 (TTL: 300s)
    │           │       캐시 히트 → 사용자 정보 반환
    │           │       캐시 미스 → DB 조회 → 캐시 저장
    │           │
    │           └── 토큰 유효하지 않음 → null 반환
    │
    ├── [4] null이면 → 필터 통과 (SecurityContext에 인증 없음)
    │           → Spring Security가 401/403 반환
    │
    └── [5] 유효하면 → UsernamePasswordAuthenticationToken 생성
                        principal = UserPrincipal (userId, groups, permissions)
                        credentials = token (로그아웃 시 사용)
                    → SecurityContextHolder에 저장
                    → 요청 속성에도 저장 (ASYNC_AUTH_ATTRIBUTE)


[로그아웃 요청] POST /api/auth/logout
    │  쿠키 자동 첨부
    ▼
AuthController.logout()
    │
    ├── SecurityContextHolder에서 credentials (= token) 추출
    ├── LogoutUseCase.execute(token) → DB에서 세션 삭제 + Redis 캐시 무효화
    └── Set-Cookie: authToken=; Max-Age=0  ← 쿠키 삭제
```

---

## 핵심 클래스

| 클래스 | 역할 |
|--------|------|
| `UiTokenAuthenticationFilter` | 요청에서 쿠키 추출 → 토큰 검증 → SecurityContext 설정 |
| `ValidateTokenUseCase` | 토큰 유효성 검사 (Redis 캐시 → DB 조회) |
| `LoginUseCase` | 자격증명 검증 → 토큰 생성 → DB 저장 |
| `LogoutUseCase` | 토큰 삭제 (DB) + Redis 캐시 무효화 |
| `UiSecurityConfig` | Spring Security 설정 (필터 체인, 공개 경로, STATELESS) |
| `UiAuthProperties` | 쿠키 이름, 경로 등 인증 관련 설정 |

---

## UiTokenAuthenticationFilter 동작 상세

```java
// 쿠키에서 토큰 추출
String token = Arrays.stream(request.getCookies())
    .filter(c -> c.getName().equals(authProperties.cookieName()))
    .findFirst()
    .map(Cookie::getValue)
    .orElse(null);

// 검증 → SecurityContext 설정
UserPrincipal principal = validateTokenUseCase.execute(token);
UsernamePasswordAuthenticationToken auth =
    new UsernamePasswordAuthenticationToken(principal, token, List.of());
SecurityContextHolder.getContext().setAuthentication(auth);

// ASYNC DeferredResult 재전파를 위해 요청 속성에도 저장
request.setAttribute(ASYNC_AUTH_ATTRIBUTE, auth);
```

### ASYNC 재전파 이유

Spring MVC의 `DeferredResult`는 별도 스레드에서 완료된다.
해당 스레드에는 원래 요청의 `SecurityContext`가 없으므로,
필터가 재호출될 때 요청 속성(`ASYNC_AUTH_ATTRIBUTE`)에 저장된 인증 객체를 재사용한다.

```
메인 스레드: 인증 처리 → ASYNC_AUTH_ATTRIBUTE 저장
    │
    └── DeferredResult 등록 후 응답 보류
            │
비동기 스레드: DeferredResult 완료
            │
UiTokenAuthenticationFilter (ASYNC dispatch 재진입)
            │
            └── ASYNC_AUTH_ATTRIBUTE 재사용 → SecurityContext 복원
```

---

## Redis 토큰 캐시

토큰 검증 시 매번 DB를 조회하면 부하가 크므로, Redis에 캐시한다.

```
ValidateTokenUseCase.execute(token)
    │
    ├── [1] Redis GET: "ui:token:{token}" → UserPrincipal 반환 (캐시 히트)
    │
    └── [2] Redis MISS → DB 조회 (tc_user_session JOIN tc_user)
                → 유효하면 Redis SET with TTL (300s)
                → UserPrincipal 반환
```

| 설정 | 값 | 설명 |
|------|----|------|
| Redis 키 패턴 | `ui:token:{token}` | 토큰별 캐시 |
| TTL | 300s | 세션 활성 상태 유지 기간 |

> Redis 캐시 상세: [공통: Redis 통합](../common/07-redis-integration.md)

---

## MDC 추적 ID 설정

필터는 로그 추적을 위해 MDC에 `traceId`를 설정한다.

```
1. X-Request-Id 헤더가 있으면 그 값을 traceId로 사용
2. 없으면 UUID를 생성해 사용
3. 응답 헤더 X-Request-Id에 값을 전달
4. finally 블록에서 MDC 정리 (스레드 풀 오염 방지)
```

---

## 공개 경로 vs 보호 경로

```
공개 (인증 불필요):
  POST /api/auth/login
  GET  /api/auth/csrf
  GET  /api/actuator/health
  OPTIONS /**  (CORS Preflight)
  /error

보호 (인증 필수 + 권한 확인):
  그 외 모든 /api/**
```

`UiSecurityConfig`에서 `requestMatchers().permitAll()` / `.anyRequest().authenticated()` 로 설정된다.
인증이 없거나 실패하면 → **401 Unauthorized**.
권한이 없으면 → **403 Forbidden**.

---

## 세션 비저장 (STATELESS)

Spring Security 세션 정책은 `STATELESS`다.
`HttpSession`은 사용하지 않으며, 인증 상태는 오직 **쿠키 + Redis 캐시**로만 유지된다.

```java
// UiSecurityConfig
http.sessionManagement(session ->
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
);
```

---

## 운영 포인트

| 항목 | 설명 |
|------|------|
| 쿠키 이름 | `UiAuthProperties.cookieName` 설정값 (기본 `authToken`) |
| HttpOnly 강제 | JS에서 쿠키 접근 차단 — XSS 방어 |
| SameSite=Lax | 크로스 사이트 요청 차단 — CSRF 방어 1차 |
| CSRF 이중 방어 | SameSite 외에도 Double Submit Cookie 방식 추가 적용 |
| Redis 장애 | Redis 미응답 시 DB 조회로 폴백 (설정에 따름) |
| 토큰 재시작 | 앱 재시작 시 Redis 캐시 초기화 — 첫 요청에서 DB 조회 발생 |
| 로그아웃 동기화 | 로그아웃 시 Redis 캐시 즉시 무효화 — 다른 서버 인스턴스도 다음 요청에서 DB 재조회 |

---

## 관련 문서

- [UI: CSRF/CORS 보안](03-csrf-cors-security.md)
- [UI: REST API 구조](01-rest-api-structure.md)
- [공통: Redis 통합](../common/07-redis-integration.md)
- [공통: MDC 추적 로깅](../common/05-mdc-trace-logging.md)
