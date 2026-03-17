# Business Plugin JAR

## 1. 목적

설비마다 워크플로우 액션의 비즈니스 로직이 다를 수 있습니다. Business Plugin JAR는 설비별로 커스텀 workflow action을 런타임에 교체·주입할 수 있도록 제공하는 경량 플러그인 시스템입니다.

**핵심 원칙: Plugin JAR 없이도 완전히 동작합니다.**

- JAR 없음: Core 액션 레지스트리(`BusinessWorkflowCoreActionRegistry`)의 기본 액션이 100% 실행됩니다.
- JAR 있음: 해당 설비의 워크플로우 실행 시 Plugin 액션이 우선 적용됩니다. 없는 액션은 Core fallback.

---

## 2. 시스템 구성도

```
[MES / 설비 이벤트]
  │
  ▼
[BusinessWorkflowDispatchingActionExecutor]
  │
  ├─ Plugin 레지스트리에 @TcAction 있음? → Plugin 액션 실행 (JAR 로드)
  │
  └─ 없음 → Core 액션 실행 (기본 동작)
```

### 런타임 관리 흐름

```
DB (tc_jar_business)
  │  eqpId + JAR bytes
  │
  ▼
BusinessWorkflowPluginRuntimeManager
  │  URLClassLoader 기반 동적 로드
  │  AbstractXxxActionExecutor 상속 클래스 탐색
  │  @TcAction 어노테이션 스캔 → 불변 레지스트리 구성
  │  CAS 원자적 교체 (서비스 무중단)
  │
  ▼
BusinessWorkflowDispatchingActionExecutor
  │  Plugin 우선 조회 → Core fallback
  │
  ▼
BusinessWorkflowActionMethodInvoker
  │  리플렉션으로 메서드 실행
```

---

## 3. DB 테이블 연계

| 컬럼 | 설명 |
|---|---|
| `eqp_id` | 설비 ID (PK 일부) |
| `jar_bytes` | JAR 파일 바이너리 |
| `created_at` | 등록 시각 |

---

## 4. `tc-business-action` SDK 의존 방법

플러그인 JAR를 개발할 때 `tc-business-action` 모듈만 `compileOnly`로 의존합니다.
Spring, Kafka, Jackson 등 앱 내부 의존성은 포함하지 않습니다.

```kotlin
// build.gradle.kts (플러그인 JAR 프로젝트)
dependencies {
    compileOnly("com.nori.tc:tc-business-action:1.0.0")
    // 순수 Java만 포함합니다. 추가 의존성 없음.
}
```

> **fat JAR 금지**: 앱이 이미 로드한 클래스를 플러그인 JAR에 중복 포함하지 않습니다.

---

## 5. 액션 구현 방법

패키지: `com.nori.tc.business.action`

### 5-1. Executor 클래스 상속

플러그인은 메시지 타입에 맞는 Abstract 클래스를 상속합니다.

| 클래스 | 사용 시점 |
|---|---|
| `AbstractSecsActionExecutor` | SECS/GEM 프로토콜 메시지 처리 |
| `AbstractSocketActionExecutor` | 일반 소켓 메시지 처리 |
| `AbstractMesActionExecutor` | MES 수신 메시지 처리 |

### 5-2. `@TcAction` 메서드 정의

```java
package com.example.plugin;

import com.nori.tc.business.action.AbstractSecsActionExecutor;
import com.nori.tc.business.action.TcAction;
import com.nori.tc.business.action.TcActionContext;

public class CustomSecsActionExecutor extends AbstractSecsActionExecutor {

    @TcAction("S1F1")
    public void handleS1F1(TcActionContext context) {
        // context에서 eqpId, messageName, payload, messageVariables 등을 꺼내 사용
        String eqpId = context.eqpId();
        String messageName = context.messageName();
        // ...
    }

    @TcAction("S2F41")
    public void handleS2F41(TcActionContext context) {
        // ...
    }
}
```

### 5-3. `@TcAction` 메서드 규칙

| 규칙 | 설명 |
|---|---|
| 반환 타입 | `void` 또는 `throws Exception` 포함 가능 |
| 파라미터 | 0개 또는 `TcActionContext` 1개만 허용 |
| `static` 금지 | static 메서드는 액션으로 등록되지 않음 |
| 어노테이션 값 | workflow에서 사용하는 액션 이름과 일치해야 함 |

