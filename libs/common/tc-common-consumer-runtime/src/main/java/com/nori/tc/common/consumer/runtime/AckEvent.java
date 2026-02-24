package com.nori.tc.common.consumer.runtime;

import java.util.Objects;

/**
 * 작업 처리 결과를 소비 루프(커밋 담당 영역)로 전달하는 ACK 이벤트입니다.
 *
 * <p>토픽/파티션/오프셋 정보를 중립 타입으로 유지하여, 코어/공용 계층이 특정 브로커 SDK에 직접
 * 의존하지 않도록 설계합니다.</p>
 *
 * @param topic            토픽 이름
 * @param partition        파티션 번호
 * @param offset           처리 완료/실패 대상 오프셋
 * @param status           처리 상태
 * @param occurredAtEpochMs ACK 발생 시각(epoch millis)
 */
public record AckEvent(
        String topic,
        int partition,
        long offset,
        AckStatus status,
        long occurredAtEpochMs
) {

    /**
     * ACK 이벤트 생성 시 최소 유효성 검사를 수행합니다.
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
     * 현재 ACK 이벤트의 파티션 식별자를 중립 값 객체로 반환합니다.
     *
     * @return 소비 파티션 식별자
     */
    public ConsumerPartition consumerPartition() {
        return new ConsumerPartition(topic, partition);
    }

    /**
     * 현재 ACK가 커밋 전진 대상인지 반환합니다.
     *
     * @return 커밋 가능 여부
     */
    public boolean isCommitEligible() {
        return status.isCommitEligible();
    }
}
