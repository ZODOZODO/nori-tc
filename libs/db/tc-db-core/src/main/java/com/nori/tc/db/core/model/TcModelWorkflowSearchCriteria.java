package com.nori.tc.db.core.model;

/**
 * tc_model_workflow 검색 조건(Criteria)
 *
 * - null은 "조건 없음"을 의미합니다.
 * - LIKE 검색 규칙(contains 등)은 구현체에서 문서화합니다.
 */
public record TcModelWorkflowSearchCriteria(
        Long modelKey,
        String workflowNameLike,
        String messageNameLike
) {
    public static TcModelWorkflowSearchCriteria empty() {
        return new TcModelWorkflowSearchCriteria(null, null, null);
    }
}
