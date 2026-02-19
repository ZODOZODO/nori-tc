package com.nori.tc.comm.adapters.kafka.messaging.ui;

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
 * UI task 처리 실패를 DLQ로 기록하는 보조 컴포넌트입니다.
 *
 * <p>REP 발행 실패, 라우팅 실패, 예외 재시도 소진 같은
 * 운영 추적이 필요한 케이스를 DLQ에 남깁니다.</p>
 */
@Component
public class GatewayUiTaskDlqPublisher implements KafkaTaskDlqReporter<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskDlqPublisher.class);

    private final ClockPort clockPort;
    private final TraceIdGeneratorPort traceIdGeneratorPort;
    private final DlqPublisherPort dlqPublisherPort;

    /**
     * DLQ 발행에 필요한 공통 포트를 주입합니다.
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
     * 공통 파이프라인 DLQ 보고 계약을 받아 gateway DLQ 메시지로 발행합니다.
     *
     * @param request 원본 요청
     * @param stage 실패 단계
     * @param reasonCode 실패 사유 코드
     * @param reasonMessage 실패 상세 메시지
     * @param replyEventType 응답 이벤트 타입
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
     * UI task 실패 정보를 실제 DLQ sink로 발행합니다.
     *
     * @param message 원본 UI task
     * @param stage 실패 단계(예: ROUTING/PUBLISH)
     * @param reasonCode 실패 사유 코드
     * @param reasonMessage 실패 상세 메시지
     * @param replyEventType 응답 이벤트 타입(가능한 경우)
     */
    private void publish(
            final KafkaUiTaskMessage message,
            final String stage,
            final DlqReasonCode reasonCode,
            final String reasonMessage,
            final String replyEventType
    ) {
        if (message == null) {
            log.warn("UI task DLQ publish skipped (message is null). stage={}, reasonCode={}", stage, reasonCode);
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
            log.warn("UI task DLQ published. eqpId={}, traceId={}, stage={}, reasonCode={}",
                    eqpId, traceId, dlqMessage.stage(), dlqMessage.reasonCode());
        } catch (Exception ex) {
            log.error("UI task DLQ publish failed. eqpId={}, traceId={}, stage={}, reasonCode={}",
                    eqpId, traceId, dlqMessage.stage(), dlqMessage.reasonCode(), ex);
        }
    }

    /**
     * DLQ 필수값인 eqpId를 보정합니다.
     */
    private String normalizeEqpId(final KafkaUiTaskMessage message) {
        final String eqpId = message.data() == null ? null : message.data().eqpId();
        if (eqpId == null || eqpId.isBlank()) {
            return "UNKNOWN_EQP";
        }
        return eqpId.trim();
    }

    /**
     * DLQ 필수값인 traceId를 보정합니다.
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
     * eventType 문자열을 안전하게 조회합니다.
     */
    private String safeEventType(final KafkaUiTaskMessage message) {
        final String eventType = message.metadata() == null ? null : message.metadata().eventType();
        if (eventType == null || eventType.isBlank()) {
            return "UNKNOWN_EVENT";
        }
        return eventType.trim();
    }

    /**
     * 공통 stage enum을 gateway DLQ stage 문자열로 변환합니다.
     *
     * @param stage 공통 stage
     * @return gateway DLQ stage 문자열
     */
    private static String toStage(final KafkaTaskPipelineStage stage) {
        if (stage == KafkaTaskPipelineStage.PUBLISH) {
            return DlqMessage.STAGE_PUBLISH;
        }
        return DlqMessage.STAGE_ROUTING;
    }

    /**
     * 공통 reasonCode를 gateway DLQ 사유 코드로 변환합니다.
     *
     * @param reasonCode 공통 reasonCode
     * @return gateway DLQ 사유 코드
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
