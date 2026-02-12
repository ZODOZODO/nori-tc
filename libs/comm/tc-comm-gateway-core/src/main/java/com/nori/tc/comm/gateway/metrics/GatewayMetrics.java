package com.nori.tc.comm.gateway.metrics;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Gateway metrics registry (in-memory).
 *
 * NOTE:
 * - This class does not export to any backend by itself.
 * - It provides counters/gauges that can be wired to Micrometer later.
 */
@Component
public final class GatewayMetrics {

    private final LongAdder commandsDropNoConnection = new LongAdder();
    private final LongAdder duplicateEqpRejectTotal = new LongAdder();
    private final LongAdder bindTimeoutTotal = new LongAdder();
    private final LongAdder inboundQueueOverflowTotal = new LongAdder();
    private final LongAdder outboundQueueOverflowTotal = new LongAdder();
    private final LongAdder kafkaCommitFailTotal = new LongAdder();
    private final LongAdder eventPublishSuccessTotal = new LongAdder();
    private final LongAdder eventPublishFailTotal = new LongAdder();
    private final LongAdder dlqPublishTotal = new LongAdder();
    private final LongAdder decodeFailTotal = new LongAdder();
    private final LongAdder hsmsTimeoutTotal = new LongAdder();

    private final AtomicInteger activeConnections = new AtomicInteger();
    private final AtomicInteger boundConnections = new AtomicInteger();
    private final AtomicInteger unboundConnections = new AtomicInteger();

    private final Map<String, AtomicInteger> inboundQueueDepth = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> outboundQueueDepth = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> consumerLag = new ConcurrentHashMap<>();

    // ------------------------
    // Counters
    // ------------------------

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementCommandsDropNoConnection() {
        commandsDropNoConnection.increment();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementDuplicateEqpReject() {
        duplicateEqpRejectTotal.increment();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementBindTimeout() {
        bindTimeoutTotal.increment();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementInboundQueueOverflow() {
        inboundQueueOverflowTotal.increment();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementOutboundQueueOverflow() {
        outboundQueueOverflowTotal.increment();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementKafkaCommitFail() {
        kafkaCommitFailTotal.increment();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementEventPublishSuccess() {
        eventPublishSuccessTotal.increment();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementEventPublishFail() {
        eventPublishFailTotal.increment();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementDlqPublish() {
        dlqPublishTotal.increment();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementDecodeFail() {
        decodeFailTotal.increment();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementHsmsTimeout() {
        hsmsTimeoutTotal.increment();
    }

    // ------------------------
    // Connection gauges
    // ------------------------

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void decrementActiveConnections() {
        activeConnections.updateAndGet(v -> Math.max(0, v - 1));
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementBoundConnections() {
        boundConnections.incrementAndGet();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void decrementBoundConnections() {
        boundConnections.updateAndGet(v -> Math.max(0, v - 1));
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void incrementUnboundConnections() {
        unboundConnections.incrementAndGet();
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
    public void decrementUnboundConnections() {
        unboundConnections.updateAndGet(v -> Math.max(0, v - 1));
    }

    // ------------------------
    // Queue depth sampling
    // ------------------------

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @param depth 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void recordInboundQueueDepth(final String eqpId, final int depth) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        inboundQueueDepth.computeIfAbsent(eqpId, key -> new AtomicInteger())
                .set(Math.max(0, depth));
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     * @param depth 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void recordOutboundQueueDepth(final String eqpId, final int depth) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        outboundQueueDepth.computeIfAbsent(eqpId, key -> new AtomicInteger())
                .set(Math.max(0, depth));
    }

    
    /**
     * 게이트웨이 코어 모듈 데이터 정리 또는 삭제를 처리합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param eqpId 설비 식별 정보
     */
    public void clearQueueDepth(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        inboundQueueDepth.remove(eqpId);
        outboundQueueDepth.remove(eqpId);
    }

    // ------------------------
    // Kafka consumer lag
    // ------------------------

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param topic Kafka 토픽 이름
     * @param partition 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     * @param lag 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void recordConsumerLag(final String topic, final int partition, final long lag) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        final String key = topic + "-" + partition;
        consumerLag.computeIfAbsent(key, k -> new AtomicLong()).set(Math.max(0, lag));
    }
}
