# 01. 앱 기동 및 초기화 (Startup & Initialization)

## 개요

`tc-comm-gateway-app`은 기동 시 여러 단계를 거쳐 준비 상태가 됩니다.
단순히 Spring Boot가 뜨는 것으로 끝나지 않고, **설비 프로파일 로딩**, **설정 검증**, **Netty 바인딩**까지 완료되어야 실제로 설비 통신이 가능합니다.

기동 실패 시 앱이 즉시 종료되는 **Fail-Fast** 설계를 채택하여, 잘못된 설정으로 운영되는 상황을 방지합니다.

---

## 전체 기동 순서

```
┌─────────────────────────────────────────────────────────────────────┐
│                       기동 Phase 순서                               │
│                                                                     │
│  [Phase: Spring 초기화]                                             │
│   1. main() 호출                                                    │
│   2. Spring Boot 컨텍스트 시작                                      │
│   3. 외부 설정 파일 로드 (config/tc-*.properties)                   │
│   4. Bean 생성 및 의존성 주입                                       │
│   5. @PostConstruct 실행                                            │
│       ├─ EquipmentContextBootstrap.load()                           │
│       └─ GatewayKafkaOperationalInvariantChecker.verify()           │
│                                                                     │
│  [Phase -100: 상태머신 / UI 처리기 초기화]                          │
│   6. EquipmentLifecycleStateMachine.start()                         │
│   7. GatewayUiTaskDispatcher.start()                                │
│                                                                     │
│  [Phase 0: 통신 및 메시지 처리 시작]                                │
│   8. GatewayNettyBootstrap.start()                                  │
│       └─ enabled 장비 자동 연결 시도                                │
│   9. EquipmentProcessingCoordinator.start()                         │
│   10. GatewayEqpCommandKafkaSubscriber.start()                      │
│   11. GatewayUiEventKafkaSubscriber.start()                         │
│                                                                     │
│  [ApplicationReadyEvent]                                            │
│   12. 기동 완료 로그 출력                                           │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Phase -100: 먼저 시작해야 하는 컴포넌트

Spring의 `SmartLifecycle` 인터페이스의 `getPhase()`가 **작은 숫자일수록 먼저 시작**됩니다.

```
Phase -100 (가장 먼저 시작)
    ↓
Phase 0 (기본값, 나중에 시작)
```

Phase -100에서 시작하는 이유:
- 상태머신(`EquipmentLifecycleStateMachine`)이 준비되기 전에 Netty나 Kafka가 메시지를 받으면 처리할 수 없습니다
- UI Task Dispatcher도 먼저 준비되어야 이후 메시지를 처리할 수 있습니다

```java
// EquipmentLifecycleStateMachine.java
@Override
public int getPhase() {
    return -100;  // 가장 먼저 시작
}
```

---

## EquipmentContextBootstrap — 설비 프로파일 로딩

앱이 뜰 때 DB에서 모든 설비 정보를 메모리로 로딩합니다.

```java
@Component
public class EquipmentContextBootstrap {

    @PostConstruct
    public void load() {
        int page = 0;
        int totalLoaded = 0;
        List<EquipmentProfile> batch;

        do {
            // 500개씩 페이지 단위 로드 (대량 설비 시 OOM 방지)
            batch = profileQueryPort.findAllPaged(page++, PAGE_SIZE);

            for (EquipmentProfile profile : batch) {
                if (profile.isEnabled()) {
                    // 활성화된 설비: 시작 대기 상태로 등록
                    contextRegistry.register(profile, DesiredState.STARTED,
                                             RuntimeState.DISCONNECTED);
                } else {
                    // 비활성화된 설비: 종료 상태로 등록
                    contextRegistry.register(profile, DesiredState.ENDED,
                                             RuntimeState.REGISTERED);
                }
                totalLoaded++;
            }
        } while (batch.size() == PAGE_SIZE);

        log.info("설비 프로파일 로딩 완료: {}개 (활성: {}개)",
                 totalLoaded, contextRegistry.countEnabled());
    }
}
```

**설비 상태 초기값:**

| 설비 상태 | DesiredState | RuntimeState | 의미 |
|----------|-------------|-------------|------|
| enabled=true | STARTED | DISCONNECTED | 연결되어야 하지만 아직 연결 안 됨 |
| enabled=false | ENDED | REGISTERED | 비활성화 (연결 시도 안 함) |

---

## GatewayKafkaOperationalInvariantChecker — 운영 불변조건 검증

설정 값의 일관성을 **앱 기동 시점에 미리 검증**합니다.
잘못된 설정으로 런타임 오류가 발생하는 것을 방지합니다.

```java
@Component
public class GatewayKafkaOperationalInvariantChecker {

    @PostConstruct
    public void verify() {
        verifyPartitionCountConsistency();
        verifyOwnedPartitionsRange();
        verifyTopicsExist();
    }

    // 1. Kafka 토픽의 실제 partition 수 = 설정의 commands-partition-count
    private void verifyPartitionCountConsistency() {
        int actualPartitions = kafkaAdmin.describeTopics(commandsTopic)
            .get(commandsTopic).partitions().size();
        int configuredCount = properties.getCommandsPartitionCount();

        if (actualPartitions != configuredCount) {
            throw new GatewayStartupException(
                "tc.eqp.commands 토픽의 실제 partition 수(%d)가 " +
                "설정값(%d)과 다릅니다. tc-comm.properties의 " +
                "commands-partition-count를 수정하세요.",
                actualPartitions, configuredCount
            );
        }
    }

