package com.nori.tc.apps.commgateway.netty;

import com.nori.tc.apps.commgateway.config.GatewayNettyProperties;
import com.nori.tc.apps.commgateway.comm.EquipmentInfoProvider;
import com.nori.tc.apps.commgateway.db.GatewayEquipmentInfo;
import com.nori.tc.apps.commgateway.comm.ConnectionMode;
import com.nori.tc.apps.commgateway.kafka.KafkaShardOwnership;
import com.nori.tc.comm.domain.type.CommInterfaceType;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
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
 * Netty bootstrapper (PASSIVE server + ACTIVE client).
 *
 * 처리 모델 요약
 * - BossGroup: accept 전용 (listen)
 * - WorkerGroup: IO 처리 (read/write)
 * - PASSIVE: 서버 소켓 bind 후 설비 접속 대기
 * - ACTIVE : DB(tc_eqp) 기준으로 GW가 설비에 connect
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

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;

        // Boss/Worker 분리
        bossGroup = new NioEventLoopGroup(nettyProperties.getBossThreads());
        workerGroup = new NioEventLoopGroup(nettyProperties.getWorkerThreads());
        reconnectScheduler = Executors.newScheduledThreadPool(nettyProperties.getReconnectSchedulerThreads());

        startServers();
        startActiveConnections();
    }

    @Override
    public void stop() {
        running = false;

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
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    private void startServers() {
        // PASSIVE flow:
        // - bind/listen per interface
        // - accept -> GatewayChannelHandler(UNBOUND)
        // - register message -> bind -> mailbox creation
        // PASSIVE 서버: HSMS, SOCKET 각각 별도 포트 bind
        hsmsServerChannel = startServer(nettyProperties.getHsmsBindPort(), CommInterfaceType.HSMS);
        socketServerChannel = startServer(nettyProperties.getSocketBindPort(), CommInterfaceType.SOCKET);
    }

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

    private void startActiveConnections() {
        // ACTIVE flow:
        // - connect only for owned eqpIds (shard ownership)
        // - bind immediately on channelActive (eqpId is known)
        final List<GatewayEquipmentInfo> equipmentList = equipmentInfoProvider.findAll();

        for (GatewayEquipmentInfo info : equipmentList) {
            if (!info.enabled()) {
                continue;
            }
            if (info.connectionMode() == null) {
                continue;
            }
            if (info.connectionMode() == ConnectionMode.ACTIVE) {
                if (!shardOwnership.isOwned(info.equipmentId())) {
                    continue;
                }
                connectActive(info);
            }
        }
    }

    private void connectActive(final GatewayEquipmentInfo info) {
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

    private void scheduleReconnect(final GatewayEquipmentInfo info) {
        final String eqpId = info.equipmentId();
        final AtomicBoolean flag = reconnecting.computeIfAbsent(eqpId, key -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            return;
        }

        reconnectScheduler.schedule(() -> {
            try {
                connectActive(info);
            } finally {
                flag.set(false);
            }
        }, nettyProperties.getActiveReconnectDelayMs(), TimeUnit.MILLISECONDS);
    }

    private void safeClose(final Channel channel) {
        if (channel != null) {
            channel.close();
        }
    }
}
