package com.nori.tc.common.task.execution.pipeline.exception;

/**
 * Kafka 태스크 응답 발행 재시도가 모두 소진된 경우 발생하는 예외입니다.
 *
 * <p>파이프라인은 이 예외를 상위 계층으로 전달해,
 * 호출자가 커밋 보류/재처리/DLQ 전략을 선택할 수 있게 합니다.</p>
 */
public class KafkaTaskReplyPublishException extends RuntimeException {

    /**
     * 예외 메시지와 원인 예외를 함께 초기화합니다.
     *
     * @param message 예외 메시지
     * @param cause 원인 예외
     */
    public KafkaTaskReplyPublishException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
