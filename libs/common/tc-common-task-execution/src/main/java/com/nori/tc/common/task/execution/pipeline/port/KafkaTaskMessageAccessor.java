package com.nori.tc.common.task.execution.pipeline.port;

/**
 * 요청 메시지에서 파이프라인 공통 필드를 추출하는 접근자 인터페이스입니다.
 *
 * @param <T> 처리 요청 타입
 */
public interface KafkaTaskMessageAccessor<T> {

    /**
     * 요청의 이벤트 타입을 반환합니다.
     *
     * @param request 원본 요청
     * @return 이벤트 타입
     */
    String eventType(T request);

    /**
     * 요청의 추적 식별자(traceId)를 반환합니다.
     *
     * @param request 원본 요청
     * @return traceId
     */
    String traceId(T request);

    /**
     * 요청의 장비 식별자(eqpId)를 반환합니다.
     *
     * @param request 원본 요청
     * @return eqpId
     */
    String eqpId(T request);
}