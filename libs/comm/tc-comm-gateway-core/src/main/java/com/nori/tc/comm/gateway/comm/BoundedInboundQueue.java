package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.comm.core.inbound.InboundChunk;
import com.nori.tc.comm.core.inbound.InboundQueue;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * eqp별 inbound 큐 구현체
 *
 * 핵심 요구사항
 * - 반드시 bounded 큐를 사용해야 합니다.
 * - 포화 시에는 false 반환 → 상위에서 DLQ/Quarantine 정책 수행
 */
public final class BoundedInboundQueue implements InboundQueue {

    private final ArrayBlockingQueue<InboundChunk> queue;

    public BoundedInboundQueue(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public boolean offer(final InboundChunk chunk) {
        Objects.requireNonNull(chunk, "chunk is null");
        return queue.offer(chunk);
    }

    @Override
    public InboundChunk poll() {
        return queue.poll();
    }

    @Override
    public int size() {
        return queue.size();
    }
}
