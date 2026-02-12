package com.nori.tc.messaging.kafka.starter.runtime;

/**
 * Kafka Consumer 바인딩 모드입니다.
 *
 * <p>SUBSCRIBE: 그룹 리밸런싱 기반 구독
 * ASSIGN: 고정 파티션 수동 할당</p>
 */
public enum KafkaConsumerBindingMode {
    SUBSCRIBE,
    ASSIGN
}
