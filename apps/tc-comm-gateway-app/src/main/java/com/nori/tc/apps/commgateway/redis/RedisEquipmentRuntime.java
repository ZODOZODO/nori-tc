package com.nori.tc.apps.commgateway.redis;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.time.Instant;

/**
 * Redis 기반 설비 런타임 상태
 *
 * - 통신 상태/최종 수신 시각 등을 캐시에 저장하여 빠른 조회를 지원합니다.
 */
@RedisHash("tc-comm-eqp-runtime")
public class RedisEquipmentRuntime {

    @Id
    private String equipmentId;

    private String connectionState;

    private Instant lastReceivedAt;

    private Instant lastCommandAt;

    @TimeToLive
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
