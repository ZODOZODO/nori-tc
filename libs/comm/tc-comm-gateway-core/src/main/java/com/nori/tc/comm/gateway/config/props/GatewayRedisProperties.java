package com.nori.tc.comm.gateway.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

/**
 * Gateway Redis settings.
 *
 * This property holder is app-specific and controls DLQ and quarantine
 * retention (TTL) without modifying the shared redis starter.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.redis")
public class GatewayRedisProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayRedisProperties.class);

    /**
     * DLQ TTL in seconds. 0 means no expiry.
     */
    private Long dlqTtlSeconds;

    /**
     * Quarantine TTL in seconds. 0 means no expiry.
     */
    private Long quarantineTtlSeconds;

    
    /**
     * 게이트웨이 Redis 어댑터 입력/설정 유효성을 검증합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     */
    @PostConstruct
    public void validate() {
        if (dlqTtlSeconds == null || dlqTtlSeconds < 0) {
            throw new IllegalStateException("tc.comm.gateway.redis.dlq-ttl-seconds must be >= 0");
        }
        if (quarantineTtlSeconds == null || quarantineTtlSeconds < 0) {
            throw new IllegalStateException("tc.comm.gateway.redis.quarantine-ttl-seconds must be >= 0");
        }
        log.info("GatewayRedisProperties validated. dlqTtlSeconds={}, quarantineTtlSeconds={}",
                dlqTtlSeconds, quarantineTtlSeconds);
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public long getDlqTtlSeconds() {
        return dlqTtlSeconds;
    }

    
    /**
     * 게이트웨이 Redis 어댑터 설정 값을 반영합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @param dlqTtlSeconds 시간 관련 설정 값
     */
    public void setDlqTtlSeconds(final long dlqTtlSeconds) {
        this.dlqTtlSeconds = dlqTtlSeconds;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public long getQuarantineTtlSeconds() {
        return quarantineTtlSeconds;
    }

    
    /**
     * 게이트웨이 Redis 어댑터 설정 값을 반영합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @param quarantineTtlSeconds 시간 관련 설정 값
     */
    public void setQuarantineTtlSeconds(final long quarantineTtlSeconds) {
        this.quarantineTtlSeconds = quarantineTtlSeconds;
    }
}
