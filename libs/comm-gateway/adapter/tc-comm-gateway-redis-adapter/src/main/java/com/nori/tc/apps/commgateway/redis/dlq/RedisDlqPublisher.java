package com.nori.tc.apps.commgateway.redis.dlq;

import com.nori.tc.apps.commgateway.config.GatewayRedisProperties;
import com.nori.tc.apps.commgateway.metrics.GatewayMetrics;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.domain.dlq.DlqMessage;
import com.nori.tc.comm.domain.dlq.DlqReasonCode;
import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis-based DLQ publisher.
 *
 * This is app-specific to keep message retention and key format
 * under the application's control rather than the shared starter.
 */
@Component
public class RedisDlqPublisher implements DlqPublisherPort {

    private static final String DLQ_KEY_PREFIX = "tc:comm:gateway:dlq:";

    private final TcRedisCrudRepository repository;
    private final GatewayRedisProperties redisProperties;
    private final GatewayMetrics metrics;

    public RedisDlqPublisher(
            final TcRedisCrudRepository repository,
            final GatewayRedisProperties redisProperties,
            final GatewayMetrics metrics
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
        this.redisProperties = Objects.requireNonNull(redisProperties, "redisProperties is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
    }

    @Override
    public void publish(final DlqMessage message) {
        Objects.requireNonNull(message, "message is null");

        // TTL is used both for Redis expiry (Duration) and for entry metadata (seconds).
        final long ttlSeconds = redisProperties.getDlqTtlSeconds();
        final Duration ttl = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : null;

        final RedisDlqEntry entry = new RedisDlqEntry(
                message.dlqId(),
                message.eqpId(),
                message.traceId(),
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

        metrics.incrementDlqPublish();
        if (message.reasonCode() == DlqReasonCode.PARSING_FAILED) {
            metrics.incrementDecodeFail();
        }
    }
}
