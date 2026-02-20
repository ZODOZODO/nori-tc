package com.nori.tc.business.core.workflow.api.match;

import com.nori.tc.business.domain.modelcache.WorkflowRuntimeEntry;

import java.util.List;
import java.util.Objects;

/**
 * 워크플로우 매칭 결과 모델입니다.
 *
 * <p>매칭된 워크플로우 목록과 필터 컨텍스트를 함께 보관합니다.</p>
 */
public record BusinessWorkflowMatchResult(
        List<WorkflowRuntimeEntry> matchedWorkflows,
        BusinessWorkflowFilterContext filterContext
) {

    /**
     * 생성 시 입력 값을 방어적으로 검증/복사합니다.
     */
    public BusinessWorkflowMatchResult {
        matchedWorkflows = List.copyOf(Objects.requireNonNull(matchedWorkflows, "matchedWorkflows is null"));
        Objects.requireNonNull(filterContext, "filterContext is null");
    }

    /**
     * 매칭된 워크플로우가 존재하는지 반환합니다.
     *
     * @return 1개 이상 매칭되면 true
     */
    public boolean hasMatchedWorkflow() {
        return !matchedWorkflows.isEmpty();
    }
}