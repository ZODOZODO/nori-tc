package com.nori.tc.db.core.model.upsert;

/**
 * {@code tc_model_mdf} upsert 입력(Command)입니다.
 *
 * <p>
 * 스키마 규칙은 {@code model_version_key} 기준 1:1입니다.
 * </p>
 * <p>
 * 1) {@code mdfKey}가 있으면 PK 기준 갱신을 수행합니다.
 * 2) {@code mdfKey}가 없으면 {@code modelVersionKey} 기준으로 기존 행을 찾아 갱신하거나 생성합니다.
 * </p>
 */
public record UpsertTcModelMdf(
        Long mdfKey,
        long modelVersionKey,
        String mdfName,
        byte[] mdfFile
) {
}
