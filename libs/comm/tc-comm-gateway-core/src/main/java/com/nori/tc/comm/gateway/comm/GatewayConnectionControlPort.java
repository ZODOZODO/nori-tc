package com.nori.tc.comm.gateway.comm;

/**
 * 게이트웨이 Netty 통신 런타임 제어 포트입니다.
 *
 * <p>장비 기준 connectionMode( ACTIVE / PASSIVE )에 따라 실행 방식이 달라집니다.</p>
 * <p>- ACTIVE  : 장비가 게이트웨이로 접속하므로 게이트웨이는 서버(listener)를 준비합니다.</p>
 * <p>- PASSIVE : 게이트웨이가 장비로 접속하므로 아웃바운드 연결을 시도합니다.</p>
 *
 * <p>주의: 기존 메서드명에 포함된 "Active"는 역사적인 이름이며,
 * 실제 의미는 장비 기준 PASSIVE(게이트웨이 아웃바운드 접속) 제어입니다.</p>
 */
public interface GatewayConnectionControlPort {

    /**
     * 장비 1대의 통신 런타임을 현재 DB/컨텍스트 설정 기준으로 시작합니다.
     *
     * <p>구현체는 다음 항목을 내부에서 판단합니다.</p>
     * <p>1) shard ownership 여부</p>
     * <p>2) enabled 여부</p>
     * <p>3) 장비 기준 connectionMode</p>
     * <p>4) 모드별 필수 설정(ip/port 등) 유효성</p>
     *
     * @param eqpId 대상 장비 ID
     */
    void startRuntimeIfPossible(String eqpId);

    /**
     * 장비 1대의 통신 런타임을 현재 DB/컨텍스트 설정 기준으로 정지합니다.
     *
     * <p>구현체는 장비 기준 모드에 따라 아래 동작을 수행합니다.</p>
     * <p>- ACTIVE  : 공유 listener 멤버십 해제 및 마지막 멤버일 때 listener 종료</p>
     * <p>- PASSIVE : 자동 재연결 억제(suppress)</p>
     *
     * <p>실제 연결 채널 close는 상위 런타임 서비스가 별도 수행할 수 있습니다.</p>
     *
     * @param eqpId 대상 장비 ID
     */
    void stopRuntimeIfPossible(String eqpId);

    /**
     * 장비 기준 PASSIVE 장비에 대해 즉시 아웃바운드 연결을 시도합니다.
     *
     * @param eqpId 대상 장비 ID
     */
    void connectActiveIfPossible(String eqpId);

    /**
     * 장비 기준 PASSIVE 장비의 자동 재연결을 억제합니다.
     *
     * @param eqpId 대상 장비 ID
     */
    void suppressActiveReconnect(String eqpId);

    /**
     * 장비 기준 PASSIVE 장비의 자동 재연결 억제를 해제합니다.
     *
     * @param eqpId 대상 장비 ID
     */
    void resumeActiveReconnect(String eqpId);
}
