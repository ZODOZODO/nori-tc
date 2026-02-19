package com.nori.tc.common.task.policy;

/**
 * 태스크 실패 카테고리 표준입니다.
 *
 * <p>운영 관측과 DLQ 사유 집계를 위해 앱별 에러를 공통 분류로 정규화합니다.</p>
 */
public enum TaskFailureCategory {

    /**
     * 입력 검증 또는 필수 필드 검증 실패입니다.
     */
    VALIDATION,

    /**
     * workflow를 찾지 못한 케이스입니다.
     * 정책상 실패가 아닌 정상 종료 대상으로 취급할 수 있습니다.
     */
    WORKFLOW_NOT_FOUND,

    /**
     * workflow filter 평가 실패입니다.
     */
    FILTER_EVAL,

    /**
     * action 실행 실패입니다.
     */
    ACTION_EXEC,

    /**
     * 실행 시간 제한 초과(timeout interrupt)입니다.
     */
    TIMEOUT,

    /**
     * 분류되지 않은 예외입니다.
     */
    UNKNOWN
}

