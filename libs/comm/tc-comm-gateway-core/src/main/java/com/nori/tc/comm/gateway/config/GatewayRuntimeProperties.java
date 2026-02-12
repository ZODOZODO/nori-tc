package com.nori.tc.comm.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(GatewayRuntimeProperties.class);

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

    
    /**
     * 게이트웨이 코어 모듈 입력/설정 유효성을 검증합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
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
        log.info("GatewayRuntimeProperties validated. inboundQueueCapacity={}, outboundQueueCapacity={}, workerThreads={}",
                inboundQueueCapacity, outboundQueueCapacity, workerThreads);
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getInboundQueueCapacity() {
        return inboundQueueCapacity;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param inboundQueueCapacity 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setInboundQueueCapacity(final int inboundQueueCapacity) {
        this.inboundQueueCapacity = inboundQueueCapacity;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getReassemblyInitialBytes() {
        return reassemblyInitialBytes;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param reassemblyInitialBytes 처리할 원본 데이터
     */
    public void setReassemblyInitialBytes(final int reassemblyInitialBytes) {
        this.reassemblyInitialBytes = reassemblyInitialBytes;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getReassemblyMaxBytes() {
        return reassemblyMaxBytes;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param reassemblyMaxBytes 처리할 원본 데이터
     */
    public void setReassemblyMaxBytes(final int reassemblyMaxBytes) {
        this.reassemblyMaxBytes = reassemblyMaxBytes;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getMaxChunksPerDrain() {
        return maxChunksPerDrain;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param maxChunksPerDrain 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setMaxChunksPerDrain(final int maxChunksPerDrain) {
        this.maxChunksPerDrain = maxChunksPerDrain;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getWorkerThreads() {
        return workerThreads;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param workerThreads 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setWorkerThreads(final int workerThreads) {
        this.workerThreads = workerThreads;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getOutboundQueueCapacity() {
        return outboundQueueCapacity;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param outboundQueueCapacity 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setOutboundQueueCapacity(final int outboundQueueCapacity) {
        this.outboundQueueCapacity = outboundQueueCapacity;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getMaxOutboundPerDrain() {
        return maxOutboundPerDrain;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param maxOutboundPerDrain 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setMaxOutboundPerDrain(final int maxOutboundPerDrain) {
        this.maxOutboundPerDrain = maxOutboundPerDrain;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getOutboundRetryMax() {
        return outboundRetryMax;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param outboundRetryMax 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setOutboundRetryMax(final int outboundRetryMax) {
        this.outboundRetryMax = outboundRetryMax;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getOutboundRetryBackoffMs() {
        return outboundRetryBackoffMs;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param outboundRetryBackoffMs 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setOutboundRetryBackoffMs(final int outboundRetryBackoffMs) {
        this.outboundRetryBackoffMs = outboundRetryBackoffMs;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getOutboundRetrySchedulerThreads() {
        return outboundRetrySchedulerThreads;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param outboundRetrySchedulerThreads 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setOutboundRetrySchedulerThreads(final int outboundRetrySchedulerThreads) {
        this.outboundRetrySchedulerThreads = outboundRetrySchedulerThreads;
    }
}
