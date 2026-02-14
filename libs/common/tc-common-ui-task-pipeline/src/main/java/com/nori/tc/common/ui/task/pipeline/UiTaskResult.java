package com.nori.tc.common.ui.task.pipeline;

/**
 * UI 작업 처리 결과 모델입니다.
 *
 * @param status PASS/FAIL 상태
 * @param errorCode 실패 시 오류 코드
 * @param errorMessage 실패 시 오류 메시지
 */
public record UiTaskResult(
        UiTaskReplyStatus status,
        String errorCode,
        String errorMessage
) {

    /**
     * 성공(PASS) 결과를 생성합니다.
     *
     * @return 성공 결과
     */
    public static UiTaskResult pass() {
        return new UiTaskResult(UiTaskReplyStatus.PASS, null, null);
    }

    /**
     * 실패(FAIL) 결과를 생성합니다.
     *
     * @param errorCode 실패 오류 코드
     * @param errorMessage 실패 메시지
     * @return 실패 결과
     */
    public static UiTaskResult fail(final String errorCode, final String errorMessage) {
        return new UiTaskResult(UiTaskReplyStatus.FAIL, errorCode, errorMessage);
    }
}
