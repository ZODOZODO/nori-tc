package com.nori.tc.db.core.model.upsert;

/**
 * tc_model_param upsert 입력(Command)
 *
 * <p>
 * 중복 키(Unique: model_version_key, param_name)를 기준으로
 * param_value를 갱신하거 신규로 생성합니다.
 * </p>
 */
public record UpsertTcModelParam(
        long modelVersionKey,
        String paramName,
        String paramValue,
        String description
) {

    public UpsertTcModelParam {
        ModelFieldLengthValidator.requireTextWithMax(
                paramName,
                "paramName",
                ModelFieldLengthValidator.PARAM_NAME_MAX_LENGTH
        );
    }
}
