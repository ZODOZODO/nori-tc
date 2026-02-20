package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentInfoProvider;
import com.nori.tc.comm.gateway.comm.GatewayConnectionControlPort;
import com.nori.tc.comm.gateway.config.GatewayNettyProperties;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.kafka.KafkaShardOwnership;
import com.nori.tc.comm.gateway.lifecycle.EqpLifecycleStateMachine;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gateway Netty 서버/클라이언트 부트스트랩을 관리하는 컴포넌트입니다.
 *
 * <p>핵심 책임은 다음과 같습니다.</p>
 * <p>1) HSMS/SOCKET 수신 서버 포트를 열고 채널 파이프라인을 구성합니다.</p>
 * <p>2) 설비 모드가 PASSIVE인 경우(게이트웨이가 접속 주체) 아웃바운드 연결을 시도합니다.</p>
 * <p>3) 아웃바운드 연속 실패 횟수를 추적해 임계치 도달 시 자동 재연결을 중단합니다.</p>
 *
 * <p>주의: 메서드명의 "Active"는 기존 인터페이스 호환 용어이며,
 * 실제 의미는 "게이트웨이에서 아웃바운드 연결을 수행"입니다.</p>
 */
@Component
public class GatewayNettyBootstrap implements SmartLifecycle, GatewayConnectionControlPort {

    private static final Logger log = LoggerFactory.getLogger(GatewayNettyBootstrap.class);

    private final GatewayNettyProperties nettyProperties;
    private final EquipmentInfoProvider equipmentInfoProvider;
    private final GatewayChannelHandlerFactory handlerFactory;
    private final KafkaShardOwnership shardOwnership;
    private final EqpLifecycleStateMachine lifecycleStateMachine;

    /**
     * Netty 서버/클라이언트 리소스입니다.
     */
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel hsmsServerChannel;
    private Channel socketServerChannel;
    private ScheduledExecutorService reconnectScheduler;

    /**
     * 동일 설비에 대한 중복 재연결 스케줄 등록을 방지하는 플래그 맵입니다.
     */
    private final ConcurrentHashMap<String, AtomicBoolean> reconnecting = new ConcurrentHashMap<>();

    /**
     * 운영 제어로 재연결을 억제한 설비 목록입니다.
     */
    private final Set<String> reconnectSuppressedEqpIds = ConcurrentHashMap.newKeySet();

    /**
     * 설비별 아웃바운드 연결 연속 실패 횟수입니다.
     */
    private final ConcurrentHashMap<String, AtomicInteger> consecutiveOutboundFailures = new ConcurrentHashMap<>();

    /**
     * 게이트웨이 런타임 실행 상태입니다.
     */
    private volatile boolean running = false;

    /**
     * 의존 컴포넌트를 주입받아 부트스트랩을 초기화합니다.
     *
     * @param nettyProperties Netty 동작 속성
     * @param equipmentInfoProvider 설비 조회 포트
     * @param handlerFactory 채널 핸들러 팩토리
     * @param shardOwnership 샤드 소유권 판별기
     * @param lifecycleStateMachine 설비 라이프사이클 상태 머신
     */
    public GatewayNettyBootstrap(
            final GatewayNettyProperties nettyProperties,
            final EquipmentInfoProvider equipmentInfoProvider,
            final GatewayChannelHandlerFactory handlerFactory,
            final KafkaShardOwnership shardOwnership,
            final EqpLifecycleStateMachine lifecycleStateMachine
    ) {
        this.nettyProperties = Objects.requireNonNull(nettyProperties, "nettyProperties is null");
        this.equipmentInfoProvider = Objects.requireNonNull(equipmentInfoProvider, "equipmentInfoProvider is null");
        this.handlerFactory = Objects.requireNonNull(handlerFactory, "handlerFactory is null");
        this.shardOwnership = Objects.requireNonNull(shardOwnership, "shardOwnership is null");
        this.lifecycleStateMachine = Objects.requireNonNull(lifecycleStateMachine, "lifecycleStateMachine is null");
    }

