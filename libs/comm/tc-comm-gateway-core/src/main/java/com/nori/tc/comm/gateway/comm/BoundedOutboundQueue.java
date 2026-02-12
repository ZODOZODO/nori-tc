package com.nori.tc.apps.commgateway.comm;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * eqp별 outbound 큐 구현체
 *
 * - bounded 필수
 * - offer 실패 시 상위 정책(재시도/close/metric)으로 처리
 */
public final class BoundedOutboundQueue {

    private final ArrayBlockingQueue<OutboundCommand> queue;

    public BoundedOutboundQueue(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public boolean offer(final OutboundCommand command) {
        Objects.requireNonNull(command, "command is null");
        return queue.offer(command);
    }

    public OutboundCommand poll() {
        return queue.poll();
    }

    public int size() {
        return queue.size();
    }
}
