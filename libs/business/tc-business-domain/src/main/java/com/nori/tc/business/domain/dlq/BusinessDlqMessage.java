package com.nori.tc.business.domain.dlq;

import java.util.Map;
import java.util.Objects;

/**
 * Business Core 계열 DLQ 표준 메시지입니다.
 *
 * <p>설계 목적:</p>
 * <p>1) 코어/어댑터가 저장소 구현(Redis/DB/Kafka)에 의존하지 않도록
 * 기술 중립적인 DLQ 메타데이터 계약을 고정합니다.</p>
 * <p>2) Runtime 엔진 실패와 UI 파이프라인 실패를 동일 스키마로 수집해
 * 운영 조회/분석 시 일관된 필드를 제공합니다.</p>
 */
public record BusinessDlqMessage(
        String dlqId,
        String source,
        String stage,
        String reasonCode,
        String reasonMessage,
        long occurredAt,
        String topic,
        Integer partition,
        Long offset,
        String eqpId,
        String messageType,
        String messageName,
        String traceId,
        String payloadRef,
        Map<String, String> tags
) {
    /**
     * 생성 시점에 필수 필드를 검증하고, 태그를 불변 맵으로 고정합니다.
     */
    public BusinessDlqMessage {
        requireText("dlqId", dlqId);
        requireText("source", source);
        requireText("stage", stage);
        requireText("reasonCode", reasonCode);
        requireText("reasonMessage", reasonMessage);
        if (occurredAt <= 0L) {
            throw new IllegalArgumentException("occurredAt must be > 0");
        }
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }

    /**
     * 문자열 필수값 검증 유틸리티입니다.
     *
     * @param fieldName 필드명
     * @param value 필드값
     */
    private static void requireText(final String fieldName, final String value) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
