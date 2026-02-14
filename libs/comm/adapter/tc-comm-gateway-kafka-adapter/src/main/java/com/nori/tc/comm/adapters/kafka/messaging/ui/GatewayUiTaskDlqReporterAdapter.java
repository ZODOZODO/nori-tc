package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.domain.dlq.DlqMessage;
import com.nori.tc.comm.gateway.domain.dlq.DlqReasonCode;
import com.nori.tc.common.ui.task.pipeline.UiTaskDlqReporter;
import com.nori.tc.common.ui.task.pipeline.UiTaskPipelineReasonCode;
import com.nori.tc.common.ui.task.pipeline.UiTaskPipelineStage;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 공통 UI 파이프라인 DLQ 보고 계약을 gateway DLQ 발행기로 연결하는 어댑터입니다.
 */
@Component
public class GatewayUiTaskDlqReporterAdapter implements UiTaskDlqReporter<KafkaUiTaskMessage> {

    private final GatewayUiTaskDlqPublisher delegate;

    /**
     * 어댑터를 초기화합니다.
     *
     * @param delegate gateway DLQ 발행기
     */
    public GatewayUiTaskDlqReporterAdapter(final GatewayUiTaskDlqPublisher delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is null");
    }

    /**
     * 공통 파이프라인의 DLQ 보고 이벤트를 gateway DLQ 메시지로 발행합니다.
     *
     * @param request 원본 요청
     * @param stage 실패 단계
     * @param reasonCode 실패 사유 코드
     * @param reasonMessage 실패 메시지
     * @param replyEventType 응답 이벤트 타입
     */
    @Override
    public void report(
            final KafkaUiTaskMessage request,
            final UiTaskPipelineStage stage,
            final String reasonCode,
            final String reasonMessage,
            final String replyEventType
    ) {
        delegate.publish(
                request,
                toStage(stage),
                toDlqReasonCode(reasonCode),
                reasonMessage,
                replyEventType
        );
    }

    /**
     * 공통 stage enum을 gateway DLQ stage 문자열로 변환합니다.
     *
     * @param stage 공통 stage
     * @return gateway DLQ stage 문자열
     */
    private static String toStage(final UiTaskPipelineStage stage) {
        if (stage == UiTaskPipelineStage.PUBLISH) {
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
        if (UiTaskPipelineReasonCode.PUBLISH_FAILED.equals(reasonCode)) {
            return DlqReasonCode.PUBLISH_FAILED;
        }
        if (UiTaskPipelineReasonCode.PROCESS_FAILED.equals(reasonCode)) {
            return DlqReasonCode.ROUTING_FAILED;
        }
        return DlqReasonCode.ROUTING_FAILED;
    }
}
