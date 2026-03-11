# 02. 3단계 큐 구조 (Three-Stage Queue Structure)

## 개요

`tc-business-core-app`은 Kafka에서 수신한 메시지를 처리하기까지 **3단계 큐**를 거칩니다.

각 단계는 서로 다른 역할을 담당하며, 이를 통해 **수신 속도와 처리 속도의 차이**를 안전하게 흡수하고,
**설비별 순서 보장**과 **병렬 처리**를 동시에 달성합니다.

> Mailbox 패턴의 기본 개념은
> [common/09-mailbox-sequential-processing.md](../common/09-mailbox-sequential-processing.md)를 참고하세요.
> 이 문서는 Business Core 고유의 **3단계 구조**에 집중합니다.

---

## 3단계 구조 다이어그램

```
┌──────────────────────────────────────────────────────────────────────┐
│  1단계: Topic Queue (Kafka → Runtime)                               │
│                                                                      │
│  Kafka Consumer 스레드 (토픽별 1개, 총 3개)                         │
│                                                                      │
│  [tc.eqp.events Consumer]  → TopicQueue(EQP)  [5000 capacity]      │
│  [tc.mes.events Consumer]  → TopicQueue(MES)  [5000 capacity]      │
│  [tc.ui.events Consumer]   → TopicQueue(UI)   [5000 capacity]      │
│                                                                      │
│  역할: Kafka poll() → 메시지를 큐에 적재 → Kafka ACK 준비          │
└──────────────────────────────┬───────────────────────────────────────┘
                               ↓ eqpId 기반 분류
┌──────────────────────────────────────────────────────────────────────┐
│  2단계: Mailbox Queue (설비별 분류)                                  │
│                                                                      │
│  Mailbox(EQP-001) [10000 capacity]: [작업1] [작업2] [작업3]         │
│  Mailbox(EQP-002) [10000 capacity]: [작업1] [작업2]                 │
│  Mailbox(EQP-N)   [10000 capacity]: [작업1]                         │
│                                                                      │
│  역할: eqpId 기준으로 메시지 분류 → 설비별 순서 보장               │
└──────────────────────────────┬───────────────────────────────────────┘
                               ↓ Worker 할당
┌──────────────────────────────────────────────────────────────────────┐
│  3단계: Worker Pool (실제 비즈니스 처리)                             │
│                                                                      │
│  Worker 1 ←── EQP-001의 작업1 처리 (워크플로우 실행)               │
│  Worker 2 ←── EQP-002의 작업1 처리                                  │
│  Worker 3 ←── EQP-003의 작업1 처리                                  │
│  ...                                                                 │
│  Worker 8 ←── 유휴                                                   │
│                                                                      │
│  역할: 실제 워크플로우 실행, 액션 처리, Kafka 발행                  │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 1단계: Topic Queue

### 역할

Kafka Consumer가 `poll()`로 메시지를 가져와서 **Topic Queue**에 적재합니다.
이 큐는 Kafka Consumer 스레드와 내부 처리 사이의 **버퍼** 역할을 합니다.

```java
// BusinessRuntimeEngine.java 내부
private final LinkedBlockingQueue<BusinessInboundRecord> eqpTopicQueue
    = new LinkedBlockingQueue<>(topicQueueCapacity);  // 기본 5000개
```

### Consumer 루프

```java
private void runTopicConsumerLoop(
        KafkaConsumer<String, String> consumer,
        BlockingQueue<BusinessInboundRecord> queue) {

    while (running) {
        // 1. Kafka에서 메시지 수신 (최대 500ms 대기)
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

        // 2. ACK(offset commit) 준비 처리
        drainAckAndPrepareCommit(consumer);

        // 3. 메시지를 Topic Queue에 적재
        for (ConsumerRecord<String, String> record : records) {
            BusinessInboundRecord inbound = toInboundRecord(record);
            boolean offered = queue.offer(inbound, 100, TimeUnit.MILLISECONDS);

            if (!offered) {
                // Topic Queue가 가득 찬 경우 → DLQ 처리
                log.warn("Topic Queue 오버플로우: eqpId={}", inbound.eqpId());
                dlqPublisher.publish(inbound, "TOPIC_QUEUE_OVERFLOW");
            }
        }

        // 4. Kafka offset 커밋
        consumer.commitSync(collectCommitOffsets());
    }
}
```

### ACK 조율 (중요)

Kafka 수동 커밋을 안전하게 처리하기 위한 메커니즘입니다.

```
처리 흐름:

