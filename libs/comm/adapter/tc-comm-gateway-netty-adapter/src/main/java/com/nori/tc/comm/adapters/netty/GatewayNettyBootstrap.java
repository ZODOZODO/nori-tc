package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.gateway.db.ConnectionMode;
import com.nori.tc.comm.gateway.equipment.port.EquipmentInfoProvider;
import com.nori.tc.comm.gateway.runtime.channel.GatewayConnectionControlPort;
import com.nori.tc.comm.gateway.config.props.GatewayNettyProperties;
import com.nori.tc.comm.gateway.config.props.GatewaySocketProperties;
import com.nori.tc.comm.gateway.context.model.EquipmentContext;
import com.nori.tc.comm.gateway.context.service.EquipmentContextRegistry;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.kafka.KafkaShardOwnership;
import com.nori.tc.comm.gateway.lifecycle.service.EquipmentLifecycleStateMachine;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
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
import java.util.Map;
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
 * <p>2) 게이트웨이 기준 모드가 ACTIVE인 경우(게이트웨이가 접속 주체) 아웃바운드 연결을 시도합니다.</p>
 * <p>3) 아웃바운드 연속 실패 횟수를 추적해 임계치 도달 시 자동 재연결을 중단합니다.</p>
 *
 * <p>주의: 메서드명의 "Active"는 기존 인터페이스 호환 용어이며,
 * 실제 의미는 "게이트웨이에서 아웃바운드 연결을 수행"입니다.</p>
 */
@Component
public class GatewayNettyBootstrap implements SmartLifecycle, GatewayConnectionControlPort {

    private static final Logger log = LoggerFactory.getLogger(GatewayNettyBootstrap.class);
    /**
     * 게이트웨이 기준 PASSIVE(listener) 경로의 바인드 IP 정책 상수입니다.
     *
     * <p>요구사항 기준으로 PASSIVE listener는 항상 게이트웨이 로컬 루프백에만 바인드합니다.</p>
     */
    private static final String PASSIVE_LISTENER_BIND_IP = "127.0.0.1";

    private final GatewayNettyProperties nettyProperties;
    private final GatewaySocketProperties socketProperties;
    private final EquipmentInfoProvider equipmentInfoProvider;
    private final EquipmentContextRegistry equipmentContextRegistry;
    private final GatewayChannelHandlerFactory handlerFactory;
    private final KafkaShardOwnership shardOwnership;
    private final EquipmentLifecycleStateMachine lifecycleStateMachine;

    /**
     * Netty 서버/클라이언트 리소스입니다.
     */
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ScheduledExecutorService reconnectScheduler;

    /**
     * 게이트웨이 기준 PASSIVE 장비들이 공유하는 listener 채널 맵입니다.
     *
     * <p>key = (interfaceType + port) 조합이며, bind IP는 정책상 127.0.0.1로 고정됩니다.</p>
     */
    private final ConcurrentHashMap<PassiveListenerKey, Channel> passiveListenerChannels = new ConcurrentHashMap<>();

    /**
     * listener 별 멤버십(이 listener를 필요로 하는 eqpId 집합)입니다.
     *
     * <p>여러 PASSIVE 장비가 동일 interface+port를 공유할 수 있으므로, 마지막 멤버 제거 시에만
     * listener 채널을 종료해야 합니다.</p>
     */
    private final ConcurrentHashMap<PassiveListenerKey, Set<String>> passiveListenerMembers = new ConcurrentHashMap<>();

    /**
     * eqpId -> listenerKey 역참조 맵입니다.
     *
     * <p>UI END/DELETE, 설정 변경 시 기존 listener 멤버십을 빠르게 정리하기 위해 사용합니다.</p>
     */
    private final ConcurrentHashMap<String, PassiveListenerKey> passiveListenerKeyByEqpId = new ConcurrentHashMap<>();

