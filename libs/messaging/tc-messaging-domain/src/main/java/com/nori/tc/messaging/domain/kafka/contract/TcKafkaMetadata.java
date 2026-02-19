package com.nori.tc.messaging.domain.kafka.contract;

/**
 * Kafka envelope metadata 공통 계약 인터페이스입니다.
 *
 * <p>모든 토픽은 {@code metadata + data} 구조를 사용하며,
 * metadata는 최소한 아래 공통 필드를 포함해야 합니다.</p>
 *
 * <p>공통 필드:
 * 1) eventType
 * 2) timestamp
 * 3) source
 * 4) schemaVersion</p>
 *
 * <p>식별자 필드는 토픽 성격에 따라 분기합니다.
 * - MES 토픽: correlationId
 * - 비-MES 토픽: traceId</p>
 */
public sealed interface TcKafkaMetadata permits TcMesKafkaMetadata, TcCommonKafkaMetadata {

    /**
     * 이벤트 타입을 반환합니다.
     *
     * @return 이벤트 타입 문자열
     */
    String eventType();

    /**
     * 이벤트 발생 시각 문자열(ISO-8601 권장)을 반환합니다.
     *
     * @return 타임스탬프 문자열
     */
    String timestamp();

    /**
     * 발행자 source 식별자를 반환합니다.
     *
     * @return source 문자열
     */
    String source();

    /**
     * 스키마 버전을 반환합니다.
     *
     * @return schemaVersion 문자열
     */
    String schemaVersion();
}

