package com.nori.tc.comm.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(GatewayKafkaShardProperties.class);

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

    
    /**
     * 게이트웨이 Kafka 어댑터 입력/설정 유효성을 검증합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     */
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
        log.info("GatewayKafkaShardProperties validated. commandsPartitionCount={}, ownedPartitions={}",
                commandsPartitionCount, ownedPartitions);
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public int getCommandsPartitionCount() {
        return commandsPartitionCount;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param commandsPartitionCount 처리할 요청/명령 정보
     */
    public void setCommandsPartitionCount(final int commandsPartitionCount) {
        this.commandsPartitionCount = commandsPartitionCount;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 조회/처리 결과 목록
     */
    public List<Integer> getOwnedPartitions() {
        return ownedPartitions;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param ownedPartitions 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     */
    public void setOwnedPartitions(final List<Integer> ownedPartitions) {
        this.ownedPartitions = (ownedPartitions == null) ? null : new ArrayList<>(ownedPartitions);
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public long getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param pollTimeoutMs 시간 관련 설정 값
     */
    public void setPollTimeoutMs(final long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public long getUiPollTimeoutMs() {
        return uiPollTimeoutMs;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param uiPollTimeoutMs 시간 관련 설정 값
     */
    public void setUiPollTimeoutMs(final long uiPollTimeoutMs) {
        this.uiPollTimeoutMs = uiPollTimeoutMs;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public int getCommitRetryMax() {
        return commitRetryMax;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param commitRetryMax 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     */
    public void setCommitRetryMax(final int commitRetryMax) {
        this.commitRetryMax = commitRetryMax;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public long getCommitRetryBackoffMs() {
        return commitRetryBackoffMs;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param commitRetryBackoffMs 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     */
    public void setCommitRetryBackoffMs(final long commitRetryBackoffMs) {
        this.commitRetryBackoffMs = commitRetryBackoffMs;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public long getLagSampleIntervalMs() {
        return lagSampleIntervalMs;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param lagSampleIntervalMs 시간 관련 설정 값
     */
    public void setLagSampleIntervalMs(final long lagSampleIntervalMs) {
        this.lagSampleIntervalMs = lagSampleIntervalMs;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public long getConsumerShutdownWaitMs() {
        return consumerShutdownWaitMs;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param consumerShutdownWaitMs 게이트웨이 Kafka 어댑터 처리에 사용하는 입력 값
     */
    public void setConsumerShutdownWaitMs(final long consumerShutdownWaitMs) {
        this.consumerShutdownWaitMs = consumerShutdownWaitMs;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터의 현재 값을 조회합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 Kafka 어댑터 처리 결과
     */
    public long getAdminTimeoutSeconds() {
        return adminTimeoutSeconds;
    }

    
    /**
     * 게이트웨이 Kafka 어댑터 설정 값을 반영합니다.
     *
     * <p>토픽 구성, 메시지 발행/구독 흐름, 직렬화 규칙을 기준으로 처리합니다.</p>
     * @param adminTimeoutSeconds 시간 관련 설정 값
     */
    public void setAdminTimeoutSeconds(final long adminTimeoutSeconds) {
        this.adminTimeoutSeconds = adminTimeoutSeconds;
    }
}
