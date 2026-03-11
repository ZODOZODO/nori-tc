# 04. Netty 기반 장비 TCP 통신 (Netty TCP Communication)

## 개요

`tc-comm-gateway-app`은 **Netty** 프레임워크를 사용해서 설비(Equipment)와 TCP 통신을 합니다.

Netty는 고성능 비동기 네트워크 프레임워크입니다.
일반적인 `Socket`, `ServerSocket` 대신 Netty를 사용하는 이유는
수백 개의 설비와 동시에 통신할 때 성능과 안정성이 훨씬 뛰어나기 때문입니다.

---

## TCP 연결 모드

설비와의 연결 방식은 두 가지가 있습니다.

### PASSIVE 모드 (설비가 접속)

```
Gateway:  포트 5000을 열고 기다림 (ServerBootstrap.bind(5000))
설비:     Gateway의 IP:5000으로 TCP 접속

Gateway (서버 역할)  ←──TCP 연결──  설비 (클라이언트 역할)
```

**언제 사용하는가:**
- 설비가 고정 IP를 가지지 않거나 자주 바뀌는 경우
- 설비 측에서 연결 시작 로직이 이미 구현된 경우
- HSMS 프로토콜의 Passive 모드 (표준 사양)

### ACTIVE 모드 (Gateway가 접속)

```
설비:     포트 5000을 열고 기다림
Gateway:  설비의 IP:5000으로 TCP 접속 (Bootstrap.connect(eqpHost, 5000))

Gateway (클라이언트 역할)  ──TCP 연결──→  설비 (서버 역할)
```

**언제 사용하는가:**
- 설비 IP와 포트가 고정된 경우
- Gateway가 능동적으로 연결을 시작해야 하는 경우
- HSMS 프로토콜의 Active 모드 (표준 사양)

---

## 핵심 컴포넌트

### GatewayNettyBootstrap

Netty 전체를 조율하는 메인 컴포넌트입니다.

```java
@Component
public class GatewayNettyBootstrap implements SmartLifecycle {

    private NioEventLoopGroup bossGroup;    // 연결 수락 전담 (PASSIVE 모드용)
    private NioEventLoopGroup workerGroup;  // I/O 처리 전담

    @Override
    public void start() {
        // 1. EventLoop 그룹 생성
        bossGroup = new NioEventLoopGroup(1);    // PASSIVE: 연결 수락 1개 스레드
        workerGroup = new NioEventLoopGroup(4);  // I/O 처리 4개 스레드

        // 2. enabled 설비에 대해 연결 시작
        contextRegistry.findAllEnabled().forEach(ctx -> {
            eqpBindingService.startBinding(ctx);
        });
    }

    @Override
    public void stop() {
        // 모든 채널 종료 후 EventLoop 종료
        channelRegistry.closeAll();
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }
}
```

**설정:**
```properties
# tc-comm.properties
tc.comm.gateway.netty.boss-threads=1      # 연결 수락 스레드 (PASSIVE 모드)
tc.comm.gateway.netty.worker-threads=4   # I/O 처리 스레드
```

### EqpBindingService

설비별 TCP 연결을 시작/종료합니다.

```java
@Component
public class EqpBindingService {

    /**
     * 설비 연결 시작
     * - PASSIVE: ServerBootstrap으로 포트 바인딩
     * - ACTIVE: Bootstrap으로 설비에 직접 연결
     */
    public void startBinding(EquipmentContext context) {
        if (context.connectionMode() == ConnectionMode.PASSIVE) {
            bindPassive(context);
        } else {
            connectActive(context);
        }
    }

    private void bindPassive(EquipmentContext context) {
        ServerBootstrap serverBootstrap = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(channelHandlerFactory.create(context));

        ChannelFuture future = serverBootstrap.bind(context.listenPort());
        future.addListener(f -> {
            if (f.isSuccess()) {
                log.info("PASSIVE 바인딩 성공: eqpId={}, port={}", context.eqpId(), context.listenPort());
            } else {
                log.error("PASSIVE 바인딩 실패: eqpId={}", context.eqpId(), f.cause());
                bindAttemptExecutor.scheduleRetry(context);  // 재시도 스케줄
            }
        });
    }

    private void connectActive(EquipmentContext context) {
        Bootstrap bootstrap = new Bootstrap()
            .group(workerGroup)
            .channel(NioSocketChannel.class)
            .handler(channelHandlerFactory.create(context));

        ChannelFuture future = bootstrap.connect(context.eqpHost(), context.eqpPort());
        future.addListener(f -> {
            if (f.isSuccess()) {
                log.info("ACTIVE 연결 성공: eqpId={}, {}:{}", context.eqpId(), context.eqpHost(), context.eqpPort());
            } else {
                log.warn("ACTIVE 연결 실패: eqpId={}, 재시도 예정", context.eqpId());
                bindAttemptExecutor.scheduleRetry(context);
            }
        });
    }
}
```

