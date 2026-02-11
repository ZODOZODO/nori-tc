package com.nori.tc.apps.commgateway.messaging;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.apps.commgateway.comm.EquipmentChannelRegistry;
import com.nori.tc.apps.commgateway.comm.GatewayProcessingService;
import com.nori.tc.apps.commgateway.metrics.GatewayLogContext;
import com.nori.tc.apps.commgateway.metrics.GatewayLogSampler;
import com.nori.tc.apps.commgateway.metrics.GatewayMetrics;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.domain.dlq.DlqMessage;
import com.nori.tc.comm.domain.dlq.DlqReasonCode;
import com.nori.tc.comm.domain.type.CommInterfaceType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Gateway inbound command dispatcher.
 *
 * Responsibilities
 * - Decodes Base64 payload into raw bytes.
 * - Validates equipmentId and commInterfaceType.
 * - Drops immediately if there is no active connection (spec requirement).
 * - Enqueues outbound frames into the per-eqp mailbox queue.
 *   (Actual send happens in worker threads via EqpProcessingCoordinator.)
 * - On failure, routes to DLQ and optionally quarantines the equipment.
 */
@Component
public class GatewayCommandDispatcher implements KafkaCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GatewayCommandDispatcher.class);

    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayProcessingService processingService;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;
    private final ClockPort clockPort;
    private final TraceIdGeneratorPort traceIdGeneratorPort;
    private final DlqPublisherPort dlqPublisherPort;
    private final QuarantinePort quarantinePort;

    public GatewayCommandDispatcher(
            final EquipmentChannelRegistry channelRegistry,
            final GatewayProcessingService processingService,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler,
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final DlqPublisherPort dlqPublisherPort,
            final QuarantinePort quarantinePort
    ) {
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.traceIdGeneratorPort = Objects.requireNonNull(traceIdGeneratorPort, "traceIdGeneratorPort is null");
        this.dlqPublisherPort = Objects.requireNonNull(dlqPublisherPort, "dlqPublisherPort is null");
        this.quarantinePort = Objects.requireNonNull(quarantinePort, "quarantinePort is null");
    }

    @Override
    public void dispatch(final KafkaCommandMessage message) {
        Objects.requireNonNull(message, "message is null");

        final String traceId = (message.traceId() == null || message.traceId().isBlank())
                ? traceIdGeneratorPort.newTraceId()
                : message.traceId();

        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(message.equipmentId(), traceId)) {

        final EquipmentId equipmentId;
        final CommInterfaceType interfaceType;

        try {
            equipmentId = new EquipmentId(message.equipmentId());
            interfaceType = CommInterfaceType.fromText(message.commInterfaceType());
        } catch (IllegalArgumentException ex) {
            publishDlq(message, DlqReasonCode.INVALID_INPUT, ex.getMessage(), traceId);
            return;
        }

        // 연결 없으면 즉시 drop (정상 처리)
        final var channel = channelRegistry.get(equipmentId);
        if (channel == null || !channel.isActive()) {
            if (logSampler.shouldLogCommandDrop()) {
                log.warn("Command drop (no connection). eqpId={}", equipmentId.value());
            }
            // metric: commands_drop_no_connection_total++
            metrics.incrementCommandsDropNoConnection();
            return;
        }

        final byte[] payload;
        try {
            payload = Base64.getDecoder().decode(message.payloadBase64());
        } catch (IllegalArgumentException ex) {
            publishDlq(message, DlqReasonCode.BASE64_DECODE_FAIL, ex.getMessage(), traceId);
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
            // Queue-based outbound: worker thread will encode/write in-order.
            processingService.enqueueOutbound(frame);
            if (log.isDebugEnabled()) {
                log.debug("Command enqueued to outbound queue. eqpId={}, traceId={}", equipmentId.value(), traceId);
            }
        } catch (Exception ex) {
            publishDlq(message, DlqReasonCode.PUBLISH_FAILED, ex.getMessage(), traceId);
            try {
                quarantinePort.quarantine(equipmentId, DlqReasonCode.PUBLISH_FAILED.name(), "Outbound send failed");
            } catch (Exception qex) {
            }
        }
        }
    }

    private void publishDlq(
            final KafkaCommandMessage message,
            final DlqReasonCode reasonCode,
            final String reasonMessage,
            final String traceId
    ) {
        CommInterfaceType commInterfaceType;
        try {
            commInterfaceType = CommInterfaceType.fromText(message.commInterfaceType());
        } catch (Exception ex) {
            // fallback to SOCKET to avoid DLQ failure due to invalid input
            // (actual reason is preserved in reasonCode/reasonMessage)
            commInterfaceType = CommInterfaceType.SOCKET;
        }

        final String resolvedTraceId = (traceId == null || traceId.isBlank())
                ? traceIdGeneratorPort.newTraceId()
                : traceId;
        final long now = clockPort.nowEpochMillis();

        final DlqMessage dlqMessage = new DlqMessage(
                traceIdGeneratorPort.newTraceId(),
                message.equipmentId(),
                resolvedTraceId,
                commInterfaceType,
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
