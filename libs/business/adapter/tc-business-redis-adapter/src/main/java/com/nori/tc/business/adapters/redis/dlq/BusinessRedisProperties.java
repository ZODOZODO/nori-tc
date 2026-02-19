package com.nori.tc.business.adapters.redis.dlq;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Business Redis 관련 런타임 설정입니다.
 *
 * <p>prefix: {@code tc.business.core.redis}</p>
 */
@ConfigurationProperties(prefix = "tc.business.core.redis")
public class BusinessRedisProperties {

    private static final Logger log = LoggerFactory.getLogger(BusinessRedisProperties.class);

    /**
     * DLQ 보관 TTL(초)입니다.
     *
     * <p>0이면 만료 없이 보관합니다.</p>
     */
    private Long dlqTtlSeconds;

    /**
     * 설정값 유효성을 검증합니다.
     */
    @PostConstruct
    public void validate() {
        if (dlqTtlSeconds == null || dlqTtlSeconds < 0L) {
            throw new IllegalStateException("tc.business.core.redis.dlq-ttl-seconds must be >= 0");
        }
        log.info("BusinessRedisProperties validated. dlqTtlSeconds={}", dlqTtlSeconds);
    }

    public long getDlqTtlSeconds() {
        return dlqTtlSeconds;
    }

    public void setDlqTtlSeconds(final long dlqTtlSeconds) {
        this.dlqTtlSeconds = dlqTtlSeconds;
    }
}
