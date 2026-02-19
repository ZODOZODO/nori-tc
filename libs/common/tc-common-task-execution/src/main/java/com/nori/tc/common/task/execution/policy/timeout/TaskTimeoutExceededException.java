package com.nori.tc.common.task.execution.policy.timeout;

/**
 * 태스크 실행이 timeout 기준을 초과했을 때 발생하는 런타임 예외입니다.
 *
 * <p>이 예외는 원본 InterruptedException/실행 예외를 timeout 도메인 예외로
 * 표준화해 상위 계층의 정책 판단을 단순화합니다.</p>
 */
public class TaskTimeoutExceededException extends RuntimeException {

    /**
     * timeout 값만 포함한 예외를 생성합니다.
     *
     * @param timeoutMs timeout 기준(ms)
     */
    public TaskTimeoutExceededException(final long timeoutMs) {
        super("Task execution timed out after " + timeoutMs + " ms");
    }

    /**
     * 원인 예외를 포함한 timeout 예외를 생성합니다.
     *
     * @param timeoutMs timeout 기준(ms)
     * @param cause 원인 예외
     */
    public TaskTimeoutExceededException(final long timeoutMs, final Throwable cause) {
        super("Task execution timed out after " + timeoutMs + " ms", cause);
    }
}
