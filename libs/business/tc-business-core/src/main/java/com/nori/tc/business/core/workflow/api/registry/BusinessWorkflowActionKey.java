package com.nori.tc.business.core.workflow.api.registry;

import com.nori.tc.business.core.workflow.api.action.BusinessWorkflowActionMessageType;

import java.util.Locale;
import java.util.Objects;

/**
 * 액션 레지스트리 조회 키입니다.
 *
 * <p>키 구성은 (messageType, actionName)이며 actionName은 대문자로 정규화됩니다.</p>
 */
public record BusinessWorkflowActionKey(
        BusinessWorkflowActionMessageType messageType,
        String actionName
) {

    /**
     * 생성 시 필수 값과 정규화를 수행합니다.
     */
    public BusinessWorkflowActionKey {
        Objects.requireNonNull(messageType, "messageType is null");
        actionName = normalizeActionName(actionName);
    }

    /**
     * 키 팩토리 메서드입니다.
     *
     * @param messageType 액션 메시지 타입
     * @param actionName 워크플로우 액션 이름
     * @return 정규화된 키
     */
    public static BusinessWorkflowActionKey of(
            final BusinessWorkflowActionMessageType messageType,
            final String actionName
    ) {
        return new BusinessWorkflowActionKey(messageType, actionName);
    }

    /**
     * 액션 이름을 정규화합니다.
     *
     * @param actionName 원본 액션 이름
     * @return trim + upper-case 적용 문자열
     */
    public static String normalizeActionName(final String actionName) {
        if (actionName == null || actionName.isBlank()) {
            throw new IllegalArgumentException("actionName is required");
        }
        return actionName.trim().toUpperCase(Locale.ROOT);
    }
}