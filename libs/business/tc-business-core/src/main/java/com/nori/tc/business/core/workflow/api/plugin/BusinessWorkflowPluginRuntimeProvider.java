package com.nori.tc.business.core.workflow.api.plugin;

import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionRegistry;

import java.util.Optional;

/**
 * 설비별 플러그인 액션 레지스트리 조회 포트입니다.
 */
@FunctionalInterface
public interface BusinessWorkflowPluginRuntimeProvider {

    /**
     * eqpId 기준으로 활성 플러그인 레지스트리를 조회합니다.
     *
     * @param eqpId 설비 ID
     * @return 플러그인 레지스트리(optional)
     */
    Optional<BusinessWorkflowActionRegistry> findRegistryByEqpId(String eqpId);

    /**
     * 항상 empty를 반환하는 no-op provider를 제공합니다.
     *
     * @return no-op provider
     */
    static BusinessWorkflowPluginRuntimeProvider noop() {
        return eqpId -> Optional.empty();
    }
}