# 04. 워크플로우 액션 타입 (Workflow Action Types)

## 개요

워크플로우 매칭이 완료된 후, 각 `WorkflowRuntimeEntry`의 `actionName`에 맞는 **액션 실행기(ActionExecutor)**를 찾아 실행한다.
액션은 메시지 타입(`MessageType`)과 액션 이름(`action_name`)의 조합 키로 식별되며,
`@TcAction` 어노테이션이 붙은 메서드를 통해 등록된다.

### 액션 메시지 타입 (MessageType)

| MessageType | 해당 프로토콜 | 기반 추상 클래스 |
|-------------|--------------|----------------|
| `SECS` | HSMS 프로토콜 장비 | `AbstractSecsActionExecutor` |
| `SOCKET` | TCP Socket 프로토콜 장비 | `AbstractSocketActionExecutor` |
| `MES` | MES 시스템 이벤트 | `AbstractMesActionExecutor` |
| `UI` | UI 백엔드 태스크 | (별도 처리 경로) |

> `MessageType`은 인바운드 레코드의 출처(topic)와 모델 런타임의 `protocolType`을 조합해 런타임에 결정된다.

---

## 왜 @TcAction 어노테이션 방식인가?

| 대안 | 문제점 |
|------|--------|
| if/switch로 직접 분기 | 액션 추가/제거 시 기존 코드 수정 필요 |
| Spring Bean 이름 매핑 | 네이밍 충돌, 스캔 순서 의존성 |
| **@TcAction 어노테이션** | 클래스별로 액션 메서드 선언 → 레지스트리가 자동 수집 → OCP 준수 |

---

## 구조 다이어그램

```
AbstractSecsActionExecutor
        ↑ (상속)
MySomeSecsActionExecutor
    @TcAction("ALARM_ON")
    void handleAlarmOn(BusinessWorkflowActionContext ctx) { ... }

    @TcAction("STATUS_CHANGED")
    void handleStatusChanged(BusinessWorkflowActionContext ctx) { ... }


BusinessWorkflowActionRegistryBuilder
        │
        └── registerExecutor(mySomeSecsActionExecutor, MessageType.SECS)
                │
                └── 리플렉션으로 @TcAction 메서드 스캔
                        → BusinessWorkflowActionKey(SECS, "ALARM_ON") → invoker
                        → BusinessWorkflowActionKey(SECS, "STATUS_CHANGED") → invoker
                        → BusinessWorkflowActionRegistry (불변 맵)


BusinessWorkflowDispatchingActionExecutor
        │
        ├── 매칭된 WorkflowRuntimeEntry.actionName = "ALARM_ON"
        ├── ActionKey = (SECS, "ALARM_ON")
        │
        ├── [1] pluginRegistry.findInvoker(actionKey) → Plugin 우선
        ├── [2] 없으면 coreRegistry.findInvoker(actionKey) → Core Fallback
        │
        └── selectedInvoker.invoke(actionContext) → 메서드 실행
```

---

## 핵심 클래스/인터페이스

| 클래스/인터페이스 | 역할 |
|---|---|
| `@TcAction` | 액션 메서드 선언 어노테이션 (`value = action_name`) |
| `AbstractSecsActionExecutor` | SECS 액션 기반 클래스 (타입 마커 역할) |
| `AbstractSocketActionExecutor` | SOCKET 액션 기반 클래스 |
| `AbstractMesActionExecutor` | MES 액션 기반 클래스 |
| `BusinessWorkflowActionRegistryBuilder` | Executor 스캔 → Registry 빌드 |
| `BusinessWorkflowActionKey` | `(MessageType, actionName)` 복합 키 |
| `BusinessWorkflowActionMethodInvoker` | 리플렉션 기반 메서드 래퍼 |
| `BusinessWorkflowActionRegistry` | Key → Invoker 불변 맵 |
| `BusinessWorkflowCoreActionRegistry` | 내장 Core Registry 홀더 |
| `BusinessWorkflowPluginRuntimeProvider` | eqpId 기준 Plugin Registry 제공 |
| `BusinessWorkflowDispatchingActionExecutor` | Plugin 우선 / Core Fallback 디스패치 |
| `BusinessWorkflowActionContext` | 액션 메서드에 전달되는 실행 컨텍스트 |

---

## @TcAction 메서드 규칙

| 항목 | 규칙 |
|------|------|
| 반환 타입 | `void` 만 허용 |
| 파라미터 | 없거나 `BusinessWorkflowActionContext` 1개만 허용 |
| static | 금지 |
| 접근 제한자 | 제한 없음 (리플렉션으로 접근) |
| 중복 키 | 같은 `(MessageType, actionName)` 중복 시 빌드 단계에서 예외 발생 |

