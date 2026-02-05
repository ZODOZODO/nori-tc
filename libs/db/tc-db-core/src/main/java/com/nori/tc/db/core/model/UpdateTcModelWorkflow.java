package com.nori.tc.db.core.model;

/**
 * tc_model_workflow 갱신 입력(Command).
 *
 * - workflow_key는 변경할 수 없습니다.
 * - updated_at은 DB가 갱신합니다.
 */
public record UpdateTcModelWorkflow(
        long workflowKey,
        long modelKey,
        String workflowName,
        String messageName,
        String eventId,
        String transactionId,
        String workflowFilter,
        String actionName,
        String actionDataIndex
) {
}
