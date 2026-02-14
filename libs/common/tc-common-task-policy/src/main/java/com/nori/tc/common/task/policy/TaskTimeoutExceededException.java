package com.nori.tc.common.task.policy;

/**
 * timeout interrupt가 트리거되어 태스크 실행 제한을 초과했음을 나타내는 예외입니다.
 */
public class TaskTimeoutExceededException extends RuntimeException {

    /**
     * timeout 정보를 포함한 예외를 생성합니다.
     *
     * @param timeoutMs 제한 시간(ms)
     */
    public TaskTimeoutExceededException(final long timeoutMs) {
        super("Task execution timed out after " + timeoutMs + " ms");
    }

    /**
     * 원인 예외를 포함한 timeout 예외를 생성합니다.
     *
     * @param timeoutMs 제한 시간(ms)
     * @param cause 원인 예외
     */
    public TaskTimeoutExceededException(final long timeoutMs, final Throwable cause) {
        super("Task execution timed out after " + timeoutMs + " ms", cause);
    }
}

