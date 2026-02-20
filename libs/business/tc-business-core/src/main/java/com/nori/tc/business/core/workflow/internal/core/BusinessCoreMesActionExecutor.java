package com.nori.tc.business.core.workflow.internal.core;

import com.nori.tc.business.core.messaging.BusinessMesCommandMessage;
import com.nori.tc.business.core.messaging.BusinessMesCommandPublishPort;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.annotation.TcAction;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractMesActionExecutor;
import com.nori.tc.business.core.workflow.internal.support.BusinessWorkflowCommandSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MES 메시지 타입에서 사용하는 기본(core) 액션 실행기입니다.
 *
 * <p>주요 역할:</p>
 * <p>1) 단순 로그 액션(`CORE_LOG`) 처리</p>
 * <p>2) 공통 컨텍스트를 기반으로 MES command 메시지 생성/발행</p>
 */
@Component
public class BusinessCoreMesActionExecutor extends AbstractMesActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessCoreMesActionExecutor.class);
    private static final String DEFAULT_MES_EVENT_TYPE = "MES_COMMAND";

    private final BusinessMesCommandPublishPort mesCommandPublishPort;

    /**
     * 발행 포트를 주입받아 실행기를 생성합니다.
     *
     * @param mesCommandPublishPort MES command 발행 포트
     */
    public BusinessCoreMesActionExecutor(final BusinessMesCommandPublishPort mesCommandPublishPort) {
        this.mesCommandPublishPort = Objects.requireNonNull(mesCommandPublishPort, "mesCommandPublishPort is null");
    }

    /**
     * 매칭된 워크플로우 실행 사실을 로그로 남기는 기본 액션입니다.
     *
     * @param context 워크플로우 액션 실행 컨텍스트
     */
    @TcAction("CORE_LOG")
    public void coreLog(final BusinessWorkflowActionContext context) {
        log.info("CORE MES action executed. eqpId={}, messageName={}, workflowKey={}, actionName={}",
                context.record().eqpId(),
                context.record().messageName(),
                context.workflowEntry().workflowKey(),
                context.workflowEntry().actionName());
        if (log.isDebugEnabled()) {
            log.debug("CORE MES action detail. contextVariables={}, messageVariables={}",
                    context.contextVariables(),
                    context.messageVariables());
        }
    }

    /**
     * 워크플로우 컨텍스트를 기반으로 MES command 메시지를 발행합니다.
     *
     * <p>동작 순서:</p>
     * <p>1) eventType/correlationId를 공통 규칙으로 해석</p>
     * <p>2) 공통 payload + rawMessage/transactionId를 조합</p>
     * <p>3) `BusinessMesCommandMessage`를 생성해 publish</p>
     *
     * @param context 워크플로우 액션 실행 컨텍스트
     * @throws Exception 메시지 발행 중 예외가 발생한 경우
     */
    @TcAction("PUBLISH_MES_COMMAND")
    public void publishMesCommand(final BusinessWorkflowActionContext context) throws Exception {
        final String eventType = BusinessWorkflowCommandSupport.resolveCommandEventType(
                context,
                DEFAULT_MES_EVENT_TYPE
        );
        final String correlationId = BusinessWorkflowCommandSupport.resolveCorrelationId(context);
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is required for MES command publish");
        }

        final Map<String, Object> payload = new LinkedHashMap<>(
                BusinessWorkflowCommandSupport.buildCommandPayload(context)
        );
        payload.put("rawMessage", BusinessWorkflowCommandSupport.resolveRawMessage(context));
        payload.put("transactionId", BusinessWorkflowCommandSupport.resolveTransactionId(context));

        final BusinessMesCommandMessage message = new BusinessMesCommandMessage(
                eventType,
                correlationId,
                context.record().eqpId(),
                BusinessWorkflowCommandSupport.resolveTraceId(context),
                payload
        );

        mesCommandPublishPort.publish(message);
        log.info("MES command published from workflow action. eqpId={}, correlationId={}, eventType={}, workflowKey={}, actionName={}",
                context.record().eqpId(),
                correlationId,
                eventType,
                context.workflowEntry().workflowKey(),
                context.workflowEntry().actionName());
        if (log.isDebugEnabled()) {
            log.debug("MES command payload detail. payload={}", payload);
        }
    }
}


