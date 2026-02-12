package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code EQP_START} 이벤트 처리기입니다.
 *
 * <p>START는 컨텍스트 목표 상태를 STARTED로 전환하고,
 * ACTIVE 설비는 즉시 connect 시도를 트리거합니다.</p>
 */
@Component
public class EqpStartUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EqpStartUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;

    /**
     * START 처리에 필요한 런타임 제어 서비스를 초기화합니다.
     */
    public EqpStartUiTaskHandler(final GatewayUiRuntimeControlService runtimeControlService) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
    }

    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_START;
    }

    @Override
    public void handle(final KafkaUiTaskMessage message) {
        if (log.isDebugEnabled()) {
            log.debug("EQP_START task start. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        }
        try {
            runtimeControlService.startRuntime(
                    message.data().eqpId(),
                    message.data().interfaceType(),
                    message.metadata().traceId()
            );
            log.info("EQP_START task success. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        } catch (GatewayUiTaskProcessingException ex) {
            log.warn("EQP_START task failed. eqpId={}, traceId={}, errorCode={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex.errorCode());
        } catch (Exception ex) {
            log.error("EQP_START task failed by unexpected error. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
        }
    }
}
