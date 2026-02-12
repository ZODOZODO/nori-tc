package com.nori.tc.comm.gateway.comm;

/**
 * ACTIVE 연결 라이프사이클 제어 포트입니다.
 *
 * <p>Kafka/UI adapter가 Netty 구현 세부사항에 직접 의존하지 않도록
 * core 계층에서 런타임 제어 계약을 분리합니다.</p>
 */
public interface GatewayConnectionControlPort {

    /**
     * 장비에 대해 ACTIVE 즉시 연결 시도를 요청합니다.
     */
    void connectActiveIfPossible(String eqpId);

    /**
     * 장비의 ACTIVE 자동 재연결을 억제합니다.
     */
    void suppressActiveReconnect(String eqpId);

    /**
     * 장비의 ACTIVE 자동 재연결을 재개합니다.
     */
    void resumeActiveReconnect(String eqpId);
}