---

## 6. `TcActionContext` API 레퍼런스

패키지: `com.nori.tc.business.action`

플러그인 `@TcAction` 메서드에서 받는 경량 컨텍스트입니다. Spring/Kafka/Jackson 의존 없음.

| 메서드 | 반환 타입 | 설명 |
|---|---|---|
| `eqpId()` | `String` | 설비 ID |
| `messageName()` | `String` | 수신 메시지 이름 (예: `"S1F1"`, `"DCSPECREQ_REP"`) |
| `payload()` | `String` | 수신 메시지 원본 페이로드 (JSON 문자열) |
| `traceId()` | `String` | 분산 추적용 trace ID |
| `messageVariables()` | `Map<String, Object>` | 파싱된 메시지 변수 맵 |
| `contextVariables()` | `Map<String, Object>` | 워크플로우 컨텍스트 변수 맵 |
| `workflowName()` | `String` | 현재 실행 중인 워크플로우 이름 |
| `actionName()` | `String` | 현재 실행 중인 액션 이름 |

---

## 7. 플러그인 JAR 내부 구조

플러그인 JAR에는 **커스텀 구현 클래스만** 포함합니다.

```
my-plugin.jar
├── com/example/plugin/CustomSecsActionExecutor.class
├── com/example/plugin/CustomMesActionExecutor.class
└── META-INF/MANIFEST.MF
```

**포함 금지 항목:**
- `tc-business-action` 클래스 (앱이 제공)
- Spring, Kafka, Jackson 등 앱 내부 라이브러리
- `tc-business-core`, `tc-comm-socket` 등 앱 내부 모듈

---

## 8. 런타임 reload 트리거

| 이벤트 | 동작 |
|---|---|
| `EQP_UPDATE_JARFILE` (business type) | 해당 설비의 Plugin JAR를 DB에서 재로드 |
| `EQP_REMOVE_JARFILE` (business type) | 해당 설비의 Plugin JAR를 제거 → Core 100% fallback |

`BusinessWorkflowPluginRuntimeManager`는 CAS(Compare-And-Swap) 방식으로 원자적 교체를 수행하므로, reload 중에도 진행 중인 워크플로우가 중단되지 않습니다.

---

## 9. ClassLoader 동작 원리

```
AppClassLoader (앱 전체)
    │
    └─ PluginClassLoader (URLClassLoader, 설비 단위)
           │ parent = AppClassLoader
           │
           └─ CustomSecsActionExecutor (플러그인 JAR)
```

- **parent-first 위임**: `AbstractSecsActionExecutor`, `TcAction`, `TcActionContext` 등 SDK 클래스는 앱 ClassLoader에서 먼저 로드됩니다.
- **패키지 일치 필요**: 플러그인 JAR의 Executor는 반드시 `com.nori.tc.business.action` 패키지의 Abstract 클래스를 상속해야 합니다. 다른 패키지의 동명 클래스는 인식되지 않습니다.
- **설비 단위 격리**: 설비별로 독립적인 URLClassLoader를 사용하므로 설비 간 클래스 충돌이 없습니다.
- **Discovery 방식**: JAR 내 모든 클래스를 스캔하여 `AbstractSecsActionExecutor`, `AbstractSocketActionExecutor`, `AbstractMesActionExecutor` 상속 클래스를 찾고, `@TcAction` 어노테이션이 있는 메서드를 레지스트리에 등록합니다.

---

## 10. Plugin 우선 + Core Fallback 동작

`BusinessWorkflowDispatchingActionExecutor`의 실행 순서:

1. 해당 설비의 Plugin 레지스트리에서 액션명(`@TcAction` 값) 조회
2. Plugin에 등록되어 있으면 Plugin 메서드 실행
3. Plugin에 없으면 Core 레지스트리에서 동일 액션명 조회 후 실행
4. 어디에도 없으면 WARN 로그 후 건너뜀

이 동작 덕분에 **부분 override**가 가능합니다. 예를 들어 `S1F1`만 Plugin에서 override하면, 나머지 액션(`S2F41`, `DATACOLL` 등)은 Core 기본 구현으로 자동 처리됩니다.
