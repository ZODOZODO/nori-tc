package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * eqpId 기반 outbound sender 구현체
 *
 * - eqpId에 매핑된 채널이 없으면 즉시 예외를 던집니다.
 * - 예외는 상위(EqpSequentialProcessor)에서 DLQ/Quarantine로 처리됩니다.
 */
public final class ChannelBasedOutboundSender implements OutboundSenderPort {

    private static final Logger log = LoggerFactory.getLogger(ChannelBasedOutboundSender.class);
    private final EquipmentChannelRegistry registry;

    public ChannelBasedOutboundSender(final EquipmentChannelRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry is null");
    }

    @Override
    public void send(final OutboundRawFrame frame) throws Exception {
        Objects.requireNonNull(frame, "frame is null");

        final EquipmentId equipmentId = frame.equipmentId();
        final EquipmentChannel channel = registry.get(equipmentId);

        if (channel == null || !channel.isActive()) {
            log.warn("Outbound send failed (no active channel). eqpId={}", equipmentId.value());
            throw new IllegalStateException("No channel registered for eqpId=" + equipmentId.value());
        }

        if (log.isDebugEnabled()) {
            log.debug("Outbound send to channel. eqpId={}, bytes={}", equipmentId.value(), frame.bytes().length);
        }
        channel.send(frame);
    }
}
