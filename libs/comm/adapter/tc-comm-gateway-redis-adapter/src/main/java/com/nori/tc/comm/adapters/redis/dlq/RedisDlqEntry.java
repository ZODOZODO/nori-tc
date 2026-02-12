package com.nori.tc.comm.adapters.redis.dlq;

import java.io.Serializable;
import java.util.Map;

/**
 * Redis DLQ entry.
 *
 * Stores DLQ metadata to support inspection and replay.
 */
public class RedisDlqEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String dlqId;

    private String equipmentId;
    private String traceId;
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

    private Long ttlSeconds;

    
    /**
     * 게이트웨이 Redis 어댑터 구성 요소를 초기화합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     */
    protected RedisDlqEntry() {
    }

    public RedisDlqEntry(
            final String dlqId,
            final String equipmentId,
            final String traceId,
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
        this.traceId = traceId;
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

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public String getDlqId() {
        return dlqId;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public String getEquipmentId() {
        return equipmentId;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public String getTraceId() {
        return traceId;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public String getCommInterfaceType() {
        return commInterfaceType;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public String getSocketType() {
        return socketType;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public String getStage() {
        return stage;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public String getReasonCode() {
        return reasonCode;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public String getReasonMessage() {
        return reasonMessage;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public long getOccurredAt() {
        return occurredAt;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public String getPayloadRefKey() {
        return payloadRefKey;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public long getRawLen() {
        return rawLen;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public long getB64Len() {
        return b64Len;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public Map<String, String> getTags() {
        return tags;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public Long getTtlSeconds() {
        return ttlSeconds;
    }
}
