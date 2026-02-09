package com.nori.tc.apps.commgateway.redis;

import com.nori.tc.apps.commgateway.config.GatewayRedisProperties;
import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.port.QuarantinePort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * Redis 기반 Quarantine 저장소
 */
@Component
public class RedisQuarantineStore implements QuarantinePort {

    private final RedisQuarantineRepository repository;
    private final GatewayRedisProperties redisProperties;

    public RedisQuarantineStore(
            final RedisQuarantineRepository repository,
            final GatewayRedisProperties redisProperties
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
        this.redisProperties = Objects.requireNonNull(redisProperties, "redisProperties is null");
    }

    @Override
    public void quarantine(final EquipmentId equipmentId, final String reasonCode, final String reasonMessage) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");

        final Long ttl = redisProperties.getQuarantineTtlSeconds() > 0
                ? redisProperties.getQuarantineTtlSeconds()
                : null;

        final RedisQuarantineEntry entry = new RedisQuarantineEntry(
                equipmentId.value(),
                reasonCode,
                reasonMessage,
                Instant.now(),
                ttl
        );

        repository.save(entry);
    }
}
