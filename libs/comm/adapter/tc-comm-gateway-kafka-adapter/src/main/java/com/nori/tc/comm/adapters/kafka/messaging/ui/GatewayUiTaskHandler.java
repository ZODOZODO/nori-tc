package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;

/**
 * UI task 이벤트 단위 처리기 계약입니다.
 *
 * <p>핸들러는 비즈니스 처리와 PASS/FAIL 결과 생성만 담당하고,
 * Kafka reply 발행과 커밋 연계는 dispatcher가 공통 처리합니다.</p>
 */
public interface GatewayUiTaskHandler {

    /**
     * 현재 핸들러가 처리하는 UI 이벤트 타입을 반환합니다.
     */
    KafkaUiTaskEventType eventType();

    /**
     * 처리 결과를 회신할 UI reply eventType을 반환합니다.
     *
     * <p>예: EQP_START -> EQP_START_REP</p>
     */
    String replyEventType();

    /**
     * 수신된 UI task 메시지를 처리하고 PASS/FAIL 결과를 반환합니다.
     */
    GatewayUiTaskResult handle(KafkaUiTaskMessage message);
}
