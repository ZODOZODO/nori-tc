package com.nori.tc.common.task.execution.policy.runtime;

import com.nori.tc.common.task.execution.policy.types.TaskFailureContext;
import com.nori.tc.common.task.execution.policy.types.TaskHandlingDecision;

/**
 * 실패 상황에서 재시도/DLQ/계속 진행 여부를 결정하는 정책 인터페이스입니다.
 *
 * <p>구현체는 동일 입력 컨텍스트에 대해 일관된 결정을 반환해야 하며,
 * 결정 결과는 커밋/재시도/DLQ 흐름에 직접 영향을 줍니다.</p>
 */
@FunctionalInterface
public interface TaskHandlingPolicy {

    /**
     * 실패 컨텍스트를 기반으로 다음 처리 동작을 결정합니다.
     *
     * @param context 실패 컨텍스트
     * @return 정책 결정 결과
     */
    TaskHandlingDecision decide(TaskFailureContext context);
}
