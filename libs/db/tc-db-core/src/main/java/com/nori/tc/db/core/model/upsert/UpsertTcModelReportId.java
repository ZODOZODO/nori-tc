package com.nori.tc.db.core.model.upsert;

/**
 * tc_model_reportid upsert 입력(Command)
 */
public record UpsertTcModelReportId(
        long modelVersionKey,
        String reportId,
        String variableId,
        boolean enabled,
        String description
) {
}
