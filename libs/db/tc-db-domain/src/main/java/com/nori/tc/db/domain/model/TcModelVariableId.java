package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.model.VariableIdType;

/**
 * tc_model_variableid 테이블 1행에 대응하는 순수 DTO.
 *
 * - variable_key: DB에서 IDENTITY로 생성됨 (조회 결과에는 항상 존재)
 * - variable_id: varchar(1000)
 * - Unique(model_version_key, variable_id_type, variable_id)
 */
public record TcModelVariableId(
        long variableKey,
        long modelVersionKey,
        String variableId,
        VariableIdType variableIdType,
        String description,
        OffsetDateTime updatedAt
) {
}
