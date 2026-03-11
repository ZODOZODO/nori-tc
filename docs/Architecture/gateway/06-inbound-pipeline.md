# 06. 인바운드 파이프라인 (Inbound Pipeline)

## 개요

**인바운드 파이프라인**은 설비에서 수신한 raw bytes를 Gateway가 처리할 수 있는 이벤트 객체로 변환하는 과정입니다.

설비에서 받은 데이터는 단순한 바이트 배열(`byte[]`)입니다.
이 바이트 배열을 파싱하고, 의미를 해석하고, 설비 ID를 확인하고, 결과를 Kafka에 발행하는 일련의 과정이 인바운드 파이프라인입니다.

---

## 전체 처리 흐름

```
설비
  │ TCP 전송
  ↓
┌────────────────────────────────────────────────────────────────┐
│  GatewayChannelHandler (Netty 채널 핸들러)                    │
│  - raw bytes 수신                                             │
│  - eqpId 확인 (채널 속성에서)                                │
└───────────────────────┬────────────────────────────────────────┘
                        │
        ┌───────────────┴───────────────┐
        │                               │
        ↓ HSMS 프로토콜                 ↓ SOCKET 프로토콜
┌───────────────────┐         ┌─────────────────────┐
│HsmsInboundPipeline│         │SocketInboundPipeline│
│                   │         │                     │
│ 1. 프레임 재조립  │         │ 1. 프레임 분리      │
│ 2. 크기 검증      │         │    (줄바꿈/정규식)   │
│ 3. SECS-II 디코딩 │         │ 2. 크기 검증         │
│ 4. eqpId 추출     │         │ 3. 플러그인 디코딩   │
│    (SessionID)    │         │ 4. eqpId 확인        │
└─────────┬─────────┘         └──────────┬──────────┘
          │                              │
          └──────────────┬───────────────┘
                         ↓
            EquipmentMailboxRegistry
            (eqpId 기반 메일박스로 전달)
                         ↓
              EqpSequentialProcessor
              (순서대로 처리)
                         ↓
            RouteAndPublishUseCase
            (이벤트 분류 → Kafka 발행)
                         ↓
              tc.eqp.events (Kafka)
```

---

## HSMS 인바운드 파이프라인

### 1단계: 프레임 재조립 (HsmsFrameExtractor)

TCP는 스트림 기반이므로 하나의 HSMS 메시지가 여러 번에 나눠서 도착할 수 있습니다.
`HsmsFrameExtractor`는 누적된 bytes에서 완전한 프레임을 조립합니다.

```
첫 번째 수신 (100 bytes):
  [Length: 500][Header: ...][Data: 첫 90 bytes...]
  → 아직 완성 안 됨 (500 bytes가 필요한데 100만 받음)

두 번째 수신 (400 bytes):
  [...Data 이어서...]
  → 총 500 bytes 완성!
  → 완성된 프레임 전달
```

**재조립 버퍼 설정:**
```properties
# tc-comm.properties
tc.comm.gateway.hsms.reassembly-initial-bytes=4096   # 초기 버퍼 크기
tc.comm.gateway.hsms.reassembly-max-bytes=1048576    # 최대 버퍼 크기 (1MB)
```

### 2단계: 프레임 크기 검증

```java
// HsmsFrameExtractor.java
private void validateFrameSize(int frameLength) {
    if (frameLength > maxFrameBytes) {
        throw new HsmsFrameTooLargeException(
            "HSMS 프레임 크기 초과: " + frameLength + " bytes (최대: " + maxFrameBytes + " bytes)"
        );
    }
    // 프레임 크기 초과 시 → 채널 강제 종료 → 재연결 트리거
}
```

### 3단계: SECS-II 디코딩 (Secs2Decoder)

HSMS 헤더 이후의 데이터는 **SECS-II** 포맷으로 인코딩되어 있습니다.
`Secs2Decoder`가 이진 데이터를 Java 객체로 변환합니다.

```
SECS-II 메시지 예시:
  S6F11 (Equipment Event Report):
    DATAID: 1001
    CEID: 12 (PROCESS_STARTED)
    RPT: [...]

→ Secs2Data 객체로 변환:
  {
    stream: 6,
    function: 11,
    body: {
      "DATAID": 1001,
      "CEID": 12,
      "RPT": [...]
    }
  }
```

### 4단계: eqpId 추출

```java
// HsmsEqpIdExtractor.java
// HSMS 헤더의 SessionID로 설비 조회
public String extract(HsmsFrame frame) {
    int sessionId = frame.getHeader().getSessionId();

    return contextRegistry.findBySessionId(sessionId)
        .map(EquipmentContext::eqpId)
        .orElseGet(() -> {
            // 알 수 없는 SessionID → Quarantine 처리
            quarantineStore.store(frame.toBytes(), "UNKNOWN_SESSION_ID:" + sessionId);
            return null;  // null이면 처리 중단
        });
}
```

