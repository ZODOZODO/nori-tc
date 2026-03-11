# 04. Kafka 메시지 처리 패턴 (Kafka Messaging Pattern)

## 개요

nori-tc의 모든 앱은 Kafka를 통해 서로 메시지를 주고받습니다.
메시지 처리는 **Subscriber(수신) → Dispatcher(분기) → Publisher(발행)** 3계층 구조로 분리되어 있습니다.

Kafka는 nori-tc에서 앱 간 통신의 핵심 채널입니다. HTTP API로 직접 서비스를 호출하지 않고,
Kafka 토픽을 통해 느슨하게 연결(Loose Coupling)되어 있습니다.

---

## 전체 메시지 흐름

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         nori-tc 메시지 흐름                              │
│                                                                         │
│  [UI 화면]                                                              │
│      ↓ HTTP                                                             │
│  [tc-ui-backend-app]                                                    │
│      │ 발행: tc.ui.events.gateway                                       │
│      │ 발행: tc.ui.events.business                                      │
│      ↓                               ↓                                  │
│  [tc-comm-gateway-app]          [tc-business-core-app]                  │
│      │ 소비: tc.ui.events.gateway     │ 소비: tc.ui.events.business     │
│      │ 소비: tc.eqp.commands          │ 소비: tc.eqp.events            │
│      │                               │ 소비: tc.mes.events             │
│      │ 발행: tc.eqp.events            │                                 │
│      │ 발행: tc.ui.commands           │ 발행: tc.eqp.commands           │
│      ↓                               │ 발행: tc.mes.commands           │
│  [설비 (Equipment)]                  │ 발행: tc.ui.commands             │
│                                      ↓                                  │
│                                [MES 시스템]                              │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 토픽 목록

| 토픽 이름 | 방향 | 설명 |
|----------|------|------|
| `tc.eqp.commands` | Gateway 수신 | 설비에 보낼 명령 |
| `tc.eqp.events` | Gateway 발행 | 설비에서 받은 이벤트 |
| `tc.ui.events.gateway` | Gateway 수신, UI 발행 | UI → Gateway 작업 요청 |
| `tc.ui.events.business` | Business Core 수신, UI 발행 | UI → Business 작업 요청 |
| `tc.ui.commands` | UI 수신, Gateway/Business 발행 | Gateway/Business → UI 응답 |
| `tc.mes.events` | Business Core 수신 | MES에서 받은 이벤트 |
| `tc.mes.commands` | Business Core 발행 | MES에 보낼 명령 |

---

## 3계층 구조

### 1단계: Subscriber (수신)

Kafka 토픽에서 메시지를 **폴링(polling)** 해서 가져옵니다.
메시지를 역직렬화하고, 계약 검증 후 Dispatcher에 넘깁니다.

```
Kafka Topic
    ↓ poll()
Subscriber
    ↓ JSON 역직렬화
    ↓ 계약 검증 (필수 필드 확인)
    ↓
Dispatcher
```

**주요 클래스 예시:**

| 앱 | 클래스 | 구독 토픽 |
|----|--------|---------|
| Gateway | `GatewayEqpCommandKafkaSubscriber` | `tc.eqp.commands` |
| Gateway | `GatewayUiEventKafkaSubscriber` | `tc.ui.events.gateway` |
| UI Backend | `UiCommandKafkaSubscriber` | `tc.ui.commands` |
| Business Core | `BusinessEqpEventKafkaSubscriber` | `tc.eqp.events` |

### 2단계: Dispatcher (분기)

수신된 메시지를 **타입, 목적지, 우선순위**에 따라 적절한 처리기로 분기합니다.

```
Dispatcher
    ├─ 메시지 타입 A → 처리기 A
    ├─ 메시지 타입 B → 처리기 B
    └─ 알 수 없는 타입 → DLQ or 무시
```

**주요 클래스 예시:**

| 앱 | 클래스 | 역할 |
|----|--------|------|
| Gateway | `GatewayCommandDispatcher` | 설비 명령 → 메일박스 전달 |
| Gateway | `GatewayUiTaskDispatcher` | UI 작업 → 타입별 핸들러 분기 |

### 3단계: Publisher (발행)

처리 결과를 Kafka 토픽으로 **발행**합니다.

```
처리 결과
    ↓ JSON 직렬화
Publisher
    ↓ kafkaTemplate.send()
    ↓ 동기 확인 (get(timeout))
Kafka Topic
```

**주요 클래스 예시:**

| 앱 | 클래스 | 발행 토픽 |
|----|--------|---------|
| Gateway | `GatewayEqpEventKafkaPublisher` | `tc.eqp.events` |
| Gateway | `GatewayUiCommandKafkaPublisher` | `tc.ui.commands` |
| UI Backend | `UiGatewayEventKafkaPublisher` | `tc.ui.events.gateway` |
| Business Core | Kafka 어댑터 구현체 | `tc.eqp.commands`, `tc.ui.commands` |

---

## Producer 신뢰성 설정

모든 앱의 Kafka Producer는 다음과 같은 신뢰성 설정을 공통으로 적용합니다.

```properties
# tc-messaging.properties
spring.kafka.producer.acks=all
# acks=all: Leader + 모든 ISR(동기화된 replica)이 응답해야 발행 완료로 처리
# → 가장 강력한 내구성 보장

spring.kafka.producer.properties.enable.idempotence=true
# idempotence: 네트워크 오류로 재전송 시 중복 메시지가 생기지 않도록 보장

spring.kafka.producer.retries=2147483647
# retries: 사실상 무한 재시도 (일시적인 Kafka 장애 시 자동 복구)
```

**이 세 가지 설정의 의미:**

