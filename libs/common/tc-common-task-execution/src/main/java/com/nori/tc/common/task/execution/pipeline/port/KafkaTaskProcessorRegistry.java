package com.nori.tc.common.task.execution.pipeline.port;

import com.nori.tc.common.task.execution.pipeline.types.KafkaTaskProcessorSpec;

import java.util.Optional;

/**
 * 이벤트 타입에 맞는 처리 사양을 조회하는 레지스트리 인터페이스입니다.
 *
 * @param <T> 처리 요청 타입
 */
@FunctionalInterface
public interface KafkaTaskProcessorRegistry<T> {

    /**
     * 이벤트 타입으로 처리기 사양을 조회합니다.
     *
     * @param eventType 조회할 이벤트 타입
     * @return 처리기 사양(없으면 빈 Optional)
     */
    Optional<KafkaTaskProcessorSpec<T>> find(String eventType);
}