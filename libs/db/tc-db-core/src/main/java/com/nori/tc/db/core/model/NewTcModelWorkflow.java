package com.nori.tc.db.core.model;

/**
 * tc_model_workflow 생성 입력(Command).
 *
 * - workflow_key/updated_at은 DB가 생성합니다.
 * - (model_key, workflow_name, message_name)은 유니크 제약입니다.
 */
public record NewTcModelWorkflow(
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
