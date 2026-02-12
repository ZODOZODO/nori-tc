package com.nori.tc.apps.commgateway.comm;

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

    public int size() {
        return queue.size();
    }
}
