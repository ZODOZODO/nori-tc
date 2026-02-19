package com.nori.tc.common.kafka.task.pipeline;

/**
 * 실패가 발생한 Kafka task 파이프라인 단계를 나타냅니다.
 */
public enum KafkaTaskPipelineStage {
    ROUTING,
    PROCESS,
    PUBLISH
}


