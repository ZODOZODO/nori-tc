package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import org.springframework.stereotype.Component;

/**
 * {@code EQP_UPDATE} 이벤트 처리기입니다.
 */
@Component
public class EqpUpdateUiTaskHandler extends AbstractCreateUpdateUiTaskHandler {

    /**
     * UPDATE 처리기를 초기화합니다.
     */
    public EqpUpdateUiTaskHandler(
            final GatewayUiRuntimeControlService runtimeControlService,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties
    ) {
        super(runtimeControlService, uiTaskPolicyProperties.getUpdateTimeoutMs());
    }

    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_UPDATE;
    }

    @Override
    public String replyEventType() {
        return "EQP_UPDATE_REP";
    }
}
