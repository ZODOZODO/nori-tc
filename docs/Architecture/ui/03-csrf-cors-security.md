# 03. CSRF/CORS 보안 (CSRF/CORS Security)

## 개요

`tc-ui-backend-app`은 브라우저 기반 SPA(Single Page Application)와 통신하므로,
두 가지 웹 보안 위협에 대응해야 한다.

- **CSRF (Cross-Site Request Forgery)**: 다른 사이트가 로그인된 쿠키를 이용해 위조 요청을 보내는 공격
- **CORS (Cross-Origin Resource Sharing)**: 프론트엔드와 백엔드의 출처(origin)가 다를 때 브라우저가 요청을 차단하는 정책

---

## CSRF 방어

### Double Submit Cookie 방식

서버는 CSRF 토큰을 **읽기 가능한 쿠키**로 발급하고,
클라이언트는 이 쿠키 값을 읽어 **요청 헤더에 함께 전송**한다.
서버는 쿠키 값과 헤더 값을 비교해 요청의 정합성을 검증한다.

```
[CSRF 토큰 발급]
GET /api/auth/csrf  ← CSRF 쿠키 초기화 트리거
    │
    ▼
서버 응답
    Set-Cookie: XSRF-TOKEN=<token>; Path=/; SameSite=Lax
    (HttpOnly 없음 — JS에서 읽어야 하므로)


[이후 상태 변경 요청 (POST/PUT/DELETE)]
    요청 헤더: X-XSRF-TOKEN: <token>  ← JS가 쿠키를 읽어 헤더에 추가
    쿠키:      XSRF-TOKEN=<token>     ← 브라우저 자동 전송

서버 검증:
    │
    ├── 쿠키의 XSRF-TOKEN 값 추출
    ├── 헤더의 X-XSRF-TOKEN 값 추출
    └── 두 값이 일치 → 정상 요청
        다른 출처(다른 도메인)는 쿠키를 읽을 수 없으므로 → 위조 불가
```

### 왜 Double Submit Cookie인가?

| 방식 | 특징 |
|------|------|
| Synchronizer Token (서버 세션) | 서버 세션 필요 — STATELESS와 충돌 |
| **Double Submit Cookie** | 서버 세션 불필요 — STATELESS 방식과 호환 ✅ |

이 프로젝트는 Spring Security `STATELESS` 세션 정책을 사용하므로,
세션 기반 CSRF 토큰 저장 방식은 사용할 수 없다.

### Spring Security 설정

```java
// UiSecurityConfig
http.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
);
```

| 설정 | 내용 |
|------|------|
| `CookieCsrfTokenRepository` | CSRF 토큰을 쿠키로 저장/발급 |
| `withHttpOnlyFalse()` | JS에서 쿠키를 읽을 수 있도록 HttpOnly 비활성화 |
| `CsrfTokenRequestAttributeHandler` | 헤더(`X-XSRF-TOKEN`)의 원시 토큰 값으로 비교 |

### CSRF 공개 경로 예외

다음 경로는 CSRF 검증을 수행하지 않는다.

```
POST /api/auth/login   ← 로그인 전이므로 토큰 없음
GET  /api/auth/csrf    ← 토큰 발급 자체
GET  /api/actuator/health
OPTIONS /**            ← CORS Preflight
```

---

## SameSite 쿠키 속성 (1차 방어)

인증 쿠키(`authToken`)에는 `SameSite=Lax` 속성을 설정한다.

```
SameSite=Lax:
  - 같은 사이트 요청: 쿠키 전송 O
  - 다른 사이트에서 GET 탑 레벨 내비게이션: 쿠키 전송 O
  - 다른 사이트에서 POST/PUT/DELETE: 쿠키 전송 X  ← CSRF 방어
```

SameSite만으로는 완전하지 않으므로 Double Submit Cookie를 추가 방어층으로 사용한다.

---

## CORS 설정

브라우저는 다른 출처(origin)의 API를 기본적으로 차단한다.
`tc-ui-backend-app`은 허용된 출처 목록을 명시해 CORS를 허용한다.

### 핵심 설정

```java
// UiSecurityConfig
CorsConfiguration config = new CorsConfiguration();
config.setAllowedOrigins(uiCorsProperties.allowedOrigins()); // 명시적 whitelist
config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
config.setAllowedHeaders(List.of("*"));
config.setAllowCredentials(true);  // 쿠키 포함 요청 허용
config.setMaxAge(3600L);           // Preflight 캐시 1시간
```

### `allowCredentials(true)`가 필요한 이유

쿠키 기반 인증에서는 브라우저가 크로스 오리진 요청에도 쿠키를 함께 전송해야 한다.
이를 위해:

1. 서버: `Access-Control-Allow-Credentials: true`
2. 서버: `Access-Control-Allow-Origin`은 와일드카드(`*`) 금지 → 명시적 출처 필요
3. 클라이언트: `fetch()` 또는 `axios`에서 `credentials: 'include'` 설정

```
브라우저
    │  OPTIONS /api/eqp  (Preflight)
    │  Origin: https://nori-tc-ui.example.com
    ▼
서버 응답:
    Access-Control-Allow-Origin:      https://nori-tc-ui.example.com
    Access-Control-Allow-Credentials: true
    Access-Control-Allow-Methods:     GET, POST, PUT, DELETE, OPTIONS
    Access-Control-Max-Age:           3600

    │  실제 요청 허용
    ▼
브라우저 → 실제 POST /api/eqp 전송 (쿠키 포함)
```

### 허용 출처 설정

```properties
# config/tc-ui-backend.properties (예시)
tc.ui.cors.allowed-origins=https://nori-tc-ui.example.com,https://admin.example.com
```

와일드카드(`*`)는 `allowCredentials=true`와 함께 사용 불가하므로,
모든 프론트엔드 출처를 명시적으로 등록해야 한다.

---

## 전체 보안 레이어 정리

```
브라우저 요청
    │
    ├── [Layer 1] SameSite=Lax
    │       다른 사이트에서 POST 시 쿠키 자동 차단
    │
    ├── [Layer 2] CORS
    │       허용되지 않은 출처에서의 JS 요청 차단
    │
    ├── [Layer 3] CSRF Double Submit Cookie
    │       쿠키 값과 헤더 값 비교 검증
    │
    └── [Layer 4] HttpOnly 인증 쿠키
            JS에서 인증 토큰 탈취 차단 (XSS 방어)
```

---

## 운영 포인트

| 항목 | 설명 |
|------|------|
| 출처 화이트리스트 | `allowed-origins` 에 운영 환경 도메인을 정확히 등록해야 함 |
| 개발 환경 | `http://localhost:3000` 등 로컬 출처를 별도 프로파일로 관리 |
| CSRF 예외 경로 | 공개 API 추가 시 CSRF 예외 여부를 함께 검토 |
| X-XSRF-TOKEN 헤더 | 프론트엔드는 Axios interceptor 등으로 자동 주입 필요 |
| Preflight 캐시 | `maxAge=3600` — OPTIONS 요청 빈도 줄임 |

---

## 관련 문서

- [UI: 쿠키 기반 인증](02-cookie-based-authentication.md)
- [UI: REST API 구조 (공개/보호 경로)](01-rest-api-structure.md)