1. Kafka poll() → records 수신
2. records → Topic Queue에 적재
3. ACK Queue에 추가 (처리 완료 예약)
4. Worker가 처리 완료 → ACK Queue에서 제거
5. 다음 poll() 직전에 drainAckAndPrepareCommit()
6. 커밋 준비된 offset → Kafka commitSync()
```

이 방식으로 처리 완료된 메시지만 커밋되어 메시지 유실을 방지합니다.

---

## 2단계: Mailbox Queue

### 역할

Topic Queue에서 꺼낸 메시지를 **eqpId 기준으로 분류**해서 각 설비의 Mailbox에 넣습니다.
같은 설비의 메시지는 반드시 같은 Mailbox에 들어가므로 **순서가 보장**됩니다.

```java
// Mailbox Dispatcher 스레드 (dispatcher-threads=4)
while (running) {
    BusinessInboundRecord record = topicQueue.poll(100, TimeUnit.MILLISECONDS);
    if (record == null) continue;

    // eqpId 기반으로 Mailbox 선택 (없으면 새로 생성)
    Mailbox<BusinessMailboxTask> mailbox = mailboxRegistry.getOrCreate(record.eqpId());

    BusinessMailboxTask task = BusinessMailboxTask.of(record);
    boolean enqueued = mailbox.offer(task);

    if (!enqueued) {
        // Mailbox가 가득 찬 경우 → DLQ
        dlqPublisher.publish(record, "MAILBOX_OVERFLOW");
    }
}
```

### Mailbox 용량

```properties
tc.business.core.runtime.mailbox-capacity=10000  # 설비별 Mailbox 최대 크기
```

설비가 100개면 최대 100 × 10,000 = 1,000,000개의 작업이 메모리에 존재할 수 있습니다.
실제로는 처리 속도가 빠르므로 항상 가득 차지는 않습니다.

---

## 3단계: Worker Pool

### 역할

`MailboxExecutionRuntime`이 유휴 Worker에 Mailbox를 할당합니다.
각 Worker는 할당된 Mailbox에서 작업을 꺼내 실제 비즈니스 로직(워크플로우)을 실행합니다.

```java
// Worker 실행 패턴
void executeMailbox(Mailbox<BusinessMailboxTask> mailbox) {
    BusinessMailboxTask task;

    while ((task = mailbox.poll()) != null) {
        try (BusinessLogContext ctx =
                BusinessLogContext.withEqpAndTraceId(
                    task.record().eqpId(),
                    task.record().traceId()
                )) {

            // 타임아웃 감시 하에 워크플로우 실행
            timeoutBoundRunner.run(() -> processTask(task));

        } catch (TimeoutExceededException e) {
            // 처리 타임아웃 → DLQ
            dlqPublisher.publish(task.record(), "TIMEOUT");
        } catch (Exception e) {
            // 처리 오류 → 재시도 정책 적용
            retryPolicyEvaluator.evaluate(task, e);
        }
    }
}
```

---

## 3단계가 필요한 이유

### 1단계(Topic Queue)만 있으면?

```
Kafka → Topic Queue → Worker (직접)

문제: EQP-001의 메시지가 Worker-1과 Worker-2에 동시에 배분될 수 있음
→ 같은 설비의 메시지 순서가 보장되지 않음
→ 레시피 설정보다 처리 시작이 먼저 실행될 수 있음 (심각한 오류)
```

### 2단계(Mailbox) 추가 후

```
Kafka → Topic Queue → Mailbox(EQP별) → Worker

장점:
- 같은 설비의 메시지는 항상 같은 Mailbox에서 꺼냄
- 하나의 Mailbox는 동시에 하나의 Worker만 처리
- 순서 완전 보장 + 설비 간 병렬 처리
```

---

## Topic Queue가 필요한 이유

> 초급 개발자를 위한 상세 설명입니다.

### Kafka Consumer 스레드란 무엇인가?

Kafka에서 메시지를 받으려면 **Consumer 스레드**가 `poll()`을 주기적으로 호출해야 합니다.
`poll()`을 호출하면 Kafka 브로커에서 메시지를 가져오고, Kafka 브로커는 이 호출을 "나 아직 살아있어요"라는 **heartbeat 신호**로도 인식합니다.

```
Kafka 브로커 ←─── poll() 호출 ───── Consumer 스레드
              ───── 메시지 전달 ────→
```

문제는 `poll()`을 너무 오랫동안 호출하지 않으면 Kafka 브로커가 이 Consumer를 **죽었다고 판단**한다는 것입니다.
이 시간 제한이 `max.poll.interval.ms` 설정값이며, 기본값은 **5분**입니다.

---

### Consumer 스레드가 직접 처리하면 어떤 문제가 생기나?

만약 Topic Queue 없이 Consumer 스레드가 메시지를 직접 처리한다고 가정하겠습니다.

```
Consumer 스레드가 직접 처리하는 경우:

