package com.nori.tc.apps.commgateway.redis;

import com.nori.tc.apps.commgateway.config.GatewayRedisProperties;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.comm.domain.dlq.DlqMessage;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Redis 기반 DLQ Publisher
 */
@Component
public class RedisDlqPublisher implements DlqPublisherPort {

    private final RedisDlqRepository repository;
    private final GatewayRedisProperties redisProperties;

    public RedisDlqPublisher(
            final RedisDlqRepository repository,
            final GatewayRedisProperties redisProperties
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
        this.redisProperties = Objects.requireNonNull(redisProperties, "redisProperties is null");
    }

    @Override
    public void publish(final DlqMessage message) {
        Objects.requireNonNull(message, "message is null");

        final Long ttl = redisProperties.getDlqTtlSeconds() > 0
                ? redisProperties.getDlqTtlSeconds()
                : null;

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
                ttl
        );

        repository.save(entry);
    }
}
