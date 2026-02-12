package com.nori.tc.comm.core.eqp;

import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;

/**
 * 설비 통신 프로파일(정적 설정)
 *
 * 예)
 * - commInterfaceType: HSMS | SOCKET
 * - socketType: socket_protocol_type 값 (SOCKET인 경우에만 의미)
 *
 * 주의
 * - 네트워크 정보(ip/port)는 앱(netty) 레이어에서 다루는 경우가 많으므로,
 *   core 엔진에는 "파이프라인 분기"에 필요한 최소 정보만 둡니다.
 */
public record EquipmentProfile(
        EquipmentId equipmentId,
        CommInterfaceType commInterfaceType,
        String socketType
) {
    public EquipmentProfile {
        if (equipmentId == null) {
            throw new IllegalArgumentException("equipmentId is required");
        }
        if (commInterfaceType == null) {
            throw new IllegalArgumentException("commInterfaceType is required");
        }

        // SOCKET이면 socketType이 필수인 경우가 대부분이라, 방어적으로 검증합니다.
        if (commInterfaceType == CommInterfaceType.SOCKET) {
            if (socketType == null || socketType.isBlank()) {
                throw new IllegalArgumentException("socketType is required for SOCKET");
            }
        }
    }
}