### BindAttemptExecutor — 재연결 스케줄러

연결 실패 시 자동으로 재시도합니다.

```java
@Component
public class BindAttemptExecutor {

    /**
     * 연결 실패 후 재시도 스케줄
     * - 3초 후 다시 연결 시도
     * - 최대 3회 연속 실패 시 포기
     */
    public void scheduleRetry(EquipmentContext context) {
        int consecutiveFailures = context.incrementFailureCount();

        if (consecutiveFailures >= maxConnectFailures) {
            log.warn("연속 {}회 연결 실패. 재시도 중단: eqpId={}", maxConnectFailures, context.eqpId());
            stateMachine.onBindingAbandoned(context.eqpId());  // DesiredState = ENDED
            return;
        }

        log.info("{}초 후 재연결 시도: eqpId={} ({}회째 실패)",
                 reconnectDelaySeconds, context.eqpId(), consecutiveFailures);

        scheduler.schedule(
            () -> eqpBindingService.startBinding(context),
            reconnectDelaySeconds,
            TimeUnit.SECONDS
        );
    }
}
```

**설정:**
```properties
# tc-comm.properties
tc.comm.gateway.netty.reconnect-delay-seconds=3   # 재연결 대기 시간
tc.comm.gateway.netty.max-connect-failures=3      # 최대 연속 실패 횟수
tc.comm.gateway.netty.bind-timeout-seconds=30     # 바인딩 타임아웃
tc.comm.gateway.netty.connect-timeout-seconds=5   # 연결 타임아웃
```

### GatewayChannelHandler

채널에서 데이터를 수신하고 처리합니다.

```java
@ChannelHandler.Sharable
public class GatewayChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // TCP 연결 성공 → 상태머신에 CHANNEL_CONNECTED 이벤트 전달
        String eqpId = extractEqpId(ctx.channel());
        stateMachine.onChannelConnected(eqpId, wrapChannel(ctx.channel()));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // TCP 연결 끊김 → 상태머신에 CHANNEL_DISCONNECTED 이벤트 전달
        String eqpId = getEqpId(ctx.channel());
        stateMachine.onChannelDisconnected(eqpId);

        // DesiredState가 STARTED면 재연결 시도
        if (contextRegistry.getDesiredState(eqpId) == DesiredState.STARTED) {
            bindAttemptExecutor.scheduleRetry(contextRegistry.get(eqpId));
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        // 데이터 수신 → 인바운드 파이프라인으로 전달
        byte[] bytes = toByteArray(msg);
        inboundPipeline.process(getEqpId(ctx.channel()), bytes);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String eqpId = getEqpId(ctx.channel());
        log.error("채널 예외: eqpId={}", eqpId, cause);
        ctx.close();  // 예외 발생 시 채널 종료 → channelInactive 트리거
    }
}
```

---

## NettyEquipmentChannel

Netty 채널을 앱의 도메인 개념으로 감싸는 래퍼 클래스입니다.

```java
public class NettyEquipmentChannel implements EquipmentChannel {

    private final Channel nettyChannel;     // 실제 Netty 채널
    private final String eqpId;            // 설비 ID
    private final Protocol protocol;       // HSMS or SOCKET
    private final ConnectionMode mode;     // PASSIVE or ACTIVE

    /**
     * 설비에 메시지 전송
     */
    @Override
    public void send(byte[] data) {
        if (!nettyChannel.isActive()) {
            throw new ChannelNotActiveException("채널이 활성화 상태가 아닙니다: " + eqpId);
        }
        nettyChannel.writeAndFlush(Unpooled.wrappedBuffer(data));
    }

    /**
     * 채널 연결 상태 확인
     */
    @Override
    public boolean isConnected() {
        return nettyChannel.isActive();
    }
}
```

