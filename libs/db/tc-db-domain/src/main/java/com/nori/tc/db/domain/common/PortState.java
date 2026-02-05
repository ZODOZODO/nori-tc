package com.nori.tc.db.domain.common;

/**
 * 포트 상태 (tc_eqp_port_status.port_state)
 *
 * DB Check Constraint:
 * - EMPTY
 * - LOADED
 * - READY_TO_LOAD
 * - DOWN
 * - IN_SERVICE
 * - UNKNOWN
 */
public enum PortState {
    EMPTY,
    LOADED,
    READY_TO_LOAD,
    DOWN,
    IN_SERVICE,
    UNKNOWN
}
