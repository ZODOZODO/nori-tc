package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

/**
 * tc_model_socket_message 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - socket_msg_key : bigint identity (PK)
 * - model_version_key      : tc_model_version.model_version_key FK (ON DELETE CASCADE)
 *
 * Unique:
 * - (model_version_key, socket_msg_name)
 * - socket_msg_name: varchar(1000)
 */
public record TcModelSocketMessage(
        long socketMsgKey,
        long modelVersionKey,
        String socketMsgName,
        String description,
        String dataIndex,
        OffsetDateTime updatedAt
) {
}
