package com.nori.tc.common.consumer.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 파티션별 {@link PartitionCommitTracker}를 집계하여 커밋 가능한 오프셋 계획을 계산하는 코디네이터입니다.
 *
 * <p>이 클래스는 Kafka SDK 타입을 직접 노출하지 않으며, 중립 파티션 키와 오프셋(long)만 반환합니다.
 * Kafka/Spring Kafka 연동 계층에서는 이 결과를 브로커 전용 타입으로 변환하여 사용합니다.</p>
 */
public final class PartitionCommitCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PartitionCommitCoordinator.class);

    private final Map<ConsumerPartition, PartitionCommitTracker> trackers = new ConcurrentHashMap<>();

    /**
     * 파티션 추적기를 등록(또는 교체)합니다.
     *
     * @param consumerPartition 추적 대상 파티션
     * @param initialOffset     시작 오프셋
     */
    public void registerPartition(final ConsumerPartition consumerPartition, final long initialOffset) {
        Objects.requireNonNull(consumerPartition, "consumerPartition is null");
        trackers.put(consumerPartition, new PartitionCommitTracker(consumerPartition, initialOffset));
        if (log.isDebugEnabled()) {
            log.debug("커밋 추적기를 등록(교체 가능)했습니다. partition={}, initialOffset={}",
                    consumerPartition, initialOffset);
        }
    }

    /**
     * 토픽/파티션 값을 받아 추적기를 등록(또는 교체)합니다.
     *
     * @param topic         토픽 이름
     * @param partition     파티션 번호
     * @param initialOffset 시작 오프셋
     */
    public void registerPartition(final String topic, final int partition, final long initialOffset) {
        registerPartition(new ConsumerPartition(topic, partition), initialOffset);
    }

    /**
     * 아직 등록되지 않은 경우에만 파티션 추적기를 등록합니다.
     *
     * @param consumerPartition 추적 대상 파티션
     * @param initialOffset     시작 오프셋
     */
    public void registerPartitionIfAbsent(final ConsumerPartition consumerPartition, final long initialOffset) {
        Objects.requireNonNull(consumerPartition, "consumerPartition is null");
        final PartitionCommitTracker previous = trackers.putIfAbsent(
                consumerPartition,
                new PartitionCommitTracker(consumerPartition, initialOffset)
        );
        if (previous == null && log.isDebugEnabled()) {
            log.debug("커밋 추적기를 신규 등록했습니다. partition={}, initialOffset={}",
                    consumerPartition, initialOffset);
        }
    }

    /**
     * 토픽/파티션 값을 받아 아직 등록되지 않은 경우에만 추적기를 등록합니다.
     *
     * @param topic         토픽 이름
     * @param partition     파티션 번호
     * @param initialOffset 시작 오프셋
     */
    public void registerPartitionIfAbsent(final String topic, final int partition, final long initialOffset) {
        registerPartitionIfAbsent(new ConsumerPartition(topic, partition), initialOffset);
    }

    /**
     * 파티션 추적기를 제거합니다.
     *
     * @param consumerPartition 제거 대상 파티션
     */
    public void unregisterPartition(final ConsumerPartition consumerPartition) {
        if (consumerPartition == null) {
            return;
        }

        final PartitionCommitTracker removed = trackers.remove(consumerPartition);
        if (removed != null && log.isDebugEnabled()) {
            log.debug("커밋 추적기를 제거했습니다. partition={}", consumerPartition);
        }
    }

    /**
     * 토픽/파티션 값을 받아 파티션 추적기를 제거합니다.
     *
     * @param topic     토픽 이름
     * @param partition 파티션 번호
     */
    public void unregisterPartition(final String topic, final int partition) {
        unregisterPartition(new ConsumerPartition(topic, partition));
    }

    /**
     * ACK 이벤트를 해당 파티션 추적기에 반영합니다.
     *
     * @param event 작업 처리 결과 ACK 이벤트
     */
    public void applyAck(final AckEvent event) {
        Objects.requireNonNull(event, "event is null");

        if (!event.isCommitEligible()) {
            if (log.isDebugEnabled()) {
                log.debug("커밋 비대상 ACK를 무시합니다. topic={}, partition={}, offset={}, status={}",
                        event.topic(), event.partition(), event.offset(), event.status());
            }
            return;
        }

        final ConsumerPartition key = event.consumerPartition();
        final PartitionCommitTracker tracker = trackers.get(key);
        if (tracker == null) {
            if (log.isDebugEnabled()) {
                log.debug("등록되지 않은 파티션의 ACK를 무시합니다. partition={}, offset={}, status={}",
                        key, event.offset(), event.status());
            }
            return;
        }

        tracker.recordCompletedOffset(event.offset());
        if (log.isDebugEnabled()) {
            log.debug("ACK를 반영했습니다. partition={}, offset={}, nextCommitOffset={}, pendingCount={}",
                    key,
                    event.offset(),
                    tracker.nextCommitOffset(),
                    tracker.pendingCompletionCount());
        }
    }

    /**
     * 각 파티션에서 커밋 가능한 오프셋 계획을 계산하여 반환합니다.
     *
     * @return 중립 커밋 오프셋 계획(Map: 파티션 -> 커밋 오프셋)
     */
    public Map<ConsumerPartition, Long> collectCommitOffsets() {
        final Map<ConsumerPartition, Long> commitPlan = new HashMap<>();

        for (Map.Entry<ConsumerPartition, PartitionCommitTracker> entry : trackers.entrySet()) {
            final OptionalLong commitOffset = entry.getValue().pollCommittableOffset();
            if (commitOffset.isPresent()) {
                commitPlan.put(entry.getKey(), commitOffset.getAsLong());
            }
        }

        if (commitPlan.isEmpty()) {
            return Map.of();
        }

        if (log.isDebugEnabled()) {
            log.debug("커밋 가능한 오프셋 계획을 계산했습니다. partitionCount={}, commitPlan={}",
                    commitPlan.size(), commitPlan);
        }

        return Collections.unmodifiableMap(commitPlan);
    }

    /**
     * 현재 등록된 파티션 추적기 개수를 반환합니다.
     *
     * @return 추적기 개수
     */
    public int trackerCount() {
        return trackers.size();
    }

    /**
     * 모든 파티션 추적기를 제거합니다.
     *
     * <p>런타임 종료/재시작 시점 정리 용도로 사용합니다.</p>
     */
    public void clear() {
        if (log.isDebugEnabled()) {
            log.debug("전체 커밋 추적기를 정리합니다. trackerCount={}", trackers.size());
        }
        trackers.clear();
    }
}
