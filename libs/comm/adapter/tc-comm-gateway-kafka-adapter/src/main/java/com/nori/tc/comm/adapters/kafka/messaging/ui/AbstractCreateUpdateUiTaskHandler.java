package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.comm.gateway.db.GatewayEquipmentInfo;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * EQP_CREATE / EQP_UPDATE 공통 처리 추상 핸들러입니다.
 *
 * <p>공통 동작:</p>
 * <p>- DB 프로필 조회 + EquipmentContext upsert</p>
 * <p>- 예외를 GatewayUiTaskResult(FAIL)로 표준 변환</p>
 */
public abstract class AbstractCreateUpdateUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractCreateUpdateUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;
    private final long timeoutMs;

    /**
     * 공통 처리에 필요한 서비스와 타임아웃을 초기화합니다.
     */
    protected AbstractCreateUpdateUiTaskHandler(
            final GatewayUiRuntimeControlService runtimeControlService,
            final long timeoutMs
    ) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
        this.timeoutMs = timeoutMs;
    }

    /**
     * CREATE/UPDATE 공통 본문입니다.
     */
    @Override
    public GatewayUiTaskResult handle(final KafkaUiTaskMessage message) {
        if (log.isDebugEnabled()) {
            log.debug("UI {} task start. eqpId={}, traceId={}, timeoutMs={}",
                    eventType(),
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    timeoutMs);
        }

        try {
            final GatewayEquipmentInfo equipmentInfo = runtimeControlService.createOrUpdateContext(
                    message.data().eqpId(),
                    message.data().interfaceType(),
                    message.metadata().traceId(),
                    eventType().name(),
                    timeoutMs
            );

            log.info("UI {} task success. eqpId={}, traceId={}, enabled={}",
                    eventType(),
                    equipmentInfo.equipmentId(),
                    message.metadata().traceId(),
                    equipmentInfo.enabled());
            return GatewayUiTaskResult.pass();
        } catch (GatewayUiTaskProcessingException ex) {
            log.warn("UI {} task failed. eqpId={}, traceId={}, errorCode={}",
                    eventType(),
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex.errorCode());
            return GatewayUiTaskResult.fail(ex.errorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("UI {} task failed by unexpected error. eqpId={}, traceId={}",
                    eventType(),
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
            return GatewayUiTaskResult.fail(
                    GatewayUiTaskErrorCode.INTERNAL_ERROR,
                    "Unhandled error while processing UI task"
            );
        }
    }

    @Override
    public abstract KafkaUiTaskEventType eventType();
}
