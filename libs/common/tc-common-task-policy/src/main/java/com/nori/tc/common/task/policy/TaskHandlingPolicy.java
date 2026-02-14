package com.nori.tc.common.task.policy;

/**
 * 태스크 실패 처리 정책 계약입니다.
 */
@FunctionalInterface
public interface TaskHandlingPolicy {

    /**
     * 실패 컨텍스트를 평가하여 후속 액션을 결정합니다.
     *
     * @param context 실패 컨텍스트
     * @return 처리 결정
     */
    TaskHandlingDecision decide(TaskFailureContext context);
}

