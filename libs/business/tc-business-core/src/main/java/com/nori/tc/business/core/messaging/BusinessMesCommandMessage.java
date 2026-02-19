package com.nori.tc.business.core.messaging;

import java.util.Map;

/**
 * Business Core에서 외부 MES 어댑터로 전달할 명령 메시지 모델입니다.
 *
 * <p>토픽 기준:
 * {@code tc.mes.commands}</p>
 *
 * <p>MES 경계 메시지는 {@code correlationId}가 필수이며,
 * Kafka key도 동일 값으로 사용됩니다.</p>
 *
 * @param eventType 명령 이벤트 타입
 * @param correlationId MES 연계 추적 ID(= lotId)
 * @param eqpId 관련 설비 ID
 * @param traceId 내부 추적 ID(선택)
 * @param data 명령 payload 데이터
 */
public record BusinessMesCommandMessage(
        String eventType,
        String correlationId,
        String eqpId,
        String traceId,
        Map<String, Object> data
) {

    /**
     * 메시지 생성 시 필수 필드를 검증합니다.
     */
    public BusinessMesCommandMessage {
        eventType = normalizeRequired("eventType", eventType);
        correlationId = normalizeRequired("correlationId", correlationId);
        eqpId = normalizeRequired("eqpId", eqpId);
        traceId = normalizeNullable(traceId);
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    /**
     * normalizeRequired 기능을 수행합니다.
     *
     * @param field 입력 값
     * @param value 입력 값
     * @return 처리 결과
     */

    private static String normalizeRequired(final String field, final String value) {
        final String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    /**
     * normalizeNullable 기능을 수행합니다.
     *
     * @param value 입력 값
     * @return 처리 결과
     */

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
