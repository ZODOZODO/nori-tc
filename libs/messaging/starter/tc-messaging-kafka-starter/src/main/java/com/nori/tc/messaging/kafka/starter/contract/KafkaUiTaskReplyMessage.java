package com.nori.tc.messaging.kafka.starter.contract;

/**
 * Gateway -> UI task 처리 결과 응답 포맷입니다.
 *
 * <p>UI 요청 메시지의 traceId를 그대로 전달해 요청-응답 상관관계를 유지합니다.</p>
 */
public record KafkaUiTaskReplyMessage(
        KafkaUiTaskMessage.KafkaUiTaskMetadata metadata,
        KafkaUiTaskReplyData data
) {
    public KafkaUiTaskReplyMessage {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        if (data == null) {
            throw new IllegalArgumentException("data is required");
        }
    }

    /**
     * 응답 데이터 블록입니다.
     *
     * <p>STATUS는 PASS/FAIL만 허용하며, 실패 시 ERRORCODE/ERRORMSG를 채웁니다.</p>
     */
    public record KafkaUiTaskReplyData(
            String eqpId,
            String interfaceType,
            String STATUS,
            String ERRORMSG,
            String ERRORCODE
    ) {
        public KafkaUiTaskReplyData {
            requireText("eqpId", eqpId);
            requireText("interfaceType", interfaceType);
            requireText("STATUS", STATUS);
        }
    }

    private static void requireText(final String fieldName, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
