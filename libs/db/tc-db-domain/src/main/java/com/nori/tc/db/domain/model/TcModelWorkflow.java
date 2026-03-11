package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

/**
 * tc_model_workflow 테이블 1행에 대응하는 순수 DTO.
 *
 * [DB 스키마 요약]
 * - workflow_key      : bigint PK (IDENTITY)
 * - model_version_key         : bigint FK -> tc_model_version.model_version_key (ON DELETE CASCADE)
 * - workflow_name     : varchar(1000) NOT NULL
 * - message_name      : varchar(1000) NOT NULL
 * - event_id          : varchar(200) NULL
 * - transaction_id    : varchar(2000) NULL
 * - workflow_filter   : varchar(4000) NULL
 * - action_name       : varchar(200) NOT NULL
 * - action_data_index : varchar(4000) NULL
 * - updated_at        : timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
 *
 * [제약/인덱스]
 * - PK: workflow_key
 * - UK: (model_version_key, workflow_name, message_name)
 * - INDEX: model_version_key, workflow_name
 */
public record TcModelWorkflow(
        long workflowKey,
        long modelVersionKey,
        String workflowName,
        String messageName,
        String eventId,
        String transactionId,
        String workflowFilter,
        String actionName,
        String actionDataIndex,
        OffsetDateTime updatedAt
) {
}
