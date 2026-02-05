package com.nori.tc.db.domain.common.eqp;

/**
 * 포트 캐리어 타입 (tc_eqp_port_status.carrier_type)
 *
 * DB Check Constraint:
 * - FOUP
 * - CASSETTE
 * - WAFER_BOX
 * - TRAY
 * - OTHER
 */
public enum CarrierType {
    FOUP,
    CASSETTE,
    WAFER_BOX,
    TRAY,
    OTHER
}
