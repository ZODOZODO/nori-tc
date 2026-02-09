package com.nori.tc.apps.commgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
    private int inboundQueueCapacity = 2048;

    /**
     * reassembly 버퍼 초기 용량(바이트)
     */
    private int reassemblyInitialBytes = 4096;

    /**
     * reassembly 버퍼 최대 상한(바이트)
     */
    private int reassemblyMaxBytes = 1_048_576;

    /**
     * 한 번 drain 호출 시 처리할 최대 chunk 수
     */
    private int maxChunksPerDrain = 64;

    /**
     * eqp별 순차 처리용 worker thread 수(공유 풀)
     */
    private int workerThreads = 8;

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
}
