package com.nori.tc.db.core.eqp.upsert;

/**
 * tc_eqp_param upsert 입력(Command)
 *
 * <p>
 * 중복 키(Unique: eqp_key, param_name, param_version)를 기준으로
 * param_value/description을 갱신하거나 신규로 생성합니다.
 * </p>
 */
public record UpsertTcEqpParam(
        long eqpKey,
        String paramName,
        String paramVersion,
        String paramValue,
        String description
) {
}
