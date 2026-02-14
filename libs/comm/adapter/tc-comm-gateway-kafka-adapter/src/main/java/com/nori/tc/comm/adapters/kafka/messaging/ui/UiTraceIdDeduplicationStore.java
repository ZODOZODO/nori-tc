package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.common.ui.task.pipeline.UiTaskDeduplicationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UI task traceId 중복 처리용 인메모리 저장소입니다.
 *
 * <p>동일 traceId가 TTL 내에 재수신되면 비즈니스 로직을 재실행하지 않고
 * 중복 요청으로 간주해 스킵할 수 있도록 지원합니다.</p>
 *
 * <p>주의: 인메모리 저장소이므로 프로세스 재기동 시 이력은 초기화됩니다.</p>
 */
@Component
public class UiTraceIdDeduplicationStore implements UiTaskDeduplicationStore {

    private static final Logger log = LoggerFactory.getLogger(UiTraceIdDeduplicationStore.class);

    /**
     * traceId -> 만료시각(epoch ms) 저장 맵
     */
    private final ConcurrentHashMap<String, Long> processedTraceExpiryById = new ConcurrentHashMap<>();

    /**
     * traceId가 이미 처리된 이력인지 확인합니다.
     *
     * @param traceId 확인할 traceId
     * @param nowEpochMs 현재 시각(epoch ms)
     * @return true면 중복(이미 처리됨), false면 신규
     */
    @Override
    public boolean isProcessed(final String traceId, final long nowEpochMs) {
        if (traceId == null || traceId.isBlank()) {
            return false;
        }
        cleanupExpired(nowEpochMs);
        final Long expiry = processedTraceExpiryById.get(traceId.trim());
        return expiry != null && expiry > nowEpochMs;
    }

    /**
     * traceId를 처리 완료 이력으로 기록합니다.
     *
     * @param traceId 처리 완료된 traceId
     * @param ttlMs 만료 TTL(ms)
     * @param nowEpochMs 현재 시각(epoch ms)
     */
    @Override
    public void markProcessed(final String traceId, final long ttlMs, final long nowEpochMs) {
        Objects.requireNonNull(traceId, "traceId is null");
        if (traceId.isBlank()) {
            return;
        }
        if (ttlMs <= 0L) {
            throw new IllegalArgumentException("ttlMs must be > 0");
        }

        cleanupExpired(nowEpochMs);
        final String normalized = traceId.trim();
        final long expiryAt = nowEpochMs + ttlMs;
        processedTraceExpiryById.put(normalized, expiryAt);

        if (log.isDebugEnabled()) {
            log.debug("TraceId marked as processed. traceId={}, expiryAt={}, cacheSize={}",
                    normalized, expiryAt, processedTraceExpiryById.size());
        }
    }

    /**
     * 만료된 traceId 이력을 정리합니다.
     *
     * <p>별도 스케줄러 없이 요청 처리 시점에 기회 정리를 수행합니다.</p>
     */
    private void cleanupExpired(final long nowEpochMs) {
        final Iterator<Map.Entry<String, Long>> iterator = processedTraceExpiryById.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() == null || entry.getValue() <= nowEpochMs) {
                iterator.remove();
            }
        }
    }
}
