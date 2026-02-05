package com.nori.tc.db.domain.common;

/**
 * 설비 상태 (tc_eqp_state.eqp_state)
 *
 * DB Check Constraint:
 * - IDLE
 * - RUN
 * - DOWN
 * - MAINTENANCE
 * - PAUSE
 */
public enum EqpState {
    IDLE,
    RUN,
    DOWN,
    MAINTENANCE,
    PAUSE
}