---

## Netty Channel Attributes

Netty 채널에는 사용자 정의 속성을 저장할 수 있습니다.
Gateway는 채널에 설비 관련 정보를 저장해서 핸들러에서 쉽게 접근합니다.

```java
// NettyChannelAttributes.java — 채널 속성 키 상수 정의
public final class NettyChannelAttributes {

    // 채널에 연결된 설비 ID
    public static final AttributeKey<String> EQP_ID =
        AttributeKey.valueOf("eqpId");

    // 설비의 통신 프로토콜 (HSMS or SOCKET)
    public static final AttributeKey<Protocol> PROTOCOL =
        AttributeKey.valueOf("protocol");

    // 연결 모드 (PASSIVE or ACTIVE)
    public static final AttributeKey<ConnectionMode> CONNECTION_MODE =
        AttributeKey.valueOf("connectionMode");
}

// 사용 예시 (채널 핸들러 내부)
String eqpId = ctx.channel().attr(NettyChannelAttributes.EQP_ID).get();
Protocol protocol = ctx.channel().attr(NettyChannelAttributes.PROTOCOL).get();
```

---

## EventLoop 구조

Netty는 **EventLoop** 를 사용해 비동기적으로 채널을 처리합니다.

```
┌───────────────────────────────────────────────────────────────┐
│                    Boss EventLoop Group                       │
│                                                               │
│  EventLoop 1: 새 연결 수락 전담 (PASSIVE 모드)               │
└───────────────────────────┬───────────────────────────────────┘
                            │ 새 연결 발생 시 Worker에 위임
┌───────────────────────────────────────────────────────────────┐
│                   Worker EventLoop Group                      │
│                                                               │
│  EventLoop 1: EQP-001, EQP-005, EQP-009 채널 담당           │
│  EventLoop 2: EQP-002, EQP-006, EQP-010 채널 담당           │
│  EventLoop 3: EQP-003, EQP-007, EQP-011 채널 담당           │
│  EventLoop 4: EQP-004, EQP-008, EQP-012 채널 담당           │
│                                                               │
│  → 각 EventLoop는 단일 스레드                                │
│  → 같은 채널은 항상 같은 EventLoop에서 처리                  │
└───────────────────────────────────────────────────────────────┘
```

---

## 연결 흐름 요약

```
ACTIVE 모드 연결 성공 흐름:

1. GatewayNettyBootstrap.start()
2. EqpBindingService.connectActive(EQP-001)
3. Bootstrap.connect(192.168.1.100, 5000)
4. TCP 3-way handshake 완료
5. GatewayChannelHandler.channelActive()
6. NettyChannelAttributes에 eqpId="EQP-001" 저장
7. NettyEquipmentChannel 생성
8. EquipmentChannelRegistry.register("EQP-001", channel)
9. EquipmentLifecycleStateMachine.onChannelConnected("EQP-001")
10. RuntimeState = CONNECTED
11. (HSMS면) HSMS Select 절차 시작
```

---

## 주의사항

| 항목 | 내용 |
|------|------|
| **EventLoop 스레드에서 블로킹 금지** | Netty EventLoop 스레드에서 블로킹 작업(DB 조회, 긴 연산 등)을 하면 모든 채널이 멈춥니다. 무거운 작업은 별도 스레드로 위임하세요 |
| **채널 write는 Netty가 관리** | `channel.writeAndFlush()` 이후 데이터가 즉시 전송되지 않을 수 있습니다. 전송 완료 확인이 필요하면 ChannelFuture를 사용하세요 |
| **MAX FRAME SIZE** | 최대 프레임 크기(기본 256KB)를 초과하는 메시지는 거절합니다. OOM(Out of Memory) 방지를 위한 설정입니다 |
| **PASSIVE 포트 충돌** | 여러 설비가 같은 포트를 사용하면 바인딩 실패합니다. 설비별로 다른 포트를 사용하세요 |
| **TIME_WAIT** | TCP 연결이 자주 끊어지면 OS 레벨에서 TIME_WAIT 소켓이 쌓일 수 있습니다. `SO_LINGER` 또는 `SO_REUSEADDR` 설정을 검토하세요 |
