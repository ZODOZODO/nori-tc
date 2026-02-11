package com.nori.tc.apps.commgateway.redis.runtime;

import java.io.Serializable;
import java.time.Instant;

/**
 * Redis-based equipment runtime state snapshot.
 */
public class RedisEquipmentRuntime implements Serializable {

    private static final long serialVersionUID = 1L;

    private String equipmentId;

    private String connectionState;

    private Instant lastReceivedAt;

    private Instant lastCommandAt;

    private Long ttlSeconds;

    protected RedisEquipmentRuntime() {
    }

    public RedisEquipmentRuntime(
            final String equipmentId,
            final String connectionState,
            final Instant lastReceivedAt,
            final Instant lastCommandAt,
            final Long ttlSeconds
    ) {
        this.equipmentId = equipmentId;
        this.connectionState = connectionState;
        this.lastReceivedAt = lastReceivedAt;
        this.lastCommandAt = lastCommandAt;
        this.ttlSeconds = ttlSeconds;
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public String getConnectionState() {
        return connectionState;
    }

    public Instant getLastReceivedAt() {
        return lastReceivedAt;
    }

    public Instant getLastCommandAt() {
        return lastCommandAt;
    }

    public Long getTtlSeconds() {
        return ttlSeconds;
    }
}
