package com.nori.tc.messaging.kafka.contract;

/**
 * UI 작업 응답 상태 코드입니다.
 *
 * <p>현재 계약에서는 성공/실패 2가지만 사용합니다.</p>
 */
public enum KafkaUiTaskReplyStatus {
    PASS,
    FAIL
}
