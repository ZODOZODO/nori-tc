package com.nori.tc.common.task.policy;

import com.nori.tc.common.kafka.processing.FixedRetryPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link DefaultTaskHandlingPolicy} 정책 분기 검증 테스트입니다.
 */
class DefaultTaskHandlingPolicyTest {

    /**
     * WORKFLOW_NOT_FOUND는 실패가 아닌 CONTINUE로 처리되는지 검증합니다.
     */
    @Test
    void shouldReturnContinueWhenWorkflowNotFound() {
        final DefaultTaskHandlingPolicy policy = new DefaultTaskHandlingPolicy(
                new FixedRetryPolicy(3, 100L),
                new DefaultDlqRecordFactory(200),
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
     * retry 가능 횟수에서는 RETRY decision이 반환되는지 검증합니다.
     */
    @Test
    void shouldReturnRetryWhenRetryPolicyAllows() {
        final DefaultTaskHandlingPolicy policy = new DefaultTaskHandlingPolicy(
                new FixedRetryPolicy(3, 250L),
                new DefaultDlqRecordFactory(200),
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
     * retry 한도 초과 시 DLQ decision과 payloadRef 포함 레코드가 생성되는지 검증합니다.
     */
    @Test
    void shouldReturnDlqWhenRetryExhausted() {
        final DefaultTaskHandlingPolicy policy = new DefaultTaskHandlingPolicy(
                new FixedRetryPolicy(2, 100L),
                new DefaultDlqRecordFactory(120),
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
     * timeoutTriggered가 true이면 최종 카테고리가 TIMEOUT으로 강제되는지 검증합니다.
     */
    @Test
    void shouldForceTimeoutCategoryWhenTimeoutTriggered() {
        final DefaultTaskHandlingPolicy policy = new DefaultTaskHandlingPolicy(
                new FixedRetryPolicy(1, 0L),
                new DefaultDlqRecordFactory(120),
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

