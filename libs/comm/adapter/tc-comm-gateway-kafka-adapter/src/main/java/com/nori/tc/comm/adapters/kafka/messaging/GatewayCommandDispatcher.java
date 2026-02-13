package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.adapters.kafka.messaging.contract.GatewayBusinessCommandMessage;
import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.gateway.comm.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.comm.GatewayProcessingService;
import com.nori.tc.comm.gateway.config.GatewaySocketProperties;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.dlq.DlqMessage;
import com.nori.tc.comm.gateway.domain.dlq.DlqReasonCode;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.comm.gateway.metrics.GatewayLogSampler;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeEncodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaCommandMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Gateway inbound command dispatcher입니다.
 *
 * <p>이 클래스는 두 가지 명령 수신 경로를 함께 처리합니다.</p>
 * <p>1) Legacy 경로: {@link KafkaCommandMessage} (payloadBase64 기반)</p>
 * <p>2) Business 경로: {@link GatewayBusinessCommandMessage} (metadata + data, rawMessage 기반)</p>
 *
 * <p>공통 책임:</p>
 * <p>- 입력 검증 및 인터페이스 타입 분기</p>
 * <p>- 활성 채널 확인 후 outbound mailbox enqueue</p>
 * <p>- 실패 시 DLQ 발행 및 필요 시 장비 격리(quarantine)</p>
 */
