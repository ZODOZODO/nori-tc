package com.nori.tc.business.adapters.kafka.publish.contract;

import java.util.Map;

/**
 * Business -> MES 명령 토픽({@code tc.mes.commands}) 전송 계약입니다.
 *
 * <p>JSON 구조는 {@code metadata + data} 고정입니다.</p>
 */
public record BusinessMesCommandKafkaMessage(
        BusinessMesCommandKafkaMetadata metadata,
        Map<String, Object> data
) {

    /**
     * MES 명령 metadata 블록입니다.
     *
     * @param eventType 이벤트 타입
     * @param timestamp 발행 시각(ISO-8601)
     * @param source 발행 소스
     * @param correlationId MES 연계 추적 ID(= lotId)
     */
    public record BusinessMesCommandKafkaMetadata(
            String eventType,
            String timestamp,
            String source,
            String correlationId
    ) {
        public BusinessMesCommandKafkaMetadata {
            requireText("eventType", eventType);
            requireText("timestamp", timestamp);
            requireText("source", source);
            requireText("correlationId", correlationId);
        }
    }

    public BusinessMesCommandKafkaMessage {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    /**
     * requireText 기능을 수행합니다.
     *
     * @param field 입력 값
     * @param value 입력 값
     */

    private static void requireText(final String field, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
