package com.nori.tc.business.core.workflow;

import java.util.Objects;

/**
 * workflow action 해석 정책(Policy)입니다.
 *
 * <p>현재 정책은 고정적으로 "plugin 우선, core fallback"이며,
 * 정책 결과는 {@link ActionResolutionTrace}로 반환됩니다.</p>
 */
public final class ActionResolutionPolicy {

    /**
     * plugin 우선/fallback core 정책 인스턴스입니다.
     */
    private static final ActionResolutionPolicy PLUGIN_FIRST_FALLBACK_CORE = new ActionResolutionPolicy();

    /**
     * 외부 생성은 막고 정적 팩터리로만 사용합니다.
     */
    private ActionResolutionPolicy() {
    }

    /**
     * 기본 정책 인스턴스를 반환합니다.
     */
    public static ActionResolutionPolicy pluginFirstFallbackCore() {
        return PLUGIN_FIRST_FALLBACK_CORE;
    }

    /**
     * action key를 plugin/core 레지스트리에서 해석합니다.
     *
     * <p>우선순위:</p>
     * <p>1) plugin에 key가 있으면 plugin 선택</p>
     * <p>2) plugin에 없고 core에 있으면 core fallback 선택</p>
     * <p>3) 둘 다 없으면 미해결(NONE)</p>
     *
     * @param eqpId 설비 ID
     * @param workflowKey workflow key
     * @param actionKey 조회 key
     * @param pluginRegistry 설비 plugin registry
     * @param coreRegistry core registry
     * @return 해석 trace
     */
    public ActionResolutionTrace resolve(
            final String eqpId,
            final long workflowKey,
            final BusinessWorkflowActionKey actionKey,
            final BusinessWorkflowActionRegistry pluginRegistry,
            final BusinessWorkflowActionRegistry coreRegistry
    ) {
        Objects.requireNonNull(actionKey, "actionKey is null");
        Objects.requireNonNull(pluginRegistry, "pluginRegistry is null");
        Objects.requireNonNull(coreRegistry, "coreRegistry is null");

        final BusinessWorkflowActionMethodInvoker pluginInvoker = pluginRegistry.find(actionKey).orElse(null);
        final BusinessWorkflowActionMethodInvoker coreInvoker = coreRegistry.find(actionKey).orElse(null);

        if (pluginInvoker != null) {
            return new ActionResolutionTrace(
                    eqpId,
                    workflowKey,
                    actionKey,
                    ActionResolutionTrace.ResolutionSource.PLUGIN,
                    pluginInvoker,
                    true,
                    coreInvoker != null
            );
        }

        if (coreInvoker != null) {
            return new ActionResolutionTrace(
                    eqpId,
                    workflowKey,
                    actionKey,
                    ActionResolutionTrace.ResolutionSource.CORE,
                    coreInvoker,
                    false,
                    true
            );
        }

        return new ActionResolutionTrace(
                eqpId,
                workflowKey,
                actionKey,
                ActionResolutionTrace.ResolutionSource.NONE,
                null,
                false,
                false
        );
    }
}
