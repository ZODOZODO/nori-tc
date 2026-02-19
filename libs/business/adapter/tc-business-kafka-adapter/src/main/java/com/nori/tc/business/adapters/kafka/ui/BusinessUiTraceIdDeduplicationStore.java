package com.nori.tc.business.adapters.kafka.ui;

import com.nori.tc.business.adapters.kafka.config.BusinessUiTaskPolicyProperties;
import com.nori.tc.common.kafka.task.pipeline.KafkaTaskDeduplicationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Business UI traceId 중복 방지를 위한 in-memory 저장소 구현입니다.
 *
 * <p>요구사항:</p>
 * <p>1) 동일 traceId의 재처리를 방지합니다.</p>
 * <p>2) TTL 기반 만료를 지원합니다.</p>
 * <p>3) maxSize 상한으로 캐시 메모리 사용량을 제한합니다.</p>
 */
@Component
public class BusinessUiTraceIdDeduplicationStore implements KafkaTaskDeduplicationStore {

    /**
     * 저장소 동작 로그를 출력하는 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(BusinessUiTraceIdDeduplicationStore.class);

    /**
     * traceId -> expiresAt(epoch ms) 매핑입니다.
     */
    private final Map<String, Long> expiresAtByTraceId = new ConcurrentHashMap<>();

    /**
     * mark 호출 누적 횟수입니다.
     *
     * <p>주기적 만료 정리 트리거로 사용합니다.</p>
     */
    private final AtomicLong markCount = new AtomicLong(0L);

    /**
     * dedup 캐시 최대 엔트리 수입니다.
     */
    private final int maxCacheSize;

    /**
     * UI task 정책 프로퍼티를 주입받습니다.
     *
     * @param policyProperties Business UI task 정책 프로퍼티
     */
    public BusinessUiTraceIdDeduplicationStore(final BusinessUiTaskPolicyProperties policyProperties) {
        Objects.requireNonNull(policyProperties, "policyProperties is null");
        this.maxCacheSize = policyProperties.getDuplicateTraceMaxSize();
    }

    /**
     * traceId가 이미 처리된 이력인지 확인합니다.
     *
     * @param traceId 확인 대상 traceId
     * @param nowEpochMs 현재 시각(epoch ms)
     * @return true면 중복 traceId, false면 신규 traceId
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
     * traceId를 처리 완료 상태로 기록합니다.
     *
     * @param traceId 처리 완료된 traceId
     * @param ttlMs dedup 유지 시간(ms)
     * @param nowEpochMs 현재 시각(epoch ms)
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

        /*
         * 만료 정리 비용을 모든 요청에 분산시키지 않기 위해,
         * 256건마다 한 번씩 일괄 정리를 수행합니다.
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
     * 만료된 traceId 엔트리를 정리합니다.
     *
     * @param nowEpochMs 현재 시각(epoch ms)
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
     * 캐시가 상한에 도달하면 가장 오래된 엔트리 1건을 제거해 삽입 여유를 확보합니다.
     *
     * @param nowEpochMs 현재 시각(epoch ms)
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
     * 입력 문자열을 trim 후 null-safe 형태로 정규화합니다.
     *
     * @param value 입력 문자열
     * @return 공백/빈 문자열이면 null, 아니면 trim된 문자열
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

