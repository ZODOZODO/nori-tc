# 11. UI Task 중복 방지 (UI Task Deduplication)

## 개요

UI에서 설비 START/END 같은 작업을 요청할 때, 같은 요청이 **두 번 처리되면 안 됩니다**.

예를 들어, 네트워크 지연으로 인해 UI가 같은 요청을 두 번 발행했거나,
Kafka Consumer가 재시작되어 같은 메시지를 다시 처리하는 경우,
중복으로 처리되면 설비 상태가 의도치 않게 변경될 수 있습니다.

`UiTraceIdDeduplicationStore`는 **Redis를 활용해 같은 traceId의 요청이 두 번 처리되는 것을 방지**합니다.

---

## 왜 중복 방지가 필요한가?

### 문제 시나리오 1: 네트워크 재전송

```
UI Backend: Kafka 발행 시도
  → 타임아웃으로 발행 실패 판단
  → 재시도 (같은 traceId로 다시 발행)
  → 실제로는 첫 번째 발행도 Kafka에 저장됨

결과: Gateway가 같은 START 요청을 두 번 받음
     → 설비에 두 번 연결 시도
     → 이미 연결된 상태에서 또 연결 시도 → 오류 발생 가능
```

### 문제 시나리오 2: Kafka Consumer 재시작

```
Gateway Consumer가 메시지를 처리 중 재시작:
  → Kafka offset이 커밋되지 않은 상태
  → 재시작 후 같은 메시지를 다시 수신

결과: 같은 START 요청이 두 번 처리됨
```

### 해결: traceId 기반 중복 방지

```
traceId=01JNCMX7YB...인 START 요청 최초 수신:
  Redis SET NX (Not Exists):
    Key: tc:comm:gateway:ui:dedup:01JNCMX7YB...
    Value: "1"
    TTL: 600초
  → SET 성공 (키가 없었음) → 새 요청으로 처리

같은 traceId로 재요청 또는 중복 메시지:
  Redis SET NX:
    Key: tc:comm:gateway:ui:dedup:01JNCMX7YB...
  → SET 실패 (키가 이미 있음) → 중복으로 거절
```

---

## 동작 원리

```
┌──────────────────────────────────────────────────────────────┐
│                      Redis                                   │
│                                                              │
│  tc:comm:gateway:ui:dedup:01JNCMX7YB... = "1" (TTL: 600s) │
│  tc:comm:gateway:ui:dedup:01JNCMX8ZC... = "1" (TTL: 580s) │
│  tc:comm:gateway:ui:dedup:01JNCMX9AD... = "1" (TTL: 312s) │
│                                                              │
└──────────────────────────────────────────────────────────────┘

처리 흐름:

새 요청 traceId=01JNCMXABC...
        ↓
SET NX tc:comm:gateway:ui:dedup:01JNCMXABC... "1" EX 600
        │
        ├─ 반환값: 1 (성공, 키 없었음)
        │       → 처리 진행
        │
        └─ 반환값: 0 (실패, 키 이미 있음)
                → 중복 요청으로 거절
                → 로그 출력
                → 처리 안 함
```

---

## UiTraceIdDeduplicationStore

```java
@Component
public class UiTraceIdDeduplicationStore {

    private final StringRedisTemplate redisTemplate;

    // deduplication TTL: 600초 (설정)
    // 최대 저장 수: 100,000개 (설정)

    /**
     * traceId가 중복인지 확인하고, 새 요청이면 등록
     *
     * @return true  → 중복 (이미 처리했거나 처리 중)
     *         false → 새 요청 (처리 진행)
     */
    public boolean isDuplicateAndRegister(String traceId) {
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(
            buildKey(traceId),  // tc:comm:gateway:ui:dedup:{traceId}
            "1",
            Duration.ofSeconds(deduplicationTtlSeconds)  // 600초
        );

        // setIfAbsent: 키가 없으면 SET (true 반환), 있으면 무시 (false 반환)
        return Boolean.FALSE.equals(isNew);  // false=키 있었음=중복
    }

    private String buildKey(String traceId) {
        return "tc:comm:gateway:ui:dedup:" + traceId;
    }
}
```

