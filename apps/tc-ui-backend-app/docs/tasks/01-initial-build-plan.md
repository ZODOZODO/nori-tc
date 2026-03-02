> 작성일: 2026-03-01 | 최종수정: 2026-03-02 (Phase 4 완료)

# tc-ui-backend-app 초기 구현 Plan List (T01)

## 진행 방법
- 각 Phase를 순서대로 완료 후 다음 Phase 진행
- 체크박스는 구현 완료 후 즉시 체크
- Phase 간 의존 관계: domain → core → adapter 3종 → starter → app → 검증
- 상세 설계는 `docs/design/01-system-architecture.md` 참조
- Phase 완료 시 상단 최종수정일 갱신

---

## Phase 0: 프로젝트 뼈대 구성 ✅

### settings.gradle.kts 수정
- [x] libs/ui 모듈 7개 추가
  ```
  include(":libs:ui:tc-ui-domain")
  include(":libs:ui:tc-ui-core")
  include(":libs:ui:adapter:tc-ui-db-adapter")
  include(":libs:ui:adapter:tc-ui-kafka-adapter")
  include(":libs:ui:adapter:tc-ui-redis-adapter")
  include(":libs:ui:adapter:tc-ui-web-adapter")
  include(":libs:ui:starter:tc-ui-backend-starter")
  ```
- [x] apps/tc-ui-backend-app 추가 (이미 등록되어 있음)

### libs/ui 모듈별 build.gradle.kts 뼈대 생성 (의존성 포함)
- [x] `libs/ui/tc-ui-domain/build.gradle.kts`
- [x] `libs/ui/tc-ui-core/build.gradle.kts`
- [x] `libs/ui/adapter/tc-ui-db-adapter/build.gradle.kts`
- [x] `libs/ui/adapter/tc-ui-kafka-adapter/build.gradle.kts`
- [x] `libs/ui/adapter/tc-ui-redis-adapter/build.gradle.kts`
- [x] `libs/ui/adapter/tc-ui-web-adapter/build.gradle.kts`
- [x] `libs/ui/starter/tc-ui-backend-starter/build.gradle.kts`

### apps/tc-ui-backend-app 뼈대 생성
- [x] `apps/tc-ui-backend-app/build.gradle.kts`
- [x] `apps/tc-ui-backend-app/src/main/java/com/nori/tc/apps/uibackend/TcUiBackendApplication.java`
- [x] `apps/tc-ui-backend-app/src/main/resources/application.yaml` (web-application-type: servlet 수정)
- [x] `apps/tc-ui-backend-app/src/test/java/com/nori/tc/apps/uibackend/TcUiBackendApplicationTest.java`

### config 템플릿 파일 생성
- [x] `apps/tc-ui-backend-app/config/tc-ui-backend.properties` (토픽, auth, async 타임아웃, DLQ 키 prefix)
- [x] `apps/tc-ui-backend-app/config/tc-messaging.properties` (Kafka broker 설정)
- [x] `apps/tc-ui-backend-app/config/tc-redis.properties` (gateway:6379, business:6380 각각 - dual Redis 템플릿)
- [x] `apps/tc-ui-backend-app/config/tc-log.properties` (로그 설정)

### 빌드 확인
- [x] `./gradlew :apps:tc-ui-backend-app:compileJava` → BUILD SUCCESSFUL

---

## Phase 1: tc-ui-domain ✅

도메인 POJO, 외부 의존성 없음.

- [x] `AuthToken` — token(String), userPk(Long), issuedAt, expiresAt (`isExpired()`, `isValid()` 헬퍼 포함)
- [x] `UserPrincipal` — userPk, userId, permissionCodes(Set<String>) (`hasPermission()`, `hasAnyPermission()`, `hasNoPermission()` 헬퍼 포함)
- [x] `UiTaskStatus` — PASS / FAIL enum (별도 파일)
- [x] `UiTaskResult` — traceId, source, status, errorCode, errorMsg (`pass()`, `fail()` 팩토리 + `isSuccess()`, `isFailed()` 헬퍼 포함)

---

## Phase 2: tc-ui-core ✅

Port 인터페이스, UseCase, DualResponseRegistry.

