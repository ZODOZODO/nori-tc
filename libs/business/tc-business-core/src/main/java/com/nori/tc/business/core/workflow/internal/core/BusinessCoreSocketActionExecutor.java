package com.nori.tc.business.core.workflow.internal.core;

import com.nori.tc.business.core.messaging.BusinessEqpCommandMessage;
import com.nori.tc.business.core.messaging.BusinessEqpCommandPublishPort;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.annotation.TcAction;
import com.nori.tc.business.core.workflow.api.spi.executor.AbstractSocketActionExecutor;
import com.nori.tc.business.core.workflow.internal.support.BusinessMdfMessageComposer;
import com.nori.tc.business.core.workflow.internal.support.BusinessWorkflowCommandSupport;
import com.nori.tc.business.domain.modelcache.MdfRuntimeDefinition.MdfTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SOCKET 메시지 타입의 기본(Core) 액션 실행기입니다.
 */
@Component
public class BusinessCoreSocketActionExecutor extends AbstractSocketActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessCoreSocketActionExecutor.class);
    private static final String DEFAULT_EQP_EVENT_TYPE = "EQP_COMMAND";
    private static final String INTERFACE_TYPE_SOCKET = "SOCKET";

    private final BusinessEqpCommandPublishPort eqpCommandPublishPort;
    private final BusinessMdfMessageComposer mdfMessageComposer;

    /**
     * 필수 의존성을 주입받습니다.
     */
    public BusinessCoreSocketActionExecutor(
            final BusinessEqpCommandPublishPort eqpCommandPublishPort,
            final BusinessMdfMessageComposer mdfMessageComposer
    ) {
        this.eqpCommandPublishPort = Objects.requireNonNull(eqpCommandPublishPort, "eqpCommandPublishPort is null");
        this.mdfMessageComposer = Objects.requireNonNull(mdfMessageComposer, "mdfMessageComposer is null");
    }

    /**
     * 워크플로우 실행 사실을 INFO 레벨로 기록합니다.
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
     * SOCKET 인터페이스용 EQP command를 발행합니다.
     *
     * <p>
     * MDF 정의가 존재하면 rawMessage를 MDF 템플릿으로 생성합니다.
     * </p>
     */
    @TcAction("PUBLISH_EQP_COMMAND")
    public void publishEqpCommand(final BusinessWorkflowActionContext context) throws Exception {
        final String eventType = BusinessWorkflowCommandSupport.resolveCommandEventType(
                context,
                DEFAULT_EQP_EVENT_TYPE
        );
        final String traceId = BusinessWorkflowCommandSupport.resolveTraceId(context);

        final Optional<BusinessMdfMessageComposer.MdfComposeResult> mdfResult = mdfMessageComposer.compose(
                context,
                MdfTargetType.EQP
        );

        final String rawMessage = mdfResult
                .map(BusinessMdfMessageComposer.MdfComposeResult::rawMessage)
                .orElseGet(() -> BusinessWorkflowCommandSupport.resolveRawMessage(context));

        final Map<String, Object> attributes = new LinkedHashMap<>(BusinessWorkflowCommandSupport.buildCommandPayload(context));
        mdfResult.ifPresent(result -> {
            attributes.put("mdfMessageName", result.messageDefinition().name());
            attributes.put("mdfFields", result.fieldValues());
        });

        final BusinessEqpCommandMessage message = new BusinessEqpCommandMessage(
                eventType,
                context.record().eqpId(),
                traceId,
                INTERFACE_TYPE_SOCKET,
                rawMessage,
                BusinessWorkflowCommandSupport.resolveTransactionId(context),
                Map.copyOf(attributes)
        );

        eqpCommandPublishPort.publish(message);
        log.info("EQP command published from SOCKET workflow action. eqpId={}, eventType={}, workflowKey={}, actionName={}, mdfApplied={}",
                context.record().eqpId(),
                eventType,
                context.workflowEntry().workflowKey(),
                context.workflowEntry().actionName(),
                mdfResult.isPresent());
        if (log.isDebugEnabled()) {
            log.debug("EQP command payload detail(SOCKET). traceId={}, rawMessageLength={}, mdfApplied={}, attributes={}",
                    traceId,
                    rawMessage == null ? 0 : rawMessage.length(),
                    mdfResult.isPresent(),
                    attributes);
        }
    }
}