poll()
  └─→ 메시지 10개 수신
        └─→ 메시지 1개: 워크플로우 실행 (DB 조회 + 상태 전이 + Kafka 발행 = 약 200ms)
              └─→ 메시지 2개: 워크플로우 실행 (200ms)
                    └─→ ...
                          └─→ 메시지 10개 처리 완료 (총 2000ms = 2초)
poll()  ← 다음 poll()은 2초 후에야 호출됨
```

메시지 처리가 느리면 `poll()` 간격이 벌어집니다.
처리 부하가 몰려 5분을 넘기면 다음 문제가 발생합니다.

```
문제 발생 시나리오:

1. Consumer가 메시지를 받아서 처리 중 (워크플로우가 오래 걸림)
2. 5분 동안 poll()을 못 함
3. Kafka 브로커: "Consumer-1이 죽었다고 판단 → Group Rebalance 시작"
4. 처리 중이던 파티션을 다른 Consumer에게 재할당
5. Consumer-1은 여전히 처리 중 + Consumer-2도 같은 메시지를 처리 시작
   → 같은 메시지가 두 번 처리되는 중복 처리 발생!
6. Consumer-1이 처리를 완료하고 offset을 커밋하려 해도 파티션 소유권이 없어 실패
   → offset 관리 혼란 → 메시지 유실 또는 중복 위험
```

---

### Topic Queue가 이 문제를 어떻게 해결하는가?

Topic Queue를 두면 Consumer 스레드와 처리 로직을 완전히 분리할 수 있습니다.

```
Topic Queue가 있는 경우:

Consumer 스레드:
  poll() → 메시지 10개 수신 → Topic Queue에 적재 (1ms 이내) → 즉시 다음 poll()
  poll() → 메시지 10개 수신 → Topic Queue에 적재 (1ms 이내) → 즉시 다음 poll()
  → poll() 간격이 항상 짧게 유지됨 → Rebalance 발생 없음

Dispatcher 스레드 (별도):
  Topic Queue에서 꺼냄 → Mailbox로 분류
  → 아무리 느려도 Consumer 스레드에 전혀 영향 없음

Worker 스레드 (별도):
  Mailbox에서 꺼냄 → 워크플로우 실행 (200ms씩)
  → 아무리 느려도 Consumer 스레드에 전혀 영향 없음
```

**결론: Consumer 스레드는 오직 "메시지 수신 → Topic Queue 적재"만 담당합니다.
절대로 처리 로직을 실행하지 않으므로 항상 빠르게 `poll()`을 호출할 수 있습니다.**

---

### ACK(offset commit) 조율 문제

또 다른 이유는 Kafka **수동 커밋 방식** 때문입니다.

이 시스템은 "처리가 완전히 끝난 메시지만 Kafka에 커밋"하는 정책을 사용합니다.
처리 완료 전에 커밋하면 앱이 죽었을 때 해당 메시지를 다시 받지 못해 **메시지 유실**이 발생하기 때문입니다.

```
올바른 커밋 흐름:

1. poll() → 메시지 A, B, C 수신
2. Topic Queue에 A, B, C 적재
3. Worker가 A 처리 완료 → "A 커밋 준비 완료" 신호
4. Worker가 B 처리 완료 → "B 커밋 준비 완료" 신호
5. Worker가 C 처리 완료 → "C 커밋 준비 완료" 신호
6. 다음 poll() 직전: drainAckAndPrepareCommit() 실행
   → A, B, C 모두 완료 확인 → Kafka에 offset 커밋
```

Topic Queue와 ACK Queue가 협력하여 "처리 완료된 메시지만 정확히 커밋"하는 메커니즘을 만듭니다.
Consumer 스레드가 직접 처리하면 이 타이밍 조율이 훨씬 복잡해집니다.

---

### Gateway는 왜 Topic Queue가 없는가?

Gateway의 수신 주체는 **Netty 채널 핸들러**입니다.
Netty는 이미 비동기 I/O 이벤트 루프 기반으로 동작하므로 채널 핸들러가 해야 할 일은
"bytes decode → Mailbox에 offer (non-blocking, O(1))" 뿐입니다.

처리가 매우 빠르고, Kafka `max.poll.interval.ms` 같은 제약도 없으며,
TCP 연결 자체가 설비의 전송 속도를 자연스럽게 제한(배압)하기 때문에
별도의 Topic Queue 없이도 안정적으로 동작합니다.

```
Gateway 구조 (2단계):
  Netty 채널 핸들러 → Mailbox → Worker Pool

