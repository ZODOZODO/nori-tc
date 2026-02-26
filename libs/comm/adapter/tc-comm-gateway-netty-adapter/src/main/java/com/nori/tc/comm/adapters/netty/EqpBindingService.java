package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.gateway.db.ConnectionMode;
import com.nori.tc.comm.gateway.runtime.channel.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.runtime.channel.EquipmentChannel;
import com.nori.tc.comm.gateway.application.ingress.GatewayIngressService;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.kafka.KafkaShardOwnership;
import com.nori.tc.comm.gateway.lifecycle.service.EquipmentLifecycleStateMachine;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Netty 채널을 설비(eqpId)에 바인딩/언바인딩하는 서비스입니다.
 *
 * <p>핵심 책임은 다음과 같습니다.</p>
 * <p>1) 바인딩 전 공통 검증(route_partition 기반 shard ownership, DB 등록, enabled, 인터페이스, 모드)을 수행합니다.</p>
 * <p>2) 채널 레지스트리 등록 및 mailbox 바인딩을 수행합니다.</p>
 * <p>3) 연결/해제 라이프사이클 이벤트를 상태머신으로 전달합니다.</p>
 *
 * <p>connectionMode는 게이트웨이 기준 의미를 사용합니다.</p>
 * <p>- ACTIVE : 게이트웨이가 접속 주체(아웃바운드 클라이언트)</p>
 * <p>- PASSIVE: 게이트웨이가 수신 주체(리스너 서버)</p>
 */
@Service
public class EqpBindingService {

    private static final Logger log = LoggerFactory.getLogger(EqpBindingService.class);

    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayIngressService gatewayIngressService;
    private final KafkaShardOwnership shardOwnership;
    private final EquipmentLifecycleStateMachine lifecycleStateMachine;

    /**
     * 바인딩 서비스 의존 객체를 초기화합니다.
     *
     * @param channelRegistry 설비 채널 레지스트리
     * @param gatewayIngressService 게이트웨이 인입/출력 큐 적재 진입 서비스
     * @param shardOwnership 샤드 소유권 판별기
     * @param lifecycleStateMachine 라이프사이클 상태머신
     */
    public EqpBindingService(
            final EquipmentChannelRegistry channelRegistry,
            final GatewayIngressService gatewayIngressService,
            final KafkaShardOwnership shardOwnership,
            final EquipmentLifecycleStateMachine lifecycleStateMachine
    ) {
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.gatewayIngressService = Objects.requireNonNull(gatewayIngressService, "gatewayIngressService is null");
        this.shardOwnership = Objects.requireNonNull(shardOwnership, "shardOwnership is null");
        this.lifecycleStateMachine = Objects.requireNonNull(lifecycleStateMachine, "lifecycleStateMachine is null");

        log.info("EqpBindingService initialized. gatewayModeInterpretation=GATEWAY_SIDE");
    }

