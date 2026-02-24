package com.nori.tc.common.consumer.runtime;

/**
 * 재시도 정책 계산 인터페이스입니다.
 *
 * <p>{@code currentAttempt}는 현재 실패를 포함한 누적 실패 횟수이며,
 * 첫 실패 발생 시 {@code 1}부터 시작합니다.</p>
 */
@FunctionalInterface
public interface RetryPolicy {

    /**
     * 현재 실패 횟수와 예외를 기반으로 재시도 여부/대기 시간을 계산합니다.
     *
     * @param currentAttempt 현재 실패 시도 횟수(1 이상)
     * @param failure        실패 원인
     * @return 재시도 판단 결과
     */
    RetryDecision evaluate(int currentAttempt, Throwable failure);
}
