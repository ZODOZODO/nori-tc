package com.nori.tc.comm.gateway.metrics;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Gateway 런타임 메트릭 저장소입니다.
 *
 * <p>내부 카운터/게이지를 메모리에 누적하고,
 * Micrometer 레지스트리가 존재하면 동일 값을 외부 모니터링으로 노출합니다.</p>
 */
@Component
public final class GatewayMetrics implements MeterBinder {

    /**
     * 메트릭 바인딩 상태 로그를 위한 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(GatewayMetrics.class);

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

    /**
     * 동일 인스턴스의 중복 바인딩을 방지하기 위한 플래그입니다.
     */
    private final AtomicBoolean meterBound = new AtomicBoolean(false);

    /**
     * Micrometer 레지스트리에 Gateway 메트릭을 등록합니다.
     *
     * @param registry meter registry
     */
    @Override
    public void bindTo(final MeterRegistry registry) {
        if (!meterBound.compareAndSet(false, true)) {
            if (log.isDebugEnabled()) {
                log.debug("Gateway metrics binder already registered. registration is skipped.");
            }
            return;
        }

        FunctionCounter.builder("tc.gateway.commands.drop.no_connection.total", commandsDropNoConnection, LongAdder::sum)
                .description("Commands dropped because equipment is not connected")
                .register(registry);
        FunctionCounter.builder("tc.gateway.duplicate_eqp.reject.total", duplicateEqpRejectTotal, LongAdder::sum)
                .description("Duplicate equipment registration rejects")
                .register(registry);
        FunctionCounter.builder("tc.gateway.bind.timeout.total", bindTimeoutTotal, LongAdder::sum)
                .description("Bind timeout occurrences")
                .register(registry);
        FunctionCounter.builder("tc.gateway.queue.inbound.overflow.total", inboundQueueOverflowTotal, LongAdder::sum)
                .description("Inbound queue overflow occurrences")
                .register(registry);
        FunctionCounter.builder("tc.gateway.queue.outbound.overflow.total", outboundQueueOverflowTotal, LongAdder::sum)
                .description("Outbound queue overflow occurrences")
                .register(registry);
        FunctionCounter.builder("tc.gateway.kafka.commit.fail.total", kafkaCommitFailTotal, LongAdder::sum)
                .description("Kafka commit failure occurrences")
                .register(registry);
        FunctionCounter.builder("tc.gateway.event.publish.success.total", eventPublishSuccessTotal, LongAdder::sum)
                .description("Event publish success count")
                .register(registry);
        FunctionCounter.builder("tc.gateway.event.publish.fail.total", eventPublishFailTotal, LongAdder::sum)
                .description("Event publish failure count")
                .register(registry);
        FunctionCounter.builder("tc.gateway.dlq.publish.total", dlqPublishTotal, LongAdder::sum)
                .description("DLQ publish count")
                .register(registry);
        FunctionCounter.builder("tc.gateway.decode.fail.total", decodeFailTotal, LongAdder::sum)
                .description("Decode failure count")
                .register(registry);
        FunctionCounter.builder("tc.gateway.hsms.timeout.total", hsmsTimeoutTotal, LongAdder::sum)
                .description("HSMS timeout count")
                .register(registry);

        Gauge.builder("tc.gateway.connection.active", activeConnections, AtomicInteger::get)
                .description("Active connection count")
                .register(registry);
        Gauge.builder("tc.gateway.connection.bound", boundConnections, AtomicInteger::get)
                .description("Bound connection count")
                .register(registry);
        Gauge.builder("tc.gateway.connection.unbound", unboundConnections, AtomicInteger::get)
                .description("Unbound connection count")
                .register(registry);

        Gauge.builder("tc.gateway.queue.inbound.depth.total", this, GatewayMetrics::sumInboundQueueDepth)
                .description("Sum of inbound queue depths")
                .register(registry);
        Gauge.builder("tc.gateway.queue.outbound.depth.total", this, GatewayMetrics::sumOutboundQueueDepth)
                .description("Sum of outbound queue depths")
                .register(registry);
        Gauge.builder("tc.gateway.kafka.consumer.lag.total", this, GatewayMetrics::sumConsumerLag)
                .description("Sum of tracked Kafka consumer lag")
                .register(registry);

        log.info("Gateway metrics binder registered to MeterRegistry.");
    }

    /** 비연결 상태 명령 드롭 카운터를 증가시킵니다. */
    public void incrementCommandsDropNoConnection() {
        commandsDropNoConnection.increment();
    }

    /** 중복 설비 등록 거부 카운터를 증가시킵니다. */
    public void incrementDuplicateEqpReject() {
        duplicateEqpRejectTotal.increment();
    }

