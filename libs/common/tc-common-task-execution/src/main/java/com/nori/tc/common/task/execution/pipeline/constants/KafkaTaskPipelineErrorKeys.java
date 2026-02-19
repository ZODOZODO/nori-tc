package com.nori.tc.common.task.execution.pipeline.constants;

/**
 * Kafka 태스크 파이프라인의 실패 코드 키 상수입니다.
 *
 * <p>응답/로그/지표에서 동일한 키를 사용해 장애 원인을 분류할 때 사용합니다.</p>
 */
public final class KafkaTaskPipelineErrorKeys {

    /**
     * eventType 값이 없거나 형식이 잘못된 경우 사용합니다.
     */
    public static final String INVALID_EVENT_TYPE = "INVALID_EVENT_TYPE";

    /**
     * eventType에 매핑된 처리기가 존재하지 않을 때 사용합니다.
     */
    public static final String HANDLER_NOT_FOUND = "HANDLER_NOT_FOUND";

    /**
     * 처리기 내부 예외 등 일반 처리 실패에 사용합니다.
     */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /**
     * 처리 결과 응답을 발행하지 못한 경우 사용합니다.
     */
    public static final String REPLY_PUBLISH_FAILED = "REPLY_PUBLISH_FAILED";

    /**
     * 유틸리티 클래스의 인스턴스 생성을 방지합니다.
     */
    private KafkaTaskPipelineErrorKeys() {
    }
}