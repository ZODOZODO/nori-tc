package com.nori.tc.db.domain.common.eqp;

/**
 * 설비 상태 (tc_eqp_state.eqp_state)
 *
 * DB Check Constraint:
 * - IDLE
 * - RUN
 * - DOWN
 * - MAINTENANCE
 * - PAUSE
 * - SERVICE_UNAVAILABLE
 */
public enum EqpState {
    IDLE,
    RUN,
    DOWN,
    MAINTENANCE,
    PAUSE,
    SERVICE_UNAVAILABLE
}
