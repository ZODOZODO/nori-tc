package com.nori.tc.common.kafka.processing;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 파티션별 {@link PartitionCommitTracker}를 집계하여 커밋 계획을 만드는 코디네이터입니다.
 *
 * <p>핵심 책임:</p>
 * <p>1) 파티션 등록/해제 라이프사이클 관리</p>
 * <p>2) worker ack 이벤트를 트래커에 반영</p>
 * <p>3) 연속 오프셋이 확보된 파티션만 Kafka commit 맵으로 변환</p>
 */
public final class PartitionCommitCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PartitionCommitCoordinator.class);

    private final Map<TopicPartition, PartitionCommitTracker> trackers = new ConcurrentHashMap<>();

    /**
     * 파티션 트래커를 등록합니다.
     *
     * <p>이미 등록된 파티션이면 기존 트래커를 교체합니다.</p>
     *
     * @param topicPartition 파티션 식별자
     * @param initialOffset 해당 파티션의 첫 미커밋 오프셋
     */
    public void registerPartition(final TopicPartition topicPartition, final long initialOffset) {
        Objects.requireNonNull(topicPartition, "topicPartition is null");
        trackers.put(topicPartition, new PartitionCommitTracker(topicPartition, initialOffset));
        if (log.isDebugEnabled()) {
            log.debug("커밋 트래커를 등록(교체 가능)했습니다. topicPartition={}, initialOffset={}",
                    topicPartition, initialOffset);
        }
    }

    /**
     * 파티션 트래커를 미등록 상태에서만 등록합니다.
     *
     * @param topicPartition 파티션 식별자
     * @param initialOffset 해당 파티션의 첫 미커밋 오프셋
     */
    public void registerPartitionIfAbsent(final TopicPartition topicPartition, final long initialOffset) {
        Objects.requireNonNull(topicPartition, "topicPartition is null");
        final PartitionCommitTracker previous = trackers.putIfAbsent(
                topicPartition,
                new PartitionCommitTracker(topicPartition, initialOffset)
        );
        if (previous == null && log.isDebugEnabled()) {
            log.debug("커밋 트래커를 신규 등록했습니다. topicPartition={}, initialOffset={}",
                    topicPartition, initialOffset);
        }
    }

    /**
     * 파티션 트래커를 해제합니다.
     *
     * @param topicPartition 파티션 식별자
     */
    public void unregisterPartition(final TopicPartition topicPartition) {
        if (topicPartition == null) {
            return;
        }
        final PartitionCommitTracker removed = trackers.remove(topicPartition);
        if (removed != null && log.isDebugEnabled()) {
            log.debug("커밋 트래커를 해제했습니다. topicPartition={}", topicPartition);
        }
    }

    /**
     * ack 이벤트를 해당 파티션 트래커에 반영합니다.
     *
     * @param event worker 실행 결과 ack 이벤트
     */
    public void applyAck(final AckEvent event) {
        Objects.requireNonNull(event, "event is null");
        if (!event.isCommitEligible()) {
            if (log.isDebugEnabled()) {
                log.debug("커밋 비대상 ack를 무시했습니다. topic={}, partition={}, offset={}, status={}",
                        event.topic(), event.partition(), event.offset(), event.status());
            }
            return;
        }

        final PartitionCommitTracker tracker = trackers.get(event.topicPartition());
        if (tracker == null) {
            if (log.isDebugEnabled()) {
                log.debug("등록되지 않은 파티션 ack를 무시했습니다. topic={}, partition={}, offset={}, status={}",
                        event.topic(), event.partition(), event.offset(), event.status());
            }
            return;
        }

        tracker.recordCompletedOffset(event.offset());
        if (log.isDebugEnabled()) {
            log.debug("ack를 반영했습니다. topic={}, partition={}, offset={}, nextCommitOffset={}, pendingCount={}",
                    event.topic(),
                    event.partition(),
                    event.offset(),
                    tracker.nextCommitOffset(),
                    tracker.pendingCompletionCount());
        }
    }

    /**
     * 연속 완료 구간이 전진한 파티션만 모아 Kafka commit 맵을 생성합니다.
     *
     * @return KafkaConsumer#commitSync에 전달 가능한 불변 맵
     */
    public Map<TopicPartition, OffsetAndMetadata> collectCommitOffsets() {
        final Map<TopicPartition, OffsetAndMetadata> commitMap = new HashMap<>();
        for (Map.Entry<TopicPartition, PartitionCommitTracker> entry : trackers.entrySet()) {
            final OptionalLong commitOffset = entry.getValue().pollCommittableOffset();
            if (commitOffset.isPresent()) {
                commitMap.put(entry.getKey(), new OffsetAndMetadata(commitOffset.getAsLong()));
            }
        }
        if (commitMap.isEmpty()) {
            return Map.of();
        }
        if (log.isDebugEnabled()) {
            log.debug("커밋 가능한 오프셋을 계산했습니다. partitionCount={}, commitMap={}",
                    commitMap.size(), commitMap);
        }
        return Collections.unmodifiableMap(commitMap);
    }

    /**
     * 현재 등록된 파티션 트래커 개수를 반환합니다.
     *
     * @return 트래커 개수
     */
    public int trackerCount() {
        return trackers.size();
    }

    /**
     * 모든 파티션 트래커를 제거합니다.
     */
    public void clear() {
        if (log.isDebugEnabled()) {
            log.debug("전체 커밋 트래커를 정리합니다. trackerCount={}", trackers.size());
        }
        trackers.clear();
    }
}
