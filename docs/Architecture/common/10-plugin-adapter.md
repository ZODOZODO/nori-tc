# 10. 플러그인 어댑터 (Plugin Adapter)

## 적용 앱

- `tc-comm-gateway-app` — SOCKET 프로토콜 인코더/디코더 플러그인
- `tc-business-core-app` — 설비별 비즈니스 워크플로우 액션 플러그인

---

## 개요

**플러그인 어댑터**는 앱을 재배포하지 않고 **런타임에 기능을 동적으로 추가/교체**하는 구조입니다.

설비마다 통신 포맷이나 비즈니스 처리 로직이 다를 수 있습니다.
이 다양성을 앱 코드에 모두 하드코딩하면 새 설비가 추가될 때마다 앱을 재배포해야 합니다.
플러그인 어댑터는 이 로직을 **JAR 파일**로 분리해서 DB에 저장하고, 앱 기동 시 동적으로 로드합니다.

---

## 왜 플러그인이 필요한가?

### 문제: 설비마다 로직이 다른 경우

```
설비 A: 메시지 포맷 → JSON {"type":"S6F11", "eqpId":"EQP-001"}
설비 B: 메시지 포맷 → CSV EQP-002,S6F11,EVENT_DATA
설비 C: 메시지 포맷 → 바이너리 프로토콜 (사용자 정의)
```

앱에 모든 포맷 처리를 넣으면:
- 새 설비 추가 → 앱 코드 수정 → 빌드 → 배포 → 재시작 (모든 설비 영향)
- 설비가 100종류면 앱에 100개의 분기가 생김

플러그인으로 분리하면:
- 새 설비 추가 → 플러그인 JAR 작성 → DB에 업로드 → 런타임 reload (서비스 중단 없음)
- 앱 코드는 변경 없음

---

## 플러그인 동작 원리

```
┌──────────────────────────────────────────────────────────────┐
│                      앱 기동 시                              │
│                                                              │
│  1. DB에서 설비별 JAR 바이트 로드                           │
│     tc_jar_comm 또는 tc_jar_business 테이블                  │
│                                                              │
│  2. 임시 파일에 저장                                        │
│     /tmp/nori-tc/{gateway|business}-plugin-runtime/          │
│                                                              │
│  3. SHA-256 해시 검증                                       │
│     allowlist에 등록된 해시인지 확인                         │
│                                                              │
│  4. URLClassLoader로 JAR 로드                               │
│     새 ClassLoader 생성 → JAR에서 클래스 탐색               │
│                                                              │
│  5. 구현체 클래스 인스턴스 생성                             │
│     AbstractXxxActionExecutor를 implements 한 클래스 탐색   │
│                                                              │
│  6. eqpId → 플러그인 런타임 맵에 등록                       │
│     원자적으로 교체 (CAS: Compare-And-Swap)                  │
└──────────────────────────────────────────────────────────────┘
```

---

## Gateway 플러그인 (SOCKET 프로토콜)

### 목적

SOCKET 프로토콜을 사용하는 설비의 **메시지 인코딩/디코딩** 로직을 플러그인으로 제공합니다.

HSMS 프로토콜은 표준화된 포맷을 사용하지만,
SOCKET 프로토콜은 설비마다 포맷이 완전히 다를 수 있습니다.

### 플러그인이 구현해야 하는 인터페이스

```java
// 인바운드 (설비 → Gateway): 설비 메시지를 공통 포맷으로 변환
public abstract class AbstractSocketInboundDecoder {
    // 설비에서 받은 raw bytes → 앱이 처리할 수 있는 이벤트 객체
    public abstract EqpInboundEvent decode(byte[] rawBytes);
}

// 아웃바운드 (Gateway → 설비): 공통 포맷을 설비가 이해하는 포맷으로 변환
public abstract class AbstractSocketOutboundEncoder {
    // 앱의 명령 객체 → 설비에 보낼 raw bytes
    public abstract byte[] encode(EqpOutboundCommand command);
}
```

### 설정

```properties
# tc-comm.properties
tc.comm.gateway.plugin.load-on-startup=true          # 기동 시 자동 로드
tc.comm.gateway.plugin.fail-fast-on-startup=true     # 로드 실패 시 앱 기동 중단
tc.comm.gateway.plugin.max-jar-bytes=10485760        # 최대 10MB
tc.comm.gateway.plugin.enforce-sha256-allowlist=true # SHA-256 검증 활성화
tc.comm.gateway.plugin.allowed-sha256[0]=abc123...   # 허용된 JAR의 SHA-256 해시
tc.comm.gateway.plugin.allowed-sha256[1]=def456...
```

---

## Business Core 플러그인 (워크플로우 액션)

### 목적

설비별 **비즈니스 처리 로직(Action)**을 플러그인으로 제공합니다.

어떤 이벤트가 들어왔을 때 어떤 동작을 할지는 설비마다 다릅니다.
이 로직을 플러그인으로 분리해서 설비별로 독립적으로 관리합니다.

### 지원 액션 타입

| 타입 | 기반 클래스 | 용도 |
|------|-----------|------|
| SECS | `AbstractSecsActionExecutor` | SECS-II 기반 명령/응답 처리 |
| SOCKET | `AbstractSocketActionExecutor` | SOCKET 기반 커스텀 통신 처리 |
| MES | `AbstractMesActionExecutor` | MES(Manufacturing Execution System) 연동 처리 |

### 플러그인이 구현해야 하는 인터페이스 예시

