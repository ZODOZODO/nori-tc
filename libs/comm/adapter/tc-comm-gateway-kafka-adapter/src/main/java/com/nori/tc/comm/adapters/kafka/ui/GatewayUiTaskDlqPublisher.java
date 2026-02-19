package com.nori.tc.comm.adapters.kafka.ui;

import com.nori.tc.comm.core.port.ClockPort;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.core.port.TraceIdGeneratorPort;
import com.nori.tc.comm.gateway.domain.dlq.DlqMessage;
import com.nori.tc.comm.gateway.domain.dlq.DlqReasonCode;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskDlqReporter;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskPipelineReasonCode;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskPipelineStage;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * UI Task 파이프라인 실패를 Gateway DLQ 메시지로 변환하여 저장하는 리포터입니다.
 *
 * <p>주요 책임:</p>
 * <p>1) 파이프라인 stage/reasonCode를 DLQ 표준 코드로 정규화합니다.</p>
 * <p>2) traceId/eqpId/interfaceType을 보정하여 DLQ 메시지를 구성합니다.</p>
 * <p>3) DLQ 발행 성공/실패를 로그로 남깁니다.</p>
 */
@Component
public class GatewayUiTaskDlqPublisher implements KafkaTaskDlqReporter<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskDlqPublisher.class);

    private final ClockPort clockPort;
    private final TraceIdGeneratorPort traceIdGeneratorPort;
    private final DlqPublisherPort dlqPublisherPort;

    /**
     * DLQ 리포터 의존성을 초기화합니다.
     *
     * @param clockPort 현재 시간 제공 포트
     * @param traceIdGeneratorPort traceId 생성 포트
     * @param dlqPublisherPort DLQ 발행 포트
     */
    public GatewayUiTaskDlqPublisher(
            final ClockPort clockPort,
            final TraceIdGeneratorPort traceIdGeneratorPort,
            final DlqPublisherPort dlqPublisherPort
    ) {
        this.clockPort = Objects.requireNonNull(clockPort, "clockPort is null");
        this.traceIdGeneratorPort = Objects.requireNonNull(traceIdGeneratorPort, "traceIdGeneratorPort is null");
        this.dlqPublisherPort = Objects.requireNonNull(dlqPublisherPort, "dlqPublisherPort is null");
    }

    /**
     * 공통 파이프라인에서 전달한 실패 정보를 DLQ로 기록합니다.
     *
     * @param request 원본 UI Task 메시지
     * @param stage 실패 단계(ROUTING/PROCESS/PUBLISH)
     * @param reasonCode 실패 코드
     * @param reasonMessage 실패 상세 메시지
     * @param replyEventType 대응 Reply 이벤트 타입
     */
    @Override
    public void report(
            final KafkaUiTaskMessage request,
            final KafkaTaskPipelineStage stage,
            final String reasonCode,
            final String reasonMessage,
            final String replyEventType
    ) {
        publish(
                request,
                toStage(stage),
                toDlqReasonCode(reasonCode),
                reasonMessage,
                replyEventType
        );
    }

    /**
     * DLQ 메시지를 구성해 발행합니다.
     *
     * @param message 원본 UI Task 메시지
     * @param stage DLQ stage 문자열
     * @param reasonCode DLQ reason code
     * @param reasonMessage 실패 상세 메시지
     * @param replyEventType 대응 Reply 이벤트 타입
     */
    private void publish(
            final KafkaUiTaskMessage message,
            final String stage,
            final DlqReasonCode reasonCode,
            final String reasonMessage,
            final String replyEventType
    ) {
        if (message == null) {
            log.warn("UI task DLQ publish skipped because message is null. stage={}, reasonCode={}", stage, reasonCode);
            return;
        }

        final String eqpId = normalizeEqpId(message);
        final String traceId = normalizeTraceId(message);
        final CommInterfaceType interfaceType = resolveInterfaceType(message);
        final long occurredAt = clockPort.nowEpochMillis();

        final Map<String, String> tags = new LinkedHashMap<>();
        tags.put("source", "UI_TASK");
        tags.put("eventType", safeEventType(message));
        tags.put("replyEventType", (replyEventType == null || replyEventType.isBlank()) ? "n/a" : replyEventType);
        tags.put("reasonCode", reasonCode == null ? "n/a" : reasonCode.name());

        final DlqMessage dlqMessage = new DlqMessage(
                traceIdGeneratorPort.newTraceId(),
                eqpId,
                traceId,
                interfaceType,
                null,
                stage == null || stage.isBlank() ? DlqMessage.STAGE_ROUTING : stage,
                reasonCode == null ? DlqReasonCode.ROUTING_FAILED : reasonCode,
                reasonMessage == null ? "UI task DLQ publish" : reasonMessage,
                occurredAt,
                null,
                DlqMessage.UNKNOWN_LENGTH,
                DlqMessage.UNKNOWN_LENGTH,
                tags
        );

        try {
            dlqPublisherPort.publish(dlqMessage);
            log.warn(
                    "UI task DLQ published. eqpId={}, traceId={}, stage={}, reasonCode={}",
                    eqpId,
                    traceId,
                    dlqMessage.stage(),
                    dlqMessage.reasonCode()
            );
        } catch (Exception ex) {
            log.error(
                    "UI task DLQ publish failed. eqpId={}, traceId={}, stage={}, reasonCode={}",
                    eqpId,
                    traceId,
                    dlqMessage.stage(),
                    dlqMessage.reasonCode(),
                    ex
            );
        }
    }

    /**
     * DLQ 저장용 eqpId를 보정합니다.
     *
     * @param message 원본 메시지
     * @return 비어있지 않은 eqpId
     */
    private String normalizeEqpId(final KafkaUiTaskMessage message) {
        final String eqpId = message.data() == null ? null : message.data().eqpId();
        if (eqpId == null || eqpId.isBlank()) {
            return "UNKNOWN_EQP";
        }
        return eqpId.trim();
    }

    /**
     * DLQ 저장용 traceId를 보정합니다.
     *
     * @param message 원본 메시지
     * @return 비어있지 않은 traceId
     */
    private String normalizeTraceId(final KafkaUiTaskMessage message) {
        final String traceId = message.metadata() == null ? null : message.metadata().traceId();
        if (traceId == null || traceId.isBlank()) {
            return traceIdGeneratorPort.newTraceId();
        }
        return traceId.trim();
    }

    /**
     * interfaceType 문자열을 안전하게 enum으로 변환합니다.
     *
     * @param message 원본 메시지
     * @return 변환된 interfaceType, 실패 시 SOCKET
     */
    private CommInterfaceType resolveInterfaceType(final KafkaUiTaskMessage message) {
        try {
            return CommInterfaceType.fromText(
                    message.data() == null ? null : message.data().interfaceType()
            );
        } catch (Exception ex) {
            return CommInterfaceType.SOCKET;
        }
    }

    /**
     * eventType 문자열을 DLQ 태그에 저장 가능한 형태로 보정합니다.
     *
     * @param message 원본 메시지
     * @return 비어있지 않은 eventType
     */
    private String safeEventType(final KafkaUiTaskMessage message) {
        final String eventType = message.metadata() == null ? null : message.metadata().eventType();
        if (eventType == null || eventType.isBlank()) {
            return "UNKNOWN_EVENT";
        }
        return eventType.trim();
    }

    /**
     * 파이프라인 stage를 DLQ stage 문자열로 변환합니다.
     *
     * @param stage 파이프라인 stage
     * @return DLQ stage
     */
    private static String toStage(final KafkaTaskPipelineStage stage) {
        if (stage == KafkaTaskPipelineStage.PUBLISH) {
            return DlqMessage.STAGE_PUBLISH;
        }
        return DlqMessage.STAGE_ROUTING;
    }

    /**
     * 파이프라인 reasonCode를 DLQ reasonCode로 변환합니다.
     *
     * @param reasonCode 파이프라인 reasonCode
     * @return DLQ reasonCode
     */
    private static DlqReasonCode toDlqReasonCode(final String reasonCode) {
        if (KafkaTaskPipelineReasonCode.PUBLISH_FAILED.equals(reasonCode)) {
            return DlqReasonCode.PUBLISH_FAILED;
        }
        if (KafkaTaskPipelineReasonCode.PROCESS_FAILED.equals(reasonCode)) {
            return DlqReasonCode.ROUTING_FAILED;
        }
        return DlqReasonCode.ROUTING_FAILED;
    }
}
