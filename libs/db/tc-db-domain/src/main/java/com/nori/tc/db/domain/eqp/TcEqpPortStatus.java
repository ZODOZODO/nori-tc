package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.eqp.CarrierState;
import com.nori.tc.db.domain.common.eqp.CarrierType;
import com.nori.tc.db.domain.common.eqp.PortState;
import com.nori.tc.db.domain.common.eqp.PortType;

/**
 * tc_eqp_port_status 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - eqp_port_status_key (IDENTITY)
 * - eqp_key (tc_eqp.eqp_key) ON DELETE CASCADE
 *
 * Unique:
 * - (eqp_key, port_id)
 *
 * nullable 컬럼은 Java에서 null 허용 타입으로 둡니다.
 */
public record TcEqpPortStatus(
        long eqpPortStatusKey,
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
