package com.nori.tc.business.core.ui;

/**
 * Business UI task 처리 에러 코드 상수입니다.
 */
public final class BusinessUiTaskErrorCode {

    /**
     * 지원하지 않는 eventType입니다.
     */
    public static final String UNSUPPORTED_EVENT_TYPE = "UNSUPPORTED_EVENT_TYPE";

    /**
     * UI 메시지 본문 형식이 올바르지 않습니다.
     */
    public static final String INVALID_UI_MESSAGE = "INVALID_UI_MESSAGE";

    /**
     * modelVersionKey가 누락되었거나 파싱에 실패했습니다.
     */
    public static final String MODEL_KEY_REQUIRED = "MODEL_KEY_REQUIRED";

    /**
     * eqpId 기준 modelVersionKey 바인딩이 존재하지 않습니다.
     */
    public static final String MODEL_BINDING_NOT_FOUND = "MODEL_BINDING_NOT_FOUND";

    /**
     * model runtime 갱신 처리 중 예외가 발생했습니다.
     */
    public static final String MODEL_RUNTIME_UPDATE_FAILED = "MODEL_RUNTIME_UPDATE_FAILED";

    /**
     * eqpId -> modelVersionKey 바인딩 삭제 처리 중 예외가 발생했습니다.
     */
    public static final String MODEL_BINDING_DELETE_FAILED = "MODEL_BINDING_DELETE_FAILED";

    /**
     * workflow 플러그인(JAR) 런타임 리로드에 실패했습니다.
     */
    public static final String WORKFLOW_PLUGIN_RELOAD_FAILED = "WORKFLOW_PLUGIN_RELOAD_FAILED";

    /**
     * workflow 플러그인(JAR) 런타임 제거에 실패했습니다.
     */
    public static final String WORKFLOW_PLUGIN_REMOVE_FAILED = "WORKFLOW_PLUGIN_REMOVE_FAILED";

    /**
     * eqpId 필수값이 누락되었습니다.
     */
    public static final String EQP_ID_REQUIRED = "EQP_ID_REQUIRED";

    /**
     * 처리 중 예기치 못한 내부 오류가 발생했습니다.
     */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /**
     * BusinessUiTaskErrorCode 생성자를 초기화합니다.
     *
     */

    private BusinessUiTaskErrorCode() {
        throw new IllegalStateException("BusinessUiTaskErrorCode cannot be instantiated");
    }
}
