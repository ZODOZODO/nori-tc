package com.nori.tc.common.task.execution.policy.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

/**
 * timeout 인터럽트 가드를 적용해 작업을 실행하는 러너입니다.
 *
 * <p>실행 중 timeout이 트리거되면 예외 유형을 `TaskTimeoutExceededException`으로
 * 통일해 상위 파이프라인이 동일한 정책으로 처리할 수 있게 합니다.</p>
 */
public final class TimeoutBoundRunner {

    private static final Logger log = LoggerFactory.getLogger(TimeoutBoundRunner.class);

    private final ScheduledExecutorService timeoutScheduler;
    private final long timeoutMs;

    /**
     * timeout 러너를 생성합니다.
     *
     * @param timeoutScheduler timeout 예약 스케줄러
     * @param timeoutMs timeout 기준(ms)
     */
    public TimeoutBoundRunner(
            final ScheduledExecutorService timeoutScheduler,
            final long timeoutMs
    ) {
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler is null");
        if (timeoutMs <= 0L) {
            throw new IllegalArgumentException("timeoutMs must be > 0");
        }
        this.timeoutMs = timeoutMs;
    }

    /**
     * timeout 가드 하에서 작업을 실행합니다.
     *
     * @param action 실행할 작업
     * @param <T> 반환 타입
     * @return 작업 결과
     * @throws Exception 작업 예외 또는 timeout 예외
     */
    public <T> T run(final InterruptibleAction<T> action) throws Exception {
        Objects.requireNonNull(action, "action is null");

        try (InterruptTimeoutGuard guard = InterruptTimeoutGuard.start(timeoutScheduler, timeoutMs)) {
            if (log.isDebugEnabled()) {
                log.debug("timeout 가드 실행을 시작합니다. timeoutMs={}", timeoutMs);
            }
            try {
                final T result = action.execute();
                if (guard.isTimeoutTriggered()) {
                    if (log.isInfoEnabled()) {
                        log.info("작업 완료 후 timeout 트리거가 확인되어 timeout 예외로 변환합니다. timeoutMs={}", timeoutMs);
                    }
                    throw new TaskTimeoutExceededException(timeoutMs);
                }
                return result;
            } catch (InterruptedException interrupted) {
                if (guard.isTimeoutTriggered()) {
                    if (log.isInfoEnabled()) {
                        log.info("InterruptedException을 timeout 예외로 변환합니다. timeoutMs={}", timeoutMs);
                    }
                    throw new TaskTimeoutExceededException(timeoutMs, interrupted);
                }
                Thread.currentThread().interrupt();
                throw interrupted;
            } catch (Exception ex) {
                if (guard.isTimeoutTriggered() && !(ex instanceof TaskTimeoutExceededException)) {
                    if (log.isInfoEnabled()) {
                        log.info("실행 예외를 timeout 예외로 변환합니다. timeoutMs={}, causeClass={}",
                                timeoutMs,
                                ex.getClass().getName());
                    }
                    throw new TaskTimeoutExceededException(timeoutMs, ex);
                }
                throw ex;
            }
        }
    }

    /**
     * timeout 러너에서 실행할 액션 함수형 계약입니다.
     *
     * @param <T> 반환 타입
     */
    @FunctionalInterface
    public interface InterruptibleAction<T> {

        /**
         * 작업을 실행합니다.
         *
         * @return 작업 결과
         * @throws Exception 작업 중 발생한 예외
         */
        T execute() throws Exception;
    }
}
