package com.nori.tc.messaging.kafka.runtime;

/**
 * Kafka Consumer 런타임 공통 동작 정책 인터페이스입니다.
 *
 * <p>여러 애플리케이션(gateway/business/ui-backend)이 동일한 소비/커밋/지연 샘플링 규칙을
 * 재사용할 수 있도록 최소 정책 집합을 정의합니다.</p>
 */
public interface KafkaConsumerRuntimePolicy {

    /**
     * Consumer 종료 시 worker thread join 대기 시간(ms)을 반환합니다.
     *
     * @return 종료 대기 시간(ms)
     */
    long shutdownWaitMs();

    /**
     * 커밋 실패 시 재시도 최대 횟수를 반환합니다.
     *
     * @return 최대 재시도 횟수
     */
    int commitRetryMax();

    /**
     * 커밋 재시도 간 백오프 시간을 반환합니다.
     *
     * @return 백오프 시간(ms)
     */
    long commitRetryBackoffMs();

    /**
     * Consumer lag 샘플링 주기를 반환합니다.
     *
     * @return lag 샘플링 주기(ms)
     */
    long lagSampleIntervalMs();

    /**
     * poll 스레드와 레코드 처리 스레드를 분리할지 여부를 반환합니다.
     *
     * <p>{@code true}이면 poll 루프는 레코드를 worker에 위임하고, ACK/커밋 처리를 병행합니다.</p>
     *
     * @return 비동기 레코드 처리 활성화 여부
     */
    default boolean asyncRecordProcessingEnabled() {
        return false;
    }

    /**
     * 비동기 처리 활성화 시 레코드 처리 worker 스레드 수를 반환합니다.
     *
     * @return worker 스레드 수(1 이상)
     */
    default int recordWorkerThreads() {
        return 1;
    }

    /**
     * poll 루프에서 한 번에 drain할 ACK 이벤트 최대 개수를 반환합니다.
     *
     * @return ACK drain 최대 개수(1 이상)
     */
    default int ackDrainMaxBatch() {
        return 512;
    }

    /**
     * poll 루프가 허용하는 최대 in-flight 레코드 개수를 반환합니다.
     *
     * @return 최대 in-flight 개수(1 이상)
     */
    default int maxInFlightRecords() {
        return 10_000;
    }
}
