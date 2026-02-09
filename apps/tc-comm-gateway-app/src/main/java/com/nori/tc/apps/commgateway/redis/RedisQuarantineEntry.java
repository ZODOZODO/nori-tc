package com.nori.tc.apps.commgateway.redis;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.time.Instant;

/**
 * Redis Quarantine 엔트리
 */
@RedisHash("tc-comm-quarantine")
public class RedisQuarantineEntry {

    @Id
    private String equipmentId;

    private String reasonCode;

    private String reasonMessage;

    private Instant quarantinedAt;

    @TimeToLive
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
