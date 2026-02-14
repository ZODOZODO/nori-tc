package com.nori.tc.common.kafka.processing;

/**
 * 재시도 정책 인터페이스입니다.
 *
 * <p>{@code currentAttempt}는 현재 실패를 포함한 누적 실패 횟수입니다.
 * 예: 첫 실패 발생 시 {@code currentAttempt = 1}</p>
 */
@FunctionalInterface
public interface RetryPolicy {

    /**
     * 재시도 지속 여부를 평가합니다.
     *
     * @param currentAttempt 현재 실패 시도 횟수(1 이상)
     * @param failure 실패 원인
     * @return 재시도 결정 결과
     */
    RetryDecision evaluate(int currentAttempt, Throwable failure);
}
