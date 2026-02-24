package com.nori.tc.messaging.kafka.contract;

/**
 * Gateway -> UI 작업 응답 메시지 계약입니다.
 *
 * <p>요청 메시지의 메타데이터(traceId 포함)를 그대로 전달하여 요청/응답 상관관계를 유지합니다.</p>
 */
public record KafkaUiTaskReplyMessage(
        KafkaUiTaskMessage.KafkaUiTaskMetadata metadata,
        KafkaUiTaskReplyData data
) {

    /**
     * 루트 응답 메시지 필수 블록 존재 여부를 검증합니다.
     */
    public KafkaUiTaskReplyMessage {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        if (data == null) {
            throw new IllegalArgumentException("data is required");
        }
    }

    /**
     * 응답 본문 데이터 블록입니다.
     *
     * <p>{@code STATUS}는 PASS/FAIL 중 하나이며, 실패 시 에러 코드/메시지를 함께 채울 수 있습니다.</p>
     */
    public record KafkaUiTaskReplyData(
            String eqpId,
            String interfaceType,
            String STATUS,
            String ERRORMSG,
            String ERRORCODE
    ) {

        /**
         * 응답 데이터 필수 필드를 검증합니다.
         */
        public KafkaUiTaskReplyData {
            requireText("eqpId", eqpId);
            requireText("interfaceType", interfaceType);
            requireText("STATUS", STATUS);
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
