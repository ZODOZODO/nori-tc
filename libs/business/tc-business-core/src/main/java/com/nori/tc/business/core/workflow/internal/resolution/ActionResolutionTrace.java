package com.nori.tc.business.core.workflow.internal.resolution;

import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionKey;
import com.nori.tc.business.core.workflow.api.registry.BusinessWorkflowActionMethodInvoker;

import java.util.Objects;

/**
 * 액션 해석 결과를 추적하기 위한 불변 모델입니다.
 *
 * <p>런타임 로그/예외 메시지에서 어떤 소스(PLUGIN/CORE/NONE)가 선택되었는지
 * 일관되게 표현하기 위해 사용합니다.</p>
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
     * 레코드 생성 시 필수 값과 도메인 제약을 검증합니다.
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
     * 실행 가능한 액션이 최종 선택되었는지 반환합니다.
     *
     * @return 선택된 실행기가 있으면 true
     */
    public boolean isResolved() {
        return selectedInvoker != null;
    }

    /**
     * 플러그인/코어가 모두 존재하는 상황에서 플러그인이 선택되었는지 반환합니다.
     *
     * @return 플러그인 오버라이드 상황이면 true
     */
    public boolean isPluginOverride() {
        return resolutionSource == ResolutionSource.PLUGIN
                && pluginActionPresent
                && coreActionPresent;
    }

    /**
     * 플러그인 액션이 없어 코어로 폴백되었는지 반환합니다.
     *
     * @return 코어 폴백 상황이면 true
     */
    public boolean isCoreFallback() {
        return resolutionSource == ResolutionSource.CORE
                && !pluginActionPresent
                && coreActionPresent;
    }

    /**
     * 로그/예외 메시지에 사용하기 위한 단일 요약 문자열을 생성합니다.
     *
     * @return 해석 결과 요약 문자열
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
     * eqpId 문자열을 정규화하고 필수 값을 검증합니다.
     */
    private static String normalizeEqpId(final String eqpId) {
        if (eqpId == null || eqpId.isBlank()) {
            throw new IllegalArgumentException("eqpId is required");
        }
        return eqpId.trim();
    }

    /**
     * 액션 해석 소스를 나타냅니다.
     */
    public enum ResolutionSource {
        PLUGIN,
        CORE,
        NONE
    }
}