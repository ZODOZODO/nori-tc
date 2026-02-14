package com.nori.tc.business.core.workflow;

import java.util.Optional;

/**
 * 설비별 플러그인 액션 레지스트리 조회 포트입니다.
 */
@FunctionalInterface
public interface BusinessWorkflowPluginRuntimeProvider {

    /**
     * eqpId 기준으로 현재 활성 플러그인 레지스트리를 조회합니다.
     *
     * @param eqpId equipment id
     * @return plugin registry(optional)
     */
    Optional<BusinessWorkflowActionRegistry> findRegistryByEqpId(String eqpId);

    /**
     * no-op provider를 반환합니다.
     *
     * @return provider returning empty registry always
     */
    static BusinessWorkflowPluginRuntimeProvider noop() {
        return eqpId -> Optional.empty();
    }
}


