package com.nori.tc.comm.gateway.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Gateway Kafka 소비 런타임(샤드/커밋/비동기) 설정입니다.
 *
 * <p>prefix: {@code tc.comm.gateway.kafka}</p>
 *
 * <p>설계 의도:</p>
 * <p>1) tc.eqp.commands는 고정 assign 파티션 방식으로 소비합니다.</p>
 * <p>2) 런타임 중 파티션 수 변경을 금지하고, 기동 시 불변식으로 검증합니다.</p>
 * <p>3) poll-loop 비블로킹(비동기) 관련 모든 튜닝 값을 프로퍼티로 외부화합니다.</p>
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.kafka")
public class GatewayKafkaShardProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayKafkaShardProperties.class);

    /**
     * tc.eqp.commands 전체 파티션 수입니다.
     */
    private Integer commandsPartitionCount;

    /**
     * 현재 Gateway 인스턴스가 소유하는 고정 파티션 목록입니다.
     */
    private List<Integer> ownedPartitions;

    /**
     * tc.eqp.commands 소비 poll timeout(ms)입니다.
     */
    private Long pollTimeoutMs;

    /**
     * tc.ui.events 소비 poll timeout(ms)입니다.
     */
    private Long uiPollTimeoutMs;

    /**
     * commit 실패 시 재시도 횟수입니다.
     */
    private Integer commitRetryMax;

    /**
     * commit 재시도 backoff(ms)입니다.
     */
    private Long commitRetryBackoffMs;

    /**
     * lag 샘플링 주기(ms)입니다.
     */
    private Long lagSampleIntervalMs;

    /**
     * consumer 종료 대기(join) 시간(ms)입니다.
     */
    private Long consumerShutdownWaitMs;

    /**
     * Kafka AdminClient 기반 기동 검증 timeout(초)입니다.
     */
    private Long adminTimeoutSeconds;

    /**
     * poll 스레드와 레코드 처리 스레드를 분리할지 여부입니다.
     */
    private Boolean asyncRecordProcessingEnabled;

    /**
     * 비동기 처리 모드에서 사용할 워커 스레드 수입니다.
     */
    private Integer recordWorkerThreads;

    /**
     * poll 루프에서 한 번에 드레인할 ack 이벤트 최대 개수입니다.
     */
    private Integer ackDrainMaxBatch;

    /**
     * 비동기 처리 모드에서 허용할 최대 in-flight 레코드 수입니다.
     */
    private Integer maxInFlightRecords;

    /**
     * 애플리케이션 시작 시 설정 유효성을 검증합니다.
     */
    @PostConstruct
    public void validate() {
        requirePositive("tc.comm.gateway.kafka.commands-partition-count", commandsPartitionCount);
        requireNotEmpty("tc.comm.gateway.kafka.owned-partitions", ownedPartitions);
        for (Integer partition : ownedPartitions) {
            requireNonNegative("tc.comm.gateway.kafka.owned-partitions element", partition);
            if (partition >= commandsPartitionCount) {
                throw new IllegalStateException(
                        "Owned partition out of range: " + partition + " (commandsPartitionCount=" + commandsPartitionCount + ")"
                );
            }
        }

        requirePositive("tc.comm.gateway.kafka.poll-timeout-ms", pollTimeoutMs);
        requirePositive("tc.comm.gateway.kafka.ui-poll-timeout-ms", uiPollTimeoutMs);
        requireNonNegative("tc.comm.gateway.kafka.commit-retry-max", commitRetryMax);
        requireNonNegative("tc.comm.gateway.kafka.commit-retry-backoff-ms", commitRetryBackoffMs);
        requirePositive("tc.comm.gateway.kafka.lag-sample-interval-ms", lagSampleIntervalMs);
        requirePositive("tc.comm.gateway.kafka.consumer-shutdown-wait-ms", consumerShutdownWaitMs);
        requirePositive("tc.comm.gateway.kafka.admin-timeout-seconds", adminTimeoutSeconds);

        requireNotNull("tc.comm.gateway.kafka.async-record-processing-enabled", asyncRecordProcessingEnabled);
        requirePositive("tc.comm.gateway.kafka.record-worker-threads", recordWorkerThreads);
        requirePositive("tc.comm.gateway.kafka.ack-drain-max-batch", ackDrainMaxBatch);
        requirePositive("tc.comm.gateway.kafka.max-in-flight-records", maxInFlightRecords);

        log.info(
                "GatewayKafkaShardProperties validated. commandsPartitionCount={}, ownedPartitions={}, asyncRecordProcessingEnabled={}, recordWorkerThreads={}, ackDrainMaxBatch={}, maxInFlightRecords={}",
                commandsPartitionCount,
                ownedPartitions,
                asyncRecordProcessingEnabled,
                recordWorkerThreads,
                ackDrainMaxBatch,
                maxInFlightRecords
        );
    }

    /**
     * getCommandsPartitionCount 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getCommandsPartitionCount() {
        return commandsPartitionCount;
    }

    /**
     * setCommandsPartitionCount 기능을 수행합니다.
     *
     * @param commandsPartitionCount 입력 값
     */

    public void setCommandsPartitionCount(final int commandsPartitionCount) {
        this.commandsPartitionCount = commandsPartitionCount;
    }

    /**
     * getOwnedPartitions 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public List<Integer> getOwnedPartitions() {
        return ownedPartitions;
    }

    /**
     * setOwnedPartitions 기능을 수행합니다.
     *
     * @param ownedPartitions 입력 값
     */

    public void setOwnedPartitions(final List<Integer> ownedPartitions) {
        this.ownedPartitions = ownedPartitions == null ? null : new ArrayList<>(ownedPartitions);
    }

    /**
     * getPollTimeoutMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    /**
     * setPollTimeoutMs 기능을 수행합니다.
     *
     * @param pollTimeoutMs 입력 값
     */

    public void setPollTimeoutMs(final long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    /**
     * getUiPollTimeoutMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getUiPollTimeoutMs() {
        return uiPollTimeoutMs;
    }

    /**
     * setUiPollTimeoutMs 기능을 수행합니다.
     *
     * @param uiPollTimeoutMs 입력 값
     */

    public void setUiPollTimeoutMs(final long uiPollTimeoutMs) {
        this.uiPollTimeoutMs = uiPollTimeoutMs;
    }

    /**
     * getCommitRetryMax 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getCommitRetryMax() {
        return commitRetryMax;
    }

    /**
     * setCommitRetryMax 기능을 수행합니다.
     *
     * @param commitRetryMax 입력 값
     */

    public void setCommitRetryMax(final int commitRetryMax) {
        this.commitRetryMax = commitRetryMax;
    }

    /**
     * getCommitRetryBackoffMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getCommitRetryBackoffMs() {
        return commitRetryBackoffMs;
    }

    /**
     * setCommitRetryBackoffMs 기능을 수행합니다.
     *
     * @param commitRetryBackoffMs 입력 값
     */

    public void setCommitRetryBackoffMs(final long commitRetryBackoffMs) {
        this.commitRetryBackoffMs = commitRetryBackoffMs;
    }

    /**
     * getLagSampleIntervalMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getLagSampleIntervalMs() {
        return lagSampleIntervalMs;
    }

    /**
     * setLagSampleIntervalMs 기능을 수행합니다.
     *
     * @param lagSampleIntervalMs 입력 값
     */

    public void setLagSampleIntervalMs(final long lagSampleIntervalMs) {
        this.lagSampleIntervalMs = lagSampleIntervalMs;
    }

    /**
     * getConsumerShutdownWaitMs 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getConsumerShutdownWaitMs() {
        return consumerShutdownWaitMs;
    }

    /**
     * setConsumerShutdownWaitMs 기능을 수행합니다.
     *
     * @param consumerShutdownWaitMs 입력 값
     */

    public void setConsumerShutdownWaitMs(final long consumerShutdownWaitMs) {
        this.consumerShutdownWaitMs = consumerShutdownWaitMs;
    }

    /**
     * getAdminTimeoutSeconds 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public long getAdminTimeoutSeconds() {
        return adminTimeoutSeconds;
    }

    /**
     * setAdminTimeoutSeconds 기능을 수행합니다.
     *
     * @param adminTimeoutSeconds 입력 값
     */

    public void setAdminTimeoutSeconds(final long adminTimeoutSeconds) {
        this.adminTimeoutSeconds = adminTimeoutSeconds;
    }

    /**
     * isAsyncRecordProcessingEnabled 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public boolean isAsyncRecordProcessingEnabled() {
        return asyncRecordProcessingEnabled;
    }

    /**
     * setAsyncRecordProcessingEnabled 기능을 수행합니다.
     *
     * @param asyncRecordProcessingEnabled 입력 값
     */

    public void setAsyncRecordProcessingEnabled(final boolean asyncRecordProcessingEnabled) {
        this.asyncRecordProcessingEnabled = asyncRecordProcessingEnabled;
    }

    /**
     * getRecordWorkerThreads 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getRecordWorkerThreads() {
        return recordWorkerThreads;
    }

    /**
     * setRecordWorkerThreads 기능을 수행합니다.
     *
     * @param recordWorkerThreads 입력 값
     */

    public void setRecordWorkerThreads(final int recordWorkerThreads) {
        this.recordWorkerThreads = recordWorkerThreads;
    }

    /**
     * getAckDrainMaxBatch 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getAckDrainMaxBatch() {
        return ackDrainMaxBatch;
    }

    /**
     * setAckDrainMaxBatch 기능을 수행합니다.
     *
     * @param ackDrainMaxBatch 입력 값
     */

    public void setAckDrainMaxBatch(final int ackDrainMaxBatch) {
        this.ackDrainMaxBatch = ackDrainMaxBatch;
    }

    /**
     * getMaxInFlightRecords 기능을 수행합니다.
     *
     * @return 처리 결과
     */

    public int getMaxInFlightRecords() {
        return maxInFlightRecords;
    }

    /**
     * setMaxInFlightRecords 기능을 수행합니다.
     *
     * @param maxInFlightRecords 입력 값
     */

    public void setMaxInFlightRecords(final int maxInFlightRecords) {
        this.maxInFlightRecords = maxInFlightRecords;
    }

    /**
     * requireNotNull 기능을 수행합니다.
     *
     * @param key 입력 값
     * @param value 입력 값
     */

    private static void requireNotNull(final String key, final Object value) {
        if (value == null) {
            throw new IllegalStateException(key + " is required");
        }
    }

    /**
     * requireNotEmpty 기능을 수행합니다.
     *
     * @param key 입력 값
     * @param value 입력 값
     */

    private static void requireNotEmpty(final String key, final List<?> value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(key + " must not be empty");
        }
    }

    /**
     * requirePositive 기능을 수행합니다.
     *
     * @param key 입력 값
     * @param value 입력 값
     */

    private static void requirePositive(final String key, final Number value) {
        if (value == null || value.longValue() <= 0L) {
            throw new IllegalStateException(key + " must be > 0");
        }
    }

    /**
     * requireNonNegative 기능을 수행합니다.
     *
     * @param key 입력 값
     * @param value 입력 값
     */

    private static void requireNonNegative(final String key, final Number value) {
        if (value == null || value.longValue() < 0L) {
            throw new IllegalStateException(key + " must be >= 0");
        }
    }
}
