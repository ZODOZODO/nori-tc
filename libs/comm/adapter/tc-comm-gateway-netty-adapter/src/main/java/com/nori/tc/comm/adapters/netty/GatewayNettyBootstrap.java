package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentInfoProvider;
import com.nori.tc.comm.gateway.comm.GatewayConnectionControlPort;
import com.nori.tc.comm.gateway.config.GatewayNettyProperties;
import com.nori.tc.comm.gateway.context.EquipmentContext;
import com.nori.tc.comm.gateway.context.EquipmentContextRegistry;
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
    /**
     * 장비 기준 ACTIVE(장비가 접속) 리스너의 게이트웨이 바인드 IP 정책 상수입니다.
     *
     * <p>요구사항 기준으로 ACTIVE 리스너는 항상 게이트웨이 로컬 루프백에만 바인드합니다.</p>
     */
    private static final String ACTIVE_LISTENER_BIND_IP = "127.0.0.1";

    private final GatewayNettyProperties nettyProperties;
    private final EquipmentInfoProvider equipmentInfoProvider;
    private final EquipmentContextRegistry equipmentContextRegistry;
    private final GatewayChannelHandlerFactory handlerFactory;
    private final KafkaShardOwnership shardOwnership;
    private final EqpLifecycleStateMachine lifecycleStateMachine;

    /**
     * Netty 서버/클라이언트 리소스입니다.
     */
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ScheduledExecutorService reconnectScheduler;

    /**
     * 장비 기준 ACTIVE 장비들이 공유하는 listener 채널 맵입니다.
     *
     * <p>key = (interfaceType + port) 조합이며, bind IP는 정책상 127.0.0.1로 고정됩니다.</p>
     */
    private final ConcurrentHashMap<ActiveListenerKey, Channel> activeListenerChannels = new ConcurrentHashMap<>();

    /**
     * listener 별 멤버십(이 listener를 필요로 하는 eqpId 집합)입니다.
     *
     * <p>여러 ACTIVE 장비가 동일 interface+port를 공유할 수 있으므로, 마지막 멤버 제거 시에만
     * listener 채널을 종료해야 합니다.</p>
     */
    private final ConcurrentHashMap<ActiveListenerKey, Set<String>> activeListenerMembers = new ConcurrentHashMap<>();

    /**
     * eqpId -> listenerKey 역참조 맵입니다.
     *
     * <p>UI END/DELETE, 설정 변경 시 기존 listener 멤버십을 빠르게 정리하기 위해 사용합니다.</p>
     */
    private final ConcurrentHashMap<String, ActiveListenerKey> activeListenerKeyByEqpId = new ConcurrentHashMap<>();

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
            final EquipmentContextRegistry equipmentContextRegistry,
            final GatewayChannelHandlerFactory handlerFactory,
            final KafkaShardOwnership shardOwnership,
            final EqpLifecycleStateMachine lifecycleStateMachine
    ) {
        this.nettyProperties = Objects.requireNonNull(nettyProperties, "nettyProperties is null");
        this.equipmentInfoProvider = Objects.requireNonNull(equipmentInfoProvider, "equipmentInfoProvider is null");
        this.equipmentContextRegistry = Objects.requireNonNull(equipmentContextRegistry, "equipmentContextRegistry is null");
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

        /**
         * DB -> EquipmentContextRegistry 로 적재된 컨텍스트를 기준으로 enabled 장비 런타임을 기동합니다.
         * - 장비 기준 ACTIVE  : 공유 listener 보장
         * - 장비 기준 PASSIVE : 아웃바운드 연결 시도
         */
        startEnabledRuntimesFromContextRegistry();
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
        closeAllActiveListeners();

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
        activeListenerChannels.clear();
        activeListenerMembers.clear();
        activeListenerKeyByEqpId.clear();

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
     * 장비 1대의 통신 런타임을 장비 기준 모드에 맞게 시작합니다.
     *
     * <p>동작 규칙:</p>
     * <p>1) shard ownership 및 enabled 여부를 확인합니다.</p>
     * <p>2) 장비 기준 PASSIVE는 기존 아웃바운드 연결/재연결 로직을 사용합니다.</p>
     * <p>3) 장비 기준 ACTIVE는 공유 listener(interface + port)를 보장합니다.</p>
     *
     * @param eqpId 장비 ID
     */
    @Override
    public void startRuntimeIfPossible(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        withEqpLogContext(eqpId, () -> {
            if (!running) {
                log.warn("Transport runtime start skipped (gateway not running). eqpId={}", eqpId);
                return;
            }
            if (!shardOwnership.isOwned(eqpId)) {
                if (log.isDebugEnabled()) {
                    log.debug("Transport runtime start skipped (not owned). eqpId={}", eqpId);
                }
                return;
            }

            final GatewayEquipmentInfo info = equipmentInfoProvider.findById(eqpId).orElse(null);
            if (info == null) {
                log.warn("Transport runtime start skipped (equipment not found). eqpId={}", eqpId);
                return;
            }
            if (!info.enabled()) {
                log.info("Transport runtime start skipped (equipment disabled). eqpId={}", eqpId);
                return;
            }
            if (info.connectionMode() == null) {
                log.warn("Transport runtime start skipped (connectionMode is null). eqpId={}", eqpId);
                return;
            }

            /**
             * 모드 전환(예: ACTIVE -> PASSIVE) 이후 stale listener 멤버십이 남지 않도록
             * PASSIVE 시작 경로에서는 먼저 ACTIVE listener 멤버십을 정리합니다.
             */
            if (info.connectionMode() == ConnectionMode.PASSIVE) {
                releaseActiveListenerMembership(eqpId, "MODE_SWITCH_OR_PASSIVE_START");
                resumeActiveReconnect(eqpId);
                connectActiveIfPossible(eqpId);
                return;
            }

            try {
                reconnectSuppressedEqpIds.remove(eqpId);
                ensureActiveListenerForEquipment(info, "RUNTIME_START");
            } catch (Exception ex) {
                /**
                 * listener 생성 실패 시 멤버십 맵에 잔여 상태가 남으면 이후 재시도/정지 로직이 혼란스러워질 수 있으므로
                 * 즉시 정리합니다.
                 */
                releaseActiveListenerMembership(eqpId, "ACTIVE_LISTENER_START_FAILED_CLEANUP");
                lifecycleStateMachine.onStartFailedIfPending(eqpId, "SYSTEM", "ACTIVE_LISTENER_START_FAILED");
                log.error("ACTIVE listener runtime start failed. eqpId={}, interfaceType={}, port={}",
                        eqpId,
                        info.commInterfaceType(),
                        info.eqpPort(),
                        ex);
            }
        });
    }

    /**
     * 장비 1대의 통신 런타임을 장비 기준 모드에 맞게 정지합니다.
     *
     * <p>이 메서드는 모드별 런타임 자원만 정리합니다.</p>
     * <p>- ACTIVE  : 공유 listener 멤버십 해제 및 마지막 멤버일 때 listener 종료</p>
     * <p>- PASSIVE : 자동 재연결 억제</p>
     *
     * <p>실제 장비 채널 close는 상위 UI 런타임 제어 서비스가 담당할 수 있습니다.</p>
     *
     * @param eqpId 장비 ID
     */
    @Override
    public void stopRuntimeIfPossible(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        withEqpLogContext(eqpId, () -> {
            final GatewayEquipmentInfo info = equipmentInfoProvider.findById(eqpId).orElse(null);
            final ConnectionMode mode = info == null ? null : info.connectionMode();

            if (mode == ConnectionMode.ACTIVE) {
                releaseActiveListenerMembership(eqpId, "RUNTIME_STOP");
                return;
            }

            /**
             * PASSIVE 또는 DB 조회 실패(null) 케이스는 재연결 억제를 수행합니다.
             * DB 조회 실패 시에도 stale suppress 상태 정리에 의미가 있으므로 그대로 호출합니다.
             */
            suppressActiveReconnect(eqpId);
            releaseActiveListenerMembership(eqpId, "RUNTIME_STOP_FALLBACK_CLEANUP");
        });
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
     * EquipmentContextRegistry에 적재된 컨텍스트를 기준으로 enabled 장비 런타임을 기동합니다.
     *
     * <p>컨텍스트 적재는 별도 부트스트랩(EquipmentContextBootstrap)에서 수행되며,
     * 이 메서드는 그 결과를 읽어 실제 통신 런타임(Netty listener/outbound connect)을 실행합니다.</p>
     */
    private void startEnabledRuntimesFromContextRegistry() {
        final List<EquipmentContext> contexts = List.copyOf(equipmentContextRegistry.snapshot());
        int enabledCount = 0;

        log.info("Transport runtime bootstrap started from EquipmentContextRegistry. totalContexts={}", contexts.size());

        for (EquipmentContext context : contexts) {
            if (context == null) {
                continue;
            }

            final GatewayEquipmentInfo info = context.profile().equipmentInfo();
            if (info == null) {
                continue;
            }

            if (!info.enabled()) {
                if (log.isDebugEnabled()) {
                    log.debug("Transport runtime bootstrap skipped (disabled). eqpId={}", info.equipmentId());
                }
                continue;
            }

            enabledCount++;
            startRuntimeIfPossible(info.equipmentId());
        }

        log.info("Transport runtime bootstrap completed. enabledContextsProcessed={}", enabledCount);
    }

    /**
     * 장비 기준 ACTIVE 장비용 공유 listener를 보장합니다.
     *
     * <p>공유 기준은 interface + port이며, bind IP는 정책상 127.0.0.1 고정입니다.</p>
     *
     * @param info 장비 정보
     * @param reason 호출 사유(로그용)
     */
    private synchronized void ensureActiveListenerForEquipment(final GatewayEquipmentInfo info, final String reason) {
        Objects.requireNonNull(info, "info is null");

        final String eqpId = info.equipmentId();
        final ActiveListenerKey nextKey = ActiveListenerKey.from(info.commInterfaceType(), info.eqpPort());

        /**
         * ACTIVE 모드에서는 DB의 eqp_ip를 bind IP로 사용하지 않고 정책상 고정값(127.0.0.1)을 사용합니다.
         * 운영 확인을 위해 DEBUG 로그로 DB값과 실제 bind 정책값을 함께 남깁니다.
         */
        if (log.isDebugEnabled() && info.eqpIp() != null && !info.eqpIp().isBlank()) {
            log.debug("ACTIVE listener bind IP policy applied. eqpId={}, dbEqpIp={}, bindIp={}",
                    eqpId,
                    info.eqpIp(),
                    ACTIVE_LISTENER_BIND_IP);
        }

        final ActiveListenerKey currentKey = activeListenerKeyByEqpId.get(eqpId);
        if (currentKey != null && !currentKey.equals(nextKey)) {
            log.info("ACTIVE listener membership key changed. eqpId={}, previousKey={}, nextKey={}, reason={}",
                    eqpId,
                    currentKey,
                    nextKey,
                    reason);
            releaseActiveListenerMembership(eqpId, "ACTIVE_LISTENER_KEY_CHANGED");
        }

        final Set<String> members = activeListenerMembers.computeIfAbsent(nextKey, key -> ConcurrentHashMap.newKeySet());
        members.add(eqpId);
        activeListenerKeyByEqpId.put(eqpId, nextKey);

        final Channel existing = activeListenerChannels.get(nextKey);
        if (existing != null && existing.isActive()) {
            if (log.isDebugEnabled()) {
                log.debug("ACTIVE shared listener already running. eqpId={}, listenerKey={}, memberCount={}, reason={}",
                        eqpId,
                        nextKey,
                        members.size(),
                        reason);
            }
            return;
        }

        if (existing != null) {
            activeListenerChannels.remove(nextKey, existing);
            safeClose(existing);
            if (log.isDebugEnabled()) {
                log.debug("Removed stale ACTIVE listener channel before restart. listenerKey={}", nextKey);
            }
        }

        final Channel started = startActiveListenerServer(nextKey);
        activeListenerChannels.put(nextKey, started);
        log.info("ACTIVE shared listener ensured. eqpId={}, listenerKey={}, memberCount={}, reason={}",
                eqpId,
                nextKey,
                members.size(),
                reason);
    }

    /**
     * 장비 기준 ACTIVE 장비의 공유 listener 멤버십을 해제합니다.
     *
     * <p>마지막 멤버가 제거되면 해당 listener 채널을 종료합니다.</p>
     *
     * @param eqpId 장비 ID
     * @param reason 호출 사유(로그용)
     */
    private synchronized void releaseActiveListenerMembership(final String eqpId, final String reason) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        final ActiveListenerKey key = activeListenerKeyByEqpId.remove(eqpId);
        if (key == null) {
            return;
        }

        final Set<String> members = activeListenerMembers.get(key);
        if (members == null) {
            activeListenerMembers.remove(key);
            return;
        }

        members.remove(eqpId);
        if (!members.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("ACTIVE listener membership removed. eqpId={}, listenerKey={}, remainingMembers={}, reason={}",
                        eqpId,
                        key,
                        members.size(),
                        reason);
            }
            return;
        }

        activeListenerMembers.remove(key, members);
        final Channel channel = activeListenerChannels.remove(key);
        safeClose(channel);
        log.info("ACTIVE shared listener stopped (last member removed). eqpId={}, listenerKey={}, reason={}",
                eqpId,
                key,
                reason);
    }

    /**
     * 게이트웨이 종료 시 ACTIVE 공유 listener 채널을 일괄 종료합니다.
     */
    private synchronized void closeAllActiveListeners() {
        if (activeListenerChannels.isEmpty()) {
            return;
        }

        for (ActiveListenerKey key : List.copyOf(activeListenerChannels.keySet())) {
            final Channel channel = activeListenerChannels.remove(key);
            safeClose(channel);
            if (log.isDebugEnabled()) {
                log.debug("ACTIVE shared listener closed during gateway stop. listenerKey={}", key);
            }
        }
    }

    /**
     * ACTIVE 공유 listener 서버를 실제로 바인드합니다.
     *
     * <p>장비 기준 ACTIVE 장비가 접속하는 경로이므로 게이트웨이 입장에서는 수신 서버 경로이며,
     * child pipeline에는 PASSIVE handler(수신 바인딩 경로)를 사용합니다.</p>
     *
     * @param key 공유 listener 식별 키
     * @return 바인드된 서버 채널
     */
    private Channel startActiveListenerServer(final ActiveListenerKey key) {
        Objects.requireNonNull(key, "key is null");

        if (bossGroup == null || workerGroup == null) {
            throw new IllegalStateException("Netty event loop groups are not initialized");
        }

        try {
            final ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        /**
                         * 장비 기준 ACTIVE(장비가 게이트웨이로 접속) 경로이므로
                         * 게이트웨이 측 수신 바인딩 핸들러를 등록합니다.
                         */
                        @Override
                        protected void initChannel(final SocketChannel ch) {
                            ch.pipeline().addLast(handlerFactory.newPassiveHandler(key.interfaceType()));
                        }
                    });

            final ChannelFuture future = bootstrap.bind(ACTIVE_LISTENER_BIND_IP, key.port()).sync();
            log.info("ACTIVE shared listener started. listenerKey={}, bindIp={}, bindPort={}",
                    key,
                    ACTIVE_LISTENER_BIND_IP,
                    key.port());
            return future.channel();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ACTIVE shared listener bind interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("ACTIVE shared listener bind failed. key=" + key, ex);
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
     * 장비 기준 ACTIVE 공유 listener 식별 키입니다.
     *
     * <p>bind IP는 정책상 127.0.0.1 고정이므로 키에는 포함하지 않고,
     * interface + port 조합으로 공유 여부를 판단합니다.</p>
     */
    private record ActiveListenerKey(
            CommInterfaceType interfaceType,
            int port
    ) {
        /**
         * 장비 정보에서 공유 listener 키를 생성합니다.
         *
         * @param interfaceType 인터페이스 타입(HSMS/SOCKET)
         * @param port tc_eqp.eqp_port 값 (ACTIVE 모드에서는 게이트웨이 bind port로 사용)
         * @return 공유 listener 키
         */
        private static ActiveListenerKey from(final CommInterfaceType interfaceType, final Integer port) {
            if (interfaceType == null) {
                throw new IllegalArgumentException("interfaceType is null");
            }
            if (port == null || port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Invalid ACTIVE listener port: " + port);
            }
            return new ActiveListenerKey(interfaceType, port);
        }
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
