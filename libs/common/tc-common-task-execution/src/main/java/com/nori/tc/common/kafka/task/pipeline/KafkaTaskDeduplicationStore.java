package com.nori.tc.common.kafka.task.pipeline;

/**
 * traceId 중복 처리를 위한 저장소 계약입니다.
 */
public interface KafkaTaskDeduplicationStore {

    /**
     * 주어진 traceId가 이미 처리된 요청인지 확인합니다.
     *
     * @param traceId 추적 ID
     * @param nowEpochMs 현재 시각(epoch millis)
     * @return 이미 처리된 traceId면 true
     */
    boolean isProcessed(String traceId, long nowEpochMs);

    /**
     * traceId를 처리 완료 상태로 기록합니다.
     *
     * @param traceId 추적 ID
     * @param ttlMs 만료 시간(ms)
     * @param nowEpochMs 현재 시각(epoch millis)
     */
    void markProcessed(String traceId, long ttlMs, long nowEpochMs);
}


