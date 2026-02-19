package com.nori.tc.common.kafka.task.pipeline;

/**
 * 설정된 재시도 횟수 이후에도 응답 발행이 실패했을 때 발생하는 예외입니다.
 */
public class KafkaTaskReplyPublishException extends RuntimeException {

    /**
     * 응답 발행 예외를 생성합니다.
     *
     * @param message 예외 메시지
     * @param cause 원인 예외
     */
    public KafkaTaskReplyPublishException(final String message, final Throwable cause) {
        super(message, cause);
    }
}


