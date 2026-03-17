# Design: Action Modules (`tc-business-action`, `tc-gateway-action`)

## 1. 설계 배경 및 문제 정의

### 1-1. 기존 문제: 무거운 의존성 강요

nori-tc는 설비별 플러그인 JAR를 런타임에 URLClassLoader로 로드하는 플러그인 시스템을 갖추고 있습니다. 그러나 플러그인 개발자가 JAR를 빌드하려면 다음 모듈을 의존해야 했습니다.

| 플러그인 유형 | 기존 의존 필요 모듈 | 문제 |
|---|---|---|
| Business | `tc-business-core` | Spring, Kafka, Jackson 등 앱 내부 라이브러리 전체 포함 |
| Gateway | `tc-comm-socket` | 불필요한 내부 클래스(파이프라인, 레지스트리 등) 전부 노출 |

플러그인 개발자에게는 `AbstractSecsActionExecutor`, `@TcAction`, `SocketTypeHandler` 같은 SPI 계약 클래스만 필요합니다. 그 외 Spring Boot Autoconfiguration, Kafka Consumer, Jackson Deserializer 등은 전혀 필요하지 않습니다.

### 1-2. 기존 문제: `api.spi` 패키지 중복

Business SPI 클래스들이 `com.nori.tc.business.core.workflow.api.spi.executor` 패키지에 위치했습니다. `api`와 `spi`는 동일한 의미를 가리키므로 패키지 이름에 중복이 있었습니다.

### 1-3. 해결 방향

2개의 경량 Action 모듈을 신규 생성하여 SPI 계약 클래스를 분리합니다.

- `tc-business-action`: Business 플러그인 JAR 개발자용 SDK
- `tc-gateway-action`: Gateway 플러그인 JAR 개발자용 SDK

---

## 2. JAR 없이도 동작하는 이유 (Core Fallback)

플러그인 JAR는 **없어도 시스템이 완전히 동작**합니다.

### Business Fallback

`BusinessWorkflowDispatchingActionExecutor`는 Plugin 레지스트리를 먼저 조회하고, 없으면 Core 레지스트리로 fallback합니다. JAR가 없으면 Plugin 레지스트리가 비어 있으므로 항상 Core fallback이 실행됩니다.

```
Plugin 레지스트리 조회
    ├─ 있음 → Plugin 메서드 실행
    └─ 없음 → Core 레지스트리 조회 → Core 메서드 실행
```

### Gateway Fallback

`SocketInboundPipeline.selectHandler()`는 설비별 Plugin 핸들러를 먼저 조회하고, 없으면 `SocketTypeRegistry`의 기본 핸들러를 사용합니다.

```
GatewaySocketPluginRuntimeProvider 조회
    ├─ 있음 → Plugin SocketTypeHandler 사용
    └─ 없음 → SocketTypeRegistry (Line/Regex 기본 핸들러)
```

이 동작은 이미 구현되어 있으며, 두 신규 모듈은 이 동작을 변경하지 않습니다.

---

## 3. `tc-business-action` 설계 상세

### 3-1. 포함 클래스

| 클래스 | 이동 전 패키지 | 신규 패키지 |
|---|---|---|
| `AbstractSecsActionExecutor` | `com.nori.tc.business.core.workflow.api.spi.executor` | `com.nori.tc.business.action` |
| `AbstractSocketActionExecutor` | 동일 | `com.nori.tc.business.action` |
| `AbstractMesActionExecutor` | 동일 | `com.nori.tc.business.action` |
| `@TcAction` | `com.nori.tc.business.core.workflow.api.annotation` | `com.nori.tc.business.action` |
| `TcActionContext` | 없음 (신규) | `com.nori.tc.business.action` |

### 3-2. 패키지 재설계 결정

- **Before**: `com.nori.tc.business.core.workflow.api.spi.executor` (`api`와 `spi` 중복)
- **After**: `com.nori.tc.business.action` (모듈 이름 기반, 단순하고 명확)

### 3-3. `TcActionContext` 설계 결정

`TcActionContext`는 플러그인 개발자용 경량 컨텍스트 인터페이스입니다.

**설계 요구사항:**
- 순수 Java: Spring/Kafka/Jackson 의존 없음
- 플러그인 개발자가 필요한 정보만 노출: eqpId, messageName, payload, traceId, messageVariables, contextVariables, workflowName, actionName
- 앱 내부 타입(`BusinessWorkflowRecord`, `WorkflowEntry` 등) 노출 금지

