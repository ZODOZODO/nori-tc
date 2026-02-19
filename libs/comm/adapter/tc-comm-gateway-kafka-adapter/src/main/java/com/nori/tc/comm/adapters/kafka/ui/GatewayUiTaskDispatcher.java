package com.nori.tc.comm.adapters.kafka.ui;

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
 * UI 이벤트 구독자와 공통 Task 파이프라인 사이를 연결하는 디스패처입니다.
 *
 * <p>역할:</p>
 * <p>1) 수신 메시지를 공통 파이프라인에 전달합니다.</p>
 * <p>2) 파이프라인 처리 결과를 ACCEPTED/REJECTED disposition 메트릭으로 집계합니다.</p>
 * <p>3) 운영 추적을 위한 info/debug 로그를 남깁니다.</p>
 */
@Component
public class GatewayUiTaskDispatcher implements KafkaMessageDispatcher<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskDispatcher.class);
    private static final String FLOW_UI_TASK = "UI_TASK";

    /** UI 공통 처리 파이프라인입니다. */
    private final DefaultKafkaTaskPipeline<KafkaUiTaskMessage> uiTaskPipeline;

    /** disposition(수락/거절) 집계를 담당하는 메트릭 포트입니다. */
    private final GatewayDispositionMetrics dispositionMetrics;

    /**
     * 디스패처 의존성을 초기화합니다.
     *
     * @param uiTaskPipeline UI Task 공통 파이프라인
     * @param dispositionMetrics disposition 메트릭 수집기
     */
    public GatewayUiTaskDispatcher(
            final DefaultKafkaTaskPipeline<KafkaUiTaskMessage> uiTaskPipeline,
            final GatewayDispositionMetrics dispositionMetrics
    ) {
        this.uiTaskPipeline = Objects.requireNonNull(uiTaskPipeline, "uiTaskPipeline is null");
        this.dispositionMetrics = Objects.requireNonNull(dispositionMetrics, "dispositionMetrics is null");
    }

    /**
     * UI Task 메시지를 파이프라인에 전달하고 결과를 기록합니다.
     *
     * @param message 수신한 UI Task 메시지
     */
    @Override
    public void dispatch(final KafkaUiTaskMessage message) {
        Objects.requireNonNull(message, "message is null");

        final String eventType = message.metadata() == null ? null : message.metadata().eventType();
        final String eqpId = message.data() == null ? null : message.data().eqpId();
        final String traceId = message.metadata() == null ? null : message.metadata().traceId();

        if (log.isDebugEnabled()) {
            log.debug(
                    "Gateway UI task dispatch started. eventType={}, eqpId={}, traceId={}",
                    eventType,
                    eqpId,
                    traceId
            );
        }

        final KafkaTaskDispatchReport report = uiTaskPipeline.dispatch(message);
        if (report.result().status() == KafkaTaskReplyStatus.FAIL) {
            dispositionMetrics.increment(FLOW_UI_TASK, GatewayDisposition.REJECTED);
            log.info(
                    "GATEWAY_UI_DISPOSITION. flow={}, disposition=REJECTED, reason=PIPELINE_FAIL, eventType={}, eqpId={}, traceId={}, replyEventType={}, errorCode={}",
                    FLOW_UI_TASK,
                    eventType,
                    eqpId,
                    traceId,
                    report.replyEventType(),
                    report.result().errorCode()
            );
            return;
        }

        dispositionMetrics.increment(FLOW_UI_TASK, GatewayDisposition.ACCEPTED);
        if (log.isDebugEnabled()) {
            log.debug(
                    "GATEWAY_UI_DISPOSITION. flow={}, disposition=ACCEPTED, reason=PIPELINE_PASS, eventType={}, eqpId={}, traceId={}, replyEventType={}, duplicateSkipped={}",
                    FLOW_UI_TASK,
                    eventType,
                    eqpId,
                    traceId,
                    report.replyEventType(),
                    report.duplicateSkipped()
            );
        }
    }
}
