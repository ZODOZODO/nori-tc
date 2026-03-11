# 05. 프로토콜 추상화 (HSMS / SOCKET Protocol Abstraction)

## 개요

`tc-comm-gateway-app`은 두 가지 통신 프로토콜을 지원합니다.

- **HSMS (High-Speed Message Services)**: 반도체 장비 통신 표준 (SEMI E37)
- **SOCKET**: 사용자 정의 TCP 기반 프로토콜

두 프로토콜은 메시지 포맷이 완전히 다르지만,
Gateway 내부에서는 **동일한 인터페이스(Port)** 로 처리됩니다.
프로토콜에 따라 적절한 파이프라인으로 **자동 분기**되는 구조입니다.

---

## HSMS 프로토콜

### HSMS란?

**HSMS(High-Speed Message Services)** 는 반도체 제조 장비와 호스트 시스템 간의
TCP/IP 기반 통신 표준입니다. (SEMI 표준 E37)

주로 SECS-II(SEMI E5) 메시지를 전달하는 전송 계층으로 사용됩니다.

### HSMS 프레임 구조

```
┌────────────────────────────────────────────────────────────────┐
│                      HSMS 프레임                               │
│                                                                │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │        Length (4 bytes)                                 │   │
│  │        전체 메시지 길이 (헤더 + 데이터)                 │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │        HSMS Header (10 bytes)                           │   │
│  │  ┌──────────┬──────────┬──────────┬──────────────────┐  │   │
│  │  │SessionID │  Byte2   │  Byte3   │  SystemBytes     │  │   │
│  │  │(2 bytes) │(1 byte)  │(1 byte)  │  (4 bytes)       │  │   │
│  │  └──────────┴──────────┴──────────┴──────────────────┘  │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │        SECS-II Data (가변 길이)                         │   │
│  │        실제 메시지 내용 (S6F11 등)                      │   │
│  └─────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
```

### HSMS 타이머

HSMS는 연결 상태와 메시지 전달을 보장하기 위해 여러 타이머를 사용합니다.

| 타이머 | 기본값 | 용도 |
|--------|--------|------|
| T3 | 45초 | Reply 타임아웃 (응답 대기 최대 시간) |
| T5 | 10초 | Connect 타임아웃 (연결 시도 최대 시간) |
| T6 | 5초 | Control Transaction 타임아웃 |
| T7 | 10초 | Not Selected 타임아웃 (Select 절차 완료 대기) |
| T8 | 5초 | Network Inter-character Timeout |

### HSMS Linktest

연결 상태를 주기적으로 확인합니다.

```
Gateway                    설비
    │                        │
    │── Linktest.req ────────→│  (30초마다 전송)
    │←── Linktest.rsp ────────│  (응답)
    │                        │
    │  응답이 없으면: 연결 끊김으로 판단 → 재연결
```

```properties
# tc-comm.properties
tc.comm.gateway.hsms.t3-seconds=45
tc.comm.gateway.hsms.t5-seconds=10
tc.comm.gateway.hsms.t6-seconds=5
tc.comm.gateway.hsms.t7-seconds=10
tc.comm.gateway.hsms.t8-seconds=5
tc.comm.gateway.hsms.linktest-enabled=true
tc.comm.gateway.hsms.linktest-interval-seconds=30
tc.comm.gateway.hsms.max-frame-bytes=262144   # 256KB
```

---

## SOCKET 프로토콜

### SOCKET이란?

설비마다 다른 **사용자 정의 TCP 프로토콜**입니다.
HSMS처럼 표준화된 포맷이 없고, 설비 제조사가 자체적으로 정의합니다.

### 프레임 분리 방식

TCP는 스트림 기반이므로 수신된 bytes가 하나의 메시지인지 여러 메시지인지 알 수 없습니다.
SOCKET 프로토콜은 **메시지의 경계를 구분하는 방법**에 따라 두 가지 타입이 있습니다.

#### LINE_DELIMITED (줄바꿈 구분)

```
수신 바이트 스트림: "CMD:01\nCMD:02\nSTATUS\n"

→ 줄바꿈(\n)으로 분리:
   메시지 1: "CMD:01"
   메시지 2: "CMD:02"
   메시지 3: "STATUS"
```

#### REGEX_DELIMITED (정규식 구분)

```
수신 바이트 스트림: "DATA_START{...}END\nDATA_START{...}END\n"

→ 정규식 패턴 "END\n"으로 분리:
   메시지 1: "DATA_START{...}END"
   메시지 2: "DATA_START{...}END"
```

```properties
# tc-comm.properties
tc.comm.gateway.socket.default-socket-type=LINE_DELIMITED
tc.comm.gateway.socket.regex-pattern=END\n      # REGEX_DELIMITED 사용 시
tc.comm.gateway.socket.max-frame-bytes=262144   # 256KB
```

---

## 프로토콜 추상화 구조

