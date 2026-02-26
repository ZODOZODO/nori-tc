package com.nori.tc.messaging.domain.kafka.contract;

/**
 * 비-MES 토픽용 metadata 계약입니다.
 *
 * <p>대상 토픽:
 * - tc.eqp.events
 * - tc.eqp.commands
 * - tc.ui.events.gateway
 * - tc.ui.events.business
 * - tc.ui.commands</p>
 *
 * <p>식별자는 traceId를 사용합니다.</p>
 *
 * @param eventType 이벤트 타입
 * @param timestamp 이벤트 시각 문자열(ISO-8601 권장)
 * @param source 발행 source
 * @param traceId 분산 추적 식별자
 * @param schemaVersion 스키마 버전(기본값: v1)
 */
public record TcCommonKafkaMetadata(
        String eventType,
        String timestamp,
        String source,
        String traceId,
        String schemaVersion
) implements TcKafkaMetadata {

    /**
     * schemaVersion 기본값(v1)을 사용하는 생성자입니다.
     *
     * @param eventType 이벤트 타입
     * @param timestamp 이벤트 시각 문자열
     * @param source 발행 source
     * @param traceId 분산 추적 식별자
     */
    public TcCommonKafkaMetadata(
            final String eventType,
            final String timestamp,
            final String source,
            final String traceId
    ) {
        this(eventType, timestamp, source, traceId, "v1");
    }

    /**
     * 필수 필드 유효성을 검증합니다.
     */
    public TcCommonKafkaMetadata {
        requireText("eventType", eventType);
        requireText("timestamp", timestamp);
        requireText("source", source);
        requireText("traceId", traceId);
        requireText("schemaVersion", schemaVersion);
    }

    /**
     * 문자열 필수값을 검증합니다.
     *
     * @param fieldName 필드명
     * @param value 검증값
     */
    private static void requireText(final String fieldName, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
