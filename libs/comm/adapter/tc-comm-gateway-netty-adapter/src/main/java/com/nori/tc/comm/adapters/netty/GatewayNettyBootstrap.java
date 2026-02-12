package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentInfoProvider;
import com.nori.tc.comm.gateway.comm.GatewayConnectionControlPort;
import com.nori.tc.comm.gateway.config.GatewayNettyProperties;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gateway Netty 부트스트랩 관리자입니다.
 *
 * <p>역할:
 * 1) PASSIVE 모드 수신 서버(HSMS/SOCKET) 기동
 * 2) ACTIVE 모드 장비 대상 클라이언트 연결/재연결 관리
 * 3) UI runtime 제어 요청에 따른 재연결 억제/재개/즉시 연결 제어</p>
 */
@Component
public class GatewayNettyBootstrap implements SmartLifecycle, GatewayConnectionControlPort {

    private static final Logger log = LoggerFactory.getLogger(GatewayNettyBootstrap.class);

    private final GatewayNettyProperties nettyProperties;
    private final EquipmentInfoProvider equipmentInfoProvider;
    private final GatewayChannelHandlerFactory handlerFactory;
    private final KafkaShardOwnership shardOwnership;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel hsmsServerChannel;
    private Channel socketServerChannel;
    private ScheduledExecutorService reconnectScheduler;

    private final ConcurrentHashMap<String, AtomicBoolean> reconnecting = new ConcurrentHashMap<>();
    private final Set<String> reconnectSuppressedEqpIds = ConcurrentHashMap.newKeySet();

    private volatile boolean running = false;

    /**
     * Netty 서버/클라이언트 부트스트랩 구성요소를 초기화합니다.
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
     * SmartLifecycle 시작 진입점입니다.
     *
     * <p>이벤트루프/재연결 스케줄러를 초기화하고
     * PASSIVE 서버 + ACTIVE 초기 연결을 순차적으로 시작합니다.</p>
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
     * SmartLifecycle 종료 진입점입니다.
     *
     * <p>서버 채널을 닫고 이벤트루프/스케줄러를 정리합니다.</p>
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

        log.info("GatewayNettyBootstrap stopped.");
    }

    /**
     * 현재 부트스트랩 실행 여부를 반환합니다.
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 라이프사이클 phase를 반환합니다.
     */
    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * ACTIVE 모드 장비에 대해 즉시 연결 시도를 수행합니다.
     *
     * <p>소유 shard, 장비 존재/활성 여부, connectionMode를 모두 통과할 때만 연결합니다.</p>
     */
    @Override
    public void connectActiveIfPossible(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        if (!running) {
            log.warn("Active connect skipped (gateway not running). eqpId={}", eqpId);
            return;
        }
        if (!shardOwnership.isOwned(eqpId)) {
            if (log.isDebugEnabled()) {
                log.debug("Active connect skipped (not owned). eqpId={}", eqpId);
            }
            return;
        }

        final GatewayEquipmentInfo info = equipmentInfoProvider.findById(eqpId).orElse(null);
        if (info == null) {
            log.warn("Active connect skipped (equipment not found). eqpId={}", eqpId);
            return;
        }
        if (!info.enabled()) {
            log.warn("Active connect skipped (equipment disabled). eqpId={}", eqpId);
            return;
        }
        if (info.connectionMode() != ConnectionMode.ACTIVE) {
            if (log.isDebugEnabled()) {
                log.debug("Active connect skipped (not ACTIVE mode). eqpId={}, mode={}", eqpId, info.connectionMode());
            }
            return;
        }

        reconnectSuppressedEqpIds.remove(eqpId);
        log.info("Active connect requested by runtime control. eqpId={}", eqpId);
        connectActive(info);
    }

    /**
     * 장비별 ACTIVE 재연결을 억제합니다.
     */
    @Override
    public void suppressActiveReconnect(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        reconnectSuppressedEqpIds.add(eqpId);
        log.info("Active reconnect suppressed. eqpId={}", eqpId);
    }

