> 작성일: 2026-03-01

# tc-ui-backend-app 시스템 아키텍처 설계

## Context

tc-ui-front와 연동하는 UI 백엔드 앱.
기존 tc-comm-gateway-app / tc-business-core-app과 동일한 헥사고날 아키텍처 적용.

### 핵심 전제
- front ↔ UI-backend: **REST API만 사용. WebSocket/SSE 없음**
- 장비 상태 조회: front 요청 시 UI-backend가 DB에서 조회하여 반환 (Kafka push 없음)
- 인증: DB 세션 토큰 (TcUiAuthSessionEntity 기존 엔티티 활용)
- Redis: Gateway Redis(6379) + Business Redis(6380) 동시 접속 (DLQ 관리)
- **단일 인스턴스 운영 전제**: DeferredResult는 JVM 메모리에 저장되므로 UI-backend는 단일 인스턴스로만 운영. 다중 인스턴스 전환 시 Redis polling으로 통일하는 리팩토링 필요.

### 작업별 응답 패턴

| 작업 | 발행 대상 | UI-backend 응답 방식 | tc.ui.commands 수신 | 비고 |
|------|-----------|---------------------|---------------------|------|
| eqp_create | gateway + business | **DualResponse** 대기 (양쪽 PASS 시 성공) | gateway 1개 + business 1개 (source 필드로 구분) | 한쪽만 FAIL이어도 최종 FAIL |
| eqp_update | gateway + business | **DualResponse** 대기 | 동일 | gateway가 재시작 필요 판단, timeout 여유 있게 설정 |
| eqp_delete | gateway + business | **DualResponse** 대기 | 동일 | |
| eqp_start | **gateway만** | **202 즉시 반환** (fire-and-forget) | gateway 1개 (설비 연결 완료 후 비동기) | Redis polling으로 결과 확인 |
| eqp_end | **gateway만** | **202 즉시 반환** (fire-and-forget) | gateway 1개 (설비 해제 완료 후 비동기) | Redis polling으로 결과 확인 |

---

## 디렉터리 구조

### apps/tc-ui-backend-app

```
apps/tc-ui-backend-app/
├── src/
│   ├── main/
│   │   ├── java/com/nori/tc/apps/uibackend/
│   │   │   └── TcUiBackendApplication.java
│   │   └── resources/
│   │       └── application.yaml
│   └── test/
│       └── java/com/nori/tc/apps/uibackend/
│           └── TcUiBackendApplicationTest.java
├── config/
│   ├── tc-ui-backend.properties     ← UI 전용 (토픽, auth, async 타임아웃, DLQ 키 prefix)
│   ├── tc-messaging.properties      ← Kafka (기존 앱과 동일 형태)
│   ├── tc-redis.properties          ← Redis 2개 설정 (gateway:6379, business:6380)
│   ├── tc-db.properties             ← DB 설정 (optional)
│   └── tc-log.properties            ← 로그 설정 (optional)
│   [없는 것 - gateway 대비: tc-comm.properties (Netty/HSMS/Socket 불필요)]
├── docs/
│   ├── design/
│   │   └── 01-system-architecture.md  ← 이 파일 (시스템 아키텍처 설계)
│   │       신규 설계 시: 02-xxx-design.md 추가
│   └── tasks/
│       └── 01-initial-build-plan.md   ← T01 구현 체크리스트
│           신규 작업 시: 02-xxx-plan.md 추가
└── build.gradle.kts
```

**build.gradle.kts:**
```kotlin
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    java
}

group = "com.nori.tc"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(libs.spring.boot.starter)
    implementation(project(":libs:common:tc-common-logging"))
    implementation(project(":libs:ui:starter:tc-ui-backend-starter"))
    implementation(project(":libs:db:starter:tc-db-postgres-jpa-starter"))
    testImplementation(libs.spring.boot.starter.test)
}

springBoot {
    mainClass.set("com.nori.tc.apps.uibackend.TcUiBackendApplication")
}
```

