package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.ProtocolType;

/**
 * tc_eqp 테이블 1행에 대응하는 순수 DTO.
 *
 * PK:
 * - eqp_id
 *
 * FK:
 * - model_key -> tc_model.model_key
 */
public record TcEqp(
        String eqpId,
        ProtocolType protocolType,
        String eqpIp,
        int eqpPort,
        long modelKey,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
