package com.nori.tc.common.task.execution.policy.types;

/**
 * 실패 정책 평가 결과입니다.
 *
 * @param action 결정된 동작
 * @param retryBackoffMs RETRY일 때 적용할 backoff(ms)
 * @param finalCategory 최종 실패 카테고리
 * @param dlqRecord DLQ 동작 시 생성된 레코드
 */
public record TaskHandlingDecision(
        TaskHandlingAction action,
        long retryBackoffMs,
        TaskFailureCategory finalCategory,
        DlqRecord dlqRecord
) {

    /**
     * 결정값 조합의 일관성을 검증합니다.
     */
    public TaskHandlingDecision {
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if (retryBackoffMs < 0L) {
            throw new IllegalArgumentException("retryBackoffMs must be >= 0");
        }
        if (finalCategory == null) {
            throw new IllegalArgumentException("finalCategory is required");
        }
        if (action == TaskHandlingAction.RETRY && dlqRecord != null) {
            throw new IllegalArgumentException("dlqRecord must be null for RETRY");
        }
        if (action == TaskHandlingAction.DLQ && dlqRecord == null) {
            throw new IllegalArgumentException("dlqRecord is required for DLQ");
        }
        if (action != TaskHandlingAction.RETRY && retryBackoffMs != 0L) {
            throw new IllegalArgumentException("retryBackoffMs must be 0 when action is not RETRY");
        }
    }

    /**
     * RETRY 결정을 생성합니다.
     */
    public static TaskHandlingDecision retry(
            final TaskFailureCategory finalCategory,
            final long backoffMs
    ) {
        return new TaskHandlingDecision(TaskHandlingAction.RETRY, backoffMs, finalCategory, null);
    }

    /**
     * DLQ 결정을 생성합니다.
     */
    public static TaskHandlingDecision dlq(
            final TaskFailureCategory finalCategory,
            final DlqRecord dlqRecord
    ) {
        return new TaskHandlingDecision(TaskHandlingAction.DLQ, 0L, finalCategory, dlqRecord);
    }

    /**
     * FAIL 결정을 생성합니다.
     */
    public static TaskHandlingDecision fail(final TaskFailureCategory finalCategory) {
        return new TaskHandlingDecision(TaskHandlingAction.FAIL, 0L, finalCategory, null);
    }

    /**
     * CONTINUE 결정을 생성합니다.
     */
    public static TaskHandlingDecision continueNormally(final TaskFailureCategory finalCategory) {
        return new TaskHandlingDecision(TaskHandlingAction.CONTINUE, 0L, finalCategory, null);
    }
}