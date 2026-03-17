package com.nori.tc.db.core.model.upsert;

/**
 * 모델 계열 문자열 컬럼의 공통 길이 계약을 검증합니다.
 */
final class ModelFieldLengthValidator {

    static final int MODEL_NAME_MAX_LENGTH = 1000;
    static final int MODEL_VERSION_MAX_LENGTH = 100;
    static final int WORKFLOW_NAME_MAX_LENGTH = 1000;
    static final int MESSAGE_NAME_MAX_LENGTH = 1000;
    static final int TRANSACTION_ID_MAX_LENGTH = 2000;
    static final int WORKFLOW_FILTER_MAX_LENGTH = 4000;
    static final int DCOP_ITEM_NAME_MAX_LENGTH = 1000;
    static final int VARIABLE_ID_MAX_LENGTH = 1000;
    static final int MDF_NAME_MAX_LENGTH = 1000;
    static final int PARAM_NAME_MAX_LENGTH = 1000;
    static final int SECS_MSG_NAME_MAX_LENGTH = 1000;
    static final int SOCKET_MSG_NAME_MAX_LENGTH = 1000;
    static final int MES_MSG_NAME_MAX_LENGTH = 1000;

    private ModelFieldLengthValidator() {
    }

    /**
     * 필수 문자열의 공백 여부와 최대 길이를 함께 검증합니다.
     */
    static void requireTextWithMax(final String value, final String fieldName, final int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null/blank");
        }
        validateOptionalTextMax(value, fieldName, maxLength);
    }

    /**
     * 선택 문자열이 존재할 때만 최대 길이를 검증합니다.
     */
    static void validateOptionalTextMax(final String value, final String fieldName, final int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " length must be <= " + maxLength);
        }
    }
}