Business Core 구조 (3단계):
  Kafka Consumer → Topic Queue → Mailbox → Worker Pool
```

---

## Mailbox 용량 차이 (Gateway 2,048 vs Business Core 10,000)

### 설정값 비교

```properties
# Gateway (tc-comm.properties)
tc.comm.gateway.inbound-queue-capacity=2048      # 설비당 Mailbox 최대 크기

# Business Core (tc-business-core.properties)
tc.business.core.runtime.mailbox-capacity=10000  # 설비당 Mailbox 최대 크기
```

### 왜 Business Core의 Mailbox가 5배 더 큰가?

용량 차이는 **처리 속도**와 **버스트(burst) 가능성** 두 가지 요인에서 비롯됩니다.

#### 처리 속도 차이

| 구분 | 처리 내용 | 처리 시간 |
|------|-----------|-----------|
| Gateway | bytes decode → Kafka 발행 | 수 ms |
| Business Core | 워크플로우 매칭 → 액션 실행 → DB 조회 → Kafka 발행 | 수십 ~ 수백 ms |

Gateway는 처리가 빠르기 때문에 Mailbox가 쌓여도 금방 소화합니다.
Business Core는 처리가 복잡하고 느리기 때문에 메시지가 더 오래 Mailbox에 대기합니다.
대기 공간이 작으면 금방 오버플로우가 발생해서 DLQ로 빠지게 됩니다.

#### 버스트 가능성 차이

Gateway는 실시간 TCP 연결을 통해 메시지를 받습니다.
설비가 보내는 속도 = TCP 전송 속도이므로, 자연스러운 흐름 제어(TCP backpressure)가 작동합니다.
설비가 갑자기 수만 건을 한번에 보내는 것은 TCP 물리적으로 불가능합니다.

```
Gateway 흐름 제어:
  설비 → [TCP 버퍼 제한] → Netty → Mailbox
                ↑
         여기서 자연스럽게 속도 제한됨
```

Business Core는 Kafka에서 메시지를 받습니다.
Kafka 토픽에 이미 수만 건이 쌓인 상태라면 Consumer가 기동 시점에 한번에 대량으로 가져올 수 있습니다.
예를 들어 앱을 잠시 내렸다가 다시 올리면 그 사이에 쌓인 메시지가 한번에 밀려들 수 있습니다.

```
Business Core 버스트 시나리오:
  앱 재시작 → 그 사이 Kafka에 쌓인 5,000건 → Consumer가 한번에 수신
  → Topic Queue → Dispatcher가 설비별 Mailbox로 분류
  → Mailbox가 작으면 → 오버플로우 → DLQ 대량 발생
```

따라서 Business Core의 Mailbox는 이런 버스트를 안전하게 흡수할 수 있도록 더 크게 설정합니다.

#### 메모리 관점

Mailbox 용량을 무한정 크게 늘릴 수는 없습니다.

```
설비 100개 × Mailbox 10,000개 = 최대 1,000,000개의 메시지가 메모리에 존재 가능
```

실제로는 처리 속도가 수신 속도보다 빠르기 때문에 항상 가득 차지는 않지만,
메모리 사용량의 최대치를 예측하고 설비 수와 처리 속도에 맞게 조정해야 합니다.

---

## 흐름 요약 표

| 단계 | 구현 | 스레드 | 용량 | 역할 |
|------|------|--------|------|------|
| 1. Topic Queue | `LinkedBlockingQueue` | Consumer 스레드 1개/토픽 | 5,000 | Kafka 메시지 버퍼링 |
| 2. Mailbox Queue | `MailboxScheduler` | Dispatcher 스레드 4개 | 설비당 10,000 | eqpId 기반 순서 보장 |
| 3. Worker Pool | `MailboxExecutionRuntime` | Worker 스레드 8개 | — | 실제 비즈니스 처리 |

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **큐 오버플로우 모니터링** | Topic Queue 또는 Mailbox가 지속적으로 가득 차면 Worker 수나 처리 로직 최적화가 필요합니다 |
| **Consumer 1개 고정** | Kafka Consumer는 스레드당 1개입니다. 처리 속도는 Worker 수를 늘려서 향상합니다 |
| **순서 보장 범위** | 같은 eqpId 내에서만 순서가 보장됩니다. 서로 다른 eqpId는 병렬로 처리됩니다 |
| **ACK 커밋 지연** | 처리 중인 Worker가 느리면 Kafka offset 커밋이 지연됩니다. 재시작 시 이미 처리된 메시지를 다시 받을 수 있습니다 |
