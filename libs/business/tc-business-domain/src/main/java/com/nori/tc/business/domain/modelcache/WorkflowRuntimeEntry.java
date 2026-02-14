package com.nori.tc.business.domain.modelcache;

import com.nori.tc.db.domain.model.TcModelWorkflow;

import java.util.Objects;

/**
 * 런타임에서 사용하는 workflow 단건 엔트리입니다.
 */
public record WorkflowRuntimeEntry(
        long workflowKey,
        String workflowName,
        String messageName,
        String eventId,
        String transactionId,
        String workflowFilter,
        String actionName,
        String actionDataIndex,
        int order
) {

    /**
     * 생성 시 문자열 정규화와 order 검증을 수행합니다.
     */
    public WorkflowRuntimeEntry {
        if (workflowKey <= 0L) {
            throw new IllegalArgumentException("workflowKey must be > 0");
        }
        workflowName = normalizeRequired("workflowName", workflowName);
        messageName = normalizeRequired("messageName", messageName);
        eventId = normalizeNullable(eventId);
        transactionId = normalizeNullable(transactionId);
        workflowFilter = normalizeNullable(workflowFilter);
        actionName = normalizeRequired("actionName", actionName);
        actionDataIndex = normalizeNullable(actionDataIndex);
        if (order < 0) {
            throw new IllegalArgumentException("order must be >= 0");
        }
    }

    /**
     * DB workflow 레코드에서 런타임 엔트리를 생성합니다.
     *
     * @param workflow DB workflow
     * @param order runtime 내부 정렬 순번
     * @return 런타임 엔트리
     */
    public static WorkflowRuntimeEntry from(final TcModelWorkflow workflow, final int order) {
        Objects.requireNonNull(workflow, "workflow is null");
        return new WorkflowRuntimeEntry(
                workflow.workflowKey(),
                workflow.workflowName(),
                workflow.messageName(),
                workflow.eventId(),
                workflow.transactionId(),
                workflow.workflowFilter(),
                workflow.actionName(),
                workflow.actionDataIndex(),
                order
        );
    }

    /**
     * SECS 상세키가 필요한 workflow인지 여부를 반환합니다.
     *
     * @return SECS 상세키 필요 여부
     */
    public boolean isSecsSpecific() {
        return eventId != null || transactionId != null;
    }

    private static String normalizeRequired(final String fieldName, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeNullable(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}

