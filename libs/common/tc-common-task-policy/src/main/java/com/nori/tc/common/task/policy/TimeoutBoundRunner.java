package com.nori.tc.common.task.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

/**
 * timeout interrupt 정책을 적용해 작업을 실행하는 헬퍼입니다.
 *
 * <p>실행 스레드는 별도 worker를 만들지 않고 현재 호출 스레드를 사용합니다.
 * timeout 시 scheduler가 현재 스레드에 interrupt를 걸고, 실행 결과는
 * {@link TaskTimeoutExceededException}으로 표준화합니다.</p>
 */
public final class TimeoutBoundRunner {

    private static final Logger log = LoggerFactory.getLogger(TimeoutBoundRunner.class);

    private final ScheduledExecutorService timeoutScheduler;
    private final long timeoutMs;

    /**
     * timeout 실행기를 생성합니다.
     *
     * @param timeoutScheduler timeout interrupt 스케줄러
     * @param timeoutMs 제한 시간(ms)
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
     * timeout 정책을 적용해 작업을 실행합니다.
     *
     * @param action 실행할 작업
     * @param <T> 반환 타입
     * @return 작업 결과
     * @throws Exception 작업 원본 예외 또는 timeout 예외
     */
    public <T> T run(final InterruptibleAction<T> action) throws Exception {
        Objects.requireNonNull(action, "action is null");

        try (InterruptTimeoutGuard guard = InterruptTimeoutGuard.start(timeoutScheduler, timeoutMs)) {
            if (log.isDebugEnabled()) {
                log.debug("timeout 제한 실행을 시작합니다. timeoutMs={}", timeoutMs);
            }
            try {
                final T result = action.execute();
                if (guard.isTimeoutTriggered()) {
                    if (log.isInfoEnabled()) {
                        log.info("작업이 완료되었지만 timeout이 이미 트리거되어 timeout 예외로 변환합니다. timeoutMs={}", timeoutMs);
                    }
                    throw new TaskTimeoutExceededException(timeoutMs);
                }
                return result;
            } catch (InterruptedException interrupted) {
                if (guard.isTimeoutTriggered()) {
                    if (log.isInfoEnabled()) {
                        log.info("interrupt를 timeout 예외로 변환합니다. timeoutMs={}", timeoutMs);
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
     * timeout 실행 대상 작업 함수형 계약입니다.
     *
     * @param <T> 반환 타입
     */
    @FunctionalInterface
    public interface InterruptibleAction<T> {

        /**
         * 작업을 실행합니다.
         *
         * @return 작업 결과
         * @throws Exception 실행 중 발생한 예외
         */
        T execute() throws Exception;
    }
}
