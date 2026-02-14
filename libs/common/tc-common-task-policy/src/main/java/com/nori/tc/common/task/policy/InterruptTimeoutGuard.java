package com.nori.tc.common.task.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 현재 실행 스레드에 timeout interrupt를 거는 가드입니다.
 *
 * <p>사용 패턴:</p>
 * <pre>{@code
 * try (InterruptTimeoutGuard guard = InterruptTimeoutGuard.start(scheduler, 180_000L)) {
 *     runTask();
 *     if (guard.isTimeoutTriggered()) {
 *         throw new TaskTimeoutExceededException(guard.timeoutMs());
 *     }
 * }
 * }</pre>
 */
public final class InterruptTimeoutGuard implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(InterruptTimeoutGuard.class);

    private final ScheduledFuture<?> timeoutFuture;
    private final AtomicBoolean timeoutTriggered;
    private final long timeoutMs;

    private InterruptTimeoutGuard(
            final ScheduledFuture<?> timeoutFuture,
            final AtomicBoolean timeoutTriggered,
            final long timeoutMs
    ) {
        this.timeoutFuture = Objects.requireNonNull(timeoutFuture, "timeoutFuture is null");
        this.timeoutTriggered = Objects.requireNonNull(timeoutTriggered, "timeoutTriggered is null");
        this.timeoutMs = timeoutMs;
    }

    /**
     * 현재 스레드 기준 timeout interrupt를 시작합니다.
     *
     * @param scheduler timeout 실행 스케줄러
     * @param timeoutMs 제한 시간(ms)
     * @return timeout guard
     */
    public static InterruptTimeoutGuard start(
            final ScheduledExecutorService scheduler,
            final long timeoutMs
    ) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        if (timeoutMs <= 0L) {
            throw new IllegalArgumentException("timeoutMs must be > 0");
        }

        final Thread targetThread = Thread.currentThread();
        final AtomicBoolean timeoutTriggered = new AtomicBoolean(false);
        final ScheduledFuture<?> timeoutFuture = scheduler.schedule(
                () -> {
                    timeoutTriggered.set(true);
                    if (log.isInfoEnabled()) {
                        log.info("작업 timeout이 발생하여 실행 스레드에 interrupt를 전송합니다. threadName={}, timeoutMs={}",
                                targetThread.getName(),
                                timeoutMs);
                    }
                    targetThread.interrupt();
                },
                timeoutMs,
                TimeUnit.MILLISECONDS
        );

        if (log.isDebugEnabled()) {
            log.debug("timeout 가드를 시작했습니다. threadName={}, timeoutMs={}",
                    targetThread.getName(),
                    timeoutMs);
        }
        return new InterruptTimeoutGuard(timeoutFuture, timeoutTriggered, timeoutMs);
    }

    /**
     * timeout trigger 여부를 반환합니다.
     *
     * @return timeout trigger 여부
     */
    public boolean isTimeoutTriggered() {
        return timeoutTriggered.get();
    }

    /**
     * 설정된 timeout 값을 반환합니다.
     *
     * @return timeout(ms)
     */
    public long timeoutMs() {
        return timeoutMs;
    }

    /**
     * 스케줄된 timeout interrupt 작업을 취소합니다.
     */
    @Override
    public void close() {
        final boolean canceled = timeoutFuture.cancel(false);
        if (log.isDebugEnabled()) {
            log.debug("timeout 가드를 종료했습니다. timeoutMs={}, timeoutTriggered={}, cancelResult={}",
                    timeoutMs,
                    timeoutTriggered.get(),
                    canceled);
        }
    }
}
