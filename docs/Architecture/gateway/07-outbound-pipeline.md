# 07. 아웃바운드 파이프라인 (Outbound Pipeline)

## 개요

**아웃바운드 파이프라인**은 Kafka에서 수신한 설비 명령을 실제 설비에 TCP로 전송하는 과정입니다.

Kafka 메시지(JSON)를 설비가 이해하는 bytes로 변환하고, 해당 설비의 TCP 채널을 통해 전송합니다.

---

## 전체 처리 흐름

```
tc.eqp.commands (Kafka)
        │
        ↓
GatewayEqpCommandKafkaSubscriber
  - JSON 역직렬화
  - 계약 검증 (commandId, eqpId 필수)
        │
        ↓
GatewayCommandDispatcher
  - 설비 상태 확인 (CONNECTED 여부)
  - 미연결 → DLQ 저장
        │
        ↓ (CONNECTED 상태인 설비만)
EquipmentMailboxRegistry
  - eqpId 기반 메일박스에 적재
        │
        ↓
EqpSequentialProcessor
  - 순서대로 처리
        │
        ↓
OutboundSenderPort
  - 프로토콜별 인코딩
  - 채널로 전송
        │
        ↓
NettyEquipmentChannel.send(bytes)
        │
        ↓
설비 (TCP 수신)
```

---

## GatewayCommandDispatcher — 명령 검증 및 전달

Kafka에서 받은 명령을 메일박스로 전달하기 전에 검증합니다.

```java
@Component
public class GatewayCommandDispatcher {

    public void dispatch(GatewayBusinessCommandMessage message) {
        String eqpId = message.getEqpId();

        // 1. 설비 존재 여부 확인
        EquipmentContext context = contextRegistry.find(eqpId);
        if (context == null) {
            log.warn("등록되지 않은 설비: eqpId={}", eqpId);
            dlqStore.store(eqpId, message, "UNKNOWN_EQUIPMENT");
            return;
        }

        // 2. 설비 활성화 여부 확인
        if (!context.isEnabled()) {
            log.warn("비활성화된 설비로 명령 수신: eqpId={}", eqpId);
            dlqStore.store(eqpId, message, "EQUIPMENT_DISABLED");
            return;
        }

        // 3. 연결 상태 확인
        if (context.getRuntimeState() != RuntimeState.CONNECTED) {
            log.warn("미연결 설비로 명령 수신: eqpId={}, state={}", eqpId, context.getRuntimeState());
            dlqStore.store(eqpId, message, "EQUIPMENT_NOT_CONNECTED");
            return;
        }

        // 4. 메일박스에 적재
        mailboxRegistry.getOrCreate(eqpId).enqueue(message);
    }
}
```

---

## OutboundSenderPort — 프로토콜별 인코딩 및 전송

```java
@Component
public class OutboundSenderAdapter implements OutboundSenderPort {

    @Override
    public void send(String eqpId, EqpOutboundCommand command) {
        // 채널 조회
        EquipmentChannel channel = channelRegistry.get(eqpId);
        if (channel == null || !channel.isConnected()) {
            throw new ChannelNotAvailableException("채널 없음 또는 미연결: " + eqpId);
        }

        Protocol protocol = contextRegistry.getProtocol(eqpId);
        byte[] encodedBytes;

        if (protocol == Protocol.HSMS) {
            // HSMS 인코딩 (SECS-II → HSMS 프레임)
            encodedBytes = hsmsEncoder.encode(command);
        } else {
            // SOCKET 인코딩 (플러그인 인코더 사용)
            SocketPluginRuntime runtime = pluginProvider.getRuntime(eqpId);
            if (runtime == null) {
                // 플러그인 없음 → 명령 직렬화 그대로 전송
                encodedBytes = command.getPayload().getBytes(StandardCharsets.UTF_8);
            } else {
                encodedBytes = runtime.encoder().encode(command);
            }
        }

        // Netty 채널로 전송
        channel.send(encodedBytes);

        log.debug("명령 전송 완료: eqpId={}, bytes={}", eqpId, encodedBytes.length);
    }
}
```

