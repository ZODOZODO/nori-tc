package com.nori.tc.common.task.execution.policy.types;

/**
 * 정책 판단 결과로 선택되는 처리 동작입니다.
 *
 * <p>동작 값은 실행 엔진의 후속 제어 흐름(재시도, DLQ, 커밋 여부)을 결정합니다.</p>
 */
public enum TaskHandlingAction {

    /**
     * backoff 이후 동일 메시지를 재시도합니다.
     */
    RETRY,

    /**
     * DLQ 레코드를 생성하고 실패 메시지를 격리합니다.
     */
    DLQ,

    /**
     * 실패로 처리하고 더 이상 진행하지 않습니다.
     */
    FAIL,

    /**
     * 실패로 보지 않고 다음 처리로 진행합니다.
     */
    CONTINUE
}
