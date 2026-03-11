# nori-tc 설계 및 구현 코드 리뷰

| 항목 | 내용 |
|------|------|
| 최초 작성 | 2026-03-03 |
| 최종 업데이트 | 2026-03-04 |
| 작성자 | Claude Code 리뷰 |
| 대상 브랜치 | main |
| 작업 플랜 | [tasks/01-initial-code-review.md](../tasks/01-initial-code-review.md) |

---

## 목차

1. [총평](#총평)
2. [아키텍처 및 구조](#1-아키텍처-및-구조)
3. [객체지향 설계](#2-객체지향-설계)
4. [의존성 관리](#3-의존성-관리)
5. [코드 품질](#4-코드-품질)
6. [예외 처리](#5-예외-처리)
7. [시스템 안정성 및 데이터 무결성](#6-시스템-안정성-및-데이터-무결성)
8. [성능 및 확장성](#7-성능-및-확장성)
9. [메모리 누수](#8-메모리-누수)
10. [보안](#9-보안)
11. [운영 가시성](#10-운영-가시성-추가-평가)
12. [테스트 커버리지](#11-테스트-커버리지-추가-평가)
13. [API 설계](#12-api-설계-추가-평가)
14. [개선 우선순위 요약](#개선-우선순위-요약)

> **변경 이력**
> - 2026-03-04: [ARCH-04] Dual 발행 fire-and-forget, [SEC-04] 평문 비밀번호 git 커밋,
>   [SEC-05] 플러그인 서명 검증 미구현, [QUALITY-04] AuthController 강제 캐스팅,
>   [EX-05] EqpSequentialProcessor 무로그 예외 삼킴 추가 (교차 리뷰 보완)

---

## 총평

헥사고날 아키텍처 원칙을 멀티모듈 Gradle로 구현한 방향성은 명확하며,
Domain → Core(Port) → Adapter → Starter 의 계층 구조, Port.noop() 패턴,
CompletableFuture + DeferredResult 조합 등 고수준 설계 의도는 잘 반영되어 있습니다.

그러나 **핵심 운영 시나리오에서 치명적인 설계 결함**이 존재하며,
특히 분산 환경 가정 오류 (DualResponseRegistry 인메모리 설계),
보안 취약점 (JDK 직렬화 역직렬화 공격),
**평문 비밀번호가 git에 커밋되어 추적 중인 상태**,
**Dual 발행의 브로커 실패를 caller가 인지할 수 없는 구조**는
운영 투입 전 반드시 수정이 필요합니다.

---

## 1. 아키텍처 및 구조

---

### [ARCH-01] 치명적(다중 인스턴스 전환 시) - DualResponseRegistry 분산 환경 불가

#### 문제

`DualResponseRegistry`는 `ConcurrentHashMap<String, DualResponseTracker>` 로 JVM 힙 메모리 내에서만 동작합니다.

현재 설계 문서에는 UI-backend **단일 인스턴스 운영 전제**가 명시되어 있으므로,
단일 인스턴스로만 운영한다면 즉시 장애로 드러나지 않을 수 있습니다.
다만 다중 인스턴스(HPA/롤링/이중화)로 전환하는 순간 치명적 장애로 전환됩니다.

```
tc-ui-backend-app (인스턴스 A)
  └─ DualResponseRegistry
       trackers: { "traceId-X": tracker }

tc-ui-backend-app (인스턴스 B)
  └─ DualResponseRegistry
       trackers: {}  ← 비어 있음
```

**실패 시나리오:**

```
1. 클라이언트 → 인스턴스 A: POST /api/eqp
2. 인스턴스 A: DualResponseRegistry.register("traceId-X")
3. 인스턴스 A: Kafka 발행 (tc.ui.events.gateway, tc.ui.events.business)

4. Gateway/Business 처리 완료
5. tc.ui.commands 응답 발행 → Kafka 파티셔닝

6. 인스턴스 B: @KafkaListener 수신 ("traceId-X")
7. 인스턴스 B: DualResponseRegistry.record("traceId-X", ...)
   → trackers에 해당 key 없음 → WARN 로그 + 무시

8. 인스턴스 A: orTimeout(5000ms) 초과 → 504 GATEWAY_TIMEOUT
```

단 2대 이상 운영하면 EQP_CREATE/UPDATE/DELETE 요청의 상당 비율이 504로 실패합니다.
K8s HPA, 롤링 배포, 장애 복구를 위한 다중 인스턴스 환경에서 서비스가 불가능합니다.

#### 왜 위험한가

- 개발 환경(단일 인스턴스)에서는 정상 동작하므로 배포 전까지 발견하기 매우 어렵습니다.
- 장애 시 원인이 불명확해 트러블슈팅에 오랜 시간이 소요됩니다.
- 스케일아웃이 근본적으로 불가능한 구조입니다.

#### 개선 방법

**방법 1: Redis 기반 DualResponseRegistry (권장)**

```java
// DualResponseRedisRegistry - Redis Hash로 양쪽 응답 수집
// Key: tc:ui:backend:dual:{traceId}
// Field: gateway → gatewayResult JSON
// Field: business → businessResult JSON
// TTL: timeoutMs + 버퍼 (예: timeoutMs + 5000ms)

// 흐름:
// 1. register(traceId): Redis HSET 생성 + TTL 설정
// 2. record(traceId, source, result): Redis HSET 업데이트
// 3. 양쪽 모두 설정되면 Pub/Sub으로 완료 알림
// 4. DeferredResult 완료

@Component
public class DualResponseRedisRegistry {
    private static final String KEY_PREFIX = "tc:ui:backend:dual:";

    public CompletableFuture<UiDualTaskFinalResult> register(String traceId, long timeoutMs) {
        String key = KEY_PREFIX + traceId;
        // Redis Hash 초기화 + TTL
        redisTemplate.opsForHash().put(key, "status", "PENDING");
        redisTemplate.expire(key, Duration.ofMillis(timeoutMs + 5000));

        CompletableFuture<UiDualTaskFinalResult> future = new CompletableFuture<>();
        // Redis Pub/Sub 구독 등록 (채널: tc:ui:backend:dual:complete:{traceId})
        subscribeDualComplete(traceId, future);

        return future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                     .whenComplete((r, ex) -> cleanup(traceId));
    }

    public void record(String traceId, String source, UiTaskResult result) {
        String key = KEY_PREFIX + traceId;
        redisTemplate.opsForHash().put(key, source, serialize(result));

        // 양쪽 모두 수신됐는지 확인
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.containsKey("gateway") && entries.containsKey("business")) {
            UiDualTaskFinalResult finalResult = buildFinalResult(entries);
            // 완료 알림 Pub/Sub 발행
            redisPubSub.publish("tc:ui:backend:dual:complete:" + traceId, serialize(finalResult));
        }
    }
}
```

**방법 2: Sticky Session (단기 임시방편)**

Nginx/L4 로드밸런서에서 같은 사용자의 요청이 같은 인스턴스로 라우팅되도록 설정합니다.
근본 해결책이 아니므로 방법 1과 병행해야 합니다.

---

### [ARCH-02] 중요 - tc-ui-core Port가 Kafka 계약 타입에 직접 의존

#### 문제

`tc-ui-core` (기술 중립 계층) 의 Port 인터페이스가 `tc-messaging-kafka-contract` 모듈의 타입을 직접 사용합니다.

```java
// UiCommandIngressPort.java (tc-ui-core)
public interface UiCommandIngressPort {
    void handle(KafkaUiTaskReplyMessage reply);  // Kafka 특화 타입 직접 참조
}

// AsyncResultStorePort.java (tc-ui-core)
public interface AsyncResultStorePort {
    void save(String traceId, KafkaUiTaskReplyMessage reply);  // 동일 문제
    Optional<KafkaUiTaskReplyMessage> get(String traceId);
}
```

헥사고날 아키텍처에서 Port는 기술 중립이어야 합니다.
Core가 Kafka 타입을 알면 메시징 계층을 RabbitMQ로 교체할 때 Core도 수정해야 합니다.

#### 왜 위험한가

- 헥사고날 아키텍처의 핵심 가치인 기술 독립성이 무너집니다.
- Kafka 라이브러리 버전 업그레이드 또는 교체 시 Core 모듈도 변경 필요합니다.
- Core 단위 테스트 시 Kafka 의존성을 포함해야 합니다.

#### 개선 방법

Core 전용 중간 DTO를 정의하고 Adapter에서 변환합니다.

```java
// tc-ui-core: 기술 중립 타입 정의
// UiCommandReply.java
public record UiCommandReply(
    String traceId,
    String source,           // "TC-COMM-GATEWAY" | "TC-BUSINESS-CORE"
    String eventType,        // "EQP_CREATE_REP" 등
    UiTaskStatus status,     // PASS | FAIL
    String errorCode,
    String errorMsg
) {}

// UiCommandIngressPort.java (수정)
public interface UiCommandIngressPort {
    void handle(UiCommandReply reply);  // Core 전용 타입 사용
}

// AsyncResultStorePort.java (수정)
public interface AsyncResultStorePort {
    void save(String traceId, UiCommandReply reply);
    Optional<UiCommandReply> get(String traceId);
}

// tc-ui-kafka-adapter: 변환 책임
// UiCommandKafkaSubscriber.java
private UiCommandReply toCommandReply(KafkaUiTaskReplyMessage kafkaMsg) {
    return new UiCommandReply(
        kafkaMsg.metadata().traceId(),
        kafkaMsg.metadata().source(),
        kafkaMsg.metadata().eventType(),
        UiTaskStatus.valueOf(kafkaMsg.data().STATUS()),
        kafkaMsg.data().ERRORCODE(),
        kafkaMsg.data().ERRORMSG()
    );
}
```

---

### [ARCH-03] 구조적 - @ComponentScan 과다 스캔

#### 문제

```java
// TcUiBackendAutoConfiguration.java
@ComponentScan("com.nori.tc.ui")  // ui 하위 모든 패키지 전체 스캔
```

Web Adapter, Kafka Adapter, Redis Adapter, Core의 모든 Bean이 이 단일 ComponentScan으로 일괄 등록됩니다.

#### 왜 위험한가

- 어댑터를 선택적으로 활성화하기 어렵습니다 (예: Kafka 없이 테스트).
- 예상치 못한 Bean이 등록될 수 있습니다.
- 새 모듈 추가 시 자동 등록되어 의도치 않은 의존성 충돌이 발생할 수 있습니다.

#### 개선 방법

각 어댑터 모듈에 `@AutoConfiguration` 을 선언하고 `TcUiBackendAutoConfiguration` 에서 명시적으로 `@Import` 합니다.

```java
// tc-ui-web-adapter → UiWebAdapterAutoConfiguration.java
@AutoConfiguration
@ComponentScan("com.nori.tc.ui.adapters.web")
public class UiWebAdapterAutoConfiguration {}

// tc-ui-kafka-adapter → UiKafkaAdapterAutoConfiguration.java
@AutoConfiguration
@ComponentScan("com.nori.tc.ui.adapters.kafka")
public class UiKafkaAdapterAutoConfiguration {}

// tc-ui-redis-adapter → UiRedisAdapterAutoConfiguration.java
@AutoConfiguration
@ComponentScan("com.nori.tc.ui.adapters.redis")
public class UiRedisAdapterAutoConfiguration {}

// TcUiBackendAutoConfiguration.java (수정)
@AutoConfiguration
@Import({
    UiWebAdapterAutoConfiguration.class,
    UiKafkaAdapterAutoConfiguration.class,
    UiRedisAdapterAutoConfiguration.class
})
public class TcUiBackendAutoConfiguration {}
```

---

### [ARCH-04] 치명적 - Dual 발행 fire-and-forget: 브로커 실패를 호출부가 인지 불가

#### 문제

`UiGatewayEventKafkaPublisher.publish()` 의 실제 구현:

```java
// UiGatewayEventKafkaPublisher.java:139
kafkaTemplate.send(record).whenComplete((sendResult, ex) -> {
    if (ex != null) {
        // 브로커 전송 실패 — 비동기 콜백이므로 예외를 throw할 수 없음
        log.error("tc.ui.events.gateway 발행 실패(비동기). ...");
        return;  // ← 실패가 호출부로 전달되지 않음
    }
});
// publish() 메서드는 kafkaTemplate.send() 등록 직후 즉시 return (void)
```

`EqpController.submitDualRequest()` 에서는 이 publish()를 호출합니다:

```java
// EqpController.java:292-301
try {
    gatewayEventPublishPort.publish(message);   // 즉시 return (브로커 응답 대기 없음)
    businessEventPublishPort.publish(message);  // 즉시 return
} catch (Exception e) {
    future.cancel(true);  // Java 계층 예외만 잡음
}
```

`publish()` 는 `kafkaTemplate.send().whenComplete()` 를 등록한 후 **즉시 반환**합니다.
브로커가 메시지를 수신하기 전에 메서드가 종료되므로,
브로커 다운·네트워크 단절 등의 실패는 EqpController의 `catch` 블록에서 **절대 잡히지 않습니다**.

#### 실패 시나리오

```
1. gatewayPort.publish(message)  → void 반환 (브로커 미확인)
2. businessPort.publish(message) → void 반환 (브로커 미확인)
3. try-catch 블록 정상 종료 → 발행 "성공"으로 처리

4. (비동기) 브로커 전송 실패 → whenComplete에서 ERROR 로그만 출력
5. DualResponseRegistry: gateway/business 응답 미도착
6. orTimeout(5000ms) → 504 GATEWAY_TIMEOUT

→ 브로커에 메시지가 전혀 전달되지 않았지만 UI에서 "발행은 됐는데 응답이 없음"처럼 보임
→ [OOP-01]의 "보상 트랜잭션 부재"보다 더 근본적인 문제
```

#### 왜 위험한가

- **false success**: 브로커 장애 중에도 EqpController는 예외 없이 DeferredResult를 기다립니다.
  타임아웃 전까지 어떤 오류도 감지되지 않으며, 원인 파악이 지연됩니다.
- **운영 중 발행 실패가 로그로만 남고 알람 없음**: `whenComplete` 콜백의 ERROR 로그는 있지만
  caller(Controller)는 실패를 모릅니다.
- **[OOP-01]의 보상 트랜잭션 설계가 무의미**: Gateway에만 발행되고 Business는 실패해도
  두 publish() 호출이 모두 "정상 완료"처럼 보이기 때문입니다.

#### 개선 방법

**방법 1: publish() 를 동기화하여 브로커 확인 대기 (권장)**

```java
// UiGatewayEventKafkaPublisher.publish() 수정
public void publish(KafkaUiTaskMessage message) {
    // ...
    try {
        // .get()으로 브로커 승인 대기 (동기)
        SendResult<String, Object> result = kafkaTemplate.send(record).get(3, TimeUnit.SECONDS);
        log.debug("발행 완료. partition={}, offset={}", ...);
    } catch (TimeoutException e) {
        throw new UiKafkaPublishException("브로커 응답 타임아웃", e);
    } catch (ExecutionException e) {
        throw new UiKafkaPublishException("브로커 전송 실패", e.getCause());
    }
}
```

**방법 2: Transactional Outbox Pattern**

DB outbox 테이블에 발행 요청을 먼저 저장하고, 별도 스케줄러가 outbox를 읽어 Kafka로 발행합니다.
브로커 장애 시에도 outbox가 남아 재발행이 보장됩니다.

**주의**: 방법 1 채택 시 publish()가 blocking I/O가 되어 서블릿 스레드를 점유합니다.
DeferredResult와 함께 사용하므로 비동기 처리가 없어지는 것은 아니지만,
Kafka 응답을 기다리는 시간(수 ms ~ 수십 ms)만큼 스레드가 대기합니다.
타임아웃은 `tc.ui.backend.kafka.dual-request-timeout-ms` 와 별개로 짧게(1~3초) 설정합니다.

---

## 2. 객체지향 설계

---

### [OOP-01] 중요 - EQP 발행 실패 시 보상 트랜잭션 부재

#### 문제

`EqpController.create()` 에서 Gateway 발행 성공 후 Business 발행이 실패하면:

```
시나리오:
1. gatewayPort.publish(message)  → 성공
2. businessPort.publish(message) → 네트워크 오류 발생
3. catch(Exception e) → future.cancel(true)
4. DeferredResult: 500 PUBLISH_FAILED 응답

결과:
- UI: "등록 실패"로 인식
- Gateway: EQP_CREATE 수신 후 설비 생성 처리 시작
- Business: EQP_CREATE 미수신 → 설비 생성 안 됨

→ Gateway와 Business Core 간 데이터 불일치 발생
```

#### 왜 위험한가

- 운영 중 Gateway에는 설비가 있지만 Business Core에는 없는 상태가 지속될 수 있습니다.
- 이후 EQP_UPDATE/EQP_START 요청 시 Business가 설비를 모르므로 처리 실패합니다.
- 불일치 상태가 쌓이면 수동 보정이 필요하고 운영 부담이 급증합니다.

#### 개선 방법

**방법 1: 보상 이벤트 발행**

```java
// EqpController.create() - 발행 실패 시 보상 이벤트
try {
    gatewayPort.publish(createMsg);
    businessPort.publish(createMsg);
} catch (Exception e) {
    if (gatewayPublished) {
        // Gateway에 이미 발행됐다면 취소 이벤트 발행
        KafkaUiTaskMessage cancelMsg = buildCancelMessage(traceId, eqpId, KafkaUiTaskEventType.EQP_CREATE_ROLLBACK);
        gatewayPort.publish(cancelMsg);
    }
    future.cancel(true);
}
```

**방법 2: Transactional Outbox Pattern**

발행 요청을 DB의 outbox 테이블에 먼저 저장하고,
별도 스케줄러가 outbox를 읽어 Kafka로 발행합니다.
네트워크 실패 시 outbox가 남아 있어 재발행이 보장됩니다.

```
tc_ui_outbox 테이블:
  outbox_id, trace_id, topic, payload, status(PENDING/SENT/FAILED), created_at
```

---

### [OOP-02] 보안/설계 - UiApiPermissionCache 기본 개방 정책

#### 문제

```java
// UiApiPermissionCache.isAuthorized()
if (matchedCodes.isEmpty()) {
    return true;  // 매핑된 권한이 없으면 모두 허용 (open by default)
}
```

새 API 엔드포인트를 추가할 때 `tc_ui_permission` 테이블에 등록하지 않으면
**자동으로 모든 인증된 사용자에게 노출**됩니다.

#### 왜 위험한가

- 개발자가 API를 추가하고 권한 등록을 잊으면 즉시 보안 취약점이 됩니다.
- 내부 관리 API, 디버그 API 등이 의도치 않게 공개될 수 있습니다.
- 감사(Audit) 시 "이 API는 왜 권한 설정이 없는가?" 라는 질문에 답하기 어렵습니다.

#### 개선 방법

**방법 1: Closed by Default + 명시적 OPEN 마킹**

```java
// UiApiPermissionCache.isAuthorized() 수정
if (matchedCodes.isEmpty()) {
    // 매핑 없음 = 정의되지 않은 API = 기본 차단
    log.warn("권한 미정의 API 접근 차단: {} {}", method, uri);
    return false;
}

// 공개 API는 tc_ui_permission에 permission_code = 'PUBLIC'으로 등록
// matchedCodes.contains("PUBLIC") → 허용
```

**방법 2: @OpenApi 어노테이션 도입**

```java
// @OpenApi: 권한 없이 인증된 사용자 모두 허용
@GetMapping("/api/eqp/{eqpId}/status")
@OpenApi
public ApiResponse<EqpStatusResponse> getStatus(...) { ... }
```

---

### [OOP-03] 코드 컨벤션 - KafkaUiTaskReplyData 필드명 위반

#### 문제

```java
// KafkaUiTaskReplyData.java
public record KafkaUiTaskReplyData(
    String eqpId,
    String interfaceType,
    String STATUS,      // Java 컨벤션 위반 (대문자 필드명)
    String ERRORMSG,    // Java 컨벤션 위반
    String ERRORCODE    // Java 컨벤션 위반
) {}
```

Java record의 필드명은 `camelCase` 가 원칙입니다.
대문자 필드명은 Jackson 역직렬화 시 외부 JSON의 키가 반드시 `STATUS`, `ERRORMSG`, `ERRORCODE` 대문자여야 합니다.

#### 왜 위험한가

- Gateway/Business Core가 `status`, `errorMsg`, `errorCode` 소문자로 응답하면 파싱이 실패합니다.
- Jackson 기본 설정에서 대소문자는 구분됩니다 (`MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES` 미적용 시).
- 외부 시스템과의 계약 변경 없이 내부 리팩토링이 어렵습니다.

#### 개선 방법

```java
// KafkaUiTaskReplyData.java 수정
public record KafkaUiTaskReplyData(
    String eqpId,
    String interfaceType,
    @JsonProperty("STATUS")   String status,      // JSON 키는 유지, 필드명만 camelCase
    @JsonProperty("ERRORMSG") String errorMsg,
    @JsonProperty("ERRORCODE") String errorCode
) {}
```

---

## 3. 의존성 관리

---

### [DEP-01] 중요 - Core 계층이 Messaging 계약에 의존

`tc-ui-core/build.gradle.kts` 에 `tc-messaging-kafka-contract` 의존성이 포함된 경우
의존성 방향이 역전됩니다.

```
잘못된 구조:
tc-ui-core → tc-messaging-kafka-contract (Kafka 라이브러리 의존)

올바른 구조:
tc-ui-kafka-adapter → tc-messaging-kafka-contract
tc-ui-kafka-adapter → tc-ui-core (Port 구현)
tc-ui-core (Port 인터페이스만, 기술 의존 없음)
```

#### 개선 방법

`[ARCH-02]` 의 Core 전용 DTO 도입으로 동시에 해결됩니다.
`tc-ui-core` 의 `build.gradle.kts` 에서 `tc-messaging-kafka-contract` 의존성을 제거합니다.

---

### [DEP-02] 경미 - GatewayEquipmentProfileSnapshot이 Kafka 계약 레이어에 위치

#### 문제

`GatewayEquipmentProfileSnapshot` 은 설비의 도메인 스냅샷 객체입니다.
그런데 `tc-messaging-kafka-contract` 에 위치해 있어
Kafka를 사용하지 않는 모듈도 이 도메인 객체에 접근하려면 Kafka 계약 모듈을 의존해야 합니다.

#### 개선 방법

`GatewayEquipmentProfileSnapshot` 을 `tc-db-domain` 또는 별도 `tc-comm-domain` 에 위치시키고,
`tc-messaging-kafka-contract` 에서는 이를 참조합니다.

---

## 4. 코드 품질

---

### [QUALITY-01] 중요 - DeferredResult 비동기 재디스패치 시 토큰 이중 검증

#### 문제

```java
// UiTokenAuthenticationFilter.java
@Override
protected boolean shouldNotFilterAsyncDispatch() {
    return false;  // 비동기 디스패치에서도 필터 실행
}
```

DeferredResult 처리 흐름:

```
1. 클라이언트 요청 도착
2. UiTokenAuthenticationFilter 실행 (1회차) → Redis 캐시 조회 → SecurityContext 설정
3. EqpController.create() 실행 → DeferredResult 반환 (비동기 대기)
4. Kafka 응답 수신 → DeferredResult.setResult() 호출
5. Servlet 컨테이너: 비동기 디스패치 발생
6. UiTokenAuthenticationFilter 실행 (2회차) → Redis 캐시 조회 (불필요한 재검증)
7. HTTP 응답 완료
```

하나의 요청에서 Redis 조회가 2회 발생할 수 있습니다. 고트래픽 환경에서는 불필요한 부하가 될 수 있습니다.

#### 개선 방법

```java
// UiTokenAuthenticationFilter.java 수정 (권장)
@Override
protected boolean shouldNotFilterAsyncDispatch() {
    return false;  // 유지: DeferredResult 재디스패치 401 회귀 방지
}

// 비동기 재디스패치에서 이미 인증 정보가 복원된 경우만 빠르게 통과
@Override
protected void doFilterInternal(...) {
    if (isAsyncDispatch(request)
            && SecurityContextHolder.getContext().getAuthentication() != null) {
        filterChain.doFilter(request, response);
        return;
    }
    // 토큰 검증 로직
}
```

---

### [QUALITY-02] 경미 - 401 응답 포맷 불일치

#### 문제

```java
// UiTokenAuthenticationFilter.java
sendUnauthorized(response, "유효하지 않은 인증 토큰입니다.");
// → ApiResponse.error("UNAUTHORIZED", ...) JSON 반환

// UiSecurityConfig.java - AuthenticationEntryPoint
response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
// → 기본 에러 포맷(컨테이너/프레임워크) 반환
```

클라이언트가 인증 에러와 비즈니스 에러를 다르게 파싱해야 합니다.

#### 개선 방법

```java
// AuthenticationEntryPoint를 커스텀해서 401 포맷을 ApiResponse로 통일
public class UiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        String body = objectMapper.writeValueAsString(
                ApiResponse.error("UNAUTHORIZED", "인증이 필요합니다."));
        response.getWriter().write(body);
    }
}
```

---

### [QUALITY-03] 경미 - EqpController 발행 실패 흐름 복잡성

#### 문제

```java
// EqpController.create()
CompletableFuture<UiDualTaskFinalResult> future = registry.register(traceId, timeoutMs);
try {
    gatewayPort.publish(createMsg);
    businessPort.publish(createMsg);
} catch (Exception e) {
    future.cancel(true);  // → whenComplete에서 CancellationException 처리
}
future.whenComplete((result, ex) -> {
    if (ex instanceof CancellationException) {
        deferredResult.setErrorResult(...);  // 간접적 에러 처리
    }
});
```

`future.cancel(true)` → `whenComplete(CancellationException)` 의 간접 흐름은 직관적이지 않습니다.

#### 개선 방법

```java
// 발행 실패 시 DeferredResult에 직접 에러 설정
try {
    gatewayPort.publish(createMsg);
    businessPort.publish(createMsg);
} catch (Exception e) {
    registry.cancel(traceId);  // trackers 정리만 담당
    deferredResult.setErrorResult(
        ResponseEntity.status(500).body(ApiResponse.error("PUBLISH_FAILED", "발행 실패"))
    );
    return deferredResult;
}
```

---

### [QUALITY-04] 중간 - AuthController 강제 캐스팅 → ClassCastException 위험

#### 문제

```java
// AuthController.java:118
final String token = (String) authentication.getCredentials();  // 강제 캐스팅

// AuthController.java:147
final UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();  // 강제 캐스팅
```

`UiTokenAuthenticationFilter` 가 항상 `credentials = String 토큰`, `principal = UserPrincipal` 을 설정한다는
전제 하에 동작합니다. 하지만 다음 경우 전제가 깨집니다:

- DeferredResult 비동기 재디스패치에서 SecurityContext가 다른 Authentication 타입으로 채워진 경우
- 향후 다른 인증 방식(API Key, OAuth 등) 추가 시
- 테스트 환경에서 MockAuthentication 사용 시
- Spring Security 업그레이드로 Anonymous Authentication이 필터를 통과하는 경우

`ClassCastException` 발생 시 Spring 기본 예외 처리기가 500을 반환합니다.

#### 왜 위험한가

- 예외가 `ExceptionHandler` 를 거치지 않고 Spring 기본 500 응답으로 나갑니다.
- 에러 메시지에 내부 클래스명이 포함될 수 있어 정보 노출 위험이 있습니다.
- 장애 원인이 Authentication 타입 불일치인지 즉시 파악하기 어렵습니다.

#### 개선 방법

```java
// AuthController.java - 안전한 타입 확인 후 처리
@PostMapping("/logout")
public ResponseEntity<ApiResponse<Void>> logout() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (!(authentication.getCredentials() instanceof String token)) {
        log.warn("로그아웃 요청: credentials 타입 불일치 - {}", authentication.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "인증 정보가 유효하지 않습니다."));
    }

    if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
        log.warn("로그아웃 요청: principal 타입 불일치 - {}", authentication.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "인증 정보가 유효하지 않습니다."));
    }

    logoutUseCase.execute(token);
    SecurityContextHolder.clearContext();
    return ResponseEntity.ok(ApiResponse.success(null));
}
```

---

## 5. 예외 처리

---

### [EX-01] 치명적 - Kafka 파싱 실패 메시지 영구 소멸

#### 문제

```java
// UiCommandKafkaSubscriber.java
try {
    KafkaUiTaskReplyMessage reply = objectMapper.readValue(payload, KafkaUiTaskReplyMessage.class);
    ingressPort.handle(reply);
    acknowledgment.acknowledge();
} catch (JsonProcessingException e) {
    log.warn("파싱 실패: {}", e.getMessage());
    acknowledgment.acknowledge();  // ← 메시지 소비 완료 처리
    // DLQ 저장 없음 → 메시지 영구 소멸, 추적 불가
}
```

Gateway/Business가 보낸 응답 메시지가 파싱 실패 시 아무 곳에도 저장되지 않고 사라집니다.
이 경우 해당 traceId의 DualResponse는 타임아웃까지 기다리다가 504로 실패합니다.

#### 왜 위험한가

- 계약 변경(필드 추가/삭제) 없이 배포 순서가 어긋날 때 모든 응답이 소멸됩니다.
- 장애 원인 파악이 불가능합니다 (메시지가 사라지므로 재처리도 불가).
- WARN 로그만으로는 운영팀이 실제 문제 규모를 파악하기 어렵습니다.

#### 개선 방법

```java
// UiCommandKafkaSubscriber.java 수정
} catch (JsonProcessingException e) {
    log.error("Kafka 메시지 파싱 실패 - DLQ 전송: topic={}, payload={}", topic, payload, e);
    // Dead Letter Topic으로 전송
    kafkaDltProducer.send("tc.ui.commands.DLT", payload);
    acknowledgment.acknowledge();
}
```

또는 Spring Kafka의 `SeekToCurrentErrorHandler` + `DeadLetterPublishingRecoverer` 를 사용합니다.

```java
// UiKafkaConfiguration.java 추가
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(template,
            (record, ex) -> new TopicPartition("tc.ui.commands.DLT", record.partition()));
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
}
```

---

### [EX-02] 중요 - LogoutUseCase DB/Redis 불일치 가능성

#### 문제

```java
// LogoutUseCase.java
sessionPort.revoke(token);    // DB: revoked = true
tokenCachePort.evict(token);  // Redis: 캐시 제거
```

`revoke` 성공 후 `evict` 가 실패하면:
- DB: 세션 취소됨 (로그아웃 완료)
- Redis: 캐시에 토큰 존재 (TTL만큼 인증 통과)

Redis 캐시 TTL(기본 300초) 동안 로그아웃된 사용자의 토큰으로 API 접근이 가능합니다.

#### 개선 방법

```java
// LogoutUseCase.java 수정
public void execute(String token) {
    sessionPort.revoke(token);

    try {
        tokenCachePort.evict(token);
    } catch (Exception e) {
        // Redis 실패는 경고 수준으로 처리 (DB revoke는 이미 완료)
        // 다음 요청 시 ValidateTokenUseCase에서 DB 확인 후 캐시 재갱신됨
        log.error("Redis 캐시 제거 실패 - TTL 만료 전까지 토큰 유효: token=***", e);
        // 필요시 비동기 재시도 스케줄링
    }
}
```

추가로, `ValidateTokenUseCase` 에서 DB 조회 시 `revoked=true` 세션을 감지하면
자동으로 캐시를 무효화하는 보호 로직을 추가합니다.

---

### [EX-03] 중요 - ValidateTokenUseCase lastSeenAt 실패 시 인증 실패 전파 위험

#### 문제

```java
// ValidateTokenUseCase.java (추정 코드)
UserPrincipal principal = buildPrincipal(session, user, permissions);
tokenCachePort.put(token, principal);
sessionPort.updateLastSeenAt(token, OffsetDateTime.now());  // 실패 시 예외 전파?
return principal;
```

`updateLastSeenAt` 이 DB 타임아웃 등으로 실패하면 `RuntimeException` 이 전파되어
인증 자체가 실패할 수 있습니다. `lastSeenAt` 은 통계/감사 목적의 비필수 업데이트입니다.

#### 개선 방법

```java
// ValidateTokenUseCase.java 수정
try {
    sessionPort.updateLastSeenAt(token, OffsetDateTime.now());
} catch (Exception e) {
    // 비필수 업데이트 실패는 인증 결과에 영향 없음
    log.warn("lastSeenAt 업데이트 실패 (인증은 정상 처리됨): {}", e.getMessage());
}
return principal;
```

---

### [EX-04] 중요 - UiApiPermissionCache 초기화 실패 시 보안 개방

#### 문제

```java
// UiApiPermissionCache.java
@PostConstruct
void init() {
    try {
        permissions = permissionPort.findAllActiveApiPermissions();
    } catch (Exception e) {
        log.error("권한 목록 로드 실패 - 빈 캐시로 시작");
        permissions = Collections.emptyList();  // 전체 API 개방 상태
    }
}
```

기동 시 DB 연결 실패, 네트워크 불안 등으로 권한 로드가 실패하면
**모든 API가 인증된 사용자에게 전면 개방**됩니다.

#### 개선 방법

```java
// 방법 1: 초기화 실패 시 기동 중단
@PostConstruct
void init() {
    try {
        permissions = permissionPort.findAllActiveApiPermissions();
        log.info("권한 목록 로드 완료: {}건", permissions.size());
    } catch (Exception e) {
        throw new IllegalStateException("권한 목록 로드 실패 - 보안상 기동 중단", e);
    }
}

// 방법 2: 초기화 실패 시 전체 차단 failsafe
private boolean initializationFailed = false;

@PostConstruct
void init() {
    try {
        permissions = permissionPort.findAllActiveApiPermissions();
    } catch (Exception e) {
        log.error("권한 목록 로드 실패 - failsafe 모드 (전체 차단)");
        initializationFailed = true;
    }
}

public boolean isAuthorized(...) {
    if (initializationFailed) return false;  // 전체 차단
    // 기존 로직
}
```

---

### [EX-05] 낮음 - EqpSequentialProcessor 예외 삼킴 + 무로그

#### 문제

```java
// EqpSequentialProcessor.java:311-322
try {
    dlqPublisherPort.publish(dlqMessage);
} catch (Exception dlqEx) {
    // DLQ 발행 실패는 반드시 운영 관측 대상입니다.   ← 주석만 있음
    // core 엔진은 여기서 예외를 재던지지 않습니다.   ← log.error() 없음
}

try {
    quarantinePort.quarantine(profile.equipmentId(), reasonCode.name(), safeMessage(ex));
} catch (Exception qEx) {
    // 격리 실패 역시 운영 관측 대상입니다.           ← 주석만 있음, log 없음
}
```

주석에서 "운영 관측 대상"이라고 명시하면서 실제 `log.error()` 호출이 없습니다.
**의도는 있었지만 구현이 빠진 상태**입니다.

#### 왜 위험한가

- DLQ 발행 실패가 무소음으로 사라집니다. Gateway 엔진이 메시지 처리 실패 후 DLQ에도 넣지 못했는데
  아무 알람도 발생하지 않습니다.
- 격리(quarantine) 실패도 동일하게 운영자가 인식할 수 없습니다.
- 이미 catch 블록을 작성한 개발자가 "로깅이 있을 것"으로 착각할 수 있어 향후 방치될 가능성이 높습니다.

#### 개선 방법

```java
try {
    dlqPublisherPort.publish(dlqMessage);
} catch (Exception dlqEx) {
    // 예외를 재던지지 않지만 반드시 로그를 남겨야 운영 관측이 가능함
    log.error("DLQ 발행 실패 - 메시지 영구 유실 위험. eqpId={}, reasonCode={}",
              profile.equipmentId(), reasonCode, dlqEx);
}

try {
    quarantinePort.quarantine(profile.equipmentId(), reasonCode.name(), safeMessage(ex));
} catch (Exception qEx) {
    log.error("설비 격리 실패 - 수동 격리 조치 필요. eqpId={}, reasonCode={}",
              profile.equipmentId(), reasonCode, qEx);
}
```

---

## 6. 시스템 안정성 및 데이터 무결성

---

### [STAB-01] 중요 - EQP_START/END 비동기 결과 소멸 가능성

#### 문제

EQP_START 응답 처리 흐름:

```
Gateway → tc.ui.commands (EQP_START_REP)
→ UiCommandKafkaSubscriber 수신
→ AsyncResultStoreService.save(traceId, reply) → Business Redis(6380)
→ Front: GET /api/async/{traceId} polling → 결과 반환
```

**소멸 시나리오:**

1. Business Redis(6380) 연결 실패 → save() 예외 → 결과 저장 불가
   → Front polling: 영원히 404 (처리됐는지 알 수 없음)

2. Front가 TTL(기본 600초) 후 polling → 결과 만료
   → 처리 성공인지 실패인지 알 수 없음

3. "처리 중"과 "존재하지 않는 traceId"를 구분하는 API 없음
   → 둘 다 404 반환

#### 개선 방법

**상태 구분 API 추가:**

```java
// AsyncResultController
// 200: 처리 완료 (결과 있음)
// 202: 처리 중 (아직 결과 없음)
// 404: traceId 불명 (잘못된 요청)
// 408: 타임아웃 (처리 실패)

// AsyncStatusEntry를 Redis에 저장
enum AsyncStatus { PENDING, COMPLETED, TIMEOUT }

// save() 전에 PENDING 상태로 등록
// 완료 시 COMPLETED로 업데이트
// 스케줄러가 일정 시간 경과 후 TIMEOUT으로 변경
```

---

### [STAB-02] 중요 - DualResponseRegistry 타임아웃 후 정리 불확실성

#### 문제

```java
// DualResponseRegistry.register()
CompletableFuture<UiDualTaskFinalResult> future = new CompletableFuture<UiDualTaskFinalResult>()
    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
    .whenComplete((result, ex) -> {
        trackers.remove(traceId);  // A: orTimeout 경로
    });

// EqpController 발행 실패 시
future.cancel(true);  // B: cancel 경로
// → whenComplete가 CancellationException으로 실행됨 → trackers.remove() 실행됨
```

`cancel(true)` 시에도 `whenComplete` 가 실행되어 `trackers.remove()` 가 호출됩니다.
그러나 Future 체인이 복잡한 경우 A 경로와 B 경로가 동시에 실행되어
`trackers.remove()` 가 2번 호출될 수 있습니다. 이는 문제가 되지 않지만,
각 경로에서 DeferredResult 설정이 중복 호출되면 `IllegalStateException` 이 발생합니다.

#### 개선 방법

```java
// DeferredResult는 setResult/setErrorResult 한 번만 호출 보장
private final AtomicBoolean completed = new AtomicBoolean(false);

private void safeSetResult(DeferredResult<?> deferredResult, Object result) {
    if (completed.compareAndSet(false, true)) {
        deferredResult.setResult(result);
    }
}
```

---

## 7. 성능 및 확장성

---

### [PERF-01] 중요 - lastSeenAt 동기 DB 업데이트 (캐시 미스 경로)

#### 문제

현재 구현은 `ValidateTokenUseCase` 에서 **캐시 미스 경로에서만** `lastSeenAt` 을 동기 업데이트합니다.
다만 토큰 종류가 많거나 TTL이 짧아 캐시 미스 비율이 높아지면,
인증 경로의 DB write 부담이 커질 수 있습니다.

#### 개선 방법

```java
// 방법 1: @Async 비동기 처리
@Async
public void updateLastSeenAtAsync(String token) {
    sessionPort.updateLastSeenAt(token, OffsetDateTime.now());
}

// 방법 2: 일정 간격으로만 업데이트 (쓰로틀링)
// Redis에 "마지막 업데이트 시각"을 저장하고 5분 이상 경과 시에만 DB 업데이트
private boolean shouldUpdate(String token) {
    String key = "tc:ui:backend:lastseen:" + token;
    if (redisTemplate.opsForValue().get(key) != null) {
        return false;  // 최근 업데이트됨
    }
    redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(5));
    return true;
}
```

---

### [PERF-02] 중요 - GatewayEquipmentProfileSnapshot Kafka 메시지 크기

#### 문제

```java
// KafkaUiTaskData.java
record KafkaUiTaskData(
    String eqpId,
    String interfaceType,
    String uiMessage,
    GatewayEquipmentProfileSnapshot equipmentProfile  // 대형 중첩 객체
)
```

`GatewayEquipmentProfileSnapshot` 에는 HSMS 설정, 소켓 설정, 포트 상태 목록, 파라미터 목록 등이 포함됩니다.
설비에 파라미터가 수십 개, 포트가 다수라면 단일 메시지가 수백 KB 가 될 수 있습니다.
Kafka 기본 메시지 크기 제한(1MB) 초과 시 `RecordTooLargeException` 이 발생합니다.

#### 개선 방법

1. Kafka 설정에서 `max.request.size` 와 `message.max.bytes` 를 적절히 조정합니다.
2. EQP_START/END 처럼 profile이 불필요한 이벤트는 `equipmentProfile = null` 로 전송합니다.
3. 프로파일 전체 대신 변경된 필드만 포함하는 Delta 방식을 고려합니다.

---

### [PERF-03] 경미 - UiApiPermissionCache 동적 갱신 불가

#### 문제

```java
@PostConstruct
void init() {
    permissions = permissionPort.findAllActiveApiPermissions();
    // 이후 갱신 없음
}
```

DB에서 권한을 추가/삭제해도 서버 재시작 전까지 반영되지 않습니다.

#### 개선 방법

```java
// 주기적 갱신 (5분마다)
@Scheduled(fixedDelay = 300_000)
public void refresh() {
    try {
        List<TcUiPermission> updated = permissionPort.findAllActiveApiPermissions();
        this.permissions = updated;
        log.info("권한 캐시 갱신 완료: {}건", updated.size());
    } catch (Exception e) {
        log.warn("권한 캐시 갱신 실패 - 기존 캐시 유지: {}", e.getMessage());
    }
}
```

---

## 8. 메모리 누수

---

### [MEM-01] 중요 - DualResponseRegistry trackers 누수 가능 경로 검증 필요

#### 문제

`DualResponseTracker` 가 `trackers` Map에서 반드시 제거되어야 하는 경로:

| 경로 | 제거 방법 | 보장 여부 |
|------|-----------|-----------|
| 정상 완료 (양쪽 응답 수신) | `whenComplete` → `trackers.remove()` | 확인 필요 |
| 타임아웃 (`orTimeout`) | `whenComplete` → `trackers.remove()` | 확인 필요 |
| 발행 실패 (`future.cancel(true)`) | `whenComplete(CancellationException)` → `trackers.remove()` | 확인 필요 |
| 예외적 완료 (`future.completeExceptionally`) | `whenComplete` → `trackers.remove()` | 확인 필요 |

`whenComplete` 는 성공/실패/취소 모든 경우에 실행되므로 이론상 정리됩니다.
그러나 `whenComplete` 내부에서 예외가 발생하면 `trackers.remove()` 가 실행되지 않을 수 있습니다.

#### 개선 방법

```java
// finally 블록으로 반드시 제거 보장
future.whenComplete((result, ex) -> {
    try {
        // DeferredResult 설정 로직
        handleCompletion(traceId, deferredResult, result, ex);
    } finally {
        trackers.remove(traceId);  // 반드시 실행
    }
});
```

---

## 9. 보안

---

### [SEC-01] 치명적 - JDK 직렬화 역직렬화 공격 취약점 (RCE 위험)

#### 문제

```java
// UiRedisConfiguration.java
template.setValueSerializer(new JdkSerializationRedisSerializer());

// 저장: UserPrincipal → JDK 직렬화 바이트 → Redis
// 로드: Redis 바이트 → JDK 역직렬화 → UserPrincipal
```

Java JDK 직렬화는 **역직렬화 공격(Deserialization Attack)** 에 취약합니다.
Redis에 악의적으로 조작된 바이트를 저장할 수 있는 공격자가 있다면
역직렬화 과정에서 **임의 코드 실행(Remote Code Execution, RCE)** 이 가능합니다.

**관련 CVE:** CVE-2015-4852, CVE-2016-4461, CVE-2017-3248 (Apache Commons Collections, Spring 기반)

#### 왜 위험한가

- Redis 접근 권한을 획득한 내부 공격자 또는 Redis 직접 접근이 가능한 외부 공격자가 악용 가능합니다.
- RCE는 서버 전체 탈취로 이어집니다.
- `UserPrincipal`, `KafkaUiTaskReplyMessage` 등 모든 Redis 저장 객체가 영향을 받습니다.

#### 개선 방법

```java
// UiRedisConfiguration.java 수정
// JdkSerializationRedisSerializer → GenericJackson2JsonRedisSerializer

@Bean
public RedisTemplate<String, Object> businessRedisTemplate(...) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(businessConnectionFactory);
    template.setKeySerializer(new StringRedisSerializer());

    // JDK 직렬화 제거 → JSON 직렬화로 교체
    // 주의: activateDefaultTyping + LaissezFaireSubTypeValidator 조합은 사용하지 않습니다.
    GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
    template.setValueSerializer(serializer);
    template.setHashValueSerializer(serializer);
    return template;
}
```

`UserPrincipal`, `RedisUiSessionEntry`, `RedisUiAsyncResultEntry` 에서 `implements Serializable` 제거 후 Jackson 직렬화 가능하도록 수정합니다.

---

### [SEC-02] 중요 - Redis Key에 토큰 원문 사용

#### 문제

```
Redis Key: tc:ui:backend:session:{token}
```

Bearer 토큰 원문이 Redis 키로 사용됩니다.
Redis 접근 권한이 있는 사람이 `SCAN tc:ui:backend:session:*` 명령으로 모든 유효 토큰을 조회할 수 있습니다.

#### 왜 위험한가

- Redis는 운영팀/DBA가 직접 접근하는 경우가 많습니다.
- 토큰을 탈취하면 해당 사용자로 위장 가능합니다.
- 토큰 자체가 SecureRandom 기반이지만 키에 평문 노출은 불필요한 리스크입니다.

#### 개선 방법

```java
// 토큰의 SHA-256 해시를 Redis 키로 사용
private String buildCacheKey(String token) {
    try {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
        return "tc:ui:backend:session:" + HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 지원 불가", e);
    }
}
```

---

### [SEC-03] 보안/설계 - UiApiPermissionCache 초기화 실패 시 전체 개방

`[EX-04]` 와 동일합니다. 보안 관점에서도 최우선 수정이 필요합니다.

---

### [SEC-04] 치명적 - 평문 비밀번호가 git tracked 파일에 존재

#### 문제

아래 파일들이 `git ls-files` 기준으로 **실제 git 저장소에 추적되고 있으며**,
평문 비밀번호가 포함되어 있습니다:

```
config/tc-db.properties:40
  spring.datasource.password=REDACTED_DB_PASSWORD

apps/tc-ui-backend-app/config/tc-redis.properties:22,34
  tc.ui.backend.redis.gateway.password=REDACTED_REDIS_PASSWORD
  tc.ui.backend.redis.business.password=REDACTED_REDIS_PASSWORD

apps/tc-business-core-app/config/tc-redis.properties:12
  spring.data.redis.password=REDACTED_REDIS_PASSWORD

apps/tc-comm-gateway-app/config/tc-redis.properties:9
  spring.data.redis.password=REDACTED_REDIS_PASSWORD
```

또한 `config/tc-db.properties:12` 주석에도 평문 비밀번호가 포함되어 있습니다:
```
# - dbname=dev_tc / username=nori / password=REDACTED_DB_PASSWORD
```

#### 왜 위험한가

- git 커밋 이력에 평문 비밀번호가 영구 기록됩니다.
  파일을 삭제해도 이력에는 남아 있어 `git log -p` 로 언제든 조회 가능합니다.
- 저장소 접근 권한이 있는 모든 사람이 DB/Redis 자격증명을 즉시 획득합니다.
- GitHub/GitLab 등 원격 저장소에 push된 이력이 있다면 이미 노출됐을 가능성이 있습니다.
- 향후 팀원 추가, 외부 컨트리뷰터 초대, 저장소 공개 전환 시 치명적 침해로 이어집니다.

#### 개선 방법

**즉시 해야 할 것:**

```bash
# 1. 비밀번호를 즉시 교체 (DB/Redis 모두)
# 2. 해당 파일들을 .gitignore에 추가
echo "config/tc-db.properties"                              >> .gitignore
echo "apps/*/config/tc-redis.properties"                   >> .gitignore
echo "apps/*/config/tc-db.properties"                      >> .gitignore

# 3. git 이력에서 파일 제거 (git filter-branch 또는 BFG Repo-Cleaner 사용)
# BFG 사용 예:
# bfg --delete-files tc-db.properties
# git reflog expire --expire=now --all && git gc --prune=now --aggressive

# 4. 원격 저장소 force push (협의 후 진행)
```

**구조적 개선:**

```
# 환경별 설정 파일 구조
config/
  tc-db.properties.template      ← git 추적 (빈값 또는 placeholder)
  tc-db.properties               ← git 무시 (.gitignore)

# 또는 환경변수 방식
spring.datasource.password=${DB_PASSWORD}
```

**운영 환경:**
- HashiCorp Vault, AWS Secrets Manager, Azure Key Vault 등 시크릿 관리 도구를 사용합니다.
- CI/CD 파이프라인에서 환경변수로 주입합니다.

---

### [SEC-05] 중간 - 플러그인 서명 검증 미구현 (운영 반영 전 필수)

#### 문제

```java
// GatewaySocketPluginRuntimeManager.java:93
SECURITY_TODO_BACKLOG = List.of(
    new SecurityTodoBacklogItem(
        1, "PLUGIN_SIGNATURE_VERIFY",
        "플러그인 서명 검증",
        "운영 반영 전 JAR 무결성/발행자 신뢰를 강제합니다."
    ),
    new SecurityTodoBacklogItem(
        2, "TRUSTED_PUBLISHER_ALLOWLIST",
        "신뢰 발행자 allowlist",
        "허용된 발행자/인증서 체인만 배포 가능하도록 제한합니다."
    )
)
```

플러그인 JAR 파일을 로드할 때 서명 검증과 신뢰 발행자 확인이 구현되어 있지 않습니다.
코드 자체에서 "운영 반영 전 강제"라고 명시했으나 백로그 상태로 방치되어 있습니다.

#### 왜 위험한가

- **공급망 공격(Supply Chain Attack)**: 악의적으로 수정된 JAR가 검증 없이 로드될 수 있습니다.
- **인프로세스 실행**: 플러그인은 JVM 프로세스 내에서 실행되므로 격리가 없습니다.
  악의적 플러그인이 메모리 접근, 네트워크 통신, 파일 시스템 접근이 가능합니다.
- **코드 자체에서 "운영 반영 전 필수"로 명시**했음에도 미구현 상태입니다.

#### 개선 방법

```java
// 플러그인 로드 전 서명 검증
public void loadPlugin(Path jarPath) {
    // 1. 파일 해시 검증 (사전 등록된 SHA-256과 비교)
    String actualHash = computeSha256(jarPath);
    if (!allowedHashes.contains(actualHash)) {
        throw new PluginSecurityException("허용되지 않은 플러그인 JAR: " + jarPath);
    }

    // 2. JAR 서명 검증 (jarsigner / JarFile 서명 검증 API)
    try (JarFile jar = new JarFile(jarPath.toFile(), true)) {
        verifyJarSignature(jar);  // CertificateException 등 발생 시 로드 중단
    }

    // 3. 발행자 allowlist 확인
    Certificate[] certs = getCertificates(jarPath);
    if (!isTrustedPublisher(certs)) {
        throw new PluginSecurityException("신뢰할 수 없는 발행자: " + jarPath);
    }

    // 이후 URLClassLoader로 로드
}
```

---

## 10. 운영 가시성 (추가 평가)

---

### [OPS-01] 중요 - Metrics 부재

Micrometer/Prometheus 연동이 없어 다음 지표를 실시간으로 확인할 수 없습니다:

- DualResponse 성공률 / 실패율 / 타임아웃율
- Kafka Consumer 처리 지연 (tc.ui.commands lag)
- Redis 캐시 히트율
- API 응답 시간 분포 (p50, p95, p99)

#### 개선 방법

```java
// DualResponseRegistry - 메트릭 추가
meterRegistry.counter("dual_response.completed", "status", "success").increment();
meterRegistry.counter("dual_response.completed", "status", "timeout").increment();
meterRegistry.timer("dual_response.duration").record(duration, TimeUnit.MILLISECONDS);

// UiSessionCacheService - 캐시 히트율
meterRegistry.counter("session_cache.hit").increment();
meterRegistry.counter("session_cache.miss").increment();
```

---

### [OPS-02] 중요 - Distributed Tracing 미연계

Kafka 헤더에 `x-trace-id` 를 추가하지만, 이 값이 MDC에 설정되지 않으면
로그에서 요청 단위 추적이 불가능합니다.

#### 개선 방법

```java
// UiCommandKafkaSubscriber.java - MDC 설정
String traceId = reply.metadata().traceId();
MDC.put("traceId", traceId);
try {
    ingressPort.handle(reply);
} finally {
    MDC.remove("traceId");
}

// logback.xml 또는 log4j2.xml - MDC 출력 패턴
// %X{traceId} 를 로그 패턴에 포함
```

---

## 11. 테스트 커버리지 (추가 평가)

---

### [TEST-01] 중요 - DualResponseRegistry 동시성 테스트 부재

`DualResponseRegistry` 는 다중 스레드에서 동시에 접근합니다:
- HTTP 스레드: `register()`, `record()`
- Kafka 컨슈머 스레드: `record()`
- 타임아웃 스레드: `orTimeout()` 콜백

동시성 버그는 일반 단위 테스트로 발견하기 어렵습니다.

```java
// 동시성 테스트 예시
@Test
void 동시_응답_수신_테스트() throws InterruptedException {
    String traceId = UUID.randomUUID().toString();
    CompletableFuture<UiDualTaskFinalResult> future = registry.register(traceId, 5000);

    // Gateway 응답과 Business 응답을 동시에 전송
    ExecutorService executor = Executors.newFixedThreadPool(2);
    executor.submit(() -> registry.record(traceId, SOURCE_GATEWAY, passResult()));
    executor.submit(() -> registry.record(traceId, SOURCE_BUSINESS, passResult()));

    UiDualTaskFinalResult result = future.get(3, TimeUnit.SECONDS);
    assertThat(result.status()).isEqualTo(UiTaskStatus.PASS);
    assertThat(registry.size()).isZero();  // trackers 정리 확인
}
```

---

### [TEST-02] 중요 - UseCase 단위 테스트 부재

`LoginUseCase`, `ValidateTokenUseCase`, `LogoutUseCase` 에 대한 단위 테스트가 없습니다.

- 비밀번호 불일치 시 사용자 열거 방지 확인
- 만료 세션 거부 확인
- Redis 캐시 히트 시 DB 미조회 확인
- lastSeenAt 실패 시 인증 성공 확인

---

## 12. API 설계 (추가 평가)

---

### [API-01] 경미 - DELETE 메서드 Request Body

```
DELETE /api/eqp/{eqpId}  + Request Body
```

HTTP 스펙 상 DELETE에 body 허용은 되지만, 일부 프록시/방화벽에서 body를 제거합니다.
eqpId는 이미 경로 변수에 있으므로 추가 body가 필요하다면 쿼리 파라미터 사용을 권장합니다.

---

### [API-02] 중요 - Polling "처리 중"과 "없음" 구분 불가

```
GET /api/async/{traceId}
→ 결과 있음: 200 OK
→ 결과 없음 (처리 중 또는 잘못된 traceId): 404 Not Found
```

클라이언트가 "아직 처리 중"인지 "잘못된 traceId인지"를 구분할 수 없습니다.

#### 개선 방법

```
202 Accepted: 처리 중 (아직 결과 없음)
200 OK: 처리 완료
404 Not Found: 알 수 없는 traceId
408 Request Timeout: 처리 타임아웃 (실패)
```

---

## 개선 우선순위 요약

| ID | 제목 | 위험도 | 분류 | 비고 |
|----|------|--------|------|------|
| **SEC-04** | **평문 비밀번호 git 커밋 (DB/Redis)** | **치명적** | 보안 | ⭐ 신규 |
| ARCH-01 | DualResponseRegistry 분산 환경 불가 | **치명적** | 아키텍처 | |
| **ARCH-04** | **Dual 발행 fire-and-forget (브로커 실패 감지 불가)** | **치명적** | 아키텍처/무결성 | ⭐ 신규 |
| SEC-01 | JDK 직렬화 RCE 취약점 | **치명적** | 보안 | |
| SEC-02 | Redis Key에 토큰 원문 노출 | **높음** | 보안 | |
| EX-04 / SEC-03 | 권한 캐시 초기화 실패 시 전체 개방 | **높음** | 보안/예외 | |
| ARCH-02 | Core Port가 Kafka 타입에 직접 의존 | **높음** | 아키텍처 | |
| EX-01 | Kafka 파싱 실패 메시지 영구 소멸 | **높음** | 안정성 | |
| EX-02 | LogoutUseCase DB/Redis 불일치 | **높음** | 안정성 | |
| OOP-01 | EQP 발행 실패 시 보상 트랜잭션 없음 | **높음** | 무결성 | ARCH-04와 연관 |
| EX-03 | lastSeenAt 실패 시 인증 실패 전파 | **중간** | 예외처리 | |
| PERF-01 | lastSeenAt 동기 DB 업데이트 (캐시 미스 경로) | **중간** | 성능 | |
| STAB-01 | EQP_START/END 결과 소멸 가능성 | **중간** | 안정성 | |
| OOP-02 | 권한 캐시 기본 개방 정책 | **중간** | 보안/설계 | |
| **QUALITY-04** | **AuthController 강제 캐스팅 → 500 위험** | **중간** | 코드품질 | ⭐ 신규 |
| **SEC-05** | **플러그인 서명 검증 미구현** | **중간** | 보안 | ⭐ 신규 |
| QUALITY-01 | 비동기 재디스패치 토큰 이중 검증 | **중간** | 성능 | |
| MEM-01 | DualResponseTracker 누수 가능 경로 | **중간** | 메모리 | |
| ARCH-03 | ComponentScan 과다 스캔 | **낮음** | 구조 | |
| OOP-03 | ReplyData 필드명 대문자 컨벤션 위반 | **낮음** | 코드품질 | |
| QUALITY-02 | 401 응답 포맷 불일치 | **낮음** | 코드품질 | |
| QUALITY-03 | EqpController 발행 실패 흐름 복잡성 | **낮음** | 코드품질 | |
| **EX-05** | **EqpSequentialProcessor 무로그 예외 삼킴** | **낮음** | 예외처리 | ⭐ 신규 |
| OPS-01 | Metrics 부재 | **낮음** | 운영 | |
| OPS-02 | Distributed Tracing 미연계 | **낮음** | 운영 | |
| TEST-01 | DualResponseRegistry 동시성 테스트 | **낮음** | 테스트 | |
| TEST-02 | UseCase 단위 테스트 부재 | **낮음** | 테스트 | |
| API-01 | DELETE 메서드 Body 사용 | **낮음** | API | |
| API-02 | Polling 상태 구분 불가 | **낮음** | API | |
