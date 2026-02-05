package com.nori.tc.db.domain.common;

/**
 * 설비 제어 상태 (tc_eqp_state.control_state)
 *
 * DB Check Constraint:
 * - OFFLINE
 * - LOCAL
 * - REMOTE
 */
public enum ControlState {
    OFFLINE,
    LOCAL,
    REMOTE
}
