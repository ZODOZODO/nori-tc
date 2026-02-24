package com.nori.tc.common.consumer.runtime;

/**
 * 특정 소비 시스템의 파티션 식별자(토픽 + 파티션 번호)를 표현하는 중립 값 객체입니다.
 *
 * <p>Kafka의 {@code TopicPartition}와 유사한 의미를 가지지만, Kafka SDK 타입을 직접 노출하지 않기 위해
 * 코어/공용 모듈에서는 이 타입을 사용합니다.</p>
 *
 * @param topic     논리 토픽(또는 스트림) 이름
 * @param partition 파티션 번호
 */
public record ConsumerPartition(
        String topic,
        int partition
) {

    /**
     * 값 객체 생성 시 최소 유효성 검사를 수행합니다.
     */
    public ConsumerPartition {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        if (partition < 0) {
            throw new IllegalArgumentException("partition must be >= 0");
        }
    }
}
