package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.common.ui.task.pipeline.UiTaskReplyPublisher;
import com.nori.tc.common.ui.task.pipeline.UiTaskReplyStatus;
import com.nori.tc.common.ui.task.pipeline.UiTaskResult;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskReplyStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 공통 UI 파이프라인 응답 발행 계약을 gateway Kafka 발행기로 연결하는 어댑터입니다.
 */
@Component
public class GatewayUiTaskReplyPublisherAdapter implements UiTaskReplyPublisher<KafkaUiTaskMessage> {

    private final KafkaUiReplyPublisher delegate;

    /**
     * 어댑터를 초기화합니다.
     *
     * @param delegate gateway Kafka REP 발행기
     */
    public GatewayUiTaskReplyPublisherAdapter(final KafkaUiReplyPublisher delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is null");
    }

    /**
     * 공통 결과 모델을 gateway 결과 모델로 변환해 REP를 발행합니다.
     *
     * @param request 원본 요청
     * @param replyEventType 응답 이벤트 타입
     * @param result 공통 처리 결과
     * @throws Exception 발행 실패 예외
     */
    @Override
    public void publishResult(
            final KafkaUiTaskMessage request,
            final String replyEventType,
            final UiTaskResult result
    ) throws Exception {
        Objects.requireNonNull(result, "result is null");
        delegate.publishResult(
                request,
                replyEventType,
                new GatewayUiTaskResult(
                        result.status() == UiTaskReplyStatus.PASS
                                ? KafkaUiTaskReplyStatus.PASS
                                : KafkaUiTaskReplyStatus.FAIL,
                        result.errorCode(),
                        result.errorMessage()
                )
        );
    }
}
