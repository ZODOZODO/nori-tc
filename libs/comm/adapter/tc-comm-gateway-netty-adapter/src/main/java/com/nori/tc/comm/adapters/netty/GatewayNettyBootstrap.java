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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gateway Netty 서버/클라이언트 부트스트랩 관리자입니다.
 *
 * <p>핵심 동작:</p>
 * <p>1) HSMS/SOCKET 서버 포트를 항상 열어 수신을 허용합니다.</p>
 * <p>2) 설비 connectionMode가 PASSIVE인 설비에 대해서만 아웃바운드 접속을 시도합니다.</p>
 * <p>3) 아웃바운드 연결 시도 실패가 설정 임계값 이상 누적되면 자동 재연결을 중단합니다.</p>
 *
 * <p>참고: 메서드 이름에 "Active"가 남아 있는 포트 시그니처는 하위 호환 목적입니다.
 * 실제 의미는 "게이트웨이 아웃바운드 연결 제어"입니다.</p>
 */
@Component
public class GatewayNettyBootstrap implements SmartLifecycle, GatewayConnectionControlPort {

    private static final Logger log = LoggerFactory.getLogger(GatewayNettyBootstrap.class);

    private final GatewayNettyProperties nettyProperties;
    private final EquipmentInfoProvider equipmentInfoProvider;
    private final GatewayChannelHandlerFactory handlerFactory;
    private final KafkaShardOwnership shardOwnership;
    private final EqpLifecycleStateMachine lifecycleStateMachine;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel hsmsServerChannel;
    private Channel socketServerChannel;
    private ScheduledExecutorService reconnectScheduler;

