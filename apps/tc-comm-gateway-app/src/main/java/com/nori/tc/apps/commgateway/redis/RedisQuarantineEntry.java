package com.nori.tc.apps.commgateway.redis;

import java.io.Serializable;
import java.time.Instant;

/**
 * Redis Quarantine 엔트리
 */
public class RedisQuarantineEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String equipmentId;

    private String reasonCode;

    private String reasonMessage;

    private Instant quarantinedAt;

    private Long ttlSeconds;

    protected RedisQuarantineEntry() {
    }

    public RedisQuarantineEntry(
            final String equipmentId,
            final String reasonCode,
            final String reasonMessage,
            final Instant quarantinedAt,
            final Long ttlSeconds
    ) {
        this.equipmentId = equipmentId;
        this.reasonCode = reasonCode;
        this.reasonMessage = reasonMessage;
        this.quarantinedAt = quarantinedAt;
        this.ttlSeconds = ttlSeconds;
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonMessage() {
        return reasonMessage;
    }

    public Instant getQuarantinedAt() {
        return quarantinedAt;
    }

    public Long getTtlSeconds() {
        return ttlSeconds;
    }
}
