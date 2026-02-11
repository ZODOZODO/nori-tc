package com.nori.tc.apps.commgateway.db;

import com.nori.tc.apps.commgateway.comm.ConnectionMode;
import com.nori.tc.comm.domain.type.CommInterfaceType;

/**
 * 설비 런타임 정보 DTO.
 *
 * - DB 어댑터가 tc_eqp* 테이블을 조회해 생성한다
 * - 코어/넷티/카프카 등 런타임 모듈이 공통으로 사용
 * - 앱별 필드/조합 규칙이 다를 수 있어 구조는 유지하되 구현은 어댑터가 담당
 */
public record GatewayEquipmentInfo(
        String equipmentId,
        CommInterfaceType commInterfaceType,
        String socketType,
        Integer hsmsDeviceId,
        String eqpIp,
        Integer eqpPort,
        ConnectionMode connectionMode,
        boolean enabled
) {
}
