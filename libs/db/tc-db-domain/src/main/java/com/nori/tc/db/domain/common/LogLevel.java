package com.nori.tc.db.domain.common;

/**
 * 설비 로그 레벨 (tc_eqp_log.log_level)
 *
 * DB Check Constraint:
 * - INFO
 * - DEBUG
 * - TRACE
 */
public enum LogLevel {
    INFO,
    DEBUG,
    TRACE
}