### Port (DB)
- [x] `UserPort` — findByUserIdNorm(String), findByUserPk(long)
- [x] `SessionPort` — save, findValidByToken(String), revoke(String), updateLastSeenAt(String, OffsetDateTime)
- [x] `PermissionPort` — findPermissionCodesByUserPk(long): Set<String>
- [x] `PasswordVerifierPort` — matches(rawPassword, encodedPassword): boolean (BCrypt 추상화)

### Port (route_partition, U13)
- [x] `UiGatewayEqpRoutePartitionLookupPort` — findRoutePartition(eqpId): Optional<Integer>

### Port (Kafka 발행)
- [x] `UiGatewayEventPublishPort` — publish(KafkaUiTaskMessage)
- [x] `UiBusinessEventPublishPort` — publish(KafkaUiTaskMessage)

### Port (Kafka 수신)
- [x] `UiCommandIngressPort` — handle(KafkaUiTaskReplyMessage)

### Port (Redis)
- [x] `TokenCachePort` — get(token): Optional<UserPrincipal>, put(token, principal), evict(token)
- [x] `AsyncResultStorePort` — save(traceId, reply), get(traceId): Optional<KafkaUiTaskReplyMessage>

### UseCase
- [x] `LoginUseCase` — execute(userId, rawPassword): AuthToken
- [x] `LogoutUseCase` — execute(token)
- [x] `ValidateTokenUseCase` — execute(token): UserPrincipal

### DualResponseRegistry
- [x] `UiDualTaskFinalResult` — traceId, success, gatewayResult, businessResult record (hasPartialFailure, firstFailedResult 헬퍼 포함)
- [x] `DualResponseRegistry` — register(traceId, timeoutMs): CompletableFuture<>, record(traceId, source, result)
  - 내부 DualResponseTracker (synchronized record + volatile 필드)
  - 양쪽 모두 수신 시 CompletableFuture 자동 완료
  - 한쪽 FAIL → 최종 FAIL + 부분 실패 WARN 로그

### Service
- [x] `UiCommandIngressService` — UiCommandIngressPort 구현 (eventType 기반 라우팅)
  - EQP_CREATE/UPDATE/DELETE → DualResponseRegistry
  - EQP_START/END → AsyncResultStorePort

### Properties
- [x] `UiAuthProperties` — @ConfigurationProperties(prefix="tc.ui.backend.auth")
  - sessionTtlHours (기본 8), tokenCacheTtlSeconds (기본 300)

### 예외
- [x] `UiAuthenticationException` — 인증 실패 시 발생 (HTTP 401 변환용)

### 빌드 확인
- [x] `./gradlew :libs:ui:tc-ui-core:compileJava` → BUILD SUCCESSFUL

---

## Phase 3: tc-ui-db-adapter ✅

**설계 원칙**: JPA 기술에 직접 의존하지 않고 `tc-db-core` Store 추상화를 통해 DB에 접근합니다.
gateway-db-adapter / business-db-adapter와 동일한 구조입니다.

> **Phase 3 구조 교정 이력 (2026-03-01)**
> 초기 구현 시 JPA Repository / Entity / Mapper를 어댑터 내부에서 직접 참조하는
> 잘못된 구조로 작성되었습니다. `tc-db-core` Store 추상화 계층을 거치도록 교정하였습니다.
>
> - `repository/` 폴더 전체 삭제 (UiAuth/UserGroupMember/UserGroupPermission/UiPermission Repository)
> - JPA 직접 의존성 제거: `implementation(tc-db-jpa-common-schema)`, `compileOnly(spring-boot-starter-data-jpa)`
> - 필요한 Store 메서드를 `tc-db-core` 인터페이스에 추가하고 `tc-db-jpa-common-schema`에 구현 추가

### tc-db-core Store 인터페이스 메서드 추가
- [x] `TcUiAuthSessionStore` — findValidByToken(token), revokeByToken(token), updateLastSeenAt(token, lastSeenAt)
- [x] `TcUserGroupMemberStore` — findAllByUserPk(userPk) (페이징 없음 overload)
- [x] `TcUserGroupPermissionStore` — findAllByGroupIdIn(Collection<Long> groupIds)
- [x] `TcUiPermissionStore` — findAllActiveByPermIdIn(Collection<Long> permIds)

