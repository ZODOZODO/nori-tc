package com.nori.tc.common.mailbox.execution;

import com.nori.tc.common.mailbox.Mailbox;
import com.nori.tc.common.mailbox.MailboxScheduler;
import com.nori.tc.common.mailbox.MailboxTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link MailboxExecutionRuntime} 동작 검증 테스트입니다.
 */
class MailboxExecutionRuntimeTest {

    /**
     * direct 모드에서 동일 routingKey task가 순차 처리되고 release가 보장되는지 검증합니다.
     */
    @Test
    void shouldProcessTasksSequentiallyInDirectMode() throws Exception {
        final MailboxScheduler<TestTask> scheduler = new MailboxScheduler<>(8);
        final List<String> processed = new CopyOnWriteArrayList<>();
        final CountDownLatch done = new CountDownLatch(2);

        final MailboxExecutionRuntime<TestTask> runtime = new MailboxExecutionRuntime<>(
                "test-runtime-direct",
                scheduler,
                MailboxExecutionRuntime.Config.direct(1, 2_000L, "test-runtime-direct-dispatcher-"),
                task -> {
                    processed.add(task.value());
                    done.countDown();
                }
        );

        runtime.start();
        try {
            final long now = System.currentTimeMillis();
            Assertions.assertTrue(scheduler.enqueue(new TestTask("EQP-01", "A"), now));
            Assertions.assertTrue(scheduler.enqueue(new TestTask("EQP-01", "B"), now + 1));

            Assertions.assertTrue(done.await(3, TimeUnit.SECONDS), "모든 task가 시간 내 처리되어야 합니다.");
            Assertions.assertEquals(List.of("A", "B"), processed);

            final Mailbox<TestTask> mailbox = scheduler.getMailbox("EQP-01");
            Assertions.assertNotNull(mailbox);
            Assertions.assertFalse(mailbox.inFlightFlag().get(), "처리 후 inFlight는 반드시 해제되어야 합니다.");
        } finally {
            runtime.stop();
        }
    }

    /**
     * task 처리 예외가 발생해도 failure 훅이 호출되고 다음 task 처리가 계속되는지 검증합니다.
     */
    @Test
    void shouldInvokeFailureHandlerAndContinueProcessing() throws Exception {
        final MailboxScheduler<TestTask> scheduler = new MailboxScheduler<>(8);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);
        final CountDownLatch done = new CountDownLatch(2);

        final MailboxExecutionRuntime<TestTask> runtime = new MailboxExecutionRuntime<>(
                "test-runtime-failure",
                scheduler,
                MailboxExecutionRuntime.Config.direct(1, 2_000L, "test-runtime-failure-dispatcher-"),
                task -> {
                    if ("FAIL".equals(task.value())) {
                        throw new IllegalStateException("expected failure");
                    }
                    successCount.incrementAndGet();
                    done.countDown();
                },
                null,
                (task, ex) -> {
                    failureCount.incrementAndGet();
                    done.countDown();
                },
                null
        );

        runtime.start();
        try {
            final long now = System.currentTimeMillis();
            Assertions.assertTrue(scheduler.enqueue(new TestTask("EQP-02", "FAIL"), now));
            Assertions.assertTrue(scheduler.enqueue(new TestTask("EQP-02", "OK"), now + 1));

            Assertions.assertTrue(done.await(3, TimeUnit.SECONDS), "실패/성공 이벤트가 모두 발생해야 합니다.");
            Assertions.assertEquals(1, failureCount.get());
            Assertions.assertEquals(1, successCount.get());
        } finally {
            runtime.stop();
        }
    }

    /**
     * 테스트용 mailbox task 모델입니다.
     *
     * @param routingKey mailbox 라우팅 키
     * @param value 검증용 값
     */
    private record TestTask(
            String routingKey,
            String value
    ) implements MailboxTask {

        /**
         * record 생성 시 입력 유효성을 검증합니다.
         */
        private TestTask {
            if (routingKey == null || routingKey.isBlank()) {
                throw new IllegalArgumentException("routingKey is required");
            }
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("value is required");
            }
        }
    }
}
