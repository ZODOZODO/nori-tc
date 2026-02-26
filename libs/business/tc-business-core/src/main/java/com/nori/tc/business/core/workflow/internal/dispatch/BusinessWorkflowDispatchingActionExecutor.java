package com.nori.tc.business.core.workflow.internal.dispatch;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionContext;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionExecutionException;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionExecutor;
import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;
import com.nori.tc.business.core.workflow.api.match.BusinessWorkflowMatchResult;
import com.nori.tc.business.core.workflow.api.plugin.BusinessWorkflowPluginRuntimeProvider;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionKey;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistry;
import com.nori.tc.business.core.workflow.internal.core.BusinessWorkflowCoreActionRegistry;
import com.nori.tc.business.core.workflow.internal.resolution.ActionResolutionPolicy;
import com.nori.tc.business.core.workflow.internal.resolution.ActionResolutionTrace;
import com.nori.tc.business.domain.modelcache.TcModelRuntime;
import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;
import com.nori.tc.business.domain.runtime.BusinessInboundRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 매칭된 워크플로 액션을 실제 실행기로 디스패치하는 기본 액션 실행기입니다.
 *
 * <p>동작 순서:</p>
 * <p>1) {@link ActionResolutionPolicy}로 plugin/core 실행기 후보를 해석합니다.</p>
 * <p>2) 선택된 invoker를 실행하고, 실패 시 실행 문맥을 포함한 예외 로그를 남깁니다.</p>
 * <p>3) plugin override / core fallback 상황은 운영 추적을 위해 INFO 로그로 기록합니다.</p>
 * <p>4) 일반 선택 결과는 DEBUG 로그로 기록합니다.</p>
 */
@Primary
@Component
public class BusinessWorkflowDispatchingActionExecutor implements BusinessWorkflowActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusinessWorkflowDispatchingActionExecutor.class);

    /**
     * 액션 해석 실패 시 사용하는 이벤트 코드입니다.
     */
    private static final String ACTION_RESOLUTION_EVENT_MISS = "ACTION_RESOLUTION_MISS";

    /**
     * plugin 실행기가 core 실행기를 대체한 경우 사용하는 이벤트 코드입니다.
     */
    private static final String ACTION_RESOLUTION_EVENT_PLUGIN_OVERRIDE = "ACTION_RESOLUTION_PLUGIN_OVERRIDE";

    /**
     * plugin 실행기가 없어서 core로 대체된 경우 사용하는 이벤트 코드입니다.
     */
    private static final String ACTION_RESOLUTION_EVENT_CORE_FALLBACK = "ACTION_RESOLUTION_CORE_FALLBACK";

    /**
     * 일반적인 실행기 선택 결과를 표현하는 이벤트 코드입니다.
     */
    private static final String ACTION_RESOLUTION_EVENT_SELECTED = "ACTION_RESOLUTION_SELECTED";

    /**
     * 액션 실행 중 예외가 발생했을 때 사용하는 이벤트 코드입니다.
     */
    private static final String ACTION_EXECUTION_EVENT_FAILED = "ACTION_EXECUTION_FAILED";

    private final BusinessWorkflowCoreActionRegistry coreActionRegistry;
    private final BusinessWorkflowPluginRuntimeProvider pluginRuntimeProvider;
    private final ActionResolutionPolicy actionResolutionPolicy;

    /**
     * 기본 정책(plugin 우선, core fallback)으로 디스패처를 생성합니다.
     *
     * @param coreActionRegistry core 액션 레지스트리 공급자
     * @param pluginRuntimeProvider 설비별 plugin 레지스트리 공급자
     */
    // 생성자가 2개이므로 스프링이 런타임 주입용 생성자를 명확히 선택하도록 지정합니다.
    @Autowired
    public BusinessWorkflowDispatchingActionExecutor(
            final BusinessWorkflowCoreActionRegistry coreActionRegistry,
            final BusinessWorkflowPluginRuntimeProvider pluginRuntimeProvider
    ) {
        this(coreActionRegistry, pluginRuntimeProvider, ActionResolutionPolicy.pluginFirstFallbackCore());
    }

    /**
     * 테스트/확장 목적에서 해석 정책을 주입받아 디스패처를 생성합니다.
     *
     * @param coreActionRegistry core 액션 레지스트리 공급자
     * @param pluginRuntimeProvider 설비별 plugin 레지스트리 공급자
     * @param actionResolutionPolicy 액션 선택 정책
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
     * 매칭 결과에 포함된 워크플로를 순서대로 실행합니다.
     *
     * <p>각 워크플로마다 action key를 생성하고, plugin/core 레지스트리에서 실행 가능한 invoker를 선택한 뒤
     * 선택된 invoker를 실제로 실행합니다.</p>
     *
     * @param record 수신 원본 레코드
     * @param modelRuntime 모델 런타임 정보
     * @param matchResult 워크플로 매칭 결과
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

            // 현재 워크플로 컨텍스트에서 plugin/core 후보를 비교하여 최종 실행기를 선택합니다.
            final ActionResolutionTrace trace = actionResolutionPolicy.resolve(
                    record.eqpId(),
                    workflowEntry.workflowKey(),
                    actionKey,
                    pluginRegistry,
                    coreRegistry
            );

            if (!trace.isResolved()) {
                log.info("{}. {}", ACTION_RESOLUTION_EVENT_MISS, trace.summary());
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

            try {
                trace.selectedInvoker().invoke(actionContext);
            } catch (RuntimeException ex) {
                log.warn(
                        "{}. eqpId={}, workflowKey={}, actionKey={}, resolution={}, reason={}",
                        ACTION_EXECUTION_EVENT_FAILED,
                        record.eqpId(),
                        workflowEntry.workflowKey(),
                        actionKey,
                        trace.summary(),
                        ex.getMessage(),
                        ex
                );
                throw ex;
            }

            if (trace.resolutionSource() == ActionResolutionTrace.ResolutionSource.PLUGIN) {
                pluginExecutedCount++;

                // plugin이 core를 대체한 경우는 운영 추적에서 중요한 이벤트이므로 INFO로 남깁니다.
                if (trace.isPluginOverride()) {
                    pluginOverrideCount++;
                    log.info("{}. {}", ACTION_RESOLUTION_EVENT_PLUGIN_OVERRIDE, trace.summary());
                } else if (log.isDebugEnabled()) {
                    log.debug(
                            "{}. source=PLUGIN, eqpId={}, workflowKey={}, actionKey={}, method={}",
                            ACTION_RESOLUTION_EVENT_SELECTED,
                            record.eqpId(),
                            workflowEntry.workflowKey(),
                            actionKey,
                            trace.selectedInvoker().describeMethod()
                    );
                }
            } else {
                coreExecutedCount++;

                // core fallback은 plugin 미구현 또는 불일치 신호일 수 있으므로 INFO로 남깁니다.
                if (trace.isCoreFallback()) {
                    coreFallbackCount++;
                    log.info("{}. {}", ACTION_RESOLUTION_EVENT_CORE_FALLBACK, trace.summary());
                } else if (log.isDebugEnabled()) {
                    log.debug(
                            "{}. source=CORE, eqpId={}, workflowKey={}, actionKey={}, method={}",
                            ACTION_RESOLUTION_EVENT_SELECTED,
                            record.eqpId(),
                            workflowEntry.workflowKey(),
                            actionKey,
                            trace.selectedInvoker().describeMethod()
                    );
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
