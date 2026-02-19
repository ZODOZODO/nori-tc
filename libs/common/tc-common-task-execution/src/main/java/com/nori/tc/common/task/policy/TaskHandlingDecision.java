package com.nori.tc.common.task.policy;

/**
 * 실패 처리 정책 평가 결과입니다.
 *
 * @param action 후속 처리 액션
 * @param retryBackoffMs 재시도 지연(ms), RETRY가 아니면 0
 * @param finalCategory 최종 확정 실패 카테고리
 * @param dlqRecord DLQ 이관 시 포함할 레코드, DLQ가 아니면 null
 */
public record TaskHandlingDecision(
        TaskHandlingAction action,
        long retryBackoffMs,
        TaskFailureCategory finalCategory,
        DlqRecord dlqRecord
) {

    /**
     * decision 생성 시 유효성 검증을 수행합니다.
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
     * RETRY decision을 생성합니다.
     *
     * @param finalCategory 최종 카테고리
     * @param backoffMs backoff(ms)
     * @return RETRY decision
     */
    public static TaskHandlingDecision retry(
            final TaskFailureCategory finalCategory,
            final long backoffMs
    ) {
        return new TaskHandlingDecision(TaskHandlingAction.RETRY, backoffMs, finalCategory, null);
    }

    /**
     * DLQ decision을 생성합니다.
     *
     * @param finalCategory 최종 카테고리
     * @param dlqRecord DLQ 레코드
     * @return DLQ decision
     */
    public static TaskHandlingDecision dlq(
            final TaskFailureCategory finalCategory,
            final DlqRecord dlqRecord
    ) {
        return new TaskHandlingDecision(TaskHandlingAction.DLQ, 0L, finalCategory, dlqRecord);
    }

    /**
     * FAIL decision을 생성합니다.
     *
     * @param finalCategory 최종 카테고리
     * @return FAIL decision
     */
    public static TaskHandlingDecision fail(final TaskFailureCategory finalCategory) {
        return new TaskHandlingDecision(TaskHandlingAction.FAIL, 0L, finalCategory, null);
    }

    /**
     * CONTINUE decision을 생성합니다.
     *
     * @param finalCategory 최종 카테고리
     * @return CONTINUE decision
     */
    public static TaskHandlingDecision continueNormally(final TaskFailureCategory finalCategory) {
        return new TaskHandlingDecision(TaskHandlingAction.CONTINUE, 0L, finalCategory, null);
    }
}

