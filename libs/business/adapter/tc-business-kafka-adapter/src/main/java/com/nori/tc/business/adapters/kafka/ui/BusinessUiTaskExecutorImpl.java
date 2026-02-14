package com.nori.tc.business.adapters.kafka.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.tc.business.core.ui.BusinessUiTaskExecutor;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.common.ui.task.pipeline.DefaultUiTaskPipeline;
import com.nori.tc.common.ui.task.pipeline.UiTaskDispatchReport;
import com.nori.tc.common.ui.task.pipeline.UiTaskReplyStatus;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@link BusinessUiTaskExecutor} 구현체입니다.
 *
 * <p>런타임 record payload(JSON)를 {@link KafkaUiTaskMessage}로 역직렬화한 뒤
 * 공통 UI task 파이프라인으로 위임합니다.</p>
 */
@Component
public class BusinessUiTaskExecutorImpl implements BusinessUiTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiTaskExecutorImpl.class);

    private final ObjectMapper objectMapper;
    private final DefaultUiTaskPipeline<KafkaUiTaskMessage> uiTaskPipeline;

    /**
     * UI task 실행기 의존성을 주입받습니다.
     */
    public BusinessUiTaskExecutorImpl(
            final ObjectMapper objectMapper,
            final DefaultUiTaskPipeline<KafkaUiTaskMessage> uiTaskPipeline
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is null");
        this.uiTaskPipeline = Objects.requireNonNull(uiTaskPipeline, "uiTaskPipeline is null");
    }

    @Override
    public UiTaskDispatchReport execute(final BusinessInboundRecord record) throws Exception {
        Objects.requireNonNull(record, "record is null");

        final String payload = normalize(record.payload());
        if (payload == null) {
            throw new IllegalArgumentException("UI record payload is required");
        }

        final KafkaUiTaskMessage request = objectMapper.readValue(payload, KafkaUiTaskMessage.class);
        final String requestEventType = request.metadata() == null ? null : request.metadata().eventType();

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
                    request.data() == null ? null : request.data().eqpId(),
                    request.metadata() == null ? null : request.metadata().traceId(),
                    record.partition(),
                    record.offset());
        }

        final UiTaskDispatchReport report = uiTaskPipeline.dispatch(request);
        if (report.result().status() == UiTaskReplyStatus.FAIL) {
            log.info("UI task finished with FAIL reply. eventType={}, eqpId={}, traceId={}, replyEventType={}, errorCode={}",
                    requestEventType,
                    request.data() == null ? null : request.data().eqpId(),
                    request.metadata() == null ? null : request.metadata().traceId(),
                    report.replyEventType(),
                    report.result().errorCode());
        } else if (log.isDebugEnabled()) {
            log.debug("UI task finished with PASS reply. eventType={}, eqpId={}, traceId={}, replyEventType={}",
                    requestEventType,
                    request.data() == null ? null : request.data().eqpId(),
                    request.metadata() == null ? null : request.metadata().traceId(),
                    report.replyEventType());
        }

        return report;
    }

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