    /**
     * 동일 설비에 대해 중복 재연결 스케줄 등록을 방지합니다.
     */
    private final ConcurrentHashMap<String, AtomicBoolean> reconnecting = new ConcurrentHashMap<>();
    /**
     * 자동 재연결 중단 대상 설비 목록입니다.
     */
    private final Set<String> reconnectSuppressedEqpIds = ConcurrentHashMap.newKeySet();
    /**
     * 설비별 아웃바운드 연속 실패 횟수입니다.
     */
    private final ConcurrentHashMap<String, AtomicInteger> consecutiveOutboundFailures = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    /**
     * Netty 부트스트랩 의존성을 주입받아 초기화합니다.
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
     * 라이프사이클 시작 시 서버 포트와 아웃바운드 연결 스케줄러를 기동합니다.
     */
    @Override
    public void start() {
        if (running) {
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
     * 라이프사이클 종료 시 채널/스레드 자원을 정리합니다.
     */
    @Override
    public void stop() {
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
     * 현재 실행 여부를 반환합니다.
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * SmartLifecycle phase를 반환합니다.
     */
    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * 지정 설비에 대해 아웃바운드 즉시 연결을 요청합니다.
     *
     * <p>하위 호환 때문에 메서드명은 connectActiveIfPossible이지만,
     * 실제로는 설비 connectionMode=PASSIVE 대상만 연결 시도합니다.</p>
     *
     * @param eqpId 대상 설비 ID
     */
    @Override
    public void connectActiveIfPossible(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
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
    }

    /**
     * 자동 재연결을 억제합니다.
     *
     * @param eqpId 대상 설비 ID
     */
    @Override
    public void suppressActiveReconnect(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        reconnectSuppressedEqpIds.add(eqpId);
        log.info("Outbound reconnect suppressed. eqpId={}", eqpId);
    }

    /**
     * 자동 재연결 억제를 해제합니다.
     *
     * <p>재개 시 연속 실패 카운터도 초기화하여 신규 시퀀스로 재시도합니다.</p>
     *
     * @param eqpId 대상 설비 ID
     */
    @Override
    public void resumeActiveReconnect(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        reconnectSuppressedEqpIds.remove(eqpId);
        resetOutboundFailureCounter(eqpId, "manual reconnect resume");
        log.info("Outbound reconnect resumed. eqpId={}", eqpId);
    }

    /**
     * HSMS/SOCKET 서버 포트를 시작합니다.
     */
    private void startServers() {
        hsmsServerChannel = startServer(nettyProperties.getHsmsBindPort(), CommInterfaceType.HSMS);
        socketServerChannel = startServer(nettyProperties.getSocketBindPort(), CommInterfaceType.SOCKET);
    }

    /**
     * 단일 인터페이스용 Netty 서버를 시작합니다.
     *
     * @param port 바인드 포트
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
     * 전체 설비 목록 중 아웃바운드 대상(PASSIVE) 설비에 대해 초기 연결을 시도합니다.
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
            if (!shardOwnership.isOwned(info.equipmentId())) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound connect skipped (not owned). eqpId={}", info.equipmentId());
                }
                continue;
            }
            if (reconnectSuppressedEqpIds.contains(info.equipmentId())) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound connect skipped (suppressed). eqpId={}", info.equipmentId());
                }
                continue;
            }
            connectOutbound(info);
        }
    }

    /**
     * 단일 설비에 대한 아웃바운드 연결을 시도합니다.
     *
     * @param info 설비 정보
     */
    private void connectOutbound(final GatewayEquipmentInfo info) {
        final String eqpId = info.equipmentId();
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
        bootstrap.connect(info.eqpIp(), info.eqpPort()).addListener((ChannelFuture future) -> {
            if (!future.isSuccess()) {
                final String errorMessage = future.cause() == null ? "unknown" : future.cause().getMessage();
                handleOutboundAttemptFailure(info, "TCP_CONNECT_FAILED: " + errorMessage);
                return;
            }

            log.info("Outbound TCP connect success. eqpId={}", eqpId);
            final Channel channel = future.channel();
            channel.closeFuture().addListener(closeFuture -> handleOutboundChannelClosed(info, channel));
        });
    }

    /**
     * 아웃바운드 채널 종료 시 바운드 여부에 따라 성공/실패를 분기 처리합니다.
     *
     * <p>BOUND 상태로 한 번이라도 정상 바인딩된 채널이면 연속 실패 카운터를 초기화하고 재연결합니다.</p>
     * <p>BOUND 이전에 닫힌 채널이면 시도 실패로 간주해 연속 실패 카운터를 증가시킵니다.</p>
     *
     * @param info 설비 정보
     * @param channel 종료된 채널
     */
    private void handleOutboundChannelClosed(final GatewayEquipmentInfo info, final Channel channel) {
        final String eqpId = info.equipmentId();
        final boolean boundAtLeastOnce = NettyChannelAttributes.getBindState(channel) == BindState.BOUND
                || NettyChannelAttributes.getEqpId(channel) != null;

        if (boundAtLeastOnce) {
            resetOutboundFailureCounter(eqpId, "bound session closed");
            scheduleReconnect(info);
            return;
        }

        handleOutboundAttemptFailure(info, "CHANNEL_CLOSED_BEFORE_BIND");
    }

    /**
     * 아웃바운드 연결 시도 실패를 기록하고 재연결/중단을 결정합니다.
     *
     * @param info 설비 정보
     * @param reason 실패 사유
     */
    private void handleOutboundAttemptFailure(final GatewayEquipmentInfo info, final String reason) {
        final String eqpId = info.equipmentId();
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
    }

    /**
     * 설비의 아웃바운드 연속 실패 카운터를 초기화합니다.
     *
     * @param eqpId 설비 ID
     * @param reason 초기화 사유
     */
    private void resetOutboundFailureCounter(final String eqpId, final String reason) {
        final AtomicInteger removed = consecutiveOutboundFailures.remove(eqpId);
        if (removed != null && removed.get() > 0 && log.isDebugEnabled()) {
            log.debug("Outbound failure counter reset. eqpId={}, previousCount={}, reason={}",
                    eqpId,
                    removed.get(),
                    reason);
        }
    }

    /**
     * 아웃바운드 재연결을 예약합니다.
     *
     * @param info 설비 정보
     */
    private void scheduleReconnect(final GatewayEquipmentInfo info) {
        final String eqpId = info.equipmentId();
        if (reconnectSuppressedEqpIds.contains(eqpId)) {
            if (log.isDebugEnabled()) {
                log.debug("Outbound reconnect skipped (suppressed). eqpId={}", eqpId);
            }
            return;
        }

        final AtomicBoolean flag = reconnecting.computeIfAbsent(eqpId, key -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Scheduling outbound reconnect. eqpId={}, delayMs={}",
                    eqpId,
                    nettyProperties.getActiveReconnectDelayMs());
        }

        reconnectScheduler.schedule(GatewayLogContext.wrap(() -> {
            try {
                connectOutbound(info);
            } finally {
                flag.set(false);
            }
        }), nettyProperties.getActiveReconnectDelayMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * 채널 null-safe 종료 헬퍼입니다.
     *
     * @param channel 종료 대상 채널
     */
    private void safeClose(final Channel channel) {
        if (channel != null) {
            channel.close();
        }
    }
}