**application.yaml:**
```yaml
spring:
  application:
    name: tc-ui-backend-app
  main:
    web-application-type: servlet   # HTTP 서버 ON (기존 앱들과 다른 점)
  config:
    import:
      - optional:file:config/tc-db.properties
      - optional:file:config/tc-messaging.properties
      - optional:file:config/tc-redis.properties
      - optional:file:config/tc-log.properties
      - optional:file:config/tc-ui-backend.properties

logging:
  level:
    root: INFO
    com.nori.tc: DEBUG
    org.springframework.kafka: WARN
    org.apache.kafka: WARN
```

---

### libs/ui/ (신규)

```
libs/ui/
├── tc-ui-domain
├── tc-ui-core
├── adapter/
│   ├── tc-ui-db-adapter
│   ├── tc-ui-kafka-adapter
│   ├── tc-ui-redis-adapter
│   └── tc-ui-web-adapter
└── starter/
    └── tc-ui-backend-starter
```

**settings.gradle.kts 추가:**
```kotlin
// libs/ui/
include(":libs:ui:tc-ui-domain")
include(":libs:ui:tc-ui-core")
include(":libs:ui:adapter:tc-ui-db-adapter")
include(":libs:ui:adapter:tc-ui-kafka-adapter")
include(":libs:ui:adapter:tc-ui-redis-adapter")
include(":libs:ui:adapter:tc-ui-web-adapter")
include(":libs:ui:starter:tc-ui-backend-starter")

// apps/
include(":apps:tc-ui-backend-app")
```

---

## 각 모듈 상세 설계

### tc-ui-domain
도메인 POJO (외부 의존성 없음):
- `AuthToken` - 발급된 토큰 및 만료 정보
- `UserPrincipal` - 인증된 사용자 (userPk, userId, 권한 목록)
- `UiTaskResult` - 비동기 결과 저장 (taskId, status, errorCode, errorMsg)

### tc-ui-core
Port 인터페이스와 UseCase:
```
UseCase:
  LoginUseCase
  LogoutUseCase
  ValidateTokenUseCase

Port (DB):
  UserPort               ← tc_user_info CRUD
  SessionPort            ← tc_ui_auth_session CRUD
  PermissionPort         ← tc_ui_permission + 그룹 권한 조회

Port (Kafka 발행):
  UiGatewayEventPublishPort   ← tc.ui.events.gateway
  UiBusinessEventPublishPort  ← tc.ui.events.business

Port (Kafka 수신):
  UiCommandIngressPort   ← tc.ui.commands 수신 처리

Port (DB - route_partition, U13):
  UiGatewayEqpRoutePartitionLookupPort
    - findRoutePartition(eqpId): Optional<Integer>
    - 조회 실패/미배정 → Optional.empty() 반환
    - BusinessEqpRoutePartitionLookupPort와 동일 패턴 (U12 참고)

Port (Redis):
  TokenCachePort         ← 토큰 캐싱 (business Redis 공유)
  AsyncResultStorePort   ← eqp_start/eqp_end 결과 임시 저장

DualResponseRegistry (eqp_create/update/delete용):
  traceId → DualResponseTracker 관리
    - gatewayResult: KafkaUiTaskReplyData (nullable, source=TC-COMM-GATEWAY)
    - businessResult: KafkaUiTaskReplyData (nullable, source=TC-BUSINESS-CORE)
    - 양쪽 모두 수신 시 → DeferredResult.setResult(finalResult)
      최종 결과: 둘 다 PASS → PASS, 하나라도 FAIL → FAIL (로그에 부분 성공 기록)
    - timeout 처리 (tc.ui.backend.async.dual-request-timeout-ms)
```

### tc-ui-db-adapter
기존 tc-db-jpa-common-schema Entity 활용 (신규 Entity 없음):
```
Repository (기존 Entity 사용):
  TcUserInfoRepository
  TcUiAuthSessionRepository
  TcUserGroupMemberRepository
  TcUserGroupPermissionRepository
  TcUiPermissionRepository
  TcEqpRepository              ← tc_eqp.route_partition 조회 (U13)

Port 구현체:
  JpaUserPort
  JpaSessionPort
  JpaPermissionPort
  UiEqpRoutePartitionDbAdapter ← UiGatewayEqpRoutePartitionLookupPort 구현 (U13)
                                  BusinessEqpRoutePartitionDbAdapter와 동일 패턴
```

