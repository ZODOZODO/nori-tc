package com.nori.tc.db.domain.common.eqp;

/**
 * 포트 캐리어 상태 (tc_eqp_port_status.carrier_state)
 *
 * DB Check Constraint:
 * - CLAMPED
 * - UNCLAMPED
 * - OPENED
 * - CLOSED
 * - UNKNOWN
 */
public enum CarrierState {
    CLAMPED,
    UNCLAMPED,
    OPENED,
    CLOSED,
    UNKNOWN
}
