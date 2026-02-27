package com.nori.tc.db.domain.model;

import java.time.OffsetDateTime;

/**
 * tc_model_reportid 테이블 1행에 대응하는 순수 DTO.
 *
 * PK/FK:
 * - report_key (PK, identity)
 * - model_version_key (FK -> tc_model_version.model_version_key, ON DELETE CASCADE)
 *
 * Unique:
 * - (model_version_key, report_id)
 */
public record TcModelReportId(
        long reportKey,
        long modelVersionKey,
        String reportId,
        String variableId,
        boolean enabled,
        OffsetDateTime updatedAt
) {
}
