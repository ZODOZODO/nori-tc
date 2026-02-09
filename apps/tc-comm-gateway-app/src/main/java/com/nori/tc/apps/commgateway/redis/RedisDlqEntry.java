package com.nori.tc.apps.commgateway.redis;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.time.Instant;
import java.util.Map;

/**
 * Redis DLQ 엔트리
 *
 * - DLQ 메시지를 Redis에 저장하여 운영자가 빠르게 조회할 수 있도록 합니다.
 */
@RedisHash("tc-comm-dlq")
public class RedisDlqEntry {

    @Id
    private String dlqId;

    private String equipmentId;
    private String traceNo;
    private String commInterfaceType;
    private String socketType;
    private String stage;
    private String reasonCode;
    private String reasonMessage;
    private long occurredAt;
    private String payloadRefKey;
    private long rawLen;
    private long b64Len;
    private Map<String, String> tags;

    @TimeToLive
    private Long ttlSeconds;

    protected RedisDlqEntry() {
    }

    public RedisDlqEntry(
            final String dlqId,
            final String equipmentId,
            final String traceNo,
            final String commInterfaceType,
            final String socketType,
            final String stage,
            final String reasonCode,
            final String reasonMessage,
            final long occurredAt,
            final String payloadRefKey,
            final long rawLen,
            final long b64Len,
            final Map<String, String> tags,
            final Long ttlSeconds
    ) {
        this.dlqId = dlqId;
        this.equipmentId = equipmentId;
        this.traceNo = traceNo;
        this.commInterfaceType = commInterfaceType;
        this.socketType = socketType;
        this.stage = stage;
        this.reasonCode = reasonCode;
        this.reasonMessage = reasonMessage;
        this.occurredAt = occurredAt;
        this.payloadRefKey = payloadRefKey;
        this.rawLen = rawLen;
        this.b64Len = b64Len;
        this.tags = tags;
        this.ttlSeconds = ttlSeconds;
    }

    public String getDlqId() {
        return dlqId;
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public String getTraceNo() {
        return traceNo;
    }

    public String getCommInterfaceType() {
        return commInterfaceType;
    }

    public String getSocketType() {
        return socketType;
    }

    public String getStage() {
        return stage;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonMessage() {
        return reasonMessage;
    }

    public long getOccurredAt() {
        return occurredAt;
    }

    public String getPayloadRefKey() {
        return payloadRefKey;
    }

    public long getRawLen() {
        return rawLen;
    }

    public long getB64Len() {
        return b64Len;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public Long getTtlSeconds() {
        return ttlSeconds;
    }
}
