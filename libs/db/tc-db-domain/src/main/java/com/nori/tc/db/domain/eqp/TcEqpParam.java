package com.nori.tc.db.domain.eqp;

import java.time.OffsetDateTime;

/**
 * tc_eqp_param 테이블 1행에 대응하는 순수 DTO.
 *
 * PK:
 * - eqp_param_key (IDENTITY)
 *
 * FK:
 * - eqp_key -> tc_eqp.eqp_key ON DELETE CASCADE
 *
 * Unique:
 * - (eqp_key, param_name, param_version)
 */
public record TcEqpParam(
        long eqpParamKey,
        long eqpKey,
        String paramName,
        String paramVersion,
        String paramValue,
        String description,
        String createdBy,
        OffsetDateTime updatedAt
) {
}
