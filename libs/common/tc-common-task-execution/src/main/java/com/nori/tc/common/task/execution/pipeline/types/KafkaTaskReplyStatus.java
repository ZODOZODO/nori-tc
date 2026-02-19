package com.nori.tc.common.task.execution.pipeline.types;

/**
 * Kafka 태스크 처리 결과 상태입니다.
 *
 * <p>최종 응답과 disposition 지표 집계의 공통 기준값으로 사용됩니다.</p>
 */
public enum KafkaTaskReplyStatus {

    /**
     * 처리가 정상 완료된 상태입니다.
     */
    PASS,

    /**
     * 처리 중 오류가 발생한 상태입니다.
     */
    FAIL
}
