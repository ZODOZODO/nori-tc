package com.nori.tc.db.domain.common.eqp;

/**
 * 포트 타입 (tc_eqp_port_status.port_type)
 *
 * DB Check Constraint:
 * - LOAD_PORT
 * - UNLOAD_PORT
 * - INTERNAL_BUFFER
 * - OTHER
 */
public enum PortType {
    LOAD_PORT,
    UNLOAD_PORT,
    INTERNAL_BUFFER,
    OTHER
}
