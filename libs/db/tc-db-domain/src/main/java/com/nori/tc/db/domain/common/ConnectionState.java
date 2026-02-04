package com.nori.tc.db.domain.common;

/**
 * 설비 연결 상태 (tc_eqp_conn_state.conn_state)
 *
 * DB Check Constraint:
 * - DISCONNECTED
 * - CONNECTING
 * - CONNECTED
 * - ERROR
 */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
