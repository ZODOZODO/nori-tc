package com.nori.tc.common.kafka.task.pipeline;

/**
 * 기본 Kafka task 파이프라인에서 공통으로 사용하는 오류 코드 상수입니다.
 */
public final class KafkaTaskPipelineErrorCode {

    /**
     * eventType 누락/형식 오류
     */
    public static final String INVALID_EVENT_TYPE = "INVALID_EVENT_TYPE";

    /**
     * eventType에 해당하는 처리기 미등록
     */
    public static final String HANDLER_NOT_FOUND = "HANDLER_NOT_FOUND";

    /**
     * 처리기 실행이 재시도 후에도 실패
     */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /**
     * 응답 발행이 재시도 후에도 실패
     */
    public static final String REPLY_PUBLISH_FAILED = "REPLY_PUBLISH_FAILED";

    private KafkaTaskPipelineErrorCode() {
    }
}


