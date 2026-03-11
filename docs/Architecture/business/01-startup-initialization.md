# 01. 앱 기동 및 초기화 (Startup & Initialization)

## 개요

`tc-business-core-app`은 **비즈니스 로직 처리 전용** 백그라운드 프로세스입니다.
웹 서버 없이 Kafka 메시지를 수신해서 워크플로우를 실행하고, 결과를 다시 Kafka로 발행합니다.

기동 시 핵심 컴포넌트인 `BusinessRuntimeEngine`이 `SmartLifecycle`을 통해 순서대로 시작됩니다.

> 공통 기동 패턴(Thin Composition Root, 외부 설정 파일 로딩)은
> [common/01-thin-composition-root.md](../common/01-thin-composition-root.md) 및
> [common/03-external-config-loading.md](../common/03-external-config-loading.md)를 참고하세요.

---

## 전체 기동 순서

```
┌─────────────────────────────────────────────────────────────────────┐
│  1. main() → SpringApplication.run()                               │
│                                                                     │
│  2. 외부 설정 파일 로드 (application.yaml → config/ import)         │
│       - tc-messaging.properties  (Kafka 토픽/Consumer/Producer)     │
│       - tc-business-core.properties  (스레드/큐/타임아웃 설정)       │
│       - tc-db.properties  (DB 연결)                                 │
│       - tc-redis.properties  (Redis 연결)                           │
│                                                                     │
│  3. Bean 생성 및 의존성 주입 완료                                   │
│                                                                     │
│  4. @PostConstruct — 선행 초기화                                    │
│       - 모델 런타임 캐시 로딩 (DB → 메모리)                         │
│       - 플러그인 런타임 로딩 (DB → JAR → ClassLoader)               │
│                                                                     │
│  5. SmartLifecycle.start() — Phase 0                                │
│       - BusinessRuntimeEngine.start()                               │
│           ├─ 토픽 런타임 등록 (EQP/MES/UI)                         │
│           ├─ Topic Queue Consumer 스레드 시작 (토픽별 1개)          │
│           ├─ Mailbox Dispatcher 스레드 시작                         │
│           ├─ Worker Pool 스레드 시작                                │
│           └─ Timeout Scheduler 시작                                 │
│                                                                     │
│  6. ApplicationReadyEvent                                           │
│       - BusinessObservationLogger.logBootReady()                    │
│       - "tc-business-core-app 기동 완료. 등록된 Bean 수: NNN"       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 웹 서버 없는 백그라운드 프로세스

```yaml
# application.yaml
spring:
  main:
    web-application-type: none   # Tomcat/Netty 웹 서버 시작 안 함
    keep-alive: true             # 웹 서버 없어도 프로세스 계속 유지
```

`keep-alive: true` 덕분에 웹 서버가 없어도 JVM 프로세스가 종료되지 않고
Kafka 메시지를 계속 수신/처리합니다.

---

## BusinessRuntimeEngine — 핵심 런타임

```java
@Component
public class BusinessRuntimeEngine implements SmartLifecycle {

    @Override
    public int getPhase() {
        return 0;  // 기본 phase
    }

    @Override
    public void start() {
        // 1. 토픽별 런타임 등록
        registerTopicRuntime(eqpEventsTopic, eqpConsumerThreads);
        registerTopicRuntime(mesEventsTopic, mesConsumerThreads);
        registerTopicRuntime(uiEventsTopic, uiConsumerThreads);

        // 2. 각 토픽 Consumer 스레드 시작
        startTopicConsumerLoops();

        // 3. Mailbox 스케줄러 시작 (dispatcher 스레드)
        mailboxScheduler.start();

        // 4. Worker 실행 런타임 시작
        mailboxExecutionRuntime.start();

        // 5. Timeout 스케줄러 시작
        timeoutScheduler.start();

        running = true;
        log.info("BusinessRuntimeEngine 기동 완료");
    }

