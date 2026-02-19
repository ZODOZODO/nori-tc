package com.nori.tc.common.task.execution.pipeline.constants;

/**
 * Kafka 태스크 파이프라인의 처리 단계를 나타냅니다.
 *
 * <p>한 요청은 일반적으로 `ROUTING -> PROCESS -> PUBLISH` 순서로 진행되며,
 * 실패 시점의 stage 값이 DLQ/로그/메트릭에 기록됩니다.</p>
 */
public enum KafkaTaskPipelineStage {

    /**
     * 이벤트 타입을 기준으로 처리기를 찾는 라우팅 단계입니다.
     */
    ROUTING,

    /**
     * 실제 업무 처리기를 실행하는 단계입니다.
     */
    PROCESS,

    /**
     * 처리 결과를 응답 토픽으로 발행하는 단계입니다.
     */
    PUBLISH
}
