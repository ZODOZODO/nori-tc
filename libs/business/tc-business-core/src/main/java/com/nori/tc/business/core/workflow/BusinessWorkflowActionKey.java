package com.nori.tc.business.core.workflow;

import java.util.Locale;
import java.util.Objects;

/**
 * 액션 레지스트리 조회 키입니다.
 *
 * <p>설계 key 규칙인 {@code (MessageType, action_name)}를 불변 타입으로 표현합니다.</p>
 */
public record BusinessWorkflowActionKey(
        BusinessWorkflowActionMessageType messageType,
        String actionName
) {

    /**
     * 생성 시 key 구성값을 정규화합니다.
     *
     * <p>actionName은 대소문자 혼용으로 인한 매칭 실패를 막기 위해
     * 내부적으로 대문자로 통일합니다.</p>
     */
    public BusinessWorkflowActionKey {
        Objects.requireNonNull(messageType, "messageType is null");
        actionName = normalizeActionName(actionName);
    }

    /**
     * key 팩토리 메서드입니다.
     *
     * @param messageType action message type
     * @param actionName workflow action name
     * @return normalized key
     */
    public static BusinessWorkflowActionKey of(
            final BusinessWorkflowActionMessageType messageType,
            final String actionName
    ) {
        return new BusinessWorkflowActionKey(messageType, actionName);
    }

    /**
     * action name 정규화 함수입니다.
     *
     * @param actionName raw action name
     * @return normalized action name(upper-case)
     */
    public static String normalizeActionName(final String actionName) {
        if (actionName == null || actionName.isBlank()) {
            throw new IllegalArgumentException("actionName is required");
        }
        return actionName.trim().toUpperCase(Locale.ROOT);
    }
}


