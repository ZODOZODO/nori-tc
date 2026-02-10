package com.nori.tc.apps.commgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
    private long dlqTtlSeconds = 0;

    /**
     * Quarantine TTL in seconds. 0 means no expiry.
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
