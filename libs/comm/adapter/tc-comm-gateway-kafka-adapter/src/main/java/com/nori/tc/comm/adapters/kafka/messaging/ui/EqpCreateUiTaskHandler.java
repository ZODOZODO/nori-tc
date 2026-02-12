package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import org.springframework.stereotype.Component;

/**
 * {@code EQP_CREATE} 이벤트 처리기입니다.
 */
@Component
public class EqpCreateUiTaskHandler extends AbstractCreateUpdateUiTaskHandler {

    /**
     * CREATE 처리기를 초기화합니다.
     */
    public EqpCreateUiTaskHandler(
            final GatewayUiRuntimeControlService runtimeControlService,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties
    ) {
        super(runtimeControlService, uiTaskPolicyProperties.getCreateTimeoutMs());
    }

    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_CREATE;
    }

    @Override
    public String replyEventType() {
        return "EQP_CREATE_REP";
    }
}
