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
 * <p>장비 삭제 시 runtime 자원(재연결/채널/mailbox) 정리를 수행합니다.</p>
 */
@Component
public class EqpDeleteUiTaskHandler implements GatewayUiTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EqpDeleteUiTaskHandler.class);

    private final GatewayUiRuntimeControlService runtimeControlService;

    /**
     * DELETE 처리에 필요한 runtime 제어 서비스를 초기화합니다.
     */
    public EqpDeleteUiTaskHandler(final GatewayUiRuntimeControlService runtimeControlService) {
        this.runtimeControlService = Objects.requireNonNull(runtimeControlService, "runtimeControlService is null");
    }

    /**
     * 담당 이벤트 타입을 반환합니다.
     */
    @Override
    public KafkaUiTaskEventType eventType() {
        return KafkaUiTaskEventType.EQP_DELETE;
    }

    /**
     * 장비 runtime 종료를 수행합니다.
     */
    @Override
    public void handle(final KafkaUiTaskMessage message) {
        if (log.isDebugEnabled()) {
            log.debug("EQP_DELETE task start. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        }
        try {
            runtimeControlService.stopRuntime(message.data().eqpId());
            log.info("EQP_DELETE task success. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId());
        } catch (Exception ex) {
            log.warn("EQP_DELETE task failed. eqpId={}, traceId={}",
                    message.data().eqpId(),
                    message.metadata().traceId(),
                    ex);
        }
    }
}
