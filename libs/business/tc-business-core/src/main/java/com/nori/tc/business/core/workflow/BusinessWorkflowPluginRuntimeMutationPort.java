package com.nori.tc.business.core.workflow;

/**
 * 플러그인 런타임 갱신 포트입니다.
 *
 * <p>주로 UI 이벤트(EQP_UPDATE_JARFILE) 처리 경로에서 호출합니다.</p>
 */
@FunctionalInterface
public interface BusinessWorkflowPluginRuntimeMutationPort {

    /**
     * 특정 설비의 플러그인 런타임을 재로딩합니다.
     *
     * @param eqpId equipment id
     */
    void reloadByEqpId(String eqpId);

    /**
     * no-op mutation port를 반환합니다.
     *
     * @return no-op implementation
     */
    static BusinessWorkflowPluginRuntimeMutationPort noop() {
        return eqpId -> {
            // no-op
        };
    }
}


