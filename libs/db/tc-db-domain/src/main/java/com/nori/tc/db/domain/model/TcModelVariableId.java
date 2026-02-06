package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

import com.nori.tc.db.domain.common.model.VariableIdType;

/**
 * tc_model_variableid 테이블 1행에 대응하는 순수 DTO.
 *
 * - variable_key: DB에서 IDENTITY로 생성됨 (조회 결과에는 항상 존재)
 * - Unique(model_key, variable_id_type, variable_id)
 */
public record TcModelVariableId(
        long variableKey,
        long modelKey,
        String variableId,
        VariableIdType variableIdType,
        String description,
        OffsetDateTime updatedAt
) {
}
