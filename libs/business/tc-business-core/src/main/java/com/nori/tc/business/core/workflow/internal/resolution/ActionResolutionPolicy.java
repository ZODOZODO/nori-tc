package com.nori.tc.business.core.workflow.internal.resolution;

import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionKey;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionMethodInvoker;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 워크플로우 액션 해석 정책을 담당하는 클래스입니다.
 *
 * <p>정책 우선순위는 다음과 같습니다.</p>
 * <p>1) 플러그인 액션이 존재하면 플러그인을 선택합니다.</p>
 * <p>2) 플러그인 액션이 없고 코어 액션이 존재하면 코어로 폴백합니다.</p>
 * <p>3) 둘 다 없으면 미해결(NONE)로 반환합니다.</p>
 */
public final class ActionResolutionPolicy {

    /**
     * 정책 해석 흐름을 추적하기 위한 로거입니다.
     */
    private static final Logger log = LoggerFactory.getLogger(ActionResolutionPolicy.class);

    /**
     * 플러그인 우선/코어 폴백 정책의 싱글턴 인스턴스입니다.
     */
    private static final ActionResolutionPolicy PLUGIN_FIRST_FALLBACK_CORE = new ActionResolutionPolicy();

    /**
     * 외부 생성은 허용하지 않고 팩토리 메서드를 통해서만 사용합니다.
     */
    private ActionResolutionPolicy() {
    }

    /**
     * 기본 정책 인스턴스를 반환합니다.
     *
     * @return 플러그인 우선/코어 폴백 정책
     */
    public static ActionResolutionPolicy pluginFirstFallbackCore() {
        return PLUGIN_FIRST_FALLBACK_CORE;
    }

    /**
     * 액션 키를 기준으로 플러그인/코어 실행기 중 실제 실행 대상을 해석합니다.
     *
     * @param eqpId 설비 ID
     * @param workflowKey 워크플로우 키
     * @param actionKey 조회 키
     * @param pluginRegistry 설비별 플러그인 액션 레지스트리
     * @param coreRegistry 코어 액션 레지스트리
     * @return 해석 결과 추적 정보
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
            if (log.isDebugEnabled()) {
                log.debug("Action resolved to PLUGIN. eqpId={}, workflowKey={}, actionKey={}, coreAlsoPresent={}",
                        eqpId,
                        workflowKey,
                        actionKey,
                        coreInvoker != null);
            }
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
            if (log.isDebugEnabled()) {
                log.debug("Action resolved to CORE fallback. eqpId={}, workflowKey={}, actionKey={}",
                        eqpId,
                        workflowKey,
                        actionKey);
            }
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

        if (log.isDebugEnabled()) {
            log.debug("Action resolution miss. eqpId={}, workflowKey={}, actionKey={}",
                    eqpId,
                    workflowKey,
                    actionKey);
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