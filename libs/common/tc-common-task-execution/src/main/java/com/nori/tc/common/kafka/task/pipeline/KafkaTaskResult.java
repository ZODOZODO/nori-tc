package com.nori.tc.common.kafka.task.pipeline;

/**
 * Kafka task 처리 결과 모델입니다.
 *
 * @param status PASS/FAIL 상태
 * @param errorCode 실패 시 오류 코드
 * @param errorMessage 실패 시 오류 메시지
 */
public record KafkaTaskResult(
        KafkaTaskReplyStatus status,
        String errorCode,
        String errorMessage
) {

    /**
     * 성공(PASS) 결과를 생성합니다.
     *
     * @return 성공 결과
     */
    public static KafkaTaskResult pass() {
        return new KafkaTaskResult(KafkaTaskReplyStatus.PASS, null, null);
    }

    /**
     * 실패(FAIL) 결과를 생성합니다.
     *
     * @param errorCode 실패 오류 코드
     * @param errorMessage 실패 메시지
     * @return 실패 결과
     */
    public static KafkaTaskResult fail(final String errorCode, final String errorMessage) {
        return new KafkaTaskResult(KafkaTaskReplyStatus.FAIL, errorCode, errorMessage);
    }
}


