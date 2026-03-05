> 작성일: 2026-03-05

# tc-ui-backend-app HttpOnly Cookie 인증 전환 + Kafka DLT 제거 구현 계획 (T03)

## 진행 방법
- 본 문서는 `D03` 설계를 구현 단위로 분해한 체크리스트 문서입니다.
- 기존 `01`, `02` 문서는 이력으로 유지하고, 인증/보안 및 DLT 관련 최신 작업 기준은 본 문서를 따릅니다.
- 체크박스는 실제 구현 완료 시 즉시 갱신합니다.
- 의존 순서는 `설정/계약 확정 → Web Adapter 전환 → Kafka DLT 제거 → 테스트 갱신 → 검증`으로 고정합니다.

## 기준 문서
- 최신 설계 문서: `docs/design/03-http-only-cookie-auth-and-dlt-removal-design.md`
- 이력 문서:
  - `docs/design/01-system-architecture.md`
  - `docs/design/02-ui-management-pages-design.md`
  - `docs/tasks/01-initial-build-plan.md`
  - `docs/tasks/02-ui-management-pages-build-plan.md`

---

## 문서 반영 완료 항목
- [x] `03-http-only-cookie-auth-and-dlt-removal-design.md` 신규 생성
- [x] `03-http-only-cookie-auth-and-dlt-removal-build-plan.md` 신규 생성

---

## Phase 1: 설정/프로퍼티 계약 전환

### 목표
- Cookie 인증 + CSRF/CORS 적용에 필요한 설정 키를 확정하고, 운영/로컬 프로파일 정책을 분리합니다.

### 작업
- [x] `UiAuthProperties` 확장
  - [x] `cookieName`
  - [x] `cookiePath`
  - [x] `cookieDomain` (옵션)
  - [x] `cookieSecure`
  - [x] `cookieSameSite`
  - [x] `csrfCookieName`
  - [x] `csrfHeaderName`
  - [x] `corsAllowedOrigins`
- [x] `application.yaml`에 프로파일별 설정 import 추가
  - [x] `optional:file:config/tc-ui-backend-${spring.profiles.active}.properties`
- [x] `config/tc-ui-backend-local.properties` 신규 생성
  - [x] `cookieSecure=false`
  - [x] `cookieSameSite=Lax`
- [x] `config/tc-ui-backend.properties` 운영 기본값 반영
  - [x] `cookieSecure=true`
  - [x] `cookieSameSite=None`

---

## Phase 2: 인증 Web Adapter 완전 전환

### 목표
- Header Bearer 인증 의존을 제거하고 Cookie 인증으로 완전 전환합니다.

### 작업
- [ ] `AuthController`
  - [ ] 로그인 성공 시 `Set-Cookie(TC_UI_AUTH, HttpOnly)` 발급
  - [ ] 로그인 응답 DTO에서 `token` 제거
  - [ ] 로그아웃 시 삭제 쿠키(`Max-Age=0`) 발급
  - [ ] `GET /auth/csrf` 엔드포인트 추가
- [ ] `LoginResponse` 계약 변경 (`userPk`, `issuedAt`, `expiresAt`)
- [ ] `UiTokenAuthenticationFilter`
  - [ ] 토큰 추출 로직을 쿠키 전용으로 변경
  - [ ] `Authorization: Bearer` 파싱 로직 제거
- [ ] `UiSecurityConfig`
  - [ ] CSRF 활성화 (`CookieCsrfTokenRepository`)
  - [ ] CORS 활성화 (`allowCredentials=true`, 프로퍼티 기반 Origin)

---

## Phase 3: Kafka DLT 제거

### 목표
- DLT 구성/설정/코드 의존을 전면 제거하고 파싱 실패 정책을 단순화합니다.

### 작업
- [ ] `config/tc-ui-backend.properties`에서 `commands-dlt-*` 전부 삭제
- [ ] `UiKafkaTopicProperties`
  - [ ] DLT 필드 제거
  - [ ] DLT 유효성 검증 제거
  - [ ] DLT getter/setter 제거
  - [ ] 로그 포맷 정리
- [ ] `UiKafkaConfiguration`
  - [ ] DLT `NewTopic` 빈 제거
  - [ ] `DeadLetterPublishingRecoverer` 의존 제거
  - [ ] DLT 전제 로그/주석 제거
- [ ] `UiCommandKafkaSubscriber`
  - [ ] DLT 발행 로직 제거
  - [ ] 파싱 실패 정책을 `WARN + parse_error 메트릭 + ACK`로 고정
  - [ ] DLT 헤더/복사 보조 메서드 제거

---

## Phase 4: 테스트 갱신

### 목표
- 변경된 인증 계약 및 DLT 제거 정책에 맞게 테스트를 갱신합니다.

### 작업
- [ ] 인증 시나리오 테스트
  - [ ] 로그인 응답에 `data.token` 미포함 검증
  - [ ] 로그인 응답 `Set-Cookie` 검증
  - [ ] 쿠키 없는 보호 API `401` 검증
  - [ ] 쿠키 있는 보호 API 인증 통과 검증
  - [ ] 로그아웃 후 쿠키 삭제 및 재사용 불가 검증
- [ ] CSRF 테스트
  - [ ] 상태 변경 요청에서 CSRF 누락 시 `403`
  - [ ] 유효 CSRF 포함 시 정상 처리
- [ ] Kafka 테스트
  - [ ] 파싱 실패 시 DLT 발행 assertion 제거
  - [ ] 파싱 실패 ACK 및 ingress 미호출 검증
  - [ ] topic properties fixture에서 DLT 키 제거

---

## Phase 5: 문서/정합성 검증

### 목표
- 최신 기준 문서와 실제 구현 결과가 일치하는지 확인합니다.

### 작업
- [ ] `D03`와 구현 코드의 API/설정/정책 정합성 점검
- [ ] `T03` 체크리스트 완료 상태 갱신
- [ ] 테스트 실행 결과 기록
- [ ] 운영 적용 시 주의사항(쿠키/CORS/CSRF) 점검 결과 기록

---

## Definition of Done
- [ ] 런타임 코드/설정/테스트에서 `commands-dlt-*`, DLT 발행 로직이 제거됨
- [ ] 인증 경로가 Cookie 기반으로 완전 전환됨 (Bearer 의존 제거)
- [ ] CSRF/CORS 정책이 코드/설정/문서에 일관되게 반영됨
- [ ] 로그인 응답 계약 변경(`token` 제거)이 테스트로 검증됨
- [ ] `D03`, `T03`가 최신 기준 문서로 유지됨

## 구현 가정
- 완전 전환 정책으로 Bearer 하위 호환은 제공하지 않습니다.
- 로컬 환경은 프로파일 분리로 보안 속성을 완화합니다.
- 파싱 실패는 DLT 적재 대신 로그/메트릭 기반 운영 대응을 사용합니다.
