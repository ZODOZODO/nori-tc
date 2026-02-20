package com.nori.tc.common.task.execution.pipeline.types;

/**
 * Kafka 태스크 응답 발행 시점을 정의합니다.
 *
 * <p>운영 의도:</p>
 * <p>1) 일반 요청은 처리 결과를 즉시 응답합니다.</p>
 * <p>2) lifecycle START/END처럼 "실제 완료 시점" 응답이 필요한 경우
 * PASS 응답을 지연하고, 별도 완료 이벤트에서 최종 응답을 발행합니다.</p>
 */
public enum KafkaTaskReplyPublishMode {
    /**
     * 처리 결과(PASS/FAIL)를 파이프라인에서 즉시 응답 발행합니다.
     */
    IMMEDIATE,

    /**
     * FAIL은 즉시 응답하고, PASS는 즉시 응답하지 않습니다.
     *
     * <p>PASS 응답은 외부 비동기 완료 이벤트(예: lifecycle applied)에서 발행해야 합니다.</p>
     */
    DEFERRED_ON_PASS
}