```java
// 올바른 선언 예시
public class FabSecsActionExecutor extends AbstractSecsActionExecutor {

    // 파라미터 없는 액션 — 컨텍스트 불필요한 단순 처리
    @TcAction("HEARTBEAT")
    void onHeartbeat() {
        // ...
    }

    // 컨텍스트 포함 액션 — payload, modelRuntime, filterContext 활용
    @TcAction("ALARM_ON")
    void onAlarmOn(BusinessWorkflowActionContext ctx) {
        String alarmCode = (String) ctx.filterContext().messageVariables().get("alarmCode");
        // ...
    }
}
```

---

## 액션 실행 흐름 (디스패치)

```
매칭된 WorkflowRuntimeEntry (1개 이상)
        │
        ▼ (순서대로 순회)
for each entry:
    ActionMessageType = from(record, modelRuntime)
        ├── record.topic == tc.eqp.events → protocolType 기준 SECS/SOCKET
        └── record.topic == tc.mes.events → MES

    ActionKey = (ActionMessageType, entry.actionName())
        │
        ▼
    ActionResolutionPolicy.resolve(eqpId, workflowKey, actionKey, pluginRegistry, coreRegistry)
        │
        ├── pluginRegistry에 ActionKey 존재 + coreRegistry에도 존재
        │       → Plugin이 Core를 대체 (PLUGIN_OVERRIDE, INFO 로그)
        ├── pluginRegistry에만 존재
        │       → Plugin 선택 (선택 로그)
        ├── coreRegistry에만 존재
        │       → Core 선택 (선택 로그)
        ├── pluginRegistry에 없고 coreRegistry에 있음 (fallback)
        │       → Core Fallback (CORE_FALLBACK, INFO 로그)
        └── 둘 다 없음
                → ACTION_RESOLUTION_MISS → 예외 발생

    selectedInvoker.invoke(actionContext)
        └── 실행 실패 시 RuntimeException → 재시도/DLQ 처리로 전달
```

---

## BusinessWorkflowActionContext 구성

```java
public record BusinessWorkflowActionContext(
    BusinessInboundRecord record,           // 수신 원본 레코드 (payload, topic, eqpId …)
    TcModelRuntime modelRuntime,            // 모델 런타임 (워크플로우 인덱스, 메시지 정의 …)
    WorkflowRuntimeEntry workflowEntry,     // 현재 실행 중인 워크플로우 항목
    BusinessWorkflowFilterContext filterContext, // MSG/CTX 변수 맵, 필터 평가 컨텍스트
    BusinessWorkflowActionMessageType actionMessageType  // SECS / SOCKET / MES
)
```

---

## Plugin 우선 / Core Fallback 정책

Plugin 어댑터(`tc-business-plugin-adapter`)는 DB에서 JAR를 로드해 eqpId별 독립 레지스트리를 구성한다.

```
pluginRegistry (eqpId별)
        │
        └── 동일한 (MessageType, actionName) 키를 가진 메서드가 있으면 → Plugin 실행
                                            없으면 → coreRegistry Fallback
```

이를 통해 장비 벤더별 커스텀 액션 로직을 플러그인 JAR로 제공하면서도,
표준 동작은 Core 내장 실행기로 보장한다.

> 관련 문서: [공통: 플러그인 어댑터](../common/10-plugin-adapter.md)

---

## 내장 Core 액션 실행기

| 실행기 클래스 | MessageType | 설명 |
|---|---|---|
| `BusinessCoreSecsActionExecutor` | `SECS` | HSMS 메시지 기본 처리 |
| `BusinessCoreSocketActionExecutor` | `SOCKET` | TCP Socket 메시지 기본 처리 |
| `BusinessCoreMesActionExecutor` | `MES` | MES 이벤트 기본 처리 |

---

## 운영 포인트

| 항목 | 설명 |
|------|------|
| Plugin Override 로그 | INFO 레벨 — plugin이 core를 대체한 경우 명시적으로 기록 |
| Core Fallback 로그 | INFO 레벨 — plugin 미구현/불일치 시 core로 대체됨을 기록 |
| Action Miss 로그 | INFO + 예외 — 핸들러를 찾지 못하면 `BusinessWorkflowActionExecutionException` 발생 |
| 중복 키 방지 | 빌드 시점에 `IllegalStateException` 발생 → 배포 전에 발견 가능 |
| 메서드 파라미터 선택 | `requiresContext` 플래그로 0-파라미터 / 1-파라미터를 런타임에 구분 |

---

## 관련 문서

- [Business: 워크플로우 매칭](03-workflow-matching.md) — 액션 실행 전 매칭 단계
- [공통: 플러그인 어댑터](../common/10-plugin-adapter.md) — Plugin Registry 로드 메커니즘
- [Business: 태스크 재시도/타임아웃 정책](06-task-retry-timeout-policy.md) — 액션 실행 실패 후 처리
