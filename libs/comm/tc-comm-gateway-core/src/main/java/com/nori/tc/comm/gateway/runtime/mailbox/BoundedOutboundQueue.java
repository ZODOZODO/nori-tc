package com.nori.tc.comm.gateway.runtime.mailbox;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * eqp별 outbound 큐 구현체
 *
 * - bounded 필수
 * - offer 실패 시 상위 정책(재시도/close/metric)으로 처리
 */
public final class BoundedOutboundQueue {

    private final ArrayBlockingQueue<OutboundQueueCommand> queue;

    
    /**
     * 설비별 outbound bounded queue를 생성합니다.
     *
     * @param capacity 큐 최대 적재 건수(0보다 커야 함)
     */
    public BoundedOutboundQueue(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    
    /**
     * outbound 전송 명령을 큐에 적재합니다.
     *
     * @param command 적재할 outbound 명령
     * @return 적재 성공 여부(포화 시 {@code false})
     */
    public boolean offer(final OutboundQueueCommand command) {
        Objects.requireNonNull(command, "command is null");
        return queue.offer(command);
    }

    
    /**
     * 큐에서 다음 outbound 명령을 꺼냅니다.
     *
     * @return 다음 명령, 없으면 {@code null}
     */
    public OutboundQueueCommand poll() {
        return queue.poll();
    }

    
    /**
     * 현재 큐 적재 건수를 반환합니다.
     *
     * @return 큐 크기
     */
    public int size() {
        return queue.size();
    }
}
