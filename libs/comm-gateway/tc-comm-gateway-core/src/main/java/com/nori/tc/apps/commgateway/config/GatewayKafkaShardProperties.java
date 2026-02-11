package com.nori.tc.apps.commgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Kafka shard ownership configuration.
 *
 * - commandsPartitionCount: total partitions for tc.eqp.commands.
 * - ownedPartitions: fixed partition set owned by this gateway instance.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.kafka")
public class GatewayKafkaShardProperties {

    /**
     * Total partition count of tc.eqp.commands.
     */
    private Integer commandsPartitionCount;

    /**
     * Fixed owned partitions for this gateway instance.
     * Example: 0,1,2
     */
    private List<Integer> ownedPartitions;

    /**
     * Kafka poll timeout for the assigned consumer (ms).
     */
    private Long pollTimeoutMs;

    /**
     * Kafka poll timeout for UI commands consumer (ms).
     */
    private Long uiPollTimeoutMs;

    /**
     * Commit retry count when commitSync fails.
     */
    private Integer commitRetryMax;

    /**
     * Commit retry backoff (ms).
     */
    private Long commitRetryBackoffMs;

    /**
     * Consumer lag sampling interval (ms).
     */
    private Long lagSampleIntervalMs;

    /**
     * Consumer shutdown wait (join) timeout (ms).
     */
    private Long consumerShutdownWaitMs;

    /**
     * Kafka admin timeout for invariant checks (seconds).
     */
    private Long adminTimeoutSeconds;

    @PostConstruct
    public void validate() {
        if (commandsPartitionCount == null || commandsPartitionCount <= 0) {
            throw new IllegalStateException("tc.comm.gateway.kafka.commands-partition-count must be > 0");
        }
        if (ownedPartitions == null || ownedPartitions.isEmpty()) {
            throw new IllegalStateException("tc.comm.gateway.kafka.owned-partitions must not be empty");
        }
        for (Integer p : ownedPartitions) {
            if (p == null || p < 0) {
                throw new IllegalStateException("Invalid owned partition: " + p);
            }
            if (p >= commandsPartitionCount) {
                throw new IllegalStateException("Owned partition out of range: " + p);
            }
        }
        if (pollTimeoutMs == null || pollTimeoutMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.kafka.poll-timeout-ms must be > 0");
        }
        if (uiPollTimeoutMs == null || uiPollTimeoutMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.kafka.ui-poll-timeout-ms must be > 0");
        }
        if (commitRetryMax == null || commitRetryMax < 0) {
            throw new IllegalStateException("tc.comm.gateway.kafka.commit-retry-max must be >= 0");
        }
        if (commitRetryBackoffMs == null || commitRetryBackoffMs < 0) {
            throw new IllegalStateException("tc.comm.gateway.kafka.commit-retry-backoff-ms must be >= 0");
        }
        if (lagSampleIntervalMs == null || lagSampleIntervalMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.kafka.lag-sample-interval-ms must be > 0");
        }
        if (consumerShutdownWaitMs == null || consumerShutdownWaitMs <= 0) {
            throw new IllegalStateException("tc.comm.gateway.kafka.consumer-shutdown-wait-ms must be > 0");
        }
        if (adminTimeoutSeconds == null || adminTimeoutSeconds <= 0) {
            throw new IllegalStateException("tc.comm.gateway.kafka.admin-timeout-seconds must be > 0");
        }
    }

    public int getCommandsPartitionCount() {
        return commandsPartitionCount;
    }

    public void setCommandsPartitionCount(final int commandsPartitionCount) {
        this.commandsPartitionCount = commandsPartitionCount;
    }

    public List<Integer> getOwnedPartitions() {
        return ownedPartitions;
    }

    public void setOwnedPartitions(final List<Integer> ownedPartitions) {
        this.ownedPartitions = (ownedPartitions == null) ? null : new ArrayList<>(ownedPartitions);
    }

    public long getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    public void setPollTimeoutMs(final long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public long getUiPollTimeoutMs() {
        return uiPollTimeoutMs;
    }

    public void setUiPollTimeoutMs(final long uiPollTimeoutMs) {
        this.uiPollTimeoutMs = uiPollTimeoutMs;
    }

    public int getCommitRetryMax() {
        return commitRetryMax;
    }

    public void setCommitRetryMax(final int commitRetryMax) {
        this.commitRetryMax = commitRetryMax;
    }

    public long getCommitRetryBackoffMs() {
        return commitRetryBackoffMs;
    }

    public void setCommitRetryBackoffMs(final long commitRetryBackoffMs) {
        this.commitRetryBackoffMs = commitRetryBackoffMs;
    }

    public long getLagSampleIntervalMs() {
        return lagSampleIntervalMs;
    }

    public void setLagSampleIntervalMs(final long lagSampleIntervalMs) {
        this.lagSampleIntervalMs = lagSampleIntervalMs;
    }

    public long getConsumerShutdownWaitMs() {
        return consumerShutdownWaitMs;
    }

    public void setConsumerShutdownWaitMs(final long consumerShutdownWaitMs) {
        this.consumerShutdownWaitMs = consumerShutdownWaitMs;
    }

    public long getAdminTimeoutSeconds() {
        return adminTimeoutSeconds;
    }

    public void setAdminTimeoutSeconds(final long adminTimeoutSeconds) {
        this.adminTimeoutSeconds = adminTimeoutSeconds;
    }
}
