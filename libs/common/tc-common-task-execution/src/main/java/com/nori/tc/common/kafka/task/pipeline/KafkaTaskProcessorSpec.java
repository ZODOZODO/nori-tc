package com.nori.tc.common.kafka.task.pipeline;

import java.util.Objects;

/**
 * 단일 Kafka task 이벤트 타입에 대한 처리기 사양입니다.
 *
 * @param eventType 이벤트 타입 키
 * @param replyEventType 응답 발행 시 사용할 이벤트 타입
 * @param processor 실제 처리기 구현체
 * @param <T> 요청 타입
 */
public record KafkaTaskProcessorSpec<T>(
        String eventType,
        String replyEventType,
        KafkaTaskProcessor<T> processor
) {

    /**
     * 처리기 사양의 기본 유효성을 검증합니다.
     */
    public KafkaTaskProcessorSpec {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (replyEventType == null || replyEventType.isBlank()) {
            throw new IllegalArgumentException("replyEventType is required");
        }
        Objects.requireNonNull(processor, "processor is null");
    }
}