**구현 결정:**

`BusinessWorkflowActionContext`(앱 내부)가 `TcActionContext`를 구현합니다. 플러그인 메서드는 `TcActionContext` 파라미터를 선언하지만, 런타임에는 `BusinessWorkflowActionContext` 인스턴스가 전달됩니다. 이는 Java 인터페이스 다형성에 의해 정상 동작합니다.

```java
// 앱 내부 (tc-business-core)
public record BusinessWorkflowActionContext(...) implements TcActionContext {
    @Override public String eqpId() { return record.eqpId(); }
    // ...
}

// 플러그인 JAR (개발자 작성)
@TcAction("S1F1")
public void handle(TcActionContext context) {
    // 런타임에 BusinessWorkflowActionContext 인스턴스를 받음
    String eqpId = context.eqpId(); // 정상 동작
}
```

**`@TcAction` 파라미터 검증 변경:**

`BusinessWorkflowActionRegistryBuilder.validateActionMethod()`에서 파라미터 타입 검사를 변경했습니다.

- **Before**: `BusinessWorkflowActionContext.class.isAssignableFrom(paramType)` — Core 내부 타입에 강결합
- **After**: `TcActionContext.class.isAssignableFrom(paramType)` — 인터페이스 기반 검사

이로 인해 `TcActionContext` 파라미터를 선언한 플러그인 메서드와 `BusinessWorkflowActionContext` 파라미터를 선언한 Core 메서드 모두 유효한 `@TcAction` 메서드로 인식됩니다.

### 3-4. 의존성

```
tc-business-action
    │
    └─ (없음): 순수 Java 표준 라이브러리만 사용
```

---

## 4. `tc-gateway-action` 설계 상세

### 4-1. 포함 클래스

| 클래스 | 이동 전 패키지 | 신규 패키지 |
|---|---|---|
| `SocketTypeHandler` | `com.nori.tc.comm.gateway.socket.socketType.core` | `com.nori.tc.comm.gateway.action` |
| `SocketTypeDecodeResult` | 동일 | `com.nori.tc.comm.gateway.action` |
| `SocketTypeEncodeResult` | 동일 | `com.nori.tc.comm.gateway.action` |
| `SocketFrame` | `com.nori.tc.comm.gateway.socket.frame` | `com.nori.tc.comm.gateway.action` |

### 4-2. `ReassemblyBuffer` 처리 결정

`ReassemblyBuffer`는 `tc-comm-core` 모듈에 위치합니다. 15개 이상의 파일에서 사용 중이므로 이동이 불가합니다.

`SocketTypeHandler.tryExtractOne(ReassemblyBuffer, int)` 메서드 시그니처가 `ReassemblyBuffer`를 참조하므로, `tc-gateway-action`이 `tc-comm-core`를 전이 의존합니다.

`tc-comm-core`는 순수 Java(Netty 버퍼 래퍼 정도)로 구성되어 있어 플러그인 개발자에게 과도한 의존을 강요하지 않습니다.

```
tc-gateway-action
    │
    └─ api(tc-comm-core): ReassemblyBuffer 참조를 위해
```

### 4-3. 패키지 재설계 결정

- **Before**: `com.nori.tc.comm.gateway.socket.socketType.core` (내부 구조 노출)
- **After**: `com.nori.tc.comm.gateway.action` (모듈 이름 기반, 단순하고 명확)

---

## 5. 의존성 그래프 Before / After

### Before

```
플러그인 JAR 개발자 (Business)
    compileOnly → tc-business-core (Spring + Kafka + Jackson + 앱 내부 구조 전체)

플러그인 JAR 개발자 (Gateway)
    compileOnly → tc-comm-socket (내부 파이프라인, 레지스트리 전체 노출)
```

### After

```
플러그인 JAR 개발자 (Business)
    compileOnly → tc-business-action (순수 Java, 의존 없음)

플러그인 JAR 개발자 (Gateway)
    compileOnly → tc-gateway-action → tc-comm-core (순수 Java)

tc-business-core
    api → tc-business-action (신규)
    (기존 의존성 유지)

tc-comm-socket
    api → tc-gateway-action (신규)
    (기존 의존성 유지)

tc-comm-gateway-plugin-adapter
    api → tc-gateway-action (신규, SocketTypeHandler 탐색에 사용)
```

