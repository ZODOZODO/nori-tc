package com.nori.tc.common.task.execution.pipeline.port;

import com.nori.tc.common.task.execution.pipeline.constants.KafkaTaskPipelineStage;

/**
 * Kafka 태스크 처리 실패를 DLQ 저장소에 보고하는 인터페이스입니다.
 *
 * @param <T> 처리 요청 타입
 */
@FunctionalInterface
public interface KafkaTaskDlqReporter<T> {

    /**
     * 처리 실패 정보를 DLQ로 전달합니다.
     *
     * @param request 원본 요청
     * @param stage 실패가 발생한 파이프라인 단계
     * @param reasonKey 실패 사유 키
     * @param reasonMessage 상세 실패 메시지
     * @param replyEventType 응답 이벤트 타입
     */
    void report(
            T request,
            KafkaTaskPipelineStage stage,
            String reasonKey,
            String reasonMessage,
            String replyEventType
    );

    /**
     * DLQ 기능을 사용하지 않는 환경에서 사용할 no-op 구현을 반환합니다.
     *
     * @param <T> 처리 요청 타입
     * @return 아무 작업도 수행하지 않는 DLQ 보고기
     */
    static <T> KafkaTaskDlqReporter<T> noop() {
        return (request, stage, reasonKey, reasonMessage, replyEventType) -> {
            // intentionally no-op
        };
    }
}