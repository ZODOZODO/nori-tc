package com.nori.tc.apps.commgateway.redis;

import com.nori.tc.apps.commgateway.config.GatewayRedisProperties;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.domain.dlq.DlqMessage;
import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis 기반 DLQ Publisher
 */
@Component
public class RedisDlqPublisher implements DlqPublisherPort {

    private static final String DLQ_KEY_PREFIX = "tc:comm:gateway:dlq:";

    private final TcRedisCrudRepository repository;
    private final GatewayRedisProperties redisProperties;

    public RedisDlqPublisher(
            final TcRedisCrudRepository repository,
            final GatewayRedisProperties redisProperties
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
        this.redisProperties = Objects.requireNonNull(redisProperties, "redisProperties is null");
    }

    @Override
    public void publish(final DlqMessage message) {
        Objects.requireNonNull(message, "message is null");

        // TTL은 "Redis 만료"와 "Entry 내부 메타데이터" 둘 다에 사용된다.
        // - Redis: Duration 필요
        // - Entry: 초 단위(Long)로 저장
        final long ttlSeconds = redisProperties.getDlqTtlSeconds();
        final Duration ttl = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : null;

        final RedisDlqEntry entry = new RedisDlqEntry(
                message.dlqId(),
                message.eqpId(),
                message.traceNo(),
                message.commInterfaceType().name(),
                message.socketType(),
                message.stage(),
                message.reasonCode().name(),
                message.reasonMessage(),
                message.occurredAt(),
                message.payloadRefKey(),
                message.rawLen(),
                message.b64Len(),
                message.tags(),
                ttlSeconds > 0 ? ttlSeconds : null
        );

        final String key = DLQ_KEY_PREFIX + entry.getDlqId();

        if (ttl == null) {
            repository.set(key, entry);
        } else {
            repository.set(key, entry, ttl);
        }
    }
}
