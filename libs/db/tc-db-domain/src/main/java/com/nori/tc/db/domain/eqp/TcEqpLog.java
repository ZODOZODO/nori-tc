package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.LogLevel;

/**
 * tc_eqp_log 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - eqp_key (tc_eqp.eqp_key) ON DELETE CASCADE
 *
 * 주요 컬럼:
 * - log_level           : TRACE/DEBUG/INFO/WARN/ERROR
 * - log_retention_days  : 1 이상 (기본 30)
 * - log_path            : nullable
 */
public record TcEqpLog(
        long eqpKey,
        LogLevel logLevel,
        int logRetentionDays,
        String logPath,
        OffsetDateTime updatedAt
) {
}
