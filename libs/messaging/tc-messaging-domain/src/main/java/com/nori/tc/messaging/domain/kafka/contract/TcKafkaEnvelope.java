package com.nori.tc.messaging.domain.kafka.contract;

import java.util.Objects;

/**
 * Kafka 표준 Envelope 계약입니다.
 *
 * <p>모든 토픽 메시지는 다음 JSON 구조를 사용합니다.</p>
 *
 * <pre>
 * {
 *   "metadata": { ... },
 *   "data": { ... }
 * }
 * </pre>
 *
 * <p>구조적 일관성을 강제하여,
 * 소비자 파이프라인이 토픽별 분기 없이 공통 로직으로 동작할 수 있도록 합니다.</p>
 *
 * @param <M> metadata 타입
 * @param <D> data payload 타입
 * @param metadata 공통 메타데이터
 * @param data 도메인 페이로드
 */
public record TcKafkaEnvelope<M extends TcKafkaMetadata, D>(
        M metadata,
        D data
) {

    /**
     * Envelope 필수값을 검증합니다.
     */
    public TcKafkaEnvelope {
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(data, "data is required");
    }
}

