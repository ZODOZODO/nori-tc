package com.nori.tc.apps.commgateway.kafka;

import com.nori.tc.apps.commgateway.config.GatewayKafkaShardProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Shard ownership 판단 유틸.
 *
 * 핵심 원칙
 * - Kafka의 기본 파티셔닝 로직과 100% 동일해야 합니다.
 * - eqpId -> partition 계산 결과가 ownedPartitions에 포함되는지로 소유 여부를 판정합니다.
 * - 소유 파티션은 config로 고정되며 리밸런싱에 의해 변하지 않습니다.
 */
@Component
public class KafkaShardOwnership {

    private final GatewayKafkaShardProperties properties;
    private final Set<Integer> ownedPartitionSet;

    public KafkaShardOwnership(final GatewayKafkaShardProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties is null");
        this.ownedPartitionSet = new HashSet<>(properties.getOwnedPartitions());
    }

    public int partitionOf(final String eqpId) {
        return KafkaPartitioner.partitionForKey(eqpId, properties.getCommandsPartitionCount());
    }

    public boolean isOwned(final String eqpId) {
        final int p = partitionOf(eqpId);
        return ownedPartitionSet.contains(p);
    }
}
