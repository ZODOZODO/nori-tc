package com.nori.tc.common.mailbox;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link MailboxScheduler} 핵심 알고리즘 검증 테스트입니다.
 *
 * <p>검증 대상은 다음과 같습니다.</p>
 * <p>1) 동일 라우팅 키에서 in-flight 단일 실행 제약이 지켜지는지</p>
 * <p>2) release 이후 잔여 작업이 있으면 재스케줄되는지</p>
 * <p>3) capacity 초과 시 drop 카운트가 증가하는지</p>
 */
class MailboxSchedulerTest {

    /**
     * 같은 키로 두 건을 enqueue 했을 때, 첫 작업 완료 후 둘째 작업이 재스케줄되는지 검증합니다.
     */
    @Test
    void shouldRescheduleWhenMailboxStillHasPendingTasks() throws Exception {
        final MailboxScheduler<TestTask> scheduler = new MailboxScheduler<>(8);
        final long now = System.currentTimeMillis();

        scheduler.enqueue(new TestTask("EQP-A", "task-1"), now);
        scheduler.enqueue(new TestTask("EQP-A", "task-2"), now + 1);

        final String firstReadyKey = scheduler.takeReadyKey();
        Assertions.assertEquals("EQP-A", firstReadyKey);

        final Mailbox<TestTask> mailbox = scheduler.tryAcquire(firstReadyKey);
        Assertions.assertNotNull(mailbox);
        Assertions.assertTrue(mailbox.inFlightFlag().get());

        final TestTask firstTask = mailbox.poll();
        Assertions.assertNotNull(firstTask);
        Assertions.assertEquals("task-1", firstTask.value());

        scheduler.release(mailbox);
        Assertions.assertFalse(mailbox.inFlightFlag().get());

        final String secondReadyKey = scheduler.takeReadyKey();
        Assertions.assertEquals("EQP-A", secondReadyKey);

        final Mailbox<TestTask> mailboxAgain = scheduler.tryAcquire(secondReadyKey);
        Assertions.assertNotNull(mailboxAgain);

        final TestTask secondTask = mailboxAgain.poll();
        Assertions.assertNotNull(secondTask);
        Assertions.assertEquals("task-2", secondTask.value());
    }

    /**
     * capacity를 1로 제한했을 때, 두 번째 enqueue가 실패하고 droppedCount가 증가하는지 검증합니다.
     */
    @Test
    void shouldIncreaseDroppedCountWhenCapacityExceeded() {
        final MailboxScheduler<TestTask> scheduler = new MailboxScheduler<>(1);
        final long now = System.currentTimeMillis();

        final boolean first = scheduler.enqueue(new TestTask("EQP-B", "task-1"), now);
        final boolean second = scheduler.enqueue(new TestTask("EQP-B", "task-2"), now + 1);

        Assertions.assertTrue(first);
        Assertions.assertFalse(second);

        final Mailbox<TestTask> mailbox = scheduler.getMailbox("EQP-B");
        Assertions.assertNotNull(mailbox);
        Assertions.assertEquals(1L, mailbox.droppedCount());
    }

    /**
     * in-flight 상태에서는 추가 acquire가 거부되는지 검증합니다.
     */
    @Test
    void shouldRejectSecondAcquireWhileInFlight() throws Exception {
        final MailboxScheduler<TestTask> scheduler = new MailboxScheduler<>(4);
        final long now = System.currentTimeMillis();

        scheduler.enqueue(new TestTask("EQP-C", "task-1"), now);

        final String readyKey = scheduler.takeReadyKey();
        final Mailbox<TestTask> firstAcquire = scheduler.tryAcquire(readyKey);

        Assertions.assertNotNull(firstAcquire);
        Assertions.assertTrue(firstAcquire.inFlightFlag().get());

        final Mailbox<TestTask> secondAcquire = scheduler.tryAcquire(readyKey);
        Assertions.assertNull(secondAcquire);
    }

    /**
     * 테스트 전용 작업 모델입니다.
     *
     * @param routingKey 라우팅 키
     * @param value 테스트 식별 값
     */
    private record TestTask(String routingKey, String value) implements MailboxTask {
    }
}

