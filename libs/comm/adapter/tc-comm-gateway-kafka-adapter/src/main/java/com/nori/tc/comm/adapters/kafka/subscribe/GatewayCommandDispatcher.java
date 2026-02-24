package com.nori.tc.comm.adapters.kafka.subscribe;

import com.nori.tc.comm.adapters.kafka.config.GatewayKafkaTopicProperties;
import com.nori.tc.comm.adapters.kafka.contract.GatewayBusinessCommandMessage;
import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.message.OutboundRawFrame;
import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.gateway.runtime.channel.EquipmentChannelRegistry;
import com.nori.tc.comm.gateway.application.ingress.GatewayIngressService;
import com.nori.tc.comm.gateway.config.props.GatewaySocketProperties;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.comm.gateway.domain.dlq.DlqMessage;
import com.nori.tc.comm.gateway.domain.dlq.DlqReasonCode;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.comm.gateway.observability.metrics.GatewayDisposition;
import com.nori.tc.comm.gateway.observability.metrics.GatewayDispositionMetrics;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogContext;
import com.nori.tc.comm.gateway.observability.logging.GatewayLogSampler;
import com.nori.tc.comm.gateway.observability.metrics.GatewayMetrics;
import com.nori.tc.comm.gateway.socket.plugin.spi.GatewaySocketPluginRuntimeProvider;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeEncodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeHandler;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeRegistry;
import com.nori.tc.common.consumer.runtime.FixedRetryPolicy;
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
 * 게이트웨이 Business Command(Kafka 수신)를 설비별 outbound 큐로 변환/전달하는 디스패처입니다.
 *
 * <p>핵심 책임:</p>
 * <p>1) 메시지 스키마/필수 필드 검증</p>
 * <p>2) 설비/연결 상태 검증</p>
 * <p>3) SOCKET payload 인코딩(socketType/plugin 반영)</p>
 * <p>4) {@link GatewayIngressService}를 통한 outbound 큐 적재</p>
 * <p>5) 실패 시 DLQ/Quarantine/Disposition 메트릭 기록</p>
 */
