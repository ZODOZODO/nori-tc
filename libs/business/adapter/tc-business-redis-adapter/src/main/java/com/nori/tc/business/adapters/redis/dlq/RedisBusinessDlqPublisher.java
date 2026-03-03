package com.nori.tc.business.adapters.redis.dlq;

import com.nori.tc.db.domain.redis.dlq.RedisBusinessDlqEntry;
import com.nori.tc.business.core.dlq.BusinessDlqPublisherPort;
import com.nori.tc.business.domain.dlq.BusinessDlqMessage;
import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Business Redis(6380) 기반 DLQ 발행기입니다.
 *
 * <p>직렬화 포맷({@link RedisBusinessDlqEntry})은 {@code tc-db-domain}에 위치하여
 * {@code tc-ui-redis-adapter}가 동일 클래스를 사용해 역직렬화할 수 있습니다.</p>
 *
 * <p>저장 규칙:</p>
 * <ul>
 *   <li>Redis Key: {@code tc:business:core:dlq:{dlqId}}</li>
 *   <li>Value: {@link RedisBusinessDlqEntry} (JDK 직렬화)</li>
 *   <li>TTL: {@code tc.business.core.redis.dlq-ttl-seconds}</li>
 * </ul>
 */
@Component
public class RedisBusinessDlqPublisher implements BusinessDlqPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RedisBusinessDlqPublisher.class);
    private static final String DLQ_KEY_PREFIX = "tc:business:core:dlq:";

    private final TcRedisCrudRepository repository;
    private final BusinessRedisProperties redisProperties;

    /**
     * 발행기에 필요한 의존성을 주입받습니다.
     */
    public RedisBusinessDlqPublisher(
            final TcRedisCrudRepository repository,
            final BusinessRedisProperties redisProperties
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
        this.redisProperties = Objects.requireNonNull(redisProperties, "redisProperties is null");
    }

    /**
     * DLQ 메시지를 Redis에 저장합니다.
     *
     * @param dlqMessage DLQ 표준 메시지
     */
    @Override
    public void publish(final BusinessDlqMessage dlqMessage) {
        Objects.requireNonNull(dlqMessage, "dlqMessage is null");

        final long ttlSeconds = redisProperties.getDlqTtlSeconds();
        final Duration ttl = ttlSeconds > 0L ? Duration.ofSeconds(ttlSeconds) : null;
        final Long ttlMeta = ttlSeconds > 0L ? ttlSeconds : null;

        final RedisBusinessDlqEntry entry = new RedisBusinessDlqEntry(
                dlqMessage.dlqId(),
                dlqMessage.source(),
                dlqMessage.stage(),
                dlqMessage.reasonCode(),
                dlqMessage.reasonMessage(),
                dlqMessage.occurredAt(),
                dlqMessage.topic(),
                dlqMessage.partition(),
                dlqMessage.offset(),
                dlqMessage.eqpId(),
                dlqMessage.messageType(),
                dlqMessage.messageName(),
                dlqMessage.traceId(),
                dlqMessage.payloadRef(),
                dlqMessage.tags(),
                ttlMeta
        );

        final String redisKey = DLQ_KEY_PREFIX + dlqMessage.dlqId();
        if (ttl == null) {
            repository.set(redisKey, entry);
        } else {
            repository.set(redisKey, entry, ttl);
        }

        if (log.isDebugEnabled()) {
            log.debug("Business DLQ entry stored in Redis. key={}, source={}, stage={}, reasonCode={}, eqpId={}, traceId={}, ttlSeconds={}",
                    redisKey,
                    dlqMessage.source(),
                    dlqMessage.stage(),
                    dlqMessage.reasonCode(),
                    dlqMessage.eqpId(),
                    dlqMessage.traceId(),
                    ttlMeta);
        }
    }
}