---

## 설정

```properties
# tc-comm.properties
tc.comm.gateway.ui-task.dedup-ttl-seconds=600        # 중복 체크 유지 시간 (10분)
tc.comm.gateway.ui-task.dedup-max-size=100000         # 최대 저장 traceId 수
tc.comm.gateway.duplicate-reject-log-every=1          # 중복 거절 로그: 매번
```

**TTL을 600초(10분)로 설정한 이유:**
- UI의 비동기 polling TTL(`tc.ui.backend.async.result-ttl-seconds=600`)과 동일합니다
- 같은 요청에 대한 재시도는 10분 이내에 발생한다고 가정합니다
- 10분이 지나면 완전히 새 요청으로 처리합니다

---

## GatewayUiTaskDispatcher에서의 사용

```java
@Component
public class GatewayUiTaskDispatcher {

    public void dispatch(GatewayBusinessCommandMessage message) {
        String traceId = message.getTraceId();

        // 중복 확인 및 등록 (원자적 연산)
        if (deduplicationStore.isDuplicateAndRegister(traceId)) {
            log.warn("중복 UI 작업 거절: traceId={}, eqpId={}",
                     traceId, message.getEqpId());
            // 아무것도 처리하지 않음 (응답도 보내지 않음)
            // UI Backend는 기존 pending 결과를 polling하면 됨
            return;
        }

        // 새 요청 → 처리 진행
        UiTaskProcessor processor = processorRegistry.get(message.getType());
        processor.process(message);
    }
}
```

---

## traceId가 없는 경우 처리

메시지에 traceId가 없으면:

```java
if (message.getTraceId() == null || message.getTraceId().isBlank()) {
    // traceId 없으면 중복 체크 불가 → 계약 위반으로 거절
    log.warn("traceId 없는 UI 작업 거절: eqpId={}", message.getEqpId());
    dlqStore.store(message.getEqpId(), message, "MISSING_TRACE_ID");
    return;
}
```

모든 UI 작업에는 반드시 고유한 traceId가 있어야 합니다.

---

## 중복 방지 범위

중복 방지는 **같은 Gateway 인스턴스 내에서만** 적용됩니다.

```
Gateway-1 (owned: partition 0,1)
  Redis: tc:comm:gateway:ui:dedup:01JNCMX7YB... = "1"
  → partition 0,1로 오는 요청에 대한 중복 방지

Gateway-2 (owned: partition 2,3)
  Redis: 별도 (Gateway-1과 공유 안 할 수도 있음)
  → partition 2,3으로 오는 요청에 대한 중복 방지
```

같은 traceId의 메시지가 partition 0에도, partition 2에도 발행된다면:
- Gateway-1과 Gateway-2가 각각 처리할 수 있습니다
- 이런 상황은 정상적으로 발생하지 않아야 합니다 (route_partition이 고정되어 있으므로)

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **Redis 연결 필수** | Redis가 다운되면 중복 체크가 불가합니다. 이 경우 중복 처리가 발생할 수 있습니다. Redis 고가용성을 확보하세요 |
| **TTL 조정** | `dedup-ttl-seconds`를 너무 길게 설정하면 같은 작업을 한참 뒤에 재시도할 때도 중복으로 처리됩니다 |
| **traceId 재사용 금지** | UI Backend는 모든 요청에 새로운 고유 traceId를 생성해야 합니다. 재사용하면 새 요청도 중복으로 거절됩니다 |
| **max-size 초과** | `dedup-max-size=100000` 설정은 참고용이며, 실제 Redis 메모리 관리는 TTL로 합니다. 100,000개 × 평균 key 크기를 고려해서 Redis 메모리를 설정하세요 |
