package com.nori.tc.business.adapters.redis.ui;

import com.nori.tc.common.kafka.task.pipeline.KafkaTaskDeduplicationStore;
import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis 기반 UI traceId 중복 방지 저장소입니다.
 *
 * <p>설계 목적:</p>
 * <p>1) 단일 JVM 메모리 대신 Redis를 사용해 멀티 인스턴스 환경에서도
 * 중복 traceId를 동일하게 차단합니다.</p>
 * <p>2) {@code SETNX + TTL} 패턴으로 원자적 중복 등록을 보장합니다.</p>
 */
@Primary
@Component
public class RedisBusinessUiTraceIdDeduplicationStore implements KafkaTaskDeduplicationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisBusinessUiTraceIdDeduplicationStore.class);
    private static final String TRACE_KEY_PREFIX = "tc:business:core:ui:trace:";

    private final TcRedisCrudRepository repository;

    /**
     * Redis CRUD 저장소를 주입받습니다.
     */
    public RedisBusinessUiTraceIdDeduplicationStore(final TcRedisCrudRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
    }

    /**
     * traceId 처리 이력이 존재하는지 확인합니다.
     *
     * @param traceId traceId
     * @param nowEpochMs 현재 시각(epoch millis), 인터페이스 규약 일관성을 위해 받지만 Redis 조회에서는 직접 사용하지 않습니다.
     * @return 이미 처리된 traceId이면 true
     */
    @Override
    public boolean isProcessed(final String traceId, final long nowEpochMs) {
        final String normalizedTraceId = normalize(traceId);
        if (normalizedTraceId == null) {
            return false;
        }

        final String redisKey = TRACE_KEY_PREFIX + normalizedTraceId;
        try {
            return repository.exists(redisKey);
        } catch (Exception ex) {
            /*
             * dedup 조회 실패로 전체 파이프라인이 중단되면 가용성이 떨어지므로
             * 보수적으로 "미처리(false)"로 간주해 계속 진행합니다.
             */
            log.warn("Redis traceId dedup lookup failed. key={}, traceId={}", redisKey, normalizedTraceId, ex);
            return false;
        }
    }

    /**
     * traceId를 처리 완료 상태로 기록합니다.
     *
     * @param traceId traceId
     * @param ttlMs TTL(ms)
     * @param nowEpochMs 현재 시각(epoch millis), 인터페이스 규약 일관성을 위해 전달받습니다.
     */
    @Override
    public void markProcessed(final String traceId, final long ttlMs, final long nowEpochMs) {
        final String normalizedTraceId = normalize(traceId);
        if (normalizedTraceId == null || ttlMs <= 0L) {
            return;
        }

        final String redisKey = TRACE_KEY_PREFIX + normalizedTraceId;
        final Duration ttl = Duration.ofMillis(ttlMs);
        try {
            final boolean inserted = repository.setIfAbsent(redisKey, nowEpochMs, ttl);
            if (log.isDebugEnabled()) {
                log.debug("Redis traceId mark processed. key={}, traceId={}, inserted={}, ttlMs={}",
                        redisKey,
                        normalizedTraceId,
                        inserted,
                        ttlMs);
            }
        } catch (Exception ex) {
            /*
             * dedup 기록 실패는 처리 흐름을 끊지 않고 운영 로그로만 남깁니다.
             * (요청 처리 자체는 계속 진행)
             */
            log.warn("Redis traceId dedup mark failed. key={}, traceId={}, ttlMs={}",
                    redisKey,
                    normalizedTraceId,
                    ttlMs,
                    ex);
        }
    }

    /**
     * traceId를 trim + null-safe로 정규화합니다.
     */
    private static String normalize(final String traceId) {
        if (traceId == null) {
            return null;
        }
        final String normalized = traceId.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}

