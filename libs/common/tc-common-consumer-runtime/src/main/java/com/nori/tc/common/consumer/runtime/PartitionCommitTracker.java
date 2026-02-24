package com.nori.tc.common.consumer.runtime;

import java.util.NavigableSet;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.TreeSet;

/**
 * 단일 파티션의 처리 완료 오프셋을 추적하여 연속 커밋 가능한 오프셋을 계산합니다.
 *
 * <p>동작 규칙은 다음과 같습니다.</p>
 * <p>1) 완료 오프셋은 순서 없이(out-of-order) 들어올 수 있습니다.</p>
 * <p>2) {@code nextCommitOffset}부터 연속 구간이 확인될 때만 커밋 지점을 전진합니다.</p>
 * <p>3) 반환되는 커밋 오프셋은 "다음에 커밋할 오프셋" 규칙(마지막 완료 + 1)에 맞춥니다.</p>
 */
public final class PartitionCommitTracker {

    private final ConsumerPartition consumerPartition;
    private final NavigableSet<Long> completedOffsets = new TreeSet<>();

    private long nextCommitOffset;
    private long lastPreparedCommitOffset;

    /**
     * 파티션별 커밋 추적기를 생성합니다.
     *
     * @param consumerPartition 추적 대상 파티션 식별자
     * @param initialOffset     시작 오프셋(다음 커밋 기준 오프셋)
     */
    public PartitionCommitTracker(final ConsumerPartition consumerPartition, final long initialOffset) {
        this.consumerPartition = Objects.requireNonNull(consumerPartition, "consumerPartition is null");
        if (initialOffset < 0L) {
            throw new IllegalArgumentException("initialOffset must be >= 0");
        }
        this.nextCommitOffset = initialOffset;
        this.lastPreparedCommitOffset = initialOffset;
    }

    /**
     * 처리 완료된 오프셋을 기록합니다.
     *
     * <p>{@code nextCommitOffset}보다 작은 값은 이미 처리/커밋된 중복 값으로 보고 무시합니다.</p>
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
     * 연속 완료 구간을 전진시킨 뒤 커밋 가능한 다음 오프셋이 있으면 반환합니다.
     *
     * @return 커밋 가능한 다음 오프셋(없으면 empty)
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
     * 추적 대상 파티션 식별자를 반환합니다.
     *
     * @return 소비 파티션 식별자
     */
    public ConsumerPartition consumerPartition() {
        return consumerPartition;
    }

    /**
     * 다음으로 기대하는 커밋 기준 오프셋을 반환합니다.
     *
     * @return 다음 커밋 기준 오프셋
     */
    public long nextCommitOffset() {
        return nextCommitOffset;
    }

    /**
     * 연속성 부족으로 대기 중인 완료 오프셋 개수를 반환합니다.
     *
     * @return 대기 중인 완료 오프셋 개수
     */
    public int pendingCompletionCount() {
        return completedOffsets.size();
    }

    /**
     * 현재 {@code nextCommitOffset}부터 연속 완료 구간을 가능한 만큼 전진합니다.
     */
    private void advanceContiguousRange() {
        while (completedOffsets.remove(nextCommitOffset)) {
            nextCommitOffset++;
        }
    }
}
