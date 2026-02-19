package com.nori.tc.common.task.execution.policy.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 현재 스레드에 timeout 인터럽트를 예약하는 가드 객체입니다.
 *
 * <p>생성 후 close를 호출하면 예약된 인터럽트를 취소합니다.</p>
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
     * 현재 스레드 기준 timeout 인터럽트를 예약합니다.
     *
     * @param scheduler timeout 예약용 스케줄러
     * @param timeoutMs timeout 기준(ms)
     * @return timeout 가드
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
                        log.info("timeout 도달로 현재 스레드에 interrupt를 전송합니다. threadName={}, timeoutMs={}",
                                targetThread.getName(),
                                timeoutMs);
                    }
                    targetThread.interrupt();
                },
                timeoutMs,
                TimeUnit.MILLISECONDS
        );

        if (log.isDebugEnabled()) {
            log.debug("timeout 인터럽트 예약을 완료했습니다. threadName={}, timeoutMs={}",
                    targetThread.getName(),
                    timeoutMs);
        }
        return new InterruptTimeoutGuard(timeoutFuture, timeoutTriggered, timeoutMs);
    }

    /**
     * timeout이 실제로 트리거되었는지 반환합니다.
     */
    public boolean isTimeoutTriggered() {
        return timeoutTriggered.get();
    }

    /**
     * 설정된 timeout 기준(ms)을 반환합니다.
     */
    public long timeoutMs() {
        return timeoutMs;
    }

    /**
     * 예약된 timeout 인터럽트를 취소합니다.
     */
    @Override
    public void close() {
        final boolean canceled = timeoutFuture.cancel(false);
        if (log.isDebugEnabled()) {
            log.debug("timeout 인터럽트 예약을 해제했습니다. timeoutMs={}, timeoutTriggered={}, cancelResult={}",
                    timeoutMs,
                    timeoutTriggered.get(),
                    canceled);
        }
    }
}