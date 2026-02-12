package com.nori.tc.comm.adapters.redis.dlq;

import com.nori.tc.comm.gateway.config.GatewayRedisProperties;
import com.nori.tc.comm.gateway.domain.dlq.DlqMessage;
import com.nori.tc.comm.gateway.domain.dlq.DlqReasonCode;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
import com.nori.tc.comm.core.port.DlqPublisherPort;
import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis 기반 DLQ 발행기.
 *
 * 설계 의도
 * - DLQ 키 규칙과 보관 기간(TTL)을 게이트웨이 앱 정책으로 제어합니다.
 * - 공통 Redis 스타터를 그대로 쓰되, DLQ 저장 포맷은 앱 계층에서 명시적으로 관리합니다.
 */
@Component
public class RedisDlqPublisher implements DlqPublisherPort {

    private static final String DLQ_KEY_PREFIX = "tc:comm:gateway:dlq:";
    private static final Logger log = LoggerFactory.getLogger(RedisDlqPublisher.class);

    private final TcRedisCrudRepository repository;
    private final GatewayRedisProperties redisProperties;
    private final GatewayMetrics metrics;

    
    /**
     * 게이트웨이 Redis 어댑터 구성 요소를 초기화합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @param repository 게이트웨이 Redis 어댑터 처리에 사용하는 입력 값
     * @param redisProperties 게이트웨이 Redis 어댑터 처리에 사용하는 입력 값
     * @param metrics 게이트웨이 Redis 어댑터 처리에 사용하는 입력 값
     */
    public RedisDlqPublisher(
            final TcRedisCrudRepository repository,
            final GatewayRedisProperties redisProperties,
            final GatewayMetrics metrics
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
        this.redisProperties = Objects.requireNonNull(redisProperties, "redisProperties is null");
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
    }

    
    /**
     * 게이트웨이 Redis 어댑터 메시지 또는 이벤트를 발행합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @param message 처리할 원본 데이터
     */
    @Override
    public void publish(final DlqMessage message) {
        // 출력 단계: 결과를 외부 저장소/브로커로 반영합니다.
        Objects.requireNonNull(message, "message is null");

        // TTL은 Redis 만료(Duration)와 엔트리 메타데이터(초 단위)에 함께 사용합니다.
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
        if (log.isDebugEnabled()) {
            log.debug("DLQ published to Redis. key={}, eqpId={}, traceId={}, reason={}",
                    key, message.eqpId(), message.traceId(), message.reasonCode());
        }
    }
}