    /**
     * SmartLifecycle 시작 시 Netty 리소스를 기동합니다.
     *
     * <p>서버 바인딩과 초기 아웃바운드 연결 부팅을 순서대로 수행합니다.</p>
     */
    @Override
    public void start() {
        if (running) {
            if (log.isDebugEnabled()) {
                log.debug("GatewayNettyBootstrap start skipped because it is already running.");
            }
            return;
        }
        running = true;

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
     * SmartLifecycle 종료 시 Netty 리소스를 정리합니다.
     */
    @Override
    public void stop() {
        if (!running) {
            if (log.isDebugEnabled()) {
                log.debug("GatewayNettyBootstrap stop skipped because it is already stopped.");
            }
            return;
        }
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

        reconnecting.clear();
        reconnectSuppressedEqpIds.clear();
        consecutiveOutboundFailures.clear();

        log.info("GatewayNettyBootstrap stopped.");
    }

    /**
     * 현재 실행 상태를 반환합니다.
     *
     * @return 실행 중이면 true
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * SmartLifecycle phase 값을 반환합니다.
     *
     * @return lifecycle phase
     */
    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * 특정 설비에 대한 아웃바운드 연결을 즉시 시도합니다.
     *
     * <p>이 메서드는 운영 제어(UI 등)에서 START 요청 시 호출됩니다.</p>
     *
     * @param eqpId 설비 ID
     */
    @Override
    public void connectActiveIfPossible(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        withEqpLogContext(eqpId, () -> {
            if (!running) {
                log.warn("Outbound connect skipped (gateway not running). eqpId={}", eqpId);
                return;
            }
            if (!shardOwnership.isOwned(eqpId)) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound connect skipped (not owned). eqpId={}", eqpId);
                }
                return;
            }

            final GatewayEquipmentInfo info = equipmentInfoProvider.findById(eqpId).orElse(null);
            if (info == null) {
                log.warn("Outbound connect skipped (equipment not found). eqpId={}", eqpId);
                return;
            }
            if (!info.enabled()) {
                log.warn("Outbound connect skipped (equipment disabled). eqpId={}", eqpId);
                return;
            }
            if (info.connectionMode() != ConnectionMode.PASSIVE) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound connect skipped (equipment mode is not PASSIVE). eqpId={}, mode={}",
                            eqpId,
                            info.connectionMode());
                }
                return;
            }

            reconnectSuppressedEqpIds.remove(eqpId);
            resetOutboundFailureCounter(eqpId, "manual connect request");
            log.info("Outbound connect requested by runtime control. eqpId={}", eqpId);
            connectOutbound(info);
        });
    }

    /**
     * 특정 설비의 자동 재연결을 억제합니다.
     *
     * @param eqpId 설비 ID
     */
    @Override
    public void suppressActiveReconnect(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        withEqpLogContext(eqpId, () -> {
            reconnectSuppressedEqpIds.add(eqpId);
            log.info("Outbound reconnect suppressed. eqpId={}", eqpId);
        });
    }

    /**
     * 특정 설비의 자동 재연결 억제를 해제합니다.
     *
     * @param eqpId 설비 ID
     */
    @Override
    public void resumeActiveReconnect(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        withEqpLogContext(eqpId, () -> {
            reconnectSuppressedEqpIds.remove(eqpId);
            resetOutboundFailureCounter(eqpId, "manual reconnect resume");
            log.info("Outbound reconnect resumed. eqpId={}", eqpId);
        });
    }

    /**
     * HSMS/SOCKET 수신 서버를 시작합니다.
     */
    private void startServers() {
        hsmsServerChannel = startServer(nettyProperties.getHsmsBindPort(), CommInterfaceType.HSMS);
        socketServerChannel = startServer(nettyProperties.getSocketBindPort(), CommInterfaceType.SOCKET);
    }

    /**
     * 단일 인터페이스 타입 서버를 시작합니다.
     *
     * @param port 바인딩 포트
     * @param interfaceType 인터페이스 타입
     * @return 서버 채널
     */
    private Channel startServer(final int port, final CommInterfaceType interfaceType) {
        try {
            final ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        /**
                         * 수신 채널 파이프라인에 PASSIVE 핸들러를 등록합니다.
                         */
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
     * 부팅 시 DB의 설비 목록을 기준으로 초기 아웃바운드 연결을 시작합니다.
     *
     * <p>연결 대상은 enabled=true 이고 connectionMode=PASSIVE 이며, 현재 샤드 소유인 설비입니다.</p>
     */
    private void startActiveConnections() {
        final List<GatewayEquipmentInfo> equipmentList = equipmentInfoProvider.findAll();
        log.info("Outbound connection bootstrap started. totalEquipments={}", equipmentList.size());

        for (GatewayEquipmentInfo info : equipmentList) {
            if (!info.enabled() || info.connectionMode() == null) {
                continue;
            }
            if (info.connectionMode() != ConnectionMode.PASSIVE) {
                continue;
            }

            final String eqpId = info.equipmentId();
            withEqpLogContext(eqpId, () -> {
                if (!shardOwnership.isOwned(eqpId)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Outbound connect skipped (not owned). eqpId={}", eqpId);
                    }
                    return;
                }
                if (reconnectSuppressedEqpIds.contains(eqpId)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Outbound connect skipped (suppressed). eqpId={}", eqpId);
                    }
                    return;
                }
                connectOutbound(info);
            });
        }
    }

    /**
     * 단일 설비에 대해 아웃바운드 TCP 연결을 시도합니다.
     *
     * @param info 설비 정보
     */
    private void connectOutbound(final GatewayEquipmentInfo info) {
        final String eqpId = info.equipmentId();
        withEqpLogContext(eqpId, () -> {
            if (!running) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound connect skipped because gateway is stopping or stopped. eqpId={}", eqpId);
                }
                return;
            }
            if (workerGroup == null) {
                log.warn("Outbound connect skipped because workerGroup is not initialized. eqpId={}", eqpId);
                return;
            }
            if (reconnectSuppressedEqpIds.contains(eqpId)) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound connect skipped (suppressed). eqpId={}", eqpId);
                }
                return;
            }
            if (info.eqpIp() == null || info.eqpIp().isBlank()) {
                log.warn("Outbound connect skipped (missing eqpIp). eqpId={}", eqpId);
                return;
            }
            if (info.eqpPort() == null || info.eqpPort() <= 0) {
                log.warn("Outbound connect skipped (invalid eqpPort). eqpId={}", eqpId);
                return;
            }

            final Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, nettyProperties.getConnectTimeoutMillis())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        /**
                         * 아웃바운드 채널 파이프라인에 ACTIVE 핸들러를 등록합니다.
                         */
                        @Override
                        protected void initChannel(final SocketChannel ch) {
                            ch.pipeline().addLast(handlerFactory.newActiveHandler(info.commInterfaceType(), eqpId));
                        }
                    });

            if (log.isDebugEnabled()) {
                log.debug("Outbound connect attempt. eqpId={}, ip={}, port={}", eqpId, info.eqpIp(), info.eqpPort());
            }

            bootstrap.connect(info.eqpIp(), info.eqpPort()).addListener((ChannelFuture future) ->
                    withEqpLogContext(eqpId, () -> {
                        if (!future.isSuccess()) {
                            final String errorMessage = future.cause() == null ? "unknown" : future.cause().getMessage();
                            handleOutboundAttemptFailure(info, "TCP_CONNECT_FAILED: " + errorMessage);
                            return;
                        }

                        log.info("Outbound TCP connect success. eqpId={}", eqpId);
                        final Channel channel = future.channel();
                        channel.closeFuture().addListener(closeFuture -> handleOutboundChannelClosed(info, channel));
                    })
            );
        });
    }

    /**
     * 아웃바운드 채널 종료 시 바인딩 여부에 따라 후속 동작을 분기합니다.
     *
     * @param info 설비 정보
     * @param channel 종료된 채널
     */
    private void handleOutboundChannelClosed(final GatewayEquipmentInfo info, final Channel channel) {
        final String eqpId = info.equipmentId();
        withEqpLogContext(eqpId, () -> {
            final boolean boundAtLeastOnce = NettyChannelAttributes.getBindState(channel) == BindState.BOUND
                    || NettyChannelAttributes.getEqpId(channel) != null;

            if (boundAtLeastOnce) {
                resetOutboundFailureCounter(eqpId, "bound session closed");
                scheduleReconnect(info);
                return;
            }

            handleOutboundAttemptFailure(info, "CHANNEL_CLOSED_BEFORE_BIND");
        });
    }

    /**
     * 아웃바운드 연결 시도 실패를 누적하고 재연결/중단을 결정합니다.
     *
     * @param info 설비 정보
     * @param reason 실패 원인 문자열
     */
    private void handleOutboundAttemptFailure(final GatewayEquipmentInfo info, final String reason) {
        final String eqpId = info.equipmentId();
        withEqpLogContext(eqpId, () -> {
            if (!running) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound attempt failure ignored because gateway is stopping or stopped. eqpId={}, reason={}",
                            eqpId,
                            reason);
                }
                return;
            }
            if (reconnectSuppressedEqpIds.contains(eqpId)) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound attempt failure ignored (suppressed). eqpId={}, reason={}", eqpId, reason);
                }
                return;
            }

            final int failureCount = consecutiveOutboundFailures
                    .computeIfAbsent(eqpId, key -> new AtomicInteger(0))
                    .incrementAndGet();
            final int maxFailures = nettyProperties.getOutboundMaxConsecutiveFailures();

            if (failureCount >= maxFailures) {
                reconnectSuppressedEqpIds.add(eqpId);
                lifecycleStateMachine.onStartFailedIfPending(
                        eqpId,
                        "SYSTEM",
                        "OUTBOUND_RETRY_EXHAUSTED"
                );
                log.error(
                        "Outbound reconnect stopped after consecutive failures. eqpId={}, failureCount={}, threshold={}, reason={}",
                        eqpId,
                        failureCount,
                        maxFailures,
                        reason
                );
                return;
            }

            log.info("Outbound attempt failed. eqpId={}, consecutiveFailureCount={}, reason={}",
                    eqpId,
                    failureCount,
                    reason);
            scheduleReconnect(info);
        });
    }

    /**
     * 설비별 연속 실패 카운터를 초기화합니다.
     *
     * @param eqpId 설비 ID
     * @param reason 초기화 사유
     */
    private void resetOutboundFailureCounter(final String eqpId, final String reason) {
        withEqpLogContext(eqpId, () -> {
            final AtomicInteger removed = consecutiveOutboundFailures.remove(eqpId);
            if (removed != null && removed.get() > 0 && log.isDebugEnabled()) {
                log.debug("Outbound failure counter reset. eqpId={}, previousCount={}, reason={}",
                        eqpId,
                        removed.get(),
                        reason);
            }
        });
    }

    /**
     * 아웃바운드 재연결을 지연 스케줄링합니다.
     *
     * @param info 설비 정보
     */
    private void scheduleReconnect(final GatewayEquipmentInfo info) {
        final String eqpId = info.equipmentId();
        withEqpLogContext(eqpId, () -> {
            if (!running) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound reconnect skipped because gateway is stopping or stopped. eqpId={}", eqpId);
                }
                return;
            }
            if (reconnectSuppressedEqpIds.contains(eqpId)) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound reconnect skipped (suppressed). eqpId={}", eqpId);
                }
                return;
            }

            final ScheduledExecutorService scheduler = reconnectScheduler;
            if (scheduler == null || scheduler.isShutdown()) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound reconnect skipped because reconnectScheduler is not available. eqpId={}", eqpId);
                }
                return;
            }

            final AtomicBoolean flag = reconnecting.computeIfAbsent(eqpId, key -> new AtomicBoolean(false));
            if (!flag.compareAndSet(false, true)) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound reconnect skipped (already scheduled). eqpId={}", eqpId);
                }
                return;
            }

            if (log.isDebugEnabled()) {
                log.debug("Scheduling outbound reconnect. eqpId={}, delayMs={}",
                        eqpId,
                        nettyProperties.getActiveReconnectDelayMs());
            }

            try {
                scheduler.schedule(GatewayLogContext.wrap(() -> {
                    try {
                        connectOutbound(info);
                    } finally {
                        flag.set(false);
                    }
                }), nettyProperties.getActiveReconnectDelayMs(), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException rejected) {
                flag.set(false);
                if (log.isDebugEnabled()) {
                    log.debug("Outbound reconnect scheduling rejected. eqpId={}", eqpId, rejected);
                }
            }
        });
    }

    /**
     * 채널이 null이 아닐 때만 안전하게 close 합니다.
     *
     * @param channel 종료 대상 채널
     */
    private void safeClose(final Channel channel) {
        if (channel != null) {
            channel.close();
        }
    }

    /**
     * 설비 로그 컨텍스트(MDC eqpId)를 적용한 상태로 작업을 실행합니다.
     *
     * <p>eqpId가 비어 있으면 컨텍스트 없이 그대로 실행합니다.</p>
     *
     * @param eqpId 설비 ID
     * @param task 실행 작업
     */
    private void withEqpLogContext(final String eqpId, final Runnable task) {
        if (task == null) {
            return;
        }
        if (eqpId == null || eqpId.isBlank()) {
            task.run();
            return;
        }
        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId)) {
            task.run();
        }
    }
}
