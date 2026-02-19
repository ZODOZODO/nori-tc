package com.nori.tc.common.task.execution.policy.timeout;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@link TimeoutBoundRunner} 동작 검증 테스트입니다.
 */
class TimeoutBoundRunnerTest {

    /**
     * 작업이 timeout 이전에 끝나면 정상 결과를 반환하는지 검증합니다.
     */
    @Test
    void shouldReturnResultWhenActionCompletesBeforeTimeout() throws Exception {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            final TimeoutBoundRunner runner = new TimeoutBoundRunner(scheduler, 300L);
            final String result = runner.run(() -> {
                Thread.sleep(50L);
                return "OK";
            });

            Assertions.assertEquals("OK", result);
        } finally {
            scheduler.shutdownNow();
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * 작업이 timeout을 넘기면 TaskTimeoutExceededException이 발생하는지 검증합니다.
     */
    @Test
    void shouldThrowTimeoutExceptionWhenActionExceedsTimeout() throws Exception {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            final TimeoutBoundRunner runner = new TimeoutBoundRunner(scheduler, 60L);

            Assertions.assertThrows(TaskTimeoutExceededException.class, () ->
                    runner.run(() -> {
                        Thread.sleep(500L);
                        return "NEVER";
                    })
            );
        } finally {
            scheduler.shutdownNow();
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        }
    }
}