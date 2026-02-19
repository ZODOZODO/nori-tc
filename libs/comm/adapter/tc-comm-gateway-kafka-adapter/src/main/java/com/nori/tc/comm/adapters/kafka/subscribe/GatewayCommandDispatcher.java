package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
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
import com.nori.tc.common.task.policy.DefaultDlqRecordFactory;
import com.nori.tc.common.task.policy.DefaultTaskHandlingPolicy;
import com.nori.tc.common.task.policy.DlqRecord;
import com.nori.tc.common.task.policy.TaskFailureCategory;
import com.nori.tc.common.task.policy.TaskFailureContext;
import com.nori.tc.common.task.policy.TaskHandlingAction;
import com.nori.tc.common.task.policy.TaskHandlingDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Gateway inbound command 디스패처입니다.
 *
 * <p>설계 기준:</p>
 * <p>1) 입력 계약은 {@link GatewayBusinessCommandMessage}(metadata + data)만 허용합니다.</p>
 * <p>2) Kafka key/토픽 정책은 상위 consumer 계층에서 보장하고, 본 클래스는 실제 송신 전 검증/라우팅만 담당합니다.</p>
 * <p>3) 실패는 공통 task-policy + DLQ + disposition 메트릭으로 표준화합니다.</p>
 *
 * <p>현재 범위:</p>
 * <p>- SOCKET 명령 송신 활성화</p>
 * <p>- HSMS 명령 송신은 TODO 정책에 따라 DLQ로 분류</p>
 */
