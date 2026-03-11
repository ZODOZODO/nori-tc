# 01. 얇은 조립 진입점 (Thin Composition Root)

## 개요

nori-tc의 모든 애플리케이션(`tc-comm-gateway-app`, `tc-ui-backend-app`, `tc-business-core-app`)은
**앱 자체에 비즈니스 로직을 작성하지 않는다**는 공통 원칙을 따릅니다.

각 앱의 `src/main/java` 아래에는 오직 하나의 파일, `TcXxxApplication.java`만 존재하며,
실제 모든 로직은 `libs/` 하위의 라이브러리 모듈(Starter)에서 제공됩니다.

이 구조를 **Thin Composition Root(얇은 조립 진입점)** 패턴이라고 부릅니다.

---

## 왜 이 구조가 필요한가?

### 문제: 앱에 로직을 직접 작성하면 어떻게 되는가?

```
❌ 안 좋은 예시 (앱에 로직이 섞인 경우)
apps/tc-comm-gateway-app/src/
    └── TcCommGatewayApplication.java   ← Spring Boot 진입점
    └── config/
        └── KafkaConfig.java            ← Kafka 설정
        └── NettyConfig.java            ← Netty 설정
    └── service/
        └── EquipmentService.java       ← 비즈니스 로직
    └── handler/
        └── ChannelHandler.java         ← 채널 처리
```

- 앱 코드와 비즈니스 로직이 섞여서 테스트가 어려워집니다
- 다른 앱에서 같은 로직을 재사용하려면 복사-붙여넣기를 해야 합니다
- 앱의 역할(조립)과 라이브러리의 역할(구현)이 구분되지 않습니다

### 해결: Thin Composition Root

```
✅ 좋은 예시 (앱은 조립만, 로직은 lib에)
apps/tc-comm-gateway-app/src/
    └── TcCommGatewayApplication.java   ← 오직 여기만!

libs/comm/
    └── starter/tc-comm-gateway-starter/    ← 자동 조립
    └── adapter/tc-comm-gateway-kafka-adapter/  ← Kafka 처리
    └── adapter/tc-comm-gateway-netty-adapter/  ← Netty 처리
    └── core/tc-comm-gateway-core/             ← 비즈니스 로직
```

- 앱은 "무엇을 조합할지"만 선언합니다
- 실제 구현은 라이브러리에 있어 독립적으로 테스트 가능합니다
- 필요한 라이브러리 조합만 골라서 새 앱을 만들 수 있습니다

---

