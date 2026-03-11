# 09. 비동기 라이프사이클 응답 처리 (Deferred Lifecycle Reply)

## 개요

설비 START/END 작업은 **즉시 완료되지 않습니다**.

START를 요청하면 Netty가 TCP 연결을 시도하고, 연결이 성공(또는 실패/타임아웃)한 후에야
결과를 알 수 있습니다. 이 비동기적인 완료 결과를 UI Backend로 전달하는 것이
**GatewayUiDeferredLifecycleReplyService**의 역할입니다.

---

## 왜 "Deferred(지연된)" 응답인가?

```
동기 방식 (불가능):
  UI → START 요청 (Kafka)
  Gateway: 즉시 TCP 연결 시도
  Gateway: 연결 성공 후 응답 (얼마나 걸릴지 모름)
  → HTTP에서는 가능하지만, 비동기 Kafka에서는 응답 채널이 없음

비동기 방식 (현재 구현):
  UI → START 요청 (Kafka) → traceId 포함
  Gateway: TCP 연결 시도 시작 → 즉시 반환
  Gateway: 나중에 연결 성공 → tc.ui.commands에 응답 발행 (traceId 포함)
  UI Backend: tc.ui.commands 수신 → traceId로 매핑 → Redis 업데이트
  웹 화면: Redis polling으로 결과 확인
```

---

## 전체 흐름

```
[웹 화면]
    │ POST /api/eqp/EQP-001/start
    ↓
[UI Backend]
    │ Redis: tc:ui:backend:async:{traceId} = "PENDING"
    │ Kafka 발행: tc.ui.events.gateway
    │   {type: "START", traceId: "01JNCMX7YB...", eqpId: "EQP-001"}
    │ HTTP 202 Accepted + traceId 반환
    ↓
[웹 화면]
    │ GET /api/async/01JNCMX7YB... (폴링)
    │ 응답: {status: "PENDING"} (아직 처리 중)

... Gateway에서 처리 중 ...

[Gateway]
    │ GatewayUiRuntimeControlService.processStart()
    │ stateMachine.requestStart("EQP-001", "01JNCMX7YB...")
    │ Netty TCP 연결 시도 (비동기)
    │
    ├─ [성공 케이스] TCP 연결 완료 (t+5초)
    │       stateMachine.onChannelConnected("EQP-001")
    │       pendingOutcomes.complete("EQP-001", CONNECTED)
    │       GatewayUiDeferredLifecycleReplyService.replySuccess(traceId)
    │       Kafka 발행: tc.ui.commands
    │           {traceId: "01JNCMX7YB...", result: "SUCCESS"}
    │
    └─ [타임아웃] 30초 경과
            timeoutScheduler가 만료 감지
            GatewayUiDeferredLifecycleReplyService.replyTimeout(traceId)
            Kafka 발행: tc.ui.commands
                {traceId: "01JNCMX7YB...", result: "TIMEOUT"}

[UI Backend]
    │ tc.ui.commands 수신
    │ Redis 업데이트:
    │   tc:ui:backend:async:{traceId} = "SUCCESS" (또는 "TIMEOUT")
    ↓
[웹 화면]
    │ GET /api/async/01JNCMX7YB... (폴링)
    │ 응답: {status: "SUCCESS"} → 완료 화면으로 이동
```

---

## GatewayUiDeferredLifecycleReplyService

```java
@Component
public class GatewayUiDeferredLifecycleReplyService {

    /**
     * START/END 성공 응답 발행
     *
     * 상태머신에서 CHANNEL_CONNECTED 또는 END 완료 이벤트를 받은 후 호출됨
     */
    public void replySuccess(String traceId, String detail) {
        GatewayUiTaskReplyMessage reply = GatewayUiTaskReplyMessage.builder()
            .traceId(traceId)
            .result(TaskResult.SUCCESS)
            .message(detail)
            .timestamp(clock.nowEpochMs())
            .build();

        uiCommandPublisher.publish(reply);
        log.info("라이프사이클 성공 응답 발행: traceId={}, detail={}", traceId, detail);
    }

    /**
     * START/END 타임아웃 응답 발행
     *
     * 30초 타임아웃 타이머가 만료된 후 호출됨
     */
    public void replyTimeout(String traceId) {
        GatewayUiTaskReplyMessage reply = GatewayUiTaskReplyMessage.builder()
            .traceId(traceId)
            .result(TaskResult.TIMEOUT)
            .message("작업이 제한 시간 내에 완료되지 않았습니다")
            .timestamp(clock.nowEpochMs())
            .build();

        uiCommandPublisher.publish(reply);
        log.warn("라이프사이클 타임아웃 응답 발행: traceId={}", traceId);
    }

    /**
     * START/END 실패 응답 발행
     *
     * 연결 실패가 확정된 후 호출됨 (max-connect-failures 초과 등)
     */
    public void replyFailed(String traceId, String reason) {
        GatewayUiTaskReplyMessage reply = GatewayUiTaskReplyMessage.builder()
            .traceId(traceId)
            .result(TaskResult.FAILED)
            .message(reason)
            .timestamp(clock.nowEpochMs())
            .build();

        uiCommandPublisher.publish(reply);
        log.warn("라이프사이클 실패 응답 발행: traceId={}, reason={}", traceId, reason);
    }
}
```

