package com.nori.tc.apps.commgateway.db;

import com.nori.tc.comm.domain.type.CommInterfaceType;

/**
 * Gateway runtime startup equipment info aggregated from tc_eqp* tables.
 */
public record GatewayEquipmentInfo(
        String equipmentId,
        CommInterfaceType commInterfaceType,
        String socketType,
        Integer hsmsDeviceId,
        boolean enabled
) {
}
