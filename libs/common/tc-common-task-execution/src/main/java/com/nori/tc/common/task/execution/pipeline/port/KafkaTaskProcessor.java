package com.nori.tc.common.task.execution.pipeline.port;

import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskResult;

/**
 * Kafka 태스크 본처리기를 정의하는 인터페이스입니다.
 *
 * @param <T> 처리 요청 타입
 */
@FunctionalInterface
public interface KafkaTaskProcessor<T> {

    /**
     * 전달된 요청을 실제 업무 로직으로 처리합니다.
     *
     * @param request 처리할 요청 데이터
     * @return 처리 결과(성공/실패 상태 포함)
     * @throws Exception 업무 처리 도중 발생한 예외
     */
    KafkaTaskResult process(T request) throws Exception;
}