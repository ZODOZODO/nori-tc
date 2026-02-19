package com.nori.tc.business.core.workflow;

import com.nori.tc.business.core.messaging.BusinessMesCommandMessage;
import com.nori.tc.business.core.messaging.BusinessMesCommandPublishPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Business Core 기본 MES 액션 Executor입니다.
 *
 * <p>역할:
 * 1) 공통 로깅 액션 제공
 * 2) MES 명령 발행 액션 제공 ({@code tc.mes.commands})</p>
 */
@Component
public class BusinessCoreMesActionExecutor extends MesActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessCoreMesActionExecutor.class);
    private static final String DEFAULT_MES_EVENT_TYPE = "MES_COMMAND";

    private final BusinessMesCommandPublishPort mesCommandPublishPort;

    /**
     * MES 명령 발행 포트를 주입받습니다.
     *
     * @param mesCommandPublishPort MES 명령 발행 포트
     */
    public BusinessCoreMesActionExecutor(final BusinessMesCommandPublishPort mesCommandPublishPort) {
        this.mesCommandPublishPort = Objects.requireNonNull(mesCommandPublishPort, "mesCommandPublishPort is null");
    }

    /**
     * 공통 MES 로그 액션입니다.
     *
     * @param context action context
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
     * MES 명령 발행 액션입니다.
     *
     * <p>동작:
     * 1) workflow/action 컨텍스트에서 eventType/correlationId를 해석합니다.
     * 2) 표준 명령 모델을 구성합니다.
     * 3) MES 명령 발행 포트를 통해 {@code tc.mes.commands}로 발행합니다.</p>
     *
     * @param context action context
     * @throws Exception 발행 실패
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
