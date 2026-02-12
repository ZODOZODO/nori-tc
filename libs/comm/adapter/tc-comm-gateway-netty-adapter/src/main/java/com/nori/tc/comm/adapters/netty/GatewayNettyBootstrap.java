package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.config.GatewayNettyProperties;
import com.nori.tc.comm.gateway.comm.EquipmentInfoProvider;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.kafka.KafkaShardOwnership;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty 부트스트랩 관리 컴포넌트(PASSIVE 서버 + ACTIVE 클라이언트).
 *
 * 동작 개요
 * - BossGroup: 서버 소켓 accept 전용 이벤트 루프
 * - WorkerGroup: 채널 read/write 및 핸들러 실행 이벤트 루프
 * - PASSIVE: 게이트웨이가 포트를 열고 장비 접속을 대기
 * - ACTIVE : DB(tc_eqp) 기준으로 게이트웨이가 장비로 직접 연결
 */
@Component
public class GatewayNettyBootstrap implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GatewayNettyBootstrap.class);

    private final GatewayNettyProperties nettyProperties;
    // 설비 목록/상세를 제공하는 포트(실구현은 DB 어댑터)
    private final EquipmentInfoProvider equipmentInfoProvider;
    private final GatewayChannelHandlerFactory handlerFactory;
    private final KafkaShardOwnership shardOwnership;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel hsmsServerChannel;
    private Channel socketServerChannel;
    private ScheduledExecutorService reconnectScheduler;

    private final ConcurrentHashMap<String, AtomicBoolean> reconnecting = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    
    /**
     * 게이트웨이 Netty 어댑터 구성 요소를 초기화합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param nettyProperties 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param equipmentInfoProvider 도메인 데이터 객체
     * @param handlerFactory 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param shardOwnership 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     */
    public GatewayNettyBootstrap(
            final GatewayNettyProperties nettyProperties,
            final EquipmentInfoProvider equipmentInfoProvider,
            final GatewayChannelHandlerFactory handlerFactory,
            final KafkaShardOwnership shardOwnership
    ) {
        this.nettyProperties = Objects.requireNonNull(nettyProperties, "nettyProperties is null");
        this.equipmentInfoProvider = Objects.requireNonNull(equipmentInfoProvider, "equipmentInfoProvider is null");
        this.handlerFactory = Objects.requireNonNull(handlerFactory, "handlerFactory is null");
        this.shardOwnership = Objects.requireNonNull(shardOwnership, "shardOwnership is null");
    }

    
    /**
     * 게이트웨이 Netty 어댑터 실행 환경을 초기화하고 기동합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     */
    @Override
    public void start() {
        // 라이프사이클 단계: 자원 초기화/해제 순서를 보장합니다.
        if (running) {
            return;
        }
        running = true;

        // Netty 4.2 기준 권장 방식:
        // - NioEventLoopGroup은 deprecated
        // - MultiThreadIoEventLoopGroup + NioIoHandlerFactory 조합 사용
        bossGroup = new MultiThreadIoEventLoopGroup(
                nettyProperties.getBossThreads(),
                NioIoHandler.newFactory()
        );
        workerGroup = new MultiThreadIoEventLoopGroup(
                nettyProperties.getWorkerThreads(),
                NioIoHandler.newFactory()
        );
        reconnectScheduler = Executors.newScheduledThreadPool(nettyProperties.getReconnectSchedulerThreads());

        log.info("GatewayNettyBootstrap starting. bossThreads={}, workerThreads={}, reconnectSchedulerThreads={}",
                nettyProperties.getBossThreads(),
                nettyProperties.getWorkerThreads(),
                nettyProperties.getReconnectSchedulerThreads());

        startServers();
        startActiveConnections();
    }

    
    /**
     * 게이트웨이 Netty 어댑터 리소스를 정리하고 종료합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     */
    @Override
    public void stop() {
        // 라이프사이클 단계: 자원 초기화/해제 순서를 보장합니다.
        running = false;

        log.info("GatewayNettyBootstrap stopping.");
        safeClose(hsmsServerChannel);
        safeClose(socketServerChannel);

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdown();
        }
        log.info("GatewayNettyBootstrap stopped.");
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 처리 성공 여부
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    
    /**
     * 게이트웨이 Netty 어댑터의 현재 값을 조회합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    @Override
    public int getPhase() {
        return 0;
    }

    
    /**
     * 게이트웨이 Netty 어댑터 실행 환경을 초기화하고 기동합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     */
    private void startServers() {
        // PASSIVE 서버 흐름:
        // - 인터페이스별 포트 bind/listen
        // - accept 즉시 GatewayChannelHandler(UNBOUND) 연결
        // - 등록 메시지 수신 후 bind 및 mailbox 생성
        hsmsServerChannel = startServer(nettyProperties.getHsmsBindPort(), CommInterfaceType.HSMS);
        socketServerChannel = startServer(nettyProperties.getSocketBindPort(), CommInterfaceType.SOCKET);
    }

    
    /**
     * 게이트웨이 Netty 어댑터 실행 환경을 초기화하고 기동합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param port 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @param interfaceType 게이트웨이 Netty 어댑터 처리에 사용하는 입력 값
     * @return 게이트웨이 Netty 어댑터 처리 결과
     */
    private Channel startServer(final int port, final CommInterfaceType interfaceType) {
        // 라이프사이클 단계: 자원 초기화/해제 순서를 보장합니다.
        try {
            final ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(final SocketChannel ch) {
                            ch.pipeline().addLast(handlerFactory.newPassiveHandler(interfaceType));
                        }
                    });

            final ChannelFuture future = bootstrap.bind(port).sync();
            log.info("Netty server started: {} on port {}", interfaceType, port);
            return future.channel();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Netty server bind interrupted", ex);
        }
    }

    
    /**
     * 게이트웨이 Netty 어댑터 실행 환경을 초기화하고 기동합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     */
    private void startActiveConnections() {
        // ACTIVE 연결 흐름:
        // - 샤드 소유권이 있는 eqpId만 연결 시도
        // - channelActive 시점에 eqpId를 이미 알고 있으므로 즉시 BOUND 처리
        final List<GatewayEquipmentInfo> equipmentList = equipmentInfoProvider.findAll();
        log.info("Active connection bootstrap started. totalEquipments={}", equipmentList.size());

        for (GatewayEquipmentInfo info : equipmentList) {
            if (!info.enabled()) {
                continue;
            }
            if (info.connectionMode() == null) {
                continue;
            }
            if (info.connectionMode() == ConnectionMode.ACTIVE) {
                if (!shardOwnership.isOwned(info.equipmentId())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Active connect skipped (not owned). eqpId={}", info.equipmentId());
                    }
                    continue;
                }
                connectActive(info);
            }
        }
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param info 도메인 데이터 객체
     */
    private void connectActive(final GatewayEquipmentInfo info) {
        // 연결 제어 단계: 상태 전이와 예외 케이스를 함께 관리합니다.
        final String eqpId = info.equipmentId();
        if (info.eqpIp() == null || info.eqpIp().isBlank()) {
            log.warn("Active connect skipped (missing eqpIp). eqpId={}", eqpId);
            return;
        }
        if (info.eqpPort() == null || info.eqpPort() <= 0) {
            log.warn("Active connect skipped (invalid eqpPort). eqpId={}", eqpId);
            return;
        }

        // ACTIVE 연결: 설비 IP/PORT로 GW가 connect
        final Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, nettyProperties.getConnectTimeoutMillis())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        ch.pipeline().addLast(handlerFactory.newActiveHandler(info.commInterfaceType(), eqpId));
                    }
                });

        if (log.isDebugEnabled()) {
            log.debug("Active connect attempt. eqpId={}, ip={}, port={}", eqpId, info.eqpIp(), info.eqpPort());
        }
        bootstrap.connect(info.eqpIp(), info.eqpPort()).addListener((ChannelFuture f) -> {
            if (!f.isSuccess()) {
                log.warn("Active connect failed. eqpId={}, {}", eqpId, f.cause() == null ? "" : f.cause().getMessage());
                scheduleReconnect(info);
                return;
            }
            log.info("Active connect success. eqpId={}", eqpId);
            f.channel().closeFuture().addListener(cf -> scheduleReconnect(info));
        });
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param info 도메인 데이터 객체
     */
    private void scheduleReconnect(final GatewayEquipmentInfo info) {
        // 연결 제어 단계: 상태 전이와 예외 케이스를 함께 관리합니다.
        final String eqpId = info.equipmentId();
        final AtomicBoolean flag = reconnecting.computeIfAbsent(eqpId, key -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Scheduling active reconnect. eqpId={}, delayMs={}", eqpId, nettyProperties.getActiveReconnectDelayMs());
        }
        reconnectScheduler.schedule(GatewayLogContext.wrap(() -> {
            try {
                connectActive(info);
            } finally {
                flag.set(false);
            }
        }), nettyProperties.getActiveReconnectDelayMs(), TimeUnit.MILLISECONDS);
    }

    
    /**
     * 게이트웨이 Netty 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>채널 상태, 이벤트 루프 컨텍스트, 프레임 처리 규칙을 기준으로 동작합니다.</p>
     * @param channel 통신 채널/세션 정보
     */
    private void safeClose(final Channel channel) {
        if (channel != null) {
            channel.close();
        }
    }
}
