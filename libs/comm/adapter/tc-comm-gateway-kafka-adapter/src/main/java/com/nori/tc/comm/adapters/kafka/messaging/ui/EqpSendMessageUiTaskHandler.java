package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code EQP_SEND_MESSAGE} 이벤트 처리기입니다.
 *
 * <p>UI 원문 제어 문자열을 기존 command dispatch 경로로 전달합니다.</p>
 */
@Component
public class EqpSendMessageUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EqpSendMessageUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;

    /**
     * SEND_MESSAGE 처리에 필요한 runtime 제어 서비스를 초기화합니다.
     */
    public EqpSendMessageUiTaskHandler(final GatewayUiRuntimeControlService runtimeControlService) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
    }

    /**
     * 담당 이벤트 타입을 반환합니다.
     */
    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_SEND_MESSAGE;
    }

    /**
     * UI 메시지를 command dispatcher 경로로 전달합니다.
     */
    @Override
    public void handle(final KafkaUiTaskMessage message) {
        if (log.isDebugEnabled()) {
            log.debug("EQP_SEND_MESSAGE task start. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        }
        try {
            runtimeControlService.sendUiMessage(
                    message.data().eqpId(),
                    message.data().interfaceType(),
                    message.metadata().traceId(),
                    message.data().uiMessage()
            );
            log.info("EQP_SEND_MESSAGE task success. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        } catch (Exception ex) {
            log.warn("EQP_SEND_MESSAGE task failed. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
        }
    }
}
