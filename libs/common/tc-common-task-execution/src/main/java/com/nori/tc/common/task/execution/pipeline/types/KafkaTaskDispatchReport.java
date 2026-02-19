package com.nori.tc.common.task.execution.pipeline.types;

import java.util.Objects;

/**
 * 단일 Kafka 태스크 디스패치의 최종 결과 요약입니다.
 *
 * @param result 처리 결과
 * @param replyEventType 응답 발행에 사용한 이벤트 타입
 * @param duplicateSkipped traceId 중복으로 본처리를 생략했는지 여부
 */
public record KafkaTaskDispatchReport(
        KafkaTaskResult result,
        String replyEventType,
        boolean duplicateSkipped
) {

    /**
     * 필수 필드를 검증합니다.
     */
    public KafkaTaskDispatchReport {
        Objects.requireNonNull(result, "result is null");
        if (replyEventType == null || replyEventType.isBlank()) {
            throw new IllegalArgumentException("replyEventType is required");
        }
    }
}