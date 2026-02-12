package com.nori.tc.apps.commgateway.redis.quarantine;

import com.nori.tc.apps.commgateway.config.GatewayRedisProperties;
import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.core.port.QuarantinePort;
import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Redis-based quarantine store.
 *
 * Keeps quarantine state in Redis to protect downstream systems
 * from repeated failing equipment traffic.
 */
@Component
public class RedisQuarantineStore implements QuarantinePort {

    private static final String QUARANTINE_KEY_PREFIX = "tc:comm:gateway:quarantine:";
    private static final Logger log = LoggerFactory.getLogger(RedisQuarantineStore.class);

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

        // TTL is used both for Redis expiry (Duration) and for entry metadata (seconds).
        final long ttlSeconds = redisProperties.getQuarantineTtlSeconds();
        final Duration ttl = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : null;

        final RedisQuarantineEntry entry = new RedisQuarantineEntry(
                equipmentId.value(),
                reasonCode,
                reasonMessage,
                Instant.now(),
                ttlSeconds > 0 ? ttlSeconds : null
        );

        final String key = QUARANTINE_KEY_PREFIX + equipmentId.value();

        if (ttl == null) {
            repository.set(key, entry);
        } else {
            repository.set(key, entry, ttl);
        }
        log.info("Equipment quarantined. eqpId={}, reasonCode={}, ttlSeconds={}",
                equipmentId.value(), reasonCode, ttlSeconds > 0 ? ttlSeconds : null);
    }
}
