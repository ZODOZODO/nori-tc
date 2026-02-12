package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.context.EquipmentContextRegistry;
import com.nori.tc.comm.gateway.context.EquipmentRuntimeState;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.kafka.KafkaShardOwnership;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * eqpId 바인딩/해제 서비스입니다.
 *
 * <p>역할:</p>
 * <p>- Netty 채널 바인딩 검증 및 등록</p>
 * <p>- mailbox 생성/정리 위임</p>
 * <p>- EquipmentContextRegistry 런타임 상태 동기화</p>
 */
@Service
public class EqpBindingService {

    private static final Logger log = LoggerFactory.getLogger(EqpBindingService.class);

    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayProcessingService processingService;
    private final KafkaShardOwnership shardOwnership;
    private final EquipmentContextRegistry contextRegistry;

    /**
     * 바인딩 처리 의존성을 초기화합니다.
     */
    public EqpBindingService(
            final EquipmentChannelRegistry channelRegistry,
            final GatewayProcessingService processingService,
            final KafkaShardOwnership shardOwnership,
            final EquipmentContextRegistry contextRegistry
    ) {
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.shardOwnership = Objects.requireNonNull(shardOwnership, "shardOwnership is null");
        this.contextRegistry = Objects.requireNonNull(contextRegistry, "contextRegistry is null");
    }

    /**
     * PASSIVE 바인딩을 처리합니다.
     */
    public BindResult bindPassive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.PASSIVE, channel);
    }

    /**
     * ACTIVE 바인딩을 처리합니다.
     */
    public BindResult bindActive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.ACTIVE, channel);
    }

    /**
     * 채널 해제 시 registry/mailbox/context 상태를 정리합니다.
     */
    public void unbind(final Channel channel) {
        if (channel == null) {
            return;
        }

        final String eqpId = NettyChannelAttributes.getEqpId(channel);
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }

        channelRegistry.unregister(new EquipmentId(eqpId));
        processingService.removeMailbox(eqpId);
        contextRegistry.find(eqpId).ifPresent(ctx ->
                ctx.updateRuntimeState(EquipmentRuntimeState.DISCONNECTED, "NETTY_UNBIND", "SYSTEM")
        );

        log.info("Channel unbound. eqpId={}", eqpId);
    }

    /**
     * 공통 바인딩 검증/등록 로직입니다.
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
        if (log.isDebugEnabled()) {
            log.debug("Bind attempt. eqpId={}, interfaceType={}, mode={}", eqpId, interfaceType, expectedMode);
        }

        if (!shardOwnership.isOwned(eqpId)) {
            return BindResult.NOT_OWNED;
        }

        final GatewayEquipmentInfo info;
        try {
            info = processingService.resolveEquipment(eqpId);
        } catch (Exception ex) {
            return BindResult.UNKNOWN_EQUIPMENT;
        }

        if (!info.enabled()) {
            return BindResult.DISABLED;
        }
        if (info.commInterfaceType() != interfaceType) {
            return BindResult.COMM_INTERFACE_MISMATCH;
        }
        if (info.connectionMode() != expectedMode) {
            return BindResult.CONNECTION_MODE_MISMATCH;
        }

        final NettyEquipmentChannel equipmentChannel = new NettyEquipmentChannel(channel);
        final boolean bound = channelRegistry.tryBind(new EquipmentId(eqpId), equipmentChannel);
        if (!bound) {
            return BindResult.DUPLICATE_CONNECTION;
        }

        processingService.bindMailbox(info, equipmentChannel);
        contextRegistry.find(eqpId).ifPresent(ctx ->
                ctx.updateRuntimeState(EquipmentRuntimeState.CONNECTED, "NETTY_BIND", expectedMode.name())
        );

        log.info("Bind success. eqpId={}, interfaceType={}, mode={}", eqpId, interfaceType, expectedMode);
        return BindResult.OK;
    }

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
}