## 구조 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│                                                             │
│   TcCommGatewayApplication.java                             │
│   ┌─────────────────────────────────────────────────────┐   │
│   │  @SpringBootApplication                             │   │
│   │  public class TcCommGatewayApplication {            │   │
│   │      public static void main(String[] args) {       │   │
│   │          SpringApplication.run(...);                │   │
│   │      }                                              │   │
│   │  }                                                  │   │
│   └─────────────────────────────────────────────────────┘   │
│              ↓ build.gradle.kts 에서 의존                    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                    Starter Layer                            │
│                                                             │
│   tc-comm-gateway-starter                                   │
│   ┌─────────────────────────────────────────────────────┐   │
│   │  TcCommGatewayAutoConfiguration                     │   │
│   │  ┌─────────────────────────────────────────────┐    │   │
│   │  │ @AutoConfiguration                          │    │   │
│   │  │ @ComponentScan("com.nori.tc.comm")          │    │   │
│   │  │ @Import([GatewayCommConfig,                 │    │   │
│   │  │          GatewayProcessingConfig])          │    │   │
│   │  └─────────────────────────────────────────────┘    │   │
│   └─────────────────────────────────────────────────────┘   │
│              ↓ Bean 생성 위임                                │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                   Library Layer (libs/)                     │
│                                                             │
│   ┌──────────────────┐  ┌──────────────────┐               │
│   │  kafka-adapter   │  │  netty-adapter   │               │
│   └──────────────────┘  └──────────────────┘               │
│   ┌──────────────────┐  ┌──────────────────┐               │
│   │   db-adapter     │  │  redis-adapter   │               │
│   └──────────────────┘  └──────────────────┘               │
│   ┌──────────────────────────────────────────┐              │
│   │            core / domain                 │              │
│   └──────────────────────────────────────────┘              │
└─────────────────────────────────────────────────────────────┘
```

---

## 각 앱의 진입점 파일 구조

| 앱 이름 | 진입점 파일 | 스타터 |
|--------|-----------|--------|
| `tc-comm-gateway-app` | `TcCommGatewayApplication.java` | `tc-comm-gateway-starter` |
| `tc-ui-backend-app` | `TcUiBackendApplication.java` | `tc-ui-backend-starter` |
| `tc-business-core-app` | `TcBusinessCoreApplication.java` | `tc-business-core-starter` |

모든 앱이 동일한 패턴을 따릅니다.

---

## 실제 코드 예시

### TcCommGatewayApplication.java (앱 진입점 — 매우 단순)

```java
@SpringBootApplication
public class TcCommGatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(TcCommGatewayApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context =
            SpringApplication.run(TcCommGatewayApplication.class, args);

        // 기동 완료 시 Bean 개수로 컨텍스트 완성도 확인
        log.info("tc-comm-gateway-app 기동 완료. 등록된 Bean 수: {}",
            context.getBeanDefinitionCount());
    }
}
```

> 앱 진입점에는 설정, 서비스, 핸들러가 전혀 없습니다.
> `@SpringBootApplication`이 classpath에서 스타터의 `AutoConfiguration`을 찾아서 자동으로 조립합니다.

### build.gradle.kts (의존성 선언)

```kotlin
// 앱은 스타터에만 의존한다
dependencies {
    implementation(project(":libs:comm:starter:tc-comm-gateway-starter"))
    // 선택적으로 DB 스타터 하나 선택
    implementation(project(":libs:common:starter:tc-db-postgres-jpa-starter"))
}
```

### TcCommGatewayAutoConfiguration.java (스타터에서 실제 조립)

```java
@AutoConfiguration
@ComponentScan(basePackages = "com.nori.tc.comm")
@Import({
    GatewayCommConfiguration.class,
    GatewayProcessingConfiguration.class
})
public class TcCommGatewayAutoConfiguration {

    @PostConstruct
    public void logStartupSummary() {
        log.info("=== tc-comm-gateway 스타터 초기화 완료 ===");
        // Kafka topics, worker threads, queue capacity 등 요약 출력
    }
}
```

---

## 동작 흐름 (기동 시)

```
1. main() 호출
       ↓
2. SpringApplication.run() 실행
       ↓
3. classpath 스캔 → AutoConfiguration 발견
   (spring.factories 또는 AutoConfiguration.imports)
       ↓
4. TcCommGatewayAutoConfiguration 활성화
       ↓
5. @ComponentScan → com.nori.tc.comm 하위 모든 Bean 등록
       ↓
6. @Import → GatewayCommConfiguration, GatewayProcessingConfiguration 실행
       ↓
7. 모든 Bean 초기화 완료
       ↓
8. SmartLifecycle 컴포넌트들 순서대로 start() 호출
       ↓
9. 앱 준비 완료
```

---

## 장점 정리

| 항목 | 설명 |
|------|------|
| **단순한 앱 코드** | 앱 파일이 하나뿐이라 진입점 파악이 쉽습니다 |
| **독립적 테스트** | lib 모듈은 앱 없이도 단위/통합 테스트가 가능합니다 |
| **재사용성** | 같은 lib을 다른 앱에서도 조합해서 사용할 수 있습니다 |
| **명확한 경계** | 앱(조립 책임) vs lib(구현 책임)의 역할이 명확합니다 |
| **빠른 파악** | 새 개발자가 앱 구조를 빠르게 이해할 수 있습니다 |

---

## 주의사항

- 앱의 `src/main/java` 하위에 직접 서비스, 핸들러, 설정 클래스를 추가하지 않습니다
- 비즈니스 로직이 필요하면 반드시 `libs/` 하위 모듈에 작성합니다
- 앱의 `build.gradle.kts`에는 스타터 의존성만 선언하는 것을 원칙으로 합니다
- Bean 개수 로그는 기동 완료 여부를 빠르게 확인하는 운영 지표로 활용됩니다
