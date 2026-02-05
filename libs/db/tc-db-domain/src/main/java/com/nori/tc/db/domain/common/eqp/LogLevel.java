package com.nori.tc.db.domain.common.eqp;

/**
 * 설비 로그 레벨 (tc_eqp_log.log_level)
 *
 * DB Check Constraint:
 * - TRACE
 * - DEBUG
 * - INFO
 * - WARN
 * - ERROR
 */
public enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}
