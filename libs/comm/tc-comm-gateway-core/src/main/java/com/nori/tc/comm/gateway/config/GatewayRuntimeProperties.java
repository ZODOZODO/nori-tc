package com.nori.tc.apps.commgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * Gateway 런타임 설정
 *
 * 목적
 * - eqp별 순차 처리/격리를 위한 공통 파라미터를 외부 설정으로 제어합니다.
 * - 낮은 지연을 위해 큐 용량, reassembly 버퍼 상한 등을 보수적으로 관리합니다.
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.runtime")
public class GatewayRuntimeProperties {

    /**
     * eqp별 inbound 큐 용량 (bounded 필수)
     */
    private Integer inboundQueueCapacity;

    /**
     * reassembly 버퍼 초기 용량(바이트)
     */
    private Integer reassemblyInitialBytes;

    /**
     * reassembly 버퍼 최대 상한(바이트)
     */
    private Integer reassemblyMaxBytes;

    /**
     * 한 번 drain 호출 시 처리할 최대 chunk 수
     */
    private Integer maxChunksPerDrain;

    /**
     * eqp별 순차 처리용 worker thread 수(공유 풀)
     */
    private Integer workerThreads;

    /**
     * eqp별 outbound 큐 용량 (bounded 필수)
     */
    private Integer outboundQueueCapacity;

    /**
     * 한 번의 drain에서 처리할 outbound 최대 건수
     */
    private Integer maxOutboundPerDrain;

    /**
     * outbound 전송 재시도 최대 횟수
     */
    private Integer outboundRetryMax;

    /**
     * outbound 재시도 지연(ms)
     */
    private Integer outboundRetryBackoffMs;

    /**
     * outbound retry scheduler threads.
     */
    private Integer outboundRetrySchedulerThreads;

    @PostConstruct
    public void validate() {
        if (inboundQueueCapacity == null || inboundQueueCapacity <= 0) {
            throw new IllegalStateException("tc.comm.gateway.runtime.inbound-queue-capacity must be > 0");
        }
        if (reassemblyInitialBytes == null || reassemblyInitialBytes <= 0) {
            throw new IllegalStateException("tc.comm.gateway.runtime.reassembly-initial-bytes must be > 0");
        }
        if (reassemblyMaxBytes == null || reassemblyMaxBytes <= 0) {
            throw new IllegalStateException("tc.comm.gateway.runtime.reassembly-max-bytes must be > 0");
        }
        if (reassemblyInitialBytes > reassemblyMaxBytes) {
            throw new IllegalStateException("tc.comm.gateway.runtime.reassembly-initial-bytes must be <= reassembly-max-bytes");
        }
        if (maxChunksPerDrain == null || maxChunksPerDrain <= 0) {
            throw new IllegalStateException("tc.comm.gateway.runtime.max-chunks-per-drain must be > 0");
        }
        if (workerThreads == null || workerThreads <= 0) {
            throw new IllegalStateException("tc.comm.gateway.runtime.worker-threads must be > 0");
        }
        if (outboundQueueCapacity == null || outboundQueueCapacity <= 0) {
            throw new IllegalStateException("tc.comm.gateway.runtime.outbound-queue-capacity must be > 0");
        }
        if (maxOutboundPerDrain == null || maxOutboundPerDrain <= 0) {
            throw new IllegalStateException("tc.comm.gateway.runtime.max-outbound-per-drain must be > 0");
        }
        if (outboundRetryMax == null || outboundRetryMax < 0) {
            throw new IllegalStateException("tc.comm.gateway.runtime.outbound-retry-max must be >= 0");
        }
        if (outboundRetryBackoffMs == null || outboundRetryBackoffMs < 0) {
            throw new IllegalStateException("tc.comm.gateway.runtime.outbound-retry-backoff-ms must be >= 0");
        }
        if (outboundRetrySchedulerThreads == null || outboundRetrySchedulerThreads <= 0) {
            throw new IllegalStateException("tc.comm.gateway.runtime.outbound-retry-scheduler-threads must be > 0");
        }
    }

    public int getInboundQueueCapacity() {
        return inboundQueueCapacity;
    }

    public void setInboundQueueCapacity(final int inboundQueueCapacity) {
        this.inboundQueueCapacity = inboundQueueCapacity;
    }

    public int getReassemblyInitialBytes() {
        return reassemblyInitialBytes;
    }

    public void setReassemblyInitialBytes(final int reassemblyInitialBytes) {
        this.reassemblyInitialBytes = reassemblyInitialBytes;
    }

    public int getReassemblyMaxBytes() {
        return reassemblyMaxBytes;
    }

    public void setReassemblyMaxBytes(final int reassemblyMaxBytes) {
        this.reassemblyMaxBytes = reassemblyMaxBytes;
    }

    public int getMaxChunksPerDrain() {
        return maxChunksPerDrain;
    }

    public void setMaxChunksPerDrain(final int maxChunksPerDrain) {
        this.maxChunksPerDrain = maxChunksPerDrain;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(final int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getOutboundQueueCapacity() {
        return outboundQueueCapacity;
    }

    public void setOutboundQueueCapacity(final int outboundQueueCapacity) {
        this.outboundQueueCapacity = outboundQueueCapacity;
    }

    public int getMaxOutboundPerDrain() {
        return maxOutboundPerDrain;
    }

    public void setMaxOutboundPerDrain(final int maxOutboundPerDrain) {
        this.maxOutboundPerDrain = maxOutboundPerDrain;
    }

    public int getOutboundRetryMax() {
        return outboundRetryMax;
    }

    public void setOutboundRetryMax(final int outboundRetryMax) {
        this.outboundRetryMax = outboundRetryMax;
    }

    public int getOutboundRetryBackoffMs() {
        return outboundRetryBackoffMs;
    }

    public void setOutboundRetryBackoffMs(final int outboundRetryBackoffMs) {
        this.outboundRetryBackoffMs = outboundRetryBackoffMs;
    }

    public int getOutboundRetrySchedulerThreads() {
        return outboundRetrySchedulerThreads;
    }

    public void setOutboundRetrySchedulerThreads(final int outboundRetrySchedulerThreads) {
        this.outboundRetrySchedulerThreads = outboundRetrySchedulerThreads;
    }
}
