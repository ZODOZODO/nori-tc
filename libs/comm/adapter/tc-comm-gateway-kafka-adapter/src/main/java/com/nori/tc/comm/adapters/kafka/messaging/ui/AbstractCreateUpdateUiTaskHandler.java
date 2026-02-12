package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * EQP_CREATE/EQP_UPDATE 공통 처리 추상 핸들러입니다.
 *
 * <p>두 이벤트는 처리 흐름이 동일(장비 검증 + ACTIVE 시작)이므로
 * 실패 응답 이벤트 타입만 하위 클래스에서 분리합니다.</p>
 */
public abstract class AbstractCreateUpdateUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractCreateUpdateUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;
    private final KafkaUiReplyPublisher replyPublisher;

    /**
     * 공통 처리에 필요한 runtime 제어 서비스와 reply publisher를 초기화합니다.
     */
    protected AbstractCreateUpdateUiTaskHandler(
            final GatewayUiRuntimeControlService runtimeControlService,
            final KafkaUiReplyPublisher replyPublisher
    ) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
        this.replyPublisher = Objects.requireNonNull(replyPublisher, "replyPublisher is null");
    }

    /**
     * CREATE/UPDATE 공통 처리 본문입니다.
     *
     * <p>검증 실패 시 즉시 FAIL 응답을 발행합니다.</p>
     */
    @Override
    public void handle(final KafkaUiTaskMessage message) {
        if (log.isDebugEnabled()) {
            log.debug("UI {} task start. eqpId={}, traceId={}",
                    eventType(),
                    message.data().eqpId(),
                    message.metadata().traceId());
        }
        try {
            final GatewayEquipmentInfo equipmentInfo = runtimeControlService.resolveAndValidateEquipment(
                    message.data().eqpId(),
                    message.data().interfaceType()
            );
            runtimeControlService.startActiveIfNeeded(equipmentInfo);
            log.info("UI {} task success. eqpId={}, traceId={}",
                    eventType(),
                    message.data().eqpId(),
                    message.metadata().traceId());
        } catch (GatewayUiTaskProcessingException ex) {
            log.warn("UI {} task validation failed. eqpId={}, traceId={}, errorCode={}",
                    eventType(),
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex.errorCode());
            replyPublisher.publishFailure(message, failReplyEventType(), ex.errorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("UI task failed. eventType={}, eqpId={}, traceId={}",
                    eventType(), message.data().eqpId(), message.metadata().traceId(), ex);
            replyPublisher.publishFailure(
                    message,
                    failReplyEventType(),
                    "INTERNAL_ERROR",
                    "Unhandled error while processing UI task"
            );
        }
    }

    /**
     * 실패 응답 이벤트 타입(EQP_CREATE_REP/EQP_UPDATE_REP)을 반환합니다.
     */
    protected abstract String failReplyEventType();

    @Override
    public abstract KafkaUiTaskEventType eventType();
}
