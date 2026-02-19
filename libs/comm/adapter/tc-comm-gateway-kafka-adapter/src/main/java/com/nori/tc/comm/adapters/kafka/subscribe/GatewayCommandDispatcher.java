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
 * Gateway inbound command ?遺용뮞??μ퓗??낅빍??
 *
 * <p>??블?疫꿸퀣?:</p>
 * <p>1) ??낆젾 ?④쑴鍮?? {@link GatewayBusinessCommandMessage}(metadata + data)筌???됱뒠??몃빍??</p>
 * <p>2) Kafka key/?醫뤿동 ?類ㅼ퐠?? ?怨몄맄 consumer ?④쑴留?癒?퐣 癰귣똻???랁? 癰??????삳뮉 ??쇱젫 ??る뻿 ??野꺜筌???깆뒭??낆춸 ?????몃빍??</p>
 * <p>3) ??쎈솭???⑤벏??task-policy + DLQ + disposition 筌롫??껆뵳??앮에?????酉鍮??덈뼄.</p>
 *
 * <p>?袁⑹삺 甕곕뗄??</p>
 * <p>- SOCKET 筌뤿굝議???る뻿 ??뽮쉐??/p>
 * <p>- HSMS 筌뤿굝議???る뻿?? TODO ?類ㅼ퐠???怨뺤뵬 DLQ嚥??브쑬履?/p>
 */
