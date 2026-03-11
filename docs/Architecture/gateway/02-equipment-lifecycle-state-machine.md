# 02. 장비 라이프사이클 상태머신 (Equipment Lifecycle State Machine)

## 개요

`tc-comm-gateway-app`은 각 설비(Equipment)의 **라이프사이클 상태**를 상태머신으로 관리합니다.

상태머신은 설비가 어떤 상태에 있는지를 추적하고, 상태를 변경하는 이벤트를 처리합니다.
UI에서 START/END 요청이 오거나, Netty 채널이 연결/해제될 때 상태가 전이됩니다.

---

## 설비 상태 정의

```
┌─────────────────────────────────────────────────────────────────┐
│                       설비 상태 목록                            │
│                                                                 │
│  REGISTERED                                                     │
│    ─ 설비가 시스템에 등록은 되었지만 아직 아무것도 안 한 상태  │
│    ─ disabled 설비 또는 최초 등록 직후                          │
│                                                                 │
│  DISCONNECTED                                                   │
│    ─ START 요청을 받아 연결 시도 중이지만 아직 연결 안 된 상태 │
│    ─ enabled 설비의 기동 직후 초기 상태                         │
│                                                                 │
│  CONNECTED                                                      │
│    ─ Netty TCP 연결이 수립된 상태                               │
│    ─ 설비와 메시지를 주고받을 수 있음                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 상태 전이 다이어그램

```
                    ┌───────────────┐
                    │  REGISTERED   │
                    │  (등록됨)     │
                    └───────┬───────┘
                            │ START 요청
                            │ (UI → Gateway)
                            ↓
                    ┌───────────────┐
          ┌─────── │ DISCONNECTED  │ ◄────────────────────────────┐
          │        │  (연결 대기)  │                              │
          │        └───────┬───────┘                              │
          │                │ CHANNEL_CONNECTED 이벤트             │
          │                │ (Netty TCP 연결 성공)                │
          │                ↓                                      │
          │        ┌───────────────┐                              │
          │        │  CONNECTED    │                              │
          │        │  (연결됨)     │ ──── CHANNEL_DISCONNECTED ──→ │
          │        └───────┬───────┘      (TCP 연결 끊김)         │
          │                │ END 요청                             │
          │                │ (UI → Gateway)                       │
          │                ↓                                      │
          │        ┌───────────────┐                              │
          └──────→ │  REGISTERED   │                              │
          END 요청  └───────────────┘
          (DISCONNECTED 상태에서)
```

**상태 전이 트리거:**

| 이벤트 | 출발 상태 | 도착 상태 |
|--------|---------|---------|
| START 요청 (UI) | REGISTERED | DISCONNECTED |
| CHANNEL_CONNECTED (Netty) | DISCONNECTED | CONNECTED |
| CHANNEL_DISCONNECTED (Netty) | CONNECTED | DISCONNECTED |
| END 요청 (UI) | CONNECTED | REGISTERED |
| END 요청 (UI) | DISCONNECTED | REGISTERED |
| 연결 실패 최대 횟수 초과 | DISCONNECTED | REGISTERED |

---

## DesiredState와 RuntimeState

상태머신은 두 가지 상태를 조합해서 관리합니다.

| 속성 | 의미 | 가능한 값 |
|------|------|---------|
| `DesiredState` | **원하는** 최종 상태 (사용자의 의도) | `STARTED`, `ENDED` |
| `RuntimeState` | **현재** 실제 상태 | `REGISTERED`, `DISCONNECTED`, `CONNECTED` |

```
예시:
  enabled 설비 기동 직후:
    DesiredState = STARTED    (연결되기를 원함)
    RuntimeState = DISCONNECTED (아직 연결 안 됨)

  TCP 연결 성공 후:
    DesiredState = STARTED    (연결 원함)
    RuntimeState = CONNECTED  (실제로 연결됨)

  UI에서 END 요청:
    DesiredState = ENDED      (연결 종료를 원함)
    RuntimeState = REGISTERED (연결 해제됨)
```

**DesiredState가 별도로 존재하는 이유:**
TCP 연결이 끊겼을 때, DesiredState가 STARTED면 자동 재연결을 시도합니다.
DesiredState가 ENDED면 재연결을 시도하지 않습니다.

---

## 핵심 클래스

### EquipmentLifecycleStateMachine

```java
@Component
public class EquipmentLifecycleStateMachine implements SmartLifecycle {

    @Override
    public int getPhase() {
        return -100;  // Netty, Kafka보다 먼저 시작
    }

    /**
     * UI에서 설비 START 요청 처리
     * - DesiredState를 STARTED로 변경
     * - RuntimeState를 DISCONNECTED로 변경
     * - Netty에 연결 시도 요청
     */
    public void requestStart(String eqpId) {
        EquipmentContext context = contextRegistry.getOrThrow(eqpId);

        // 이미 CONNECTED 상태면 무시
        if (context.runtimeState() == RuntimeState.CONNECTED) {
            log.warn("이미 연결된 설비입니다: eqpId={}", eqpId);
            return;
        }

        context.setDesiredState(DesiredState.STARTED);
        context.setRuntimeState(RuntimeState.DISCONNECTED);

        // Netty 연결 시도 요청
        eqpBindingService.startBinding(context);
    }

