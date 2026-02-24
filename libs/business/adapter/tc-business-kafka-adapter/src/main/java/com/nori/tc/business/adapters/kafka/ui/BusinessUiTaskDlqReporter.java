package com.nori.tc.business.adapters.kafka.ui;

import com.nori.tc.business.core.config.BusinessCoreRuntimeProperties;
import com.nori.tc.business.core.dlq.BusinessDlqPublisherPort;
import com.nori.tc.business.domain.dlq.BusinessDlqMessage;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDlqReporter;
import com.nori.tc.common.task.execution.pipeline.constants.KafkaTaskPipelineStage;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Business UI Task 실패를 DLQ로 전환하는 리포터입니다.
 *
 * <p>역할:</p>
 * <p>1) 파이프라인 실패 정보를 Business DLQ 계약으로 변환</p>
 * <p>2) DLQ 발행 포트를 통해 영속 저장소(현재 Redis)로 전달</p>
 * <p>3) 성공/실패 로그를 표준 키로 기록</p>
 */
@Component
public class BusinessUiTaskDlqReporter implements KafkaTaskDlqReporter<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiTaskDlqReporter.class);
    private static final String UNKNOWN_EQP_ID = "UNKNOWN_EQP";
    private static final String UNKNOWN_TRACE_ID = "UNKNOWN_TRACE";

    private final BusinessDlqPublisherPort dlqPublisherPort;
    private final BusinessCoreRuntimeProperties runtimeProperties;

    /**
     * DLQ 리포터 의존성을 초기화합니다.
     *
     * @param dlqPublisherPort DLQ 발행 포트
     * @param runtimeProperties Business 런타임 설정
     */
    public BusinessUiTaskDlqReporter(
            final BusinessDlqPublisherPort dlqPublisherPort,
            final BusinessCoreRuntimeProperties runtimeProperties
    ) {
        this.dlqPublisherPort = Objects.requireNonNull(dlqPublisherPort, "dlqPublisherPort is null");
        this.runtimeProperties = Objects.requireNonNull(runtimeProperties, "runtimeProperties is null");
    }

    /**
     * 파이프라인 실패 정보를 DLQ로 변환해 발행합니다.
     *
     * @param request 원본 UI Task 메시지
     * @param stage 실패 단계
     * @param reasonCode 실패 코드
     * @param reasonMessage 실패 메시지
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

        // 토픽 문자열은 하드코딩하지 않고 런타임 설정값을 사용합니다.
        final String uiEventsTopic = runtimeProperties.getKafka().getUiEventsTopic();
        final BusinessDlqMessage dlqMessage = new BusinessDlqMessage(
                UUID.randomUUID().toString(),
                "BUSINESS_UI_PIPELINE",
                stage == null ? "UNKNOWN" : stage.name(),
                normalizeReasonCode(reasonCode),
                normalizeReasonMessage(reasonMessage),
                System.currentTimeMillis(),
                uiEventsTopic,
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
            log.info("UI task pipeline DLQ published. topic={}, stage={}, reasonCode={}, eventType={}, replyEventType={}, eqpId={}, traceId={}",
                    dlqMessage.topic(),
                    dlqMessage.stage(),
                    dlqMessage.reasonCode(),
                    eventType,
                    replyEventType,
                    dlqMessage.eqpId(),
                    dlqMessage.traceId());
        } catch (Exception ex) {
            log.error("UI task pipeline DLQ publish failed. topic={}, stage={}, reasonCode={}, eventType={}, replyEventType={}, eqpId={}, traceId={}",
                    dlqMessage.topic(),
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
     * DLQ messageName 값을 보정합니다.
     */
    private static String normalizeMessageName(final String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "UNKNOWN_EVENT";
        }
        return eventType.trim();
    }

    /**
     * DLQ reasonCode 값을 보정합니다.
     */
    private static String normalizeReasonCode(final String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "UNKNOWN_REASON";
        }
        return reasonCode.trim();
    }

    /**
     * DLQ reasonMessage 값을 보정합니다.
     */
    private static String normalizeReasonMessage(final String reasonMessage) {
        if (reasonMessage == null || reasonMessage.isBlank()) {
            return "UI task pipeline failure";
        }
        return reasonMessage.trim();
    }

    /**
     * DLQ eqpId 값을 보정합니다.
     */
    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return UNKNOWN_EQP_ID;
        }
        return eqpId.trim();
    }

    /**
     * DLQ traceId 값을 보정합니다.
     */
    private static String normalizeTraceId(final String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return UNKNOWN_TRACE_ID;
        }
        return traceId.trim();
    }

    /**
     * payload 참조 문자열(payloadRef)을 생성합니다.
     */
    private static String buildPayloadRef(final String traceId, final String eventType) {
        return "payload://ui/" + normalizeTraceId(traceId) + "/event/" + normalizeMessageName(eventType);
    }

    /**
     * 빈 문자열이 아닌 태그만 map에 추가합니다.
     */
    private static void putIfHasText(final Map<String, String> tags, final String key, final String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        tags.put(key, value.trim());
    }
}