    @Override
    public void stop() {
        // 기동의 역순으로 정리
        timeoutScheduler.stop();
        mailboxExecutionRuntime.stop();      // Worker 정리
        mailboxScheduler.stop();            // Dispatcher 정리
        stopTopicConsumerLoops();           // Consumer 정리

        running = false;
        log.info("BusinessRuntimeEngine 종료 완료");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
```

---

## Kafka 토픽 구성

Business Core는 **3개 토픽을 수신**하고 **3개 토픽으로 발행**합니다.

```
┌─────────────────────────────────────────────────────────────────┐
│                  Kafka 토픽 구성                                │
│                                                                 │
│  수신 (Consume)            발행 (Produce)                       │
│  ─────────────────────     ─────────────────────               │
│  tc.eqp.events         →   tc.eqp.commands                     │
│  tc.mes.events         →   tc.mes.commands                     │
│  tc.ui.events.business →   tc.ui.commands                      │
└─────────────────────────────────────────────────────────────────┘
```

```properties
# tc-messaging.properties
tc.business.core.kafka.eqp-events-topic=tc.eqp.events
tc.business.core.kafka.mes-events-topic=tc.mes.events
tc.business.core.kafka.ui-events-topic=tc.ui.events.business
tc.business.core.kafka.eqp-commands-topic=tc.eqp.commands
tc.business.core.kafka.mes-commands-topic=tc.mes.commands
tc.business.core.kafka.ui-commands-topic=tc.ui.commands
```

---

## 초기화 설정 값

```properties
# tc-business-core.properties

# Consumer 스레드 (토픽별 고정 1개)
tc.business.core.kafka.eqp-events-consumer-threads=1
tc.business.core.kafka.mes-events-consumer-threads=1
tc.business.core.kafka.ui-events-consumer-threads=1

# 처리 스레드
tc.business.core.runtime.dispatcher-threads=4     # Mailbox → Worker 분배
tc.business.core.runtime.worker-threads=8         # 실제 비즈니스 처리
tc.business.core.runtime.timeout-scheduler-threads=2

# 큐 용량
tc.business.core.runtime.topic-queue-capacity=5000   # Topic Queue 크기
tc.business.core.runtime.mailbox-capacity=10000      # 설비별 Mailbox 크기

# 타임아웃 및 재시도
tc.business.core.runtime.task-timeout-ms=30000       # 작업 처리 최대 30초
tc.business.core.runtime.retry-max-attempts=3        # 최대 재시도 횟수
tc.business.core.runtime.retry-backoff-ms=1000       # 재시도 대기 1초
```

---

## 기동 완료 확인 방법

```
로그에서 확인할 수 있는 기동 완료 지표:

1. "모델 런타임 캐시 로딩 완료: N개 설비" 로그
   → DB에서 TcModelRuntime 로딩 완료

2. "플러그인 런타임 로딩 완료: N개" 로그
   → JAR 기반 플러그인 로딩 완료

3. "BusinessRuntimeEngine 기동 완료" 로그
   → Consumer/Worker/Timeout 스레드 모두 시작

4. "tc-business-core-app 기동 완료. 등록된 Bean 수: NNN"
   → 전체 기동 완료
```

---

## 종료 (Graceful Shutdown)

```
종료 신호 수신 (SIGTERM)
        ↓
SmartLifecycle.stop() 호출 (기동 역순)
        ↓
1. Timeout 스케줄러 종료 (진행 중인 타이머 처리 완료)
2. Worker Pool 종료 (처리 중인 작업 완료 대기)
3. Mailbox 스케줄러 종료
4. Consumer 스레드 종료 (현재 poll 완료 후 중단)
        ↓
프로세스 종료
```

**처리 중인 메시지는 어떻게 되나?**
- Consumer가 종료될 때 아직 커밋되지 않은 offset은 커밋되지 않습니다
- 재시작 후 해당 메시지를 다시 수신해서 처리합니다 (At-Least-Once 보장)

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **Consumer 스레드 1개** | 토픽별로 Consumer 스레드가 1개입니다. Kafka Consumer는 스레드 안전하지 않으므로 하나만 사용합니다. 처리 병렬화는 Worker Pool로 합니다 |
| **모델 캐시 로딩 시간** | 설비와 워크플로우가 많으면 @PostConstruct에서 DB 로딩 시간이 길어집니다 |
| **플러그인 실패** | 플러그인 JAR 로딩 실패 시 해당 설비에 대한 워크플로우가 실행되지 않습니다. 로그를 확인하세요 |
