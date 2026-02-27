package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

/**
 * tc_model_param 테이블 1행에 대응하는 순수 DTO.
 *
 * PK:
 * - model_param_key (IDENTITY)
 *
 * FK:
 * - model_version_key -> tc_model_version.model_version_key ON DELETE CASCADE
 *
 * Unique:
 * - (model_version_key, param_name)
 */
public record TcModelParam(
        long modelParamKey,
        long modelVersionKey,
        String paramName,
        String paramValue,
        OffsetDateTime updatedAt
) {
}
