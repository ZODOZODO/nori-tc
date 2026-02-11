package com.nori.tc.apps.commgateway.metrics;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Gateway metrics registry (in-memory).
 *
 * NOTE:
 * - This class does not export to any backend by itself.
 * - It provides counters/gauges that can be wired to Micrometer later.
 */
@Component
public final class GatewayMetrics {

    private final LongAdder commandsDropNoConnection = new LongAdder();
    private final LongAdder duplicateEqpRejectTotal = new LongAdder();
    private final LongAdder bindTimeoutTotal = new LongAdder();
    private final LongAdder inboundQueueOverflowTotal = new LongAdder();
    private final LongAdder outboundQueueOverflowTotal = new LongAdder();
    private final LongAdder kafkaCommitFailTotal = new LongAdder();
    private final LongAdder eventPublishSuccessTotal = new LongAdder();
    private final LongAdder eventPublishFailTotal = new LongAdder();
    private final LongAdder dlqPublishTotal = new LongAdder();
    private final LongAdder decodeFailTotal = new LongAdder();
    private final LongAdder hsmsTimeoutTotal = new LongAdder();

    private final AtomicInteger activeConnections = new AtomicInteger();
    private final AtomicInteger boundConnections = new AtomicInteger();
    private final AtomicInteger unboundConnections = new AtomicInteger();

    private final Map<String, AtomicInteger> inboundQueueDepth = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> outboundQueueDepth = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> consumerLag = new ConcurrentHashMap<>();

    // ------------------------
    // Counters
    // ------------------------

    public void incrementCommandsDropNoConnection() {
        commandsDropNoConnection.increment();
    }

    public void incrementDuplicateEqpReject() {
        duplicateEqpRejectTotal.increment();
    }

    public void incrementBindTimeout() {
        bindTimeoutTotal.increment();
    }

    public void incrementInboundQueueOverflow() {
        inboundQueueOverflowTotal.increment();
    }

    public void incrementOutboundQueueOverflow() {
        outboundQueueOverflowTotal.increment();
    }

    public void incrementKafkaCommitFail() {
        kafkaCommitFailTotal.increment();
    }

    public void incrementEventPublishSuccess() {
        eventPublishSuccessTotal.increment();
    }

    public void incrementEventPublishFail() {
        eventPublishFailTotal.increment();
    }

    public void incrementDlqPublish() {
        dlqPublishTotal.increment();
    }

    public void incrementDecodeFail() {
        decodeFailTotal.increment();
    }

    public void incrementHsmsTimeout() {
        hsmsTimeoutTotal.increment();
    }

    // ------------------------
    // Connection gauges
    // ------------------------

    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementActiveConnections() {
        activeConnections.updateAndGet(v -> Math.max(0, v - 1));
    }

    public void incrementBoundConnections() {
        boundConnections.incrementAndGet();
    }

    public void decrementBoundConnections() {
        boundConnections.updateAndGet(v -> Math.max(0, v - 1));
    }

    public void incrementUnboundConnections() {
        unboundConnections.incrementAndGet();
    }

    public void decrementUnboundConnections() {
        unboundConnections.updateAndGet(v -> Math.max(0, v - 1));
    }

    // ------------------------
    // Queue depth sampling
    // ------------------------

    public void recordInboundQueueDepth(final String eqpId, final int depth) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        inboundQueueDepth.computeIfAbsent(eqpId, key -> new AtomicInteger())
                .set(Math.max(0, depth));
    }

    public void recordOutboundQueueDepth(final String eqpId, final int depth) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        outboundQueueDepth.computeIfAbsent(eqpId, key -> new AtomicInteger())
                .set(Math.max(0, depth));
    }

    public void clearQueueDepth(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        inboundQueueDepth.remove(eqpId);
        outboundQueueDepth.remove(eqpId);
    }

    // ------------------------
    // Kafka consumer lag
    // ------------------------

    public void recordConsumerLag(final String topic, final int partition, final long lag) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        final String key = topic + "-" + partition;
        consumerLag.computeIfAbsent(key, k -> new AtomicLong()).set(Math.max(0, lag));
    }
}
