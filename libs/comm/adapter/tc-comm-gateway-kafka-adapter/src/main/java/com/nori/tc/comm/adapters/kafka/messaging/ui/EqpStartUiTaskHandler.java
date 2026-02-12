package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code EQP_START} 이벤트 처리기입니다.
 *
 * <p>장비 검증 후 ACTIVE 모드 장비는 즉시 연결을 시도합니다.</p>
 */
@Component
public class EqpStartUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EqpStartUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;

    /**
     * START 처리에 필요한 runtime 제어 서비스를 초기화합니다.
     */
    public EqpStartUiTaskHandler(final GatewayUiRuntimeControlService runtimeControlService) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
    }

    /**
     * 담당 이벤트 타입을 반환합니다.
     */
    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_START;
    }

    /**
     * START 요청을 처리해 ACTIVE 연결을 시작합니다.
     */
    @Override
    public void handle(final KafkaUiTaskMessage message) {
        if (log.isDebugEnabled()) {
            log.debug("EQP_START task start. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        }
        try {
            final GatewayEquipmentInfo equipmentInfo = runtimeControlService.resolveAndValidateEquipment(
                    message.data().eqpId(),
                    message.data().interfaceType()
            );
            runtimeControlService.startActiveIfNeeded(equipmentInfo);
            log.info("EQP_START task success. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        } catch (Exception ex) {
            log.warn("EQP_START task failed. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
        }
    }
}
