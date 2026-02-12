package com.nori.tc.comm.gateway.metrics;

import com.nori.tc.comm.gateway.config.GatewayObservabilityProperties;
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

    
    /**
     * 게이트웨이 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param properties 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public GatewayLogSampler(final GatewayObservabilityProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties is null");
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public boolean shouldLogCommandDrop() {
        return shouldLog(commandDropCounter, properties.getCommandDropLogEvery());
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public boolean shouldLogBindTimeout() {
        return shouldLog(bindTimeoutCounter, properties.getBindTimeoutLogEvery());
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public boolean shouldLogDuplicateReject() {
        return shouldLog(duplicateRejectCounter, properties.getDuplicateRejectLogEvery());
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public boolean shouldLogQueueOverflow() {
        return shouldLog(queueOverflowCounter, properties.getQueueOverflowLogEvery());
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public boolean shouldLogCommitFail() {
        return shouldLog(commitFailCounter, properties.getCommitFailLogEvery());
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @return 처리 성공 여부
     */
    public boolean shouldLogNotOwnerReject() {
        return shouldLog(notOwnerCounter, properties.getNotOwnerLogEvery());
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param counter 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param every 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @return 처리 성공 여부
     */
    private boolean shouldLog(final LongAdder counter, final int every) {
        if (every <= 1) {
            counter.increment();
            return true;
        }
        counter.increment();
        return counter.sum() % every == 0;
    }
}
