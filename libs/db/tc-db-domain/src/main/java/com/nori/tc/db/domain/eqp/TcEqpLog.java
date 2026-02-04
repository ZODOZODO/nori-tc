package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.LogLevel;

/**
 * tc_eqp_log 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - eqp_id (tc_eqp.eqp_id) ON DELETE CASCADE
 */
public record TcEqpLog(
        String eqpId,
        LogLevel logLevel,
        String logPath,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
