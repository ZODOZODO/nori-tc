package com.nori.tc.messaging.kafka.runtime;

/**
 * Kafka Consumer 바인딩 방식입니다.
 *
 * <p>SUBSCRIBE는 그룹 기반 구독, ASSIGN은 고정 파티션 직접 할당을 의미합니다.</p>
 */
public enum KafkaConsumerBindingMode {
    /**
     * Kafka Consumer Group 기반 구독 모드입니다.
     */
    SUBSCRIBE,

    /**
     * 특정 토픽/파티션을 직접 할당하는 모드입니다.
     */
    ASSIGN
}