### tc-db-jpa-common-schema JPA 구현 추가
- [x] `TcUiAuthSessionJpaRepository` — findByTokenAndRevokedFalseAndExpiresAtAfter, revokeByToken(@Modifying), updateLastSeenAt(@Modifying)
- [x] `TcUserGroupPermissionJpaRepository` — findByGroupIdIn(Collection<Long>)
- [x] `TcUiAuthSessionJpaStore` — findValidByToken, revokeByToken, updateLastSeenAt 구현
- [x] `TcUserGroupMemberJpaStore` — findAllByUserPk (no-page) 구현 (Criteria API)
- [x] `TcUserGroupPermissionJpaStore` — findAllByGroupIdIn 구현
- [x] `TcUiPermissionJpaStore` — findAllActiveByPermIdIn 구현 (Criteria API, IN + isActive=true)

### Port 구현체
- [x] `JpaUserPort` — UserPort 구현 (TcUserInfoStore 사용)
- [x] `JpaSessionPort` — SessionPort 구현 (TcUiAuthSessionStore 사용)
- [x] `JpaPermissionPort` — PermissionPort 구현 (TcUserGroupMemberStore → TcUserGroupPermissionStore → TcUiPermissionStore 3단계)
- [x] `UiEqpRoutePartitionDbAdapter` — UiGatewayEqpRoutePartitionLookupPort 구현 (U13)
  - BusinessEqpRoutePartitionDbAdapter와 동일 패턴 적용 (TcEqpStore 활용)
  - 조회 실패/route_partition null → Optional.empty()

### build.gradle.kts
- [x] `implementation(project(":libs:db:tc-db-core"))` — Store/Domain 인터페이스 계약
- [x] JPA 직접 의존성 없음 (`tc-db-jpa-common-schema`, `spring-boot-starter-data-jpa` 미포함)

### 빌드 확인
- [x] `./gradlew :libs:ui:adapter:tc-ui-db-adapter:build` → BUILD SUCCESSFUL
- [x] `./gradlew :apps:tc-ui-backend-app:build` → BUILD SUCCESSFUL

---

## Phase 4: tc-ui-redis-adapter ✅

2개 Redis 인스턴스 동시 접속.

### 설정
- [x] `UiRedisProperties` — @ConfigurationProperties: gateway(host/port/password) + business(host/port/password)
- [x] `UiRedisConfiguration` — @Bean("gatewayRedisTemplate"), @Bean("businessRedisTemplate")
  - 각각 직접 LettuceConnectionFactory 생성 (spring.data.redis.* 미사용)
  - @Bean("gatewayLettuceConnectionFactory"), @Bean("businessLettuceConnectionFactory") — destroyMethod="destroy" 수명 주기 관리

### Service
- [x] `GatewayDlqRedisService` (gatewayRedisTemplate)
  - `tc:comm:gateway:dlq:*` → RedisDlqEntry 읽기/삭제 (SCAN 커서 방식)
  - `tc:comm:gateway:quarantine:*` → RedisQuarantineEntry 읽기
- [x] `BusinessDlqRedisService` (businessRedisTemplate)
  - `tc:business:core:dlq:*` → RedisBusinessDlqEntry 읽기/삭제 (SCAN 커서 방식)
- [x] `UiSessionCacheService` (businessRedisTemplate)
  - Key: `tc:ui:backend:session:{token}`, TTL: 설정값(기본 300초)
  - TokenCachePort 구현
  - RedisUiSessionEntry (Serializable 래퍼) 통해 UserPrincipal 저장
- [x] `AsyncResultStoreService` (businessRedisTemplate)
  - Key: `tc:ui:backend:async:{traceId}`, TTL: 설정값(기본 600초)
  - AsyncResultStorePort 구현
  - RedisUiAsyncResultEntry (Serializable 래퍼) 통해 KafkaUiTaskReplyMessage 저장
  - UiAsyncProperties(@ConfigurationProperties "tc.ui.backend.async") 통해 TTL 바인딩

### 설계 참고 사항
- DLQ 역직렬화를 위해 `tc-comm-gateway-redis-adapter`, `tc-business-redis-adapter` 의존성 추가
  (JDK 직렬화 특성상 원본 클래스가 런타임 classpath에 필요)
