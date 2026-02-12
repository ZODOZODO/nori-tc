package com.nori.tc.comm.gateway.kafka;

import com.nori.tc.comm.gateway.config.GatewayKafkaShardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(KafkaShardOwnership.class);
    private final GatewayKafkaShardProperties properties;
    private final Set<Integer> ownedPartitionSet;

    
    /**
     * 게이트웨이 Kafka 어댑터 구성 요소를 초기화합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param properties 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     */
    public KafkaShardOwnership(final GatewayKafkaShardProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties is null");
        this.ownedPartitionSet = new HashSet<>(properties.getOwnedPartitions());
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 도메인 처리 로직을 수행합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public int partitionOf(final String eqpId) {
        return KafkaPartitioner.partitionForKey(eqpId, properties.getCommandsPartitionCount());
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @return 처리 성공 여부
     */
    public boolean isOwned(final String eqpId) {
        final int p = partitionOf(eqpId);
        final boolean owned = ownedPartitionSet.contains(p);
        if (log.isDebugEnabled()) {
            log.debug("Shard ownership check. eqpId={}, partition={}, owned={}", eqpId, p, owned);
        }
        return owned;
    }
}
