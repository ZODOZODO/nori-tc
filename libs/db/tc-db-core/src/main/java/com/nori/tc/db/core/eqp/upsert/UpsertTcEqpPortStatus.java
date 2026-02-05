package com.nori.tc.db.core.eqp.upsert;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.eqp.CarrierState;
import com.nori.tc.db.domain.common.eqp.CarrierType;
import com.nori.tc.db.domain.common.eqp.PortState;
import com.nori.tc.db.domain.common.eqp.PortType;

/**
 * tc_eqp_port_status upsert 입력(Command)
 */
public record UpsertTcEqpPortStatus(
        long eqpKey,
        String portId,
        PortType portType,
        PortState portState,
        String carrierId,
        CarrierType carrierType,
        CarrierState carrierState,
        OffsetDateTime updatedAt
) {
}
