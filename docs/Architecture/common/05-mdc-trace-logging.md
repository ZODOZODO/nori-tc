# 05. MDC 기반 추적 로깅 (MDC Trace Logging)

## 개요

nori-tc의 모든 앱은 로그에 **eqpId(설비 ID)** 와 **traceId(추적 ID)** 를 함께 출력합니다.
이를 통해 하나의 요청이 여러 앱과 스레드를 거치더라도 로그에서 전체 흐름을 추적할 수 있습니다.

이 기능의 핵심은 SLF4J의 **MDC(Mapped Diagnostic Context)** 입니다.
MDC는 현재 스레드에 key-value 쌍을 저장해두고, 로그 출력 시 자동으로 포함시키는 기능입니다.

---

## 왜 필요한가?

### 문제: MDC 없이 로그를 보면?

```
2026-03-11 10:00:01 INFO  설비 연결 시도
2026-03-11 10:00:01 INFO  설비 연결 시도
2026-03-11 10:00:02 INFO  명령 수신
2026-03-11 10:00:02 ERROR 처리 실패
2026-03-11 10:00:03 INFO  설비 연결 완료
```

- 어떤 설비의 로그인지 알 수 없습니다
- 8개의 설비가 동시에 동작하면 로그가 뒤섞여서 특정 설비의 흐름을 파악하기 매우 어렵습니다
- 장애 원인 분석에 오랜 시간이 걸립니다

### 해결: MDC로 eqpId + traceId 주입

```
2026-03-11 10:00:01 INFO  [eqpId=EQP-001][traceId=01JNCMX7Y...] 설비 연결 시도
2026-03-11 10:00:01 INFO  [eqpId=EQP-002][traceId=01JNCMX8Z...] 설비 연결 시도
2026-03-11 10:00:02 INFO  [eqpId=EQP-001][traceId=01JNCMX7Y...] 명령 수신
2026-03-11 10:00:02 ERROR [eqpId=EQP-001][traceId=01JNCMX7Y...] 처리 실패
2026-03-11 10:00:03 INFO  [eqpId=EQP-002][traceId=01JNCMX8Z...] 설비 연결 완료
```

- `eqpId=EQP-001`로 필터링하면 해당 설비의 로그만 볼 수 있습니다
- `traceId=01JNCMX7Y...`로 필터링하면 하나의 요청 전체 흐름을 볼 수 있습니다

---

## MDC 동작 원리

```
┌──────────────────────────────────────────────────────────────┐
│                    스레드 A (EQP-001 처리)                    │
│                                                              │
│  MDC 저장소 (스레드 로컬)                                    │
│  ┌─────────────────────────┐                                │
│  │ eqpId   = "EQP-001"     │                                │
│  │ traceId = "01JNCMX7Y..."│                                │
│  └─────────────────────────┘                                │
│          ↓                                                   │
│  log.info("설비 연결 시도")                                  │
│          ↓                                                   │
│  Logback/Log4j2 가 MDC 값을 자동으로 패턴에 삽입             │
│  출력: [eqpId=EQP-001][traceId=01JNCMX7Y...] 설비 연결 시도 │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                    스레드 B (EQP-002 처리)                    │
│                                                              │
│  MDC 저장소 (스레드 로컬 — 스레드A와 완전히 독립)            │
│  ┌─────────────────────────┐                                │
│  │ eqpId   = "EQP-002"     │                                │
│  │ traceId = "01JNCMX8Z..."│                                │
│  └─────────────────────────┘                                │
│          ↓                                                   │
│  log.info("설비 연결 시도")                                  │
│          ↓                                                   │
│  출력: [eqpId=EQP-002][traceId=01JNCMX8Z...] 설비 연결 시도 │
└──────────────────────────────────────────────────────────────┘
```

MDC는 **스레드 로컬(ThreadLocal)** 에 저장됩니다.
각 스레드가 독립된 저장소를 가지므로, 여러 설비를 병렬로 처리해도 MDC 값이 섞이지 않습니다.

---

## traceId 생성 (ULID)

traceId는 **ULID(Universally Unique Lexicographically Sortable Identifier)** 로 생성됩니다.

```
ULID 예시: 01JNCMX7YBFZQ9KPXR3T5E2WV4

구조: [타임스탬프 10자][랜덤 16자]
      01JNCMX7YB     FZQQ9KPXR3T5E2WV4
```

### ULID를 사용하는 이유

| 비교 | UUID (기존 방식) | ULID (현재 방식) |
|------|----------------|----------------|
| 정렬 | 랜덤이라 시간순 정렬 불가 | 타임스탬프 포함으로 시간순 정렬 가능 |
| 가독성 | `550e8400-e29b-41d4-a716...` | `01JNCMX7YB...` (더 짧음) |
| 충돌 가능성 | 매우 낮음 | 매우 낮음 |
| DB 인덱스 성능 | 랜덤 삽입으로 성능 저하 가능 | 단조증가로 인덱스 효율적 |

```java
// UlidTraceIdGenerator.java
public class UlidTraceIdGenerator implements TraceIdGeneratorPort {

    @Override
    public String generate() {
        return ULID.nextULID();  // 밀리초 타임스탬프 + 랜덤 부분
    }
}
```

---

## 앱별 MDC 구현

### tc-comm-gateway-app

게이트웨이는 설비 단위 처리가 많으므로 `eqpId`와 `traceId`를 함께 관리합니다.

