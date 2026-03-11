# 02. Hexagonal 아키텍처 (포트-어댑터 패턴)

## 개요

nori-tc의 모든 앱은 **Hexagonal Architecture(육각형 아키텍처)**, 일명 **포트-어댑터 패턴**으로 설계되어 있습니다.

핵심 개념은 단순합니다:
- **Core(코어)**: 비즈니스 로직을 담당합니다. 외부 기술(Kafka, DB, Redis 등)을 전혀 모릅니다.
- **Port(포트)**: 코어가 외부와 소통하기 위해 선언한 인터페이스입니다.
- **Adapter(어댑터)**: 포트를 실제 기술로 구현한 클래스입니다 (Kafka 어댑터, DB 어댑터 등).

---

## 왜 이 구조가 필요한가?

### 문제: 비즈니스 로직과 기술이 섞이면?

```java
// 안 좋은 예시 — 비즈니스 로직이 Kafka에 직접 의존
public class EquipmentService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate; // ← Kafka에 직접 의존

    public void startEquipment(String eqpId) {
        // 비즈니스 로직...
        kafkaTemplate.send("tc.eqp.events", eqpId, "STARTED"); // ← Kafka 코드가 섞임
    }
}
```

**문제점:**
- Kafka 없이는 `EquipmentService`를 테스트할 수 없습니다
- 나중에 Kafka를 RabbitMQ로 바꾸려면 비즈니스 로직 파일까지 수정해야 합니다
- 코드의 책임이 명확하지 않습니다

### 해결: 포트-어댑터 분리

```java
// Port (인터페이스) — 코어에 선언
public interface EqpEventPublishPort {
    void publishStarted(String eqpId);
}

// Adapter (구현체) — Kafka 어댑터 모듈에 작성
public class EqpEventKafkaPublisher implements EqpEventPublishPort {
    public void publishStarted(String eqpId) {
        kafkaTemplate.send("tc.eqp.events", eqpId, "STARTED");
    }
}

// Core — 포트에만 의존, Kafka를 전혀 모름
public class EquipmentService {
    private final EqpEventPublishPort eventPublishPort; // ← 인터페이스에만 의존

    public void startEquipment(String eqpId) {
        // 비즈니스 로직만...
        eventPublishPort.publishStarted(eqpId); // ← 구현체가 뭔지 모름
    }
}
```

---

## 구조 다이어그램

```
                    외부 세계 (Kafka, DB, Redis, HTTP, Netty...)
                            │              │
                    ┌───────┴──────────────┴────────┐
                    │         Adapter Layer          │
                    │                                │
                    │  ┌─────────────────────────┐   │
                    │  │  Kafka Adapter          │   │
                    │  │  DB Adapter             │   │
                    │  │  Redis Adapter          │   │
                    │  │  Web Adapter (REST API) │   │
                    │  │  Netty Adapter          │   │
                    │  └─────────────────────────┘   │
                    └────────────┬───────────────────┘
                                 │ implements Port
                    ┌────────────┴───────────────────┐
                    │          Port Layer             │
                    │                                │
                    │  ┌─────────────────────────┐   │
                    │  │  EqpEventPublishPort     │   │
                    │  │  EqpCommandIngressPort   │   │
                    │  │  EqpQueryPort            │   │
                    │  │  DlqPublisherPort        │   │
                    │  └─────────────────────────┘   │
                    └────────────┬───────────────────┘
                                 │ depends on Port
                    ┌────────────┴───────────────────┐
                    │           Core Layer            │
                    │                                │
                    │  비즈니스 로직, 도메인 모델,    │
                    │  유스케이스, 상태머신,          │
                    │  처리 파이프라인 ...            │
                    │                                │
                    │  ← 외부 기술을 전혀 모름 →      │
                    └────────────────────────────────┘
```

---

## 각 앱의 포트-어댑터 구성

### tc-comm-gateway-app

| 포트 (인터페이스) | 어댑터 (구현체) | 역할 |
|----------------|---------------|------|
| `EqpEventPublishPort` | `GatewayEqpEventKafkaPublisher` | 설비 이벤트 → Kafka 발행 |
| `EqpCommandIngressPort` | `GatewayEqpCommandKafkaSubscriber` | Kafka → 설비 명령 수신 |
| `OutboundSenderPort` | Netty 어댑터 구현체 | 설비로 메시지 송신 |
| `EquipmentProfileQueryPort` | DB 어댑터 구현체 | DB에서 설비 정보 조회 |
| `DlqStorePort` | Redis 어댑터 구현체 | DLQ 저장 |

### tc-ui-backend-app

| 포트 (인터페이스) | 어댑터 (구현체) | 역할 |
|----------------|---------------|------|
| `UiGatewayEventPublishPort` | `UiGatewayEventKafkaPublisher` | UI 이벤트 → Gateway Kafka 발행 |
| `UiCommandIngressPort` | `UiCommandKafkaSubscriber` | Kafka → UI 명령 수신 |
| `EqpCrudPort` | `JpaEqpCrudPort` | 설비 CRUD (JPA) |
| `AsyncResultStorePort` | Redis 어댑터 구현체 | 비동기 결과 임시 저장 |
| `SessionPort` | `JpaSessionPort` | 세션 관리 (JPA) |

