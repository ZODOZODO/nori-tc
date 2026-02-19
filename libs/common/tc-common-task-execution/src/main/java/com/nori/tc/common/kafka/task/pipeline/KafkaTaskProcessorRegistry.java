package com.nori.tc.common.kafka.task.pipeline;

import java.util.Optional;

/**
 * eventType 기반 Kafka task 처리기 조회 레지스트리 계약입니다.
 *
 * @param <T> 요청 타입
 */
@FunctionalInterface
public interface KafkaTaskProcessorRegistry<T> {

    /**
     * eventType에 해당하는 처리기 사양을 조회합니다.
     *
     * @param eventType 정규화된 이벤트 타입
     * @return 처리기 사양(없으면 empty)
     */
    Optional<KafkaTaskProcessorSpec<T>> find(String eventType);
}


