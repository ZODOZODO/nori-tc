package com.nori.tc.comm.adapters.netty;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.gateway.comm.ConnectionMode;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.kafka.KafkaShardOwnership;
import com.nori.tc.comm.gateway.lifecycle.EqpLifecycleStateMachine;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * EqpBindingService 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */
@Service
public class EqpBindingService {

    private static final Logger log = LoggerFactory.getLogger(EqpBindingService.class);

    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayProcessingService processingService;
    private final KafkaShardOwnership shardOwnership;
    private final EqpLifecycleStateMachine lifecycleStateMachine;

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    public EqpBindingService(
            final EquipmentChannelRegistry channelRegistry,
            final GatewayProcessingService processingService,
            final KafkaShardOwnership shardOwnership,
            final EqpLifecycleStateMachine lifecycleStateMachine
    ) {
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.shardOwnership = Objects.requireNonNull(shardOwnership, "shardOwnership is null");
        this.lifecycleStateMachine = Objects.requireNonNull(lifecycleStateMachine, "lifecycleStateMachine is null");
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    public BindResult bindPassive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.PASSIVE, channel);
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    public BindResult bindActive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.ACTIVE, channel);
    }

    /**
     * unbind 기능을 수행합니다.
     *
     * @param channel 입력 값
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
        lifecycleStateMachine.onChannelDisconnected(eqpId, "SYSTEM", "NETTY_UNBIND");

        log.info("Channel unbound. eqpId={}", eqpId);
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
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
        lifecycleStateMachine.onChannelConnected(eqpId, expectedMode.name(), "NETTY_BIND");

        log.info("Bind success. eqpId={}, interfaceType={}, mode={}", eqpId, interfaceType, expectedMode);
        return BindResult.OK;
    }

    /**
     * BindResult 열거형입니다.
     *
     * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
     * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
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
}
