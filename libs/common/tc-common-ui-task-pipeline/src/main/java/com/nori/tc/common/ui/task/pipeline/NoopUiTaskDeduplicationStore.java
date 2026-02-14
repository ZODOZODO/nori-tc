package com.nori.tc.common.ui.task.pipeline;

/**
 * 아무 동작도 하지 않는 중복 저장소 기본 구현입니다.
 */
public final class NoopUiTaskDeduplicationStore implements UiTaskDeduplicationStore {

    /**
     * 항상 false를 반환하여 중복 차단을 수행하지 않습니다.
     *
     * @param traceId 추적 ID
     * @param nowEpochMs 현재 시각(epoch millis)
     * @return false
     */
    @Override
    public boolean isProcessed(final String traceId, final long nowEpochMs) {
        return false;
    }

    /**
     * 처리 완료 기록 요청을 무시합니다.
     *
     * @param traceId 추적 ID
     * @param ttlMs 만료 시간(ms)
     * @param nowEpochMs 현재 시각(epoch millis)
     */
    @Override
    public void markProcessed(final String traceId, final long ttlMs, final long nowEpochMs) {
        // intentionally no-op
    }
}