- UserPrincipal, KafkaUiTaskReplyMessage는 record 타입(비직렬화)이므로
  RedisUiSessionEntry, RedisUiAsyncResultEntry Serializable 래퍼 클래스로 변환 저장

### 빌드 확인
- [x] `./gradlew :libs:ui:adapter:tc-ui-redis-adapter:compileJava` → BUILD SUCCESSFUL
- [x] `./gradlew :apps:tc-ui-backend-app:build` → BUILD SUCCESSFUL

---

## Phase 5: tc-ui-kafka-adapter

### 설정
- [ ] `UiKafkaTopicProperties` — @ConfigurationProperties: gateway-events-topic, business-events-topic, commands-topic

### Publisher
- [ ] `UiGatewayEventKafkaPublisher` → `tc.ui.events.gateway`
  - **[U13] route_partition 명시 발행 필수**:
    1. `UiGatewayEqpRoutePartitionLookupPort.findRoutePartition(eqpId)` 조회
    2. empty(null)/음수 → 발행 차단 + ERROR 로그 (eqpId, topic, 사유 포함)
    3. `ProducerRecord<>(topic, routePartition, key=eqpId, payload)` 생성
    4. tracing 헤더 추가 (x-trace-id, x-event-type, x-source)
    5. `KafkaTemplate.send(record)` 비동기 콜백 (성공/실패 DEBUG/ERROR 로그)
  - `KafkaTemplate.send(topic, key, value)` 직접 호출 금지
  - UiGatewayEventPublishPort 구현
- [ ] `UiBusinessEventKafkaPublisher` → `tc.ui.events.business`
  - route_partition 불필요 (business 구독자 GROUP 모드, key hash 라우팅)
  - KafkaTemplate.send(topic, key=eqpId, payload) 일반 발행
  - UiBusinessEventPublishPort 구현

### Subscriber
- [ ] `UiCommandKafkaSubscriber` ← `tc.ui.commands`
  - Consumer Group: `tc-ui-backend-group`
  - auto-commit: disabled (MANUAL_IMMEDIATE)
  - eventType 분기:
    - EQP_CREATE / EQP_UPDATE / EQP_DELETE → DualResponseRegistry.record(traceId, source, result)
    - EQP_START_REP / EQP_END_REP (또는 EQP_START / EQP_END - Phase 9에서 확인) → AsyncResultStorePort.save(traceId, result)
  - metadata.source로 출처 구분 (TC-COMM-GATEWAY / TC-BUSINESS-CORE)
  - UiCommandIngressPort 구현

---

## Phase 6: tc-ui-web-adapter

### Spring Security
- [ ] `UiTokenAuthenticationFilter` (OncePerRequestFilter)
  - Authorization: Bearer {token} 추출
  - TokenCachePort (Redis) → hit: 캐시 UserPrincipal 사용
  - miss: SessionPort (DB) → 유효 세션 확인 → PermissionPort → UserPrincipal 구성 → 캐시 저장
  - lastSeenAt @Async 업데이트
  - SecurityContextHolder 등록
- [ ] `UiSecurityConfig` (SecurityFilterChain)
  - 공개 경로: POST /auth/login, GET /actuator/health
  - CSRF 비활성화, session stateless
  - UiTokenAuthenticationFilter 등록
  - URL 인가: TcUiPermissionEntity.matchType=PREFIX, httpMethod null이면 전체 허용

### REST 컨트롤러
- [ ] `AuthController`
  - POST /auth/login — LoginUseCase 호출, AuthToken 반환
  - POST /auth/logout — LogoutUseCase 호출
  - GET /auth/me — 현재 UserPrincipal 반환
- [ ] `EqpController`
  - POST /api/eqp — eqp_create, DualResponse DeferredResult
  - PUT /api/eqp/{id} — eqp_update, DualResponse DeferredResult
  - DELETE /api/eqp/{id} — eqp_delete, DualResponse DeferredResult
  - POST /api/eqp/{id}/start — eqp_start, 202 즉시 반환 + traceId
  - POST /api/eqp/{id}/end — eqp_end, 202 즉시 반환 + traceId
- [ ] `AsyncResultController`
  - GET /api/async/{traceId} — AsyncResultStorePort.get() → 결과 있으면 200, 없으면 404
