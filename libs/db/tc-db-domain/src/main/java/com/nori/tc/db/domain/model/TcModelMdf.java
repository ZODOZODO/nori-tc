package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

/**
 * {@code tc_model_mdf} 테이블 1행에 대응하는 레코드 모델입니다.
 *
 * <p>
 * 주요 제약은 다음과 같습니다.
 * 1) {@code mdf_key}: PK
 * 2) {@code model_version_key}: FK + UNIQUE(버전당 MDF 1건)
 * 3) {@code mdf_name}: MDF 식별 이름(업무 명칭)
 * 4) {@code mdf_file}: MDF XML 원문(byte[])
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
