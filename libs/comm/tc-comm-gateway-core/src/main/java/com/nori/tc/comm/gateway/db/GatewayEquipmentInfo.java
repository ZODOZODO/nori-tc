package com.nori.tc.comm.gateway.db;

import com.nori.tc.comm.gateway.db.ConnectionMode;
import com.nori.tc.comm.gateway.domain.type.CommInterfaceType;

/**
 * 게이트웨이 런타임에서 공통으로 사용하는 설비 핵심 정보 DTO입니다.
 *
 * <p>이 DTO는 tc_eqp + tc_eqp_hsms + tc_eqp_socket를 조합해서 만든 "핵심 조회 모델"입니다.</p>
 * <p>핵심 원칙:</p>
 * <p>- 통신 라우팅/연결 판단에 필요한 최소 정보는 이 DTO만으로 처리</p>
 * <p>- 세부 설정(tc_eqp_log, tc_eqp_param, tc_eqp_state 등)은 EquipmentContextProfile로 확장</p>
 */
public record GatewayEquipmentInfo(
        Long eqpKey,
        String equipmentId,
        CommInterfaceType commInterfaceType,
        String socketType,
        Integer hsmsDeviceId,
        String eqpIp,
        Integer eqpPort,
        Long modelKey,
        ConnectionMode connectionMode,
        boolean enabled
) {
}
