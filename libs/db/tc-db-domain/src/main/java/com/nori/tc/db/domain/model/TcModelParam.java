package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

/**
 * tc_model_param 테이블 1행에 대응하는 순수 DTO.
 *
 * PK:
 * - model_param_key (IDENTITY)
 *
 * FK:
 * - model_key -> tc_model.model_key ON DELETE CASCADE
 *
 * Unique:
 * - (model_key, param_name)
 */
public record TcModelParam(
        long modelParamKey,
        long modelKey,
        String paramName,
        String paramValue,
        OffsetDateTime updatedAt
) {
}