```
acks=all + idempotence=true + retries=무한
   ↓
메시지가 Kafka에 정확히 한 번(Exactly-Once) 저장됨을 보장합니다.
일시적인 네트워크 오류나 리더 변경이 발생해도 메시지를 잃지 않습니다.
```

---

## Consumer 수동 커밋 (Manual ACK)

모든 앱의 Kafka Consumer는 **수동 커밋(Manual Acknowledge)** 방식을 사용합니다.

```properties
spring.kafka.consumer.enable-auto-commit=false   # 자동 커밋 비활성화
```

### 수동 커밋이 필요한 이유

```
자동 커밋의 문제:
  1. Kafka에서 메시지 수신
  2. 자동으로 offset 커밋 (이미 처리 완료로 기록)
  3. 처리 중 앱이 죽음
  4. 재시작 후 이 메시지는 이미 커밋되어 다시 받지 못함 → 메시지 유실!

수동 커밋의 장점:
  1. Kafka에서 메시지 수신
  2. 메시지 처리
  3. 처리 완료 후 명시적으로 ack.acknowledge() 호출
  4. 처리 중 앱이 죽어도, 재시작 후 같은 메시지를 다시 받음 → 안전
```

### 실제 패턴 예시

```java
// @KafkaListener 또는 수동 poll 방식
public void onMessage(ConsumerRecord<String, String> record,
                      Acknowledgment ack) {
    try {
        // 메시지 처리
        process(record);

        // 처리 완료 후 커밋
        ack.acknowledge();

    } catch (Exception e) {
        // 처리 실패 시 커밋하지 않음 → 재처리 대상
        log.error("메시지 처리 실패", e);
        // DLQ로 보내거나 재시도 정책 적용
    }
}
```

---

## 동기 발행 (Synchronous Publish)

UI Backend의 Publisher는 발행 후 응답을 기다리는 **동기 발행** 방식을 사용합니다.

```java
// UiGatewayEventKafkaPublisher.java
public void publish(KafkaGatewayEventMessage message) {
    try {
        ListenableFuture<SendResult<String, String>> future =
            kafkaTemplate.send(topic, key, json);

        // 최대 3초 대기 (설정: tc.ui.backend.kafka.publish-timeout-seconds=3)
        future.get(3, TimeUnit.SECONDS);

    } catch (TimeoutException e) {
        throw new UiKafkaPublishException("Kafka 발행 타임아웃", e);
    }
}
```

**왜 동기 발행인가?**
- UI 요청(HTTP)에 대한 즉각적인 오류 응답이 필요하기 때문입니다
- Kafka 브로커가 다운됐을 때 사용자에게 오류를 즉시 알릴 수 있습니다
- 발행 성공을 확인한 후에만 HTTP 응답을 반환합니다

---

## 메시지 크기 사전 검증

UI Backend는 발행 전 메시지 크기를 미리 검증합니다.

```java
// UiGatewayEventKafkaPublisher.java
final int payloadBytes = objectMapper.writeValueAsBytes(message).length;
final int requestBytes = payloadBytes + KAFKA_RECORD_OVERHEAD_BYTES; // 헤더 등 오버헤드 포함

if (requestBytes > publishProperties.getMaxRequestBytes()) {
    // 발행 전에 거절 → Kafka에 불필요한 오류 발생 방지
    throw new UiKafkaPublishException(
        "메시지 크기 초과: " + requestBytes + " bytes (max: " + maxRequestBytes + " bytes)"
    );
}
```

---

## 오류 처리 정책

### Gateway / Business Core

```
메시지 수신
    ↓
역직렬화 실패?  → DLQ 저장 + 커밋 (메시지 유실 방지)
    ↓
계약 위반?      → DLQ 저장 + 커밋
    ↓
설비 미연결?    → DLQ 저장 + 커밋
    ↓
처리 중 예외?   → 재시도 정책 적용
                   재시도 초과 → DLQ 저장 + 커밋
    ↓
정상 처리 완료 → 커밋
```

### UI Backend

```
메시지 수신 (tc.ui.commands)
    ↓
역직렬화 실패?  → WARN 로그 + 메트릭 증가 + 커밋 (DLT 없음)
    ↓
비즈니스 오류?  → ERROR 로그 + 커밋
    ↓
인프라 오류?    → 예외 재전파 → Kafka 컨테이너가 재시도
    ↓
정상 처리 완료 → 커밋
```

---

## MDC traceId 관리

모든 Subscriber는 메시지 수신 시 MDC(Mapped Diagnostic Context)에 traceId를 주입합니다.
이를 통해 하나의 요청이 여러 앱을 거칠 때도 로그에서 추적이 가능합니다.

```java
// 수신 메시지에 포함된 traceId를 MDC에 주입
try (MdcTraceScope scope = openTraceMdcScope(record.traceId())) {
    dispatcher.dispatch(record);
}
// scope 종료 시 MDC 자동 정리
```

자세한 내용은 [05-mdc-trace-logging.md](05-mdc-trace-logging.md)를 참고하세요.

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **토픽 partition 수 일치** | 코드의 `commands-partition-count` 설정과 실제 Kafka 토픽 partition 수가 반드시 일치해야 합니다 |
| **Consumer Group** | 같은 Consumer Group 내에서는 하나의 partition을 하나의 consumer만 처리합니다 |
| **메시지 순서** | 같은 partition 내에서만 순서가 보장됩니다. 순서가 중요하면 같은 key를 사용하세요 |
| **수동 커밋 누락** | 처리 후 `ack.acknowledge()`를 호출하지 않으면 같은 메시지를 계속 재처리합니다 |
| **발행 타임아웃** | Kafka 브로커가 응답 없을 때 UI Backend는 3초 후 오류 응답합니다. 설정으로 조정 가능합니다 |