---

## SOCKET 인바운드 파이프라인

### 1단계: 프레임 분리

TCP 스트림에서 개별 메시지를 분리합니다.

**LINE_DELIMITED:**
```
수신 스트림: "CMD01\nCMD02\nCMD03\n"
분리 결과:
  프레임 1: "CMD01"
  프레임 2: "CMD02"
  프레임 3: "CMD03"
```

**REGEX_DELIMITED:**
```
수신 스트림: "START{payload1}END\nSTART{payload2}END\n"
패턴: "END\n"
분리 결과:
  프레임 1: "START{payload1}END"
  프레임 2: "START{payload2}END"
```

### 2단계: 크기 검증

LINE/REGEX로 분리된 각 프레임의 크기를 검증합니다.
`max-frame-bytes`를 초과하면 해당 데이터를 Quarantine으로 보냅니다.

### 3단계: 플러그인 디코딩

설비마다 다른 포맷을 처리하기 위해 **플러그인**을 사용합니다.

```java
// SocketInboundPipeline.java
public void process(String eqpId, byte[] frameBytes) {
    // 플러그인 런타임 조회 (없으면 no-op)
    SocketPluginRuntime pluginRuntime = pluginProvider.getRuntime(eqpId);

    if (pluginRuntime == null) {
        // 플러그인 없음 → raw bytes 그대로 이벤트로 처리
        processRawFrame(eqpId, frameBytes);
        return;
    }

    // 플러그인 디코더 실행
    EqpInboundEvent event = pluginRuntime.decoder().decode(frameBytes);

    if (event == null) {
        // 디코딩 불가 → Quarantine
        quarantineStore.store(eqpId, frameBytes, "DECODE_FAILED");
        return;
    }

    // 디코딩 성공 → 메일박스로 전달
    mailboxRegistry.getOrCreate(eqpId).enqueue(event);
}
```

---

## RouteAndPublishUseCase — 이벤트 분류 및 발행

인바운드 파이프라인의 최종 단계입니다.
처리된 이벤트를 어떤 Kafka 토픽으로 발행할지 결정합니다.

```java
@Component
public class RouteAndPublishUseCase {

    public void process(String eqpId, EqpInboundEvent event) {
        // 이벤트 타입별 라우팅
        PublishPolicy policy = publishPolicyEngine.determine(eqpId, event);

        switch (policy.getDestination()) {
            case EQP_EVENTS:
                // 설비 이벤트 → tc.eqp.events
                eqpEventPublisher.publish(eqpId, event);
                break;

            case MES_EVENTS:
                // MES 연동 이벤트 → tc.mes.events (선택 사항)
                mesEventPublisher.publish(eqpId, event);
                break;

            case DISCARD:
                // 발행 안 함 (Linktest 응답 등 내부 처리 완료된 메시지)
                log.debug("이벤트 발행 안 함 (내부 처리): eqpId={}, type={}", eqpId, event.type());
                break;
        }
    }
}
```

---

## 인바운드 큐와 배압(Backpressure)

메일박스 큐에 넣을 수 없을 때(큐 가득 참)의 처리:

```
수신 속도 > 처리 속도 → 큐가 가득 참
                           ↓
              큐 오버플로우 감지
                           ↓
              새로 수신된 메시지 → DLQ 저장
                           ↓
              로그 출력 (설정된 빈도에 따라)
```

```properties
# tc-comm.properties
tc.comm.gateway.inbound-queue-capacity=2048  # 큐 용량
tc.comm.gateway.queue-overflow-log-every=1  # 큐 오버플로우 로그 빈도 (매번)
```

---

## 전체 처리 요약 표

| 단계 | HSMS | SOCKET |
|------|------|--------|
| 프레임 추출 | Length 필드 기반 재조립 | 줄바꿈 또는 정규식 분리 |
| 크기 검증 | Length 필드 사전 확인 | 누적 크기 확인 |
| 디코딩 | SECS-II 표준 디코더 | 플러그인 디코더 |
| eqpId 추출 | HSMS SessionID | IP/Port 또는 첫 프레임 |
| 미매칭 처리 | Quarantine 저장 | Quarantine 저장 |

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **재조립 버퍼 크기** | `reassembly-initial-bytes`를 너무 크게 설정하면 연결된 설비 수만큼 메모리가 소비됩니다 |
| **플러그인 예외 처리** | 플러그인 디코더에서 예외가 발생하면 해당 프레임을 Quarantine으로 처리합니다. 앱이 죽지 않습니다 |
| **큐 오버플로우 모니터링** | 큐 오버플로우 로그가 지속적으로 발생하면 처리 속도를 높여야 합니다 (worker-threads 증가 등) |
| **HSMS Control 메시지** | Linktest, Select, Deselect 등 HSMS 제어 메시지는 `DISCARD` 정책으로 처리됩니다 (Kafka 발행 안 함) |
