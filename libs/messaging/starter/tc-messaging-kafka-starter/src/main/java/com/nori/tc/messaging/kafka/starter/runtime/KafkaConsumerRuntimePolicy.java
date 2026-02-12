package com.nori.tc.messaging.kafka.starter.runtime;

/**
 * Kafka Consumer 런타임 동작 정책을 공통화한 계약입니다.
 *
 * <p>여러 애플리케이션(gateway/business/ui-backend)이 동일한 소비/커밋/지표 샘플링
 * 규칙을 재사용할 수 있도록 최소 공통 정책을 분리했습니다.</p>
 */
public interface KafkaConsumerRuntimePolicy {

    /**
     * Consumer 종료 시 worker thread join 대기 시간(ms)입니다.
     */
    long shutdownWaitMs();

    /**
     * commit 실패 시 재시도 최대 횟수입니다.
     */
    int commitRetryMax();

    /**
     * commit 재시도 backoff(ms)입니다.
     */
    long commitRetryBackoffMs();

    /**
     * lag 샘플링 주기(ms)입니다.
     */
    long lagSampleIntervalMs();
}
