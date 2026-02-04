package com.nori.tc.db.core.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.LogLevel;

/**
 * tc_eqp_log upsert 입력(Command)
 */
public record UpsertTcEqpLog(
        String eqpId,
        LogLevel logLevel,
        String logPath,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
