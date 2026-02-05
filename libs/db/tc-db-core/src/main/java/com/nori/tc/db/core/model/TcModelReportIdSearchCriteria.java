package com.nori.tc.db.core.model;

/**
 * tc_model_reportid 검색 조건(Criteria)
 */
public record TcModelReportIdSearchCriteria(
        Long modelKey,
        String reportId,
        Boolean enabled
) {
}
