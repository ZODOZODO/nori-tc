package com.nori.tc.common.task.execution.pipeline.types;

import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskProcessor;

import java.util.Objects;

/**
 * 이벤트 타입별 처리 규칙(처리기, 응답 이벤트 타입)을 정의하는 모델입니다.
 *
 * @param eventType 입력 이벤트 타입
 * @param replyEventType 결과 발행 시 사용할 응답 이벤트 타입
 * @param processor 실제 처리기
 * @param <T> 처리 요청 타입
 */
public record KafkaTaskProcessorSpec<T>(
        String eventType,
        String replyEventType,
        KafkaTaskProcessor<T> processor
) {

    /**
     * 필수 필드 유효성을 검증합니다.
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