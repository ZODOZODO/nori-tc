package com.nori.tc.messaging.kafka.contract;

/**
 * UI -> Gateway 작업 요청 메시지 계약입니다.
 *
 * <p>JSON Envelope 구조를 {@code metadata + data} 형태로 고정하여 사용합니다.</p>
 */
public record KafkaUiTaskMessage(
        KafkaUiTaskMetadata metadata,
        KafkaUiTaskData data
) {

    /**
     * 루트 메시지 필수 블록 존재 여부를 검증합니다.
     */
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
     * <p>이벤트 타입, 발생 시각, 발행 주체, 추적 ID를 포함합니다.</p>
     */
    public record KafkaUiTaskMetadata(
            String eventType,
            String timestamp,
            String source,
            String traceId
    ) {

        /**
         * 메타데이터 필수 텍스트 필드를 검증합니다.
         */
        public KafkaUiTaskMetadata {
            requireText("eventType", eventType);
            requireText("timestamp", timestamp);
            requireText("source", source);
            requireText("traceId", traceId);
        }
    }

    /**
     * UI 작업 본문 데이터 블록입니다.
     *
     * <p>설비 식별자와 인터페이스 타입은 필수이며, {@code uiMessage}는 상황에 따라 비어 있을 수 있습니다.</p>
     */
    public record KafkaUiTaskData(
            String eqpId,
            String interfaceType,
            String uiMessage
    ) {

        /**
         * 데이터 블록 필수 텍스트 필드를 검증합니다.
         */
        public KafkaUiTaskData {
            requireText("eqpId", eqpId);
            requireText("interfaceType", interfaceType);
        }
    }

    /**
     * 공통 텍스트 필드 검증 로직입니다.
     *
     * @param fieldName 필드명(예외 메시지용)
     * @param value     검증 대상 값
     */
    private static void requireText(final String fieldName, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
