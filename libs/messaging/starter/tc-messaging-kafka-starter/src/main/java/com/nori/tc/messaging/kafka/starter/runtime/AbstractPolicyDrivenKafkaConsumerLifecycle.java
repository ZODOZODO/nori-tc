package com.nori.tc.messaging.kafka.starter.runtime;

import java.util.Objects;

/**
 * {@link KafkaConsumerRuntimePolicy} 기반 공통 정책 전달 템플릿입니다.
 *
 * <p>기존 {@link AbstractKafkaConsumerLifecycle}에서 반복되던
 * shutdown/commit/lag 정책 오버라이드를 한 곳으로 모아 중복을 제거합니다.</p>
 *
 * @param <T> consumer value 타입
 */
public abstract class AbstractPolicyDrivenKafkaConsumerLifecycle<T> extends AbstractKafkaConsumerLifecycle<T> {

    private final KafkaConsumerRuntimePolicy runtimePolicy;

    /**
     * 정책 객체를 주입받아 공통 런타임 파라미터를 위임합니다.
     */
    protected AbstractPolicyDrivenKafkaConsumerLifecycle(final KafkaConsumerRuntimePolicy runtimePolicy) {
        this.runtimePolicy = Objects.requireNonNull(runtimePolicy, "runtimePolicy is null");
    }

    @Override
    protected long shutdownWaitMs() {
        return runtimePolicy.shutdownWaitMs();
    }

    @Override
    protected int commitRetryMax() {
        return runtimePolicy.commitRetryMax();
    }

    @Override
    protected long commitRetryBackoffMs() {
        return runtimePolicy.commitRetryBackoffMs();
    }

    @Override
    protected long lagSampleIntervalMs() {
        return runtimePolicy.lagSampleIntervalMs();
    }
}