    // 2. owned-partitions가 [0, commands-partition-count) 범위 이내
    private void verifyOwnedPartitionsRange() {
        for (int partition : properties.getOwnedPartitions()) {
            if (partition < 0 || partition >= properties.getCommandsPartitionCount()) {
                throw new GatewayStartupException(
                    "owned-partitions에 유효하지 않은 partition 번호: %d", partition
                );
            }
        }
    }

    // 3. 필요한 Kafka 토픽이 실제로 존재하는지
    private void verifyTopicsExist() {
        List<String> requiredTopics = List.of(
            commandsTopic, eventsTopic, uiGatewayEventsTopic, uiCommandsTopic
        );
        // 존재하지 않는 토픽이 있으면 예외
    }
}
```

**검증 실패 시 동작:**
```
검증 실패 → GatewayStartupException 발생 → Spring 컨텍스트 초기화 실패 → 앱 즉시 종료
```

이를 **Fail-Fast** 설계라고 합니다. 잘못된 상태로 계속 동작하는 것보다,
기동 시점에 즉시 실패하고 알림을 받는 것이 훨씬 안전합니다.

---

## GatewayNettyBootstrap — Netty 시작 및 자동 연결

Phase 0에서 시작하며, 활성화된 모든 설비에 대해 TCP 연결을 자동으로 시도합니다.

```java
@Component
public class GatewayNettyBootstrap implements SmartLifecycle {

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public void start() {
        // 1. Netty 이벤트루프 그룹 생성
        bossGroup = new NioEventLoopGroup(properties.getBossThreads());   // 기본 1개
        workerGroup = new NioEventLoopGroup(properties.getWorkerThreads()); // 기본 4개

        // 2. enabled 설비 목록 조회
        List<EquipmentContext> enabledContexts = contextRegistry.findAllEnabled();

        // 3. 각 설비별 TCP 연결 시도
        for (EquipmentContext ctx : enabledContexts) {
            eqpBindingService.startBinding(ctx);
        }

        log.info("Netty 기동 완료. 연결 시도 중인 설비: {}개", enabledContexts.size());
    }
}
```

**연결 모드별 동작:**

| 연결 모드 | 동작 | 설명 |
|----------|------|------|
| PASSIVE | `ServerBootstrap.bind(port)` | Gateway가 포트를 열고 설비가 접속하기를 기다림 |
| ACTIVE | `Bootstrap.connect(host, port)` | Gateway가 설비에 직접 TCP 접속 시도 |

---

## Kafka Subscriber 기동

Phase 0에서 Kafka Consumer가 시작되어 토픽 구독을 시작합니다.

```java
@Component
public class GatewayEqpCommandKafkaSubscriber implements SmartLifecycle {

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public void start() {
        // ASSIGN 모드: owned-partitions만 구독
        // (동적 Rebalance 없음 — 이 인스턴스가 담당할 partition 고정)
        consumer.assign(ownedPartitions);
        consumer.seekToBeginning(/* 필요 시 */);

        // 별도 스레드에서 poll 루프 시작
        pollThread.start();
        log.info("Kafka Consumer 시작: topic={}, partitions={}",
                 commandsTopic, ownedPartitions);
    }
}
```

---

## 기동 완료 확인 방법

```
로그에서 확인할 수 있는 기동 완료 지표:

1. "설비 프로파일 로딩 완료: N개" 로그
   → EquipmentContextBootstrap 완료

2. "운영 불변조건 검증 완료" 로그
   → GatewayKafkaOperationalInvariantChecker 완료

3. "Netty 기동 완료. 연결 시도 중인 설비: N개" 로그
   → GatewayNettyBootstrap 완료

4. "Kafka Consumer 시작: topic=tc.eqp.commands, partitions=[0, 1]" 로그
   → Kafka Subscriber 완료

5. "tc-comm-gateway-app 기동 완료. 등록된 Bean 수: NNN" 로그
   → 전체 기동 완료
```

---

## 비정상 기동 케이스

| 상황 | 결과 | 해결 방법 |
|------|------|---------|
| `tc-comm.properties` 파일 없음 | 앱 기동 실패 (파일 없음 예외) | config/ 디렉토리에 파일 생성 |
| Kafka 토픽 partition 수 불일치 | `GatewayStartupException` → 즉시 종료 | 토픽 재생성 또는 `commands-partition-count` 수정 |
| `owned-partitions` 범위 초과 | `GatewayStartupException` → 즉시 종료 | `tc-comm.properties`의 `owned-partitions` 수정 |
| DB 연결 실패 | Spring 컨텍스트 초기화 실패 → 즉시 종료 | DB 서버 상태 확인 및 `tc-db.properties` 설정 확인 |
| 플러그인 JAR allowlist 실패 | 기동 실패 (`fail-fast-on-startup=true` 시) | SHA-256 allowlist에 JAR 해시 등록 |

---

## 운영 포인트

| 항목 | 내용 |
|------|------|
| **기동 시간** | 설비 수가 많으면 DB 로딩 시간이 길어집니다. 페이지 크기(PAGE_SIZE=500)를 상황에 맞게 조정하세요 |
| **Fail-Fast 유지** | `fail-fast-on-startup=false`로 변경하지 마세요. 잘못된 설정이 숨겨질 수 있습니다 |
| **설비 자동 연결** | 기동 시 모든 enabled 설비에 연결을 시도합니다. 설비가 꺼져 있어도 재연결 스케줄러가 계속 시도합니다 |
| **종료 순서** | 기동의 역순으로 종료됩니다. Kafka → Netty → 상태머신 순으로 정리됩니다 |
