package com.nori.tc.common.task.execution.pipeline.types;

import java.util.Objects;

/**
 * Kafka 태스크 처리 결과 본문 모델입니다.
 *
 * @param status 처리 상태
 * @param errorCode 실패 코드(성공 시 null)
 * @param errorMessage 실패 메시지(성공 시 null)
 */
public record KafkaTaskResult(
        KafkaTaskReplyStatus status,
        String errorCode,
        String errorMessage
) {

    /**
     * 필수 필드 유효성을 검증합니다.
     */
    public KafkaTaskResult {
        Objects.requireNonNull(status, "status is null");
    }

    /**
     * 성공 결과를 생성합니다.
     *
     * @return PASS 결과
     */
    public static KafkaTaskResult pass() {
        return new KafkaTaskResult(KafkaTaskReplyStatus.PASS, null, null);
    }

    /**
     * 실패 결과를 생성합니다.
     *
     * @param errorCode 실패 코드
     * @param errorMessage 실패 메시지
     * @return FAIL 결과
     */
    public static KafkaTaskResult fail(final String errorCode, final String errorMessage) {
        return new KafkaTaskResult(KafkaTaskReplyStatus.FAIL, errorCode, errorMessage);
    }
}