package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.logging.BusinessLogContext;
import com.nori.tc.business.core.ui.BusinessUiTaskExecutor;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.common.task.execution.pipeline.runtime.KafkaTaskExecutionPipeline;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskDispatchReport;
import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskReplyStatus;
import com.nori.tc.messaging.kafka.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@link BusinessUiTaskExecutor} 구현체입니다.
 *
 * <p>역할:</p>
 * <p>1) Business inbound record payload(JSON)를 {@link KafkaUiTaskMessage}로 역직렬화</p>
 * <p>2) 공통 UI Task 파이프라인으로 처리 위임</p>
 * <p>3) 처리 결과를 표준 리포트로 반환</p>
 */
@Component
public class BusinessUiTaskExecutorImpl implements BusinessUiTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiTaskExecutorImpl.class);

    private final ObjectMapper objectMapper;
    private final KafkaTaskExecutionPipeline<KafkaUiTaskMessage> uiTaskPipeline;

    /**
     * UI Task 실행에 필요한 의존성을 초기화합니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화 도구
     * @param uiTaskPipeline 공통 UI Task 실행 파이프라인
     */
    public BusinessUiTaskExecutorImpl(
            final ObjectMapper objectMapper,
            final KafkaTaskExecutionPipeline<KafkaUiTaskMessage> uiTaskPipeline
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
        this.uiTaskPipeline = Objects.requireNonNull(uiTaskPipeline, "uiTaskPipeline is null");
    }

    /**
     * 단일 UI inbound record를 파이프라인으로 전달해 처리합니다.
     *
     * @param record Business 런타임에서 전달한 inbound record
     * @return 파이프라인 처리 결과 리포트
     * @throws Exception 역직렬화/파이프라인 처리 실패
     */
    @Override
    public KafkaTaskDispatchReport execute(final BusinessInboundRecord record) throws Exception {
        Objects.requireNonNull(record, "record is null");

        final String payload = normalize(record.payload());
        if (payload == null) {
            throw new IllegalArgumentException("UI record payload is required");
        }

        final KafkaUiTaskMessage request = objectMapper.readValue(payload, KafkaUiTaskMessage.class);
        final String requestEventType = request.metadata() == null ? null : request.metadata().eventType();
        final String requestEqpId = normalize(request.data() == null ? null : request.data().eqpId());
        final String requestTraceId = normalize(request.metadata() == null ? null : request.metadata().traceId());

        /*
         * Business runtime worker 스레드에서 UI 파이프라인을 실행할 때
         * request 본문의 eqpId/traceId를 MDC에 주입해 설비 로그 분리를 보장합니다.
         */
        try (BusinessLogContext ignored = BusinessLogContext.withEqpAndTraceId(requestEqpId, requestTraceId)) {
            if (requestEventType != null && record.messageName() != null
                    && !requestEventType.equalsIgnoreCase(record.messageName())) {
                log.warn("UI messageName mismatch detected. recordMessageName={}, payloadEventType={}, eqpId={}, partition={}, offset={}",
                        record.messageName(),
                        requestEventType,
                        record.eqpId(),
                        record.partition(),
                        record.offset());
            }

            if (log.isDebugEnabled()) {
                log.debug("Dispatching UI task through pipeline. eventType={}, eqpId={}, traceId={}, partition={}, offset={}",
                        requestEventType,
                        requestEqpId,
                        requestTraceId,
                        record.partition(),
                        record.offset());
            }

            final KafkaTaskDispatchReport report = uiTaskPipeline.dispatch(request);
            if (report.result().status() == KafkaTaskReplyStatus.FAIL) {
                log.info("UI task finished with FAIL reply. eventType={}, eqpId={}, traceId={}, replyEventType={}, errorCode={}",
                        requestEventType,
                        requestEqpId,
                        requestTraceId,
                        report.replyEventType(),
                        report.result().errorCode());
            } else if (log.isDebugEnabled()) {
                log.debug("UI task finished with PASS reply. eventType={}, eqpId={}, traceId={}, replyEventType={}",
                        requestEventType,
                        requestEqpId,
                        requestTraceId,
                        report.replyEventType());
            }

            return report;
        }
    }

    /**
     * 문자열을 null-safe하게 정규화합니다.
     *
     * @param value 원본 문자열
     * @return trim 결과(빈 문자열이면 null)
     */
    private static String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}




