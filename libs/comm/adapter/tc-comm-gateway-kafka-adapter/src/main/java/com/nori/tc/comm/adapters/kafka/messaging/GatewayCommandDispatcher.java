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
import com.nori.tc.comm.gateway.metrics.GatewayDisposition;
import com.nori.tc.comm.gateway.metrics.GatewayDispositionMetrics;
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

    /**
     * gateway command 실패를 기록할 source topic 명입니다.
     *
     * <p>현재 dispatcher 호출 경로 기준으로 명령 원천 토픽은 {@code tc.eqp.commands}로 고정합니다.</p>
     */
    private static final String COMMAND_SOURCE_TOPIC = "tc.eqp.commands";

    /**
     * legacy command의 messageType 표준 값입니다.
     */
    private static final String LEGACY_MESSAGE_TYPE = "LEGACY";

    /**
     * business command의 messageType 표준 값입니다.
     */
    private static final String BUSINESS_MESSAGE_TYPE = "BUSINESS";

    /**
     * legacy command에서 사용할 기본 messageName입니다.
     */
    private static final String LEGACY_MESSAGE_NAME = "LEGACY_COMMAND";

    /**
     * 공통 DLQ 레코드 예외 메시지 최대 길이입니다.
     */
    private static final int DLQ_EXCEPTION_MESSAGE_MAX_LENGTH = 300;

    /**
     * gateway command 실패 정책 최대 시도 횟수입니다.
     *
     * <p>현재 gateway command 경로는 즉시 DLQ 정책을 사용하므로 1로 고정합니다.</p>
     */
    private static final int COMMAND_FAILURE_MAX_ATTEMPTS = 1;
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
    private final DefaultDlqRecordFactory dlqRecordFactory;
    private final DefaultTaskHandlingPolicy commandTaskHandlingPolicy;

    /**
     * Dispatcher 의존성을 초기화합니다.
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
            final GatewaySocketPluginRuntimeProvider socketPluginRuntimeProvider
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
        this.dlqRecordFactory = new DefaultDlqRecordFactory(DLQ_EXCEPTION_MESSAGE_MAX_LENGTH);
        this.commandTaskHandlingPolicy = new DefaultTaskHandlingPolicy(
                new FixedRetryPolicy(COMMAND_FAILURE_MAX_ATTEMPTS, 0L),
                dlqRecordFactory,
                true
        );
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
                publishLegacyDlq(message, DlqReasonCode.INVALID_INPUT, ex.getMessage(), traceId, ex);
                return;
            }

            if (!hasActiveChannel(equipmentId)) {
                if (logSampler.shouldLogCommandDrop()) {
                    log.warn("Command drop (no connection). eqpId={}", equipmentId.value());
                }
                metrics.incrementCommandsDropNoConnection();
                recordCommandDisposition(
                        equipmentId.value(),
                        traceId,
                        GatewayDisposition.REJECTED,
                        "NO_ACTIVE_CONNECTION"
                );
                return;
            }

            final byte[] payload;
            try {
                payload = Base64.getDecoder().decode(message.payloadBase64());
            } catch (IllegalArgumentException ex) {
                publishLegacyDlq(message, DlqReasonCode.BASE64_DECODE_FAIL, ex.getMessage(), traceId, ex);
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
                recordCommandDisposition(
                        equipmentId.value(),
                        traceId,
                        GatewayDisposition.ACCEPTED,
                        "LEGACY_ENQUEUED"
                );
                if (log.isDebugEnabled()) {
                    log.debug("Legacy command enqueued. eqpId={}, traceId={}, payloadBytes={}",
                            equipmentId.value(), traceId, payload.length);
                }
            } catch (Exception ex) {
                publishLegacyDlq(message, DlqReasonCode.PUBLISH_FAILED, ex.getMessage(), traceId, ex);
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
                        null,
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
                        null,
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
                        null,
                        ex
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
                        null,
                        ex
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
                recordCommandDisposition(
                        eqpId,
                        traceId,
                        GatewayDisposition.REJECTED,
                        "NO_ACTIVE_CONNECTION"
                );
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
                        null,
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
                        null,
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
                        null,
                        ex
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
                        resolveSocketType(equipmentInfo),
                        null
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
                        socketType,
                        null
                );
                return;
            }

            final byte[] payload;
            try {
                payload = encodeSocketRawMessage(eqpId, rawMessage, socketType);
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
                return;
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
                recordCommandDisposition(
                        eqpId,
                        traceId,
                        GatewayDisposition.ACCEPTED,
                        "BUSINESS_SOCKET_ENQUEUED"
                );
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
                        socketType,
                        ex
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
    private byte[] encodeSocketRawMessage(
            final String eqpId,
            final String rawMessage,
            final String socketType
    ) {
        /*
         * 플러그인 우선 선택 정책:
         * 1) 설비별 플러그인 핸들러가 있으면 해당 핸들러 encode 사용
         * 2) 없으면 기존 socketTypeRegistry 핸들러 encode 사용
         */
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
     * legacy 명령 처리 실패를 DLQ로 기록합니다.
     */
    private void publishLegacyDlq(
            final KafkaCommandMessage message,
            final DlqReasonCode reasonCode,
            final String reasonMessage,
            final String traceId,
            final Throwable cause
    ) {
        final String resolvedTraceId = resolveTraceId(traceId);
        final CommInterfaceType commInterfaceType = parseInterfaceTypeOrDefault(
                message.commInterfaceType(),
                CommInterfaceType.SOCKET
        );
        final TaskFailureContext failureContext = buildLegacyFailureContext(
                message,
                reasonCode,
                reasonMessage,
                resolvedTraceId,
                cause
        );
        final DlqRecord dlqRecord = resolveDlqRecord(failureContext);
        final long now = clockPort.nowEpochMillis();

        final DlqMessage dlqMessage = new DlqMessage(
                traceIdGeneratorPort.newTraceId(),
                normalizeEqpId(message.equipmentId()),
                resolvedTraceId,
                commInterfaceType,
                message.socketType(),
                DlqMessage.STAGE_PUBLISH,
                reasonCode,
                safeReason(dlqRecord.exceptionMessage(), safeReason(reasonMessage, "Command dispatch failed")),
                now,
                null,
                DlqMessage.UNKNOWN_LENGTH,
                message.payloadBase64() == null ? DlqMessage.UNKNOWN_LENGTH : message.payloadBase64().length(),
                buildLegacyDlqTags(message.attributes(), dlqRecord)
        );

        publishDlqSafely(dlqMessage);
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
     * legacy 실패 컨텍스트를 공통 task-policy 입력 모델로 변환합니다.
     *
     * @param message legacy 명령
     * @param reasonCode DLQ reason code
     * @param reasonMessage 실패 메시지
     * @param traceId 추적 ID
     * @param cause 실패 예외
     * @return 공통 실패 컨텍스트
     */
    private TaskFailureContext buildLegacyFailureContext(
            final KafkaCommandMessage message,
            final DlqReasonCode reasonCode,
            final String reasonMessage,
            final String traceId,
            final Throwable cause
    ) {
        return new TaskFailureContext(
                COMMAND_SOURCE_TOPIC,
                0,
                0L,
                normalizeEqpId(message.equipmentId()),
                LEGACY_MESSAGE_TYPE,
                LEGACY_MESSAGE_NAME,
                1,
                buildLegacyPayloadRef(message, traceId),
                mapFailureCategory(reasonCode),
                cause != null ? cause : new IllegalStateException(safeReason(reasonMessage, "Legacy command failure")),
                false,
                clockPort.nowEpochMillis()
        );
    }

    /**
     * business 실패 컨텍스트를 공통 task-policy 입력 모델로 변환합니다.
     *
     * @param message business 명령
     * @param reasonCode DLQ reason code
     * @param reasonMessage 실패 메시지
     * @param traceId 추적 ID
     * @param cause 실패 예외
     * @return 공통 실패 컨텍스트
     */
    private TaskFailureContext buildBusinessFailureContext(
            final GatewayBusinessCommandMessage message,
            final DlqReasonCode reasonCode,
            final String reasonMessage,
            final String traceId,
            final Throwable cause
    ) {
        return new TaskFailureContext(
                COMMAND_SOURCE_TOPIC,
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
     * <p>현재 gateway command는 즉시 DLQ 정책을 사용하므로, 정책 결과가 DLQ가 아니더라도
     * fallback DLQ 레코드를 만들어 운영 가시성을 유지합니다.</p>
     *
     * @param failureContext 실패 컨텍스트
     * @return 공통 DLQ 레코드
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
     * legacy DLQ 태그를 구성합니다.
     *
     * <p>원본 attributes와 공통 DLQ 레코드 메타데이터를 함께 적재합니다.</p>
     *
     * @param attributes 원본 attributes
     * @param dlqRecord 공통 DLQ 레코드
     * @return 병합된 태그 맵
     */
    private Map<String, String> buildLegacyDlqTags(
            final Map<String, String> attributes,
            final DlqRecord dlqRecord
    ) {
        final Map<String, String> tags = new HashMap<>();
        if (attributes != null) {
            tags.putAll(attributes);
        }
        tags.put("policyFailureCategory", dlqRecord.failureCategory().name());
        tags.put("policyExceptionClass", dlqRecord.exceptionClass());
        tags.put("policyAttempts", String.valueOf(dlqRecord.attempts()));
        tags.put("payloadRef", dlqRecord.payloadRef());
        return tags;
    }

    /**
     * business DLQ 태그를 null 안전하게 구성합니다.
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
     * <p>DLQ 발행 실패는 보조 경로이므로 원 처리 흐름을 깨지 않도록 삼키고 로그만 남깁니다.</p>
     *
     * @param dlqMessage 발행할 DLQ 메시지
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
     * legacy 명령 payloadRef를 생성합니다.
     *
     * @param message legacy 명령
     * @param traceId 추적 ID
     * @return payloadRef 문자열
     */
    private String buildLegacyPayloadRef(final KafkaCommandMessage message, final String traceId) {
        final int payloadLength = message.payloadBase64() == null ? 0 : message.payloadBase64().length();
        return "payload://legacy/" + resolveTraceId(traceId) + "/len/" + payloadLength;
    }

    /**
     * business 명령 payloadRef를 생성합니다.
     *
     * @param message business 명령
     * @param traceId 추적 ID
     * @return payloadRef 문자열
     */
    private String buildBusinessPayloadRef(final GatewayBusinessCommandMessage message, final String traceId) {
        final String rawMessage = message.data() == null ? null : message.data().rawMessage();
        final int payloadLength = rawMessage == null ? 0 : rawMessage.getBytes(StandardCharsets.UTF_8).length;
        return "payload://business/" + resolveTraceId(traceId) + "/len/" + payloadLength;
    }

    /**
     * business 메시지의 eventType을 실패 컨텍스트용 messageName으로 변환합니다.
     *
     * @param message business 명령
     * @return messageName
     */
    private String resolveBusinessMessageName(final GatewayBusinessCommandMessage message) {
        final String eventType = message.metadata() == null ? null : message.metadata().eventType();
        final String normalizedEventType = normalizeText(eventType);
        return normalizedEventType == null ? "UNKNOWN_EVENT" : normalizedEventType;
    }

    /**
     * DLQ reason code를 공통 실패 카테고리로 매핑합니다.
     *
     * @param reasonCode DLQ reason code
     * @return 공통 실패 카테고리
     */
    private TaskFailureCategory mapFailureCategory(final DlqReasonCode reasonCode) {
        if (reasonCode == null) {
            return TaskFailureCategory.UNKNOWN;
        }
        return switch (reasonCode) {
            case INVALID_INPUT, BASE64_DECODE_FAIL, UNKNOWN_EQUIPMENT, PAYLOAD_TOO_LARGE ->
                    TaskFailureCategory.VALIDATION;
            case ROUTING_FAILED, PUBLISH_FAILED, INBOUND_QUEUE_OVERFLOW, REASSEMBLY_OVERFLOW, FRAMING_FAILED, PARSING_FAILED ->
                    TaskFailureCategory.ACTION_EXEC;
        };
    }

    /**
     * eqpId를 DLQ 기록 기준으로 보정합니다.
     *
     * @param eqpId 원본 eqpId
     * @return 보정된 eqpId
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

    /**
     * command 처리 disposition을 표준 로그/메트릭으로 기록합니다.
     *
     * <p>로그 레벨 정책:</p>
     * <p>1) ACCEPTED는 고빈도 이벤트이므로 debug로 남깁니다.</p>
     * <p>2) DLQ/REJECTED/RETRY는 운영 추적 핵심이므로 info로 남깁니다.</p>
     *
     * @param eqpId 설비 ID
     * @param traceId 추적 ID
     * @param disposition 표준 disposition
     * @param reason 처리 사유
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
     *
     * <p>관측 로그에서 traceId가 비어 있으면 고정값 N/A를 사용해
     * 불필요한 신규 traceId 발급을 피합니다.</p>
     */
    private String safeTraceIdForLog(final String traceId) {
        final String normalized = normalizeText(traceId);
        return normalized == null ? "N/A" : normalized;
    }
}
