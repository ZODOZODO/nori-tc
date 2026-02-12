package com.nori.tc.comm.gateway.comm;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Global ReadyQueue for eqpId-based scheduling.
 *
 * Purpose:
 * - Bridge between producers (Netty/Kafka enqueue) and worker threads.
 * - Only eqpId is queued, so per-equipment processing remains sequential.
 *
 * Notes:
 * - Duplicate suppression is handled by EqpMailbox.scheduledFlag().
 * - This queue does not inspect mailbox state; it only transports eqpId tokens.
 */
public final class ReadyQueue {

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    /**
     * Enqueue eqpId for processing.
     */
    public void offer(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        queue.offer(eqpId);
    }

    /**
     * Take the next eqpId (blocking).
     */
    public String take() throws InterruptedException {
        return queue.take();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int size() {
        return queue.size();
    }
}
