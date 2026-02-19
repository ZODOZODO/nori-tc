package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskDeduplicationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway UI task traceId 중복 방지를 위한 in-memory 저장소입니다.
 *
 * <p>핵심 목적:</p>
 * <p>1) 동일 traceId가 TTL 내에 재수신되면 중복으로 간주하여 처리 중복을 방지합니다.</p>
 * <p>2) 캐시 엔트리 수 상한(maxSize)을 적용해 메모리 사용량이 무한히 증가하지 않도록 제한합니다.</p>
 */
@Component
public class UiTraceIdDeduplicationStore implements KafkaTaskDeduplicationStore {

    /**
     * 저장소 동작 로그를 출력하기 위한 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(UiTraceIdDeduplicationStore.class);

    /**
     * traceId -> 만료시각(epoch ms) 매핑입니다.
     */
    private final ConcurrentHashMap<String, Long> processedTraceExpiryById = new ConcurrentHashMap<>();

    /**
     * 중복 trace 캐시 최대 엔트리 수입니다.
     */
    private final int maxCacheSize;

    /**
     * UI task 정책으로부터 dedup 캐시 상한을 주입받습니다.
     *
     * @param policyProperties Gateway UI task 정책 프로퍼티
     */
    public UiTraceIdDeduplicationStore(final GatewayUiTaskPolicyProperties policyProperties) {
        Objects.requireNonNull(policyProperties, "policyProperties is null");
        this.maxCacheSize = policyProperties.getDuplicateTraceMaxSize();
    }

    /**
     * 주어진 traceId가 아직 유효한 중복 이력인지 확인합니다.
     *
     * @param traceId 확인할 traceId
     * @param nowEpochMs 현재 시각(epoch ms)
     * @return true면 이미 처리된 traceId, false면 신규 traceId
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
     * @param ttlMs dedup 유지 시간(ms)
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
        ensureCapacity(nowEpochMs);

        final String normalized = traceId.trim();
        final long expiryAt = nowEpochMs + ttlMs;
        processedTraceExpiryById.put(normalized, expiryAt);

        if (log.isDebugEnabled()) {
            log.debug("TraceId marked as processed. traceId={}, expiryAt={}, cacheSize={}, maxCacheSize={}",
                    normalized,
                    expiryAt,
                    processedTraceExpiryById.size(),
                    maxCacheSize);
        }
    }

    /**
     * 현재 시각 기준으로 만료된 엔트리를 정리합니다.
     *
     * @param nowEpochMs 현재 시각(epoch ms)
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

    /**
     * 캐시가 상한에 도달한 경우, 가장 먼저 만료될 가능성이 높은 엔트리 1건을 제거합니다.
     *
     * @param nowEpochMs 현재 시각(epoch ms)
     */
    private void ensureCapacity(final long nowEpochMs) {
        if (processedTraceExpiryById.size() < maxCacheSize) {
            return;
        }

        String evictionTarget = null;
        long oldestExpiry = Long.MAX_VALUE;

        for (Map.Entry<String, Long> entry : processedTraceExpiryById.entrySet()) {
            final String traceId = entry.getKey();
            final Long expiry = entry.getValue();
            if (traceId == null) {
                continue;
            }

            if (expiry == null || expiry <= nowEpochMs) {
                evictionTarget = traceId;
                break;
            }

            if (expiry < oldestExpiry) {
                oldestExpiry = expiry;
                evictionTarget = traceId;
            }
        }

        if (evictionTarget != null && processedTraceExpiryById.remove(evictionTarget) != null && log.isDebugEnabled()) {
            log.debug("TraceId cache entry evicted by capacity limit. evictedTraceId={}, cacheSize={}, maxCacheSize={}",
                    evictionTarget,
                    processedTraceExpiryById.size(),
                    maxCacheSize);
        }
    }
}

