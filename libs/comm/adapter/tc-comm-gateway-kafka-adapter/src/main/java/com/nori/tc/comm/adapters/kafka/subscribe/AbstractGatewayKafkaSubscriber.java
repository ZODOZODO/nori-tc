package com.nori.tc.comm.adapters.kafka.subscribe;

import com.nori.tc.comm.gateway.metrics.GatewayLogSampler;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
import com.nori.tc.messaging.kafka.starter.runtime.AbstractPolicyDrivenKafkaConsumerLifecycle;
import com.nori.tc.messaging.kafka.starter.runtime.KafkaConsumerRuntimePolicy;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Gateway Kafka 구독자 공통 추상 클래스입니다.
 *
 * <p>공통 책임:
 * 1) commit 실패 메트릭/로그 처리
 * 2) consumer lag 메트릭 샘플 기록
 * 3) 실제 구독/처리 로직은 하위 클래스에서 구현</p>
 *
 * @param <T> consumer value 타입
 */
public abstract class AbstractGatewayKafkaSubscriber<T> extends AbstractPolicyDrivenKafkaConsumerLifecycle<T> {

    /**
     * 하위 클래스에서 바로 사용할 수 있는 클래스 기반 로거입니다.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;

    /**
     * 공통 메트릭/로그 컴포넌트를 초기화합니다.
     *
     * @param runtimePolicy consumer 런타임 정책
     * @param metrics gateway 메트릭 포트
     * @param logSampler 샘플링 로그 정책
     */
    protected AbstractGatewayKafkaSubscriber(
            final KafkaConsumerRuntimePolicy runtimePolicy,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler
    ) {
        super(runtimePolicy);
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
    }

    /**
     * commit 실패 시 공통 메트릭을 증가시키고 샘플링 로그를 남깁니다.
     *
     * @param ex 실패 예외
     * @param attempt 재시도 횟수
     */
    @Override
    protected void onCommitFail(final Exception ex, final int attempt) {
        metrics.incrementKafkaCommitFail();
        if (logSampler.shouldLogCommitFail()) {
            log.warn("Kafka commit failed. consumer={}, attempt={}", consumerName(), attempt, ex);
        }
    }

    /**
     * lag 샘플 수집 시 topic/partition 단위 메트릭을 기록합니다.
     *
     * @param topicPartition 대상 topic partition
     * @param lag 측정된 lag 값
     */
    @Override
    protected void onLagSample(final TopicPartition topicPartition, final long lag) {
        metrics.recordConsumerLag(topicPartition.topic(), topicPartition.partition(), lag);
    }
}