두 프로토콜을 같은 방식으로 처리하기 위해 **추상 인터페이스**를 사용합니다.

```
                   TCP 데이터 수신
                         │
                GatewayChannelHandler
                         │
                         ↓
           ProtocolInboundPipelineRouter
           (프로토콜 감지 → 파이프라인 선택)
                    │         │
                   HSMS    SOCKET
                    ↓         ↓
         HsmsInboundPipeline  SocketInboundPipeline
                    │         │
                    └────┬────┘
                         ↓
              InboundPipelinePort (공통 인터페이스)
                         │
                         ↓
              EqpSequentialProcessor (공통 처리)
```

### ProtocolInboundPipelineRouter

수신된 채널의 프로토콜을 확인하고 적절한 파이프라인으로 라우팅합니다.

```java
@Component
public class ProtocolInboundPipelineRouter implements InboundPipelinePort {

    @Override
    public void process(String eqpId, byte[] data) {
        Protocol protocol = contextRegistry.getProtocol(eqpId);

        switch (protocol) {
            case HSMS:
                hsmsPipeline.process(eqpId, data);
                break;
            case SOCKET:
                socketPipeline.process(eqpId, data);
                break;
            default:
                log.warn("알 수 없는 프로토콜: eqpId={}, protocol={}", eqpId, protocol);
        }
    }
}
```

---

## 프로토콜별 eqpId 추출 방식

연결이 수립될 때, Gateway는 어떤 설비가 연결됐는지 알아야 합니다.
HSMS와 SOCKET은 eqpId를 추출하는 방법이 다릅니다.

### HSMS: SessionID로 eqpId 추출

```java
// HsmsEqpIdExtractor.java
// HSMS 헤더의 SessionID를 설비 ID로 사용
public String extract(byte[] frame) {
    int sessionId = ((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF);
    return equipmentRegistry.findBySessionId(sessionId)
        .map(EquipmentContext::eqpId)
        .orElseThrow(() -> new UnknownEquipmentException("SessionID: " + sessionId));
}
```

### SOCKET: 첫 프레임 또는 설정에서 eqpId 추출

```java
// SocketEqpIdExtractor.java
// 옵션 1: 첫 번째 프레임의 특정 위치에서 eqpId 추출
// 옵션 2: 연결된 IP/Port로 설비 조회
public String extract(ChannelHandlerContext ctx, byte[] firstFrame) {
    // IP:Port 기반 조회
    InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
    String remoteIp = remoteAddress.getAddress().getHostAddress();

    return equipmentRegistry.findByIp(remoteIp)
        .map(EquipmentContext::eqpId)
        .orElseThrow(() -> new UnknownEquipmentException("IP: " + remoteIp));
}
```

---

## 프레임 크기 제한

```
수신 중인 bytes가 max-frame-bytes를 초과하면?

HSMS:
  HsmsFrameExtractor가 Length 필드를 읽어서 미리 크기 확인
  256KB 초과 → IllegalArgumentException → 채널 종료

SOCKET:
  SocketFrameExtractor가 현재까지 누적된 bytes 확인
  256KB 초과 → 프레임 포기 → Quarantine 저장

이유:
  OOM(Out of Memory) 방지
  악의적인 데이터나 버그로 인한 무한 데이터 수신 방지
```

```properties
tc.comm.gateway.hsms.max-frame-bytes=262144    # 256KB (기본값)
tc.comm.gateway.socket.max-frame-bytes=262144  # 256KB (기본값)
```

---

## 지원 프로토콜 요약

| 항목 | HSMS | SOCKET |
|------|------|--------|
| 표준 | SEMI E37 | 사용자 정의 |
| 용도 | 반도체 장비 | 다양한 설비 |
| eqpId 추출 | HSMS 헤더 SessionID | IP/Port 또는 첫 프레임 |
| 프레임 구분 | 4바이트 Length 필드 | 줄바꿈 또는 정규식 |
| 연결 관리 | T3~T8 타이머, Linktest | 연결 감지만 |
| 플러그인 | 불필요 (표준화) | 필요 (인코더/디코더 커스텀) |
| 최대 프레임 | 256KB | 256KB |

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **프로토콜 혼재 불가** | 하나의 설비는 HSMS 또는 SOCKET 중 하나의 프로토콜만 사용합니다. 혼재는 지원하지 않습니다 |
| **SOCKET 플러그인 필수** | 인코딩/디코딩 로직이 없는 SOCKET 설비는 플러그인이 없으면 raw bytes만 통과합니다 |
| **HSMS Select 절차** | HSMS 연결 직후 Select 절차가 완료되어야 실제 메시지 교환이 가능합니다. T7 타임아웃 이내에 완료되지 않으면 연결이 종료됩니다 |
| **정규식 패턴 주의** | REGEX_DELIMITED의 `regex-pattern`이 잘못 설정되면 메시지가 분리되지 않고 계속 쌓입니다. max-frame-bytes 초과로 이어집니다 |
