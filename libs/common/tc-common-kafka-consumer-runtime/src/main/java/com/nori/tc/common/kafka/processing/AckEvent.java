package com.nori.tc.common.kafka.processing;

import org.apache.kafka.common.TopicPartition;

import java.util.Objects;

/**
 * worker 실행 결과를 consumer 측으로 전달하는 ack 이벤트입니다.
 *
 * <p>정확한 Kafka 위치(topic/partition/offset)와 상태를 함께 전달하여
 * consumer 스레드가 안전하게 연속 커밋 구간을 계산할 수 있도록 합니다.</p>
 *
 * @param topic 토픽 이름
 * @param partition 파티션 번호
 * @param offset 레코드 오프셋
 * @param status 처리 상태
 * @param occurredAtEpochMs ack 발생 시각(epoch millis)
 */
public record AckEvent(
        String topic,
        int partition,
        long offset,
        AckStatus status,
        long occurredAtEpochMs
) {

    /**
     * ack 이벤트의 최소 불변 조건을 검증합니다.
     */
    public AckEvent {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        if (partition < 0) {
            throw new IllegalArgumentException("partition must be >= 0");
        }
        if (offset < 0L) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        Objects.requireNonNull(status, "status is null");
        if (occurredAtEpochMs < 0L) {
            throw new IllegalArgumentException("occurredAtEpochMs must be >= 0");
        }
    }

    /**
     * 현재 ack 이벤트의 토픽/파티션 식별자를 반환합니다.
     *
     * @return 토픽 파티션
     */
    public TopicPartition topicPartition() {
        return new TopicPartition(topic, partition);
    }

    /**
     * 현재 ack가 커밋 오프셋 전진 대상인지 반환합니다.
     *
     * @return 커밋 가능 여부
     */
    public boolean isCommitEligible() {
        return status.isCommitEligible();
    }
}
