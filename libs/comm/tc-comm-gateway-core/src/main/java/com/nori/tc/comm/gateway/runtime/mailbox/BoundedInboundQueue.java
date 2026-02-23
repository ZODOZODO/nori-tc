package com.nori.tc.comm.gateway.runtime.mailbox;

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

    
    /**
     * 설비별 inbound bounded queue를 생성합니다.
     *
     * @param capacity 큐 최대 적재 건수(0보다 커야 함)
     */
    public BoundedInboundQueue(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    
    /**
     * inbound chunk를 큐에 적재합니다.
     *
     * <p>큐가 가득 찬 경우 {@code false}를 반환하며, overflow 후속 조치는 상위 서비스가 수행합니다.</p>
     *
     * @param chunk 적재할 inbound chunk
     * @return 적재 성공 여부
     */
    @Override
    public boolean offer(final InboundChunk chunk) {
        Objects.requireNonNull(chunk, "chunk is null");
        return queue.offer(chunk);
    }

    
    /**
     * 큐에서 다음 inbound chunk를 꺼냅니다.
     *
     * @return 다음 chunk, 없으면 {@code null}
     */
    @Override
    public InboundChunk poll() {
        return queue.poll();
    }

    
    /**
     * 현재 큐 적재 건수를 반환합니다.
     *
     * @return 큐 크기
     */
    @Override
    public int size() {
        return queue.size();
    }
}
