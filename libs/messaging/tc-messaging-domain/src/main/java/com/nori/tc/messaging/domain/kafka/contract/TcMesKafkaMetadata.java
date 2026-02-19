package com.nori.tc.messaging.domain.kafka.contract;

/**
 * MES 토픽용 metadata 계약입니다.
 *
 * <p>대상 토픽:
 * - tc.mes.events
 * - tc.mes.commands</p>
 *
 * <p>식별자는 correlationId(= lotId)를 사용합니다.</p>
 *
 * @param eventType 이벤트 타입
 * @param timestamp 이벤트 시각 문자열(ISO-8601 권장)
 * @param source 발행 source
 * @param correlationId MES 왕복 상관관계 식별자(= lotId)
 * @param schemaVersion 스키마 버전(기본값: v1)
 */
public record TcMesKafkaMetadata(
        String eventType,
        String timestamp,
        String source,
        String correlationId,
        String schemaVersion
) implements TcKafkaMetadata {

    /**
     * schemaVersion 기본값(v1)을 사용하는 생성자입니다.
     *
     * @param eventType 이벤트 타입
     * @param timestamp 이벤트 시각 문자열
     * @param source 발행 source
     * @param correlationId MES 왕복 상관관계 식별자
     */
    public TcMesKafkaMetadata(
            final String eventType,
            final String timestamp,
            final String source,
            final String correlationId
    ) {
        this(eventType, timestamp, source, correlationId, "v1");
    }

    /**
     * 필수 필드 유효성을 검증합니다.
     */
    public TcMesKafkaMetadata {
        requireText("eventType", eventType);
        requireText("timestamp", timestamp);
        requireText("source", source);
        requireText("correlationId", correlationId);
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

