package com.nori.tc.business.core.workflow;

/**
 * workflow action 실행 실패를 나타내는 예외입니다.
 *
 * <p>런타임 엔진에서는 이 예외를 {@code ACTION_EXEC} 카테고리로 분류해
 * retry/DLQ 정책을 적용합니다.</p>
 */
public class BusinessWorkflowActionExecutionException extends RuntimeException {

    /**
     * BusinessWorkflowActionExecutionException 생성자를 초기화합니다.
     *
     * @param message 입력 값
     */

    public BusinessWorkflowActionExecutionException(final String message) {
        super(message);
    }

    /**
     * BusinessWorkflowActionExecutionException 생성자를 초기화합니다.
     *
     * @param message 입력 값
     * @param cause 입력 값
     */

    public BusinessWorkflowActionExecutionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

