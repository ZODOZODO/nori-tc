package com.nori.tc.db.core.eqp.upsert;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.CarrierState;
import com.nori.tc.db.domain.common.CarrierType;
import com.nori.tc.db.domain.common.PortState;
import com.nori.tc.db.domain.common.PortType;

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
