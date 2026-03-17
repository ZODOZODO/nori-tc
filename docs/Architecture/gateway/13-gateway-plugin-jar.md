# Gateway Plugin JAR

## 1. 목적

설비마다 소켓 프로토콜 파싱 방식이 다를 수 있습니다. Gateway Plugin JAR는 설비별로 커스텀 `SocketTypeHandler`를 런타임에 교체·주입할 수 있도록 제공하는 경량 플러그인 시스템입니다.

**핵심 원칙: Plugin JAR 없이도 완전히 동작합니다.**

- JAR 없음: `SocketTypeRegistry`에 등록된 기본 핸들러(`LineDelimitedSocketTypeHandler`, `RegexDelimitedSocketTypeHandler`)가 사용됩니다.
- JAR 있음: 해당 설비의 소켓 인바운드 파이프라인에서 Plugin 핸들러를 우선 사용합니다.

---

## 2. 시스템 구성도

```
[설비]
  │  (TCP/IP)
  ▼
[Netty SocketInboundPipeline]
  │
  ├─ Plugin 핸들러 있음? → Plugin SocketTypeHandler (JAR 로드)
  │
  └─ 없음 → SocketTypeRegistry (기본 핸들러)
```

### 런타임 관리 흐름

```
DB (tc_jar_gateway)
  │  eqpId + JAR bytes + SHA-256 hash
  │
  ▼
GatewaySocketPluginRuntimeManager
  │  URLClassLoader 기반 동적 로드
  │  SHA-256 allowlist 보안 검증
  │  CAS 원자적 교체 (서비스 무중단)
  │
  ▼
GatewaySocketPluginRuntimeProvider
  │  설비별 SocketTypeHandler 조회
  │
  ▼
SocketInboundPipeline.selectHandler()
```

---

## 3. DB 테이블 연계

| 컬럼 | 설명 |
|---|---|
| `eqp_id` | 설비 ID (PK 일부) |
| `jar_bytes` | JAR 파일 바이너리 |
| `sha256_hash` | 보안 검증용 SHA-256 해시 |
| `created_at` | 등록 시각 |

---

## 4. 보안 검증 (SHA-256 allowlist)

`GatewaySocketPluginRuntimeManager`는 JAR 로드 시 SHA-256 해시를 검증합니다.

```yaml
# application.yml
tc:
  comm:
    gateway:
      plugin-runtime:
        allowed-sha256:
          - "abc123..."  # 허용할 JAR의 SHA-256 해시 목록
          - "def456..."
```

SHA-256 allowlist에 없는 JAR는 로드를 거부합니다. 허용 목록이 비어 있으면 모든 JAR를 거부합니다.

---

## 5. `tc-gateway-action` SDK 의존 방법

플러그인 JAR를 개발할 때 `tc-gateway-action` 모듈만 `compileOnly`로 의존합니다.
Spring, Kafka, Jackson 등 앱 내부 의존성은 포함하지 않습니다.

```kotlin
// build.gradle.kts (플러그인 JAR 프로젝트)
dependencies {
    compileOnly("com.nori.tc:tc-gateway-action:1.0.0")
    // tc-comm-core (ReassemblyBuffer 포함)는 tc-gateway-action에 포함됩니다.
}
```

> **fat JAR 금지**: 앱이 이미 로드한 클래스(tc-comm-core 등)를 플러그인 JAR에 중복 포함하지 않습니다.

---

## 6. `SocketTypeHandler` 구현

패키지: `com.nori.tc.comm.gateway.action`

JAR 당 `SocketTypeHandler` 구현체는 **반드시 정확히 1개**여야 합니다. 0개이거나 2개 이상이면 로드 실패합니다.

```java
package com.example.plugin;

import com.nori.tc.comm.gateway.action.SocketTypeHandler;
import com.nori.tc.comm.gateway.action.SocketTypeDecodeResult;
import com.nori.tc.comm.gateway.action.SocketTypeEncodeResult;
import com.nori.tc.comm.gateway.action.SocketFrame;
import com.nori.tc.comm.core.buffer.ReassemblyBuffer;

public class CustomSocketTypeHandler implements SocketTypeHandler {

    @Override
    public String socketType() {
        // 이 핸들러가 처리할 소켓 타입 식별자
        return "CUSTOM_V1";
    }

    @Override
    public SocketFrame tryExtractOne(ReassemblyBuffer buffer, int maxFrameBytes) {
        // 수신 버퍼에서 완성된 프레임 1개를 추출합니다.
        // 완성된 프레임이 없으면 null을 반환합니다.
        // ...
        return null;
    }

    @Override
    public SocketTypeDecodeResult decode(byte[] frameBytes) {
        // 프레임 바이트를 메시지로 변환합니다.
        // ...
        return SocketTypeDecodeResult.of(messageName, payload);
    }

    @Override
    public SocketTypeEncodeResult encode(Object command) {
        // 커맨드 객체를 송신 바이트로 변환합니다.
        // 인바운드 전용 핸들러라면 기본 구현(UnsupportedOperationException)으로 충분합니다.
        // ...
        return SocketTypeEncodeResult.of(bytes);
    }
}
```

