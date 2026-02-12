package com.nori.tc.comm.core.message;

import com.nori.tc.comm.core.eqp.EquipmentId;
import com.nori.tc.comm.domain.type.CommInterfaceType;

/**
 * 통합 tc-comm-gateway에서 "바로 TCP로 송신해야 하는" raw bytes 프레임
 *
 * 예)
 * - HSMS: SELECT/Linktest 등의 제어 프레임(세션 머신이 생성)
 * - SOCKET: 특정 타입에서 응답을 즉시 보내야 하는 경우(옵션)
 *
 * 주의
 * - Netty Channel에 직접 쓰지 않습니다.
 * - OutboundSenderPort가 eqpId로 채널을 찾아 송신하도록 둡니다.
 */
public record OutboundRawFrame(
        EquipmentId equipmentId,
        CommInterfaceType commInterfaceType,
        String socketType,
        byte[] bytes,
        long createdAtEpochMs,
        String description
) {
    public OutboundRawFrame {
        if (equipmentId == null) throw new IllegalArgumentException("equipmentId is required");
        if (commInterfaceType == null) throw new IllegalArgumentException("commInterfaceType is required");
        if (bytes == null) throw new IllegalArgumentException("bytes is required");
        if (description == null) description = "";
    }
}
