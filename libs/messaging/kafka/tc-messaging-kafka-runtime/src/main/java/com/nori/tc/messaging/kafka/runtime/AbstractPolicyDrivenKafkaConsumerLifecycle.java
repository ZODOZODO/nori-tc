package com.nori.tc.messaging.kafka.runtime;

import java.util.Objects;

/**
 * {@link KafkaConsumerRuntimePolicy} 기반 공통 정책 위임 추상 클래스입니다.
 *
 * <p>하위 클래스는 토픽 바인딩/레코드 처리 책임에 집중하고, 종료/커밋/지연 샘플링 관련 기본 정책 값은
 * 정책 객체를 통해 일괄 제공할 수 있습니다.</p>
 *
 * @param <T> Kafka Consumer value 타입
 */
public abstract class AbstractPolicyDrivenKafkaConsumerLifecycle<T> extends AbstractKafkaConsumerLifecycle<T> {

    private final KafkaConsumerRuntimePolicy runtimePolicy;

    /**
     * 런타임 정책 객체를 주입받아 공통 파라미터를 위임합니다.
     *
     * @param runtimePolicy 런타임 정책 객체
     */
    protected AbstractPolicyDrivenKafkaConsumerLifecycle(final KafkaConsumerRuntimePolicy runtimePolicy) {
        this.runtimePolicy = Objects.requireNonNull(runtimePolicy, "runtimePolicy is null");
    }

    /**
     * 종료 대기 시간을 정책 객체에서 조회합니다.
     *
     * @return 종료 대기 시간(ms)
     */
    @Override
    protected long shutdownWaitMs() {
        return runtimePolicy.shutdownWaitMs();
    }

    /**
     * 커밋 재시도 최대 횟수를 정책 객체에서 조회합니다.
     *
     * @return 커밋 재시도 최대 횟수
     */
    @Override
    protected int commitRetryMax() {
        return runtimePolicy.commitRetryMax();
    }

    /**
     * 커밋 재시도 백오프 시간을 정책 객체에서 조회합니다.
     *
     * @return 커밋 재시도 백오프(ms)
     */
    @Override
    protected long commitRetryBackoffMs() {
        return runtimePolicy.commitRetryBackoffMs();
    }

    /**
     * lag 샘플링 주기를 정책 객체에서 조회합니다.
     *
     * @return lag 샘플링 주기(ms)
     */
    @Override
    protected long lagSampleIntervalMs() {
        return runtimePolicy.lagSampleIntervalMs();
    }

    /**
     * 비동기 레코드 처리 활성화 여부를 정책 객체에서 조회합니다.
     *
     * @return 비동기 처리 활성화 여부
     */
    @Override
    protected boolean asyncRecordProcessingEnabled() {
        return runtimePolicy.asyncRecordProcessingEnabled();
    }

    /**
     * 비동기 처리 worker 스레드 수를 정책 객체에서 조회합니다.
     *
     * @return worker 스레드 수
     */
    @Override
    protected int recordWorkerThreads() {
        return runtimePolicy.recordWorkerThreads();
    }

    /**
     * ACK drain 최대 배치 수를 정책 객체에서 조회합니다.
     *
     * @return ACK drain 최대 배치 수
     */
    @Override
    protected int ackDrainMaxBatch() {
        return runtimePolicy.ackDrainMaxBatch();
    }

    /**
     * 최대 in-flight 레코드 수를 정책 객체에서 조회합니다.
     *
     * @return 최대 in-flight 레코드 수
     */
    @Override
    protected int maxInFlightRecords() {
        return runtimePolicy.maxInFlightRecords();
    }
}