    /**
     * 수신 경로(PASSIVE handler) 바인딩을 수행합니다.
     *
     * <p>수신 경로(PASSIVE handler)는 게이트웨이 리스너로 들어오는 경로이므로
     * 게이트웨이 기준 connectionMode는 PASSIVE여야 합니다.</p>
     *
     * @param eqpId 대상 설비 ID
     * @param interfaceType 인터페이스 타입
     * @param channel Netty 채널
     * @return 바인딩 결과
     */
    public BindResult bindPassive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.PASSIVE, channel);
    }

    /**
     * 발신 경로(ACTIVE handler) 바인딩을 수행합니다.
     *
     * <p>발신 경로(ACTIVE handler)는 게이트웨이가 먼저 설비로 붙는 경로이므로
     * 게이트웨이 기준 connectionMode는 ACTIVE여야 합니다.</p>
     *
     * @param eqpId 대상 설비 ID
     * @param interfaceType 인터페이스 타입
     * @param channel Netty 채널
     * @return 바인딩 결과
     */
    public BindResult bindActive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.ACTIVE, channel);
    }

    /**
     * 채널 언바인딩을 수행합니다.
     *
     * <p>채널에 기록된 eqpId를 기준으로 채널/메일박스를 정리하고,
     * 상태머신에 DISCONNECTED 이벤트를 전달합니다.</p>
     *
     * @param channel 해제 대상 Netty 채널
     */
    public void unbind(final Channel channel) {
        if (channel == null) {
            return;
        }

        final String eqpId = NettyChannelAttributes.getEqpId(channel);
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        withEqpLogContext(eqpId, () -> {
            final EquipmentChannel expectedChannel = NettyChannelAttributes.getBoundEquipmentChannel(channel);

            /*
             * 빠른 재연결 레이스 방어:
             * - 기존 채널(old)이 끊긴 직후 새 채널(new)이 같은 eqpId로 재바인딩될 수 있습니다.
             * - old channelInactive가 늦게 실행되면 eqpId만으로 unregister/remove 할 경우 new 매핑을 제거할 위험이 있습니다.
             * - 따라서 바인딩 시 저장한 EquipmentChannel 인스턴스와 "일치"하는 경우에만 cleanup을 수행합니다.
             */
            if (expectedChannel != null) {
                final boolean channelRemoved = channelRegistry.unregister(new EquipmentId(eqpId), expectedChannel);
                final boolean mailboxRemoved = gatewayIngressService.removeMailboxIfChannelMatches(eqpId, expectedChannel);

                if (!channelRemoved && !mailboxRemoved) {
                    if (log.isDebugEnabled()) {
                        log.debug("Channel unbind skipped because current mapping/mailbox belongs to another channel. eqpId={}",
                                eqpId);
                    }
                    return;
                }

                if (channelRemoved != mailboxRemoved) {
                    log.warn("Channel unbind cleanup partial result detected. eqpId={}, channelRemoved={}, mailboxRemoved={}",
                            eqpId,
                            channelRemoved,
                            mailboxRemoved);
                }

                lifecycleStateMachine.onChannelDisconnected(eqpId, "SYSTEM", "NETTY_UNBIND");
                NettyChannelAttributes.setBoundEquipmentChannel(channel, null);
                log.info("Channel unbound. eqpId={}", eqpId);
                return;
            }

            // 하위 호환 fallback: attribute가 없는 채널은 기존 eqpId 기반 정리 경로를 유지합니다.
            channelRegistry.unregister(new EquipmentId(eqpId));
            gatewayIngressService.removeMailbox(eqpId);
            lifecycleStateMachine.onChannelDisconnected(eqpId, "SYSTEM", "NETTY_UNBIND");
            log.warn("Channel unbound via legacy eqpId-only cleanup path. eqpId={}", eqpId);
        });
    }

    /**
     * 공통 바인딩 검증 및 실제 바인딩을 수행합니다.
     *
     * <p>검증 순서는 다음과 같습니다.</p>
     * <p>1) eqpId 유효성</p>
     * <p>2) 설비 조회</p>
     * <p>3) route_partition 기반 shard ownership</p>
     * <p>4) enabled / 인터페이스 / 모드 일치</p>
     * <p>5) 중복 채널 여부</p>
     *
     * @param eqpId 대상 설비 ID
     * @param interfaceType 채널 인터페이스 타입
     * @param expectedMode 현재 채널 경로에서 기대하는 게이트웨이 기준 connectionMode
     * @param channel Netty 채널
     * @return 바인딩 결과
     */
    private BindResult bindInternal(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final ConnectionMode expectedMode,
            final Channel channel
    ) {
        if (eqpId == null || eqpId.isBlank()) {
            return BindResult.INVALID_EQP_ID;
        }

        return executeWithEqpLogContext(eqpId, () -> {
            if (log.isDebugEnabled()) {
                log.debug("Bind attempt. eqpId={}, interfaceType={}, expectedGatewayMode={}",
                        eqpId, interfaceType, expectedMode);
            }

            final GatewayEquipmentInfo info;
            try {
                info = gatewayIngressService.resolveEquipment(eqpId);
            } catch (Exception ex) {
                return BindResult.UNKNOWN_EQUIPMENT;
            }

            if (!isOwnedByRoutePartition(info)) {
                if (log.isDebugEnabled()) {
                    log.debug("Bind rejected by route_partition ownership. eqpId={}, routePartition={}",
                            eqpId,
                            info.routePartition());
                }
                return BindResult.NOT_OWNED;
            }

            if (!info.enabled()) {
                return BindResult.DISABLED;
            }
            if (info.commInterfaceType() != interfaceType) {
                return BindResult.COMM_INTERFACE_MISMATCH;
            }
            if (info.connectionMode() != expectedMode) {
                if (log.isDebugEnabled()) {
                    log.debug("Bind rejected by connectionMode mismatch. eqpId={}, interfaceType={}, expectedGatewayMode={}, actualGatewayMode={}",
                            eqpId,
                            interfaceType,
                            expectedMode,
                            info.connectionMode());
                }
                return BindResult.CONNECTION_MODE_MISMATCH;
            }

            final NettyEquipmentChannel equipmentChannel = new NettyEquipmentChannel(channel);
            final boolean bound = channelRegistry.tryBind(new EquipmentId(eqpId), equipmentChannel);
            if (!bound) {
                return BindResult.DUPLICATE_CONNECTION;
            }

            gatewayIngressService.bindMailbox(info, equipmentChannel);
            NettyChannelAttributes.setBoundEquipmentChannel(channel, equipmentChannel);
            lifecycleStateMachine.onChannelConnected(eqpId, expectedMode.name(), "NETTY_BIND");

            log.info("Bind success. eqpId={}, interfaceType={}, gatewayMode={}", eqpId, interfaceType, expectedMode);
            return BindResult.OK;
        });
    }

    /**
     * 바인딩 결과 코드입니다.
     */
    public enum BindResult {
        OK,
        NOT_OWNED,
        DUPLICATE_CONNECTION,
        DISABLED,
        UNKNOWN_EQUIPMENT,
        INVALID_EQP_ID,
        COMM_INTERFACE_MISMATCH,
        CONNECTION_MODE_MISMATCH
    }

    /**
     * 설비 정보의 route_partition을 기준으로 현재 게이트웨이 인스턴스 소유 여부를 판정합니다.
     *
     * <p>U8부터 바인딩 전 소유권 검증은 eqpId 해시가 아니라 DB {@code tc_eqp.route_partition}를 기준으로 수행합니다.</p>
     * <p>routePartition이 null이면 미배정 상태로 간주하여 미소유(false) 처리합니다.</p>
     *
     * @param info 바인딩 대상 설비 정보
     * @return 현재 게이트웨이가 소유한 route_partition이면 true
     */
    private boolean isOwnedByRoutePartition(final GatewayEquipmentInfo info) {
        Objects.requireNonNull(info, "info is null");

        final Integer routePartition = info.routePartition();
        final boolean owned = shardOwnership.isOwnedPartition(routePartition);
        if (log.isDebugEnabled()) {
            log.debug("Bind route_partition ownership check. eqpId={}, routePartition={}, owned={}",
                    info.equipmentId(),
                    routePartition,
                    owned);
        }
        return owned;
    }

    /**
     * eqpId MDC를 적용한 상태로 단순 작업을 실행합니다.
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

    /**
     * eqpId MDC를 적용한 상태로 값을 반환하는 작업을 실행합니다.
     *
     * @param eqpId 설비 ID
     * @param supplier 실행 작업
     * @return 실행 결과
     * @param <T> 반환 타입
     */
    private <T> T executeWithEqpLogContext(final String eqpId, final java.util.function.Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        if (eqpId == null || eqpId.isBlank()) {
            return supplier.get();
        }
        try (GatewayLogContext ignored = GatewayLogContext.withEqpId(eqpId)) {
            return supplier.get();
        }
    }
}