```java
// 설비에서 S6F11 이벤트 수신 시 처리할 커스텀 로직
public class MyEquipmentEventHandler extends AbstractSecsActionExecutor {

    @TcAction(messageName = "S6F11", eventName = "PROCESS_STARTED")
    public BusinessActionResult onProcessStarted(BusinessWorkflowActionContext context) {
        // EQP-001의 공정 시작 이벤트 처리 로직
        String eqpId = context.eqpId();
        Secs2Data payload = context.payload();

        // MES에 보고, 레시피 조회, 상태 업데이트 등
        return BusinessActionResult.success();
    }
}
```

### 플러그인 로드 흐름

```java
// BusinessWorkflowPluginRuntimeManager.java

// 1. DB에서 JAR 로드
@Transactional(readOnly = true)
public void preload() {
    List<PluginJarRecord> jars = pluginJarRepository.findAll();

    for (PluginJarRecord jar : jars) {
        loadPlugin(jar.eqpId(), jar.jarBytes());
    }
}

// 2. JAR → 임시 파일 → URLClassLoader
private void loadPlugin(String eqpId, byte[] jarBytes) {
    // 크기 검증
    if (jarBytes.length > maxJarBytes) throw new PluginLoadException("JAR 크기 초과");

    // SHA-256 검증
    String hash = computeSha256(jarBytes);
    if (!allowedHashes.contains(hash)) throw new PluginLoadException("허용되지 않은 JAR");

    // 임시 파일 저장
    Path tmpJar = Files.write(
        Paths.get("/tmp/nori-tc/business-plugin-runtime/" + eqpId + ".jar"),
        jarBytes
    );

    // ClassLoader로 로드
    URLClassLoader classLoader = new URLClassLoader(
        new URL[]{tmpJar.toUri().toURL()},
        getClass().getClassLoader()
    );

    // 액션 레지스트리 구성
    PluginRuntime runtime = buildRegistry(classLoader);

    // 원자적으로 교체 (실행 중인 처리에 영향 없음)
    pluginRuntimeMap.compute(eqpId, (key, old) -> runtime);
}
```

---

## SHA-256 보안 검증

모든 플러그인 JAR는 **SHA-256 해시 allowlist** 검증을 통과해야 합니다.

```
검증 흐름:

1. JAR 바이트 → SHA-256 해시 계산
   예: abc123def456...

2. 설정의 allowed-sha256 목록에 해당 해시 존재?
   Yes → 로드 진행
   No  → 로드 거절 (예외 발생)

3. fail-fast-on-startup=true 일 때:
   로드 거절 → 앱 기동 실패
   → 운영자가 즉시 인식하고 조치
```

**SHA-256 allowlist의 목적:**
- 악의적인 JAR가 DB에 업로드되어 실행되는 것을 방지합니다
- JAR 파일이 변조되면 해시가 달라져서 로드를 차단합니다
- 배포 시 검증된 JAR의 해시만 allowlist에 등록합니다

---

## 런타임 reload (서비스 중단 없이 플러그인 교체)

Business Core는 실행 중에도 플러그인을 교체할 수 있습니다.

```java
// 특정 설비의 플러그인 재로드 (UI 요청 또는 관리 API)
public void reloadPluginsByEqpId(String eqpId) {
    // 새 JAR를 DB에서 조회
    PluginJarRecord newJar = pluginJarRepository.findByEqpId(eqpId);

    try {
        // 새 플러그인 로드 및 레지스트리 구성
        PluginRuntime newRuntime = loadPlugin(eqpId, newJar.jarBytes());

        // 원자적 교체 (진행 중인 처리는 이전 플러그인으로 완료)
        pluginRuntimeMap.put(eqpId, newRuntime);
        log.info("플러그인 재로드 완료: eqpId={}", eqpId);

    } catch (Exception e) {
        // 실패 시 이전 플러그인 유지
        log.error("플러그인 재로드 실패 — 이전 런타임 유지: eqpId={}", eqpId, e);
        // PLUGIN_RELOAD_ROLLED_BACK 이벤트 로그
    }
}
```

---

## 플러그인 없을 때의 동작

플러그인이 등록되지 않은 설비는 **No-op 플러그인** 으로 fallback됩니다.

```java
// GatewayCommConfiguration.java
@Bean
@ConditionalOnMissingBean(SocketPluginRuntimeProvider.class)
public SocketPluginRuntimeProvider socketPluginRuntimeProvider() {
    // 플러그인 없으면 아무것도 안 하는 no-op 반환
    // 이 경우 SOCKET 프로토콜 설비는 raw bytes를 그대로 통과
    return SocketPluginRuntimeProvider.noop();
}
```

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **SHA-256 allowlist 필수** | `enforce-sha256-allowlist=true`를 반드시 유지하세요. 비활성화하면 임의의 코드가 실행될 수 있습니다 |
| **JAR 크기 제한** | 최대 10MB(기본값)를 초과하는 JAR는 로드되지 않습니다. 의존성 라이브러리를 최소화하세요 |
| **ClassLoader 메모리** | 동적 ClassLoader는 GC로 쉽게 수집되지 않습니다. 재로드 시 이전 ClassLoader 참조가 남지 않도록 주의하세요 |
| **예외 처리** | 플러그인 코드에서 발생한 예외는 앱이 catch해서 DLQ 처리합니다. 플러그인에서 System.exit()를 호출하면 안 됩니다 |
| **DB JAR 접근 권한** | `tc_jar_comm`, `tc_jar_business` 테이블에는 엄격한 DB 권한이 필요합니다. 무결성이 깨지면 보안 위험이 있습니다 |
| **임시 파일 정리** | `/tmp/nori-tc/` 경로의 임시 파일은 앱 종료 시 정리되지 않을 수 있습니다. 정기적으로 정리하세요 |
