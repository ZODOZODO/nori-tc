package com.nori.tc.comm.core.port;

import com.nori.tc.comm.core.message.OutboundRawFrame;

/**
 * TCP 송신 Port
 *
 * 목적
 * - core 엔진은 Netty Channel을 모릅니다.
 * - 앱이 eqpId -> channel 매핑을 알고 있으므로, 실제 write+flush는 앱에서 수행합니다.
 */
public interface OutboundSenderPort {

    /**
     * raw frame을 TCP로 송신합니다.
     *
     * @throws Exception 송신 실패 시 예외
     */
    void send(OutboundRawFrame frame) throws Exception;
}
