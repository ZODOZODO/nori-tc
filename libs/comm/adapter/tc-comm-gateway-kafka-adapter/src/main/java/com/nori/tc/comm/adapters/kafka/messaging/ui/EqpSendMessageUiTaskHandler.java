package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code EQP_SEND_MESSAGE} 이벤트 처리기입니다.
 *
 * <p>PASS 기준: gateway 내부 send 경로(커맨드 디스패치)까지 정상 완료</p>
 */
@Component
public class EqpSendMessageUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EqpSendMessageUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;

    /**
     * SEND_MESSAGE 처리기를 초기화합니다.
     */
    public EqpSendMessageUiTaskHandler(
            final GatewayUiRuntimeControlService runtimeControlService,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties
    ) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");
    }

    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_SEND_MESSAGE;
    }

    @Override
    public String replyEventType() {
        return "EQP_SEND_MESSAGE_REP";
    }

    @Override
    public GatewayUiTaskResult handle(final KafkaUiTaskMessage message) {
        final long timeoutMs = uiTaskPolicyProperties.getSendMessageTimeoutMs();
        if (log.isDebugEnabled()) {
            log.debug("EQP_SEND_MESSAGE task start. eqpId={}, traceId={}, timeoutMs={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    timeoutMs);
        }
        try {
            runtimeControlService.sendUiMessage(
                    message.data().eqpId(),
                    message.data().interfaceType(),
                    message.metadata().traceId(),
                    message.data().uiMessage(),
                    timeoutMs
            );
            log.info("EQP_SEND_MESSAGE task success. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
            return GatewayUiTaskResult.pass();
        } catch (GatewayUiTaskProcessingException ex) {
            log.warn("EQP_SEND_MESSAGE task failed. eqpId={}, traceId={}, errorCode={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex.errorCode());
            return GatewayUiTaskResult.fail(ex.errorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("EQP_SEND_MESSAGE task failed by unexpected error. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
            return GatewayUiTaskResult.fail(
                    GatewayUiTaskErrorCode.INTERNAL_ERROR,
                    "Unhandled error while processing EQP_SEND_MESSAGE"
            );
        }
    }
}