    /**
     * 장비별 ACTIVE 재연결 억제를 해제합니다.
     */
    @Override
    public void resumeActiveReconnect(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        reconnectSuppressedEqpIds.remove(eqpId);
        log.info("Active reconnect resumed. eqpId={}", eqpId);
    }

    /**
     * PASSIVE 수신 서버(HSMS/SOCKET)를 시작합니다.
     */
    private void startServers() {
        hsmsServerChannel = startServer(nettyProperties.getHsmsBindPort(), CommInterfaceType.HSMS);
        socketServerChannel = startServer(nettyProperties.getSocketBindPort(), CommInterfaceType.SOCKET);
    }

    /**
     * 단일 인터페이스 타입용 Netty 서버를 기동합니다.
     */
    private Channel startServer(final int port, final CommInterfaceType interfaceType) {
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
     * ACTIVE 장비 목록을 조회해 초기 연결을 시도합니다.
     */
    private void startActiveConnections() {
        final List<GatewayEquipmentInfo> equipmentList = equipmentInfoProvider.findAll();
        log.info("Active connection bootstrap started. totalEquipments={}", equipmentList.size());

        for (GatewayEquipmentInfo info : equipmentList) {
            if (!info.enabled() || info.connectionMode() == null) {
                continue;
            }
            if (info.connectionMode() != ConnectionMode.ACTIVE) {
                continue;
            }
            if (!shardOwnership.isOwned(info.equipmentId())) {
                if (log.isDebugEnabled()) {
                    log.debug("Active connect skipped (not owned). eqpId={}", info.equipmentId());
                }
                continue;
            }
            if (reconnectSuppressedEqpIds.contains(info.equipmentId())) {
                if (log.isDebugEnabled()) {
                    log.debug("Active connect skipped (suppressed). eqpId={}", info.equipmentId());
                }
                continue;
            }
            connectActive(info);
        }
    }

    /**
     * ACTIVE 모드 단일 장비 연결을 수행합니다.
     */
    private void connectActive(final GatewayEquipmentInfo info) {
        final String eqpId = info.equipmentId();
        if (reconnectSuppressedEqpIds.contains(eqpId)) {
            if (log.isDebugEnabled()) {
                log.debug("Active connect skipped (suppressed). eqpId={}", eqpId);
            }
            return;
        }
        if (info.eqpIp() == null || info.eqpIp().isBlank()) {
            log.warn("Active connect skipped (missing eqpIp). eqpId={}", eqpId);
            return;
        }
        if (info.eqpPort() == null || info.eqpPort() <= 0) {
            log.warn("Active connect skipped (invalid eqpPort). eqpId={}", eqpId);
            return;
        }

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
        bootstrap.connect(info.eqpIp(), info.eqpPort()).addListener((ChannelFuture future) -> {
            if (!future.isSuccess()) {
                log.warn("Active connect failed. eqpId={}, {}", eqpId,
                        future.cause() == null ? "" : future.cause().getMessage());
                scheduleReconnect(info);
                return;
            }
            log.info("Active connect success. eqpId={}", eqpId);
            future.channel().closeFuture().addListener(closeFuture -> scheduleReconnect(info));
        });
    }

    /**
     * ACTIVE 연결 실패/종료 후 재연결을 예약합니다.
     */
    private void scheduleReconnect(final GatewayEquipmentInfo info) {
        final String eqpId = info.equipmentId();
        if (reconnectSuppressedEqpIds.contains(eqpId)) {
            if (log.isDebugEnabled()) {
                log.debug("Active reconnect skipped (suppressed). eqpId={}", eqpId);
            }
            return;
        }

        final AtomicBoolean flag = reconnecting.computeIfAbsent(eqpId, key -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Scheduling active reconnect. eqpId={}, delayMs={}",
                    eqpId, nettyProperties.getActiveReconnectDelayMs());
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
     * 채널을 null-safe하게 닫습니다.
     */
    private void safeClose(final Channel channel) {
        if (channel != null) {
            channel.close();
        }
    }
}