---

## 6. ClassLoader 동작 상세

### parent-first 위임 모델

URLClassLoader는 기본적으로 parent-first 위임을 사용합니다. 즉, 클래스 로드 요청이 오면 먼저 parent(AppClassLoader)에게 위임하고, parent가 찾지 못할 때만 자신(JAR)에서 찾습니다.

이 때문에 플러그인 JAR가 `tc-business-action` 클래스를 포함하더라도(실수로 fat JAR를 빌드한 경우), 앱에서 이미 로드된 `tc-business-action` 클래스가 우선 사용됩니다.

### 패키지 일치가 중요한 이유

플러그인 JAR의 `CustomSecsActionExecutor`가 `AbstractSecsActionExecutor`를 상속할 때, 이 `AbstractSecsActionExecutor`가 앱의 `com.nori.tc.business.action.AbstractSecsActionExecutor`와 동일한 클래스이어야만 `instanceof` 체크 및 리플렉션이 정상 동작합니다.

패키지가 다르면 JVM은 이를 다른 클래스로 취급합니다.

---

## 7. 변경 범위 요약

### 신규 파일

| 경로 | 설명 |
|---|---|
| `libs/action/tc-business-action/` | Business SDK 모듈 전체 |
| `libs/action/tc-gateway-action/` | Gateway SDK 모듈 전체 |

### 수정 파일 (build.gradle.kts)

| 파일 | 변경 내용 |
|---|---|
| `settings.gradle.kts` | 신규 2개 모듈 include 추가 |
| `libs/business/tc-business-core/build.gradle.kts` | `tc-business-action` 의존 추가 |
| `libs/comm/tc-comm-socket/build.gradle.kts` | `tc-gateway-action` 의존 추가 |
| `libs/comm/adapter/tc-comm-gateway-plugin-adapter/build.gradle.kts` | `tc-gateway-action` 의존 추가 |

### 수정 파일 (import 변경 — Business)

| 파일 | 변경 내용 |
|---|---|
| `BusinessWorkflowActionContext.java` | `implements TcActionContext` 추가 |
| `BusinessWorkflowActionRegistryBuilder.java` | 파라미터 검증 타입 변경 (`TcActionContext`) |
| `BusinessWorkflowCoreActionRegistry.java` | AbstractXxx import 변경 |
| `BusinessCoreSecsActionExecutor.java` | import 변경 |
| `BusinessCoreSocketActionExecutor.java` | import 변경 |
| `BusinessCoreMesActionExecutor.java` | import 변경 |
| `DatacollTcAction.java` | import 변경 |
| `CollectDcdataTcAction.java` | import 변경 |
| `DcspecreqRepTcAction.java` | import 변경 |
| `BusinessWorkflowPluginRuntimeManager.java` | import 변경 |

### 수정 파일 (import 변경 — Gateway)

| 파일 | 변경 내용 |
|---|---|
| `SocketTypeRegistry.java` | `SocketTypeHandler` import 변경 |
| `SocketInboundPipeline.java` | `SocketFrame`, `SocketTypeDecodeResult`, `SocketTypeHandler` import 변경 |
| `LineDelimitedSocketTypeHandler.java` | 4개 import 변경 |
| `RegexDelimitedSocketTypeHandler.java` | 4개 import 변경 |
| `GatewaySocketPluginRuntimeProvider.java` | `SocketTypeHandler` import 변경 |
| `GatewaySocketPluginRuntimeManager.java` | `SocketTypeHandler` import 변경 |

### 삭제 파일

| 경로 | 이유 |
|---|---|
| `tc-comm-socket/.../socketType/core/SocketTypeHandler.java` | `tc-gateway-action`으로 이동 |
| `tc-comm-socket/.../socketType/core/SocketTypeDecodeResult.java` | 동일 |
| `tc-comm-socket/.../socketType/core/SocketTypeEncodeResult.java` | 동일 |
| `tc-comm-socket/.../frame/SocketFrame.java` | 동일 |
| `tc-business-core/.../api/spi/executor/AbstractSecsActionExecutor.java` | `tc-business-action`으로 이동 |
| `tc-business-core/.../api/spi/executor/AbstractSocketActionExecutor.java` | 동일 |
| `tc-business-core/.../api/spi/executor/AbstractMesActionExecutor.java` | 동일 |
| `tc-business-core/.../api/annotation/TcAction.java` | 동일 |
