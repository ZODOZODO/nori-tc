package com.nori.tc.common.task.execution.policy.runtime;

import com.nori.tc.common.consumer.runtime.FixedRetryPolicy;
import com.nori.tc.common.task.execution.policy.dlq.TaskDlqRecordFactory;
import com.nori.tc.common.task.execution.policy.types.TaskFailureCategory;
import com.nori.tc.common.task.execution.policy.types.TaskFailureContext;
import com.nori.tc.common.task.execution.policy.types.TaskHandlingAction;
import com.nori.tc.common.task.execution.policy.types.TaskHandlingDecision;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link TaskHandlingPolicyEvaluator} 정책 분기 테스트입니다.
 */
class TaskHandlingPolicyEvaluatorTest {

    /**
     * WORKFLOW_NOT_FOUND는 CONTINUE를 반환하는지 검증합니다.
     */
    @Test
    void shouldReturnContinueWhenWorkflowNotFound() {
        final TaskHandlingPolicyEvaluator policy = new TaskHandlingPolicyEvaluator(
                new FixedRetryPolicy(3, 100L),
                new TaskDlqRecordFactory(200),
                true
        );

        final TaskHandlingDecision decision = policy.decide(baseContext(
                TaskFailureCategory.WORKFLOW_NOT_FOUND,
                1,
                false,
                new IllegalStateException("workflow not found")
        ));

        Assertions.assertEquals(TaskHandlingAction.CONTINUE, decision.action());
        Assertions.assertEquals(TaskFailureCategory.WORKFLOW_NOT_FOUND, decision.finalCategory());
        Assertions.assertNull(decision.dlqRecord());
    }

    /**
     * 재시도 가능 조건에서 RETRY를 반환하는지 검증합니다.
     */
    @Test
    void shouldReturnRetryWhenRetryPolicyAllows() {
        final TaskHandlingPolicyEvaluator policy = new TaskHandlingPolicyEvaluator(
                new FixedRetryPolicy(3, 250L),
                new TaskDlqRecordFactory(200),
                true
        );

        final TaskHandlingDecision decision = policy.decide(baseContext(
                TaskFailureCategory.ACTION_EXEC,
                1,
                false,
                new RuntimeException("temporary failure")
        ));

        Assertions.assertEquals(TaskHandlingAction.RETRY, decision.action());
        Assertions.assertEquals(250L, decision.retryBackoffMs());
        Assertions.assertEquals(TaskFailureCategory.ACTION_EXEC, decision.finalCategory());
        Assertions.assertNull(decision.dlqRecord());
    }

    /**
     * 재시도 소진 시 DLQ를 반환하는지 검증합니다.
     */
    @Test
    void shouldReturnDlqWhenRetryExhausted() {
        final TaskHandlingPolicyEvaluator policy = new TaskHandlingPolicyEvaluator(
                new FixedRetryPolicy(2, 100L),
                new TaskDlqRecordFactory(120),
                true
        );

        final TaskHandlingDecision decision = policy.decide(baseContext(
                TaskFailureCategory.FILTER_EVAL,
                2,
                false,
                new RuntimeException("permanent failure")
        ));

        Assertions.assertEquals(TaskHandlingAction.DLQ, decision.action());
        Assertions.assertNotNull(decision.dlqRecord());
        Assertions.assertEquals("payload://ref-001", decision.dlqRecord().payloadRef());
        Assertions.assertEquals(TaskFailureCategory.FILTER_EVAL, decision.dlqRecord().failureCategory());
    }

    /**
     * timeoutTriggered=true이면 TIMEOUT 카테고리가 강제되는지 검증합니다.
     */
    @Test
    void shouldForceTimeoutCategoryWhenTimeoutTriggered() {
        final TaskHandlingPolicyEvaluator policy = new TaskHandlingPolicyEvaluator(
                new FixedRetryPolicy(1, 0L),
                new TaskDlqRecordFactory(120),
                true
        );

        final TaskHandlingDecision decision = policy.decide(baseContext(
                TaskFailureCategory.UNKNOWN,
                1,
                true,
                new RuntimeException("interrupted by timeout")
        ));

        Assertions.assertEquals(TaskHandlingAction.DLQ, decision.action());
        Assertions.assertEquals(TaskFailureCategory.TIMEOUT, decision.finalCategory());
        Assertions.assertEquals(TaskFailureCategory.TIMEOUT, decision.dlqRecord().failureCategory());
    }

    /**
     * 공통 테스트 컨텍스트를 생성합니다.
     */
    private static TaskFailureContext baseContext(
            final TaskFailureCategory category,
            final int attempt,
            final boolean timeoutTriggered,
            final Throwable failure
    ) {
        return new TaskFailureContext(
                "tc.eqp.events",
                0,
                10L,
                "EQP-001",
                "EQP",
                "S6F11",
                attempt,
                "payload://ref-001",
                category,
                failure,
                timeoutTriggered,
                System.currentTimeMillis()
        );
    }
}
