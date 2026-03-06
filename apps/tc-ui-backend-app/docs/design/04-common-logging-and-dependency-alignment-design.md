> 작성일: 2026-03-06

# tc-ui-backend-app 공통 로깅 단일화 + 3개 App 의존성 정합성 설계 (D04)

## 목적
- `tc-log-starter`를 완전 제거하고 `libs/common/tc-common-logging` 단일 모듈로 공통 로깅 기능을 통합합니다.
- `tc-ui-backend-app`, `tc-business-core-app`, `tc-comm-gateway-app`의 앱 경계 조합 책임을 일관되게 맞춥니다.
- Kafka 의존성 노출 경계를 정리해 어댑터의 직접 SDK 결합을 줄입니다.

## 최신 기준 선언
- 본 문서(`D04`)는 로깅 모듈 구조와 3개 앱 의존성 정합성에 대한 최신 설계 문서입니다.
- `D04`와 이전 문서(`01~03`)가 충돌할 경우 `D04`를 우선 적용합니다.

## 범위
- `tc-log-starter` 제거 및 `tc-common-logging` 신설
- `settings.gradle.kts`, 루트/앱/라이브러리 `build.gradle.kts` 의존성 구조 정렬
- Kafka starter/app 조합 책임 통일
- Kafka adapter의 불필요한 `kafka-clients` 직접 선언 제거
- 아키텍처 가드 테스트 보강

## 비범위
- 외부 REST API 계약 변경
- Kafka payload 스키마 변경
- 인증/인가 시나리오 변경

## 변경 요약

| 구분 | 기존 | 변경 |
|---|---|---|
| 공통 로깅 모듈 | `:libs:log:starter:tc-log-starter` | `:libs:common:tc-common-logging` |
| 로깅 모듈 구조 | starter 기반 | common 단일 모듈 |
| core → starter 결합 | business-core, comm-gateway-core에서 존재 | 제거 (core는 common 로깅에만 의존) |
| 앱 로깅 의존 | `tc-log-starter` | `tc-common-logging` |
| UI/Business Kafka starter 위치 | 각 도메인 starter 내부 | 각 app 모듈에서 명시 선언 |
| Comm Kafka starter 위치 | app 모듈 | app 모듈 유지(3개 앱 통일) |
| Kafka adapter SDK 선언 | `implementation(libs.kafka.clients)` 직접 선언 | 제거(계약/런타임 모듈 경유) |

## 목표 아키텍처

### 1) 로깅 모듈
- 단일 모듈: `:libs:common:tc-common-logging`
- 제공 구성요소:
  - MDC 유틸: `TcLogContext`, `TcMdcTaskDecorator`, `TcMdcKeys`
  - Logback 필터: `AppMdcAbsenceFilter`, `EqpMdcPresenceFilter`
  - 압축/보관: `LogCompressionProperties`, `LogCompressionScheduler`
  - 자동설정: `TcLogAutoConfiguration`
  - 리소스: `logback-spring.xml`, `AutoConfiguration.imports`
- 패키지명은 `com.nori.tc.logging` 유지

### 2) 의존성 방향
- core/adapter → `tc-common-logging`
- app → (도메인 starter + DB starter + Kafka starter + common logging)
- starter는 app 조합 책임을 대체하지 않음

### 3) 3개 앱 조합 규칙
- UI App:
  - `tc-ui-backend-starter`
  - `tc-db-postgres-jpa-starter`
  - `tc-messaging-kafka-starter`
  - `tc-common-logging`
- Business App:
  - `tc-business-core-starter`
  - `tc-db-postgres-jpa-starter`
  - `tc-messaging-kafka-starter`
  - `tc-common-logging`
- Comm App:
  - `tc-comm-gateway-starter`
  - `tc-db-postgres-jpa-starter`
  - `tc-messaging-kafka-starter`
  - `tc-common-logging`

## Public Interfaces / Types
- 제거:
  - `:libs:log:starter:tc-log-starter`
- 추가:
  - `:libs:common:tc-common-logging`
- 유지:
  - `com.nori.tc.logging.*` 공개 타입명 유지 (`TcLogContext`, `TcMdcTaskDecorator`, `TcMdcKeys`)

## Breaking Changes
1. `tc-log-starter` 모듈 경로를 참조하는 모든 Gradle 의존성은 빌드 실패합니다.
2. `settings.gradle.kts`에 등록되지 않은 기존 로그 스타터 경로는 사용할 수 없습니다.
3. UI/Business starter 내부의 Kafka starter 기대 동작이 제거되어 app에서 명시 선언하지 않으면 Kafka 자동구성이 빠집니다.

## 마이그레이션 규칙
1. 로그 의존성
- `implementation(project(":libs:log:starter:tc-log-starter"))`
- → `implementation(project(":libs:common:tc-common-logging"))`

2. Kafka starter 선언 위치
- UI/Business의 `libs/*/starter/*` 에서 `tc-messaging-kafka-starter` 제거
- 각 `apps/*-app/build.gradle.kts`에서 `tc-messaging-kafka-starter` 명시 선언

3. Kafka adapter SDK 선언
- Kafka adapter의 `implementation(libs.kafka.clients)` 제거
- SDK 공개 경계는 `tc-messaging-kafka-contract/runtime` 유지

## 검증 시나리오
1. `rg "tc-log-starter" --glob "**/build.gradle.kts" --glob "settings.gradle.kts"` 결과가 0건인지 확인
2. `:libs:common:tc-common-logging:build` 성공 확인
3. `:libs:business:tc-business-core:build`, `:libs:comm:tc-comm-gateway-core:build` 성공 확인
4. 3개 app 테스트 성공 확인
5. `dependencyInsight`로 3개 app의 `kafka-clients`, `slf4j-api` 유입 경로 확인
6. 아키텍처 가드 테스트로 아래 규칙 검증
- core 모듈의 starter 직접 의존 금지
- app 모듈의 infra 조합 의존 명시