### tc-ui-kafka-adapter
```
Publisher:
  UiGatewayEventKafkaPublisher  → tc.ui.events.gateway
    - 메시지 타입: KafkaUiTaskMessage (기존 계약, libs/messaging/kafka/tc-messaging-kafka-contract)
    - eventType: EQP_CREATE / EQP_DELETE / EQP_START / EQP_END / EQP_UPDATE 등
    - Key: eqpId
    - [U13] route_partition 명시 발행:
        UiGatewayEqpRoutePartitionLookupPort.findRoutePartition(eqpId) 조회
        route_partition 미배정(null)/음수 → 발행 차단 + ERROR 로그
        ProducerRecord<>(topic, routePartition, key=eqpId, payload) 생성
        KafkaTemplate.send(record) 비동기 콜백
        tracing 헤더 추가: x-trace-id, x-event-type, x-source
    - KafkaTemplate.send(topic, key, value) 직접 호출 금지 (partition 미지정)

  UiBusinessEventKafkaPublisher → tc.ui.events.business
    - 메시지 타입: KafkaUiTaskMessage (기존 계약)
    - eventType: EQP_CREATE / EQP_UPDATE / EQP_DELETE (bean 동기화용)
    - Key: eqpId

Subscriber:
  UiCommandKafkaSubscriber ← tc.ui.commands
    - Consumer Group: tc-ui-backend-group
    - 모드: 표준 GROUP (ASSIGN 불필요)
    - auto-commit: disabled (MANUAL_IMMEDIATE)
    - 메시지 타입: KafkaUiTaskReplyMessage (기존 계약)
    - 응답 출처 구분: metadata.source 필드 (TC-COMM-GATEWAY / TC-BUSINESS-CORE)
    - 처리 분기:
        eventType == EQP_CREATE/UPDATE/DELETE
          → DualResponseRegistry에서 traceId + source로 매칭
          → gatewayResult 또는 businessResult 업데이트
          → 양쪽 모두 수신 완료 시 DeferredResult.setResult(최종결과)
          → 한쪽이 FAIL이면 최종 FAIL (부분 성공 로깅)
        eventType == EQP_START_REP (또는 EQP_START - 구현 전 확인 필요)
          → AsyncResultStore(Redis)에 저장: Key=tc:ui:backend:async:{traceId}
        eventType == EQP_END_REP (또는 EQP_END - 구현 전 확인 필요)
          → AsyncResultStore(Redis)에 저장

Config:
  UiKafkaTopicProperties:
    tc.ui.backend.kafka.gateway-events-topic=tc.ui.events.gateway
    tc.ui.backend.kafka.business-events-topic=tc.ui.events.business
    tc.ui.backend.kafka.commands-topic=tc.ui.commands
```

**KafkaUiTaskReplyEventType 신규 상수 (libs/messaging/kafka/tc-messaging-kafka-contract):**
```java
EQP_START_REP,  // gateway: 설비 연결 완료/실패 후 발행 (기존 EQP_START와 동일할 수 있음 - 확인 필요)
EQP_END_REP,    // gateway: 설비 연결 해제 완료/실패 후 발행 (기존 EQP_END와 동일할 수 있음 - 확인 필요)
```

> **구현 전 확인 TODO**: `GatewayUiDeferredLifecycleReplyService`가 EQP_START reply를 어떤 eventType으로 tc.ui.commands에 발행하는지 확인. 기존과 동일(EQP_START)이면 신규 상수 불필요.

### tc-ui-redis-adapter
**2개 Redis 인스턴스 동시 접속:**

