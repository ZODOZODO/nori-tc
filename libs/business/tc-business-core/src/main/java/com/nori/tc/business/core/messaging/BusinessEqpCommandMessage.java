package com.nori.tc.business.core.messaging;

import java.util.Map;

/**
 * Business Core에서 Gateway(EQP)로 전달할 명령 메시지 모델입니다.
 *
 * <p>토픽 기준:
 * {@code tc.eqp.commands}</p>
 *
 * <p>필수 값:
 * 1) eventType
 * 2) eqpId
 * 3) traceId
 * 4) interfaceType
 * 5) rawMessage</p>
 *
 * @param eventType 명령 이벤트 타입
 * @param eqpId 대상 설비 ID
 * @param traceId 추적 ID
 * @param interfaceType 설비 인터페이스 타입(SOCKET/HSMS)
 * @param rawMessage 전송할 원문 명령
 * @param transactionId 선택 transaction ID
 * @param attributes 확장 속성 맵(선택)
 */
public record BusinessEqpCommandMessage(
        String eventType,
        String eqpId,
        String traceId,
        String interfaceType,
        String rawMessage,
        String transactionId,
        Map<String, Object> attributes
) {

    /**
     * 메시지 생성 시 필수 필드를 검증합니다.
     */
    public BusinessEqpCommandMessage {
        eventType = normalizeRequired("eventType", eventType);
        eqpId = normalizeRequired("eqpId", eqpId);
        traceId = normalizeRequired("traceId", traceId);
        interfaceType = normalizeRequired("interfaceType", interfaceType);
        rawMessage = normalizeRequired("rawMessage", rawMessage);
        transactionId = normalizeNullable(transactionId);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    private static String normalizeRequired(final String field, final String value) {
        final String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String normalizeNullable(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}
