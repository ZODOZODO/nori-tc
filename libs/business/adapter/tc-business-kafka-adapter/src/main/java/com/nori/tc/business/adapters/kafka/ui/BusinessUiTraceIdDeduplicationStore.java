package com.nori.tc.business.adapters.kafka.ui;

import com.nori.tc.business.adapters.kafka.config.BusinessUiTaskPolicyProperties;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDeduplicationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BusinessUiTraceIdDeduplicationStore 클래스입니다.
 *
 * <p>해당 모듈에서 공통 계약과 동작 경계를 정의하며,
 * 호출 계층에서 일관된 사용이 가능하도록 설계되었습니다.</p>
 */
@Component
public class BusinessUiTraceIdDeduplicationStore implements KafkaTaskDeduplicationStore {

    /**
     * log 필드입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(BusinessUiTraceIdDeduplicationStore.class);

    /**
     * expiresAtByTraceId 필드입니다.
     */
    private final Map<String, Long> expiresAtByTraceId = new ConcurrentHashMap<>();

    /**
     * markCount 필드입니다.
     */
    private final AtomicLong markCount = new AtomicLong(0L);

    /**
     * maxCacheSize 필드입니다.
     */
    private final int maxCacheSize;

    /**
     * UI task ?뺤콉 ?꾨줈?쇳떚瑜?二쇱엯諛쏆뒿?덈떎.
     *
     * @param policyProperties Business UI task ?뺤콉 ?꾨줈?쇳떚
     */
    public BusinessUiTraceIdDeduplicationStore(final BusinessUiTaskPolicyProperties policyProperties) {
        Objects.requireNonNull(policyProperties, "policyProperties is null");
        this.maxCacheSize = policyProperties.getDuplicateTraceMaxSize();
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

        final Long expiresAt = expiresAtByTraceId.get(normalizedTraceId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < nowEpochMs) {
            expiresAtByTraceId.remove(normalizedTraceId, expiresAt);
            return false;
        }
        return true;
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

        ensureCapacity(nowEpochMs);

        final long expiresAt = nowEpochMs + ttlMs;
        expiresAtByTraceId.put(normalizedTraceId, expiresAt);

        /**
         * UTF-8 형식으로 정리된 주석입니다.
         */
        if ((markCount.incrementAndGet() & 0xFFL) == 0L) {
            cleanupExpired(nowEpochMs);
        }

        if (log.isDebugEnabled()) {
            log.debug("UI traceId marked. traceId={}, expiresAt={}, cacheSize={}, maxCacheSize={}",
                    normalizedTraceId,
                    expiresAt,
                    expiresAtByTraceId.size(),
                    maxCacheSize);
        }
    }

    /**
     * cleanupExpired 기능을 수행합니다.
     *
     * @param nowEpochMs 입력 값
     */
    private void cleanupExpired(final long nowEpochMs) {
        int removed = 0;
        for (Map.Entry<String, Long> entry : expiresAtByTraceId.entrySet()) {
            final Long expiresAt = entry.getValue();
            if (expiresAt != null && expiresAt < nowEpochMs) {
                if (expiresAtByTraceId.remove(entry.getKey(), expiresAt)) {
                    removed++;
                }
            }
        }
        if (removed > 0 && log.isDebugEnabled()) {
            log.debug("Expired UI traceId entries cleaned. removed={}, remaining={}",
                    removed,
                    expiresAtByTraceId.size());
        }
    }

    /**
     * ensureCapacity 기능을 수행합니다.
     *
     * @param nowEpochMs 입력 값
     */
    private void ensureCapacity(final long nowEpochMs) {
        if (expiresAtByTraceId.size() < maxCacheSize) {
            return;
        }

        cleanupExpired(nowEpochMs);
        if (expiresAtByTraceId.size() < maxCacheSize) {
            return;
        }

        String evictionTarget = null;
        long oldestExpiry = Long.MAX_VALUE;

        for (Map.Entry<String, Long> entry : expiresAtByTraceId.entrySet()) {
            final String traceId = entry.getKey();
            final Long expiresAt = entry.getValue();
            if (traceId == null) {
                continue;
            }
            if (expiresAt == null || expiresAt < oldestExpiry) {
                oldestExpiry = expiresAt == null ? Long.MIN_VALUE : expiresAt;
                evictionTarget = traceId;
            }
        }

        if (evictionTarget != null && expiresAtByTraceId.remove(evictionTarget) != null && log.isDebugEnabled()) {
            log.debug("UI traceId entry evicted by capacity limit. evictedTraceId={}, cacheSize={}, maxCacheSize={}",
                    evictionTarget,
                    expiresAtByTraceId.size(),
                    maxCacheSize);
        }
    }

    /**
     * normalize 기능을 수행합니다.
     *
     * @param value 입력 값
     * @return 처리 결과
     */
    private static String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}