### tc-business-core-app

| 포트 (인터페이스) | 어댑터 (구현체) | 역할 |
|----------------|---------------|------|
| `BusinessEqpCommandPublishPort` | Kafka 어댑터 구현체 | EQP 명령 발행 |
| `BusinessTaskIngressPort` | Kafka 어댑터 구현체 | 작업 수신 |
| `BusinessModelRuntimeProvider` | DB 어댑터 구현체 | 모델 런타임 제공 |
| `BusinessDlqPublisherPort` | Redis 어댑터 구현체 | DLQ 발행 |
| `BusinessWorkflowPluginRuntimeProvider` | 플러그인 어댑터 구현체 | 동적 플러그인 제공 |

---

## 어댑터 조립 방식

각 앱은 `AutoConfiguration`에서 `@ComponentScan`과 `@Import`를 통해 어댑터를 조립합니다.

```java
// tc-comm-gateway-starter 조립 예시
@AutoConfiguration
@ComponentScan(basePackages = "com.nori.tc.comm")  // ← 하위 모든 Bean 등록
@Import({
    GatewayCommConfiguration.class,       // 공통 Bean (Clock, TraceId 등)
    GatewayProcessingConfiguration.class  // 처리 파이프라인 Bean
})
public class TcCommGatewayAutoConfiguration { }
```

```java
// tc-ui-backend-starter 조립 예시
@AutoConfiguration
@ComponentScan(basePackages = "com.nori.tc.ui.core")
@Import({
    UiWebAdapterAutoConfiguration.class,    // REST API 어댑터
    UiKafkaAdapterAutoConfiguration.class,  // Kafka 어댑터
    UiRedisAdapterAutoConfiguration.class,  // Redis 어댑터
    UiDbAdapterAutoConfiguration.class      // DB 어댑터
})
public class TcUiBackendAutoConfiguration { }
```

---

## No-op (Null Object) 어댑터 패턴

포트 구현체가 없을 때 앱이 뜨지 않으면 개발/테스트가 불편합니다.
이를 위해 아무것도 하지 않는 **No-op 어댑터**를 기본값으로 제공합니다.

```java
// GatewayCommConfiguration.java 예시
@Bean
@ConditionalOnMissingBean
public BusinessWorkflowPluginRuntimeProvider pluginRuntimeProvider() {
    // 플러그인 어댑터가 없을 경우 아무것도 안 하는 no-op 반환
    return BusinessWorkflowPluginRuntimeProvider.noop();
}
```

- 테스트 환경에서는 실제 Kafka 없이도 앱을 실행할 수 있습니다
- 프로덕션에서는 실제 어댑터가 Bean으로 등록되어 no-op을 대체합니다

---

## 모듈 디렉토리 구조 (Gateway 기준)

```
libs/comm/
├── adapter/
│   ├── tc-comm-gateway-kafka-adapter/   ← Kafka 어댑터 (포트 구현)
│   ├── tc-comm-gateway-netty-adapter/   ← Netty 어댑터 (포트 구현)
│   ├── tc-comm-gateway-db-adapter/      ← DB 어댑터 (포트 구현)
│   ├── tc-comm-gateway-redis-adapter/   ← Redis 어댑터 (포트 구현)
│   └── tc-comm-gateway-plugin-adapter/  ← 플러그인 어댑터 (포트 구현)
├── core/
│   └── tc-comm-gateway-core/            ← 비즈니스 로직 (포트 선언)
├── domain/
│   └── tc-comm-gateway-domain/          ← 도메인 모델 (순수 Java)
└── starter/
    └── tc-comm-gateway-starter/         ← 자동 조립 (AutoConfiguration)
```

---

## 핵심 원칙 정리

| 원칙 | 설명 |
|------|------|
| **의존 방향** | 항상 외부(어댑터) → 내부(코어) 방향. 코어는 어댑터를 모릅니다 |
| **포트는 인터페이스** | 코어에서 선언하고, 어댑터에서 구현합니다 |
| **기술 교체 가능** | Kafka → RabbitMQ로 바꾸려면 어댑터만 교체하면 됩니다 |
| **독립 테스트** | 코어는 Mock 포트만으로 테스트 가능합니다 |
| **No-op 기본값** | 어댑터 미등록 시 no-op으로 fallback해 개발 편의성을 높입니다 |

---

## 주의사항

- Core 레이어에서 Kafka, JPA, Redis 등 특정 기술 클래스를 직접 import하지 않습니다
- 새 기능 추가 시 포트(인터페이스)를 먼저 정의하고 어댑터를 나중에 구현합니다
- 어댑터는 반드시 포트 인터페이스를 implements 해야 합니다. 직접 서비스를 호출하지 않습니다
- 의존성 방향 검증은 `TcXxxAppDependencyCompositionGuardTest`(ArchUnit 기반)에서 자동으로 수행됩니다
