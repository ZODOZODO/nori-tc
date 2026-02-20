package com.nori.tc.business.core.workflow.internal.core;

import com.nori.tc.business.core.messaging.BusinessEqpCommandMessage;
import com.nori.tc.business.core.messaging.BusinessEqpCommandPublishPort;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.annotation.TcAction;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractSocketActionExecutor;
import com.nori.tc.business.core.workflow.internal.support.BusinessWorkflowCommandSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * SOCKET 메시지 타입에서 사용하는 기본(core) 액션 실행기입니다.
 *
 * <p>주요 역할:</p>
 * <p>1) 단순 로그 액션(`CORE_LOG`) 처리</p>
 * <p>2) SOCKET 인터페이스용 EQP command 메시지 생성/발행</p>
 */
@Component
public class BusinessCoreSocketActionExecutor extends AbstractSocketActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessCoreSocketActionExecutor.class);
    private static final String DEFAULT_EQP_EVENT_TYPE = "EQP_COMMAND";
    private static final String INTERFACE_TYPE_SOCKET = "SOCKET";

    private final BusinessEqpCommandPublishPort eqpCommandPublishPort;

    /**
     * 발행 포트를 주입받아 실행기를 생성합니다.
     *
     * @param eqpCommandPublishPort EQP command 발행 포트
     */
    public BusinessCoreSocketActionExecutor(final BusinessEqpCommandPublishPort eqpCommandPublishPort) {
        this.eqpCommandPublishPort = Objects.requireNonNull(eqpCommandPublishPort, "eqpCommandPublishPort is null");
    }

    /**
     * 매칭된 워크플로우 실행 사실을 로그로 남기는 기본 액션입니다.
     *
     * @param context 워크플로우 액션 실행 컨텍스트
     */
    @TcAction("CORE_LOG")
    public void coreLog(final BusinessWorkflowActionContext context) {
        log.info("CORE SOCKET action executed. eqpId={}, messageName={}, workflowKey={}, actionName={}",
                context.record().eqpId(),
                context.record().messageName(),
                context.workflowEntry().workflowKey(),
                context.workflowEntry().actionName());
        if (log.isDebugEnabled()) {
            log.debug("CORE SOCKET action detail. contextVariables={}, messageVariables={}",
                    context.contextVariables(),
                    context.messageVariables());
        }
    }

    /**
     * 워크플로우 컨텍스트를 기반으로 SOCKET용 EQP command 메시지를 발행합니다.
     *
     * <p>동작 순서:</p>
     * <p>1) eventType/traceId/rawMessage를 공통 규칙으로 해석</p>
     * <p>2) 공통 payload를 attributes로 사용</p>
     * <p>3) 인터페이스 타입을 SOCKET으로 지정해 publish</p>
     *
     * @param context 워크플로우 액션 실행 컨텍스트
     * @throws Exception 메시지 발행 중 예외가 발생한 경우
     */
    @TcAction("PUBLISH_EQP_COMMAND")
    public void publishEqpCommand(final BusinessWorkflowActionContext context) throws Exception {
        final String eventType = BusinessWorkflowCommandSupport.resolveCommandEventType(
                context,
                DEFAULT_EQP_EVENT_TYPE
        );
        final String traceId = BusinessWorkflowCommandSupport.resolveTraceId(context);
        final String rawMessage = BusinessWorkflowCommandSupport.resolveRawMessage(context);

        final Map<String, Object> attributes = BusinessWorkflowCommandSupport.buildCommandPayload(context);
        final BusinessEqpCommandMessage message = new BusinessEqpCommandMessage(
                eventType,
                context.record().eqpId(),
                traceId,
                INTERFACE_TYPE_SOCKET,
                rawMessage,
                BusinessWorkflowCommandSupport.resolveTransactionId(context),
                attributes
        );

        eqpCommandPublishPort.publish(message);
        log.info("EQP command published from SOCKET workflow action. eqpId={}, eventType={}, workflowKey={}, actionName={}",
                context.record().eqpId(),
                eventType,
                context.workflowEntry().workflowKey(),
                context.workflowEntry().actionName());
        if (log.isDebugEnabled()) {
            log.debug("EQP command payload detail(SOCKET). traceId={}, rawMessageLength={}, attributes={}",
                    traceId,
                    rawMessage == null ? 0 : rawMessage.length(),
                    attributes);
        }
    }
}


