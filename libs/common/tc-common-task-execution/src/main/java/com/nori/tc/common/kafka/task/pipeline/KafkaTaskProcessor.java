package com.nori.tc.common.kafka.task.pipeline;

/**
 * 공통 Kafka task 이벤트 처리기 계약입니다.
 *
 * @param <T> 요청 타입
 */
@FunctionalInterface
public interface KafkaTaskProcessor<T> {

    /**
     * 단일 Kafka task 요청을 처리하고 결과를 반환합니다.
     *
     * @param request 요청 원문
     * @return 처리 결과
     * @throws Exception 처리 중 예외
     */
    KafkaTaskResult process(T request) throws Exception;
}


