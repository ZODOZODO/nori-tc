package com.nori.tc.comm.adapters.kafka.messaging.ui;

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
 * <p>장비 검증 후 jarfile 처리 확장 포인트를 호출하고,
 * 결과를 {@code EQP_UPDATE_JARFILE_REP}로 회신합니다.</p>
 */
@Component
public class EqpUpdateJarfileUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EqpUpdateJarfileUiTaskHandler.class);
    private static final String REPLY_EVENT_TYPE = "EQP_UPDATE_JARFILE_REP";

    private final GatewayUiRuntimeControlService runtimeControlService;
    private final KafkaUiReplyPublisher replyPublisher;
    private final GatewayUiJarfileTaskProcessor jarfileTaskProcessor;

    /**
     * JARFILE 처리에 필요한 의존성을 초기화합니다.
     *
     * <p>확장 처리기 구현체가 없으면 기본 FAIL 처리기로 대체됩니다.</p>
     */
    public EqpUpdateJarfileUiTaskHandler(
            final GatewayUiRuntimeControlService runtimeControlService,
            final KafkaUiReplyPublisher replyPublisher,
            final ObjectProvider<GatewayUiJarfileTaskProcessor> jarfileTaskProcessorProvider
    ) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
        this.replyPublisher = Objects.requireNonNull(replyPublisher, "replyPublisher is null");
        this.jarfileTaskProcessor = jarfileTaskProcessorProvider.getIfAvailable(
                () -> (message, equipmentInfo) -> GatewayUiTaskResult.fail(
                        "JARFILE_TASK_NOT_CONFIGURED",
                        "Jarfile task processor is not configured"
                )
        );
    }

    /**
     * 담당 이벤트 타입을 반환합니다.
     */
    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_UPDATE_JARFILE;
    }

    /**
     * JARFILE 업데이트 요청을 처리하고 결과를 UI로 회신합니다.
     */
    @Override
    public void handle(final KafkaUiTaskMessage message) {
        if (log.isDebugEnabled()) {
            log.debug("EQP_UPDATE_JARFILE task start. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
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
            replyPublisher.publishFailure(message, REPLY_EVENT_TYPE, ex.errorCode(), ex.getMessage());
            return;
        }

        final GatewayUiTaskResult result;
        try {
            final GatewayUiTaskResult processed = jarfileTaskProcessor.process(message, equipmentInfo);
            result = processed == null
                    ? GatewayUiTaskResult.fail("JARFILE_TASK_FAILED", "Jarfile task returned null result")
                    : processed;
        } catch (Exception ex) {
            log.error("EQP_UPDATE_JARFILE task failed. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
            replyPublisher.publishFailure(
                    message,
                    REPLY_EVENT_TYPE,
                    "JARFILE_TASK_FAILED",
                    "Jarfile task execution failed"
            );
            return;
        }

        replyPublisher.publishResult(message, REPLY_EVENT_TYPE, result);
        log.info("EQP_UPDATE_JARFILE task finished. eqpId={}, traceId={}, status={}",
                message.data().eqpId(),
                message.metadata().traceId(),
                result.status());
    }
}
