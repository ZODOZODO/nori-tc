package com.nori.tc.business.adapters.kafka.ui;

import com.nori.tc.common.ui.task.pipeline.UiTaskDeduplicationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UI traceId 중복 방지 저장소의 in-memory 구현입니다.
 *
 * <p>요구사항:
 * - 동일 traceId 요청 재처리 방지
 * - TTL 기반 자동 만료
 * - 외부 저장소 없이 JVM 메모리만 사용</p>
 */
@Component
public class BusinessUiTraceIdDeduplicationStore implements UiTaskDeduplicationStore {

    private static final Logger log = LoggerFactory.getLogger(BusinessUiTraceIdDeduplicationStore.class);

    /**
     * traceId -> expiresAt(epoch ms) 매핑입니다.
     */
    private final Map<String, Long> expiresAtByTraceId = new ConcurrentHashMap<>();

    /**
     * 마킹 요청 카운터입니다.
     *
     * <p>주기적으로 만료 엔트리를 정리하기 위한 트리거로 사용합니다.</p>
     */
    private final AtomicLong markCount = new AtomicLong(0L);

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

    @Override
    public void markProcessed(final String traceId, final long ttlMs, final long nowEpochMs) {
        final String normalizedTraceId = normalize(traceId);
        if (normalizedTraceId == null || ttlMs <= 0L) {
            return;
        }

        final long expiresAt = nowEpochMs + ttlMs;
        expiresAtByTraceId.put(normalizedTraceId, expiresAt);

        /*
         * 정리 비용을 매 요청마다 지불하지 않기 위해
         * 256건 단위로 만료 엔트리를 일괄 정리합니다.
         */
        if ((markCount.incrementAndGet() & 0xFFL) == 0L) {
            cleanupExpired(nowEpochMs);
        }

        if (log.isDebugEnabled()) {
            log.debug("UI traceId marked. traceId={}, expiresAt={}, cacheSize={}",
                    normalizedTraceId,
                    expiresAt,
                    expiresAtByTraceId.size());
        }
    }

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

