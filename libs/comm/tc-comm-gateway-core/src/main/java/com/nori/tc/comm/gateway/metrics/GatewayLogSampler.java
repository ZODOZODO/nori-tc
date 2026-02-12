package com.nori.tc.apps.commgateway.metrics;

import com.nori.tc.apps.commgateway.config.GatewayObservabilityProperties;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * Simple counter-based log sampler.
 *
 * Example: log every Nth event to avoid log flooding.
 */
@Component
public final class GatewayLogSampler {

    private final GatewayObservabilityProperties properties;

    private final LongAdder commandDropCounter = new LongAdder();
    private final LongAdder bindTimeoutCounter = new LongAdder();
    private final LongAdder duplicateRejectCounter = new LongAdder();
    private final LongAdder queueOverflowCounter = new LongAdder();
    private final LongAdder commitFailCounter = new LongAdder();
    private final LongAdder notOwnerCounter = new LongAdder();

    public GatewayLogSampler(final GatewayObservabilityProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties is null");
    }

    public boolean shouldLogCommandDrop() {
        return shouldLog(commandDropCounter, properties.getCommandDropLogEvery());
    }

    public boolean shouldLogBindTimeout() {
        return shouldLog(bindTimeoutCounter, properties.getBindTimeoutLogEvery());
    }

    public boolean shouldLogDuplicateReject() {
        return shouldLog(duplicateRejectCounter, properties.getDuplicateRejectLogEvery());
    }

    public boolean shouldLogQueueOverflow() {
        return shouldLog(queueOverflowCounter, properties.getQueueOverflowLogEvery());
    }

    public boolean shouldLogCommitFail() {
        return shouldLog(commitFailCounter, properties.getCommitFailLogEvery());
    }

    public boolean shouldLogNotOwnerReject() {
        return shouldLog(notOwnerCounter, properties.getNotOwnerLogEvery());
    }

    private boolean shouldLog(final LongAdder counter, final int every) {
        if (every <= 1) {
            counter.increment();
            return true;
        }
        counter.increment();
        return counter.sum() % every == 0;
    }
}