@Component
public class GatewayCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GatewayCommandDispatcher.class);

    /**
     * DLQ 疫꿸퀡以???eqpId揶쎛 ??쑴堉???됱뱽 ?????????筌???명?癒?뿯??덈뼄.
     */
    private static final String UNKNOWN_EQP_ID = "UNKNOWN_EQP";

    /**
     * task-policy?癒?퐣 ?????筌롫뗄?놅쭪? ?????브쑬履잌첎誘れ뿯??덈뼄.
     */
    private static final String BUSINESS_MESSAGE_TYPE = "BUSINESS";

    /**
     * ?⑤벏??DLQ ??됲맜??뽰벥 ??됱뇚 筌롫뗄?놅쭪? 筌ㅼ뮆? 疫뀀챷???낅빍??
     */
    private static final int DLQ_EXCEPTION_MESSAGE_MAX_LENGTH = 300;

    /**
     * gateway command ??쎈솭 ?類ㅼ퐠 筌ㅼ뮆? ??뺣즲 ??쏅땾??낅빍??
     *
     * <p>?袁⑹삺 ?類ㅼ퐠?? 筌앸맩??DLQ??疫꿸퀡???곗쨮 ?????몃빍??</p>
     */
    private static final int COMMAND_FAILURE_MAX_ATTEMPTS = 1;

    /**
     * disposition 筌롫??껆뵳???flow ??쇱뿯??덈뼄.
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
     * ?遺용뮞??μ퓗 ??뤵?源놁뱽 ?λ뜃由?酉鍮??덈뼄.
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
     * Business 筌뤿굝議??④쑴鍮?metadata + data)??筌ｌ꼶???몃빍??
     *
     * <p>筌ｌ꼶????뽮퐣:</p>
     * <p>1) envelope ?袁⑸땾揶?野꺜筌?/p>
     * <p>2) ?貫??筌?쑬瑗??紐낃숲??륁뵠???類λ???野꺜筌?/p>
     * <p>3) socketType ?紐꾪맜????outbound enqueue</p>
     * <p>4) ??쎈솭 ???⑤벏???類ㅼ퐠 疫꿸퀡而?DLQ 獄쏆뮉六?獄??袁⑹뒄 ??quarantine</p>
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
                 * TODO: HSMS business command ??る뻿 野껋럥以???類ㅼ퐠 ?類ㅼ젟 ???닌뗭겱??몃빍??
                 * ?袁⑹삺????롫즲?怨몄몵嚥?DLQ嚥??브쑬履????곸겫 揶쎛??뽮쉐???醫???몃빍??
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
     * ??뤿뻿 envelope ?袁⑸땾揶쏅???野꺜筌앹빜釉????る뻿 筌ｌ꼶????낆젾 筌뤴뫀?썸에?癰궰??묐???덈뼄.
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
     * ?貫???袁⑥쨮??鈺곌퀬?띄몴???묐뻬??랁???쎈솭 ??DLQ??獄쏆뮉六??몃빍??
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
     * SOCKET payload ?紐꾪맜??뱀뱽 ??묐뻬??랁???쎈솭 ??DLQ??獄쏆뮉六??몃빍??
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
     * ??뽮쉐 筌?쑬瑗?鈺곕똻????????類ㅼ뵥??몃빍??
     */
    private boolean hasActiveChannel(final EquipmentId equipmentId) {
        final var channel = channelRegistry.get(equipmentId);
        return channel != null && channel.isActive();
    }

    /**
     * SOCKET rawMessage??socketType ?紐껊굶??域뱀뮇???곗쨮 ?紐꾪맜??븍???덈뼄.
     *
     * <p>?類ㅼ퐠:</p>
     * <p>1) ??삵돩癰????쑎域밸챷???紐껊굶??? ??됱몵筌??怨쀪퐨 ?????몃빍??</p>
     * <p>2) ??곸몵筌?疫꿸퀡??registry ?紐껊굶??? ?????몃빍??</p>
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
     * ?貫???類ｋ궖?癒?퐣 socketType????꾪? ??쑴堉???됱몵筌?疫꿸퀡??socketType??곗쨮 癰귣똻???몃빍??
     */
    private String resolveSocketType(final GatewayEquipmentInfo equipmentInfo) {
        final String fromEquipment = normalizeText(equipmentInfo.socketType());
        if (fromEquipment != null) {
            return fromEquipment;
        }
        return socketProperties.getDefaultSocketType();
    }

    /**
     * business 筌뤿굝議?筌ｌ꼶????쎈솭??DLQ嚥?疫꿸퀡以??몃빍??
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
     * business ??쎈솭 ?뚢뫂???쎈뱜???⑤벏??task-policy ??낆젾 筌뤴뫀?썸에?癰궰??묐???덈뼄.
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
     * ?⑤벏????쎈솭 ?類ㅼ퐠??곗쨮 DLQ ??됲맜??? ?④쑴沅??몃빍??
     *
     * <p>?袁⑹삺 ?類ㅼ퐠?? ?????筌앸맩??DLQ??疫꿸퀡???곗쨮 ??????筌?
     * ?類ㅼ퐠 癰궰野???뽯퓠??獄쎻뫗堉?怨몄몵嚥?fallback DLQ????밴쉐??몃빍??</p>
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
     * business DLQ ??볥젃??null-safe ??띿쓺 ?닌딄쉐??몃빍??
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
     * DLQ 獄쏆뮉六????됱읈??띿쓺 ??묐뻬??몃빍??
     *
     * <p>DLQ 獄쏆뮉六???쎈솭??癰귣똻??野껋럥以???嚥???筌ｌ꼶???癒?カ??餓λ쵎???? ??꾪?     * disposition/?癒?쑎 嚥≪뮄?뉛쭕???ｍ돥??덈뼄.</p>
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
     * business 筌뤿굝議?payloadRef????밴쉐??몃빍??
     */
    private String buildBusinessPayloadRef(final GatewayBusinessCommandMessage message, final String traceId) {
        final String rawMessage = message.data() == null ? null : message.data().rawMessage();
        final int payloadLength = rawMessage == null ? 0 : rawMessage.getBytes(StandardCharsets.UTF_8).length;
        return "payload://business/" + resolveTraceId(traceId) + "/len/" + payloadLength;
    }

    /**
     * business 筌롫뗄?놅쭪???eventType????쎈솭 ?뚢뫂???쎈뱜??messageName??곗쨮 癰궰??묐???덈뼄.
     */
    private String resolveBusinessMessageName(final GatewayBusinessCommandMessage message) {
        final String eventType = message.metadata() == null ? null : message.metadata().eventType();
        final String normalizedEventType = normalizeText(eventType);
        return normalizedEventType == null ? "UNKNOWN_EVENT" : normalizedEventType;
    }

    /**
     * DLQ reason code???⑤벏????쎈솭 燁삳똾?믤⑥쥓?곫에?筌띲끋釉??몃빍??
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
     * eqpId??DLQ 疫꿸퀡以?疫꿸퀣???곗쨮 癰귣똻???몃빍??
     */
    private String normalizeEqpId(final String eqpId) {
        final String normalized = normalizeText(eqpId);
        return normalized == null ? UNKNOWN_EQP_ID : normalized;
    }

    /**
     * ?얜챷???곸뵠 ??쑴堉???? ??놁뱽 ???춸 ??볥젃 筌띾벊肉?揶쏅????곕떽???몃빍??
     */
    private void putIfHasText(final Map<String, String> tags, final String key, final String value) {
        final String normalized = normalizeText(value);
        if (normalized != null) {
            tags.put(key, normalized);
        }
    }

    /**
     * traceId??癰귣똻???몃빍??
     */
    private String resolveTraceId(final String traceId) {
        final String normalized = normalizeText(traceId);
        return normalized == null ? traceIdGeneratorPort.newTraceId() : normalized;
    }

    /**
     * interfaceType ???뼓 ??쎈솭 ??疫꿸퀡??첎誘れ뱽 獄쏆꼹???몃빍??
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
     * reasonMessage??null/blank ??됱읈??띿쓺 癰귣똻???몃빍??
     */
    private String safeReason(final String reasonMessage, final String fallback) {
        final String normalized = normalizeText(reasonMessage);
        return normalized == null ? fallback : normalized;
    }

    /**
     * ?얜챷???곸뱽 trim??랁???쑴堉???됱몵筌?null??獄쏆꼹???몃빍??
     */
    private String normalizeText(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * ?貫??野꺿뫖???紐꾪뀱????됱읈??띿쓺 ??묐뻬??몃빍??
     */
    private void safeQuarantine(
            final EquipmentId equipmentId,
            final DlqReasonCode reasonCode,
            final String reasonMessage
    ) {
        try {
            quarantinePort.quarantine(equipmentId, reasonCode.name(), reasonMessage);
        } catch (Exception ignored) {
            // 野꺿뫖????쎈솭??癰귣똻??筌ｌ꼶????嚥?癰??癒?カ??獄쎻뫚鍮??? ??녿뮸??덈뼄.
        }
    }

    /**
     * command 筌ｌ꼶??disposition????? 嚥≪뮄??筌롫??껆뵳??앮에?疫꿸퀡以??몃빍??
     *
     * <p>嚥≪뮄????덇볼 ?類ㅼ퐠:</p>
     * <p>1) ACCEPTED???⑥쥓?????源?紐꾩뵠沃샕嚥?debug嚥?疫꿸퀡以??몃빍??</p>
     * <p>2) DLQ/REJECTED????곸겫 ?곕뗄?????뼎???嚥?info嚥?疫꿸퀡以??몃빍??</p>
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
     * 嚥≪뮄???곗뮆???traceId??癰귣똻???몃빍??
     */
    private String safeTraceIdForLog(final String traceId) {
        final String normalized = normalizeText(traceId);
        return normalized == null ? "N/A" : normalized;
    }

    /**
     * ??る뻿 筌ｌ꼶??餓λ쵌而??怨밴묶????????? 筌뤴뫀???낅빍??
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