- [ ] `DlqController`
  - GET /api/dlq/gateway — GatewayDlqRedisService 목록
  - DELETE /api/dlq/gateway/{dlqId} — GatewayDlqRedisService 삭제
  - GET /api/dlq/business — BusinessDlqRedisService 목록
  - DELETE /api/dlq/business/{dlqId} — BusinessDlqRedisService 삭제

---

## Phase 7: tc-ui-backend-starter

- [ ] `TcUiBackendAutoConfiguration`
  ```java
  @AutoConfiguration
  @ComponentScan("com.nori.tc.ui")
  @Import({ UiWebConfiguration.class, UiKafkaConfiguration.class, UiRedisConfiguration.class })
  ```
- [ ] `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - `com.nori.tc.ui.starter.TcUiBackendAutoConfiguration` 등록

---

## Phase 8: 통합 확인 및 config 완성

### config 실제 값 작성
- [ ] `config/tc-ui-backend.properties` — Kafka 토픽, timeout, TTL, prefix 실제 값 설정
- [ ] `config/tc-messaging.properties` — Kafka broker 주소 설정
- [ ] `config/tc-redis.properties` — gateway(6379), business(6380) 실제 host/password 설정
- [ ] `config/tc-db.properties` — DB 접속 정보 설정

### 빌드 및 기동 확인
- [ ] `./gradlew :apps:tc-ui-backend-app:bootRun` 기동 성공 (HTTP 포트 정상 오픈)
- [ ] `./gradlew build` 전체 빌드 오류 없음

---

## Phase 9: Messaging contract 확인 및 보완

> **구현 전 확인 필수**: `GatewayUiDeferredLifecycleReplyService`가 EQP_START reply를
> tc.ui.commands에 발행할 때 사용하는 eventType 확인.
> 기존 EQP_START 그대로이면 신규 상수 불필요, 새 값이면 추가 필요.

- [ ] `libs/comm/...GatewayUiDeferredLifecycleReplyService.java` reply eventType 코드 확인
- [ ] 필요 시 `KafkaUiTaskReplyEventType`에 EQP_START_REP, EQP_END_REP 상수 추가
- [ ] `UiCommandKafkaSubscriber` 분기 로직 확인 및 최종 조정

---

## Phase 10: 시나리오 검증

- [ ] POST /auth/login → 200 + token 반환
- [ ] Bearer token 없이 /api/eqp → 401
- [ ] 유효 token + 권한 없는 API → 403
- [ ] 유효 token + 권한 있는 API → 200
- [ ] POST /auth/logout → 200 → 이전 token 재사용 → 401
- [ ] POST /api/eqp (create)
  - tc.ui.events.gateway 발행 확인 (route_partition 명시)
  - tc.ui.events.business 발행 확인
  - tc.ui.commands에 gateway + business 응답 수신 → DualResponse → 200 반환
- [ ] POST /api/eqp/{id}/start → 202 즉시 반환 + traceId
  - tc.ui.events.gateway 발행 확인 (route_partition 명시)
  - gateway가 tc.ui.commands에 reply 발행
  - GET /api/async/{traceId} → 결과 반환
- [ ] GET /api/dlq/gateway → tc:comm:gateway:dlq:* 목록 반환
- [ ] GET /api/dlq/business → tc:business:core:dlq:* 목록 반환
- [ ] route_partition 미배정 eqpId로 gateway 발행 시도 → 발행 차단 + ERROR 로그 확인 (U13)

---

## 진행 현황

| Phase | 상태 | 비고 |
|-------|------|------|
| Phase 0 | ✅ 완료 | |
| Phase 1 | ✅ 완료 | |
| Phase 2 | ✅ 완료 | |
| Phase 3 | ✅ 완료 | Store 추상화 구조 교정 완료 |
| Phase 4 | ✅ 완료 | DLQ/Quarantine 조회, 토큰 캐시, 비동기 결과 저장 |
| Phase 5 | ⬜ 대기 | |
| Phase 6 | ⬜ 대기 | |
| Phase 7 | ⬜ 대기 | |
| Phase 8 | ⬜ 대기 | |
| Phase 9 | ⬜ 대기 | Phase 5 UiCommandKafkaSubscriber 구현 전 선행 확인 권장 |
| Phase 10 | ⬜ 대기 | |
