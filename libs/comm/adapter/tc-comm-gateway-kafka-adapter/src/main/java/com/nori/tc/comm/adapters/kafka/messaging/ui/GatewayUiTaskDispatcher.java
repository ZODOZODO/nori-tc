package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.common.ui.task.pipeline.DefaultUiTaskPipeline;
import com.nori.tc.common.ui.task.pipeline.UiTaskDispatchReport;
import com.nori.tc.common.ui.task.pipeline.UiTaskReplyStatus;
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
 * 공통 {@link DefaultUiTaskPipeline}에 위임합니다.</p>
 */
@Component
public class GatewayUiTaskDispatcher implements KafkaMessageDispatcher<KafkaUiTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(GatewayUiTaskDispatcher.class);

    private final DefaultUiTaskPipeline<KafkaUiTaskMessage> uiTaskPipeline;

    /**
     * 디스패처 의존성을 초기화합니다.
     *
     * @param uiTaskPipeline 공통 UI task 파이프라인
     */
    public GatewayUiTaskDispatcher(final DefaultUiTaskPipeline<KafkaUiTaskMessage> uiTaskPipeline) {
        this.uiTaskPipeline = Objects.requireNonNull(uiTaskPipeline, "uiTaskPipeline is null");
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

        final UiTaskDispatchReport report = uiTaskPipeline.dispatch(message);
        if (report.result().status() == UiTaskReplyStatus.FAIL) {
            log.info("Gateway UI task finished with FAIL. eventType={}, eqpId={}, traceId={}, replyEventType={}, errorCode={}",
                    eventType,
                    eqpId,
                    traceId,
                    report.replyEventType(),
                    report.result().errorCode());
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Gateway UI task finished with PASS. eventType={}, eqpId={}, traceId={}, replyEventType={}, duplicateSkipped={}",
                    eventType,
                    eqpId,
                    traceId,
                    report.replyEventType(),
                    report.duplicateSkipped());
        }
    }
}
