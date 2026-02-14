package com.nori.tc.business.core.workflow;

import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * workflow action 디스패처 구현체입니다.
 *
 * <p>실행 순서:</p>
 * <p>1) 메시지 타입(SECS/SOCKET/MES) 결정</p>
 * <p>2) workflow row마다 key=(MessageType, action_name) 생성</p>
 * <p>3) plugin registry 우선, 없으면 core registry에서 실행기 조회</p>
 * <p>4) 액션 메서드 호출</p>
 */
@Primary
@Component
public class BusinessWorkflowDispatchingActionExecutor implements BusinessWorkflowActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowDispatchingActionExecutor.class);

    private final BusinessWorkflowCoreActionRegistry coreActionRegistry;
    private final BusinessWorkflowPluginRuntimeProvider pluginRuntimeProvider;

    /**
     * 디스패처 의존성을 주입받습니다.
     *
     * @param coreActionRegistry 코어 액션 레지스트리
     * @param pluginRuntimeProvider 설비별 플러그인 레지스트리 provider
     */
    public BusinessWorkflowDispatchingActionExecutor(
            final BusinessWorkflowCoreActionRegistry coreActionRegistry,
            final BusinessWorkflowPluginRuntimeProvider pluginRuntimeProvider
    ) {
        this.coreActionRegistry = Objects.requireNonNull(coreActionRegistry, "coreActionRegistry is null");
        this.pluginRuntimeProvider = Objects.requireNonNull(pluginRuntimeProvider, "pluginRuntimeProvider is null");
    }

    @Override
    public void execute(
            final BusinessInboundRecord record,
            final TcModelRuntime modelRuntime,
            final BusinessWorkflowMatchResult matchResult
    ) {
        Objects.requireNonNull(record, "record is null");
        Objects.requireNonNull(modelRuntime, "modelRuntime is null");
        Objects.requireNonNull(matchResult, "matchResult is null");

        if (!matchResult.hasMatchedWorkflow()) {
            return;
        }

        final BusinessWorkflowActionMessageType actionMessageType =
                BusinessWorkflowActionMessageType.from(record, modelRuntime);
        final BusinessWorkflowActionRegistry coreRegistry = coreActionRegistry.registry();
        final BusinessWorkflowActionRegistry pluginRegistry =
                pluginRuntimeProvider.findRegistryByEqpId(record.eqpId()).orElse(BusinessWorkflowActionRegistry.empty());

        int pluginExecutedCount = 0;
        int coreExecutedCount = 0;

        for (WorkflowRuntimeEntry workflowEntry : matchResult.matchedWorkflows()) {
            final BusinessWorkflowActionKey actionKey =
                    BusinessWorkflowActionKey.of(actionMessageType, workflowEntry.actionName());

            final BusinessWorkflowActionMethodInvoker pluginInvoker = pluginRegistry.find(actionKey).orElse(null);
            final BusinessWorkflowActionMethodInvoker coreInvoker =
                    pluginInvoker == null ? coreRegistry.find(actionKey).orElse(null) : null;

            final BusinessWorkflowActionMethodInvoker selectedInvoker = pluginInvoker != null ? pluginInvoker : coreInvoker;
            if (selectedInvoker == null) {
                throw new BusinessWorkflowActionExecutionException(
                        "Action handler not found. eqpId=" + record.eqpId()
                                + ", key=" + actionKey
                                + ", workflowKey=" + workflowEntry.workflowKey()
                );
            }

            final BusinessWorkflowActionContext actionContext = new BusinessWorkflowActionContext(
                    record,
                    modelRuntime,
                    workflowEntry,
                    matchResult.filterContext(),
                    actionMessageType
            );

            selectedInvoker.invoke(actionContext);
            if (pluginInvoker != null) {
                pluginExecutedCount++;
                if (log.isDebugEnabled()) {
                    log.debug("Workflow action executed by plugin. eqpId={}, key={}, method={}, workflowKey={}",
                            record.eqpId(),
                            actionKey,
                            selectedInvoker.describeMethod(),
                            workflowEntry.workflowKey());
                }
            } else {
                coreExecutedCount++;
                if (log.isDebugEnabled()) {
                    log.debug("Workflow action executed by core. eqpId={}, key={}, method={}, workflowKey={}",
                            record.eqpId(),
                            actionKey,
                            selectedInvoker.describeMethod(),
                            workflowEntry.workflowKey());
                }
            }
        }

        log.info("Workflow actions executed. eqpId={}, messageName={}, total={}, plugin={}, core={}, messageType={}",
                record.eqpId(),
                record.messageName(),
                matchResult.matchedWorkflows().size(),
                pluginExecutedCount,
                coreExecutedCount,
                actionMessageType);
    }
}



