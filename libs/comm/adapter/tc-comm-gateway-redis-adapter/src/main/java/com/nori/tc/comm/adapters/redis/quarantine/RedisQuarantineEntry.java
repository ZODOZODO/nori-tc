package com.nori.tc.comm.adapters.redis.quarantine;

import java.io.Serializable;
import java.time.Instant;

/**
 * Redis quarantine entry.
 *
 * Records why an equipment was quarantined and for how long.
 */
public class RedisQuarantineEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private String equipmentId;

    private String reasonCode;

    private String reasonMessage;

    private Instant quarantinedAt;

    private Long ttlSeconds;

    
    /**
     * 게이트웨이 Redis 어댑터 구성 요소를 초기화합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     */
    protected RedisQuarantineEntry() {
    }

    
    /**
     * 게이트웨이 Redis 어댑터 구성 요소를 초기화합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @param equipmentId 설비 식별 정보
     * @param reasonCode 게이트웨이 Redis 어댑터 처리에 사용하는 입력 값
     * @param reasonMessage 처리할 원본 데이터
     * @param quarantinedAt 게이트웨이 Redis 어댑터 처리에 사용하는 입력 값
     * @param ttlSeconds 시간 관련 설정 값
     */
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
    public Instant getQuarantinedAt() {
        return quarantinedAt;
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
