> 작성일: 2026-03-06

# tc-ui-backend-app 공통 로깅 단일화 + 3개 App 의존성 정합성 구현 계획 (T04)

## 진행 방법
- 본 문서는 `D04` 설계를 구현 단위로 분해한 체크리스트 문서입니다.
- 체크박스는 실제 코드/문서 반영 완료 시점에 즉시 갱신합니다.
- 순서는 `모듈 구조 변경 → 의존성 정렬 → 가드 테스트 → 검증 → 문서 정리`로 고정합니다.

## 기준 문서
- 최신 설계 문서: `docs/design/04-common-logging-and-dependency-alignment-design.md`
- 이력 문서:
  - `docs/design/01-system-architecture.md`
  - `docs/design/02-ui-management-pages-design.md`
  - `docs/design/03-http-only-cookie-auth-and-dlt-removal-design.md`
  - `docs/tasks/01-initial-build-plan.md`
  - `docs/tasks/02-ui-management-pages-build-plan.md`
  - `docs/tasks/03-http-only-cookie-auth-and-dlt-removal-build-plan.md`

---

## 문서 반영 완료 항목
- [x] `04-common-logging-and-dependency-alignment-design.md` 생성
- [x] `04-common-logging-and-dependency-alignment-build-plan.md` 생성

---

## Phase 1: 로깅 모듈 단일화

### 목표
- `tc-log-starter`를 완전히 제거하고 `tc-common-logging` 단일 모듈로 대체합니다.

### 작업
- [x] `settings.gradle.kts`
  - [x] `:libs:log:starter:tc-log-starter` include 제거
  - [x] `:libs:common:tc-common-logging` include 추가
- [x] `libs/common/tc-common-logging` 신규 모듈 생성
  - [x] `build.gradle.kts` 생성
  - [x] `src/main/java/com/nori/tc/logging/*` 이관
  - [x] `src/main/resources/logback-spring.xml` 이관
  - [x] `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 이관
- [x] `libs/log/starter/tc-log-starter` 디렉터리 제거
- [x] 루트 `build.gradle.kts`의 Boot 모듈 대상 log starter 자동 주입 규칙 제거

---

## Phase 2: 의존성 구조 정렬

### 목표
- core는 starter에 직접 의존하지 않고, app은 조합 책임을 명시적으로 갖도록 정렬합니다.

### 작업
- [x] 로깅 의존성 교체
  - [x] `apps/tc-ui-backend-app/build.gradle.kts` → `tc-common-logging`
  - [x] `apps/tc-business-core-app/build.gradle.kts` → `tc-common-logging`
  - [x] `apps/tc-comm-gateway-app/build.gradle.kts` → `tc-common-logging`
  - [x] `libs/business/tc-business-core/build.gradle.kts` → `tc-common-logging`
  - [x] `libs/comm/tc-comm-gateway-core/build.gradle.kts` → `tc-common-logging`
- [x] Kafka starter 선언 위치 통일
  - [x] `libs/ui/starter/tc-ui-backend-starter/build.gradle.kts`에서 Kafka starter 제거
  - [x] `libs/business/starter/tc-business-core-starter/build.gradle.kts`에서 Kafka starter 제거
  - [x] `apps/tc-ui-backend-app/build.gradle.kts`에 Kafka starter 추가
  - [x] `apps/tc-business-core-app/build.gradle.kts`에 Kafka starter 추가
  - [x] `apps/tc-comm-gateway-app/build.gradle.kts`는 app 직접 선언 유지
- [x] Kafka adapter SDK 직접 의존 제거
  - [x] `libs/ui/adapter/tc-ui-kafka-adapter/build.gradle.kts`
  - [x] `libs/business/adapter/tc-business-kafka-adapter/build.gradle.kts`
  - [x] `libs/comm/adapter/tc-comm-gateway-kafka-adapter/build.gradle.kts`
  - [x] `libs/messaging/adapter/tc-messaging-kafka/build.gradle.kts`

---

## Phase 3: 아키텍처 가드 테스트 보강

### 목표
- 재유입 방지를 위한 자동 검증 규칙을 추가합니다.

### 작업
- [x] core 모듈 가드 강화
  - [x] `BusinessCoreArchitectureGuardTest`에 starter 직접 의존 금지 검증 추가
  - [x] `CommGatewayCoreArchitectureGuardTest`에 starter 직접 의존 금지 검증 추가
- [x] app 조합 규칙 가드 추가
  - [x] `tc-ui-backend-app` 의존성 조합 검증 테스트 추가
  - [x] `tc-business-core-app` 의존성 조합 검증 테스트 추가
  - [x] `tc-comm-gateway-app` 의존성 조합 검증 테스트 추가

---

## Phase 4: 검증

### 목표
- 빌드/테스트/의존성 분석으로 변경 정합성을 확인합니다.

### 작업
- [x] 빌드/테스트 실행
  - [x] `./gradlew :libs:common:tc-common-logging:build`
  - [x] `./gradlew :libs:business:tc-business-core:build`
  - [x] `./gradlew :libs:comm:tc-comm-gateway-core:build`
  - [x] `./gradlew :apps:tc-ui-backend-app:test`
  - [x] `./gradlew :apps:tc-business-core-app:test`
  - [x] `./gradlew :apps:tc-comm-gateway-app:test`
- [x] 의존성 경계 확인
  - [x] `rg "tc-log-starter" --glob "**/build.gradle.kts" --glob "settings.gradle.kts"` 결과 0건 확인
  - [x] 3개 app `dependencyInsight` (`kafka-clients`, `slf4j-api`) 확인

---

## Phase 5: 문서/정리

### 목표
- 변경 결과를 문서와 코드 상태에 일치시킵니다.

### 작업
- [x] 빌드 설정(`build.gradle.kts`, `settings.gradle.kts`) 내 `tc-log-starter` 참조 제거
- [x] D04/T04 기준 용어(`tc-common-logging`, app 조합 책임) 반영
- [x] 최종 체크리스트 상태와 실제 검증 결과 동기화

---

## Definition of Done
- [x] `tc-log-starter` 모듈 완전 제거 및 `tc-common-logging` 단일 모듈 전환 완료
- [x] 3개 app 모두 공통 로깅 + Kafka starter를 app 경계에서 명시 조합
- [x] core 모듈의 starter 직접 의존 제거
- [x] Kafka adapter의 불필요한 `kafka-clients` 직접 선언 제거
- [x] 아키텍처 가드 테스트 보강 완료
- [x] 빌드/테스트/의존성 분석 검증 완료 및 결과 반영