    /**
     * Netty에서 TCP 연결 성공 이벤트 처리
     * - RuntimeState를 CONNECTED로 변경
     * - pending START 요청이 있으면 완료로 처리
     */
    public void onChannelConnected(String eqpId, EquipmentChannel channel) {
        EquipmentContext context = contextRegistry.getOrThrow(eqpId);

        context.setRuntimeState(RuntimeState.CONNECTED);
        channelRegistry.register(eqpId, channel);

        // 비동기 START 완료 통지 (UI 응답)
        pendingOutcomes.complete(eqpId, LifecycleOutcome.CONNECTED);
    }

    /**
     * UI에서 설비 END 요청 처리
     * - DesiredState를 ENDED로 변경
     * - TCP 연결 종료
     */
    public void requestEnd(String eqpId) {
        EquipmentContext context = contextRegistry.getOrThrow(eqpId);
        context.setDesiredState(DesiredState.ENDED);

        // Netty 연결 종료
        eqpBindingService.stopBinding(eqpId);

        context.setRuntimeState(RuntimeState.REGISTERED);
    }
}
```

---

## START 요청의 비동기 처리

START 요청은 즉시 완료되지 않고, TCP 연결이 성공할 때까지 기다립니다.
이 비동기 처리를 위해 **Pending Outcome** 메커니즘을 사용합니다.

```
UI: START 요청 (traceId=01JNCMX7YB)
        ↓
Gateway: requestStart(eqpId)
        ↓
pendingOutcomes.register(eqpId, traceId)  ← "START 기다리는 중" 등록
        ↓
Netty 연결 시도 (비동기)
        ↓
        ├─ 연결 성공 (30초 이내):
        │   onChannelConnected(eqpId)
        │   pendingOutcomes.complete(eqpId, CONNECTED)
        │   GatewayUiDeferredLifecycleReplyService → tc.ui.commands 응답 발행
        │
        └─ 타임아웃 (30초 초과):
            pendingOutcomes.timeout(eqpId)
            GatewayUiDeferredLifecycleReplyService → tc.ui.commands TIMEOUT 응답 발행
```

**Timeout 설정:**
```properties
# tc-comm.properties
tc.comm.gateway.ui-task.start-timeout-seconds=30   # START 완료 대기 시간
tc.comm.gateway.ui-task.end-timeout-seconds=30     # END 완료 대기 시간
```

---

## 자동 재연결 로직

TCP 연결이 끊어졌을 때, DesiredState가 STARTED면 자동으로 재연결을 시도합니다.

```
TCP 연결 끊김 (CHANNEL_DISCONNECTED)
        ↓
onChannelDisconnected(eqpId)
        ↓
RuntimeState = DISCONNECTED
        ↓
DesiredState == STARTED? ─── Yes ──→ 재연결 스케줄러 등록
        │                              (3초 후 재연결 시도)
       No                              최대 실패 횟수 초과?
        ↓                              ─── Yes ──→ DesiredState = ENDED (포기)
    재연결 안 함                        ─── No  ──→ 다시 재연결 시도
```

**재연결 설정:**
```properties
# tc-comm.properties
tc.comm.gateway.netty.reconnect-delay-seconds=3     # 재연결 시도 간격
tc.comm.gateway.netty.max-connect-failures=3        # 최대 연속 실패 횟수
```

---

## 상태에 따른 명령 처리 정책

설비가 특정 상태일 때 명령을 받으면 어떻게 처리하는지:

| RuntimeState | 명령 수신 시 |
|-------------|------------|
| CONNECTED | 정상 처리 (설비로 전송) |
| DISCONNECTED | DLQ 저장 (설비에 전달 불가) |
| REGISTERED | DLQ 저장 (설비에 전달 불가) |

---

## 로그 예시

```
# 기동 시
INFO  [EQP-001] EquipmentLifecycleStateMachine - 설비 등록: desiredState=STARTED, runtimeState=DISCONNECTED

# START 요청
INFO  [EQP-001][01JNCMX7YB] EquipmentLifecycleStateMachine - START 요청 수신
INFO  [EQP-001][01JNCMX7YB] EqpBindingService - ACTIVE 연결 시도: host=192.168.1.100, port=5000

# 연결 성공
INFO  [EQP-001][01JNCMX7YB] EquipmentLifecycleStateMachine - CHANNEL_CONNECTED: runtimeState=CONNECTED
INFO  [EQP-001][01JNCMX7YB] GatewayUiDeferredLifecycleReplyService - START 성공 응답 발행

# 연결 끊김
WARN  [EQP-001] EquipmentLifecycleStateMachine - CHANNEL_DISCONNECTED: 재연결 스케줄됨 (3초 후)

# 타임아웃
WARN  [EQP-001][01JNCMX7YB] EquipmentLifecycleStateMachine - START 타임아웃 (30초 초과)
INFO  [EQP-001][01JNCMX7YB] GatewayUiDeferredLifecycleReplyService - TIMEOUT 응답 발행
```

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **Phase -100 유지** | 상태머신은 반드시 Phase -100에서 시작해야 합니다. Netty나 Kafka보다 늦게 시작하면 채널 이벤트를 처리하지 못합니다 |
| **Timeout 조정** | START timeout(30초)은 설비와 네트워크 환경에 맞게 조정하세요. 너무 짧으면 정상 연결도 실패 응답합니다 |
| **재연결 횟수** | `max-connect-failures`를 너무 크게 설정하면 장기간 비정상 연결 시도가 계속됩니다. 적절히 제한하세요 |
| **비활성 설비** | enabled=false 설비는 START 요청 자체가 거절됩니다 |
