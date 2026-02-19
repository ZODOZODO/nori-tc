package com.nori.tc.common.kafka.task.pipeline;

/**
 * DLQ 보고를 수행하지 않는 no-op 구현입니다.
 *
 * @param <T> 요청 타입
 */
public final class NoopKafkaTaskDlqReporter<T> implements KafkaTaskDlqReporter<T> {

    /**
     * DLQ 보고 요청을 무시합니다.
     *
     * @param request 원본 요청
     * @param stage 실패 단계
     * @param reasonCode 실패 코드
     * @param reasonMessage 실패 상세 메시지
     * @param replyEventType 응답 이벤트 타입
     */
    @Override
    public void report(
            final T request,
            final KafkaTaskPipelineStage stage,
            final String reasonCode,
            final String reasonMessage,
            final String replyEventType
    ) {
        // intentionally no-op
    }
}


