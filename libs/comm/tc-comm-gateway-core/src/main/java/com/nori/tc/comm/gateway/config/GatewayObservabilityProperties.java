package com.nori.tc.comm.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

/**
 * Observability configuration (log sampling / metrics behavior).
 */
@ConfigurationProperties(prefix = "tc.comm.gateway.observability")
public class GatewayObservabilityProperties {

    private static final Logger log = LoggerFactory.getLogger(GatewayObservabilityProperties.class);

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

    
    /**
     * 게이트웨이 코어 모듈 입력/설정 유효성을 검증합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
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
        log.info("GatewayObservabilityProperties validated. commandDropLogEvery={}, bindTimeoutLogEvery={}, duplicateRejectLogEvery={}",
                commandDropLogEvery, bindTimeoutLogEvery, duplicateRejectLogEvery);
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getCommandDropLogEvery() {
        return commandDropLogEvery;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param commandDropLogEvery 처리할 요청/명령 정보
     */
    public void setCommandDropLogEvery(final int commandDropLogEvery) {
        this.commandDropLogEvery = commandDropLogEvery;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getBindTimeoutLogEvery() {
        return bindTimeoutLogEvery;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param bindTimeoutLogEvery 시간 관련 설정 값
     */
    public void setBindTimeoutLogEvery(final int bindTimeoutLogEvery) {
        this.bindTimeoutLogEvery = bindTimeoutLogEvery;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getDuplicateRejectLogEvery() {
        return duplicateRejectLogEvery;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param duplicateRejectLogEvery 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setDuplicateRejectLogEvery(final int duplicateRejectLogEvery) {
        this.duplicateRejectLogEvery = duplicateRejectLogEvery;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getQueueOverflowLogEvery() {
        return queueOverflowLogEvery;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param queueOverflowLogEvery 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setQueueOverflowLogEvery(final int queueOverflowLogEvery) {
        this.queueOverflowLogEvery = queueOverflowLogEvery;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getCommitFailLogEvery() {
        return commitFailLogEvery;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param commitFailLogEvery 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setCommitFailLogEvery(final int commitFailLogEvery) {
        this.commitFailLogEvery = commitFailLogEvery;
    }

    
    /**
     * 게이트웨이 코어 모듈의 현재 값을 조회합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 게이트웨이 코어 모듈 처리 결과
     */
    public int getNotOwnerLogEvery() {
        return notOwnerLogEvery;
    }

    
    /**
     * 게이트웨이 코어 모듈 설정 값을 반영합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param notOwnerLogEvery 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void setNotOwnerLogEvery(final int notOwnerLogEvery) {
        this.notOwnerLogEvery = notOwnerLogEvery;
    }
}
