package com.nori.tc.common.kafka.processing;

import java.util.Objects;

/**
 * 고정 백오프 기반 재시도 정책입니다.
 *
 * <p>규칙:</p>
 * <p>1) {@code currentAttempt < maxAttempts} 인 동안 재시도</p>
 * <p>2) 한도 도달 시 재시도 중단</p>
 */
public final class FixedRetryPolicy implements RetryPolicy {

    private final int maxAttempts;
    private final long backoffMs;

    /**
     * 고정 재시도 정책을 생성합니다.
     *
     * @param maxAttempts 최초 실행을 포함한 최대 시도 횟수
     * @param backoffMs 재시도 전 대기 시간(ms)
     */
    public FixedRetryPolicy(final int maxAttempts, final long backoffMs) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        if (backoffMs < 0L) {
            throw new IllegalArgumentException("backoffMs must be >= 0");
        }
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    /**
     * 현재 실패 시도 횟수에 대해 재시도 여부를 평가합니다.
     *
     * @param currentAttempt 현재까지의 실패 시도 횟수(1 이상)
     * @param failure 실패 원인 예외
     * @return 재시도 의사결정
     */
    @Override
    public RetryDecision evaluate(final int currentAttempt, final Throwable failure) {
        if (currentAttempt <= 0) {
            throw new IllegalArgumentException("currentAttempt must be >= 1");
        }
        Objects.requireNonNull(failure, "failure is null");

        if (currentAttempt < maxAttempts) {
            return RetryDecision.retryAfter(backoffMs);
        }
        return RetryDecision.noRetry();
    }

    /**
     * 설정된 최대 시도 횟수를 반환합니다.
     *
     * @return 최대 시도 횟수
     */
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * 설정된 고정 백오프 시간을 반환합니다.
     *
     * @return 백오프 시간(ms)
     */
    public long backoffMs() {
        return backoffMs;
    }
}
