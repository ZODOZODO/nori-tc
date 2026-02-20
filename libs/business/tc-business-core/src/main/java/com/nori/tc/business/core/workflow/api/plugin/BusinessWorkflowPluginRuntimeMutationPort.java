package com.nori.tc.business.core.workflow.api.plugin;

/**
 * 플러그인 런타임 갱신 포트입니다.
 */
@FunctionalInterface
public interface BusinessWorkflowPluginRuntimeMutationPort {

    /**
     * 특정 설비의 플러그인 런타임을 리로드합니다.
     *
     * @param eqpId 설비 ID
     */
    void reloadByEqpId(String eqpId);

    /**
     * 특정 설비의 플러그인 런타임을 제거합니다.
     *
     * <p>기본 구현은 no-op입니다.</p>
     *
     * @param eqpId 설비 ID
     */
    default void removeByEqpId(final String eqpId) {
        // default no-op
    }

    /**
     * no-op mutation port를 반환합니다.
     *
     * @return no-op 구현체
     */
    static BusinessWorkflowPluginRuntimeMutationPort noop() {
        return eqpId -> {
            // no-op
        };
    }
}