package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code EQP_START} 이벤트 처리기입니다.
 *
 * <p>PASS 기준: 실제 채널 연결 성공(활성 채널 확인)</p>
 */
@Component
public class EqpStartUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EqpStartUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;

    /**
     * START 처리기를 초기화합니다.
     */
    public EqpStartUiTaskHandler(
            final GatewayUiRuntimeControlService runtimeControlService,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties
    ) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");
    }

    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_START;
    }

    @Override
    public String replyEventType() {
        return "EQP_START_REP";
    }

    @Override
    public GatewayUiTaskResult handle(final KafkaUiTaskMessage message) {
        final long timeoutMs = uiTaskPolicyProperties.getStartTimeoutMs();
        if (log.isDebugEnabled()) {
            log.debug("EQP_START task start. eqpId={}, traceId={}, timeoutMs={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    timeoutMs);
        }
        try {
            runtimeControlService.startRuntime(
                    message.data().eqpId(),
                    message.data().interfaceType(),
                    message.metadata().traceId(),
                    timeoutMs
            );
            log.info("EQP_START task success. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
            return GatewayUiTaskResult.pass();
        } catch (GatewayUiTaskProcessingException ex) {
            log.warn("EQP_START task failed. eqpId={}, traceId={}, errorCode={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex.errorCode());
            return GatewayUiTaskResult.fail(ex.errorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("EQP_START task failed by unexpected error. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
            return GatewayUiTaskResult.fail(
                    GatewayUiTaskErrorCode.INTERNAL_ERROR,
                    "Unhandled error while processing EQP_START"
            );
        }
    }
}