```
UiRedisProperties:
  tc.ui.backend.redis.gateway.host / port / password  → Gateway Redis (6379)
  tc.ui.backend.redis.business.host / port / password → Business Redis (6380)

UiRedisConfiguration:
  @Bean("gatewayRedisTemplate") → Gateway Redis (LettuceConnectionFactory 직접 생성)
  @Bean("businessRedisTemplate") → Business Redis (LettuceConnectionFactory 직접 생성)
  → spring.data.redis.* 미사용

GatewayDlqRedisService (gatewayRedisTemplate):
  - Key 패턴: tc:comm:gateway:dlq:*         → RedisDlqEntry 읽기/삭제
  - Key 패턴: tc:comm:gateway:quarantine:*  → RedisQuarantineEntry 읽기

BusinessDlqRedisService (businessRedisTemplate):
  - Key 패턴: tc:business:core:dlq:*        → RedisBusinessDlqEntry 읽기/삭제

UiSessionCacheService (businessRedisTemplate 공유):
  - Key: tc:ui:backend:session:{token}
  - Value: UserPrincipal (Java 직렬화)
  - TTL: tc.ui.backend.auth.token-cache-ttl-seconds (기본 300초)

AsyncResultStoreService (businessRedisTemplate 공유):
  - Key: tc:ui:backend:async:{traceId}
  - Value: KafkaUiTaskReplyData (STATUS, ERRORCODE, ERRORMSG)
  - TTL: tc.ui.backend.async.result-ttl-seconds (기본 600초)
```

**tc-redis.properties:**
```properties
# Gateway Redis - DLQ, Quarantine 읽기
tc.ui.backend.redis.gateway.host=192.168.0.13
tc.ui.backend.redis.gateway.port=6379
tc.ui.backend.redis.gateway.password=REDACTED_REDIS_PASSWORD

# Business Redis - DLQ 읽기, 토큰 캐시, async 결과 임시 저장
tc.ui.backend.redis.business.host=192.168.0.13
tc.ui.backend.redis.business.port=6380
tc.ui.backend.redis.business.password=REDACTED_REDIS_PASSWORD
```

### tc-ui-web-adapter
```
Spring Security:
  UiTokenAuthenticationFilter (OncePerRequestFilter)
  UiUserDetailsService
  UiSecurityConfig (SecurityFilterChain)

REST 컨트롤러:
  AuthController        → /auth/login, /auth/logout, /auth/me
  EqpController         → /api/eqp/** (CRUD + start/end)
  WorkController        → /api/work/**
  DlqController         → /api/dlq/gateway/**, /api/dlq/business/**
  AsyncResultController → /api/async/{traceId} (eqp_start/end 결과 polling)
```

### tc-ui-backend-starter
```java
@AutoConfiguration
@ComponentScan("com.nori.tc.ui")
@Import({
    UiWebConfiguration.class,
    UiKafkaConfiguration.class,
    UiRedisConfiguration.class
})
public class TcUiBackendAutoConfiguration { }
```

---

## Spring Security 상세 설계 (6개 테이블 활용)

### 테이블별 역할

| 테이블 | Entity | 활용 시점 |
|--------|--------|-----------|
| `tc_user_info` | TcUserInfoEntity | 로그인 시 userId/password 검증, 인증 필터에서 사용자 조회 |
| `tc_ui_auth_session` | TcUiAuthSessionEntity | 로그인 시 세션 생성, 매 요청마다 token 유효성 검증, 로그아웃 시 revoke |
| `tc_user_group_member` | TcUserGroupMemberEntity | 인증 필터에서 사용자의 그룹 ID 목록 조회 |
| `tc_user_group_permission` | TcUserGroupPermissionEntity | 그룹에 매핑된 권한 ID 목록 조회 |
| `tc_ui_permission` | TcUiPermissionEntity | 권한 ID로 리소스 경로/HTTP메서드 조회, URL 인가 판단 |
| `tc_user_group` | TcUserGroupEntity | 그룹 관리 API (조회/생성/수정) |

### 로그인 흐름

```
POST /auth/login { userId, password }
  1. tc_user_info에서 userIdNorm(trim+toLowerCase)으로 조회
  2. status == ACTIVE 확인 → 비활성 시 401
  3. PasswordEncoder.matches(rawPassword, passwordHash) → 불일치 시 401
  4. TcUiAuthSessionEntity 생성:
       token     = SecureRandom 64자 (alphanumeric)
       userPk    = 사용자 PK
       issuedAt  = now
       expiresAt = now + 설정값 (기본 8시간)
       revoked   = false
  5. DB 저장 → token 반환
```

