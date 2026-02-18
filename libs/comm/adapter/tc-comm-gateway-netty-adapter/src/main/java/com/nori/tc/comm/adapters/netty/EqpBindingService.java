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
 * eqpId 諛붿씤???댁젣 ?쒕퉬?ㅼ엯?덈떎.
 *
 * <p>??븷:</p>
 * <p>1) Netty 梨꾨꼸 諛붿씤??寃利?諛??깅줉</p>
 * <p>2) mailbox ?앹꽦/?뺣━ ?꾩엫</p>
 * <p>3) lifecycle ?곹깭癒몄떊?쇰줈 CONNECTED/DISCONNECTED ?대깽???꾨떖</p>
 */
@Service
public class EqpBindingService {

    private static final Logger log = LoggerFactory.getLogger(EqpBindingService.class);

    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayProcessingService processingService;
    private final KafkaShardOwnership shardOwnership;
    private final EqpLifecycleStateMachine lifecycleStateMachine;

    /**
     * 諛붿씤??泥섎━ ?섏〈?깆쓣 珥덇린?뷀빀?덈떎.
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
     * PASSIVE 諛붿씤?⑹쓣 泥섎━?⑸땲??
     */
    public BindResult bindPassive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.PASSIVE, channel);
    }

    /**
     * ACTIVE 諛붿씤?⑹쓣 泥섎━?⑸땲??
     */
    public BindResult bindActive(
            final String eqpId,
            final CommInterfaceType interfaceType,
            final Channel channel
    ) {
        return bindInternal(eqpId, interfaceType, ConnectionMode.ACTIVE, channel);
    }

    /**
     * 梨꾨꼸 ?댁젣 ??registry/mailbox ?뺣━? lifecycle ?대깽???꾨떖???섑뻾?⑸땲??
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
     * 怨듯넻 諛붿씤??寃利??깅줉 濡쒖쭅?낅땲??
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
