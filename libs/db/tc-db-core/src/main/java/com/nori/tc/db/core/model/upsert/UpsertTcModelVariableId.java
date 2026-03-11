package com.nori.tc.db.core.model.upsert;

import com.nori.tc.db.domain.common.model.VariableIdType;

/**
 * tc_model_variableid upsert 입력(Command)
 *
 * <p>
 * Unique(model_version_key, variable_id_type, variable_id)를 기준으로
 * description을 갱신하거나 신규로 생성합니다.
 * </p>
 */
public record UpsertTcModelVariableId(
        long modelVersionKey,
        VariableIdType variableIdType,
        String variableId,
        String description
) {

    public UpsertTcModelVariableId {
        ModelFieldLengthValidator.requireTextWithMax(
                variableId,
                "variableId",
                ModelFieldLengthValidator.VARIABLE_ID_MAX_LENGTH
        );
        if (variableIdType == null) {
            throw new IllegalArgumentException("variableIdType must not be null");
        }
    }
}
