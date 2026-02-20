package com.nori.tc.business.core.workflow.api.action;

/**
 * 워크플로우 액션 실행 실패를 나타내는 예외입니다.
 *
 * <p>런타임 엔진에서는 이 예외를 ACTION_EXEC 카테고리로 분류해
 * retry/DLQ 정책을 적용합니다.</p>
 */
public class BusinessWorkflowActionExecutionException extends RuntimeException {

    /**
     * 메시지 기반 예외를 생성합니다.
     *
     * @param message 예외 메시지
     */
    public BusinessWorkflowActionExecutionException(final String message) {
        super(message);
    }

    /**
     * 메시지와 원인 예외를 포함해 예외를 생성합니다.
     *
     * @param message 예외 메시지
     * @param cause 원인 예외
     */
    public BusinessWorkflowActionExecutionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}