### 매 요청 인증 필터 (UiTokenAuthenticationFilter)

```
Authorization: Bearer {token} 추출
  없음 → SecurityContext 미설정 후 통과 (인가 단계에서 401)

Business Redis 캐시 조회 (Key: tc:ui:backend:session:{token}):
  Hit  → 캐시된 UserPrincipal 사용
  Miss → DB 조회:
    tc_ui_auth_session where token=? and revoked=false and expiresAt > now
    없음 → 401
    tc_user_info.status == ACTIVE 확인
    권한 로드 (3단계 JOIN):
      tc_user_group_member where userPk=?       → groupId 목록
      tc_user_group_permission where groupId in → permId 목록
      tc_ui_permission where permId in and isActive=true → 권한 목록
    UserPrincipal 구성 → Business Redis 캐시 저장 (TTL 300초)

lastSeenAt 비동기 업데이트 (@Async)

SecurityContextHolder에 UsernamePasswordAuthenticationToken 등록
```

### URL 인가

```
tc_ui_permission 필드 활용:
  resourceType = API
  matchType    = PREFIX → request.requestURI.startsWith(permission.resource)
  httpMethod   = null이면 모든 메서드 허용, 값이 있으면 메서드 매칭

예시 권한 데이터:
  permCode=EQP_READ,  resource=/api/eqp,  httpMethod=GET
  permCode=EQP_WRITE, resource=/api/eqp,  httpMethod=null (모든 메서드)
  permCode=DLQ_READ,  resource=/api/dlq,  httpMethod=GET

공개 경로 (인증 없이 허용):
  POST /auth/login
  GET  /actuator/health
```

### 로그아웃

```
POST /auth/logout
  1. SecurityContext에서 현재 token 추출
  2. tc_ui_auth_session.revoked = true
  3. Business Redis 캐시 삭제 (Key: tc:ui:backend:session:{token})
  4. 200 반환
```

---

## Kafka 요청 처리 상세

### eqp_create / eqp_update / eqp_delete (DualResponse DeferredResult)

```
POST /api/eqp (또는 PUT/DELETE /api/eqp/{id})
  1. traceId 발급 (ULID)
  2. DualResponseTracker 등록 (timeout: tc.ui.backend.async.dual-request-timeout-ms)
       - gateway 응답 슬롯 + business 응답 슬롯 (둘 다 비어있음)
  3. KafkaUiTaskMessage 동시 발행:
       - tc.ui.events.gateway: key=eqpId, eventType=EQP_CREATE/UPDATE/DELETE
       - tc.ui.events.business: key=eqpId, eventType=EQP_CREATE/UPDATE/DELETE
  4. HTTP thread 해제, DeferredResult 반환

UiCommandKafkaSubscriber가 tc.ui.commands 수신:
  metadata.traceId + metadata.source 추출
  DualResponseRegistry.record(traceId, source, result)
    → gateway 응답: gatewayResult 세팅
    → business 응답: businessResult 세팅
    → 양쪽 모두 채워지면:
        둘 다 PASS → DeferredResult.setResult(200 OK)
        하나라도 FAIL → DeferredResult.setResult(500 + 상세 오류, 부분 성공 여부 로깅)

timeout 처리:
  → 504 반환, 어느 쪽 응답이 안 왔는지 로깅
  → DualResponseTracker 제거
```

> **eqp_update timeout 고려**: gateway 내부에서 bean 비교 후 재시작이 필요하면 응답이 늦을 수 있음. 별도 timeout 속성(tc.ui.backend.async.update-request-timeout-ms) 분리 검토.

### eqp_start / eqp_end (202 즉시 반환, 비동기 결과 저장)

