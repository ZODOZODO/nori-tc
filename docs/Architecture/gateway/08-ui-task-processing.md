# 08. UI 작업 처리 (UI Task Processing)

## 개요

`tc-comm-gateway-app`은 UI(웹 화면)에서 요청하는 작업들을 Kafka를 통해 수신하고 처리합니다.

UI 작업(`tc.ui.events.gateway` 토픽)은 설비의 **라이프사이클 제어**(START/END)와
**직접 메시지 전송**(SEND_MESSAGE) 등을 포함합니다.

---

## UI 작업 요청 흐름

```
[웹 화면]
    │ HTTP POST /api/eqp/{eqpId}/start
    ↓
[tc-ui-backend-app]
    │ Kafka 발행: tc.ui.events.gateway
    │ 메시지: {type: "START", eqpId: "EQP-001", traceId: "01JNCMX7YB..."}
    ↓
[tc-comm-gateway-app]
    │ GatewayUiEventKafkaSubscriber: 수신
    │ GatewayUiTaskDispatcher: 타입별 분기
    │
    ├─ START → GatewayUiRuntimeControlService → 상태머신
    ├─ END   → GatewayUiRuntimeControlService → 상태머신
    └─ SEND_MESSAGE → 설비 송신 경로
    ↓
[처리 완료 후]
    │ Kafka 발행: tc.ui.commands
    │ 메시지: {traceId: "01JNCMX7YB...", result: "SUCCESS"}
    ↓
[tc-ui-backend-app]
    │ 수신 후 Redis에 결과 저장
    ↓
[웹 화면]
    GET /api/async/01JNCMX7YB... → 결과 확인
```

---

## UI 작업 타입

| 타입 | 처리 방법 | 설명 |
|------|---------|------|
| `START` | 상태머신 → Netty 연결 | 설비 TCP 연결 시작 |
| `END` | 상태머신 → Netty 해제 | 설비 TCP 연결 종료 |
| `SEND_MESSAGE` | 아웃바운드 파이프라인 | 설비에 직접 메시지 전송 |

---

## GatewayUiEventKafkaSubscriber — UI 이벤트 수신

```java
@Component
public class GatewayUiEventKafkaSubscriber implements SmartLifecycle {

    // tc.ui.events.gateway 토픽의 owned-partitions를 소비
    // (tc.eqp.commands와 같은 partition 기반 라우팅)

    private void processRecord(ConsumerRecord<String, String> record) {
        GatewayBusinessCommandMessage message;

        try {
            message = objectMapper.readValue(record.value(), GatewayBusinessCommandMessage.class);
        } catch (JsonProcessingException e) {
            log.warn("UI 이벤트 역직렬화 실패: {}", e.getMessage());
            dlqStore.store(null, record.value(), "DESERIALIZATION_FAILED");
            ack.acknowledge();
            return;
        }

        // 계약 검증
        if (!message.isValid()) {
            log.warn("UI 이벤트 계약 위반: {}", message);
            dlqStore.store(message.getEqpId(), message, "CONTRACT_VIOLATION");
            ack.acknowledge();
            return;
        }

        // Dispatcher로 전달
        uiTaskDispatcher.dispatch(message);
        ack.acknowledge();
    }
}
```

---

## GatewayUiTaskDispatcher — 작업 타입별 분기

UI 작업을 타입에 따라 적절한 처리기로 분기합니다.

```java
@Component
public class GatewayUiTaskDispatcher implements SmartLifecycle {

    @Override
    public int getPhase() {
        return -100;  // 상태머신과 함께 먼저 시작
    }

    /**
     * UI 작업 타입에 따라 처리기로 분기
     */
    public void dispatch(GatewayBusinessCommandMessage message) {
        String eqpId = message.getEqpId();
        String traceId = message.getTraceId();

        try (MdcScope scope = MdcScope.of("eqpId", eqpId, "traceId", traceId)) {

            // 중복 요청 확인
            if (deduplicationStore.isDuplicate(traceId)) {
                log.warn("중복 UI 작업 거절: traceId={}", traceId);
                return;
            }

            // traceId 등록 (이후 같은 traceId는 중복으로 처리)
            deduplicationStore.register(traceId);

            // 타입별 핸들러 조회 및 실행
            UiTaskProcessor processor = processorRegistry.get(message.getType());
            if (processor == null) {
                log.warn("알 수 없는 UI 작업 타입: type={}", message.getType());
                return;
            }

            processor.process(message);
        }
    }
}
```

---

## GatewayUiRuntimeControlService — START/END 처리

START와 END 요청을 상태머신과 연결합니다.

