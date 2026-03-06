> 작성일: 2026-03-06

# tc-ui-backend-app API Prefix `/api` 일원화 설계 (D05)

## 목적
- `tc-ui-backend-app`의 모든 HTTP 진입 경로를 `/api` 접두사 기준으로 통일합니다.
- 현재 예외 경로인 인증(`/auth/*`)과 운영 모니터링(`/actuator/*`)을 `/api` 기준으로 정렬합니다.
- 초기 개발 단계에서 경로 규칙을 고정하여 프론트/백엔드 경로 불일치 재발을 방지합니다.

## 최신 기준 선언
- 본 문서(`D05`)는 API 경로 Prefix 규칙에 대한 최신 설계 문서입니다.
- `D05`와 이전 문서(`01~04`)가 충돌할 경우 `D05`를 우선 적용합니다.

## 범위
- 인증 API 경로를 `/auth/*` → `/api/auth/*`로 전환
- Actuator Web base-path를 `/actuator` → `/api/actuator`로 전환
- Security permitAll 경로 정합성 반영
- 시나리오 테스트/샘플 데이터/문서의 경로 문자열 정합성 반영

## 비범위
- 인증/인가 로직 자체 변경
- 비즈니스 도메인 로직 변경
- DB 스키마 구조 변경

## 목표 경로 규칙
1. 비즈니스 API: `/api/**`
2. 인증 API: `/api/auth/**`
3. 운영 모니터링 API: `/api/actuator/**`
4. 초기 개발 단계 정책: 구 경로(`/auth/**`, `/actuator/**`) 호환 매핑은 제공하지 않고 일괄 전환

## 경로 전환 매핑

| 구분 | 기존 | 변경 |
|---|---|---|
| 로그인 | `POST /auth/login` | `POST /api/auth/login` |
| 로그아웃 | `POST /auth/logout` | `POST /api/auth/logout` |
| 내 정보 | `GET /auth/me` | `GET /api/auth/me` |
| CSRF 발급 | `GET /auth/csrf` | `GET /api/auth/csrf` |
| 헬스체크 | `GET /actuator/health` | `GET /api/actuator/health` |
| Prometheus | `GET /actuator/prometheus` | `GET /api/actuator/prometheus` |

## 변경 필요 목록

### 1) 런타임 코드
- `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/controller/AuthController.java`
  - 클래스 매핑 `@RequestMapping("/auth")` → `@RequestMapping("/api/auth")`
  - Javadoc 예시 경로 `/auth/*` → `/api/auth/*`
- `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/security/UiSecurityConfig.java`
  - `permitAll` 경로 변경
    - `POST /auth/login` → `POST /api/auth/login`
    - `GET /auth/csrf` → `GET /api/auth/csrf`
    - `GET /actuator/health` → `GET /api/actuator/health`
  - 클래스/메서드 Javadoc 경로 문자열 정합성 반영
- `apps/tc-ui-backend-app/src/main/resources/application.yaml`
  - `management.endpoints.web.base-path=/api/actuator` 추가
- `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/dto/request/LoginRequest.java`
  - Javadoc `POST /auth/login` → `POST /api/auth/login`
- `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/dto/response/LoginResponse.java`
  - Javadoc `POST /auth/login` → `POST /api/auth/login`
- `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/dto/response/MeResponse.java`
  - Javadoc `GET /auth/me` → `GET /api/auth/me`
- `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/security/UiTokenAuthenticationFilter.java`
  - 주석의 공개 경로 예시(`/auth/login`) 정합성 반영

### 2) 테스트 코드
- `apps/tc-ui-backend-app/src/test/java/com/nori/tc/apps/uibackend/scenario/UiAuthScenarioTest.java`
  - MockMvc 호출 경로 `/auth/*` → `/api/auth/*`
  - 테스트 설명/로그 문자열 정합성 반영
  - `apiPermission("AUTH_ME_PERM", "/auth/me", "GET")` 등 권한 경로 문자열 변경
- `apps/tc-ui-backend-app/src/test/java/com/nori/tc/apps/uibackend/scenario/UiManagementPagesScenarioTest.java`
  - 로그인 요청 `/auth/login` → `/api/auth/login`
- `apps/tc-ui-backend-app/src/test/java/com/nori/tc/apps/uibackend/scenario/UiBackendScenarioTestSupport.java`
  - 시드 권한 경로 `/auth/me`, `/auth/logout` → `/api/auth/me`, `/api/auth/logout`

### 3) 샘플 데이터/권한 경로
- `docs/db_table/sample_data/postgres_insert_sample_data.sql`
  - `AUTH_ME_PERM` URL `/auth/me` → `/api/auth/me`
  - `AUTH_LOGOUT_PERM` URL `/auth/logout` → `/api/auth/logout`

### 4) 문서
- `apps/tc-ui-backend-app/docs/design/01-system-architecture.md`
- `apps/tc-ui-backend-app/docs/design/03-http-only-cookie-auth-and-dlt-removal-design.md`
- `apps/tc-ui-backend-app/docs/tasks/01-initial-build-plan.md`
- `apps/tc-ui-backend-app/docs/tasks/03-http-only-cookie-auth-and-dlt-removal-build-plan.md`
- `apps/tc-ui-backend-app/build.gradle.kts` 내 Actuator 경로 관련 주석

### 5) 연동 영향(타 리포지토리, 참고)
- `nori-tc-ui/src/features/auth/api/auth.api.ts`
- `nori-tc-ui/src/features/auth/types/auth.types.ts`
- `nori-tc-ui/docs/mcp_command/01-login.md`
- 위 파일들은 백엔드 경로 전환과 동시에 `/auth/*` → `/api/auth/*` 정합성 반영이 필요합니다.

## 위험요소 및 대응
1. 권한 경로 미전환 위험
- 위험: `/api/auth/me` 요청이 DB 권한 매칭 실패로 403 발생
- 대응: 샘플 데이터 + 테스트 시드 경로를 함께 전환하고 시나리오 테스트로 검증

2. Actuator 모니터링 경로 변경 영향
- 위험: 기존 `/actuator/health` 모니터가 실패
- 대응: 모니터링 시스템 경로를 `/api/actuator/health`로 동시 변경

3. 프론트 경로 불일치 위험
- 위험: 프론트가 구 경로를 호출해 404/401 발생
- 대응: 프론트 API 모듈과 로그인 문서를 같은 배포 단위로 전환

## 검증 시나리오
1. `POST /api/auth/login` 성공 및 쿠키 발급 확인
2. `GET /api/auth/csrf` 성공 및 `XSRF-TOKEN` 쿠키 발급 확인
3. `GET /api/auth/me` 인증/권한 케이스별 200/401/403 확인
4. `POST /api/auth/logout` 성공 및 쿠키 삭제 확인
5. `GET /api/actuator/health` 200 확인
6. `GET /actuator/health` 미노출(정책대로 차단/미매핑) 확인

## 구현 정책 메모
- 본 문서는 경로 통일을 위한 설계 기준과 변경 대상 목록만 정의합니다.
- 실제 코드 반영은 `T05` 체크리스트에 따라 별도 작업으로 진행합니다.
