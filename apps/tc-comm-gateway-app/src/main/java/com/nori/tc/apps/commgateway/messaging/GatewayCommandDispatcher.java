package com.nori.tc.apps.commgateway.messaging;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.OutboundSenderPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceNoGeneratorPort;
import com.nori.tc.comm.domain.dlq.DlqMessage;
import com.nori.tc.comm.domain.dlq.DlqReasonCode;
import com.nori.tc.comm.domain.type.CommInterfaceType;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Kafka 커맨드를 실제 설비 전송으로 변환하는 디스패처
 */
@Component
public class GatewayCommandDispatcher {

    private final OutboundSenderPort outboundSenderPort;
    private final ClockPort clockPort;
    private final TraceNoGeneratorPort traceNoGeneratorPort;
    private final DlqPublisherPort dlqPublisherPort;
    private final QuarantinePort quarantinePort;

    public GatewayCommandDispatcher(
            final OutboundSenderPort outboundSenderPort,
            final ClockPort clockPort,
            final TraceNoGeneratorPort traceNoGeneratorPort,
            final DlqPublisherPort dlqPublisherPort,
            final QuarantinePort quarantinePort
    ) {
        this.outboundSenderPort = Objects.requireNonNull(outboundSenderPort, "outboundSenderPort is null");
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.traceNoGeneratorPort = Objects.requireNonNull(traceNoGeneratorPort, "traceNoGeneratorPort is null");
        this.dlqPublisherPort = Objects.requireNonNull(dlqPublisherPort, "dlqPublisherPort is null");
        this.quarantinePort = Objects.requireNonNull(quarantinePort, "quarantinePort is null");
    }

    public void dispatch(final GatewayCommandMessage message) {
        Objects.requireNonNull(message, "message is null");

        final byte[] payload;
        try {
            payload = Base64.getDecoder().decode(message.payloadBase64());
        } catch (IllegalArgumentException ex) {
            publishDlq(message, DlqReasonCode.BASE64_DECODE_FAIL, ex.getMessage());
            return;
        }

        final EquipmentId equipmentId;
        final CommInterfaceType interfaceType;
        try {
            equipmentId = new EquipmentId(message.equipmentId());
            interfaceType = CommInterfaceType.fromText(message.commInterfaceType());
        } catch (IllegalArgumentException ex) {
            publishDlq(message, DlqReasonCode.INVALID_INPUT, ex.getMessage());
            return;
        }
        final long now = clockPort.nowEpochMillis();

        final OutboundRawFrame frame = new OutboundRawFrame(
                equipmentId,
                interfaceType,
                message.socketType(),
                payload,
                now,
                "KAFKA_COMMAND"
        );

        try {
            outboundSenderPort.send(frame);
        } catch (Exception ex) {
            publishDlq(message, DlqReasonCode.PUBLISH_FAILED, ex.getMessage());
            try {
                quarantinePort.quarantine(equipmentId, DlqReasonCode.PUBLISH_FAILED.name(), "Outbound send failed");
            } catch (Exception ignored) {
            }
        }
    }

    private void publishDlq(
            final GatewayCommandMessage message,
            final DlqReasonCode reasonCode,
            final String reasonMessage
    ) {
        final String traceNo = traceNoGeneratorPort.newTraceNo();
        final long now = clockPort.nowEpochMillis();

        final DlqMessage dlqMessage = new DlqMessage(
                traceNoGeneratorPort.newTraceNo(),
                message.equipmentId(),
                traceNo,
                CommInterfaceType.fromText(message.commInterfaceType()),
                message.socketType(),
                DlqMessage.STAGE_PUBLISH,
                reasonCode,
                (reasonMessage == null || reasonMessage.isBlank()) ? "Command dispatch failed" : reasonMessage,
                now,
                null,
                DlqMessage.UNKNOWN_LENGTH,
                message.payloadBase64().length(),
                message.attributes() == null ? Map.of() : message.attributes()
        );

        try {
            dlqPublisherPort.publish(dlqMessage);
        } catch (Exception ignored) {
        }
    }
}
