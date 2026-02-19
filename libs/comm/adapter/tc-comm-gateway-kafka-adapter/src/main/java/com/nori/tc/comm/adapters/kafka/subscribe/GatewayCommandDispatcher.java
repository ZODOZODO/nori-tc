package com.nori.tc.comm.adapters.kafka.subscribe;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.kafka.contract.GatewayBusinessCommandMessage;
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
import com.nori.tc.comm.gateway.metrics.GatewayDisposition;
import com.nori.tc.comm.gateway.metrics.GatewayDispositionMetrics;
import com.nori.tc.comm.gateway.metrics.GatewayLogContext;
import com.nori.tc.comm.gateway.metrics.GatewayLogSampler;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
import com.nori.tc.comm.gateway.socket.plugin.GatewaySocketPluginRuntimeProvider;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeEncodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeHandler;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;
import com.nori.tc.common.kafka.processing.FixedRetryPolicy;
import com.nori.tc.common.task.execution.policy.dlq.TaskDlqRecordFactory;
import com.nori.tc.common.task.execution.policy.runtime.TaskHandlingPolicyEvaluator;
import com.nori.tc.common.task.execution.policy.types.DlqRecord;
import com.nori.tc.common.task.execution.policy.types.TaskFailureCategory;
import com.nori.tc.common.task.execution.policy.types.TaskFailureContext;
import com.nori.tc.common.task.execution.policy.types.TaskHandlingAction;
import com.nori.tc.common.task.execution.policy.types.TaskHandlingDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * GatewayCommandDispatcher 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */
@Component
public class GatewayCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GatewayCommandDispatcher.class);

    /**
     * UNKNOWN_EQP_ID 필드입니다.
     */
    private static final String UNKNOWN_EQP_ID = "UNKNOWN_EQP";

    /**
     * BUSINESS_MESSAGE_TYPE 필드입니다.
     */
    private static final String BUSINESS_MESSAGE_TYPE = "BUSINESS";

    /**
     * DLQ_EXCEPTION_MESSAGE_MAX_LENGTH 필드입니다.
     */
    private static final int DLQ_EXCEPTION_MESSAGE_MAX_LENGTH = 300;

    /**
     * COMMAND_FAILURE_MAX_ATTEMPTS 필드입니다.
     */
    private static final int COMMAND_FAILURE_MAX_ATTEMPTS = 1;

    /**
     * FLOW_COMMAND 필드입니다.
     */
    private static final String FLOW_COMMAND = "COMMAND";
    /**
     * UNKNOWN_TOPIC 필드입니다.
     */
    private static final String UNKNOWN_TOPIC = "UNKNOWN_TOPIC";
    /**
     * UNKNOWN_PARTITION 필드입니다.
     */
    private static final int UNKNOWN_PARTITION = -1;
    /**
     * UNKNOWN_OFFSET 필드입니다.
     */
    private static final long UNKNOWN_OFFSET = -1L;

    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayProcessingService processingService;
    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;
    private final GatewayDispositionMetrics dispositionMetrics;
    private final ClockPort clockPort;
    private final TraceIdGeneratorPort traceIdGeneratorPort;
    private final DlqPublisherPort dlqPublisherPort;
    private final QuarantinePort quarantinePort;
    private final GatewaySocketProperties socketProperties;
    private final SocketTypeRegistry socketTypeRegistry;
    private final GatewaySocketPluginRuntimeProvider socketPluginRuntimeProvider;
    private final GatewayKafkaTopicProperties topicProperties;
    private final TaskDlqRecordFactory dlqRecordFactory;
    private final TaskHandlingPolicyEvaluator commandTaskHandlingPolicy;

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    public GatewayCommandDispatcher(
            final EquipmentChannelRegistry channelRegistry,
            final GatewayProcessingService processingService,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler,
            final GatewayDispositionMetrics dispositionMetrics,
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final DlqPublisherPort dlqPublisherPort,
            final QuarantinePort quarantinePort,
            final GatewaySocketProperties socketProperties,
            final SocketTypeRegistry socketTypeRegistry,
            final GatewaySocketPluginRuntimeProvider socketPluginRuntimeProvider,
            final GatewayKafkaTopicProperties topicProperties
    ) {
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry is null");
        this.processingService = Objects.requireNonNull(processingService, "processingService is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
        this.dispositionMetrics = Objects.requireNonNull(dispositionMetrics, "dispositionMetrics is null");
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.traceIdGeneratorPort = Objects.requireNonNull(traceIdGeneratorPort, "traceIdGeneratorPort is null");
        this.dlqPublisherPort = Objects.requireNonNull(dlqPublisherPort, "dlqPublisherPort is null");
        this.quarantinePort = Objects.requireNonNull(quarantinePort, "quarantinePort is null");
        this.socketProperties = Objects.requireNonNull(socketProperties, "socketProperties is null");
        this.socketTypeRegistry = Objects.requireNonNull(socketTypeRegistry, "socketTypeRegistry is null");
        this.socketPluginRuntimeProvider = Objects.requireNonNull(
                socketPluginRuntimeProvider,
                "socketPluginRuntimeProvider is null"
        );
        this.topicProperties = Objects.requireNonNull(topicProperties, "topicProperties is null");
        this.dlqRecordFactory = new TaskDlqRecordFactory(DLQ_EXCEPTION_MESSAGE_MAX_LENGTH);
        this.commandTaskHandlingPolicy = new TaskHandlingPolicyEvaluator(
                new FixedRetryPolicy(COMMAND_FAILURE_MAX_ATTEMPTS, 0L),
                dlqRecordFactory,
                true
        );
    }

    /**
     * dispatchBusinessCommand 기능을 수행합니다.
     *
     * @param message 입력 값
     */
    public void dispatchBusinessCommand(final GatewayBusinessCommandMessage message) {
        dispatchBusinessCommand(
                message,
                topicProperties.getEqpCommands(),
                UNKNOWN_PARTITION,
                UNKNOWN_OFFSET
        );
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    public void dispatchBusinessCommand(
            final GatewayBusinessCommandMessage message,
            final String topic,
            final int partition,
            final long offset
    ) {
        Objects.requireNonNull(message, "message is null");

        final DispatchContext dispatchContext = normalizeDispatchContext(topic, partition, offset);
        final String traceId = resolveTraceId(message.metadata() == null ? null : message.metadata().traceId());
        final String eqpIdForLog = normalizeText(message.data() == null ? null : message.data().eqpId());

        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(eqpIdForLog, traceId)) {
            final CommandEnvelope envelope = validateEnvelope(message, traceId, dispatchContext);
            if (envelope == null) {
                return;
            }

            if (!hasActiveChannel(envelope.equipmentId())) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("Business command drop (no active connection). eqpId={}, traceId={}, eventType={}",
                            envelope.eqpId(),
                            traceId,
                            envelope.eventType());
                }
                metrics.incrementCommandsDropNoConnection();
                recordCommandDisposition(
                        dispatchContext,
                        envelope.eqpId(),
                        traceId,
                        GatewayDisposition.REJECTED,
                        "NO_ACTIVE_CONNECTION"
                );
                return;
            }

            if (envelope.interfaceType() == CommInterfaceType.HSMS) {
                /**
                 * UTF-8 형식으로 정리된 주석입니다.
                 */
                log.info("HSMS business command is not implemented yet. eqpId={}, traceId={}, eventType={}",
                        envelope.eqpId(),
                        traceId,
                        envelope.eventType());
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.ROUTING_FAILED,
                        "HSMS business command handling is not implemented yet",
                        traceId,
                        null,
                        null,
                        dispatchContext
                );
                return;
            }

            if (envelope.interfaceType() != CommInterfaceType.SOCKET) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.INVALID_INPUT,
                        "Unsupported interfaceType: " + envelope.interfaceType().name(),
                        traceId,
                        null,
                        null,
                        dispatchContext
                );
                return;
            }

            final GatewayEquipmentInfo equipmentInfo = resolveEquipmentOrPublishDlq(
                    message,
                    envelope,
                    traceId,
                    dispatchContext
            );
            if (equipmentInfo == null) {
                return;
            }

            if (equipmentInfo.commInterfaceType() != CommInterfaceType.SOCKET) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_ROUTING,
                        DlqReasonCode.INVALID_INPUT,
                        "Equipment interfaceType mismatch",
                        traceId,
                        resolveSocketType(equipmentInfo),
                        null,
                        dispatchContext
                );
                return;
            }

            final String socketType = resolveSocketType(equipmentInfo);
            final byte[] payload = encodePayloadOrPublishDlq(message, envelope, socketType, traceId, dispatchContext);
            if (payload == null) {
                return;
            }

            final OutboundRawFrame frame = new OutboundRawFrame(
                    envelope.equipmentId(),
                    CommInterfaceType.SOCKET,
                    socketType,
                    payload,
                    clockPort.nowEpochMillis(),
                    "KAFKA_COMMAND_BUSINESS_SOCKET"
            );

            try {
                processingService.enqueueOutbound(frame);
                recordCommandDisposition(
                        dispatchContext,
                        envelope.eqpId(),
                        traceId,
                        GatewayDisposition.ACCEPTED,
                        "BUSINESS_SOCKET_ENQUEUED"
                );
                if (log.isDebugEnabled()) {
                    log.debug("Business SOCKET command enqueued. eqpId={}, traceId={}, socketType={}, rawLen={}, encodedBytes={}",
                            envelope.eqpId(),
                            traceId,
                            socketType,
                            envelope.rawMessage().getBytes(StandardCharsets.UTF_8).length,
                            payload.length);
                }
            } catch (Exception ex) {
                publishBusinessDlq(
                        message,
                        DlqMessage.STAGE_PUBLISH,
                        DlqReasonCode.PUBLISH_FAILED,
                        ex.getMessage(),
                        traceId,
                        socketType,
                        ex,
                        dispatchContext
                );
                safeQuarantine(envelope.equipmentId(), DlqReasonCode.PUBLISH_FAILED, "Outbound send failed");
            }
        }
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private CommandEnvelope validateEnvelope(
            final GatewayBusinessCommandMessage message,
            final String traceId,
            final DispatchContext dispatchContext
    ) {
        if (message.metadata() == null) {
            publishBusinessDlq(
                    message,
                    DlqMessage.STAGE_ROUTING,
                    DlqReasonCode.INVALID_INPUT,
                    "metadata is required",
                    traceId,
                    null,
                    null,
                    dispatchContext
            );
            return null;
        }

        if (message.data() == null) {
            publishBusinessDlq(
                    message,
                    DlqMessage.STAGE_ROUTING,
                    DlqReasonCode.INVALID_INPUT,
                    "data is required",
                    traceId,
                    null,
                    null,
                    dispatchContext
            );
            return null;
        }

        final String eqpId = normalizeText(message.data().eqpId());
        if (eqpId == null) {
            publishBusinessDlq(
                    message,
                    DlqMessage.STAGE_ROUTING,
                    DlqReasonCode.INVALID_INPUT,
                    "data.eqpId is required",
                    traceId,
                    null,
                    null,
                    dispatchContext
            );
            return null;
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
                    null,
                    ex,
                    dispatchContext
            );
            return null;
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
                    null,
                    ex,
                    dispatchContext
            );
            return null;
        }

        final String rawMessage = normalizeText(message.data().rawMessage());
        final String eventType = normalizeText(message.metadata().eventType());

        if (interfaceType == CommInterfaceType.SOCKET && rawMessage == null) {
            publishBusinessDlq(
                    message,
                    DlqMessage.STAGE_ROUTING,
                    DlqReasonCode.INVALID_INPUT,
                    "data.rawMessage is required for SOCKET",
                    traceId,
                    null,
                    null,
                    dispatchContext
            );
            return null;
        }

        return new CommandEnvelope(equipmentId, eqpId, interfaceType, eventType, rawMessage);
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private GatewayEquipmentInfo resolveEquipmentOrPublishDlq(
            final GatewayBusinessCommandMessage message,
            final CommandEnvelope envelope,
            final String traceId,
            final DispatchContext dispatchContext
    ) {
        try {
            return processingService.resolveEquipment(envelope.eqpId());
        } catch (Exception ex) {
            publishBusinessDlq(
                    message,
                    DlqMessage.STAGE_ROUTING,
                    DlqReasonCode.UNKNOWN_EQUIPMENT,
                    ex.getMessage(),
                    traceId,
                    null,
                    ex,
                    dispatchContext
            );
            return null;
        }
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private byte[] encodePayloadOrPublishDlq(
            final GatewayBusinessCommandMessage message,
            final CommandEnvelope envelope,
            final String socketType,
            final String traceId,
            final DispatchContext dispatchContext
    ) {
        try {
            return encodeSocketRawMessage(envelope.eqpId(), envelope.rawMessage(), socketType);
        } catch (IllegalArgumentException ex) {
            publishBusinessDlq(
                    message,
                    DlqMessage.STAGE_ROUTING,
                    DlqReasonCode.INVALID_INPUT,
                    ex.getMessage(),
                    traceId,
                    socketType,
                    ex,
                    dispatchContext
            );
            return null;
        } catch (UnsupportedOperationException ex) {
            publishBusinessDlq(
                    message,
                    DlqMessage.STAGE_ROUTING,
                    DlqReasonCode.ROUTING_FAILED,
                    ex.getMessage(),
                    traceId,
                    socketType,
                    ex,
                    dispatchContext
            );
            return null;
        }
    }

    /**
     * hasActiveChannel 기능을 수행합니다.
     *
     * @param equipmentId 입력 값
     * @return 처리 결과
     */
    private boolean hasActiveChannel(final EquipmentId equipmentId) {
        final var channel = channelRegistry.get(equipmentId);
        return channel != null && channel.isActive();
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private byte[] encodeSocketRawMessage(
            final String eqpId,
            final String rawMessage,
            final String socketType
    ) {
        final SocketTypeHandler pluginHandler = socketPluginRuntimeProvider.findByEqpId(eqpId).orElse(null);
        final SocketTypeHandler selectedHandler = pluginHandler != null
                ? pluginHandler
                : socketTypeRegistry.getRequired(socketType);

        if (pluginHandler != null && log.isDebugEnabled()) {
            log.debug("SOCKET plugin encoder selected. eqpId={}, declaredSocketType={}, handlerClass={}",
                    eqpId,
                    pluginHandler.socketType(),
                    pluginHandler.getClass().getName());
        }

        final SocketTypeEncodeResult encoded = selectedHandler.encode(rawMessage);
        if (encoded.bytes().length == 0) {
            throw new IllegalArgumentException("Encoded payload is empty");
        }
        return encoded.bytes();
    }

    /**
     * resolveSocketType 기능을 수행합니다.
     *
     * @param equipmentInfo 입력 값
     * @return 처리 결과
     */
    private String resolveSocketType(final GatewayEquipmentInfo equipmentInfo) {
        final String fromEquipment = normalizeText(equipmentInfo.socketType());
        if (fromEquipment != null) {
            return fromEquipment;
        }
        return socketProperties.getDefaultSocketType();
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private void publishBusinessDlq(
            final GatewayBusinessCommandMessage message,
            final String stage,
            final DlqReasonCode reasonCode,
            final String reasonMessage,
            final String traceId,
            final String socketTypeForLog,
            final Throwable cause,
            final DispatchContext dispatchContext
    ) {
        final String resolvedEqpId = normalizeText(message.data() == null ? null : message.data().eqpId());
        final String finalEqpId = resolvedEqpId == null ? UNKNOWN_EQP_ID : resolvedEqpId;
        final String resolvedTraceId = resolveTraceId(traceId);
        final CommInterfaceType commInterfaceType = parseInterfaceTypeOrDefault(
                message.data() == null ? null : message.data().interfaceType(),
                CommInterfaceType.SOCKET
        );
        final TaskFailureContext failureContext = buildBusinessFailureContext(
                message,
                reasonCode,
                reasonMessage,
                resolvedTraceId,
                cause
        );
        final DlqRecord dlqRecord = resolveDlqRecord(failureContext);

        final String rawMessage = message.data() == null ? null : message.data().rawMessage();
        final int rawLen = rawMessage == null
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
                safeReason(dlqRecord.exceptionMessage(), safeReason(reasonMessage, "Business command dispatch failed")),
                clockPort.nowEpochMillis(),
                null,
                rawLen,
                DlqMessage.UNKNOWN_LENGTH,
                buildBusinessDlqTags(message, dlqRecord)
        );

        publishDlqSafely(dlqMessage, dispatchContext);
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private TaskFailureContext buildBusinessFailureContext(
            final GatewayBusinessCommandMessage message,
            final DlqReasonCode reasonCode,
            final String reasonMessage,
            final String traceId,
            final Throwable cause
    ) {
        return new TaskFailureContext(
                topicProperties.getEqpCommands(),
                0,
                0L,
                normalizeEqpId(message.data() == null ? null : message.data().eqpId()),
                BUSINESS_MESSAGE_TYPE,
                resolveBusinessMessageName(message),
                1,
                buildBusinessPayloadRef(message, traceId),
                mapFailureCategory(reasonCode),
                cause != null ? cause : new IllegalStateException(safeReason(reasonMessage, "Business command failure")),
                false,
                clockPort.nowEpochMillis()
        );
    }

    /**
     * resolveDlqRecord 기능을 수행합니다.
     *
     * @param failureContext 입력 값
     * @return 처리 결과
     */
    private DlqRecord resolveDlqRecord(final TaskFailureContext failureContext) {
        final TaskHandlingDecision decision = commandTaskHandlingPolicy.decide(failureContext);
        if (log.isDebugEnabled()) {
            log.debug("Gateway command failure policy evaluated. eqpId={}, messageType={}, messageName={}, action={}, finalCategory={}",
                    failureContext.eqpId(),
                    failureContext.messageType(),
                    failureContext.messageName(),
                    decision.action(),
                    decision.finalCategory());
        }

        if (decision.action() == TaskHandlingAction.DLQ && decision.dlqRecord() != null) {
            return decision.dlqRecord();
        }

        log.info("Gateway command failure policy returned non-DLQ action. eqpId={}, action={}, fallback=DLQ",
                failureContext.eqpId(),
                decision.action());
        return dlqRecordFactory.create(failureContext, decision.finalCategory());
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private Map<String, String> buildBusinessDlqTags(
            final GatewayBusinessCommandMessage message,
            final DlqRecord dlqRecord
    ) {
        final Map<String, String> tags = new HashMap<>();
        if (message.metadata() != null) {
            putIfHasText(tags, "eventType", message.metadata().eventType());
            putIfHasText(tags, "source", message.metadata().source());
            putIfHasText(tags, "timestamp", message.metadata().timestamp());
            putIfHasText(tags, "traceId", message.metadata().traceId());
        }
        if (message.data() != null) {
            putIfHasText(tags, "interfaceType", message.data().interfaceType());
            putIfHasText(tags, "transactionId", message.data().transactionId());
            if (message.data().secs2() != null) {
                putIfHasText(tags, "secs2EventId", message.data().secs2().eventId());
                putIfHasText(tags, "secs2SystemBytes", message.data().secs2().systemBytes());
            }
        }
        tags.put("policyFailureCategory", dlqRecord.failureCategory().name());
        tags.put("policyExceptionClass", dlqRecord.exceptionClass());
        tags.put("policyAttempts", String.valueOf(dlqRecord.attempts()));
        tags.put("payloadRef", dlqRecord.payloadRef());
        return tags;
    }

    /**
     * publishDlqSafely 기능을 수행합니다.
     *
     * @param dlqMessage 입력 값
     * @param dispatchContext 입력 값
     */
    private void publishDlqSafely(final DlqMessage dlqMessage, final DispatchContext dispatchContext) {
        try {
            dlqPublisherPort.publish(dlqMessage);
            metrics.incrementDlqPublish();
            recordCommandDisposition(
                    dispatchContext,
                    dlqMessage.eqpId(),
                    dlqMessage.traceId(),
                    GatewayDisposition.DLQ,
                    "DLQ_PUBLISHED_" + dlqMessage.reasonCode()
            );
            if (log.isDebugEnabled()) {
                log.debug("Command DLQ published. eqpId={}, traceId={}, stage={}, reasonCode={}",
                        dlqMessage.eqpId(),
                        dlqMessage.traceId(),
                        dlqMessage.stage(),
                        dlqMessage.reasonCode());
            }
        } catch (Exception ex) {
            recordCommandDisposition(
                    dispatchContext,
                    dlqMessage.eqpId(),
                    dlqMessage.traceId(),
                    GatewayDisposition.REJECTED,
                    "DLQ_PUBLISH_FAILED_" + dlqMessage.reasonCode()
            );
            log.error("Command DLQ publish failed. eqpId={}, traceId={}, stage={}, reasonCode={}",
                    dlqMessage.eqpId(),
                    dlqMessage.traceId(),
                    dlqMessage.stage(),
                    dlqMessage.reasonCode(),
                    ex);
        }
    }

    /**
     * buildBusinessPayloadRef 기능을 수행합니다.
     *
     * @param message 입력 값
     * @param traceId 입력 값
     * @return 처리 결과
     */
    private String buildBusinessPayloadRef(final GatewayBusinessCommandMessage message, final String traceId) {
        final String rawMessage = message.data() == null ? null : message.data().rawMessage();
        final int payloadLength = rawMessage == null ? 0 : rawMessage.getBytes(StandardCharsets.UTF_8).length;
        return "payload://business/" + resolveTraceId(traceId) + "/len/" + payloadLength;
    }

    /**
     * resolveBusinessMessageName 기능을 수행합니다.
     *
     * @param message 입력 값
     * @return 처리 결과
     */
    private String resolveBusinessMessageName(final GatewayBusinessCommandMessage message) {
        final String eventType = message.metadata() == null ? null : message.metadata().eventType();
        final String normalizedEventType = normalizeText(eventType);
        return normalizedEventType == null ? "UNKNOWN_EVENT" : normalizedEventType;
    }

    /**
     * mapFailureCategory 기능을 수행합니다.
     *
     * @param reasonCode 입력 값
     * @return 처리 결과
     */
    private TaskFailureCategory mapFailureCategory(final DlqReasonCode reasonCode) {
        if (reasonCode == null) {
            return TaskFailureCategory.UNKNOWN;
        }
        return switch (reasonCode) {
            case INVALID_INPUT, UNKNOWN_EQUIPMENT, PAYLOAD_TOO_LARGE -> TaskFailureCategory.VALIDATION;
            case ROUTING_FAILED, PUBLISH_FAILED, INBOUND_QUEUE_OVERFLOW, REASSEMBLY_OVERFLOW, FRAMING_FAILED, PARSING_FAILED ->
                    TaskFailureCategory.ACTION_EXEC;
            case BASE64_DECODE_FAIL -> TaskFailureCategory.UNKNOWN;
        };
    }

    /**
     * normalizeEqpId 기능을 수행합니다.
     *
     * @param eqpId 입력 값
     * @return 처리 결과
     */
    private String normalizeEqpId(final String eqpId) {
        final String normalized = normalizeText(eqpId);
        return normalized == null ? UNKNOWN_EQP_ID : normalized;
    }

    /**
     * putIfHasText 기능을 수행합니다.
     *
     * @param tags 입력 값
     * @param key 입력 값
     * @param value 입력 값
     */
    private void putIfHasText(final Map<String, String> tags, final String key, final String value) {
        final String normalized = normalizeText(value);
        if (normalized != null) {
            tags.put(key, normalized);
        }
    }

    /**
     * resolveTraceId 기능을 수행합니다.
     *
     * @param traceId 입력 값
     * @return 처리 결과
     */
    private String resolveTraceId(final String traceId) {
        final String normalized = normalizeText(traceId);
        return normalized == null ? traceIdGeneratorPort.newTraceId() : normalized;
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
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
     * safeReason 기능을 수행합니다.
     *
     * @param reasonMessage 입력 값
     * @param fallback 입력 값
     * @return 처리 결과
     */
    private String safeReason(final String reasonMessage, final String fallback) {
        final String normalized = normalizeText(reasonMessage);
        return normalized == null ? fallback : normalized;
    }

    /**
     * normalizeText 기능을 수행합니다.
     *
     * @param value 입력 값
     * @return 처리 결과
     */
    private String normalizeText(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private void safeQuarantine(
            final EquipmentId equipmentId,
            final DlqReasonCode reasonCode,
            final String reasonMessage
    ) {
        try {
            quarantinePort.quarantine(equipmentId, reasonCode.name(), reasonMessage);
        } catch (Exception ignored) {
            // 격리(Quarantine) 실패는 주 처리 흐름을 중단하지 않기 위해 무시합니다.
        }
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private void recordCommandDisposition(
            final DispatchContext dispatchContext,
            final String eqpId,
            final String traceId,
            final GatewayDisposition disposition,
            final String reason
    ) {
        dispositionMetrics.increment(FLOW_COMMAND, disposition);

        if (disposition == GatewayDisposition.ACCEPTED) {
            if (log.isDebugEnabled()) {
                log.debug("GATEWAY_TASK_DISPOSITION. flow={}, disposition={}, reason={}, topic={}, partition={}, offset={}, eqpId={}, traceId={}",
                        FLOW_COMMAND,
                        disposition,
                        reason,
                        dispatchContext.topic(),
                        dispatchContext.partition(),
                        dispatchContext.offset(),
                        normalizeEqpId(eqpId),
                        safeTraceIdForLog(traceId));
            }
            return;
        }

        log.info("GATEWAY_TASK_DISPOSITION. flow={}, disposition={}, reason={}, topic={}, partition={}, offset={}, eqpId={}, traceId={}",
                FLOW_COMMAND,
                disposition,
                reason,
                dispatchContext.topic(),
                dispatchContext.partition(),
                dispatchContext.offset(),
                normalizeEqpId(eqpId),
                safeTraceIdForLog(traceId));
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private DispatchContext normalizeDispatchContext(
            final String topic,
            final int partition,
            final long offset
    ) {
        final String normalizedTopic = normalizeText(topic);
        final String finalTopic = normalizedTopic == null ? UNKNOWN_TOPIC : normalizedTopic;
        final int finalPartition = partition < 0 ? UNKNOWN_PARTITION : partition;
        final long finalOffset = offset < 0L ? UNKNOWN_OFFSET : offset;
        return new DispatchContext(finalTopic, finalPartition, finalOffset);
    }

    /**
     * safeTraceIdForLog 기능을 수행합니다.
     *
     * @param traceId 입력 값
     * @return 처리 결과
     */
    private String safeTraceIdForLog(final String traceId) {
        final String normalized = normalizeText(traceId);
        return normalized == null ? "N/A" : normalized;
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private record DispatchContext(
            String topic,
            int partition,
            long offset
    ) {
    }

    /**
     * UTF-8 형식으로 정리된 주석입니다.
     */
    private record CommandEnvelope(
            EquipmentId equipmentId,
            String eqpId,
            CommInterfaceType interfaceType,
            String eventType,
            String rawMessage
    ) {
    }
}

