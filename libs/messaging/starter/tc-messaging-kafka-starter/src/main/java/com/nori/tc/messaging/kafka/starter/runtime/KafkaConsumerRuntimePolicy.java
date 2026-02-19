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

    /**
     * poll 스레드와 record 처리 스레드를 분리할지 여부입니다.
     *
     * <p>true면 poll 루프는 record를 worker에 위임하고,
     * ack/commit 처리에 집중하여 지연 전파를 줄입니다.</p>
     *
     * @return 비블로킹 처리 활성화 여부
     */
    default boolean asyncRecordProcessingEnabled() {
        return false;
    }

    /**
     * 비블로킹 처리 시 record worker 스레드 수입니다.
     *
     * @return worker 스레드 수(1 이상)
     */
    default int recordWorkerThreads() {
        return 1;
    }

    /**
     * poll 루프에서 한 번에 drain할 ack 이벤트 최대 개수입니다.
     *
     * @return ack drain 최대 배치 수(1 이상)
     */
    default int ackDrainMaxBatch() {
        return 512;
    }

    /**
     * poll 루프가 허용하는 최대 in-flight record 개수입니다.
     *
     * <p>이 값을 넘으면 poll 루프는 일시적으로 제출을 멈추고
     * ack/commit 처리를 먼저 수행합니다.</p>
     *
     * @return 최대 in-flight 개수(1 이상)
     */
    default int maxInFlightRecords() {
        return 10_000;
    }
}
