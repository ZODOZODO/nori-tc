package com.nori.tc.apps.commgateway.db;

import com.nori.tc.comm.domain.type.CommInterfaceType;

/**
 * Equipment info aggregated from tc_eqp* tables for runtime use.
 *
 * This DTO is app-specific because different apps may require
 * different fields or aggregation rules.
 */
public record GatewayEquipmentInfo(
        String equipmentId,
        CommInterfaceType commInterfaceType,
        String socketType,
        Integer hsmsDeviceId,
        boolean enabled
) {
}
