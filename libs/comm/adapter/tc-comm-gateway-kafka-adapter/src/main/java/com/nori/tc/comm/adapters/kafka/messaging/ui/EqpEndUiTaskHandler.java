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
 * <p>장비 통신 종료 요청에 따라 runtime 자원을 정리합니다.</p>
 */
@Component
public class EqpEndUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EqpEndUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;

    /**
     * END 처리에 필요한 runtime 제어 서비스를 초기화합니다.
     */
    public EqpEndUiTaskHandler(final GatewayUiRuntimeControlService runtimeControlService) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
    }

    /**
     * 담당 이벤트 타입을 반환합니다.
     */
    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_END;
    }

    /**
     * END 요청을 처리해 장비 runtime을 종료합니다.
     */
    @Override
    public void handle(final KafkaUiTaskMessage message) {
        if (log.isDebugEnabled()) {
            log.debug("EQP_END task start. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        }
        try {
            runtimeControlService.stopRuntime(message.data().eqpId());
            log.info("EQP_END task success. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        } catch (Exception ex) {
            log.warn("EQP_END task failed. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
        }
    }
}
