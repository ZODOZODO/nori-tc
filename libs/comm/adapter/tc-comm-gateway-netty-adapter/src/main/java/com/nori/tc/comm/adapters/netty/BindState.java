package com.nori.tc.comm.adapters.netty;

/**
 * 채널의 설비 바인딩 상태입니다.
 *
 * <p>UNBOUND: eqpId 미등록 상태(초기 등록 메시지 대기)</p>
 * <p>BOUND: eqpId 확정 및 registry/mailbox 등록 완료 상태</p>
 */
public enum BindState {
    UNBOUND,
    BOUND
}
