package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskReplyStatus;

/**
 * UI task 처리 결과 모델입니다.
 *
 * <p>{@link KafkaUiTaskReplyStatus}와 오류 정보를 함께 보관해
 * reply publisher가 그대로 직렬화할 수 있도록 단순화했습니다.</p>
 */
public record GatewayUiTaskResult(
        KafkaUiTaskReplyStatus status,
        String errorCode,
        String errorMessage
) {
    /**
     * 성공 결과(PASS)를 생성합니다.
     */
    public static GatewayUiTaskResult pass() {
        return new GatewayUiTaskResult(KafkaUiTaskReplyStatus.PASS, null, null);
    }

    /**
     * 실패 결과(FAIL)를 생성합니다.
     */
    public static GatewayUiTaskResult fail(final String errorCode, final String errorMessage) {
        return new GatewayUiTaskResult(KafkaUiTaskReplyStatus.FAIL, errorCode, errorMessage);
    }
}
