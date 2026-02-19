package com.nori.tc.common.task.execution.pipeline.port;

import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;

/**
 * Kafka 태스크 처리 결과를 응답 토픽으로 발행하는 인터페이스입니다.
 *
 * @param <T> 처리 요청 타입
 */
@FunctionalInterface
public interface KafkaTaskReplyPublisher<T> {

    /**
     * 처리 결과를 응답 이벤트 형태로 발행합니다.
     *
     * @param request 원본 요청
     * @param replyEventType 응답 이벤트 타입
     * @param result 처리 결과
     * @throws Exception 발행 과정에서 발생한 예외
     */
    void publishResult(T request, String replyEventType, KafkaTaskResult result) throws Exception;
}