package com.nori.tc.business.adapters.redis.ui;

import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDeduplicationStore;
import com.nori.tc.db.starter.redis.TcRedisCrudRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * RedisBusinessUiTraceIdDeduplicationStore 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */
@Primary
@Component
public class RedisBusinessUiTraceIdDeduplicationStore implements KafkaTaskDeduplicationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisBusinessUiTraceIdDeduplicationStore.class);
    private static final String TRACE_KEY_PREFIX = "tc:business:core:ui:trace:";

    private final TcRedisCrudRepository repository;

    /**
     * Redis CRUD ??μ냼瑜?二쇱엯諛쏆뒿?덈떎.
     */
    public RedisBusinessUiTraceIdDeduplicationStore(final TcRedisCrudRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
    }

    /**
     * isProcessed 기능을 수행합니다.
     *
     * @param traceId 입력 값
     * @param nowEpochMs 입력 값
     * @return 처리 결과
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
            /**
             * UTF-8 형식으로 정리된 주석입니다.
             */
            log.warn("Redis traceId dedup lookup failed. key={}, traceId={}", redisKey, normalizedTraceId, ex);
            return false;
        }
    }

    /**
     * markProcessed 기능을 수행합니다.
     *
     * @param traceId 입력 값
     * @param ttlMs 입력 값
     * @param nowEpochMs 입력 값
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
            /**
             * UTF-8 형식으로 정리된 주석입니다.
             */
            log.warn("Redis traceId dedup mark failed. key={}, traceId={}, ttlMs={}",
                    redisKey,
                    normalizedTraceId,
                    ttlMs,
                    ex);
        }
    }

    /**
     * normalize 기능을 수행합니다.
     *
     * @param traceId 입력 값
     * @return 처리 결과
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


