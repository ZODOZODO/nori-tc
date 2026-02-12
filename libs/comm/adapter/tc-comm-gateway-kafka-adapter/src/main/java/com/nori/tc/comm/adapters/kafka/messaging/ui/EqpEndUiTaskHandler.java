package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code EQP_END} 이벤트 처리기입니다.
 *
 * <p>END는 컨텍스트 목표 상태를 ENDED로 전환하고,
 * ACTIVE 재연결을 억제한 뒤 채널/메일박스를 정리합니다.</p>
 */
@Component
public class EqpEndUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EqpEndUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;

    /**
     * END 처리에 필요한 런타임 제어 서비스를 초기화합니다.
     */
    public EqpEndUiTaskHandler(final GatewayUiRuntimeControlService runtimeControlService) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
    }

    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_END;
    }

    @Override
    public void handle(final KafkaUiTaskMessage message) {
        if (log.isDebugEnabled()) {
            log.debug("EQP_END task start. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        }
        try {
            runtimeControlService.endRuntime(
                    message.data().eqpId(),
                    message.data().interfaceType(),
                    message.metadata().traceId()
            );
            log.info("EQP_END task success. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        } catch (GatewayUiTaskProcessingException ex) {
            log.warn("EQP_END task failed. eqpId={}, traceId={}, errorCode={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex.errorCode());
        } catch (Exception ex) {
            log.error("EQP_END task failed by unexpected error. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
        }
    }
}
