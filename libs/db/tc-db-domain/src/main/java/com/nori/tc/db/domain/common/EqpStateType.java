package com.nori.tc.db.domain.common;

/**
 * 설비 상태 이력 구분값 (tc_eqp_state_hist.state_type).
 *
 * <p>DB CHECK 제약 조건: OPER / CONN 만 허용됩니다.</p>
 */
public enum EqpStateType {
    /**
     * 설비 운전 상태 (Operational State)
     */
    OPER,

    /**
     * 설비 통신 상태 (Connection State)
     */
    CONN
}