@Component
public class GatewayCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GatewayCommandDispatcher.class);

    /**
     * eqpId를 식별할 수 없을 때 로그/DLQ 태그에 사용하는 기본값입니다.
     */
    private static final String UNKNOWN_EQP_ID = "UNKNOWN_EQP";

    /**
     * Task 처리 정책 평가 시 사용하는 메시지 타입 식별자입니다.
     */
    private static final String BUSINESS_MESSAGE_TYPE = "BUSINESS";

    /**
     * DLQ 레코드에 저장할 예외 메시지 최대 길이입니다.
     */
    private static final int DLQ_EXCEPTION_MESSAGE_MAX_LENGTH = 300;

    /**
     * Business Command 실패 처리 정책의 최대 재시도 횟수입니다.
     *
     * <p>현재는 재처리보다 DLQ 전환을 우선하므로 1회(즉시 실패 처리) 정책을 사용합니다.</p>
     */
    private static final int COMMAND_FAILURE_MAX_ATTEMPTS = 1;

    /**
     * disposition 메트릭/로그에서 사용하는 처리 흐름 식별자입니다.
     */
    private static final String FLOW_COMMAND = "COMMAND";
    /**
     * 토픽 정보가 없을 때 사용하는 기본 토픽명입니다.
     */
    private static final String UNKNOWN_TOPIC = "UNKNOWN_TOPIC";
    /**
     * 파티션 정보가 없을 때 사용하는 기본 파티션 값입니다.
     */
    private static final int UNKNOWN_PARTITION = -1;
    /**
     * 오프셋 정보가 없을 때 사용하는 기본 오프셋 값입니다.
     */
    private static final long UNKNOWN_OFFSET = -1L;

    private final EquipmentChannelRegistry channelRegistry;
    private final GatewayIngressService gatewayIngressService;
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
     * Business Command 디스패처 의존성을 초기화합니다.
     *
     * <p>생성 시점에 실패 처리 정책 평가기와 DLQ 레코드 팩토리를 함께 구성합니다.</p>
     *
     * @param channelRegistry 활성 설비 채널 레지스트리
     * @param gatewayIngressService 게이트웨이 인입/출력 큐 적재 진입 서비스
     * @param metrics 게이트웨이 공통 메트릭 수집기
     * @param logSampler 경고 로그 샘플링 정책
     * @param dispositionMetrics disposition 집계 메트릭
     * @param clockPort 현재 시각 제공 포트
     * @param traceIdGeneratorPort traceId 생성 포트
     * @param dlqPublisherPort DLQ 발행 포트
     * @param quarantinePort 설비 격리 포트
     * @param socketProperties SOCKET 기본 설정
     * @param socketTypeRegistry socketType 핸들러 레지스트리
     * @param socketPluginRuntimeProvider 설비별 SOCKET 플러그인 핸들러 조회 포트
     * @param topicProperties Kafka 토픽 설정
     */
    public GatewayCommandDispatcher(
            final EquipmentChannelRegistry channelRegistry,
            final GatewayIngressService gatewayIngressService,
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
        this.gatewayIngressService = Objects.requireNonNull(gatewayIngressService, "gatewayIngressService is null");
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

        log.info("GatewayCommandDispatcher initialized. flow={}, maxFailureAttempts={}, socketDefaultType={}",
                FLOW_COMMAND,
                COMMAND_FAILURE_MAX_ATTEMPTS,
                socketProperties.getDefaultSocketType());
    }

    /**
     * Kafka consumer가 topic/partition/offset 메타 정보 없이 전달하는 기본 경로를 처리합니다.
     *
     * <p>토픽/파티션/오프셋은 알 수 없는 값으로 보정하여 공통 처리 메서드에 위임합니다.</p>
     *
     * @param message 수신된 Business Command 메시지
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
     * Business Command 메시지를 검증/인코딩한 뒤 설비 outbound 큐로 전달합니다.
     *
     * <p>처리 흐름:</p>
     * <p>1) dispatch context 및 traceId 정규화</p>
     * <p>2) envelope 검증</p>
     * <p>3) 활성 채널/설비 메타 정보 검증</p>
     * <p>4) SOCKET payload 인코딩</p>
     * <p>5) outbound 큐 적재 또는 DLQ/격리 처리</p>
     *
     * @param message Kafka에서 수신한 Business Command 메시지
     * @param topic 원본 토픽명
     * @param partition 원본 파티션
     * @param offset 원본 오프셋
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
                // 현재 Phase 범위에서는 HSMS business command 송신 경로를 구현하지 않았으므로 DLQ로 전환합니다.
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
                gatewayIngressService.enqueueOutbound(frame);
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
     * Business Command 메시지에서 공통 검증/정규화를 수행하고 내부 처리용 envelope를 생성합니다.
     *
     * <p>검증 실패 시 예외를 던지지 않고 DLQ를 발행한 뒤 {@code null}을 반환합니다.</p>
     *
     * @param message 원본 Business Command 메시지
     * @param traceId 처리 traceId
     * @param dispatchContext 토픽/파티션/오프셋 정보
     * @return 검증 성공 시 envelope, 실패 시 {@code null}
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
     * 설비 메타 정보를 조회하고 실패 시 DLQ를 발행합니다.
     *
     * @param message 원본 Business Command 메시지
     * @param envelope 검증된 명령 envelope
     * @param traceId 처리 traceId
     * @param dispatchContext 토픽/파티션/오프셋 정보
     * @return 설비 메타 정보, 조회 실패 시 {@code null}
     */
    private GatewayEquipmentInfo resolveEquipmentOrPublishDlq(
            final GatewayBusinessCommandMessage message,
            final CommandEnvelope envelope,
            final String traceId,
            final DispatchContext dispatchContext
    ) {
        try {
            return gatewayIngressService.resolveEquipment(envelope.eqpId());
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Equipment lookup failed during business command dispatch. eqpId={}, traceId={}",
                        envelope.eqpId(),
                        traceId,
                        ex);
            }
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
     * rawMessage를 socketType 핸들러로 인코딩하고 실패 시 DLQ를 발행합니다.
     *
     * @param message 원본 Business Command 메시지
     * @param envelope 검증된 명령 envelope
     * @param socketType 사용할 socketType
     * @param traceId 처리 traceId
     * @param dispatchContext 토픽/파티션/오프셋 정보
     * @return 인코딩된 payload 바이트 배열, 실패 시 {@code null}
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
     * 설비에 활성 채널이 존재하는지 확인합니다.
     *
     * @param equipmentId 설비 ID
     * @return 활성 채널이 존재하면 {@code true}
     */
    private boolean hasActiveChannel(final EquipmentId equipmentId) {
        final var channel = channelRegistry.get(equipmentId);
        return channel != null && channel.isActive();
    }

    /**
     * SOCKET rawMessage를 실제 전송 바이트 배열로 인코딩합니다.
     *
     * <p>설비별 플러그인 핸들러가 있으면 우선 사용하고, 없으면 공통 {@link SocketTypeRegistry}를 사용합니다.</p>
     *
     * @param eqpId 설비 ID
     * @param rawMessage UI/Kafka에서 전달된 원본 문자열 메시지
     * @param socketType 설비에 적용할 socketType
     * @return 인코딩된 바이트 배열
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
     * 설비 메타 정보에서 socketType을 해석합니다.
     *
     * <p>설비별 socketType이 없으면 gateway 기본 socketType으로 fallback 합니다.</p>
     *
     * @param equipmentInfo 설비 메타 정보
     * @return 해석된 socketType
     */
    private String resolveSocketType(final GatewayEquipmentInfo equipmentInfo) {
        final String fromEquipment = normalizeText(equipmentInfo.socketType());
        if (fromEquipment != null) {
            return fromEquipment;
        }
        if (log.isDebugEnabled()) {
            log.debug("Equipment socketType not set. fallback to default socketType. eqpId={}, fallbackSocketType={}",
                    equipmentInfo.equipmentId(),
                    socketProperties.getDefaultSocketType());
        }
        return socketProperties.getDefaultSocketType();
    }

    /**
     * Business Command 실패 정보를 DLQ 메시지로 변환해 발행합니다.
     *
     * <p>Task 실패 정책 평가 결과(DLQ record)를 반영하여 표준 DLQ 메시지를 구성합니다.</p>
     *
     * @param message 원본 Business Command 메시지
     * @param stage 실패 단계
     * @param reasonCode DLQ 사유 코드
     * @param reasonMessage 사유 메시지
     * @param traceId traceId
     * @param socketTypeForLog socketType(로그/DLQ 태그용)
     * @param cause 예외 원인(없으면 null)
     * @param dispatchContext 토픽/파티션/오프셋 정보
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
     * Business Command 실패 상황을 정책 평가용 {@link TaskFailureContext}로 변환합니다.
     *
     * @param message 원본 Business Command 메시지
     * @param reasonCode 실패 사유 코드
     * @param reasonMessage 실패 사유 메시지
     * @param traceId traceId
     * @param cause 예외 원인(없으면 null)
     * @return 정책 평가용 실패 컨텍스트
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
     * 실패 처리 정책을 평가해 DLQ 레코드를 확정합니다.
     *
     * <p>정책이 DLQ 이외 액션을 반환하더라도 현재 구현은 fallback으로 DLQ 레코드를 생성합니다.</p>
     *
     * @param failureContext 실패 컨텍스트
     * @return 최종 DLQ 레코드
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
     * Business Command DLQ 메시지에 첨부할 태그 맵을 구성합니다.
     *
     * @param message 원본 Business Command 메시지
     * @param dlqRecord 정책 평가 결과 DLQ 레코드
     * @return DLQ 태그 맵
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
     * DLQ 발행을 안전하게 수행하고 disposition/메트릭을 함께 기록합니다.
     *
     * <p>DLQ 발행 실패도 주 처리 흐름을 멈추지 않으며, 실패 disposition을 별도로 기록합니다.</p>
     *
     * @param dlqMessage 발행할 DLQ 메시지
     * @param dispatchContext 토픽/파티션/오프셋 정보
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
     * Business Command payload를 식별하는 참조 문자열을 생성합니다.
     *
     * <p>현재는 실제 저장소 위치가 아닌 traceId + payload 길이를 기반으로 한 논리 참조를 사용합니다.</p>
     *
     * @param message 원본 Business Command 메시지
     * @param traceId traceId
     * @return payload 참조 문자열
     */
    private String buildBusinessPayloadRef(final GatewayBusinessCommandMessage message, final String traceId) {
        final String rawMessage = message.data() == null ? null : message.data().rawMessage();
        final int payloadLength = rawMessage == null ? 0 : rawMessage.getBytes(StandardCharsets.UTF_8).length;
        return "payload://business/" + resolveTraceId(traceId) + "/len/" + payloadLength;
    }

    /**
     * Business Command 메시지명을 해석합니다.
     *
     * <p>현재는 metadata.eventType을 메시지명으로 사용하며, 없으면 {@code UNKNOWN_EVENT}를 반환합니다.</p>
     *
     * @param message 원본 Business Command 메시지
     * @return 메시지명
     */
    private String resolveBusinessMessageName(final GatewayBusinessCommandMessage message) {
        final String eventType = message.metadata() == null ? null : message.metadata().eventType();
        final String normalizedEventType = normalizeText(eventType);
        return normalizedEventType == null ? "UNKNOWN_EVENT" : normalizedEventType;
    }

    /**
     * DLQ 사유 코드를 공통 task 실패 카테고리로 매핑합니다.
     *
     * @param reasonCode DLQ 사유 코드
     * @return 정책 평가용 실패 카테고리
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
     * eqpId를 로그/DLQ 안전 문자열로 정규화합니다.
     *
     * @param eqpId 원본 eqpId
     * @return 정규화된 eqpId, 없으면 {@link #UNKNOWN_EQP_ID}
     */
    private String normalizeEqpId(final String eqpId) {
        final String normalized = normalizeText(eqpId);
        return normalized == null ? UNKNOWN_EQP_ID : normalized;
    }

    /**
     * 값이 비어 있지 않을 때만 태그 맵에 추가합니다.
     *
     * @param tags 대상 태그 맵
     * @param key 태그 키
     * @param value 태그 값(정규화 후 비어 있으면 추가하지 않음)
     */
    private void putIfHasText(final Map<String, String> tags, final String key, final String value) {
        final String normalized = normalizeText(value);
        if (normalized != null) {
            tags.put(key, normalized);
        }
    }

    /**
     * traceId를 정규화하고 없으면 새 traceId를 발급합니다.
     *
     * @param traceId 외부에서 전달된 traceId
     * @return 사용할 traceId
     */
    private String resolveTraceId(final String traceId) {
        final String normalized = normalizeText(traceId);
        return normalized == null ? traceIdGeneratorPort.newTraceId() : normalized;
    }

    /**
     * 인터페이스 타입 문자열을 파싱하고 실패 시 지정한 기본값으로 대체합니다.
     *
     * @param interfaceType 원본 인터페이스 타입 문자열
     * @param fallback 파싱 실패 시 사용할 기본값
     * @return 파싱 결과 또는 기본값
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
     * 사유 메시지를 정규화하고 비어 있으면 기본 메시지로 대체합니다.
     *
     * @param reasonMessage 원본 사유 메시지
     * @param fallback 기본 사유 메시지
     * @return 최종 사유 메시지
     */
    private String safeReason(final String reasonMessage, final String fallback) {
        final String normalized = normalizeText(reasonMessage);
        return normalized == null ? fallback : normalized;
    }

    /**
     * 문자열을 trim 후 비어 있으면 {@code null}로 정규화합니다.
     *
     * @param value 원본 문자열
     * @return 정규화된 문자열 또는 {@code null}
     */
    private String normalizeText(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 설비 격리 요청을 안전하게 수행합니다.
     *
     * <p>격리 실패는 주 처리 흐름을 중단하지 않되, 운영 추적을 위해 debug 로그로 남깁니다.</p>
     *
     * @param equipmentId 격리 대상 설비 ID
     * @param reasonCode 격리 사유 코드
     * @param reasonMessage 격리 사유 메시지
     */
    private void safeQuarantine(
            final EquipmentId equipmentId,
            final DlqReasonCode reasonCode,
            final String reasonMessage
    ) {
        try {
            quarantinePort.quarantine(equipmentId, reasonCode.name(), reasonMessage);
        } catch (Exception ignored) {
            if (log.isDebugEnabled()) {
                log.debug("Business command quarantine failed. eqpId={}, reasonCode={}",
                        equipmentId == null ? null : equipmentId.value(),
                        reasonCode,
                        ignored);
            }
        }
    }

    /**
     * Business Command disposition 메트릭과 운영 로그를 기록합니다.
     *
     * <p>ACCEPTED는 고빈도 경로이므로 debug, 그 외 disposition은 info로 기록합니다.</p>
     *
     * @param dispatchContext 토픽/파티션/오프셋 정보
     * @param eqpId 설비 ID
     * @param traceId traceId
     * @param disposition disposition 결과
     * @param reason disposition 사유
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
     * 토픽/파티션/오프셋 메타 정보를 로그/DLQ 기록용으로 정규화합니다.
     *
     * @param topic 원본 토픽명
     * @param partition 원본 파티션
     * @param offset 원본 오프셋
     * @return 정규화된 dispatch context
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
     * 로그 출력용 traceId를 반환합니다.
     *
     * @param traceId 원본 traceId
     * @return 정규화된 traceId, 없으면 {@code N/A}
     */
    private String safeTraceIdForLog(final String traceId) {
        final String normalized = normalizeText(traceId);
        return normalized == null ? "N/A" : normalized;
    }

    /**
     * 디스패치 메타 정보(토픽/파티션/오프셋)를 묶는 내부 레코드입니다.
     */
    private record DispatchContext(
            String topic,
            int partition,
            long offset
    ) {
    }

    /**
     * 검증/정규화가 완료된 Business Command 핵심 필드를 보관하는 내부 레코드입니다.
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
