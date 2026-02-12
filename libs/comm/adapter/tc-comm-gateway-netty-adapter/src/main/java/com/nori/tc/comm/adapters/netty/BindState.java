package com.nori.tc.comm.adapters.netty;

/**
 * Connection bind state.
 *
 * UNBOUND: eqpId 미등록 상태 (등록 메시지 대기)
 * BOUND  : eqpId 확정 + registry/mailbox 등록 완료 상태
 */
public enum BindState {
    UNBOUND,
    BOUND
}
