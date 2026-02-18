package com.nori.tc.business.core.workflow;

import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * workflow action 디스패치 실행기입니다.
 *
 * <p>핵심 동작:</p>
 * <p>1) inbound record/model runtime으로 messageType(SECS/SOCKET/MES)를 결정합니다.</p>
 * <p>2) workflow row마다 {@code (messageType, action_name)} 키를 생성합니다.</p>
 * <p>3) {@link ActionResolutionPolicy}로 plugin/core 실행기를 해석합니다.</p>
 * <p>4) 해석 결과(trace)를 기반으로 실행 및 운영 로그를 남깁니다.</p>
 */
@Primary
@Component
public class BusinessWorkflowDispatchingActionExecutor implements BusinessWorkflowActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowDispatchingActionExecutor.class);

    private final BusinessWorkflowCoreActionRegistry coreActionRegistry;
    private final BusinessWorkflowPluginRuntimeProvider pluginRuntimeProvider;
    private final ActionResolutionPolicy actionResolutionPolicy;

    /**
     * 기본 정책(plugin 우선, core fallback)으로 디스패처를 생성합니다.
     *
     * @param coreActionRegistry 코어 액션 레지스트리
     * @param pluginRuntimeProvider 설비별 plugin 런타임 provider
     */
    public BusinessWorkflowDispatchingActionExecutor(
            final BusinessWorkflowCoreActionRegistry coreActionRegistry,
            final BusinessWorkflowPluginRuntimeProvider pluginRuntimeProvider
    ) {
        this(coreActionRegistry, pluginRuntimeProvider, ActionResolutionPolicy.pluginFirstFallbackCore());
    }

    /**
     * 액션 해석 정책(ActionResolutionPolicy)을 포함해 의존성을 주입받습니다.
     *
     * @param coreActionRegistry 코어 액션 레지스트리
     * @param pluginRuntimeProvider 설비별 plugin 런타임 provider
     * @param actionResolutionPolicy 액션 해석 정책
     */
    BusinessWorkflowDispatchingActionExecutor(
            final BusinessWorkflowCoreActionRegistry coreActionRegistry,
            final BusinessWorkflowPluginRuntimeProvider pluginRuntimeProvider,
            final ActionResolutionPolicy actionResolutionPolicy
    ) {
        this.coreActionRegistry = Objects.requireNonNull(coreActionRegistry, "coreActionRegistry is null");
        this.pluginRuntimeProvider = Objects.requireNonNull(pluginRuntimeProvider, "pluginRuntimeProvider is null");
        this.actionResolutionPolicy = Objects.requireNonNull(actionResolutionPolicy, "actionResolutionPolicy is null");
    }

    /**
     * 매칭된 workflow 목록을 순회하며 action을 실행합니다.
     *
     * <p>plugin/core 해석 결과를 trace로 남기고,
     * override/fallback/miss 케이스를 운영 로그로 분리합니다.</p>
     */
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
        int pluginOverrideCount = 0;
        int coreFallbackCount = 0;

        for (WorkflowRuntimeEntry workflowEntry : matchResult.matchedWorkflows()) {
            final BusinessWorkflowActionKey actionKey =
                    BusinessWorkflowActionKey.of(actionMessageType, workflowEntry.actionName());

            // Phase 3: 액션 선택 로직을 정책 클래스로 분리해 일관된 trace/로그를 확보합니다.
            final ActionResolutionTrace trace = actionResolutionPolicy.resolve(
                    record.eqpId(),
                    workflowEntry.workflowKey(),
                    actionKey,
                    pluginRegistry,
                    coreRegistry
            );

            if (!trace.isResolved()) {
                log.info("ACTION_RESOLUTION_MISS. {}", trace.summary());
                throw new BusinessWorkflowActionExecutionException(
                        "Action handler not found. eqpId=" + record.eqpId()
                                + ", key=" + actionKey
                                + ", workflowKey=" + workflowEntry.workflowKey()
                                + ", resolution=" + trace.summary()
                );
            }

            final BusinessWorkflowActionContext actionContext = new BusinessWorkflowActionContext(
                    record,
                    modelRuntime,
                    workflowEntry,
                    matchResult.filterContext(),
                    actionMessageType
            );

            trace.selectedInvoker().invoke(actionContext);

            if (trace.resolutionSource() == ActionResolutionTrace.ResolutionSource.PLUGIN) {
                pluginExecutedCount++;

                // plugin/core 동시 존재 시 plugin을 선택한 override 케이스는 info로 명시합니다.
                if (trace.isPluginOverride()) {
                    pluginOverrideCount++;
                    log.info("ACTION_RESOLUTION_PLUGIN_OVERRIDE. {}", trace.summary());
                }

                if (log.isDebugEnabled()) {
                    log.debug("Workflow action executed by plugin. eqpId={}, key={}, method={}, workflowKey={}",
                            record.eqpId(),
                            actionKey,
                            trace.selectedInvoker().describeMethod(),
                            workflowEntry.workflowKey());
                }
            } else {
                coreExecutedCount++;

                // plugin 미존재로 core fallback이 발생한 경우 debug trace를 남깁니다.
                if (trace.isCoreFallback()) {
                    coreFallbackCount++;
                    if (log.isDebugEnabled()) {
                        log.debug("ACTION_RESOLUTION_CORE_FALLBACK. {}", trace.summary());
                    }
                }

                if (log.isDebugEnabled()) {
                    log.debug("Workflow action executed by core. eqpId={}, key={}, method={}, workflowKey={}",
                            record.eqpId(),
                            actionKey,
                            trace.selectedInvoker().describeMethod(),
                            workflowEntry.workflowKey());
                }
            }
        }

        log.info("Workflow actions executed. eqpId={}, messageName={}, total={}, plugin={}, core={}, pluginOverride={}, coreFallback={}, messageType={}",
                record.eqpId(),
                record.messageName(),
                matchResult.matchedWorkflows().size(),
                pluginExecutedCount,
                coreExecutedCount,
                pluginOverrideCount,
                coreFallbackCount,
                actionMessageType);
    }
}
