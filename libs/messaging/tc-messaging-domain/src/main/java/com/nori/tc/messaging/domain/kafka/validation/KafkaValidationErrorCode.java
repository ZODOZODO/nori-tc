package com.nori.tc.messaging.domain.kafka.validation;

/**
 * Kafka 계약 검증 실패 에러코드 표준값입니다.
 */
public enum KafkaValidationErrorCode {

    /**
     * 토픽명이 비어 있거나 지원 범위 밖인 경우입니다.
     */
    INVALID_TOPIC,

    /**
     * metadata 자체가 누락된 경우입니다.
     */
    MISSING_METADATA,

    /**
     * 이벤트 타입이 비어 있는 경우입니다.
     */
    MISSING_EVENT_TYPE,

    /**
     * 이벤트 타입 네이밍 규칙을 위반한 경우입니다.
     */
    INVALID_EVENT_TYPE,

    /**
     * source 값이 비어 있는 경우입니다.
     */
    MISSING_SOURCE,

    /**
     * source 값이 토픽 allowlist에 포함되지 않은 경우입니다.
     */
    SOURCE_NOT_ALLOWED,

    /**
     * schemaVersion이 비어 있는 경우입니다.
     */
    MISSING_SCHEMA_VERSION,

    /**
     * schemaVersion이 허용 버전 정책을 위반한 경우입니다.
     */
    SCHEMA_VERSION_NOT_ALLOWED,

    /**
     * 비-MES 토픽에서 traceId가 누락된 경우입니다.
     */
    MISSING_TRACE_ID,

    /**
     * MES 토픽에서 correlationId가 누락된 경우입니다.
     */
    MISSING_CORRELATION_ID,

    /**
     * 토픽 성격과 metadata 타입이 맞지 않는 경우입니다.
     */
    METADATA_TYPE_MISMATCH
}

