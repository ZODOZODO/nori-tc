package com.nori.tc.comm.gateway.kafka;

import com.nori.tc.comm.gateway.config.props.GatewayKafkaShardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 설비(`eqpId`) 기준 Kafka 샤드 소유 여부를 판정하는 유틸리티입니다.
 *
 * <p>핵심 원칙:</p>
 * <p>1) 파티션 계산은 Kafka 기본 파티셔너(murmur2 + toPositive)와 동일해야 합니다.</p>
 * <p>2) `eqpId -> partition` 결과가 `ownedPartitions`에 포함되면 이 인스턴스가 처리 대상입니다.</p>
 * <p>3) 소유 파티션 집합은 설정 기반으로 고정되며, 런타임 중 변경되지 않습니다.</p>
 */
@Component
public class KafkaShardOwnership {

    private static final Logger log = LoggerFactory.getLogger(KafkaShardOwnership.class);

    /**
     * 샤드 관련 설정 원본입니다.
     */
    private final GatewayKafkaShardProperties properties;

    /**
     * 현재 인스턴스가 처리할 파티션 집합입니다.
     */
    private final Set<Integer> ownedPartitionSet;

    /**
     * 샤드 소유권 판정기를 초기화합니다.
     *
     * <p>초기화 시점에 소유 파티션을 INFO 로그로 남겨 운영 중 샤드 배치를 명확히 확인할 수 있도록 합니다.</p>
     *
     * @param properties Kafka 샤드 설정
     */
    public KafkaShardOwnership(final GatewayKafkaShardProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties is null");
        this.ownedPartitionSet = new HashSet<>(properties.getOwnedPartitions());
        log.info(
                "KafkaShardOwnership initialized. commandsPartitionCount={}, ownedPartitions={}",
                this.properties.getCommandsPartitionCount(),
                new TreeSet<>(this.ownedPartitionSet)
        );
    }

    /**
     * `eqpId`가 매핑되는 Kafka partition 번호를 계산합니다.
     *
     * <p>계산식은 Kafka 기본 파티셔닝 규칙과 동일해야 하며, 소유권 판단의 기준 값으로 사용됩니다.</p>
     *
     * @param eqpId 설비 식별자
     * @return 계산된 partition 번호
     */
    public int partitionOf(final String eqpId) {
        return KafkaPartitioner.partitionForKey(eqpId, properties.getCommandsPartitionCount());
    }

    /**
     * 현재 인스턴스가 소유한 고정 partition 목록을 반환합니다.
     *
     * <p>반환 값은 외부에서 수정할 수 없는 스냅샷이며, 로그/조회/필터링 목적에 사용합니다.</p>
     * <p>정렬된 형태로 반환하여 운영 로그/디버깅 시 가독성을 높입니다.</p>
     *
     * @return 정렬된 소유 partition 목록 스냅샷
     */
    public List<Integer> ownedPartitions() {
        return List.copyOf(new TreeSet<>(ownedPartitionSet));
    }

    /**
     * 특정 partition 번호가 현재 인스턴스 소유인지 판정합니다.
     *
     * <p>U8부터 Gateway 런타임 소유권 판정은 eqpId 해시가 아니라
     * DB의 {@code route_partition} 값을 기준으로 수행하는 방향으로 전환됩니다.</p>
     *
     * @param partition route_partition 값 (null 허용)
     * @return 소유 partition이면 true, null 또는 미소유면 false
     */
    public boolean isOwnedPartition(final Integer partition) {
        if (partition == null) {
            if (log.isDebugEnabled()) {
                log.debug("Shard ownership check skipped because routePartition is null. owned=false");
            }
            return false;
        }

        final boolean owned = ownedPartitionSet.contains(partition);
        if (log.isDebugEnabled()) {
            log.debug("Shard ownership check by routePartition. partition={}, owned={}", partition, owned);
        }
        return owned;
    }

    /**
     * 현재 인스턴스가 해당 `eqpId`를 처리해야 하는지 판정합니다.
     *
     * <p>DEBUG 로그에는 판정 근거(`partition`, `owned`)를 함께 남겨
     * 운영 중 라우팅 이슈를 빠르게 분석할 수 있도록 합니다.</p>
     *
     * @param eqpId 설비 식별자
     * @return 소유 파티션이면 true
     */
    public boolean isOwned(final String eqpId) {
        final int partition = partitionOf(eqpId);
        final boolean owned = isOwnedPartition(partition);
        if (log.isDebugEnabled()) {
            log.debug("Shard ownership check. eqpId={}, partition={}, owned={}", eqpId, partition, owned);
        }
        return owned;
    }
}
