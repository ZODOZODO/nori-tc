package com.nori.tc.business.core.workflow;

import com.nori.tc.business.core.messaging.BusinessEqpCommandMessage;
import com.nori.tc.business.core.messaging.BusinessEqpCommandPublishPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Business Core 기본 SECS(HSMS) 액션 Executor입니다.
 *
 * <p>역할:
 * 1) 공통 로깅 액션 제공
 * 2) EQP 명령 발행 액션 제공 ({@code tc.eqp.commands})</p>
 */
@Component
public class BusinessCoreSecsActionExecutor extends SecsActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessCoreSecsActionExecutor.class);
    private static final String DEFAULT_EQP_EVENT_TYPE = "EQP_COMMAND";
    private static final String INTERFACE_TYPE_HSMS = "HSMS";

    private final BusinessEqpCommandPublishPort eqpCommandPublishPort;

    /**
     * EQP 명령 발행 포트를 주입받습니다.
     *
     * @param eqpCommandPublishPort EQP 명령 발행 포트
     */
    public BusinessCoreSecsActionExecutor(final BusinessEqpCommandPublishPort eqpCommandPublishPort) {
        this.eqpCommandPublishPort = Objects.requireNonNull(eqpCommandPublishPort, "eqpCommandPublishPort is null");
    }

    /**
     * 공통 SECS 로그 액션입니다.
     *
     * @param context action context
     */
    @TcAction("CORE_LOG")
    public void coreLog(final BusinessWorkflowActionContext context) {
        log.info("CORE SECS action executed. eqpId={}, messageName={}, workflowKey={}, actionName={}",
                context.record().eqpId(),
                context.record().messageName(),
                context.workflowEntry().workflowKey(),
                context.workflowEntry().actionName());
        if (log.isDebugEnabled()) {
            log.debug("CORE SECS action detail. contextVariables={}, messageVariables={}",
                    context.contextVariables(),
                    context.messageVariables());
        }
    }

    /**
     * EQP 명령 발행 액션입니다.
     *
     * <p>동작:
     * 1) workflow/action 컨텍스트에서 eventType/traceId/rawMessage를 해석합니다.
     * 2) SECS 인터페이스 기준 명령 모델을 구성합니다.
     * 3) EQP 명령 발행 포트를 통해 {@code tc.eqp.commands}로 발행합니다.</p>
     *
     * @param context action context
     * @throws Exception 발행 실패
     */
    @TcAction("PUBLISH_EQP_COMMAND")
    public void publishEqpCommand(final BusinessWorkflowActionContext context) throws Exception {
        final String eventType = BusinessWorkflowCommandSupport.resolveCommandEventType(
                context,
                DEFAULT_EQP_EVENT_TYPE
        );
        final String traceId = BusinessWorkflowCommandSupport.resolveTraceId(context);
        final String rawMessage = BusinessWorkflowCommandSupport.resolveRawMessage(context);

        final Map<String, Object> attributes = new LinkedHashMap<>(
                BusinessWorkflowCommandSupport.buildCommandPayload(context)
        );
        attributes.put("transactionId", BusinessWorkflowCommandSupport.resolveTransactionId(context));

        final BusinessEqpCommandMessage message = new BusinessEqpCommandMessage(
                eventType,
                context.record().eqpId(),
                traceId,
                INTERFACE_TYPE_HSMS,
                rawMessage,
                BusinessWorkflowCommandSupport.resolveTransactionId(context),
                attributes
        );

        eqpCommandPublishPort.publish(message);
        log.info("EQP command published from SECS workflow action. eqpId={}, eventType={}, workflowKey={}, actionName={}",
                context.record().eqpId(),
                eventType,
                context.workflowEntry().workflowKey(),
                context.workflowEntry().actionName());
        if (log.isDebugEnabled()) {
            log.debug("EQP command payload detail(SECS). traceId={}, rawMessageLength={}, attributes={}",
                    traceId,
                    rawMessage == null ? 0 : rawMessage.length(),
                    attributes);
        }
    }
}
