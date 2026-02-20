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
     * 특정 설비의 플러그인 런타임을 제거합니다.
     *
     * <p>기본 구현은 no-op입니다.</p>
     * <p>플러그인 어댑터가 연결된 환경에서는 실제 구현체가 오버라이드하여
     * eqpId 기준 런타임 캐시를 제거합니다.</p>
     *
     * @param eqpId equipment id
     */
    default void removeByEqpId(final String eqpId) {
        // default no-op
    }

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