---

## Pending Outcome 메커니즘

START 요청이 들어오면 **Pending Outcome**을 등록하고, 완료/실패/타임아웃 시 처리합니다.

```java
// 내부적으로 ConcurrentHashMap으로 관리
private final Map<String, PendingOutcome> pendingByEqpId = new ConcurrentHashMap<>();

public void registerPendingStart(String eqpId, String traceId) {
    PendingOutcome pending = PendingOutcome.forStart(eqpId, traceId, clock.now());
    pendingByEqpId.put(eqpId, pending);
}

// CHANNEL_CONNECTED 이벤트 수신 시
public void onChannelConnected(String eqpId) {
    PendingOutcome pending = pendingByEqpId.remove(eqpId);
    if (pending != null && pending.isStartPending()) {
        // 아직 타임아웃 안 됐으면 성공 응답
        deferredReplyService.replySuccess(pending.traceId(), "CONNECTED");
    }
}
```

---

## 응답 메시지 구조 (tc.ui.commands)

```json
{
  "traceId": "01JNCMX7YB...",    // 원래 요청의 traceId
  "eqpId": "EQP-001",
  "taskType": "START",
  "result": "SUCCESS",           // SUCCESS | TIMEOUT | FAILED
  "message": "CONNECTED",        // 결과 상세
  "gatewayId": "gateway-1",      // 응답을 발행한 Gateway 인스턴스 ID
  "timestamp": 1741692031234
}
```

**result 값 목록:**

| result | 의미 | 조건 |
|--------|------|------|
| `SUCCESS` | 성공 | TCP 연결 성공 또는 연결 종료 완료 |
| `TIMEOUT` | 타임아웃 | 30초 내 완료 안 됨 |
| `FAILED` | 실패 | 최대 재연결 횟수 초과 등 |

---

## 타임아웃 후 연결 성공 처리

타임아웃 응답이 발행된 후, 우연히 TCP 연결이 성공하는 경우:

```
t=0s:    START 요청 → Pending 등록
t=30s:   타임아웃 → TIMEOUT 응답 발행 → Pending 제거
t=31s:   TCP 연결 성공 → onChannelConnected 호출
             pendingByEqpId.get(eqpId) == null  ← Pending이 이미 제거됨
             → 추가 응답 없음 (중복 응답 방지)
             → RuntimeState = CONNECTED (설비는 실제로 연결됨)
             → UI는 이 연결된 설비의 상태를 다음 조회 시 알 수 있음
```

이 경우 설비는 연결되어 있지만 UI는 TIMEOUT으로 알고 있어 불일치가 발생합니다.
이런 경우는 일반적으로 드물며, UI에서 다시 상태 조회하면 CONNECTED 상태를 확인할 수 있습니다.

---

## 운영 포인트

| 항목 | 내용 |
|------|------|
| **Timeout 값 조정** | 네트워크 환경에 따라 `start-timeout-seconds`를 조정하세요. 지연이 많은 환경에서는 늘려야 합니다 |
| **TIMEOUT 빈발** | TIMEOUT 응답이 많으면 네트워크 문제 또는 설비 응답 지연을 의심하세요 |
| **Kafka 발행 실패** | `tc.ui.commands` 발행 실패 시 UI에서 결과를 영원히 알 수 없습니다. Kafka 상태를 모니터링하세요 |
| **Pending 메모리** | START 요청이 매우 많으면 `pendingByEqpId` 맵이 커질 수 있습니다. 타임아웃 후 반드시 정리됩니다 |
