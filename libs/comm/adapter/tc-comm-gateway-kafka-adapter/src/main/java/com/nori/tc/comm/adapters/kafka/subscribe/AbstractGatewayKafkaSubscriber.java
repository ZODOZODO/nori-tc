package com.nori.tc.comm.adapters.kafka.messaging;

import com.nori.tc.comm.gateway.metrics.GatewayLogSampler;
import com.nori.tc.comm.gateway.metrics.GatewayMetrics;
import com.nori.tc.messaging.kafka.starter.runtime.AbstractPolicyDrivenKafkaConsumerLifecycle;
import com.nori.tc.messaging.kafka.starter.runtime.KafkaConsumerRuntimePolicy;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * gateway 전용 Kafka consumer 공통 베이스입니다.
 *
 * <p>역할:
 * 1) commit 실패 지표/로그 공통 처리
 * 2) consumer lag 지표 공통 처리
 * 3) 런타임 정책 값은 상위 starter 추상화로 위임</p>
 *
 * @param <T> consumer value 타입
 */
public abstract class AbstractGatewayKafkaConsumer<T> extends AbstractPolicyDrivenKafkaConsumerLifecycle<T> {

    /**
     * 하위 클래스에서 재사용 가능한 로거입니다.
     *
     * <p>클래스별 logger 이름을 유지하기 위해 인스턴스 기반으로 생성합니다.</p>
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final GatewayMetrics metrics;
    private final GatewayLogSampler logSampler;

    /**
     * 공통 지표/로그 컴포넌트와 런타임 정책을 초기화합니다.
     */
    protected AbstractGatewayKafkaConsumer(
            final KafkaConsumerRuntimePolicy runtimePolicy,
            final GatewayMetrics metrics,
            final GatewayLogSampler logSampler
    ) {
        super(runtimePolicy);
        this.metrics = Objects.requireNonNull(metrics, "metrics is null");
        this.logSampler = Objects.requireNonNull(logSampler, "logSampler is null");
    }

    /**
     * commit 실패 시 공통 지표 증가 및 샘플링 로그를 남깁니다.
     */
    @Override
    protected void onCommitFail(final Exception ex, final int attempt) {
        metrics.incrementKafkaCommitFail();
        if (logSampler.shouldLogCommitFail()) {
            log.warn("Kafka commit failed. consumer={}, attempt={}", consumerName(), attempt, ex);
        }
    }

    /**
     * lag 샘플링 시 공통 지표를 기록합니다.
     */
    @Override
    protected void onLagSample(final TopicPartition topicPartition, final long lag) {
        metrics.recordConsumerLag(topicPartition.topic(), topicPartition.partition(), lag);
    }
}
