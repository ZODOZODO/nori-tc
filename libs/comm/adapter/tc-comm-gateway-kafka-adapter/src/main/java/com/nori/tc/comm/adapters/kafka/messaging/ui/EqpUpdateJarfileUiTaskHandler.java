package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.config.GatewayUiTaskPolicyProperties;
import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code EQP_UPDATE_JARFILE} 이벤트 처리기입니다.
 *
 * <p>JARFILE 처리 결과를 PASS/FAIL로 반환하고,
 * 실제 REP 발행은 dispatcher 공통 흐름에서 처리합니다.</p>
 */
@Component
public class EqpUpdateJarfileUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EqpUpdateJarfileUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;
    private final GatewayUiTaskPolicyProperties uiTaskPolicyProperties;
    private final GatewayUiJarfileTaskProcessor jarfileTaskProcessor;

    /**
     * JARFILE 처리기를 초기화합니다.
     *
     * <p>별도 구현이 없으면 기본 FAIL 프로세서로 동작합니다.</p>
     */
    public EqpUpdateJarfileUiTaskHandler(
            final GatewayUiRuntimeControlService runtimeControlService,
            final GatewayUiTaskPolicyProperties uiTaskPolicyProperties,
            final ObjectProvider<GatewayUiJarfileTaskProcessor> jarfileTaskProcessorProvider
    ) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
        this.uiTaskPolicyProperties = Objects.requireNonNull(uiTaskPolicyProperties, "uiTaskPolicyProperties is null");
        this.jarfileTaskProcessor = jarfileTaskProcessorProvider.getIfAvailable(
                () -> (message, equipmentInfo) -> GatewayUiTaskResult.fail(
                        GatewayUiTaskErrorCode.JARFILE_TASK_NOT_CONFIGURED,
                        "Jarfile task processor is not configured"
                )
        );
    }

    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_UPDATE_JARFILE;
    }

    @Override
    public String replyEventType() {
        return "EQP_UPDATE_JARFILE_REP";
    }

    @Override
    public GatewayUiTaskResult handle(final KafkaUiTaskMessage message) {
        final long timeoutMs = uiTaskPolicyProperties.getUpdateJarfileTimeoutMs();
        if (log.isDebugEnabled()) {
            log.debug("EQP_UPDATE_JARFILE task start. eqpId={}, traceId={}, timeoutMs={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    timeoutMs);
        }

        final GatewayEquipmentInfo equipmentInfo;
        try {
            equipmentInfo = runtimeControlService.resolveAndValidateEquipment(
                    message.data().eqpId(),
                    message.data().interfaceType()
            );
        } catch (GatewayUiTaskProcessingException ex) {
            log.warn("EQP_UPDATE_JARFILE validation failed. eqpId={}, traceId={}, errorCode={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex.errorCode());
            return GatewayUiTaskResult.fail(ex.errorCode(), ex.getMessage());
        }

        try {
            final GatewayUiTaskResult result = jarfileTaskProcessor.process(message, equipmentInfo);
            if (result == null) {
                return GatewayUiTaskResult.fail(
                        GatewayUiTaskErrorCode.JARFILE_TASK_FAILED,
                        "Jarfile task returned null result"
                );
            }
            log.info("EQP_UPDATE_JARFILE task finished. eqpId={}, traceId={}, status={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    result.status());
            return result;
        } catch (Exception ex) {
            log.error("EQP_UPDATE_JARFILE task failed. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
            return GatewayUiTaskResult.fail(
                    GatewayUiTaskErrorCode.JARFILE_TASK_FAILED,
                    "Jarfile task execution failed"
            );
        }
    }
}
