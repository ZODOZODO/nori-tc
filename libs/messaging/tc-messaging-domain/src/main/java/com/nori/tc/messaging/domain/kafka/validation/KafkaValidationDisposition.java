package com.nori.tc.messaging.domain.kafka.validation;

/**
 * 유효성 실패 시 후속 처분(disposition) 표준값입니다.
 */
public enum KafkaValidationDisposition {

    /**
     * 메시지를 처리 거부(REJECT)하고 커밋/드롭 경로로 보냅니다.
     */
    REJECTED,

    /**
     * 메시지를 장애 분석/재처리 대상으로 DLQ 경로로 보냅니다.
     */
    DLQ
}

