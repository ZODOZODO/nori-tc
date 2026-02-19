package com.nori.tc.common.kafka.processing;

/**
 * 재시도 평가 결과 모델입니다.
 *
 * @param shouldRetry 다음 시도를 예약해야 하는지 여부
 * @param backoffMs 재시도 전 대기 시간(ms)
 */
public record RetryDecision(
        boolean shouldRetry,
        long backoffMs
) {

    /**
     * 재시도 결과의 유효성을 검증합니다.
     */
    public RetryDecision {
        if (backoffMs < 0L) {
            throw new IllegalArgumentException("backoffMs must be >= 0");
        }
    }

    /**
     * 재시도 수행 결정을 생성합니다.
     *
     * @param backoffMs 다음 시도 전 대기 시간(ms)
     * @return 재시도 결정
     */
    public static RetryDecision retryAfter(final long backoffMs) {
        return new RetryDecision(true, backoffMs);
    }

    /**
     * 재시도 없이 종료하는 결정을 생성합니다.
     *
     * @return 비재시도 결정
     */
    public static RetryDecision noRetry() {
        return new RetryDecision(false, 0L);
    }
}