```java
// Gateway 처리 시 MDC 주입 패턴
try (MdcScope scope = MdcScope.of("eqpId", eqpId, "traceId", traceId)) {
    processor.process(message);
}
// scope 종료 시 MDC 자동 정리
```

### tc-business-core-app

Business Core는 `BusinessLogContext` 헬퍼 클래스로 MDC를 관리합니다.

```java
// BusinessLogContext.java
public class BusinessLogContext implements AutoCloseable {

    // eqpId만 주입할 때
    public static BusinessLogContext withEqpId(String eqpId) {
        MDC.put("eqpId", eqpId);
        return new BusinessLogContext();
    }

    // eqpId + traceId 함께 주입할 때
    public static BusinessLogContext withEqpAndTraceId(String eqpId, String traceId) {
        MDC.put("eqpId", eqpId);
        MDC.put("traceId", traceId);
        return new BusinessLogContext();
    }

    @Override
    public void close() {
        MDC.remove("eqpId");
        MDC.remove("traceId");
    }
}
```

**사용 예시:**

```java
try (BusinessLogContext ignored =
        BusinessLogContext.withEqpAndTraceId(task.eqpId(), task.traceId())) {

    log.info("작업 처리 시작");   // 로그에 eqpId, traceId 자동 포함
    processTask(task);
    log.info("작업 처리 완료");
}
// finally 블록 없어도 close()가 자동 호출됨 (try-with-resources)
```

### tc-ui-backend-app

UI Backend는 HTTP 요청마다 새 traceId를 생성합니다.

```java
// 요청 시작 시 traceId 생성
String traceId = traceIdGeneratorPort.generate();

try (MdcTraceScope scope = openTraceMdcScope(traceId)) {
    log.info("HTTP 요청 처리 시작");
    result = service.process(request);
    log.info("HTTP 요청 처리 완료");
}
```

Kafka 응답 수신 시에도 응답 메시지의 traceId를 MDC에 주입해서 요청-응답 추적이 가능합니다.

---

## 비동기 스레드에서의 MDC 전파

MDC는 스레드 로컬이므로 새 스레드를 생성하면 MDC 값이 사라집니다.
nori-tc에서는 비동기 작업에 MDC를 전파하기 위한 래퍼를 사용합니다.

### 문제

```java
// MDC가 메인 스레드에 있을 때
MDC.put("eqpId", "EQP-001");

// 새 스레드에서는 MDC가 비어있음!
executor.submit(() -> {
    log.info("비동기 처리"); // eqpId 없음!
});
```

### 해결: MDC 복사 래퍼

```java
// BusinessLogContext.wrap() 사용
Map<String, String> mdcContext = MDC.getCopyOfContextMap(); // 현재 MDC 복사

executor.submit(() -> {
    MDC.setContextMap(mdcContext); // 새 스레드에 MDC 복원
    try {
        log.info("비동기 처리"); // eqpId 포함!
    } finally {
        MDC.clear(); // 스레드 풀 재사용 시 오염 방지
    }
});
```

---

## 로그 패턴 설정

MDC 값이 로그에 출력되려면 로그 패턴에 `%X{키}` 형식으로 포함해야 합니다.

```xml
<!-- logback-spring.xml 또는 tc-log.properties -->
<pattern>
  %d{yyyy-MM-dd HH:mm:ss.SSS}
  %-5level
  [%X{eqpId}][%X{traceId}]
  %logger{36} - %msg%n
</pattern>
```

**출력 예시:**

```
2026-03-11 10:00:01.234 INFO  [EQP-001][01JNCMX7YB...] c.n.t.c.GatewayChannelHandler - 설비 연결 성공
2026-03-11 10:00:01.250 DEBUG [EQP-001][01JNCMX7YB...] c.n.t.c.HsmsInboundPipeline - 프레임 수신: 128 bytes
2026-03-11 10:00:01.260 INFO  [EQP-001][01JNCMX7YB...] c.n.t.c.RouteAndPublishUseCase - 이벤트 발행 완료
```

---

## traceId 전파 흐름

traceId는 앱 간을 이동할 때도 메시지에 포함되어 전파됩니다.

```
[UI 화면]
  HTTP 요청
    ↓
[tc-ui-backend-app]
  traceId 생성: 01JNCMX7YB...
  Kafka 메시지에 traceId 포함해서 발행
    ↓
[tc-comm-gateway-app]
  메시지에서 traceId 추출
  MDC에 traceId 주입
  설비로 명령 전송
    ↓
[설비]
  명령 처리 후 응답
    ↓
[tc-comm-gateway-app]
  설비 응답 수신
  Kafka 메시지에 동일 traceId 포함해서 발행
    ↓
[tc-ui-backend-app]
  동일 traceId로 응답 매핑
  HTTP 응답 반환
```

전체 흐름에서 같은 `traceId`를 사용하므로, Kibana/Grafana에서 `traceId` 하나로 전체 요청 경로를 추적할 수 있습니다.

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **MDC 정리 필수** | try-with-resources 또는 finally에서 MDC를 반드시 정리하세요. 스레드 풀 환경에서 이전 요청의 MDC가 남아 오염될 수 있습니다 |
| **비동기 전파** | 새 스레드/Executor에서는 MDC를 명시적으로 복사해야 합니다 |
| **민감 정보** | MDC에 비밀번호, 토큰, 개인정보를 넣지 마세요. 모든 로그에 출력됩니다 |
| **traceId 신뢰** | 외부에서 받은 traceId는 검증 후 사용하세요. 임의의 값을 그대로 MDC에 넣으면 로그 주입 공격이 될 수 있습니다 |