@Component
public class GatewayCommandDispatcher implements KafkaCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GatewayCommandDispatcher.class);

    /**
     * DLQ 기록 시 eqpId가 비어 있는 경우 사용할 대체 식별자입니다.
     */
    private static final String UNKNOWN_EQP_ID = "UNKNOWN_EQP";

    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayProcessingService processingService;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;
    private final ClockPort clockPort;
    private final TraceIdGeneratorPort traceIdGeneratorPort;
    private final DlqPublisherPort dlqPublisherPort;
    private final QuarantinePort quarantinePort;
    private final GatewaySocketProperties socketProperties;
    private final SocketTypeRegistry socketTypeRegistry;

    /**
     * Dispatcher 의존성을 초기화합니다.
     */
    public GatewayCommandDispatcher(
            final EquipmentChannelRegistry channelRegistry,
            final GatewayProcessingService processingService,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler,
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final DlqPublisherPort dlqPublisherPort,
            final QuarantinePort quarantinePort,
            final GatewaySocketProperties socketProperties,
            final SocketTypeRegistry socketTypeRegistry
    ) {
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.traceIdGeneratorPort = Objects.requireNonNull(traceIdGeneratorPort, "traceIdGeneratorPort is null");
        this.dlqPublisherPort = Objects.requireNonNull(dlqPublisherPort, "dlqPublisherPort is null");
        this.quarantinePort = Objects.requireNonNull(quarantinePort, "quarantinePort is null");
        this.socketProperties = Objects.requireNonNull(socketProperties, "socketProperties is null");
        this.socketTypeRegistry = Objects.requireNonNull(socketTypeRegistry, "socketTypeRegistry is null");
    }

    /**
     * Legacy 명령 계약(KafkaCommandMessage)을 처리합니다.
     *
     * <p>기존 동작을 보존하기 위해 payloadBase64를 raw bytes로 디코딩하여
     * outbound queue로 적재합니다.</p>
     */
    @Override
    public void dispatch(final KafkaCommandMessage message) {
        Objects.requireNonNull(message, "message is null");

        final String traceId = resolveTraceId(message.traceId());
        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(message.equipmentId(), traceId)) {
            final EquipmentId equipmentId;
            final CommInterfaceType interfaceType;

            try {
                equipmentId = new EquipmentId(message.equipmentId());
                interfaceType = CommInterfaceType.fromText(message.commInterfaceType());
            } catch (IllegalArgumentException ex) {
                publishLegacyDlq(message, DlqReasonCode.INVALID_INPUT, ex.getMessage(), traceId);
                return;
            }

            if (!hasActiveChannel(equipmentId)) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("Command drop (no connection). eqpId={}", equipmentId.value());
                }
                metrics.incrementCommandsDropNoConnection();
                return;
            }

            final byte[] payload;
            try {
                payload = Base64.getDecoder().decode(message.payloadBase64());
            } catch (IllegalArgumentException ex) {
                publishLegacyDlq(message, DlqReasonCode.BASE64_DECODE_FAIL, ex.getMessage(), traceId);
                return;
            }

            final OutboundRawFrame frame = new OutboundRawFrame(
                    equipmentId,
                    interfaceType,
                    message.socketType(),
                    payload,
                    clockPort.nowEpochMillis(),
                    "KAFKA_COMMAND_LEGACY"
            );

            try {
                processingService.enqueueOutbound(frame);
                if (log.isDebugEnabled()) {
                    log.debug("Legacy command enqueued. eqpId={}, traceId={}, payloadBytes={}",
                            equipmentId.value(), traceId, payload.length);
                }
            } catch (Exception ex) {
                publishLegacyDlq(message, DlqReasonCode.PUBLISH_FAILED, ex.getMessage(), traceId);
                safeQuarantine(equipmentId, DlqReasonCode.PUBLISH_FAILED, "Outbound send failed");
            }
        }
    }

    /**
     * Business 명령 계약(metadata + data)을 처리합니다.
     *
     * <p>현재 구현 단계에서는 SOCKET만 송신 처리하고,
     * HSMS는 TODO 정책에 따라 DLQ로 분류합니다.</p>
     */
    public void dispatchBusinessCommand(final GatewayBusinessCommandMessage message) {
        Objects.requireNonNull(message, "message is null");

        final String eqpId = normalizeText(message.data() == null ? null : message.data().eqpId());
        final String traceId = resolveTraceId(message.metadata() == null ? null : message.metadata().traceId());

        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(eqpId, traceId)) {
            if (message.data() == null) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.INVALID_INPUT,
                        "data is required",
                        traceId,
                        null
                );
                return;
            }

            if (eqpId == null) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.INVALID_INPUT,
                        "data.eqpId is required",
                        traceId,
                        null
                );
                return;
            }

            final CommInterfaceType interfaceType;
            try {
                interfaceType = CommInterfaceType.fromText(message.data().interfaceType());
            } catch (Exception ex) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.INVALID_INPUT,
                        "data.interfaceType is invalid",
                        traceId,
                        null
                );
                return;
            }

            final EquipmentId equipmentId;
            try {
                equipmentId = new EquipmentId(eqpId);
            } catch (Exception ex) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.INVALID_INPUT,
                        "data.eqpId is invalid",
                        traceId,
                        null
                );
                return;
            }

            if (!hasActiveChannel(equipmentId)) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("Business command drop (no connection). eqpId={}, eventType={}",
                            eqpId,
                            message.metadata() == null ? null : message.metadata().eventType());
                }
                metrics.incrementCommandsDropNoConnection();
                return;
            }

            if (interfaceType == CommInterfaceType.HSMS) {
                log.info("HSMS business command is not implemented yet. eqpId={}, traceId={}, eventType={}",
                        eqpId,
                        traceId,
                        message.metadata() == null ? null : message.metadata().eventType());
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.ROUTING_FAILED,
                        "HSMS business command handling is not implemented yet",
                        traceId,
                        null
                );
                return;
            }

            if (interfaceType != CommInterfaceType.SOCKET) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.INVALID_INPUT,
                        "Unsupported interfaceType: " + message.data().interfaceType(),
                        traceId,
                        null
                );
                return;
            }

            final GatewayEquipmentInfo equipmentInfo;
            try {
                equipmentInfo = processingService.resolveEquipment(eqpId);
            } catch (Exception ex) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.UNKNOWN_EQUIPMENT,
                        ex.getMessage(),
                        traceId,
                        null
                );
                return;
            }

            if (equipmentInfo.commInterfaceType() != CommInterfaceType.SOCKET) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.INVALID_INPUT,
                        "Equipment interfaceType mismatch",
                        traceId,
                        resolveSocketType(equipmentInfo)
                );
                return;
            }

            final String socketType = resolveSocketType(equipmentInfo);
            final String rawMessage = message.data().rawMessage();
            if (rawMessage == null) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.INVALID_INPUT,
                        "data.rawMessage is required for SOCKET",
                        traceId,
                        socketType
                );
                return;
            }

            final byte[] payload;
            try {
                payload = encodeSocketRawMessage(rawMessage, socketType);
            } catch (IllegalArgumentException ex) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.INVALID_INPUT,
                        ex.getMessage(),
                        traceId,
                        socketType
                );
                return;
            } catch (UnsupportedOperationException ex) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.ROUTING_FAILED,
                        ex.getMessage(),
                        traceId,
                        socketType
                );
                return;
            }

            final OutboundRawFrame frame = new OutboundRawFrame(
                    equipmentId,
                    CommInterfaceType.SOCKET,
                    socketType,
                    payload,
                    clockPort.nowEpochMillis(),
                    "KAFKA_COMMAND_BUSINESS_SOCKET"
            );

            try {
                processingService.enqueueOutbound(frame);
                if (log.isDebugEnabled()) {
                    log.debug("Business SOCKET command enqueued. eqpId={}, traceId={}, socketType={}, rawLen={}, encodedBytes={}",
                            eqpId,
                            traceId,
                            socketType,
                            rawMessage.getBytes(StandardCharsets.UTF_8).length,
                            payload.length);
                }
            } catch (Exception ex) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_PUBLISH,
                        DlqReasonCode.PUBLISH_FAILED,
                        ex.getMessage(),
                        traceId,
                        socketType
                );
                safeQuarantine(equipmentId, DlqReasonCode.PUBLISH_FAILED, "Outbound send failed");
            }
        }
    }

    /**
     * 활성 채널 존재 여부를 확인합니다.
     */
    private boolean hasActiveChannel(final EquipmentId equipmentId) {
        final var channel = channelRegistry.get(equipmentId);
        return channel != null && channel.isActive();
    }

    /**
     * SOCKET rawMessage를 socketType 핸들러 규칙으로 인코딩합니다.
     *
     * <p>현재 정책은 rawMessage 원문을 그대로 입력으로 넘기고,
     * 종단문자 보정 여부는 각 socketType encode 구현에 위임합니다.</p>
     */
    private byte[] encodeSocketRawMessage(final String rawMessage, final String socketType) {
        final SocketTypeEncodeResult encoded = socketTypeRegistry
                .getRequired(socketType)
                .encode(rawMessage);

        if (encoded.bytes().length == 0) {
            throw new IllegalArgumentException("Encoded payload is empty");
        }
        return encoded.bytes();
    }

    /**
     * 장비 정보에서 socketType을 읽고, 비어 있으면 기본 socketType으로 보정합니다.
     */
    private String resolveSocketType(final GatewayEquipmentInfo equipmentInfo) {
        final String fromEquipment = normalizeText(equipmentInfo.socketType());
        if (fromEquipment != null) {
            return fromEquipment;
        }
        return socketProperties.getDefaultSocketType();
    }

    /**
     * legacy 명령 처리 실패를 DLQ로 기록합니다.
     */
    private void publishLegacyDlq(
            final KafkaCommandMessage message,
            final DlqReasonCode reasonCode,
            final String reasonMessage,
            final String traceId
    ) {
        CommInterfaceType commInterfaceType;
        try {
            commInterfaceType = CommInterfaceType.fromText(message.commInterfaceType());
        } catch (Exception ex) {
            // 입력 자체가 깨져도 DLQ 저장은 성공해야 하므로 SOCKET 기본값으로 보정합니다.
            // 실제 실패 사유는 reasonCode/reasonMessage에 남습니다.
            commInterfaceType = CommInterfaceType.SOCKET;
        }

        final String resolvedTraceId = resolveTraceId(traceId);
        final long now = clockPort.nowEpochMillis();

        final DlqMessage dlqMessage = new DlqMessage(
                traceIdGeneratorPort.newTraceId(),
                message.equipmentId(),
                resolvedTraceId,
                commInterfaceType,
                message.socketType(),
                DlqMessage.STAGE_PUBLISH,
                reasonCode,
                safeReason(reasonMessage, "Command dispatch failed"),
                now,
                null,
                DlqMessage.UNKNOWN_LENGTH,
                message.payloadBase64() == null ? DlqMessage.UNKNOWN_LENGTH : message.payloadBase64().length(),
                message.attributes() == null ? Map.of() : message.attributes()
        );

        try {
            dlqPublisherPort.publish(dlqMessage);
            metrics.incrementDlqPublish();
        } catch (Exception ignored) {
            // DLQ 발행 실패는 원 처리 흐름을 추가로 깨지 않도록 삼킵니다.
        }
    }

    /**
     * business 명령 처리 실패를 DLQ로 기록합니다.
     */
    private void publishBusinessDlq(
            final GatewayBusinessCommandMessage message,
            final String stage,
            final DlqReasonCode reasonCode,
            final String reasonMessage,
            final String traceId,
            final String socketTypeForLog
    ) {
        final String resolvedEqpId = normalizeText(message.data() == null ? null : message.data().eqpId());
        final String finalEqpId = resolvedEqpId == null ? UNKNOWN_EQP_ID : resolvedEqpId;
        final String resolvedTraceId = resolveTraceId(traceId);
        final CommInterfaceType commInterfaceType = parseInterfaceTypeOrDefault(
                message.data() == null ? null : message.data().interfaceType(),
                CommInterfaceType.SOCKET
        );

        final String rawMessage = message.data() == null ? null : message.data().rawMessage();
        final int rawLen = (rawMessage == null)
                ? DlqMessage.UNKNOWN_LENGTH
                : rawMessage.getBytes(StandardCharsets.UTF_8).length;

        final DlqMessage dlqMessage = new DlqMessage(
                traceIdGeneratorPort.newTraceId(),
                finalEqpId,
                resolvedTraceId,
                commInterfaceType,
                socketTypeForLog,
                stage,
                reasonCode,
                safeReason(reasonMessage, "Business command dispatch failed"),
                clockPort.nowEpochMillis(),
                null,
                rawLen,
                DlqMessage.UNKNOWN_LENGTH,
                buildBusinessDlqTags(message)
        );

        try {
            dlqPublisherPort.publish(dlqMessage);
            metrics.incrementDlqPublish();
        } catch (Exception ignored) {
            // DLQ 발행 실패는 원 처리 흐름을 추가로 깨지 않도록 삼킵니다.
        }
    }

    /**
     * business DLQ 태그를 null 안전하게 구성합니다.
     */
    private Map<String, String> buildBusinessDlqTags(final GatewayBusinessCommandMessage message) {
        final Map<String, String> tags = new HashMap<>();

        if (message.metadata() != null) {
            putIfHasText(tags, "eventType", message.metadata().eventType());
            putIfHasText(tags, "source", message.metadata().source());
            putIfHasText(tags, "timestamp", message.metadata().timestamp());
        }
        if (message.data() != null) {
            putIfHasText(tags, "interfaceType", message.data().interfaceType());
            putIfHasText(tags, "transactionId", message.data().transactionId());
            if (message.data().secs2() != null) {
                putIfHasText(tags, "secs2EventId", message.data().secs2().eventId());
                putIfHasText(tags, "secs2SystemBytes", message.data().secs2().systemBytes());
            }
        }

        return tags;
    }

    /**
     * 문자열이 비어 있지 않을 때만 태그 맵에 값을 추가합니다.
     */
    private void putIfHasText(final Map<String, String> tags, final String key, final String value) {
        final String normalized = normalizeText(value);
        if (normalized != null) {
            tags.put(key, normalized);
        }
    }

    /**
     * traceId를 보정합니다.
     */
    private String resolveTraceId(final String traceId) {
        final String normalized = normalizeText(traceId);
        return normalized == null ? traceIdGeneratorPort.newTraceId() : normalized;
    }

    /**
     * interfaceType 파싱 실패 시 기본값을 반환합니다.
     */
    private CommInterfaceType parseInterfaceTypeOrDefault(
            final String interfaceType,
            final CommInterfaceType fallback
    ) {
        try {
            return CommInterfaceType.fromText(interfaceType);
        } catch (Exception ex) {
            return fallback;
        }
    }

    /**
     * reasonMessage를 null/blank 안전하게 보정합니다.
     */
    private String safeReason(final String reasonMessage, final String fallback) {
        final String normalized = normalizeText(reasonMessage);
        return normalized == null ? fallback : normalized;
    }

    /**
     * 문자열을 trim하고, 비어 있으면 null을 반환합니다.
     */
    private String normalizeText(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 장비 격리 호출을 안전하게 수행합니다.
     */
    private void safeQuarantine(
            final EquipmentId equipmentId,
            final DlqReasonCode reasonCode,
            final String reasonMessage
    ) {
        try {
            quarantinePort.quarantine(equipmentId, reasonCode.name(), reasonMessage);
        } catch (Exception ignored) {
            // 격리 실패는 보조 처리이므로 본 흐름을 방해하지 않습니다.
        }
    }
}
