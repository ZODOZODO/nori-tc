package com.nori.tc.comm.gateway.observability.logging;

import com.nori.tc.comm.gateway.config.props.GatewayObservabilityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * 게이트웨이 공통 로그 샘플링 유틸리티입니다.
 *
 * <p>이 클래스는 이벤트 유형별로 발생 횟수를 누적하고, 설정된 주기(`every`)에 맞춰
 * 실제 로그를 남길지 여부를 판정합니다.</p>
 * <p>예: `every=100`이면 100번째, 200번째, 300번째 이벤트에서만 로그를 출력합니다.</p>
 */
@Component
public final class GatewayLogSampler {

    private static final Logger log = LoggerFactory.getLogger(GatewayLogSampler.class);
    private static final int ALWAYS_LOG_EVERY = 1;

    /**
     * 샘플링 주기 정책을 제공하는 설정 객체입니다.
     */
    private final GatewayObservabilityProperties properties;

    /**
     * 유형별 누적 카운터입니다.
     *
     * <p>LongAdder는 다중 스레드 환경에서 증가 연산 충돌을 줄여 주므로,
     * 고빈도 이벤트 샘플링에 적합합니다.</p>
     */
    private final LongAdder commandDropCounter = new LongAdder();
    private final LongAdder bindTimeoutCounter = new LongAdder();
    private final LongAdder duplicateRejectCounter = new LongAdder();
    private final LongAdder queueOverflowCounter = new LongAdder();
    private final LongAdder commitFailCounter = new LongAdder();
    private final LongAdder notOwnerCounter = new LongAdder();

    /**
     * 로그 샘플러를 초기화합니다.
     *
     * <p>초기화 시점에 현재 샘플링 정책을 INFO 로그로 남겨 운영자가 즉시 확인할 수 있도록 합니다.</p>
     *
     * @param properties observability 설정
     */
    public GatewayLogSampler(final GatewayObservabilityProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties is null");
        log.info(
                "GatewayLogSampler initialized. commandDropEvery={}, bindTimeoutEvery={}, duplicateRejectEvery={}, queueOverflowEvery={}, commitFailEvery={}, notOwnerEvery={}",
                this.properties.getCommandDropLogEvery(),
                this.properties.getBindTimeoutLogEvery(),
                this.properties.getDuplicateRejectLogEvery(),
                this.properties.getQueueOverflowLogEvery(),
                this.properties.getCommitFailLogEvery(),
                this.properties.getNotOwnerLogEvery()
        );
    }

    /**
     * 채널 미연결 상태에서 명령이 드롭되는 이벤트의 로그 출력 여부를 판정합니다.
     *
     * @return 이번 이벤트를 로그로 남겨야 하면 true
     */
    public boolean shouldLogCommandDrop() {
        return shouldLog("COMMAND_DROP", commandDropCounter, properties.getCommandDropLogEvery());
    }

    /**
     * 바인딩 타임아웃 이벤트의 로그 출력 여부를 판정합니다.
     *
     * @return 이번 이벤트를 로그로 남겨야 하면 true
     */
    public boolean shouldLogBindTimeout() {
        return shouldLog("BIND_TIMEOUT", bindTimeoutCounter, properties.getBindTimeoutLogEvery());
    }

    /**
     * 중복 eqpId 연결 거부 이벤트의 로그 출력 여부를 판정합니다.
     *
     * @return 이번 이벤트를 로그로 남겨야 하면 true
     */
    public boolean shouldLogDuplicateReject() {
        return shouldLog("DUPLICATE_REJECT", duplicateRejectCounter, properties.getDuplicateRejectLogEvery());
    }

    /**
     * 큐 오버플로우 이벤트의 로그 출력 여부를 판정합니다.
     *
     * @return 이번 이벤트를 로그로 남겨야 하면 true
     */
    public boolean shouldLogQueueOverflow() {
        return shouldLog("QUEUE_OVERFLOW", queueOverflowCounter, properties.getQueueOverflowLogEvery());
    }

    /**
     * Kafka commit 실패 이벤트의 로그 출력 여부를 판정합니다.
     *
     * @return 이번 이벤트를 로그로 남겨야 하면 true
     */
    public boolean shouldLogCommitFail() {
        return shouldLog("COMMIT_FAIL", commitFailCounter, properties.getCommitFailLogEvery());
    }

    /**
     * NOT_OWNER_PARTITION 거부 이벤트의 로그 출력 여부를 판정합니다.
     *
     * @return 이번 이벤트를 로그로 남겨야 하면 true
     */
    public boolean shouldLogNotOwnerReject() {
        return shouldLog("NOT_OWNER_REJECT", notOwnerCounter, properties.getNotOwnerLogEvery());
    }

    /**
     * 공통 샘플링 판정 로직입니다.
     *
     * <p>처리 순서:</p>
     * <p>1) every 값을 최소 1로 정규화합니다.</p>
     * <p>2) 카운터를 증가시킨 뒤 현재 누적값을 읽습니다.</p>
     * <p>3) `누적값 % 주기 == 0`이면 로그 출력 대상으로 판정합니다.</p>
     *
     * @param metricName 로그 메트릭 이름
     * @param counter 해당 메트릭의 누적 카운터
     * @param every 샘플링 주기(1 이하면 항상 로깅)
     * @return 로그 출력 대상이면 true
     */
    private boolean shouldLog(final String metricName, final LongAdder counter, final int every) {
        final int normalizedEvery = Math.max(every, ALWAYS_LOG_EVERY);
        counter.increment();
        final long count = counter.sum();
        final boolean shouldLog = normalizedEvery == ALWAYS_LOG_EVERY || count % normalizedEvery == 0L;

        if (shouldLog && normalizedEvery > ALWAYS_LOG_EVERY && log.isDebugEnabled()) {
            log.debug("Log sample accepted. metric={}, count={}, every={}", metricName, count, normalizedEvery);
        }
        return shouldLog;
    }
}
