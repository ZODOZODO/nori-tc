package com.nori.tc.db.core.eqp;

import com.nori.tc.db.domain.common.LogLevel;

/**
 * tc_eqp_log upsert 입력(Command)
 *
 * 상세 규칙:
 * - logLevel: null이면 DB 기본값(INFO) 적용
 * - logRetentionDays: null이면 DB 기본값(30) 적용, 1 이상이어야 함 (DB CHECK 제약 조건)
 * - logPath: nullable
 */
public record UpsertTcEqpLog(
        Long eqpKey,
        LogLevel logLevel,
        Integer logRetentionDays,
        String logPath
) {
}
