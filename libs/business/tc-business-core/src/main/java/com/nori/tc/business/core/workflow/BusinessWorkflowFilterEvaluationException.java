package com.nori.tc.business.core.workflow;

/**
 * workflow_filter 평가 또는 파싱 실패를 나타내는 예외입니다.
 *
 * <p>런타임 엔진에서는 이 예외를 {@code FILTER_EVAL} 카테고리로 분류해
 * retry/DLQ 정책을 적용합니다.</p>
 */
public class BusinessWorkflowFilterEvaluationException extends RuntimeException {

    public BusinessWorkflowFilterEvaluationException(final String message) {
        super(message);
    }

    public BusinessWorkflowFilterEvaluationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

