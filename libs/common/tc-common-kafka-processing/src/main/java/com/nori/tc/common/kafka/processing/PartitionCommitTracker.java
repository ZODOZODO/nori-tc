package com.nori.tc.common.kafka.processing;

import org.apache.kafka.common.TopicPartition;

import java.util.NavigableSet;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.TreeSet;

/**
 * 단일 파티션의 완료 오프셋을 추적하여 연속 커밋 오프셋을 계산합니다.
 *
 * <p>동작 규칙:</p>
 * <p>1) 완료 오프셋은 순서 없이(out-of-order) 들어올 수 있습니다.</p>
 * <p>2) {@code nextCommitOffset}부터 연속 구간이 확인될 때만 커밋 지점을 전진합니다.</p>
 * <p>3) Kafka 커밋 값은 항상 마지막 완료 오프셋 + 1 규칙을 따릅니다.</p>
 */
public final class PartitionCommitTracker {

    private final TopicPartition topicPartition;
    private final NavigableSet<Long> completedOffsets = new TreeSet<>();

    private long nextCommitOffset;
    private long lastPreparedCommitOffset;

    /**
     * 파티션 트래커를 생성합니다.
     *
     * @param topicPartition 파티션 식별자
     * @param initialOffset 첫 미커밋 오프셋
     */
    public PartitionCommitTracker(final TopicPartition topicPartition, final long initialOffset) {
        this.topicPartition = Objects.requireNonNull(topicPartition, "topicPartition is null");
        if (initialOffset < 0L) {
            throw new IllegalArgumentException("initialOffset must be >= 0");
        }
        this.nextCommitOffset = initialOffset;
        this.lastPreparedCommitOffset = initialOffset;
    }

    /**
     * 완료된 레코드 오프셋을 기록합니다.
     *
     * <p>{@code nextCommitOffset}보다 작은 오프셋은 이미 처리된 중복/구버전 값으로 간주하여 무시합니다.</p>
     *
     * @param offset 완료 오프셋
     */
    public void recordCompletedOffset(final long offset) {
        if (offset < 0L) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (offset < nextCommitOffset) {
            return;
        }
        completedOffsets.add(offset);
    }

    /**
     * 연속 완료 구간이 전진했는지 확인하고 커밋 가능한 오프셋을 반환합니다.
     *
     * @return Kafka 커밋 값(옵션)
     */
    public OptionalLong pollCommittableOffset() {
        advanceContiguousRange();
        if (nextCommitOffset > lastPreparedCommitOffset) {
            lastPreparedCommitOffset = nextCommitOffset;
            return OptionalLong.of(nextCommitOffset);
        }
        return OptionalLong.empty();
    }

    /**
     * 파티션 식별자를 반환합니다.
     *
     * @return 토픽 파티션
     */
    public TopicPartition topicPartition() {
        return topicPartition;
    }

    /**
     * 다음으로 기대하는 미커밋 오프셋을 반환합니다.
     *
     * @return 다음 커밋 기준 오프셋
     */
    public long nextCommitOffset() {
        return nextCommitOffset;
    }

    /**
     * 연속성 공백(gap) 때문에 대기 중인 완료 오프셋 개수를 반환합니다.
     *
     * @return 버퍼링된 완료 오프셋 개수
     */
    public int pendingCompletionCount() {
        return completedOffsets.size();
    }

    private void advanceContiguousRange() {
        while (completedOffsets.remove(nextCommitOffset)) {
            nextCommitOffset++;
        }
    }
}
