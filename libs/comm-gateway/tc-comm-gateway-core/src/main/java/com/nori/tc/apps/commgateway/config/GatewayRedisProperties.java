package com.nori.tc.apps.commgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * Gateway Redis settings.
 *
 * This property holder is app-specific and controls DLQ and quarantine
 * retention (TTL) without modifying the shared redis starter.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.redis")
public class GatewayRedisProperties {

    /**
     * DLQ TTL in seconds. 0 means no expiry.
     */
    private Long dlqTtlSeconds;

    /**
     * Quarantine TTL in seconds. 0 means no expiry.
     */
    private Long quarantineTtlSeconds;

    @PostConstruct
    public void validate() {
        if (dlqTtlSeconds == null || dlqTtlSeconds < 0) {
            throw new IllegalStateException("tc.comm.gateway.redis.dlq-ttl-seconds must be >= 0");
        }
        if (quarantineTtlSeconds == null || quarantineTtlSeconds < 0) {
            throw new IllegalStateException("tc.comm.gateway.redis.quarantine-ttl-seconds must be >= 0");
        }
    }

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
