package com.nori.tc.common.task.execution.policy.types;

/**
 * 태스크 실패 유형을 구분하는 열거형입니다.
 *
 * <p>정책 판단, DLQ 분류, 운영 지표 태깅에 공통으로 사용합니다.</p>
 */
public enum TaskFailureCategory {

    /**
     * 입력값/계약 검증 실패입니다.
     */
    VALIDATION,

    /**
     * 매핑 가능한 워크플로우가 존재하지 않는 경우입니다.
     *
     * <p>업무 규칙에 따라 정상 continue로 처리될 수 있습니다.</p>
     */
    WORKFLOW_NOT_FOUND,

    /**
     * 워크플로우 필터 평가 실패입니다.
     */
    FILTER_EVAL,

    /**
     * 액션 실행 단계 실패입니다.
     */
    ACTION_EXEC,

    /**
     * 제한 시간 초과로 인터럽트가 발생한 경우입니다.
     */
    TIMEOUT,

    /**
     * 분류되지 않은 기타 오류입니다.
     */
    UNKNOWN
}