```java
@Component
public class GatewayUiRuntimeControlService implements UiTaskProcessor {

    /**
     * START 요청 처리
     */
    public void processStart(GatewayBusinessCommandMessage message) {
        String eqpId = message.getEqpId();
        String traceId = message.getTraceId();

        log.info("START 요청 처리 시작: eqpId={}, traceId={}", eqpId, traceId);

        // 이미 CONNECTED 상태면 즉시 성공 응답
        if (contextRegistry.getRuntimeState(eqpId) == RuntimeState.CONNECTED) {
            log.info("이미 연결된 설비: eqpId={}", eqpId);
            deferredReplyService.replySuccess(traceId, "ALREADY_CONNECTED");
            return;
        }

        // 상태머신에 START 요청 (비동기)
        // 결과는 나중에 onChannelConnected 이벤트로 받음
        stateMachine.requestStart(eqpId, traceId);

        // timeout 타이머 등록 (30초 후 자동 TIMEOUT 응답)
        timeoutScheduler.schedule(
            () -> handleStartTimeout(eqpId, traceId),
            startTimeoutSeconds,
            TimeUnit.SECONDS
        );
    }

    /**
     * END 요청 처리
     */
    public void processEnd(GatewayBusinessCommandMessage message) {
        String eqpId = message.getEqpId();
        String traceId = message.getTraceId();

        log.info("END 요청 처리 시작: eqpId={}", eqpId);

        stateMachine.requestEnd(eqpId);

        // END는 동기적으로 처리됨 (TCP 연결 종료)
        // 즉시 성공 응답
        deferredReplyService.replySuccess(traceId, "DISCONNECTED");
    }
}
```

---

## UI 작업 Timeout 처리

START 요청은 비동기이므로 일정 시간 내에 완료되지 않으면 Timeout 응답을 반환합니다.

```
t=0s:    START 요청 도착
         → 상태머신에 START 요청
         → 30초 타임아웃 타이머 등록

t=15s:   TCP 연결 성공
         → onChannelConnected 이벤트
         → 타임아웃 타이머 취소
         → SUCCESS 응답 발행 (tc.ui.commands)

또는

t=30s:   타임아웃 타이머 만료
         → TCP 연결 실패로 판단
         → TIMEOUT 응답 발행 (tc.ui.commands)
```

**Timeout 설정:**
```properties
# tc-comm.properties
tc.comm.gateway.ui-task.start-timeout-seconds=30
tc.comm.gateway.ui-task.end-timeout-seconds=30
tc.comm.gateway.ui-task.retry-max-attempts=1
tc.comm.gateway.ui-task.retry-backoff-ms=200
```

---

## UiTaskProcessorRegistry — 핸들러 레지스트리

UI 작업 타입과 처리기를 연결하는 레지스트리입니다.

```java
@Component
public class GatewayUiTaskProcessorRegistry {

    private final Map<String, UiTaskProcessor> processors;

    public GatewayUiTaskProcessorRegistry(
            GatewayUiRuntimeControlService controlService,
            GatewayOutboundCommandService outboundService) {

        this.processors = Map.of(
            "START",        controlService::processStart,
            "END",          controlService::processEnd,
            "SEND_MESSAGE", outboundService::processSendMessage
        );
    }

    public UiTaskProcessor get(String taskType) {
        return processors.get(taskType);
    }
}
```

---

## 메시지 구조 (tc.ui.events.gateway)

UI Backend가 발행하는 메시지 구조:

```json
{
  "commandId": "01JNCMX7YB...",    // 필수
  "eqpId": "EQP-001",              // 필수
  "type": "START",                 // 필수: START, END, SEND_MESSAGE
  "traceId": "01JNCMX7YB...",     // 필수 (비동기 응답 매핑용)
  "payload": null,                 // START/END는 보통 null
  "timestamp": 1741692001234
}
```

---

## 처리 결과 응답 (tc.ui.commands)

처리 완료 후 UI Backend로 응답을 발행합니다:

```json
{
  "traceId": "01JNCMX7YB...",    // 요청과 동일한 traceId
  "eqpId": "EQP-001",
  "taskType": "START",
  "result": "SUCCESS",           // SUCCESS, TIMEOUT, FAILED
  "message": "CONNECTED",        // 결과 상세 메시지
  "timestamp": 1741692031234
}
```

자세한 내용은 [09-deferred-lifecycle-reply.md](09-deferred-lifecycle-reply.md)를 참고하세요.

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **Phase -100** | `GatewayUiTaskDispatcher`는 Phase -100에서 시작합니다. Kafka Consumer보다 먼저 준비되어야 합니다 |
| **중복 요청** | 같은 `traceId`의 요청은 중복으로 처리되어 거절됩니다. UI에서 재시도 시 새 traceId를 사용해야 합니다 |
| **Timeout 후 연결 성공** | Timeout 응답이 발행된 후에 TCP 연결이 성공해도 추가 응답을 발행하지 않습니다 (traceId 기반 deduplication) |
| **SEND_MESSAGE 순서** | SEND_MESSAGE는 메일박스를 거쳐 순서가 보장됩니다. 같은 설비에 여러 SEND_MESSAGE가 도착하면 순서대로 처리됩니다 |
