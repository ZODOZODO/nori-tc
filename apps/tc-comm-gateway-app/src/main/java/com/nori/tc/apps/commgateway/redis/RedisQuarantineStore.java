package com.nori.tc.apps.commgateway.redis;

import com.nori.tc.apps.commgateway.config.GatewayRedisProperties;
import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Redis 기반 Quarantine 저장소
 */
@Component
public class RedisQuarantineStore implements QuarantinePort {

    private static final String QUARANTINE_KEY_PREFIX = "tc:comm:gateway:quarantine:";

    private final TcRedisCrudRepository repository;
    private final GatewayRedisProperties redisProperties;

    public RedisQuarantineStore(
            final TcRedisCrudRepository repository,
            final GatewayRedisProperties redisProperties
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
        this.redisProperties = Objects.requireNonNull(redisProperties, "redisProperties is null");
    }

    @Override
    public void quarantine(final EquipmentId equipmentId, final String reasonCode, final String reasonMessage) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");

        final Duration ttl = redisProperties.getQuarantineTtlSeconds() > 0
                ? Duration.ofSeconds(redisProperties.getQuarantineTtlSeconds())
                : null;

        final RedisQuarantineEntry entry = new RedisQuarantineEntry(
                equipmentId.value(),
                reasonCode,
                reasonMessage,
                Instant.now(),
                ttl
        );

        final String key = QUARANTINE_KEY_PREFIX + equipmentId.value();

        if (ttl == null) {
            repository.set(key, entry);
        } else {
            repository.set(key, entry, ttl);
        }
    }
}
