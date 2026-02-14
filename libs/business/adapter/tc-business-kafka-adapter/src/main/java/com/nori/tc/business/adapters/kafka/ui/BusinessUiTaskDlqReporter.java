package com.nori.tc.business.adapters.kafka.ui;

import com.nori.tc.business.core.dlq.BusinessDlqPublisherPort;
import com.nori.tc.business.domain.dlq.BusinessDlqMessage;
import com.nori.tc.common.ui.task.pipeline.UiTaskDlqReporter;
import com.nori.tc.common.ui.task.pipeline.UiTaskPipelineStage;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * UI 파이프라인 DLQ 리포터입니다.
 *
 * <p>역할:</p>
 * <p>1) 공통 UI 파이프라인에서 전달한 실패 정보를 DLQ 표준 메시지로 변환합니다.</p>
 * <p>2) DLQ 포트로 발행하여 저장소 구현(예: Redis)에 위임합니다.</p>
 * <p>3) DLQ 포트 장애가 발생해도 원 처리 흐름을 중단하지 않고 로그로 남깁니다.</p>
 */
@Component
public class BusinessUiTaskDlqReporter implements UiTaskDlqReporter<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiTaskDlqReporter.class);
    private static final String UNKNOWN_EQP_ID = "UNKNOWN_EQP";
    private static final String UNKNOWN_TRACE_ID = "UNKNOWN_TRACE";

    private final BusinessDlqPublisherPort dlqPublisherPort;

    /**
     * DLQ 포트를 주입받습니다.
     *
     * @param dlqPublisherPort DLQ 발행 포트
     */
    public BusinessUiTaskDlqReporter(final BusinessDlqPublisherPort dlqPublisherPort) {
        this.dlqPublisherPort = Objects.requireNonNull(dlqPublisherPort, "dlqPublisherPort is null");
    }

    @Override
    public void report(
            final KafkaUiTaskMessage request,
            final UiTaskPipelineStage stage,
            final String reasonCode,
            final String reasonMessage,
            final String replyEventType
    ) {
        final String eqpId = request == null || request.data() == null ? null : request.data().eqpId();
        final String traceId = request == null || request.metadata() == null ? null : request.metadata().traceId();
        final String eventType = request == null || request.metadata() == null ? null : request.metadata().eventType();

        final Map<String, String> tags = new HashMap<>();
        putIfHasText(tags, "eventType", eventType);
        putIfHasText(tags, "replyEventType", replyEventType);
        putIfHasText(tags, "reasonCode", reasonCode);
        if (request != null && request.metadata() != null) {
            putIfHasText(tags, "source", request.metadata().source());
            putIfHasText(tags, "timestamp", request.metadata().timestamp());
        }

        final BusinessDlqMessage dlqMessage = new BusinessDlqMessage(
                UUID.randomUUID().toString(),
                "BUSINESS_UI_PIPELINE",
                stage == null ? "UNKNOWN" : stage.name(),
                normalizeReasonCode(reasonCode),
                normalizeReasonMessage(reasonMessage),
                System.currentTimeMillis(),
                "tc.ui.events",
                null,
                null,
                normalizeEqpId(eqpId),
                "UI",
                normalizeMessageName(eventType),
                normalizeTraceId(traceId),
                buildPayloadRef(traceId, eventType),
                tags
        );

        try {
            dlqPublisherPort.publish(dlqMessage);
            log.info("UI task pipeline DLQ published. stage={}, reasonCode={}, eventType={}, replyEventType={}, eqpId={}, traceId={}",
                    dlqMessage.stage(),
                    dlqMessage.reasonCode(),
                    eventType,
                    replyEventType,
                    dlqMessage.eqpId(),
                    dlqMessage.traceId());
        } catch (Exception ex) {
            log.error("UI task pipeline DLQ publish failed. stage={}, reasonCode={}, eventType={}, replyEventType={}, eqpId={}, traceId={}",
                    dlqMessage.stage(),
                    dlqMessage.reasonCode(),
                    eventType,
                    replyEventType,
                    dlqMessage.eqpId(),
                    dlqMessage.traceId(),
                    ex);
        }
    }

    /**
     * 메시지 이벤트명을 DLQ messageName 필드로 보정합니다.
     */
    private static String normalizeMessageName(final String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "UNKNOWN_EVENT";
        }
        return eventType.trim();
    }

    /**
     * DLQ reasonCode를 빈 값 없이 보정합니다.
     */
    private static String normalizeReasonCode(final String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "UNKNOWN_REASON";
        }
        return reasonCode.trim();
    }

    /**
     * DLQ reasonMessage를 빈 값 없이 보정합니다.
     */
    private static String normalizeReasonMessage(final String reasonMessage) {
        if (reasonMessage == null || reasonMessage.isBlank()) {
            return "UI task pipeline failure";
        }
        return reasonMessage.trim();
    }

    /**
     * DLQ eqpId를 빈 값 없이 보정합니다.
     */
    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return UNKNOWN_EQP_ID;
        }
        return eqpId.trim();
    }

    /**
     * DLQ traceId를 빈 값 없이 보정합니다.
     */
    private static String normalizeTraceId(final String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return UNKNOWN_TRACE_ID;
        }
        return traceId.trim();
    }

    /**
     * payloadRef를 추적 가능한 문자열로 생성합니다.
     */
    private static String buildPayloadRef(final String traceId, final String eventType) {
        return "payload://ui/" + normalizeTraceId(traceId) + "/event/" + normalizeMessageName(eventType);
    }

    /**
     * 텍스트 값이 있는 경우에만 태그 맵에 추가합니다.
     */
    private static void putIfHasText(final Map<String, String> tags, final String key, final String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        tags.put(key, value.trim());
    }
}