---

## 7. API 레퍼런스

### 7-1. `SocketTypeHandler` 인터페이스

```java
package com.nori.tc.comm.gateway.action;

public interface SocketTypeHandler {

    /**
     * 이 핸들러가 처리하는 소켓 타입 식별자를 반환합니다.
     * 설비 모델의 socketType 필드와 매핑됩니다.
     */
    String socketType();

    /**
     * 수신 버퍼에서 완성된 프레임 1개를 추출합니다.
     *
     * @param buffer       수신 데이터 재조합 버퍼
     * @param maxFrameBytes 최대 허용 프레임 바이트 수
     * @return 완성된 SocketFrame. 완성 프레임 없으면 null
     */
    SocketFrame tryExtractOne(ReassemblyBuffer buffer, int maxFrameBytes);

    /**
     * 프레임 바이트를 디코딩하여 결과를 반환합니다.
     *
     * @param frameBytes 완성된 프레임 바이트
     * @return 디코딩 결과 (messageName, payload 포함)
     */
    SocketTypeDecodeResult decode(byte[] frameBytes);

    /**
     * 커맨드를 인코딩하여 송신 바이트를 반환합니다.
     * 기본 구현은 UnsupportedOperationException을 던집니다.
     *
     * @param command 인코딩할 커맨드 객체
     * @return 인코딩 결과 (송신 바이트 포함)
     */
    default SocketTypeEncodeResult encode(Object command) {
        throw new UnsupportedOperationException("encode is not supported by this handler");
    }
}
```

### 7-2. `SocketFrame` 레코드

| 필드 | 타입 | 설명 |
|---|---|---|
| `rawBytes` | `byte[]` | 원본 프레임 바이트 |

### 7-3. `SocketTypeDecodeResult` 레코드

| 필드 | 타입 | 설명 |
|---|---|---|
| `messageName` | `String` | 메시지 종류 식별자 |
| `payload` | `String` | 디코딩된 메시지 페이로드 |

### 7-4. `SocketTypeEncodeResult` 레코드

| 필드 | 타입 | 설명 |
|---|---|---|
| `bytes` | `byte[]` | 인코딩된 송신 바이트 |

### 7-5. `ReassemblyBuffer` (tc-comm-core)

`ReassemblyBuffer`는 `tc-comm-core`에 위치하며, 네트워크 스트림에서 수신된 바이트를 순서대로 누적·관리합니다. `tc-gateway-action`이 `tc-comm-core`를 전이 의존하므로 별도 의존 추가 불필요.

---

## 8. 플러그인 JAR 내부 구조

플러그인 JAR에는 **커스텀 구현 클래스만** 포함합니다.

```
my-plugin.jar
├── com/example/plugin/CustomSocketTypeHandler.class
└── META-INF/MANIFEST.MF
```

**포함 금지 항목:**
- `tc-gateway-action` 클래스 (앱이 제공)
- `tc-comm-core` 클래스 (앱이 제공)
- Spring, Netty, Kafka 등 앱 내부 라이브러리

---

## 9. 런타임 reload 트리거

| 이벤트 | 동작 |
|---|---|
| `EQP_UPDATE_JARFILE` (gateway type) | 해당 설비의 Plugin JAR를 DB에서 재로드 |
| `EQP_REMOVE_JARFILE` (gateway type) | 해당 설비의 Plugin JAR를 제거 → 기본 Registry fallback |

`GatewaySocketPluginRuntimeManager`는 CAS(Compare-And-Swap) 방식으로 원자적 교체를 수행하므로, reload 중에도 진행 중인 연결이 끊기지 않습니다.

---

## 10. ClassLoader 동작 원리

```
AppClassLoader (앱 전체)
    │
    └─ PluginClassLoader (URLClassLoader, JAR 단위)
           │ parent = AppClassLoader
           │
           └─ CustomSocketTypeHandler (플러그인 JAR)
```

- **parent-first 위임**: `SocketTypeHandler`, `ReassemblyBuffer` 등 SDK 클래스는 앱 ClassLoader에서 먼저 로드됩니다.
- **패키지 일치 필요**: 플러그인 JAR의 `SocketTypeHandler` 구현은 반드시 `com.nori.tc.comm.gateway.action.SocketTypeHandler`를 구현해야 합니다. 다른 패키지의 동명 인터페이스는 인식되지 않습니다.
- **설비 단위 격리**: 설비별로 독립적인 URLClassLoader를 사용하므로 설비 간 클래스 충돌이 없습니다.
