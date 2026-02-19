package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.common.kafka.task.pipeline.DefaultKafkaTaskPipeline;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskDispatchReport;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskReplyStatus;
import com.nori.tc.comm.gateway.metrics.GatewayDisposition;
import com.nori.tc.comm.gateway.metrics.GatewayDispositionMetrics;
import com.nori.tc.messaging.kafka.starter.contract.KafkaMessageDispatcher;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Gateway UI task 디스패처입니다.
 *
 * <p>기존 gateway 전용 분기/재시도/중복/REP/DLQ 로직을
 * 공통 {@link DefaultKafkaTaskPipeline}에 위임합니다.</p>
 */
@Component
public class GatewayUiTaskDispatcher implements KafkaMessageDispatcher<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskDispatcher.class);
    private static final String FLOW_UI_TASK = "UI_TASK";

    private final DefaultKafkaTaskPipeline<KafkaUiTaskMessage> uiTaskPipeline;
    private final GatewayDispositionMetrics dispositionMetrics;

    /**
     * 디스패처 의존성을 초기화합니다.
     *
     * @param uiTaskPipeline 공통 UI task 파이프라인
     * @param dispositionMetrics disposition 집계기
     */
    public GatewayUiTaskDispatcher(
            final DefaultKafkaTaskPipeline<KafkaUiTaskMessage> uiTaskPipeline,
            final GatewayDispositionMetrics dispositionMetrics
    ) {
        this.uiTaskPipeline = Objects.requireNonNull(uiTaskPipeline, "uiTaskPipeline is null");
        this.dispositionMetrics = Objects.requireNonNull(dispositionMetrics, "dispositionMetrics is null");
    }

    /**
     * 단일 UI task를 공통 파이프라인으로 처리합니다.
     *
     * @param message UI task 메시지
     */
    @Override
    public void dispatch(final KafkaUiTaskMessage message) {
        Objects.requireNonNull(message, "message is null");

        final String eventType = message.metadata() == null ? null : message.metadata().eventType();
        final String eqpId = message.data() == null ? null : message.data().eqpId();
        final String traceId = message.metadata() == null ? null : message.metadata().traceId();

        if (log.isDebugEnabled()) {
            log.debug("Gateway UI task dispatch start. eventType={}, eqpId={}, traceId={}",
                    eventType,
                    eqpId,
                    traceId);
        }

        final KafkaTaskDispatchReport report = uiTaskPipeline.dispatch(message);
        if (report.result().status() == KafkaTaskReplyStatus.FAIL) {
            dispositionMetrics.increment(FLOW_UI_TASK, GatewayDisposition.REJECTED);
            log.info("GATEWAY_UI_DISPOSITION. flow={}, disposition=REJECTED, reason=PIPELINE_FAIL, eventType={}, eqpId={}, traceId={}, replyEventType={}, errorCode={}",
                    FLOW_UI_TASK,
                    eventType,
                    eqpId,
                    traceId,
                    report.replyEventType(),
                    report.result().errorCode());
            return;
        }

        dispositionMetrics.increment(FLOW_UI_TASK, GatewayDisposition.ACCEPTED);
        if (log.isDebugEnabled()) {
            log.debug("GATEWAY_UI_DISPOSITION. flow={}, disposition=ACCEPTED, reason=PIPELINE_PASS, eventType={}, eqpId={}, traceId={}, replyEventType={}, duplicateSkipped={}",
                    FLOW_UI_TASK,
                    eventType,
                    eqpId,
                    traceId,
                    report.replyEventType(),
                    report.duplicateSkipped());
        }
    }
}

