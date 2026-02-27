package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

/**
 * tc_model_mdf 테이블 1행에 대응하는 순수 DTO.
 *
 * <p>
 * 주요 제약/의미:
 * <ul>
 *   <li>mdf_key: DB에서 IDENTITY로 생성되는 대리키(PK)</li>
 *   <li>model_version_key: tc_model(model_version_key)에 대한 FK (ON DELETE CASCADE)</li>
 *   <li>(model_version_key, mdf_name) 유니크 제약으로 동일 모델 내 MDF 이름 중복 방지</li>
 *   <li>mdf_file: MDF 파일 바이너리 데이터(bytea)</li>
 *   <li>updated_at: DB에서 CURRENT_TIMESTAMP로 갱신되는 최종 수정 시간</li>
 * </ul>
 * </p>
 */
public record TcModelMdf(
        long mdfKey,
        long modelVersionKey,
        String mdfName,
        byte[] mdfFile,
        OffsetDateTime updatedAt
) {
}
