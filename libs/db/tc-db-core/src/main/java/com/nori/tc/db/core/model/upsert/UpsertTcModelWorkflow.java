package com.nori.tc.db.core.model.upsert;

/**
 * tc_model_workflow upsert 입력(Command).
 *
 * <p>
 * - workflow_key가 있으면 해당 PK 기반으로 갱신합니다.
 * - workflow_key가 없으면 (model_version_key, workflow_name, message_name) 유니크 키 기준으로
 *   존재 여부를 확인한 뒤 갱신/생성을 수행합니다.
 * </p>
 *
 * <p>
 * 주의:
 * - updated_at은 DB(또는 구현체)에서 현재 시각으로 갱신되도록 처리하는 것을 권장합니다.
 * </p>
 */
public record UpsertTcModelWorkflow(
        Long workflowKey,
        long modelVersionKey,
        String workflowName,
        String messageName,
        String eventId,
        String transactionId,
        String workflowFilter,
        String actionName,
        String actionDataIndex
) {
}
