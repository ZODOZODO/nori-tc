package com.nori.tc.common.kafka.task.pipeline;

/**
 * 요청 모델에서 파이프라인 필수 필드를 추출하는 접근자 계약입니다.
 *
 * @param <T> 요청 타입
 */
public interface KafkaTaskMessageAccessor<T> {

    /**
     * 요청에서 이벤트 타입을 반환합니다.
     *
     * @param request 요청 원문
     * @return 이벤트 타입
     */
    String eventType(T request);

    /**
     * 요청에서 traceId를 반환합니다.
     *
     * @param request 요청 원문
     * @return traceId
     */
    String traceId(T request);

    /**
     * 요청에서 설비 ID(eqpId)를 반환합니다.
     *
     * @param request 요청 원문
     * @return 설비 ID
     */
    String eqpId(T request);
}


