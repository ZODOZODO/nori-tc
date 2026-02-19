package com.nori.tc.common.task.policy;

/**
 * 실패 처리 후속 액션 종류입니다.
 */
public enum TaskHandlingAction {

    /**
     * 지정된 backoff 이후 재시도합니다.
     */
    RETRY,

    /**
     * DLQ로 이관하고 현재 처리 흐름은 완료로 간주합니다.
     */
    DLQ,

    /**
     * 추가 처리 없이 실패 종료합니다.
     */
    FAIL,

    /**
     * 실패로 간주하지 않고 정상 종료합니다.
     * 예: WORKFLOW_NOT_FOUND 정책.
     */
    CONTINUE
}

