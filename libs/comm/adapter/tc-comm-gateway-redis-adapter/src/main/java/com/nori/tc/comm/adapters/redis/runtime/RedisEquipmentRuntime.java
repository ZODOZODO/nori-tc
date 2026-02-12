package com.nori.tc.comm.adapters.redis.runtime;

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

    
    /**
     * 게이트웨이 Redis 어댑터 구성 요소를 초기화합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     */
    protected RedisEquipmentRuntime() {
    }

    
    /**
     * 게이트웨이 Redis 어댑터 구성 요소를 초기화합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @param equipmentId 설비 식별 정보
     * @param connectionState 통신 채널/세션 정보
     * @param lastReceivedAt 게이트웨이 Redis 어댑터 처리에 사용하는 입력 값
     * @param lastCommandAt 처리할 요청/명령 정보
     * @param ttlSeconds 시간 관련 설정 값
     */
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
    public String getConnectionState() {
        return connectionState;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public Instant getLastReceivedAt() {
        return lastReceivedAt;
    }

    
    /**
     * 게이트웨이 Redis 어댑터의 현재 값을 조회합니다.
     *
     * <p>Redis 키/스트림 구조와 TTL 정책을 기준으로 데이터를 처리합니다.</p>
     * @return 게이트웨이 Redis 어댑터 처리 결과
     */
    public Instant getLastCommandAt() {
        return lastCommandAt;
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
