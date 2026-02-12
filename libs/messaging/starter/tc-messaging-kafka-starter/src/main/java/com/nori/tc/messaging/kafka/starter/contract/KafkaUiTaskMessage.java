package com.nori.tc.messaging.kafka.starter.contract;

/**
 * UI -> Gateway task 요청 표준 메시지 포맷입니다.
 *
 * <p>JSON envelope는 {@code metadata + data} 구조를 고정으로 사용합니다.</p>
 */
public record KafkaUiTaskMessage(
        KafkaUiTaskMetadata metadata,
        KafkaUiTaskData data
) {
    public KafkaUiTaskMessage {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        if (data == null) {
            throw new IllegalArgumentException("data is required");
        }
    }

    /**
     * 공통 메타데이터 블록입니다.
     *
     * <p>eventType, traceId는 gateway 라우팅과 추적 상 필수입니다.</p>
     */
    public record KafkaUiTaskMetadata(
            String eventType,
            String timestamp,
            String source,
            String traceId
    ) {
        public KafkaUiTaskMetadata {
            requireText("eventType", eventType);
            requireText("timestamp", timestamp);
            requireText("source", source);
            requireText("traceId", traceId);
        }
    }

    /**
     * 태스크 데이터 블록입니다.
     *
     * <p>eqpId/interfaceType으로 대상 장비를 식별하고,
     * 필요할 때 uiMessage에 원문 제어 문자열을 담아 전달합니다.</p>
     */
    public record KafkaUiTaskData(
            String eqpId,
            String interfaceType,
            String uiMessage
    ) {
        public KafkaUiTaskData {
            requireText("eqpId", eqpId);
            requireText("interfaceType", interfaceType);
        }
    }

    private static void requireText(final String fieldName, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
