package com.nori.tc.db.domain.jar;

import java.time.OffsetDateTime;

/**
 * tc_jar_business 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - eqp_key (tc_eqp.eqp_key) ON DELETE CASCADE
 *
 * 주요 컬럼:
 * - jar_file_name : Business JAR 파일명
 * - jar_file      : Business JAR 바이너리(bytea)
 * - created_at    : 생성 시각
 * - updated_at    : 최종 수정 시각
 * - created_by    : 생성자
 * - updated_by    : 최종 수정자
 */
public record TcJarBusiness(
        long eqpKey,
        String jarFileName,
        byte[] jarFile,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        String updatedBy
) {
}
