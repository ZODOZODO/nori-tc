package com.nori.tc.messaging.domain.kafka.validation;

import java.util.Objects;

/**
 * Kafka 계약 검증 실패 정보를 표현하는 표준 레코드입니다.
 *
 * @param errorCode 실패 에러코드
 * @param disposition 후속 처분 규칙
 * @param message 사람이 읽을 수 있는 상세 사유
 */
public record KafkaValidationFailure(
        KafkaValidationErrorCode errorCode,
        KafkaValidationDisposition disposition,
        String message
) {

    /**
     * 필수 필드를 검증합니다.
     */
    public KafkaValidationFailure {
        Objects.requireNonNull(errorCode, "errorCode is required");
        Objects.requireNonNull(disposition, "disposition is required");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
    }
}

