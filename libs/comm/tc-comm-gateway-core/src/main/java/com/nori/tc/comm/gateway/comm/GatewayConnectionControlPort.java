package com.nori.tc.comm.gateway.comm;

/**
 * 게이트웨이 아웃바운드 연결 라이프사이클 제어 포트입니다.
 *
 * <p>주의: 메서드명에 "Active"가 포함되어 있으나,
 * 실제 제어 대상은 설비 관점 connectionMode=PASSIVE(게이트웨이 발신 연결)입니다.</p>
 */
public interface GatewayConnectionControlPort {

    /**
     * 지정 설비에 대해 즉시 아웃바운드 연결 시도를 요청합니다.
     *
     * @param eqpId 대상 설비 ID
     */
    void connectActiveIfPossible(String eqpId);

    /**
     * 지정 설비의 자동 아웃바운드 재연결을 억제합니다.
     *
     * @param eqpId 대상 설비 ID
     */
    void suppressActiveReconnect(String eqpId);

    /**
     * 지정 설비의 자동 아웃바운드 재연결 억제를 해제합니다.
     *
     * @param eqpId 대상 설비 ID
     */
    void resumeActiveReconnect(String eqpId);
}
