package com.nori.tc.comm.adapters.kafka.messaging.ui;

/**
 * UI task 표준 에러코드 집합입니다.
 *
 * <p>UI 응답(ERRORCODE)과 내부 예외 코드를 동일한 상수로 관리해
 * 문자열 오타를 방지하고, 운영 중 코드 기준 검색을 쉽게 만듭니다.</p>
 */
public final class GatewayUiTaskErrorCode {

    private GatewayUiTaskErrorCode() {
    }

    /**
     * 입력/형식 검증 계열
     */
    public static final String INVALID_EVENT_TYPE = "INVALID_EVENT_TYPE";
    public static final String INVALID_INTERFACE_TYPE = "INVALID_INTERFACE_TYPE";
    public static final String EQP_ID_REQUIRED = "EQP_ID_REQUIRED";
    public static final String UI_MESSAGE_REQUIRED = "UI_MESSAGE_REQUIRED";

    /**
     * 라우팅/핸들러 계열
     */
    public static final String HANDLER_NOT_FOUND = "HANDLER_NOT_FOUND";
    public static final String DUPLICATE_TRACE_ID = "DUPLICATE_TRACE_ID";

    /**
     * 장비 상태/조회 계열
     */
    public static final String EQP_NOT_FOUND = "EQP_NOT_FOUND";
    public static final String EQP_CONTEXT_NOT_FOUND = "EQP_CONTEXT_NOT_FOUND";
    public static final String INTERFACE_MISMATCH = "INTERFACE_MISMATCH";
    public static final String EQP_DISABLED = "EQP_DISABLED";
    public static final String EQP_NOT_STARTED = "EQP_NOT_STARTED";
    public static final String EQP_NOT_CONNECTED = "EQP_NOT_CONNECTED";
    public static final String EQP_RUNNING = "EQP_RUNNING";

    /**
     * 타임아웃/인프라 계열
     */
    public static final String EQP_START_TIMEOUT = "EQP_START_TIMEOUT";
    public static final String EQP_END_TIMEOUT = "EQP_END_TIMEOUT";
    public static final String TASK_TIMEOUT = "TASK_TIMEOUT";
    public static final String REPLY_PUBLISH_FAILED = "REPLY_PUBLISH_FAILED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /**
     * JARFILE 계열
     */
    public static final String JARFILE_TASK_NOT_CONFIGURED = "JARFILE_TASK_NOT_CONFIGURED";
    public static final String JARFILE_TASK_FAILED = "JARFILE_TASK_FAILED";
}
