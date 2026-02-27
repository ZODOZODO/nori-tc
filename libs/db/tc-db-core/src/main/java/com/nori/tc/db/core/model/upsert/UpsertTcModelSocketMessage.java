package com.nori.tc.db.core.model.upsert;

import java.time.OffsetDateTime;

/**
 * tc_model_socket_message upsert 입력(Command)
 */
public record UpsertTcModelSocketMessage(
        long modelVersionKey,
        String socketMsgName,
        String description,
        String dataIndex,
        OffsetDateTime updatedAt
) {
}
