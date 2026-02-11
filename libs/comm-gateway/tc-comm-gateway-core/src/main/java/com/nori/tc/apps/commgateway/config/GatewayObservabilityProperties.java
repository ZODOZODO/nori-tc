package com.nori.tc.apps.commgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * Observability configuration (log sampling / metrics behavior).
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.observability")
public class GatewayObservabilityProperties {

    /**
     * Log every Nth "command drop (no connection)".
     */
    private Integer commandDropLogEvery;

    /**
     * Log every Nth bind timeout.
     */
    private Integer bindTimeoutLogEvery;

    /**
     * Log every Nth duplicate eqpId reject.
     */
    private Integer duplicateRejectLogEvery;

    /**
     * Log every Nth queue overflow.
     */
    private Integer queueOverflowLogEvery;

    /**
     * Log every Nth Kafka commit failure.
     */
    private Integer commitFailLogEvery;

    /**
     * Log every Nth NOT_OWNER_PARTITION reject.
     */
    private Integer notOwnerLogEvery;

    @PostConstruct
    public void validate() {
        if (commandDropLogEvery == null || commandDropLogEvery <= 0) {
            throw new IllegalStateException("tc.comm.gateway.observability.command-drop-log-every must be > 0");
        }
        if (bindTimeoutLogEvery == null || bindTimeoutLogEvery <= 0) {
            throw new IllegalStateException("tc.comm.gateway.observability.bind-timeout-log-every must be > 0");
        }
        if (duplicateRejectLogEvery == null || duplicateRejectLogEvery <= 0) {
            throw new IllegalStateException("tc.comm.gateway.observability.duplicate-reject-log-every must be > 0");
        }
        if (queueOverflowLogEvery == null || queueOverflowLogEvery <= 0) {
            throw new IllegalStateException("tc.comm.gateway.observability.queue-overflow-log-every must be > 0");
        }
        if (commitFailLogEvery == null || commitFailLogEvery <= 0) {
            throw new IllegalStateException("tc.comm.gateway.observability.commit-fail-log-every must be > 0");
        }
        if (notOwnerLogEvery == null || notOwnerLogEvery <= 0) {
            throw new IllegalStateException("tc.comm.gateway.observability.not-owner-log-every must be > 0");
        }
    }

    public int getCommandDropLogEvery() {
        return commandDropLogEvery;
    }

    public void setCommandDropLogEvery(final int commandDropLogEvery) {
        this.commandDropLogEvery = commandDropLogEvery;
    }

    public int getBindTimeoutLogEvery() {
        return bindTimeoutLogEvery;
    }

    public void setBindTimeoutLogEvery(final int bindTimeoutLogEvery) {
        this.bindTimeoutLogEvery = bindTimeoutLogEvery;
    }

    public int getDuplicateRejectLogEvery() {
        return duplicateRejectLogEvery;
    }

    public void setDuplicateRejectLogEvery(final int duplicateRejectLogEvery) {
        this.duplicateRejectLogEvery = duplicateRejectLogEvery;
    }

    public int getQueueOverflowLogEvery() {
        return queueOverflowLogEvery;
    }

    public void setQueueOverflowLogEvery(final int queueOverflowLogEvery) {
        this.queueOverflowLogEvery = queueOverflowLogEvery;
    }

    public int getCommitFailLogEvery() {
        return commitFailLogEvery;
    }

    public void setCommitFailLogEvery(final int commitFailLogEvery) {
        this.commitFailLogEvery = commitFailLogEvery;
    }

    public int getNotOwnerLogEvery() {
        return notOwnerLogEvery;
    }

    public void setNotOwnerLogEvery(final int notOwnerLogEvery) {
        this.notOwnerLogEvery = notOwnerLogEvery;
    }
}