```
POST /api/eqp/{id}/start (또는 /end)
  1. traceId 발급 (ULID)
  2. KafkaUiTaskMessage 발행:
       topic=tc.ui.events.gateway, key=eqpId, eventType=EQP_START/EQP_END
       [tc.ui.events.business 발행 안 함 - 연결 작업은 gateway 전담]
  3. 즉시 202 Accepted + { "traceId": "..." } 반환

Gateway 처리 (GatewayUiDeferredLifecycleReplyService):
  - 설비 연결/해제 시도 (비동기, gateway 내부 타임아웃 30초)
  - 완료 시 tc.ui.commands에 KafkaUiTaskReplyMessage 발행

UiCommandKafkaSubscriber 수신:
  → Business Redis에 저장:
      Key=tc:ui:backend:async:{traceId}
      Value=KafkaUiTaskReplyData (STATUS, ERRORCODE, ERRORMSG)
      TTL=tc.ui.backend.async.result-ttl-seconds (기본 600초)

Front polling:
  GET /api/async/{traceId}
  → Redis 조회 → 결과 있으면 200 반환
  → 없으면 404 (아직 처리 중 또는 TTL 만료)
```

---

## tc-ui-backend.properties 주요 설정

```properties
# Kafka 토픽
tc.ui.backend.kafka.gateway-events-topic=tc.ui.events.gateway
tc.ui.backend.kafka.business-events-topic=tc.ui.events.business
tc.ui.backend.kafka.commands-topic=tc.ui.commands
spring.kafka.consumer.group-id=tc-ui-backend-group

# DualResponse 대기 타임아웃 (eqp_create/delete/update - gateway + business 양쪽 응답 수집)
tc.ui.backend.async.dual-request-timeout-ms=10000

# 비동기 결과 저장 TTL (eqp_start/end reply - Redis polling용)
tc.ui.backend.async.result-ttl-seconds=600
tc.ui.backend.async.result-key-prefix=tc:ui:backend:async:

# 인증 설정
tc.ui.backend.auth.session-ttl-hours=8
tc.ui.backend.auth.token-cache-ttl-seconds=300

# DLQ 키 prefix (Redis 조회용)
tc.ui.backend.dlq.gateway-key-prefix=tc:comm:gateway:dlq:
tc.ui.backend.dlq.business-key-prefix=tc:business:core:dlq:
tc.ui.backend.dlq.gateway-quarantine-prefix=tc:comm:gateway:quarantine:
```

---

## Kafka 메시지 계약

기존 `libs/messaging/kafka/tc-messaging-kafka-contract`에 정의된 계약을 그대로 사용:
- **발행**: `KafkaUiTaskMessage` (metadata: eventType, timestamp, source, traceId / data: eqpId, interfaceType, equipmentProfile 등)
- **수신**: `KafkaUiTaskReplyMessage` (metadata: eventType, source, traceId / data: eqpId, STATUS, ERRORCODE, ERRORMSG)

---

## 수정이 필요한 기존 파일

| 파일 | 변경 내용 |
|------|-----------|
| `settings.gradle.kts` | libs/ui 모듈 7개 + apps/tc-ui-backend-app 추가 |
| `libs/messaging/kafka/tc-messaging-kafka-contract/.../KafkaUiTaskReplyEventType.java` | EQP_START_REP, EQP_END_REP 상수 추가 (구현 전 기존 reply eventType 확인 후 결정) |

---

## 검증 방법

1. `./gradlew :apps:tc-ui-backend-app:bootRun` → HTTP 포트 기동 확인
2. `POST /auth/login` → 200 + token 반환
3. token 없이 `/api/eqp` → 401
4. 유효 token + 권한 없는 API → 403
5. 유효 token + 권한 있는 API → 200
6. `POST /auth/logout` → 200 → 이전 token 재사용 → 401
7. `POST /api/eqp` (create) → tc.ui.events.gateway + tc.ui.events.business 발행 확인
   → tc.ui.commands에 gateway/business 응답 발행 → 200 최종 반환
8. `POST /api/eqp/{id}/start` → 202 즉시 반환 → tc.ui.events.gateway 발행 확인
   → tc.ui.commands에 start reply → `GET /api/async/{traceId}` → 결과 반환
9. `GET /api/dlq/gateway` → Gateway Redis `tc:comm:gateway:dlq:*` 목록 반환
10. `GET /api/dlq/business` → Business Redis `tc:business:core:dlq:*` 목록 반환

