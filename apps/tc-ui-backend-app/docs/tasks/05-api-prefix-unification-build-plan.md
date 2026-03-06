> 작성일: 2026-03-06

# tc-ui-backend-app API Prefix `/api` 일원화 구현 계획 (T05)

## 진행 방법
- 본 문서는 `D05` 설계를 구현 단위로 분해한 체크리스트 문서입니다.
- 현재 문서는 구현 전 계획 수립 단계이며, 체크박스는 실제 반영 후 갱신합니다.
- 순서는 `계약 확정 → 런타임 변경 → 테스트/데이터 정합성 → 문서/연동 반영 → 검증`으로 고정합니다.

## 기준 문서
- 최신 설계 문서: `docs/design/05-api-prefix-unification-design.md`
- 이력 문서:
  - `docs/design/01-system-architecture.md`
  - `docs/design/03-http-only-cookie-auth-and-dlt-removal-design.md`
  - `docs/tasks/01-initial-build-plan.md`
  - `docs/tasks/03-http-only-cookie-auth-and-dlt-removal-build-plan.md`

---

## 문서 반영 완료 항목
- [x] `05-api-prefix-unification-design.md` 생성
- [x] `05-api-prefix-unification-build-plan.md` 생성

---

## Phase 1: 경로 계약 확정

### 목표
- `/api` 단일 접두사 규칙을 확정하고 전환 기준을 명문화합니다.

### 작업
- [x] 인증 경로 전환 계약 확정
  - [x] `/auth/login` → `/api/auth/login`
  - [x] `/auth/logout` → `/api/auth/logout`
  - [x] `/auth/me` → `/api/auth/me`
  - [x] `/auth/csrf` → `/api/auth/csrf`
- [x] Actuator 경로 전환 계약 확정
  - [x] `/actuator/health` → `/api/actuator/health`
  - [x] `/actuator/prometheus` → `/api/actuator/prometheus`
- [x] 호환 정책 확정
  - [x] 초기 개발 단계 정책에 따라 구 경로 호환 미제공 여부 최종 확정

---

## Phase 2: 런타임 코드 변경

### 목표
- 백엔드 런타임 매핑과 보안 예외 경로를 `/api` 기준으로 일치시킵니다.

### 작업
- [x] `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/controller/AuthController.java`
  - [x] `@RequestMapping("/api/auth")`로 변경
  - [x] 클래스/메서드 Javadoc 경로 정합성 반영
- [x] `libs/ui/adapter/tc-ui-web-adapter/src/main/java/com/nori/tc/ui/adapters/web/security/UiSecurityConfig.java`
  - [x] `requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()`
  - [x] `requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()`
  - [x] `requestMatchers(HttpMethod.GET, "/api/actuator/health").permitAll()`
  - [x] Javadoc 공개 경로 설명 정합성 반영
- [x] `apps/tc-ui-backend-app/src/main/resources/application.yaml`
  - [x] `management.endpoints.web.base-path=/api/actuator` 추가
- [x] DTO/필터 주석 정합성
  - [x] `LoginRequest.java`
  - [x] `LoginResponse.java`
  - [x] `MeResponse.java`
  - [x] `UiTokenAuthenticationFilter.java`

---

## Phase 3: 테스트 및 시드 데이터 정합성

### 목표
- 경로 전환으로 인한 회귀를 테스트/시드 레벨에서 차단합니다.

### 작업
- [x] `apps/tc-ui-backend-app/src/test/java/com/nori/tc/apps/uibackend/scenario/UiAuthScenarioTest.java`
  - [x] MockMvc `/auth/*` 호출을 `/api/auth/*`로 전환
  - [x] 테스트 설명/로그 메시지 경로 정합성 반영
  - [x] `apiPermission("AUTH_ME_PERM", "/api/auth/me", "GET")`로 전환
- [x] `apps/tc-ui-backend-app/src/test/java/com/nori/tc/apps/uibackend/scenario/UiManagementPagesScenarioTest.java`
  - [x] 로그인 요청 `/api/auth/login`으로 전환
- [x] `apps/tc-ui-backend-app/src/test/java/com/nori/tc/apps/uibackend/scenario/UiBackendScenarioTestSupport.java`
  - [x] 권한 시드 `/api/auth/me`, `/api/auth/logout`로 전환
- [x] `docs/db_table/sample_data/postgres_insert_sample_data.sql`
  - [x] `AUTH_ME_PERM` URL `/api/auth/me`로 전환
  - [x] `AUTH_LOGOUT_PERM` URL `/api/auth/logout`로 전환

---

## Phase 4: 문서 정합성 반영

### 목표
- 기존 문서의 구 경로 표기를 최신 계약(`/api` 통일)으로 갱신합니다.

### 작업
- [x] `apps/tc-ui-backend-app/docs/design/01-system-architecture.md`
- [x] `apps/tc-ui-backend-app/docs/design/03-http-only-cookie-auth-and-dlt-removal-design.md`
- [x] `apps/tc-ui-backend-app/docs/tasks/01-initial-build-plan.md`
- [x] `apps/tc-ui-backend-app/docs/tasks/03-http-only-cookie-auth-and-dlt-removal-build-plan.md`
- [x] `apps/tc-ui-backend-app/build.gradle.kts` 내 Actuator 경로 주석

---

## Phase 5: 연동 반영(타 리포지토리)

### 목표
- 프론트 호출 경로와 백엔드 계약을 동시 정합화합니다.

### 작업
- [x] `nori-tc-ui/src/features/auth/api/auth.api.ts`
  - [x] `/auth/csrf` → `/api/auth/csrf`
  - [x] `/auth/login` → `/api/auth/login`
- [x] `nori-tc-ui/src/features/auth/types/auth.types.ts`
  - [x] Javadoc 경로 표기 갱신
- [x] `nori-tc-ui/docs/mcp_command/01-login.md`
  - [x] API 경로 설명 갱신

---

## Phase 6: 검증

### 목표
- 경로 통일 이후 인증/보안/운영 엔드포인트가 정상 동작하는지 확인합니다.

### 작업
- [x] `./gradlew :apps:tc-ui-backend-app:test`
- [x] `POST /api/auth/login` 실동작 확인
- [ ] `GET /api/auth/csrf` + CSRF 헤더 흐름 확인
- [x] `GET /api/auth/me` 인증/권한 케이스 확인
- [x] `POST /api/auth/logout` 및 쿠키 삭제 확인
- [ ] `GET /api/actuator/health` 확인
- [x] 구 경로(`/auth/*`, `/actuator/*`) 비사용 정책 확인

---

## Definition of Done
- [x] 인증/운영 경로 예외 없이 모든 HTTP 진입 경로가 `/api` 접두사 기준으로 정렬됨
- [x] Security permitAll/CSRF/CORS 정책이 신규 경로와 정합함
- [x] 시나리오 테스트 및 샘플 데이터 권한 경로가 신규 경로와 정합함
- [x] `tc-ui-backend-app` 문서 및 프론트 연동 문서가 신규 계약으로 동기화됨
- [ ] 로그인/헬스체크/모니터링 실동작 검증 결과가 기록됨

## 검증 메모 (2026-03-06)
- 자동 검증
  - `./gradlew :apps:tc-ui-backend-app:test` 실행 완료 (`BUILD SUCCESSFUL`)
  - `UiAuthScenarioTest` 기준 `/api/auth/login`, `/api/auth/me`, `/api/auth/logout` 경로 검증 확인
- 미수행 수동 검증
  - `GET /api/auth/csrf` 실호출 + CSRF 헤더 end-to-end 확인
  - `GET /api/actuator/health` 실호출 확인
