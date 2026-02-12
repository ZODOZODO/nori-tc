package com.nori.tc.comm.adapters.kafka.messaging.ui;

import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskEventType;
import com.nori.tc.messaging.kafka.starter.contract.KafkaUiTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * {@code EQP_DELETE} 이벤트 처리기입니다.
 *
 * <p>DELETE는 STOP(ENDED) 상태에서만 허용되며,
 * 메모리 컨텍스트와 런타임 자원을 정리합니다.</p>
 */
@Component
public class EqpDeleteUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EqpDeleteUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;

    /**
     * DELETE 처리에 필요한 런타임 제어 서비스를 초기화합니다.
     */
    public EqpDeleteUiTaskHandler(final GatewayUiRuntimeControlService runtimeControlService) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
    }

    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_DELETE;
    }

    @Override
    public void handle(final KafkaUiTaskMessage message) {
        if (log.isDebugEnabled()) {
            log.debug("EQP_DELETE task start. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        }
        try {
            runtimeControlService.deleteRuntimeContext(
                    message.data().eqpId(),
                    message.data().interfaceType(),
                    message.metadata().traceId()
            );
            log.info("EQP_DELETE task success. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        } catch (GatewayUiTaskProcessingException ex) {
            log.warn("EQP_DELETE task failed. eqpId={}, traceId={}, errorCode={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex.errorCode());
        } catch (Exception ex) {
            log.error("EQP_DELETE task failed by unexpected error. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
        }
    }
}
