package com.nori.tc.common.task.execution.pipeline.port;

/**
 * traceId 중복 처리를 위한 저장소 인터페이스입니다.
 *
 * <p>구현체는 최소 다음 규칙을 만족해야 합니다.</p>
 * <p>1) `isProcessed`는 현재 시각 기준으로 TTL 만료를 반영해야 합니다.</p>
 * <p>2) `markProcessed`는 동일 traceId 재수신 시 중복 판별 가능해야 합니다.</p>
 * <p>3) 스레드 안전성이 보장되어야 합니다.</p>
 */
public interface KafkaTaskDeduplicationStore {

    /**
     * 전달된 traceId가 이미 처리된 값인지 확인합니다.
     *
     * @param traceId 대상 traceId
     * @param nowEpochMs 현재 시각(epoch millis)
     * @return 이미 처리된 값이면 true
     */
    boolean isProcessed(String traceId, long nowEpochMs);

    /**
     * traceId를 처리 완료 상태로 저장합니다.
     *
     * @param traceId 대상 traceId
     * @param ttlMs 보관 TTL(ms)
     * @param nowEpochMs 현재 시각(epoch millis)
     */
    void markProcessed(String traceId, long ttlMs, long nowEpochMs);

    /**
     * 중복 검사를 사용하지 않는 환경을 위한 no-op 구현을 제공합니다.
     *
     * @return no-op 저장소 구현
     */
    static KafkaTaskDeduplicationStore noop() {
        return new KafkaTaskDeduplicationStore() {
            /**
             * no-op 구현은 항상 미처리(false)를 반환합니다.
             */
            @Override
            public boolean isProcessed(final String traceId, final long nowEpochMs) {
                return false;
            }

            /**
             * no-op 구현은 상태를 저장하지 않습니다.
             */
            @Override
            public void markProcessed(final String traceId, final long ttlMs, final long nowEpochMs) {
                // intentionally no-op
            }
        };
    }
}
