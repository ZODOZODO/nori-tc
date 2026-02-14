package com.nori.tc.common.mailbox;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 라우팅 키 단위의 bounded FIFO mailbox입니다.
 *
 * <p>핵심 책임은 다음과 같습니다.</p>
 * <p>1) 작업 큐(FIFO) 보관</p>
 * <p>2) in-flight/scheduled 상태 플래그 보관</p>
 * <p>3) 기본 운영 지표(size, droppedCount) 제공</p>
 *
 * @param <T> mailbox에 저장할 작업 타입
 */
public final class Mailbox<T extends MailboxTask> {

    /**
     * 이 mailbox가 담당하는 라우팅 키입니다.
     */
    private final String routingKey;

    /**
     * queue capacity를 초과하지 않도록 제어하는 상한값입니다.
     */
    private final int capacity;

    /**
     * 작업 큐입니다.
     */
    private final Deque<T> queue;

    /**
     * 큐 동시 접근 보호용 lock 객체입니다.
     */
    private final Object lock = new Object();

    /**
     * 현재 작업이 실행 중인지 나타내는 플래그입니다.
     */
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    /**
     * ReadyQueue 중복 enqueue를 억제하기 위한 플래그입니다.
     */
    private final AtomicBoolean scheduled = new AtomicBoolean(false);

    /**
     * capacity 초과로 enqueue 실패한 횟수입니다.
     */
    private final AtomicLong droppedCount = new AtomicLong(0L);

    /**
     * 마지막 enqueue 시각(epoch millis)입니다.
     */
    private final AtomicLong lastEnqueueAt = new AtomicLong(0L);

    /**
     * mailbox를 생성합니다.
     *
     * @param routingKey 라우팅 키
     * @param capacity 큐 최대 크기
     */
    public Mailbox(final String routingKey, final int capacity) {
        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalArgumentException("routingKey is required");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }

        this.routingKey = routingKey;
        this.capacity = capacity;
        this.queue = new ArrayDeque<>(Math.min(capacity, 1024));
    }

    /**
     * mailbox의 라우팅 키를 반환합니다.
     *
     * @return 라우팅 키
     */
    public String routingKey() {
        return routingKey;
    }

    /**
     * 작업을 큐에 추가합니다.
     *
     * <p>capacity를 초과하면 false를 반환하고 droppedCount를 증가시킵니다.</p>
     *
     * @param task 저장할 작업
     * @param nowEpochMillis 현재 시각
     * @return enqueue 성공 여부
     */
    public boolean offer(final T task, final long nowEpochMillis) {
        Objects.requireNonNull(task, "task is null");

        synchronized (lock) {
            if (queue.size() >= capacity) {
                droppedCount.incrementAndGet();
                return false;
            }
            queue.addLast(task);
            lastEnqueueAt.set(nowEpochMillis);
            return true;
        }
    }

    /**
     * 큐에서 다음 작업을 꺼냅니다.
     *
     * @return 다음 작업, 없으면 null
     */
    public T poll() {
        synchronized (lock) {
            return queue.pollFirst();
        }
    }

    /**
     * 큐가 비어있는지 여부를 반환합니다.
     *
     * @return 큐 비어있음 여부
     */
    public boolean isEmpty() {
        synchronized (lock) {
            return queue.isEmpty();
        }
    }

    /**
     * 현재 큐 크기를 반환합니다.
     *
     * @return 큐 크기
     */
    public int size() {
        synchronized (lock) {
            return queue.size();
        }
    }

    /**
     * in-flight 플래그 객체를 반환합니다.
     *
     * @return in-flight 플래그
     */
    public AtomicBoolean inFlightFlag() {
        return inFlight;
    }

    /**
     * scheduled 플래그 객체를 반환합니다.
     *
     * @return scheduled 플래그
     */
    public AtomicBoolean scheduledFlag() {
        return scheduled;
    }

    /**
     * droppedCount 누적값을 반환합니다.
     *
     * @return drop 누적 횟수
     */
    public long droppedCount() {
        return droppedCount.get();
    }

    /**
     * 마지막 enqueue 시각을 반환합니다.
     *
     * @return epoch millis
     */
    public long lastEnqueueAt() {
        return lastEnqueueAt.get();
    }
}
