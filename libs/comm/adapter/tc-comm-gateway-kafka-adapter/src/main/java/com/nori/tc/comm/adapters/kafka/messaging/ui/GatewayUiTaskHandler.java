package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;

/**
 * UI task 이벤트 단위 처리기 계약입니다.
 *
 * <p>이벤트 타입별 구현체를 분리해 분기 복잡도를 낮추고,
 * 이후 business-app/ui-backend-app 확장 시 동일 패턴을 재사용할 수 있게 합니다.</p>
 */
public interface GatewayUiTaskHandler {

    /**
     * 이 핸들러가 담당하는 이벤트 타입을 반환합니다.
     */
    KafkaUiTaskEventType eventType();

    /**
     * 단건 UI task 메시지를 처리합니다.
     */
    void handle(KafkaUiTaskMessage message);
}