---

## 전송 실패 처리

설비에 전송 중 오류가 발생하면:

```
channel.send(bytes) 호출
        │
        ├─ 채널 활성 상태 → 전송 시도
        │       │
        │       ├─ 성공 → 처리 완료 (Kafka offset 커밋)
        │       │
        │       └─ 실패 (Netty write 오류)
        │               → DLQ 저장 + 오류 로그
        │
        └─ 채널 비활성 (연결 끊김) → DLQ 저장 + 오류 로그
```

```java
// 전송 결과를 ChannelFuture로 확인
ChannelFuture future = channel.writeAndFlush(data);

future.addListener(f -> {
    if (!f.isSuccess()) {
        log.error("명령 전송 실패: eqpId={}", eqpId, f.cause());
        dlqStore.store(eqpId, command, "SEND_FAILED");
    }
});
```

---

## Kafka 메시지 계약 (GatewayBusinessCommandMessage)

`tc.eqp.commands` 토픽에서 수신하는 메시지의 계약입니다.

```json
{
  "commandId": "01JNCMX7YB...",   // 필수: 명령 고유 ID
  "eqpId": "EQP-001",              // 필수: 대상 설비 ID
  "type": "SEND_MESSAGE",          // 필수: 명령 타입
  "payload": {                     // 선택: 명령 페이로드
    "stream": 1,
    "function": 1,
    "data": {...}
  },
  "traceId": "01JNCMX7YB...",     // 선택: 추적 ID
  "timestamp": 1741692001234       // 선택: 발행 시각
}
```

**계약 위반 케이스 (DLQ 처리):**

| 위반 내용 | DLQ reason |
|---------|-----------|
| `commandId` 없음 | `CONTRACT_VIOLATION: commandId is required` |
| `eqpId` 없음 | `CONTRACT_VIOLATION: eqpId is required` |
| `type` 없음 | `CONTRACT_VIOLATION: type is required` |
| JSON 파싱 실패 | `DESERIALIZATION_FAILED` |

---

## 로그 출력 빈도 제어

고빈도 명령 처리 시 로그가 너무 많아질 수 있습니다.
로그 출력 빈도를 설정으로 제어합니다.

```properties
# tc-comm.properties
tc.comm.gateway.command-drop-log-every=100   # DLQ 저장 시 100개마다 1번 로그
# 예: 10,000개의 DLQ가 발생해도 100개마다 1번만 로그 → 100개의 로그
```

중요한 이벤트(큐 오버플로우, 중복 거절 등)는 `log-every=1`로 설정해서 전수 기록합니다.

---

## 아웃바운드 vs 인바운드 비교

| 항목 | 인바운드 (설비 → Gateway) | 아웃바운드 (Gateway → 설비) |
|------|------------------------|--------------------------|
| 출발점 | 설비 TCP 채널 | Kafka tc.eqp.commands |
| 도착점 | Kafka tc.eqp.events | 설비 TCP 채널 |
| 인코딩 | 없음 (raw bytes 수신) | HSMS 또는 SOCKET 인코딩 |
| 디코딩 | HSMS/SOCKET 디코딩 | 없음 (bytes 전송) |
| 순서 보장 | 메일박스로 보장 | 메일박스로 보장 |
| 실패 처리 | Quarantine 또는 DLQ | DLQ |

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **CONNECTED 상태만 전송** | DISCONNECTED 또는 REGISTERED 상태 설비에는 명령을 전송하지 않습니다. 항상 상태 확인 후 전송합니다 |
| **전송 후 응답 대기 없음** | 아웃바운드는 fire-and-forget 방식입니다. 응답은 인바운드 파이프라인을 통해 별도로 처리됩니다 |
| **DLQ 재처리** | DLQ에 저장된 명령은 자동으로 재처리되지 않습니다. 운영자가 설비가 복구된 후 수동으로 재발행해야 합니다 |
| **SOCKET 플러그인** | SOCKET 설비에 플러그인이 없으면 payload를 UTF-8 문자열로 그대로 전송합니다. 설비가 이해할 수 없는 포맷일 수 있습니다 |
