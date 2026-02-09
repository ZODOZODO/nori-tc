package com.nori.tc.apps.commgateway.comm;

import com.nori.tc.comm.core.message.OutboundRawFrame;

/**
 * 설비 연결 채널 추상화
 *
 * - Netty Channel, NIO Socket 등 실제 전송 구현을 감싸기 위한 인터페이스입니다.
 * - gateway core는 기술을 모르므로, app 레이어에서 이 채널을 등록/관리합니다.
 */
public interface EquipmentChannel {

    /**
     * raw frame을 즉시 전송합니다.
     */
    void send(OutboundRawFrame frame) throws Exception;
}
