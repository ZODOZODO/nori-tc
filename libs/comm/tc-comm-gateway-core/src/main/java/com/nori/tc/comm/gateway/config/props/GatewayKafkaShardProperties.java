package com.nori.tc.comm.gateway.config.props;

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
        // 샤딩 불변식 검증: 전체 파티션 수와 소유 파티션 목록이 먼저 유효해야 후속 범위 검사가 가능합니다.
        requirePositive("tc.comm.gateway.kafka.commands-partition-count", commandsPartitionCount);
        requireNotEmpty("tc.comm.gateway.kafka.owned-partitions", ownedPartitions);

        // 소유 파티션 목록은 commandsPartitionCount 범위 내 값만 허용합니다.
        for (Integer partition : ownedPartitions) {
            requireNonNegative("tc.comm.gateway.kafka.owned-partitions element", partition);
            if (partition >= commandsPartitionCount) {
                throw new IllegalStateException(
                        "Owned partition out of range: " + partition + " (commandsPartitionCount=" + commandsPartitionCount + ")"
                );
            }
        }

        // Kafka poll/commit/lag/종료 대기 시간 관련 기본 운영 정책을 검증합니다.
        requirePositive("tc.comm.gateway.kafka.poll-timeout-ms", pollTimeoutMs);
        requirePositive("tc.comm.gateway.kafka.ui-poll-timeout-ms", uiPollTimeoutMs);
        requireNonNegative("tc.comm.gateway.kafka.commit-retry-max", commitRetryMax);
        requireNonNegative("tc.comm.gateway.kafka.commit-retry-backoff-ms", commitRetryBackoffMs);
        requirePositive("tc.comm.gateway.kafka.lag-sample-interval-ms", lagSampleIntervalMs);
        requirePositive("tc.comm.gateway.kafka.consumer-shutdown-wait-ms", consumerShutdownWaitMs);
        requirePositive("tc.comm.gateway.kafka.admin-timeout-seconds", adminTimeoutSeconds);

        // 비동기 레코드 처리 모드 on/off 및 워커/버퍼 정책을 검증합니다.
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
        if (log.isDebugEnabled()) {
            log.debug(
                    "Gateway Kafka consumer timing policy validated. pollTimeoutMs={}, uiPollTimeoutMs={}, commitRetryMax={}, commitRetryBackoffMs={}, lagSampleIntervalMs={}, consumerShutdownWaitMs={}, adminTimeoutSeconds={}",
                    pollTimeoutMs,
                    uiPollTimeoutMs,
                    commitRetryMax,
                    commitRetryBackoffMs,
                    lagSampleIntervalMs,
                    consumerShutdownWaitMs,
                    adminTimeoutSeconds
            );
        }
    }

    /**
     * getCommandsPartitionCount 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public int getCommandsPartitionCount() {
        return commandsPartitionCount;
    }

    /**
     * setCommandsPartitionCount 프로퍼티 값을 설정합니다.
     *
     * @param commandsPartitionCount 설정할 프로퍼티 값
     */
    public void setCommandsPartitionCount(final int commandsPartitionCount) {
        this.commandsPartitionCount = commandsPartitionCount;
    }

    /**
     * getOwnedPartitions 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public List<Integer> getOwnedPartitions() {
        return ownedPartitions;
    }

    /**
     * setOwnedPartitions 프로퍼티 값을 설정합니다.
     *
     * @param ownedPartitions 설정할 프로퍼티 값
     */
    public void setOwnedPartitions(final List<Integer> ownedPartitions) {
        this.ownedPartitions = ownedPartitions == null ? null : new ArrayList<>(ownedPartitions);
    }

    /**
     * getPollTimeoutMs 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public long getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    /**
     * setPollTimeoutMs 프로퍼티 값을 설정합니다.
     *
     * @param pollTimeoutMs 설정할 프로퍼티 값
     */
    public void setPollTimeoutMs(final long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    /**
     * getUiPollTimeoutMs 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public long getUiPollTimeoutMs() {
        return uiPollTimeoutMs;
    }

    /**
     * setUiPollTimeoutMs 프로퍼티 값을 설정합니다.
     *
     * @param uiPollTimeoutMs 설정할 프로퍼티 값
     */
    public void setUiPollTimeoutMs(final long uiPollTimeoutMs) {
        this.uiPollTimeoutMs = uiPollTimeoutMs;
    }

    /**
     * getCommitRetryMax 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public int getCommitRetryMax() {
        return commitRetryMax;
    }

    /**
     * setCommitRetryMax 프로퍼티 값을 설정합니다.
     *
     * @param commitRetryMax 설정할 프로퍼티 값
     */
    public void setCommitRetryMax(final int commitRetryMax) {
        this.commitRetryMax = commitRetryMax;
    }

    /**
     * getCommitRetryBackoffMs 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public long getCommitRetryBackoffMs() {
        return commitRetryBackoffMs;
    }

    /**
     * setCommitRetryBackoffMs 프로퍼티 값을 설정합니다.
     *
     * @param commitRetryBackoffMs 설정할 프로퍼티 값
     */
    public void setCommitRetryBackoffMs(final long commitRetryBackoffMs) {
        this.commitRetryBackoffMs = commitRetryBackoffMs;
    }

    /**
     * getLagSampleIntervalMs 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public long getLagSampleIntervalMs() {
        return lagSampleIntervalMs;
    }

    /**
     * setLagSampleIntervalMs 프로퍼티 값을 설정합니다.
     *
     * @param lagSampleIntervalMs 설정할 프로퍼티 값
     */
    public void setLagSampleIntervalMs(final long lagSampleIntervalMs) {
        this.lagSampleIntervalMs = lagSampleIntervalMs;
    }

    /**
     * getConsumerShutdownWaitMs 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public long getConsumerShutdownWaitMs() {
        return consumerShutdownWaitMs;
    }

    /**
     * setConsumerShutdownWaitMs 프로퍼티 값을 설정합니다.
     *
     * @param consumerShutdownWaitMs 설정할 프로퍼티 값
     */
    public void setConsumerShutdownWaitMs(final long consumerShutdownWaitMs) {
        this.consumerShutdownWaitMs = consumerShutdownWaitMs;
    }

    /**
     * getAdminTimeoutSeconds 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public long getAdminTimeoutSeconds() {
        return adminTimeoutSeconds;
    }

    /**
     * setAdminTimeoutSeconds 프로퍼티 값을 설정합니다.
     *
     * @param adminTimeoutSeconds 설정할 프로퍼티 값
     */
    public void setAdminTimeoutSeconds(final long adminTimeoutSeconds) {
        this.adminTimeoutSeconds = adminTimeoutSeconds;
    }

    /**
     * isAsyncRecordProcessingEnabled 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public boolean isAsyncRecordProcessingEnabled() {
        return asyncRecordProcessingEnabled;
    }

    /**
     * setAsyncRecordProcessingEnabled 프로퍼티 값을 설정합니다.
     *
     * @param asyncRecordProcessingEnabled 설정할 프로퍼티 값
     */
    public void setAsyncRecordProcessingEnabled(final boolean asyncRecordProcessingEnabled) {
        this.asyncRecordProcessingEnabled = asyncRecordProcessingEnabled;
    }

    /**
     * getRecordWorkerThreads 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public int getRecordWorkerThreads() {
        return recordWorkerThreads;
    }

    /**
     * setRecordWorkerThreads 프로퍼티 값을 설정합니다.
     *
     * @param recordWorkerThreads 설정할 프로퍼티 값
     */
    public void setRecordWorkerThreads(final int recordWorkerThreads) {
        this.recordWorkerThreads = recordWorkerThreads;
    }

    /**
     * getAckDrainMaxBatch 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public int getAckDrainMaxBatch() {
        return ackDrainMaxBatch;
    }

    /**
     * setAckDrainMaxBatch 프로퍼티 값을 설정합니다.
     *
     * @param ackDrainMaxBatch 설정할 프로퍼티 값
     */
    public void setAckDrainMaxBatch(final int ackDrainMaxBatch) {
        this.ackDrainMaxBatch = ackDrainMaxBatch;
    }

    /**
     * getMaxInFlightRecords 프로퍼티 값을 반환합니다.
     *
     * @return 프로퍼티 값
     */
    public int getMaxInFlightRecords() {
        return maxInFlightRecords;
    }

    /**
     * setMaxInFlightRecords 프로퍼티 값을 설정합니다.
     *
     * @param maxInFlightRecords 설정할 프로퍼티 값
     */
    public void setMaxInFlightRecords(final int maxInFlightRecords) {
        this.maxInFlightRecords = maxInFlightRecords;
    }

    /**
     * 필수 설정이 null 인지 검증합니다.
     *
     * @param key 설정 키
     * @param value 설정 값
     */
    private static void requireNotNull(final String key, final Object value) {
        if (value == null) {
            throw new IllegalStateException(key + " is required");
        }
    }

    /**
     * 컬렉션 설정이 비어 있지 않은지 검증합니다.
     *
     * @param key 설정 키
     * @param value 컬렉션 설정 값
     */
    private static void requireNotEmpty(final String key, final List<?> value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(key + " must not be empty");
        }
    }

    /**
     * 숫자 설정이 양수(> 0)인지 검증합니다.
     *
     * @param key 설정 키
     * @param value 숫자 설정 값
     */
    private static void requirePositive(final String key, final Number value) {
        if (value == null || value.longValue() <= 0L) {
            throw new IllegalStateException(key + " must be > 0");
        }
    }

    /**
     * 숫자 설정이 0 이상(>= 0)인지 검증합니다.
     *
     * @param key 설정 키
     * @param value 숫자 설정 값
     */
    private static void requireNonNegative(final String key, final Number value) {
        if (value == null || value.longValue() < 0L) {
            throw new IllegalStateException(key + " must be >= 0");
        }
    }
}
