package com.nori.tc.business.core.workflow.internal.core;

import com.nori.tc.business.core.messaging.BusinessMesCommandMessage;
import com.nori.tc.business.core.messaging.BusinessMesCommandPublishPort;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.action.AbstractMesActionExecutor;
import com.nori.tc.business.action.TcAction;
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
 * MES 메시지 타입의 기본(Core) 액션 실행기입니다.
 *
 * <p>
 * 주요 역할:
 * 1) CORE_LOG 액션 처리
 * 2) PUBLISH_MES_COMMAND 액션 처리
 * 3) MDF 정의가 있으면 MDF 기반으로 data를 구성
 * </p>
 */
@Component
public class BusinessCoreMesActionExecutor extends AbstractMesActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessCoreMesActionExecutor.class);
    private static final String DEFAULT_MES_EVENT_TYPE = "MES_COMMAND";

    private final BusinessMesCommandPublishPort mesCommandPublishPort;
    private final BusinessMdfMessageComposer mdfMessageComposer;

    /**
     * 필수 의존성을 주입받습니다.
     */
    public BusinessCoreMesActionExecutor(
            final BusinessMesCommandPublishPort mesCommandPublishPort,
            final BusinessMdfMessageComposer mdfMessageComposer
    ) {
        this.mesCommandPublishPort = Objects.requireNonNull(mesCommandPublishPort, "mesCommandPublishPort is null");
        this.mdfMessageComposer = Objects.requireNonNull(mdfMessageComposer, "mdfMessageComposer is null");
    }

    /**
     * 워크플로우 실행 사실을 INFO 레벨로 기록합니다.
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
     * MES command 메시지를 생성해 발행합니다.
     *
     * <p>
     * MDF 정의가 존재하면 data는 MDF 결과로 생성하고,
     * metadata(eventType/correlationId/traceId/eqpId)는 기존 정책을 유지합니다.
     * </p>
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

        final Optional<BusinessMdfMessageComposer.MdfComposeResult> mdfResult = mdfMessageComposer.compose(
                context,
                MdfTargetType.MES
        );

        final Map<String, Object> data;
        if (mdfResult.isPresent()) {
            data = new LinkedHashMap<>(mdfResult.orElseThrow().kafkaDataBlock());
        } else {
            data = new LinkedHashMap<>(BusinessWorkflowCommandSupport.buildCommandPayload(context));
            data.put("rawMessage", BusinessWorkflowCommandSupport.resolveRawMessage(context));
            data.put("transactionId", BusinessWorkflowCommandSupport.resolveTransactionId(context));
        }

        final BusinessMesCommandMessage message = new BusinessMesCommandMessage(
                eventType,
                correlationId,
                context.record().eqpId(),
                BusinessWorkflowCommandSupport.resolveTraceId(context),
                data
        );

        mesCommandPublishPort.publish(message);
        log.info("MES command published from workflow action. eqpId={}, correlationId={}, eventType={}, workflowKey={}, actionName={}, mdfApplied={}",
                context.record().eqpId(),
                correlationId,
                eventType,
                context.workflowEntry().workflowKey(),
                context.workflowEntry().actionName(),
                mdfResult.isPresent());
        if (log.isDebugEnabled()) {
            log.debug("MES command payload detail. mdfApplied={}, data={}", mdfResult.isPresent(), data);
        }
    }
}
