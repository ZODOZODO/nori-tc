package com.nori.tc.apps.commgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 기반 저장소 설정
 * - DLQ / Quarantine TTL 등 운영 정책을 외부 설정으로 제어합니다.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.redis")
public class GatewayRedisProperties {

    /**
     * DLQ TTL(초) - 0이면 만료 없음
     */
    private long dlqTtlSeconds = 0;

    /**
     * Quarantine TTL(초) - 0이면 만료 없음
     */
    private long quarantineTtlSeconds = 0;

    public long getDlqTtlSeconds() {
        return dlqTtlSeconds;
    }

    public void setDlqTtlSeconds(final long dlqTtlSeconds) {
        this.dlqTtlSeconds = dlqTtlSeconds;
    }

    public long getQuarantineTtlSeconds() {
        return quarantineTtlSeconds;
    }

    public void setQuarantineTtlSeconds(final long quarantineTtlSeconds) {
        this.quarantineTtlSeconds = quarantineTtlSeconds;
    }
}
