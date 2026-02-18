package com.nori.tc.business.core.workflow;

import java.util.Objects;

/**
 * workflow action 해석 결과를 추적하기 위한 불변 trace 모델입니다.
 *
 * <p>Phase 3 목표인 "plugin 우선 + core fallback" 정책의 의사결정 결과를
 * 실행 로그/예외 메시지에서 일관되게 표현하기 위해 사용합니다.</p>
 */
public record ActionResolutionTrace(
        String eqpId,
        long workflowKey,
        BusinessWorkflowActionKey actionKey,
        ResolutionSource resolutionSource,
        BusinessWorkflowActionMethodInvoker selectedInvoker,
        boolean pluginActionPresent,
        boolean coreActionPresent
) {

    /**
     * trace 생성 시 필수값을 검증합니다.
     */
    public ActionResolutionTrace {
        eqpId = normalizeEqpId(eqpId);
        Objects.requireNonNull(actionKey, "actionKey is null");
        Objects.requireNonNull(resolutionSource, "resolutionSource is null");
        if (workflowKey <= 0L) {
            throw new IllegalArgumentException("workflowKey must be > 0");
        }
    }

    /**
     * 실행 가능한 action invoker가 선택되었는지 반환합니다.
     */
    public boolean isResolved() {
        return selectedInvoker != null;
    }

    /**
     * plugin/core 모두 존재하는 상황에서 plugin이 선택된 override 케이스인지 반환합니다.
     */
    public boolean isPluginOverride() {
        return resolutionSource == ResolutionSource.PLUGIN
                && pluginActionPresent
                && coreActionPresent;
    }

    /**
     * plugin에 액션이 없어 core fallback이 적용된 케이스인지 반환합니다.
     */
    public boolean isCoreFallback() {
        return resolutionSource == ResolutionSource.CORE
                && !pluginActionPresent
                && coreActionPresent;
    }

    /**
     * 로그/예외 메시지에 포함할 요약 문자열을 반환합니다.
     */
    public String summary() {
        return "eqpId=" + eqpId
                + ", workflowKey=" + workflowKey
                + ", actionKey=" + actionKey
                + ", source=" + resolutionSource
                + ", pluginActionPresent=" + pluginActionPresent
                + ", coreActionPresent=" + coreActionPresent
                + ", selectedMethod=" + (selectedInvoker == null ? "N/A" : selectedInvoker.describeMethod());
    }

    /**
     * eqpId 문자열을 검증/정규화합니다.
     */
    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        return eqpId.trim();
    }

    /**
     * action 해석 소스를 표현하는 enum입니다.
     */
    public enum ResolutionSource {
        PLUGIN,
        CORE,
        NONE
    }
}
