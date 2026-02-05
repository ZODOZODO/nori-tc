package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

/**
 * tc_model_socket_message 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - socket_msg_key : bigint identity (PK)
 * - model_key      : tc_model.model_key FK (ON DELETE CASCADE)
 *
 * Unique:
 * - (model_key, socket_msg_name)
 */
public record TcModelSocketMessage(
        long socketMsgKey,
        long modelKey,
        String socketMsgName,
        String description,
        String dataIndex,
        OffsetDateTime updatedAt
) {
}
