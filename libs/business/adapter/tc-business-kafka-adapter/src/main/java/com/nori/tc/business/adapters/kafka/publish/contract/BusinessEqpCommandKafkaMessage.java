package com.nori.tc.business.adapters.kafka.publish.contract;

/**
 * Business -> Gateway(EQP) 명령 토픽({@code tc.eqp.commands}) 전송 계약입니다.
 *
 * <p>JSON 구조는 {@code metadata + data} 고정입니다.</p>
 */
public record BusinessEqpCommandKafkaMessage(
        BusinessEqpCommandKafkaMetadata metadata,
        BusinessEqpCommandKafkaData data
) {

    /**
     * 공통 metadata 블록입니다.
     *
     * @param eventType 이벤트 타입
     * @param timestamp 발행 시각(ISO-8601)
     * @param source 발행 소스
     * @param traceId 추적 ID
     */
    public record BusinessEqpCommandKafkaMetadata(
            String eventType,
            String timestamp,
            String source,
            String traceId
    ) {
        public BusinessEqpCommandKafkaMetadata {
            requireText("eventType", eventType);
            requireText("timestamp", timestamp);
            requireText("source", source);
            requireText("traceId", traceId);
        }
    }

    /**
     * 명령 데이터 블록입니다.
     *
     * @param transactionId 선택 transaction ID
     * @param eqpId 대상 설비 ID
     * @param interfaceType 인터페이스 타입(SOCKET/HSMS)
     * @param secs2 HSMS 확장 블록(현재 선택)
     * @param rawMessage 전송 원문
     */
    public record BusinessEqpCommandKafkaData(
            String transactionId,
            String eqpId,
            String interfaceType,
            BusinessEqpCommandKafkaSecs2 secs2,
            String rawMessage
    ) {
        public BusinessEqpCommandKafkaData {
            requireText("eqpId", eqpId);
            requireText("interfaceType", interfaceType);
            requireText("rawMessage", rawMessage);
        }
    }

    /**
     * HSMS SECS2 확장 블록입니다.
     *
     * @param systemBytes system bytes
     * @param eventId event id
     * @param rawBody raw body
     */
    public record BusinessEqpCommandKafkaSecs2(
            String systemBytes,
            String eventId,
            String rawBody
    ) {
    }

    public BusinessEqpCommandKafkaMessage {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        if (data == null) {
            throw new IllegalArgumentException("data is required");
        }
    }

    private static void requireText(final String field, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
