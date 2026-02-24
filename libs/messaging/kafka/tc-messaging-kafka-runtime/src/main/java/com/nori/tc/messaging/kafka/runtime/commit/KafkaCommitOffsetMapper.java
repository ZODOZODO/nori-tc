package com.nori.tc.messaging.kafka.runtime.commit;

import com.nori.tc.common.consumer.runtime.ConsumerPartition;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 중립 커밋 오프셋 계획을 Kafka 커밋 맵으로 변환하는 유틸리티입니다.
 *
 * <p>중립 공용 런타임은 Kafka SDK 타입을 알지 못하므로, 실제 Kafka 커밋 직전 변환 책임은
 * Kafka 전용 런타임 계층에서 담당합니다.</p>
 */
public final class KafkaCommitOffsetMapper {

    private static final Logger log = LoggerFactory.getLogger(KafkaCommitOffsetMapper.class);

    /**
     * 유틸리티 클래스 생성 방지 생성자입니다.
     */
    private KafkaCommitOffsetMapper() {
        // 인스턴스 생성 방지
    }

    /**
     * 중립 커밋 계획을 Kafka 커밋 맵으로 변환합니다.
     *
     * @param commitPlan 중립 커밋 계획(Map: 소비 파티션 -> 커밋 오프셋)
     * @return KafkaConsumer#commitSync에 전달 가능한 Kafka 커밋 맵
     */
    public static Map<TopicPartition, OffsetAndMetadata> toKafkaCommitMap(
            final Map<ConsumerPartition, Long> commitPlan
    ) {
        Objects.requireNonNull(commitPlan, "commitPlan is null");
        if (commitPlan.isEmpty()) {
            return Map.of();
        }

        final Map<TopicPartition, OffsetAndMetadata> commitMap = new HashMap<>(commitPlan.size());
        for (Map.Entry<ConsumerPartition, Long> entry : commitPlan.entrySet()) {
            final ConsumerPartition partition = entry.getKey();
            final Long commitOffset = entry.getValue();

            if (partition == null) {
                throw new IllegalArgumentException("commitPlan contains null partition");
            }
            if (commitOffset == null || commitOffset < 0L) {
                throw new IllegalArgumentException("commitPlan contains invalid commitOffset");
            }

            commitMap.put(
                    new TopicPartition(partition.topic(), partition.partition()),
                    new OffsetAndMetadata(commitOffset)
            );
        }

        if (log.isDebugEnabled()) {
            log.debug("중립 커밋 계획을 Kafka 커밋 맵으로 변환했습니다. partitionCount={}, commitMap={}",
                    commitMap.size(), commitMap);
        }

        return Collections.unmodifiableMap(commitMap);
    }
}