@Component
public class GatewayCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GatewayCommandDispatcher.class);

    /**
     * DLQ 기록 시 eqpId가 비어 있을 때 사용할 대체 식별자입니다.
     */
    private static final String UNKNOWN_EQP_ID = "UNKNOWN_EQP";

    /**
     * task-policy에서 사용할 메시지 타입 분류값입니다.
     */
    private static final String BUSINESS_MESSAGE_TYPE = "BUSINESS";

    /**
     * 공통 DLQ 레코드의 예외 메시지 최대 길이입니다.
     */
    private static final int DLQ_EXCEPTION_MESSAGE_MAX_LENGTH = 300;

    /**
     * gateway command 실패 정책 최대 시도 횟수입니다.
     *
     * <p>현재 정책은 즉시 DLQ를 기본으로 사용합니다.</p>
     */
    private static final int COMMAND_FAILURE_MAX_ATTEMPTS = 1;

    /**
     * disposition 메트릭의 flow 키입니다.
     */
    private static final String FLOW_COMMAND = "COMMAND";

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
    private final DefaultDlqRecordFactory dlqRecordFactory;
    private final DefaultTaskHandlingPolicy commandTaskHandlingPolicy;

    /**
     * 디스패처 의존성을 초기화합니다.
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
        this.dlqRecordFactory = new DefaultDlqRecordFactory(DLQ_EXCEPTION_MESSAGE_MAX_LENGTH);
        this.commandTaskHandlingPolicy = new DefaultTaskHandlingPolicy(
                new FixedRetryPolicy(COMMAND_FAILURE_MAX_ATTEMPTS, 0L),
                dlqRecordFactory,
                true
        );
    }

    /**
     * Business 명령 계약(metadata + data)을 처리합니다.
     *
     * <p>처리 순서:</p>
     * <p>1) envelope 필수값 검증</p>
     * <p>2) 장비 채널/인터페이스 정합성 검증</p>
     * <p>3) socketType 인코딩 후 outbound enqueue</p>
     * <p>4) 실패 시 공통 정책 기반 DLQ 발행 및 필요 시 quarantine</p>
     *
     * @param message business command envelope
     */
    public void dispatchBusinessCommand(final GatewayBusinessCommandMessage message) {
        Objects.requireNonNull(message, "message is null");

        final String traceId = resolveTraceId(message.metadata() == null ? null : message.metadata().traceId());
        final String eqpIdForLog = normalizeText(message.data() == null ? null : message.data().eqpId());

        try (GatewayLogContext ignored = GatewayLogContext.withEqpAndTraceId(eqpIdForLog, traceId)) {
            final CommandEnvelope envelope = validateEnvelope(message, traceId);
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
                        envelope.eqpId(),
                        traceId,
                        GatewayDisposition.REJECTED,
                        "NO_ACTIVE_CONNECTION"
                );
                return;
            }

            if (envelope.interfaceType() == CommInterfaceType.HSMS) {
                /*
                 * TODO: HSMS business command 송신 경로는 정책 확정 후 구현합니다.
                 * 현재는 의도적으로 DLQ로 분류해 운영 가시성을 유지합니다.
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
                        null
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
                        null
                );
                return;
            }

            final GatewayEquipmentInfo equipmentInfo = resolveEquipmentOrPublishDlq(message, envelope, traceId);
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
                        null
                );
                return;
            }

            final String socketType = resolveSocketType(equipmentInfo);
            final byte[] payload = encodePayloadOrPublishDlq(message, envelope, socketType, traceId);
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
                        ex
                );
                safeQuarantine(envelope.equipmentId(), DlqReasonCode.PUBLISH_FAILED, "Outbound send failed");
            }
        }
    }

    /**
     * 수신 envelope 필수값을 검증하고 송신 처리 입력 모델로 변환합니다.
     */
    private CommandEnvelope validateEnvelope(
            final GatewayBusinessCommandMessage message,
            final String traceId
    ) {
        if (message.metadata() == null) {
            publishBusinessDlq(
                    message,
                    DlqMessage.STAGE_ROUTING,
                    DlqReasonCode.INVALID_INPUT,
                    "metadata is required",
                    traceId,
                    null,
                    null
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
                    null
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
                    null
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
                    ex
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
                    ex
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
                    null
            );
            return null;
        }

        return new CommandEnvelope(equipmentId, eqpId, interfaceType, eventType, rawMessage);
    }

    /**
     * 장비 프로필 조회를 수행하고 실패 시 DLQ를 발행합니다.
     */
    private GatewayEquipmentInfo resolveEquipmentOrPublishDlq(
            final GatewayBusinessCommandMessage message,
            final CommandEnvelope envelope,
            final String traceId
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
                    ex
            );
            return null;
        }
    }

    /**
     * SOCKET payload 인코딩을 수행하고 실패 시 DLQ를 발행합니다.
     */
    private byte[] encodePayloadOrPublishDlq(
            final GatewayBusinessCommandMessage message,
            final CommandEnvelope envelope,
            final String socketType,
            final String traceId
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
                    ex
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
                    ex
            );
            return null;
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
     * <p>정책:</p>
     * <p>1) 설비별 플러그인 핸들러가 있으면 우선 사용합니다.</p>
     * <p>2) 없으면 기본 registry 핸들러를 사용합니다.</p>
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
     * business 명령 처리 실패를 DLQ로 기록합니다.
     */
    private void publishBusinessDlq(
            final GatewayBusinessCommandMessage message,
            final String stage,
            final DlqReasonCode reasonCode,
            final String reasonMessage,
            final String traceId,
            final String socketTypeForLog,
            final Throwable cause
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

        publishDlqSafely(dlqMessage);
    }

    /**
     * business 실패 컨텍스트를 공통 task-policy 입력 모델로 변환합니다.
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
     * 공통 실패 정책으로 DLQ 레코드를 계산합니다.
     *
     * <p>현재 정책은 사실상 즉시 DLQ를 기본으로 사용하지만,
     * 정책 변경 시에도 방어적으로 fallback DLQ를 생성합니다.</p>
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
     * business DLQ 태그를 null-safe 하게 구성합니다.
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
     * DLQ 발행을 안전하게 수행합니다.
     *
     * <p>DLQ 발행 실패는 보조 경로이므로 원 처리 흐름을 중단하지 않고
     * disposition/에러 로그만 남깁니다.</p>
     */
    private void publishDlqSafely(final DlqMessage dlqMessage) {
        try {
            dlqPublisherPort.publish(dlqMessage);
            metrics.incrementDlqPublish();
            recordCommandDisposition(
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
     * business 명령 payloadRef를 생성합니다.
     */
    private String buildBusinessPayloadRef(final GatewayBusinessCommandMessage message, final String traceId) {
        final String rawMessage = message.data() == null ? null : message.data().rawMessage();
        final int payloadLength = rawMessage == null ? 0 : rawMessage.getBytes(StandardCharsets.UTF_8).length;
        return "payload://business/" + resolveTraceId(traceId) + "/len/" + payloadLength;
    }

    /**
     * business 메시지의 eventType을 실패 컨텍스트용 messageName으로 변환합니다.
     */
    private String resolveBusinessMessageName(final GatewayBusinessCommandMessage message) {
        final String eventType = message.metadata() == null ? null : message.metadata().eventType();
        final String normalizedEventType = normalizeText(eventType);
        return normalizedEventType == null ? "UNKNOWN_EVENT" : normalizedEventType;
    }

    /**
     * DLQ reason code를 공통 실패 카테고리로 매핑합니다.
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
     * eqpId를 DLQ 기록 기준으로 보정합니다.
     */
    private String normalizeEqpId(final String eqpId) {
        final String normalized = normalizeText(eqpId);
        return normalized == null ? UNKNOWN_EQP_ID : normalized;
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
     * 문자열을 trim하고 비어 있으면 null을 반환합니다.
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

    /**
     * command 처리 disposition을 표준 로그/메트릭으로 기록합니다.
     *
     * <p>로그 레벨 정책:</p>
     * <p>1) ACCEPTED는 고빈도 이벤트이므로 debug로 기록합니다.</p>
     * <p>2) DLQ/REJECTED는 운영 추적 핵심이므로 info로 기록합니다.</p>
     */
    private void recordCommandDisposition(
            final String eqpId,
            final String traceId,
            final GatewayDisposition disposition,
            final String reason
    ) {
        dispositionMetrics.increment(FLOW_COMMAND, disposition);

        if (disposition == GatewayDisposition.ACCEPTED) {
            if (log.isDebugEnabled()) {
                log.debug("GATEWAY_COMMAND_DISPOSITION. flow={}, disposition={}, reason={}, eqpId={}, traceId={}",
                        FLOW_COMMAND,
                        disposition,
                        reason,
                        normalizeEqpId(eqpId),
                        safeTraceIdForLog(traceId));
            }
            return;
        }

        log.info("GATEWAY_COMMAND_DISPOSITION. flow={}, disposition={}, reason={}, eqpId={}, traceId={}",
                FLOW_COMMAND,
                disposition,
                reason,
                normalizeEqpId(eqpId),
                safeTraceIdForLog(traceId));
    }

    /**
     * 로그 출력용 traceId를 보정합니다.
     */
    private String safeTraceIdForLog(final String traceId) {
        final String normalized = normalizeText(traceId);
        return normalized == null ? "N/A" : normalized;
    }

    /**
     * 송신 처리 중간 상태를 담는 내부 모델입니다.
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
