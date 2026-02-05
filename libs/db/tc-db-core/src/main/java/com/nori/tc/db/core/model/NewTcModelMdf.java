package com.nori.tc.db.core.model;

/**
 * tc_model_mdf 생성 입력(Command)
 *
 * <p>
 * - mdf_key, updated_at은 DB에서 생성/갱신되므로 입력에서 제외합니다.
 * - (model_key, mdf_name)은 유니크이므로 중복 시 DbDuplicateKeyException을 기대합니다.
 * </p>
 */
public record NewTcModelMdf(
        long modelKey,
        String mdfName,
        byte[] mdfFile
) {
}
