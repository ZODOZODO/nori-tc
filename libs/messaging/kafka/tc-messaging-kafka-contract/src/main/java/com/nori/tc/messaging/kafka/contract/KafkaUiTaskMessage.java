package com.nori.tc.messaging.kafka.contract;

import com.nori.tc.comm.gateway.domain.profile.GatewayEquipmentProfileSnapshot;

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
     * <p>EQP_CREATE/EQP_UPDATE 계열 이벤트에서는 {@code equipmentProfile} payload를 함께 전달하여
     * Gateway가 DB 재조회 없이 EQP bean을 갱신할 수 있도록 합니다.</p>
     */
    public record KafkaUiTaskData(
            String eqpId,
            String interfaceType,
            String uiMessage,
            GatewayEquipmentProfileSnapshot equipmentProfile
    ) {

        /**
         * 데이터 블록 필수 텍스트 필드를 검증합니다.
         */
        public KafkaUiTaskData {
            requireText("eqpId", eqpId);
            requireText("interfaceType", interfaceType);
        }

        /**
         * 하위 호환용 생성자입니다.
         *
         * <p>기존 호출부는 eqpId/interfaceType/uiMessage만 전달하므로,
         * 신규 equipmentProfile 필드는 null로 보정합니다.</p>
         *
         * @param eqpId 설비 ID
         * @param interfaceType 인터페이스 타입
         * @param uiMessage UI 메시지(선택)
         */
        public KafkaUiTaskData(
                final String eqpId,
                final String interfaceType,
                final String uiMessage
        ) {
            this(eqpId, interfaceType, uiMessage, null);
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
