package com.nori.tc.comm.adapters.kafka.ui;

import com.nori.tc.comm.gateway.config.props.GatewayUiTaskPolicyProperties;
import com.nori.tc.common.task.execution.pipeline.port.KafkaTaskDeduplicationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UI Task traceId 중복 처리를 담당하는 in-memory 저장소입니다.
 *
 * <p>동작 방식:</p>
 * <p>1) traceId마다 만료 시각(expiry epoch ms)을 저장합니다.</p>
 * <p>2) 조회/등록 시 만료된 엔트리를 정리(cleanup)합니다.</p>
 * <p>3) 최대 크기 초과 시 가장 먼저 만료될 항목을 우선 축출합니다.</p>
 */
@Component
public class UiTraceIdDeduplicationStore implements KafkaTaskDeduplicationStore {

    private static final Logger log = LoggerFactory.getLogger(UiTraceIdDeduplicationStore.class);

    /** traceId -> 만료 시각(epoch ms) 매핑입니다. */
    private final ConcurrentHashMap<String, Long> processedTraceExpiryById = new ConcurrentHashMap<>();

    /** 저장 가능한 최대 traceId 개수입니다. */
    private final int maxCacheSize;

    /**
     * 정책 설정에서 dedup 캐시 크기를 읽어 초기화합니다.
     *
     * @param policyProperties UI Task 정책 설정
     */
    public UiTraceIdDeduplicationStore(final GatewayUiTaskPolicyProperties policyProperties) {
        Objects.requireNonNull(policyProperties, "policyProperties is null");
        this.maxCacheSize = policyProperties.getDuplicateTraceMaxSize();
    }

    /**
     * traceId가 이미 처리된 메시지인지 확인합니다.
     *
     * @param traceId 확인할 traceId
     * @param nowEpochMs 현재 시각(epoch ms)
     * @return 처리된 메시지면 true, 아니면 false
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
     * traceId를 처리 완료 상태로 등록합니다.
     *
     * @param traceId 등록할 traceId
     * @param ttlMs 보존 시간(ms)
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
            log.debug(
                    "TraceId marked as processed. traceId={}, expiryAt={}, cacheSize={}, maxCacheSize={}",
                    normalized,
                    expiryAt,
                    processedTraceExpiryById.size(),
                    maxCacheSize
            );
        }
    }

    /**
     * 만료된 traceId 엔트리를 제거합니다.
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
     * 캐시 용량이 가득 찬 경우 축출 대상을 선정해 1건 제거합니다.
     *
     * <p>우선순위:</p>
     * <p>1) 이미 만료된 항목</p>
     * <p>2) 가장 빨리 만료될 항목</p>
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
            log.debug(
                    "TraceId cache entry evicted by capacity limit. evictedTraceId={}, cacheSize={}, maxCacheSize={}",
                    evictionTarget,
                    processedTraceExpiryById.size(),
                    maxCacheSize
            );
        }
    }
}