    /**
     * PASSIVE SOCKET 공유 listener별 socketType 제약값입니다.
     *
     * <p>정책상 동일 PASSIVE(listener) 포트에는 하나의 socketType만 허용하므로,
     * listener 생성/멤버 추가 시 이 맵을 사용하여 충돌을 즉시 차단합니다.</p>
     */
    private final ConcurrentHashMap<PassiveListenerKey, String> passiveListenerSocketTypeConstraints = new ConcurrentHashMap<>();

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
            final GatewaySocketProperties socketProperties,
            final EquipmentInfoProvider equipmentInfoProvider,
            final EquipmentContextRegistry equipmentContextRegistry,
            final GatewayChannelHandlerFactory handlerFactory,
            final KafkaShardOwnership shardOwnership,
            final EquipmentLifecycleStateMachine lifecycleStateMachine
    ) {
        this.nettyProperties = Objects.requireNonNull(nettyProperties, "nettyProperties is null");
        this.socketProperties = Objects.requireNonNull(socketProperties, "socketProperties is null");
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
         * - 게이트웨이 기준 ACTIVE  : 아웃바운드 연결 시도
         * - 게이트웨이 기준 PASSIVE : 공유 listener 보장
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
        closeAllPassiveListeners();

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
        passiveListenerChannels.clear();
        passiveListenerMembers.clear();
        passiveListenerKeyByEqpId.clear();
        passiveListenerSocketTypeConstraints.clear();

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
     * 장비 1대의 통신 런타임을 게이트웨이 기준 모드에 맞게 시작합니다.
     *
     * <p>동작 규칙:</p>
     * <p>1) shard ownership 및 enabled 여부를 확인합니다.</p>
     * <p>2) 게이트웨이 기준 ACTIVE는 기존 아웃바운드 연결/재연결 로직을 사용합니다.</p>
     * <p>3) 게이트웨이 기준 PASSIVE는 공유 listener(interface + port)를 보장합니다.</p>
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

            final GatewayEquipmentInfo info = resolveRuntimeEquipmentInfo(eqpId);
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
             * 모드 전환(예: PASSIVE -> ACTIVE) 이후 stale listener 멤버십이 남지 않도록
             * 게이트웨이 ACTIVE(아웃바운드) 시작 경로에서는 먼저 PASSIVE listener 멤버십을 정리합니다.
             */
            if (info.connectionMode() == ConnectionMode.ACTIVE) {
                if (log.isDebugEnabled()) {
                    log.debug("Transport runtime start path selected. eqpId={}, gatewayMode=ACTIVE(outbound)", eqpId);
                }
                releasePassiveListenerMembership(eqpId, "MODE_SWITCH_OR_ACTIVE_START");
                resumeActiveReconnect(eqpId);
                connectActiveIfPossible(eqpId);
                return;
            }

            try {
                if (log.isDebugEnabled()) {
                    log.debug("Transport runtime start path selected. eqpId={}, gatewayMode=PASSIVE(listener)", eqpId);
                }
                reconnectSuppressedEqpIds.remove(eqpId);
                ensurePassiveListenerForEquipment(info, "RUNTIME_START");
            } catch (Exception ex) {
                /**
                 * listener 생성 실패 시 멤버십 맵에 잔여 상태가 남으면 이후 재시도/정지 로직이 혼란스러워질 수 있으므로
                 * 즉시 정리합니다.
                 */
                releasePassiveListenerMembership(eqpId, "PASSIVE_LISTENER_START_FAILED_CLEANUP");
                lifecycleStateMachine.onStartFailedIfPending(eqpId, "SYSTEM", "PASSIVE_LISTENER_START_FAILED");
                log.error("PASSIVE listener runtime start failed. eqpId={}, interfaceType={}, port={}",
                        eqpId,
                        info.commInterfaceType(),
                        info.eqpPort(),
                        ex);
            }
        });
    }

    /**
     * 장비 1대의 통신 런타임을 게이트웨이 기준 모드에 맞게 정지합니다.
     *
     * <p>이 메서드는 모드별 런타임 자원만 정리합니다.</p>
     * <p>- ACTIVE  : 자동 재연결 억제 (아웃바운드 중단)</p>
     * <p>- PASSIVE : 공유 listener 멤버십 해제 및 마지막 멤버일 때 listener 종료</p>
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
            final GatewayEquipmentInfo info = resolveRuntimeEquipmentInfo(eqpId);
            final ConnectionMode mode = info == null ? null : info.connectionMode();

            if (mode == ConnectionMode.PASSIVE) {
                if (log.isDebugEnabled()) {
                    log.debug("Transport runtime stop path selected. eqpId={}, gatewayMode=PASSIVE(listener)", eqpId);
                }
                releasePassiveListenerMembership(eqpId, "RUNTIME_STOP");
                return;
            }

            /**
             * ACTIVE 또는 DB 조회 실패(null) 케이스는 재연결 억제를 수행합니다.
             * DB 조회 실패 시에도 stale suppress 상태 정리에 의미가 있으므로 그대로 호출합니다.
             */
            if (log.isDebugEnabled()) {
                log.debug("Transport runtime stop path selected. eqpId={}, gatewayMode={} (outbound suppress path)",
                        eqpId,
                        mode);
            }
            suppressActiveReconnect(eqpId);
            releasePassiveListenerMembership(eqpId, "RUNTIME_STOP_FALLBACK_CLEANUP");
        });
    }

    /**
     * 특정 설비에 대한 아웃바운드 연결을 즉시 시도합니다.
     *
     * <p>이 메서드는 게이트웨이 기준 ACTIVE(아웃바운드) 모드 설비에 대해서만 실제 연결을 수행합니다.</p>
     * <p>운영 제어(UI 등)에서 START 요청 시 호출되며, 모드가 ACTIVE가 아니면 DEBUG 로그만 남기고 종료합니다.</p>
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

            final GatewayEquipmentInfo info = resolveRuntimeEquipmentInfo(eqpId);
            if (info == null) {
                log.warn("Outbound connect skipped (equipment not found). eqpId={}", eqpId);
                return;
            }
            if (!info.enabled()) {
                log.warn("Outbound connect skipped (equipment disabled). eqpId={}", eqpId);
                return;
            }
            if (info.connectionMode() != ConnectionMode.ACTIVE) {
                if (log.isDebugEnabled()) {
                    log.debug("Outbound connect skipped (gateway mode is not ACTIVE). eqpId={}, mode={}",
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
     * <p>게이트웨이 기준 ACTIVE(아웃바운드) 경로에서만 실제 의미가 있으며,
     * PASSIVE(listener) 모드에서는 재연결 스케줄 자체를 사용하지 않습니다.</p>
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
     * <p>게이트웨이 기준 ACTIVE(아웃바운드) 경로에서 START/재시도 재개 시 사용합니다.</p>
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
        validatePassiveSocketListenerConstraintsOnBootstrap(contexts);

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
     * 게이트웨이 기준 PASSIVE 장비용 공유 listener를 보장합니다.
     *
     * <p>공유 기준은 interface + port이며, bind IP는 정책상 127.0.0.1 고정입니다.</p>
     *
     * @param info 장비 정보
     * @param reason 호출 사유(로그용)
     */
    private synchronized void ensurePassiveListenerForEquipment(final GatewayEquipmentInfo info, final String reason) {
        Objects.requireNonNull(info, "info is null");

        final String eqpId = info.equipmentId();
        final PassiveListenerKey nextKey = PassiveListenerKey.from(info.commInterfaceType(), info.eqpPort());
        final String resolvedSocketType = resolveSocketTypeOrDefault(info);

        /**
         * PASSIVE(listener) 모드에서는 DB의 eqp_ip를 bind IP로 사용하지 않고
         * 정책상 고정값(127.0.0.1)을 사용합니다.
         * 운영 확인을 위해 DEBUG 로그로 DB값과 실제 bind 정책값을 함께 남깁니다.
         */
        if (log.isDebugEnabled() && info.eqpIp() != null && !info.eqpIp().isBlank()) {
            log.debug("PASSIVE listener bind IP policy applied. eqpId={}, dbEqpIp={}, bindIp={}",
                    eqpId,
                    info.eqpIp(),
                    PASSIVE_LISTENER_BIND_IP);
        }

        final PassiveListenerKey currentKey = passiveListenerKeyByEqpId.get(eqpId);
        if (currentKey != null && !currentKey.equals(nextKey)) {
            log.info("PASSIVE listener membership key changed. eqpId={}, previousKey={}, nextKey={}, reason={}",
                    eqpId,
                    currentKey,
                    nextKey,
                    reason);
            releasePassiveListenerMembership(eqpId, "PASSIVE_LISTENER_KEY_CHANGED");
        }

        validateAndRegisterPassiveListenerSocketTypeConstraint(nextKey, eqpId, resolvedSocketType, reason);

        final Set<String> members = passiveListenerMembers.computeIfAbsent(nextKey, key -> ConcurrentHashMap.newKeySet());
        members.add(eqpId);
        passiveListenerKeyByEqpId.put(eqpId, nextKey);

        final Channel existing = passiveListenerChannels.get(nextKey);
        if (existing != null && existing.isActive()) {
            if (log.isDebugEnabled()) {
                log.debug("PASSIVE shared listener already running. eqpId={}, listenerKey={}, memberCount={}, reason={}",
                        eqpId,
                        nextKey,
                        members.size(),
                        reason);
                if (nextKey.interfaceType() == CommInterfaceType.SOCKET) {
                    log.debug("PASSIVE SOCKET listener 제약 유지. listenerKey={}, socketType={}",
                            nextKey,
                            passiveListenerSocketTypeConstraints.get(nextKey));
                }
            }
            return;
        }

        if (existing != null) {
            passiveListenerChannels.remove(nextKey, existing);
            safeClose(existing);
            if (log.isDebugEnabled()) {
                log.debug("Removed stale PASSIVE listener channel before restart. listenerKey={}", nextKey);
            }
        }

        final Channel started = startPassiveListenerServer(nextKey);
        passiveListenerChannels.put(nextKey, started);
        log.info("PASSIVE shared listener ensured. eqpId={}, listenerKey={}, memberCount={}, socketType={}, reason={}",
                eqpId,
                nextKey,
                members.size(),
                nextKey.interfaceType() == CommInterfaceType.SOCKET ? passiveListenerSocketTypeConstraints.get(nextKey) : null,
                reason);
    }

    /**
     * 게이트웨이 기준 PASSIVE 장비의 공유 listener 멤버십을 해제합니다.
     *
     * <p>마지막 멤버가 제거되면 해당 listener 채널을 종료합니다.</p>
     *
     * @param eqpId 장비 ID
     * @param reason 호출 사유(로그용)
     */
    private synchronized void releasePassiveListenerMembership(final String eqpId, final String reason) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        final PassiveListenerKey key = passiveListenerKeyByEqpId.remove(eqpId);
        if (key == null) {
            return;
        }

        final Set<String> members = passiveListenerMembers.get(key);
        if (members == null) {
            passiveListenerMembers.remove(key);
            passiveListenerSocketTypeConstraints.remove(key);
            return;
        }

        members.remove(eqpId);
        if (!members.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("PASSIVE listener membership removed. eqpId={}, listenerKey={}, remainingMembers={}, reason={}",
                        eqpId,
                        key,
                        members.size(),
                        reason);
            }
            return;
        }

        passiveListenerMembers.remove(key, members);
        final String removedSocketType = passiveListenerSocketTypeConstraints.remove(key);
        final Channel channel = passiveListenerChannels.remove(key);
        safeClose(channel);
        log.info("PASSIVE shared listener stopped (last member removed). eqpId={}, listenerKey={}, socketType={}, reason={}",
                eqpId,
                key,
                key.interfaceType() == CommInterfaceType.SOCKET ? removedSocketType : null,
                reason);
    }

    /**
     * 게이트웨이 종료 시 PASSIVE 공유 listener 채널을 일괄 종료합니다.
     *
     * <p>게이트웨이 기준 PASSIVE는 서버(listener) 역할이므로 종료 시 전체 listener를 정리해야
     * 다음 기동에서 포트 점유 잔여 상태가 남지 않습니다.</p>
     */
    private synchronized void closeAllPassiveListeners() {
        if (passiveListenerChannels.isEmpty()) {
            return;
        }

        for (PassiveListenerKey key : List.copyOf(passiveListenerChannels.keySet())) {
            final Channel channel = passiveListenerChannels.remove(key);
            safeClose(channel);
            if (log.isDebugEnabled()) {
                log.debug("PASSIVE shared listener closed during gateway stop. listenerKey={}", key);
            }
        }
        passiveListenerSocketTypeConstraints.clear();
    }

    /**
     * PASSIVE 공유 listener 서버를 실제로 바인드합니다.
     *
     * <p>게이트웨이 기준 PASSIVE(listener) 모드에서 설비가 접속하는 수신 서버 경로를 생성하며,
     * child pipeline에는 PASSIVE handler(수신 바인딩 경로)를 사용합니다.</p>
     *
     * @param key 공유 listener 식별 키
     * @return 바인드된 서버 채널
     */
    private Channel startPassiveListenerServer(final PassiveListenerKey key) {
        Objects.requireNonNull(key, "key is null");
        final String listenerSocketType = key.interfaceType() == CommInterfaceType.SOCKET
                ? requirePassiveListenerSocketTypeConstraint(key)
                : null;

        if (bossGroup == null || workerGroup == null) {
            throw new IllegalStateException("Netty event loop groups are not initialized");
        }

        try {
            final ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        /**
                         * 게이트웨이 기준 PASSIVE(listener) 경로이므로
                         * 게이트웨이 측 수신 바인딩 핸들러를 등록합니다.
                         */
                        @Override
                        protected void initChannel(final SocketChannel ch) {
                            ch.pipeline().addLast(handlerFactory.newPassiveHandler(key.interfaceType(), listenerSocketType));
                        }
                    });

            final ChannelFuture future = bootstrap.bind(PASSIVE_LISTENER_BIND_IP, key.port()).sync();
            log.info("PASSIVE shared listener started. listenerKey={}, bindIp={}, bindPort={}, socketType={}",
                    key,
                    PASSIVE_LISTENER_BIND_IP,
                    key.port(),
                    listenerSocketType);
            return future.channel();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PASSIVE shared listener bind interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("PASSIVE shared listener bind failed. key=" + key, ex);
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
            final GatewayEquipmentInfo runtimeInfo = resolveLatestRuntimeEquipmentInfo(info);
            final String resolvedSocketType = resolveSocketTypeOrDefault(runtimeInfo);

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
            if (runtimeInfo.eqpIp() == null || runtimeInfo.eqpIp().isBlank()) {
                log.warn("Outbound connect skipped (missing eqpIp). eqpId={}", eqpId);
                return;
            }
            if (runtimeInfo.eqpPort() == null || runtimeInfo.eqpPort() <= 0) {
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
                            ch.pipeline().addLast(handlerFactory.newActiveHandler(
                                    runtimeInfo.commInterfaceType(),
                                    eqpId,
                                    resolvedSocketType
                            ));
                        }
                    });

            if (log.isDebugEnabled()) {
                log.debug("Outbound connect attempt. eqpId={}, ip={}, port={}, socketType={}",
                        eqpId,
                        runtimeInfo.eqpIp(),
                        runtimeInfo.eqpPort(),
                        resolvedSocketType);
            }

            bootstrap.connect(runtimeInfo.eqpIp(), runtimeInfo.eqpPort()).addListener((ChannelFuture future) ->
                    withEqpLogContext(eqpId, () -> {
                        if (!future.isSuccess()) {
                            final String errorMessage = future.cause() == null ? "unknown" : future.cause().getMessage();
                            handleOutboundAttemptFailure(runtimeInfo, "TCP_CONNECT_FAILED: " + errorMessage);
                            return;
                        }

                        log.info("Outbound TCP connect success. eqpId={}, socketType={}", eqpId, resolvedSocketType);
                        final Channel channel = future.channel();
                        channel.closeFuture().addListener(closeFuture -> handleOutboundChannelClosed(runtimeInfo, channel));
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
     * 부팅 직후 메모리 컨텍스트 기준으로 PASSIVE SOCKET 포트/socketType 제약 위반이 없는지 검증합니다.
     *
     * <p>이 메서드는 gateway 기준 PASSIVE(listener) 모드 설비만 검사 대상으로 삼습니다.</p>
     * <p>정책상 동일 PASSIVE(listener) 포트에는 하나의 socketType만 허용하므로, 부팅 시점에 충돌을 먼저 탐지하여
     * 리스너 생성 도중 뒤늦게 문제를 발견하지 않도록 fail-fast 합니다.</p>
     *
     * @param contexts 부팅 시점 컨텍스트 스냅샷
     */
    private void validatePassiveSocketListenerConstraintsOnBootstrap(final List<EquipmentContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return;
        }

        final Map<PassiveListenerKey, String> socketTypeByListenerKey = new ConcurrentHashMap<>();
        for (EquipmentContext context : contexts) {
            if (context == null || context.profile() == null || context.profile().equipmentInfo() == null) {
                continue;
            }

            final GatewayEquipmentInfo info = context.profile().equipmentInfo();
            if (!info.enabled()
                    || info.commInterfaceType() != CommInterfaceType.SOCKET
                    || info.connectionMode() != ConnectionMode.PASSIVE) {
                continue;
            }

            final PassiveListenerKey key = PassiveListenerKey.from(info.commInterfaceType(), info.eqpPort());
            final String resolvedSocketType = resolveSocketTypeOrDefault(info);
            final String existing = socketTypeByListenerKey.putIfAbsent(key, resolvedSocketType);
            if (existing != null && !existing.equals(resolvedSocketType)) {
                log.error("PASSIVE SOCKET listener 정책 위반(부팅 검증). listenerKey={}, existingSocketType={}, incomingSocketType={}, eqpId={}",
                        key,
                        existing,
                        resolvedSocketType,
                        info.equipmentId());
                throw new IllegalStateException(
                        "PASSIVE SOCKET listener port conflict: different socketType on same port. key=" + key
                );
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("PASSIVE SOCKET listener 부팅 제약 검증 완료. listenerKeyCount={}", socketTypeByListenerKey.size());
        }
    }

    /**
     * PASSIVE 공유 listener에 SOCKET socketType 제약값을 등록/검증합니다.
     *
     * <p>동일 listenerKey(interface+port)에 다른 socketType 설비가 합류하려고 하면 즉시 예외를 발생시켜
     * 운영 정책 위반을 fail-fast 처리합니다.</p>
     *
     * @param key 공유 listener 키
     * @param eqpId 설비 ID(로그용)
     * @param resolvedSocketType 해석 완료된 socketType
     * @param reason 호출 사유(로그용)
     */
    private void validateAndRegisterPassiveListenerSocketTypeConstraint(
            final PassiveListenerKey key,
            final String eqpId,
            final String resolvedSocketType,
            final String reason
    ) {
        if (key.interfaceType() != CommInterfaceType.SOCKET) {
            return;
        }
        if (resolvedSocketType == null || resolvedSocketType.isBlank()) {
            throw new IllegalStateException("PASSIVE SOCKET listener requires socketType. eqpId=" + eqpId + ", key=" + key);
        }

        final String existing = passiveListenerSocketTypeConstraints.putIfAbsent(key, resolvedSocketType);
        if (existing == null) {
            if (log.isDebugEnabled()) {
                log.debug("PASSIVE SOCKET listener socketType 제약 등록. eqpId={}, listenerKey={}, socketType={}, reason={}",
                        eqpId,
                        key,
                        resolvedSocketType,
                        reason);
            }
            return;
        }

        if (!existing.equals(resolvedSocketType)) {
            log.error("PASSIVE SOCKET listener 정책 위반. eqpId={}, listenerKey={}, existingSocketType={}, incomingSocketType={}, reason={}",
                    eqpId,
                    key,
                    existing,
                    resolvedSocketType,
                    reason);
            throw new IllegalStateException(
                    "PASSIVE SOCKET listener socketType mismatch on same port. key=" + key
                            + ", existing=" + existing
                            + ", incoming=" + resolvedSocketType
            );
        }
    }

    /**
     * PASSIVE SOCKET 공유 listener의 socketType 제약값을 조회합니다.
     *
     * @param key 공유 listener 키
     * @return 등록된 socketType
     */
    private String requirePassiveListenerSocketTypeConstraint(final PassiveListenerKey key) {
        if (key.interfaceType() != CommInterfaceType.SOCKET) {
            return null;
        }

        final String socketType = passiveListenerSocketTypeConstraints.get(key);
        if (socketType == null || socketType.isBlank()) {
            throw new IllegalStateException("Missing PASSIVE SOCKET listener socketType constraint. key=" + key);
        }
        return socketType;
    }

    /**
     * 런타임 소스 오브 트루스(메모리 컨텍스트)를 우선하여 설비 정보를 조회합니다.
     *
     * <p>컨텍스트가 존재하면 DB 재조회 대신 메모리 스냅샷을 사용하고, 없을 때만 DB provider로 fallback 합니다.</p>
     *
     * @param eqpId 설비 ID
     * @return 설비 정보, 없으면 null
     */
    private GatewayEquipmentInfo resolveRuntimeEquipmentInfo(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return null;
        }

        final EquipmentContext context = equipmentContextRegistry.find(eqpId).orElse(null);
        if (context != null && context.profile() != null && context.profile().equipmentInfo() != null) {
            final GatewayEquipmentInfo info = context.profile().equipmentInfo();
            if (log.isDebugEnabled()) {
                log.debug("런타임 설비 정보 조회(메모리 컨텍스트 사용). eqpId={}, interfaceType={}, mode={}",
                        eqpId,
                        info.commInterfaceType(),
                        info.connectionMode());
            }
            return info;
        }

        final GatewayEquipmentInfo info = equipmentInfoProvider.findById(eqpId).orElse(null);
        if (log.isDebugEnabled() && info != null) {
            log.debug("런타임 설비 정보 조회(DB provider fallback). eqpId={}, interfaceType={}, mode={}",
                    eqpId,
                    info.commInterfaceType(),
                    info.connectionMode());
        }
        return info;
    }

    /**
     * 기존 설비 정보(fallback)와 현재 메모리 컨텍스트 값을 비교하여 최신 런타임 설비 정보를 선택합니다.
     *
     * <p>재연결 스케줄링 등 비동기 경로에서는 과거 스냅샷이 전달될 수 있으므로,
     * 실제 연결 시도 직전에 최신 런타임 컨텍스트로 한 번 더 보정합니다.</p>
     *
     * @param fallbackInfo 기존 호출 경로에서 전달된 설비 정보
     * @return 최신 런타임 설비 정보(없으면 fallbackInfo)
     */
    private GatewayEquipmentInfo resolveLatestRuntimeEquipmentInfo(final GatewayEquipmentInfo fallbackInfo) {
        Objects.requireNonNull(fallbackInfo, "fallbackInfo is null");

        final GatewayEquipmentInfo latest = resolveRuntimeEquipmentInfo(fallbackInfo.equipmentId());
        if (latest == null) {
            return fallbackInfo;
        }
        return latest;
    }

    /**
     * 설비 정보에서 SOCKET socketType을 해석합니다.
     *
     * <p>SOCKET 설비는 `info.socketType()` 우선, 미설정이면 `default-socket-type` fallback을 적용합니다.
     * HSMS 설비는 socketType이 의미 없으므로 null을 반환합니다.</p>
     *
     * @param info 설비 정보
     * @return 해석된 socketType (HSMS면 null)
     */
    private String resolveSocketTypeOrDefault(final GatewayEquipmentInfo info) {
        Objects.requireNonNull(info, "info is null");
        if (info.commInterfaceType() != CommInterfaceType.SOCKET) {
            return null;
        }

        final String fromEquipment = normalizeText(info.socketType());
        if (fromEquipment != null) {
            return fromEquipment;
        }

        final String fallback = normalizeText(socketProperties.getDefaultSocketType());
        if (fallback == null) {
            throw new IllegalStateException("Default SOCKET socketType is not configured");
        }
        if (log.isDebugEnabled()) {
            log.debug("설비 socketType 미설정으로 기본값 fallback 적용. eqpId={}, socketType={}",
                    info.equipmentId(),
                    fallback);
        }
        return fallback;
    }

    /**
     * 문자열 정규화 유틸리티입니다.
     *
     * @param text 입력 문자열
     * @return trim 결과가 비어 있지 않으면 해당 문자열, 아니면 null
     */
    private String normalizeText(final String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 게이트웨이 기준 PASSIVE 공유 listener 식별 키입니다.
     *
     * <p>bind IP는 정책상 {@code 127.0.0.1}로 고정이므로 키에는 포함하지 않고,
     * interface + port 조합으로 공유 여부를 판단합니다.</p>
     */
    private record PassiveListenerKey(
            CommInterfaceType interfaceType,
            int port
    ) {
        /**
         * 장비 정보에서 공유 listener 키를 생성합니다.
         *
         * @param interfaceType 인터페이스 타입(HSMS/SOCKET)
         * @param port tc_eqp.eqp_port 값 (PASSIVE 모드에서는 게이트웨이 bind port로 사용)
         * @return 공유 listener 키
         */
        private static PassiveListenerKey from(final CommInterfaceType interfaceType, final Integer port) {
            if (interfaceType == null) {
                throw new IllegalArgumentException("interfaceType is null");
            }
            if (port == null || port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Invalid PASSIVE listener port: " + port);
            }
            return new PassiveListenerKey(interfaceType, port);
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