    /** 바인드 타임아웃 카운터를 증가시킵니다. */
    public void incrementBindTimeout() {
        bindTimeoutTotal.increment();
    }

    /** 인바운드 큐 오버플로우 카운터를 증가시킵니다. */
    public void incrementInboundQueueOverflow() {
        inboundQueueOverflowTotal.increment();
    }

    /** 아웃바운드 큐 오버플로우 카운터를 증가시킵니다. */
    public void incrementOutboundQueueOverflow() {
        outboundQueueOverflowTotal.increment();
    }

    /** Kafka 커밋 실패 카운터를 증가시킵니다. */
    public void incrementKafkaCommitFail() {
        kafkaCommitFailTotal.increment();
    }

    /** 이벤트 발행 성공 카운터를 증가시킵니다. */
    public void incrementEventPublishSuccess() {
        eventPublishSuccessTotal.increment();
    }

    /** 이벤트 발행 실패 카운터를 증가시킵니다. */
    public void incrementEventPublishFail() {
        eventPublishFailTotal.increment();
    }

    /** DLQ 발행 카운터를 증가시킵니다. */
    public void incrementDlqPublish() {
        dlqPublishTotal.increment();
    }

    /** 디코딩 실패 카운터를 증가시킵니다. */
    public void incrementDecodeFail() {
        decodeFailTotal.increment();
    }

    /** HSMS 타임아웃 카운터를 증가시킵니다. */
    public void incrementHsmsTimeout() {
        hsmsTimeoutTotal.increment();
    }

    /** 활성 연결 수를 증가시킵니다. */
    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    /** 활성 연결 수를 감소시킵니다(최소 0). */
    public void decrementActiveConnections() {
        activeConnections.updateAndGet(v -> Math.max(0, v - 1));
    }

    /** 바운드 연결 수를 증가시킵니다. */
    public void incrementBoundConnections() {
        boundConnections.incrementAndGet();
    }

    /** 바운드 연결 수를 감소시킵니다(최소 0). */
    public void decrementBoundConnections() {
        boundConnections.updateAndGet(v -> Math.max(0, v - 1));
    }

    /** 언바운드 연결 수를 증가시킵니다. */
    public void incrementUnboundConnections() {
        unboundConnections.incrementAndGet();
    }

    /** 언바운드 연결 수를 감소시킵니다(최소 0). */
    public void decrementUnboundConnections() {
        unboundConnections.updateAndGet(v -> Math.max(0, v - 1));
    }

    /**
     * 설비별 인바운드 큐 깊이를 기록합니다.
     */
    public void recordInboundQueueDepth(final String eqpId, final int depth) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        inboundQueueDepth.computeIfAbsent(eqpId, key -> new AtomicInteger())
                .set(Math.max(0, depth));
    }

    /**
     * 설비별 아웃바운드 큐 깊이를 기록합니다.
     */
    public void recordOutboundQueueDepth(final String eqpId, final int depth) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        outboundQueueDepth.computeIfAbsent(eqpId, key -> new AtomicInteger())
                .set(Math.max(0, depth));
    }

    /**
     * 설비 제거 시 큐 깊이 샘플을 정리합니다.
     */
    public void clearQueueDepth(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            return;
        }
        inboundQueueDepth.remove(eqpId);
        outboundQueueDepth.remove(eqpId);
    }

    /**
     * 토픽/파티션별 consumer lag를 기록합니다.
     */
    public void recordConsumerLag(final String topic, final int partition, final long lag) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        final String key = topic + "-" + partition;
        consumerLag.computeIfAbsent(key, k -> new AtomicLong()).set(Math.max(0, lag));
    }

    /** 인바운드 큐 깊이 총합을 계산합니다. */
    private static double sumInboundQueueDepth(final GatewayMetrics metrics) {
        return sumAtomicIntegerMap(metrics.inboundQueueDepth);
    }

    /** 아웃바운드 큐 깊이 총합을 계산합니다. */
    private static double sumOutboundQueueDepth(final GatewayMetrics metrics) {
        return sumAtomicIntegerMap(metrics.outboundQueueDepth);
    }

    /** Consumer lag 총합을 계산합니다. */
    private static double sumConsumerLag(final GatewayMetrics metrics) {
        long total = 0L;
        for (AtomicLong value : metrics.consumerLag.values()) {
            if (value != null) {
                total += Math.max(0L, value.get());
            }
        }
        return total;
    }

    /** AtomicInteger 맵 값의 합계를 계산합니다. */
    private static double sumAtomicIntegerMap(final Map<String, AtomicInteger> source) {
        long total = 0L;
        for (AtomicInteger value : source.values()) {
            if (value != null) {
                total += Math.max(0, value.get());
            }
        }
        return total;
    }
}
